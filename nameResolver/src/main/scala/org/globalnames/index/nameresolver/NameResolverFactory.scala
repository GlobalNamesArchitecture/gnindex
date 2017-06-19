package org.globalnames
package index
package nameresolver

import javax.inject.{Inject, Singleton}
import java.util.UUID

import org.apache.commons.lang3.StringUtils.capitalize
import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.twitter_util.UtilBijections._
import com.twitter.util.{Future => TwitterFuture}
import thrift.matcher.{Service => MatcherService}
import slick.jdbc.PostgresProfile.api._
import dao.{ Tables => T }
import thrift.nameresolver.{MatchType, MatchKind, Name, NameInput, Request, Response, Result}
import thrift.Uuid
import parser.ScientificNameParser.{Result => SNResult, instance => SNP}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future => ScalaFuture}
import scalaz.syntax.std.boolean._
import scalaz.syntax.std.option._

class NameResolver private[nameresolver](request: Request, database: Database) {
  private val NameStringsPerFuture = 200
  private val EmptyUuid = UuidGenerator.generate("")

  type NameStringsQuery = Query[T.NameStrings, T.NameStringsRow, Seq]
  case class NameInputParsed(nameInput: NameInput, parsed: SNResult)
  case class RequestResponse(request: NameInputParsed, response: Response)
  case class DBResult(name: Name, canonicalName: Option[Name])
  case class DBResults(total: Int, results: Vector[DBResult])

  private val takeCount: Int = request.perPage.min(1000).max(0)
  private val dropCount: Int = (request.page * request.perPage).max(0)
  private val namesParsed: Vector[NameInputParsed] =
    request.names.toVector.map { ni =>
      val capital = capitalize(ni.value)
      NameInputParsed(nameInput = ni.copy(value = capital),
                      parsed = SNP.fromString(capital))
    }

  private def exactNamesQuery(nameUuid: Rep[UUID], canonicalNameUuid: Rep[UUID]) = {
    T.NameStrings.filter { ns => ns.id === nameUuid || ns.canonicalUuid === canonicalNameUuid }
  }

  private def exactNamesQueryWildcard(query: Rep[String]) = {
    T.NameStrings.filter { ns => ns.name.like(query) || ns.canonical.like(query) }
  }

  private
  def queryWithSurrogates(nameStringsQuery: NameStringsQuery) = {
    val nameStringsQuerySurrogates =
      if (request.withSurrogates) nameStringsQuery
      else nameStringsQuery.filter { ns => ns.surrogate.isEmpty || !ns.surrogate }

    val query = for {
      ns <- nameStringsQuerySurrogates
      nsi <- T.NameStringIndices.filter { nsi => nsi.nameStringId === ns.id }
      ds <- {
        val ds = T.DataSources.filter { ds => ds.id === nsi.dataSourceId }
        request.dataSourceIds.isEmpty ?
          ds | ds.filter { ds => ds.id.inSetBind(request.dataSourceIds) }
      }
    } yield (ns, nsi, ds)

    query
  }

  private def queryWithVernaculars(
    query: Query[(T.NameStrings, T.NameStringIndices, T.DataSources),
                 (T.NameStringsRow, T.NameStringIndicesRow, T.DataSourcesRow), Seq]) = {

    val vernacularsQuery = if (request.withVernaculars) {
      T.VernacularStringIndices.zip(T.VernacularStrings)
    } else {
      T.VernacularStringIndices.zip(T.VernacularStrings).take(0)
    }

    query.joinLeft(vernacularsQuery)
         .on { case ((_, ns, _), (vsi, _)) =>
           ns.dataSourceId === vsi.dataSourceId && ns.taxonId === vsi.taxonId
         }
  }

