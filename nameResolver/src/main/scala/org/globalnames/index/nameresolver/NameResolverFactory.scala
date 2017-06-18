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
import thrift.nameresolver.{Name, NameInput, Request, Response, Result}
import thrift.Uuid
import parser.ScientificNameParser.{Result => SNResult, instance => SNP}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future => ScalaFuture}
import scalaz.syntax.std.boolean._

@Singleton
class NameResolverFactory @Inject()(database: Database,
                                    matcherClient: MatcherService.FutureIface) {
  private val NameStringsPerFuture = 200
  private val EmptyUuid = UuidGenerator.generate("")

  case class NameInputParsed(nameInput: NameInput, parsed: SNResult)

  class NameResolver(request: Request) {
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

    private def queryWithSurrogates(
           nameStringsQuery: Query[T.NameStrings, T.NameStringsRow, Seq]) = {
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
      nameStringsQueries: Seq[Query[T.NameStrings, T.NameStringsRow, Seq]]):
        ScalaFuture[Seq[Response]] = {

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
      val responses = resultsPerNameFut.map { resultsPerName =>
        resultsPerName.map { case (resultsMap, total) =>
          val results = resultsMap.keys.map { case (ns, nsi, ds) =>
            val canonicalNameOpt =
              for { canId <- ns.canonicalUuid; canName <- ns.canonical }
                yield Name(uuid = Uuid(canId.toString), value = canName)
            Result(name = Name(uuid = Uuid(ns.id.toString), value = ns.name),
                   canonicalName = canonicalNameOpt)
          }.toSeq
          Response(total = total, results = results)
        }
      }
      responses
    }

    private[NameResolverFactory] def queryExactMatchesByUuid(): Seq[ScalaFuture[Seq[Response]]] = {
      namesParsed
           .grouped(NameStringsPerFuture).toSeq
           .map { namesPortion =>
             val namePortionQry = namesPortion.map { name =>
               val canonicalUuid = name.parsed.canonizedUuid().map { _.id }.getOrElse(EmptyUuid)
               if (canonicalUuid == EmptyUuid) {
                 T.NameStrings.filter { ns => ns.id === name.parsed.preprocessorResult.id }
               } else {
                 exactNamesQuery(name.parsed.preprocessorResult.id, canonicalUuid)
               }
             }
             materializeNameStringsSequence(namePortionQry)
           }
    }
  }

  def resolveExact(request: Request): TwitterFuture[Seq[Response]] = {
    val nameRequest = new NameResolver(request)
    val exactMatchesByUuid =
      ScalaFuture.sequence(nameRequest.queryExactMatchesByUuid())
                 .map { _.flatten }

    val responsesFut = exactMatchesByUuid
    responsesFut.as[TwitterFuture[Seq[Response]]]
  }
}