  // NB: perPage == 5, query = sn.input.verbatim.some
  protected def materializeNameStringsSequence(
    nameStringsQueries: Seq[NameStringsQuery]): ScalaFuture[Seq[DBResults]] = {

    val resultsQuerySeq = nameStringsQueries.map { nsq =>
      val qrySurrogated = queryWithSurrogates(nsq)
      val qryVernacularized = queryWithVernaculars(qrySurrogated.drop(dropCount).take(takeCount))
      for {
        data <- qryVernacularized.result
        total <- qrySurrogated.size.result
      } yield {
        val dataGroupedWithVernaculars =
          data.groupBy { case (name, _) => name }
              .mapValues { values => values.map { case (_, vernacs) => vernacs } }
        (dataGroupedWithVernaculars, total)
      }
    }

    val resultsPerNameFut = database.run(DBIO.sequence(resultsQuerySeq))
    val result = resultsPerNameFut.map { resultsPerName =>
      resultsPerName.zip(nameStringsQueries)
                    .map { case ((resultsMap, total), nmr) =>
        val results = resultsMap.keys.map { case (ns, nsi, ds) =>
          val canonicalNameOpt =
            for { canId <- ns.canonicalUuid; canName <- ns.canonical }
              yield Name(uuid = Uuid(canId.toString), value = canName)
          DBResult(name = Name(uuid = Uuid(ns.id.toString), value = ns.name),
                   canonicalName = canonicalNameOpt)
        }
        DBResults(total = total, results = results.toVector)
      }
    }
    result
  }

  def queryExactMatchesByUuid(): ScalaFuture[Seq[RequestResponse]] = {
    val dbResultsChunksFuts = namesParsed
         .grouped(NameStringsPerFuture).toSeq
         .map { namesPortion =>
           val namePortionQry = namesPortion.map { name =>
             val canonicalUuid = name.parsed.canonizedUuid().map { _.id }.getOrElse(EmptyUuid)
             val qry: NameStringsQuery =
               if (canonicalUuid == EmptyUuid) {
                 T.NameStrings.filter { ns => ns.id === name.parsed.preprocessorResult.id }
               } else {
                 exactNamesQuery(name.parsed.preprocessorResult.id, canonicalUuid)
               }
             qry
           }
           materializeNameStringsSequence(namePortionQry)
         }

    ScalaFuture.sequence(dbResultsChunksFuts).map { dbResultssChunks =>
      val dbResultss = dbResultssChunks.flatten.toVector
      dbResultss.zip(namesParsed).map { case (dbResults, nameParsed) =>
        val results = dbResults.results.map { dbResult =>
          val matchKind =
            if (dbResult.name.uuid.uuidString ==
                  nameParsed.parsed.preprocessorResult.id.toString) {
              MatchKind.ExactNameMatchByUUID
            } else if (dbResult.canonicalName.isDefined &&
                     nameParsed.parsed.canonizedUuid().isDefined &&
                     dbResult.canonicalName.get.uuid.uuidString ==
                       nameParsed.parsed.canonizedUuid().get.id.toString) {
              MatchKind.ExactCanonicalNameMatchByUUID
            } else {
              MatchKind.Unknown
            }
          Result(name = dbResult.name, canonicalName = dbResult.canonicalName,
                 matchType = MatchType(matchKind))
        }
        val response = Response(total = dbResults.total,
                                results = results,
                                suppliedId = nameParsed.nameInput.suppliedId,
                                suppliedInput = nameParsed.nameInput.value.some)
        RequestResponse(request = nameParsed, response)
      }
    }
  }

  def fuzzyMatch(names: Seq[RequestResponse]): ScalaFuture[Seq[RequestResponse]] = {
    ScalaFuture.successful(names)
  }

  def resolveExact(): ScalaFuture[Seq[Response]] = {
    val exactMatchesByUuidFut =
      queryExactMatchesByUuid().flatMap { names =>
        val (exactMatchesByUuid, exactUnmatchesByUuid) =
          names.partition { resp => resp.response.results.nonEmpty }
        val (unmatched, unmatchedNotParsed) = exactUnmatchesByUuid.partition { reqResp =>
          reqResp.request.parsed.canonizedUuid().isDefined
        }

        val fuzzyMatchesFut = fuzzyMatch(unmatched)

        for (fuzzyMatches <- fuzzyMatchesFut) yield {
          val reqResps = exactMatchesByUuid ++ fuzzyMatches ++ unmatchedNotParsed
          reqResps.map { _.response }
        }
      }
    exactMatchesByUuidFut
  }
}

@Singleton
class NameResolverFactory @Inject()(database: Database,
                                    matcherClient: MatcherService.FutureIface) {

  def resolveExact(request: Request): TwitterFuture[Seq[Response]] = {
    val nameRequest = new NameResolver(request, database)
    nameRequest.resolveExact().as[TwitterFuture[Seq[Response]]]
  }

}
