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
import thrift.nameresolver._
import thrift.Uuid
import parser.ScientificNameParser.{Result => SNResult, instance => SNP}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future => ScalaFuture}
import scalaz.syntax.std.boolean._
import scalaz.syntax.std.option._

class NameResolver private[nameresolver](request: Request,
                                         database: Database,
                                         matcherClient: MatcherService.FutureIface) {
  private val NameStringsPerFuture = 200
  private val FuzzyMatchLimit = 5
  private val EmptyUuid = UuidGenerator.generate("")

  type NameStringsQuery = Query[T.NameStrings, T.NameStringsRow, Seq]
  case class NameInputParsed(nameInput: NameInput, parsed: SNResult)
  case class RequestResponse(request: NameInputParsed, response: Response)
  case class DBResult(nameString: T.NameStringsRow,
                      nameStringIndex: T.NameStringIndicesRow,
                      dataSource: T.DataSourcesRow,
                      vernaculars: Seq[(T.VernacularStringIndicesRow, T.VernacularStringsRow)])
  case class DBResults(total: Int, results: Vector[DBResult])
  case class CanonicalNameSplit(name: NameInputParsed, parts: List[String]) {
    val isOriginalCanonical: Boolean = {
      name.parsed.canonized().exists { can => can.length == parts.map(_.length + 1).sum - 1 }
    }
    def shortenParts: CanonicalNameSplit = {
      this.copy(parts = this.parts.dropRight(1))
    }
  }
  case class FuzzyResult(value: String, distance: Int, matchType: MatchType) {
    val uuid: UUID = UuidGenerator.generate(value)
  }
  case class FuzzyResults(canonicalNameSplit: CanonicalNameSplit, results: Seq[FuzzyResult])

  private val takeCount: Int = request.perPage.min(1000).max(0)
  private val dropCount: Int = (request.page * request.perPage).max(0)
  private val namesParsed: Vector[NameInputParsed] =
    request.names.toVector.map { ni =>
      val capital = capitalize(ni.value)
      NameInputParsed(nameInput = ni.copy(value = capital),
                      parsed = SNP.fromString(capital))
    }

  private def createResult(dbResult: DBResult, matchType: MatchType): Result = {
    val canonicalNameOpt =
      for { canId <- dbResult.nameString.canonicalUuid
            canName <- dbResult.nameString.canonical }
        yield Name(uuid = Uuid(canId.toString), value = canName)
    val classification = Classification(
      path = dbResult.nameStringIndex.classificationPath,
      pathIds = dbResult.nameStringIndex.classificationPathIds,
      pathRanks = dbResult.nameStringIndex.classificationPathRanks
    )
    Result(name = Name(uuid = Uuid(dbResult.nameString.id.toString),
                       value = dbResult.nameString.name),
           canonicalName = canonicalNameOpt,
           taxonId = dbResult.nameStringIndex.taxonId,
           matchType = matchType,
           classification = classification
    )
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
              .mapValues { _.flatMap { case (_, vernaculars) => vernaculars } }
        (dataGroupedWithVernaculars, total)
      }
    }

    val resultsPerNameFut = database.run(DBIO.sequence(resultsQuerySeq))
    val result = resultsPerNameFut.map { resultsPerName =>
      resultsPerName.map { case (resultsMap, total) =>
        val results = resultsMap.map { case ((ns, nsi, ds), vrns) => DBResult(ns, nsi, ds, vrns) }
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
            if (dbResult.nameString.id == nameParsed.parsed.preprocessorResult.id) {
              MatchKind.ExactNameMatchByUUID
            } else if (dbResult.nameString.canonicalUuid.isDefined &&
                       nameParsed.parsed.canonizedUuid().isDefined &&
                       dbResult.nameString.canonicalUuid.get ==
                         nameParsed.parsed.canonizedUuid().get.id) {
              MatchKind.ExactCanonicalNameMatchByUUID
            } else {
              MatchKind.Unknown
            }
          createResult(dbResult, MatchType(matchKind))
        }
        val response = Response(total = dbResults.total,
                                results = results,
                                suppliedId = nameParsed.nameInput.suppliedId,
                                suppliedInput = nameParsed.nameInput.value.some)
        RequestResponse(request = nameParsed, response)
      }
    }
  }

  def fuzzyMatchCanonicalParts(canonicalNameSplits: Seq[CanonicalNameSplit]):
        ScalaFuture[Seq[FuzzyResults]] = {
    val (originalOrNonUninomialCanonicals, partialByGenusCanonicals) =
      canonicalNameSplits.partition { canonicalNameSplit =>
        canonicalNameSplit.isOriginalCanonical || canonicalNameSplit.parts.size > 1
      }

    val partialByGenusFuzzyResults =
      partialByGenusCanonicals.map { canonicalNameSplit =>
        val fuzzyResults = canonicalNameSplit.parts.map { part =>
          FuzzyResult(value = part,
                      distance = 0,
                      matchType = MatchType(kind = MatchKind.ExactMatchPartialByGenus))
        }
        FuzzyResults(canonicalNameSplit = canonicalNameSplit,
                     results = fuzzyResults)
      }

    val originalOrNonUninomialFuzzyResultssFut = {
      val inputNames = originalOrNonUninomialCanonicals.map { _.parts.mkString(" ") }
      val fuzzyResultssFut = matcherClient.findMatches(inputNames).map { matcherResponses =>
        matcherResponses.zip(originalOrNonUninomialCanonicals).map { case (response, canNamSplit) =>
          val fuzzyResults =
            if (response.results.size > FuzzyMatchLimit) Seq()
            else {
              response.results.map { result =>
                val matchKind =
                  if (canNamSplit.isOriginalCanonical) {
                    if (result.distance == 0) MatchKind.ExactCanonicalNameMatchByUUID
                    else MatchKind.FuzzyCanonicalMatch
                  } else {
                    if (result.distance == 0) MatchKind.ExactPartialMatch
                    else MatchKind.FuzzyPartialMatch
                  }
                FuzzyResult(value = result.value,
                            distance = result.distance,
                            matchType = MatchType(kind = matchKind))
              }
            }
            FuzzyResults(canonicalNameSplit = canNamSplit,
                         results = fuzzyResults)
        }
      }
      fuzzyResultssFut
    }

    originalOrNonUninomialFuzzyResultssFut.map { _ ++ partialByGenusFuzzyResults }
                                          .as[ScalaFuture[Seq[FuzzyResults]]]
  }

  def fuzzyMatch(canonicalNameSplits: Seq[CanonicalNameSplit]):
      ScalaFuture[Seq[RequestResponse]] = {
    val (canonicalNameSplitsNonEmpty, canonicalNameSplitsEmpty) =
      canonicalNameSplits.partition { _.parts.nonEmpty }
    val emptyMatches = canonicalNameSplitsEmpty.map { cnp =>
      val response = Response(total = 0,
                              suppliedId = cnp.name.nameInput.suppliedId,
                              suppliedInput = cnp.name.nameInput.value.some)
      RequestResponse(request = cnp.name, response = response)
    }
    if (canonicalNameSplitsNonEmpty.isEmpty) {
      ScalaFuture.successful(emptyMatches)
    } else {
      fuzzyMatchCanonicalParts(canonicalNameSplitsNonEmpty).flatMap { fuzzyResultss =>
        val (fuzzyResultsNonEmpty, fuzzyResultsEmpty) =
          fuzzyResultss.partition { _.results.nonEmpty }
        val canonicalNameSplitsShorted = fuzzyResultsEmpty.map { _.canonicalNameSplit.shortenParts }

        val dbResultssFuts = fuzzyResultsNonEmpty.map { fuzzyResults =>
          val namesPortionQry = fuzzyResults.results.map { fuzzyResult =>
            T.NameStrings.filter { ns => ns.canonicalUuid === fuzzyResult.uuid }
          }
          materializeNameStringsSequence(namesPortionQry)
        }
        val requestResponsesFut = ScalaFuture.sequence(dbResultssFuts).map { dbResultsss =>
          dbResultsss.zip(fuzzyResultsNonEmpty).flatMap { case (dbResultss, fuzzyResults) =>
            dbResultss.zip(fuzzyResults.results).map { case (dbResults, fuzzyResult) =>
              val results = dbResults.results.map { dbResult =>
                createResult(dbResult, fuzzyResult.matchType)
              }
              val response =
                Response(total = dbResults.total,
                         results = results,
                         suppliedId = fuzzyResults.canonicalNameSplit.name.nameInput.suppliedId,
                         suppliedInput = fuzzyResults.canonicalNameSplit.name.nameInput.value.some)
              RequestResponse(request = fuzzyResults.canonicalNameSplit.name, response)
            }
          }
        }
        for {
          requestResponses <- requestResponsesFut
          restFuzzyResponses <- fuzzyMatch(canonicalNameSplitsShorted)
        } yield requestResponses ++ restFuzzyResponses ++ emptyMatches
      }
    }
  }

  def resolveExact(): ScalaFuture[Seq[Response]] = {
    val exactMatchesByUuidFut =
      queryExactMatchesByUuid().flatMap { names =>
        val (exactMatchesByUuid, exactUnmatchesByUuid) =
          names.partition { resp => resp.response.results.nonEmpty }
        val (unmatched, unmatchedNotParsed) = exactUnmatchesByUuid.partition { reqResp =>
          reqResp.request.parsed.canonizedUuid().isDefined
        }

        val canonicalNameSplits = unmatched.map { reqResp =>
          CanonicalNameSplit(name = reqResp.request,
                             parts = reqResp.request.nameInput.value.split(' ').toList)
        }
        val fuzzyMatchesFut = fuzzyMatch(canonicalNameSplits)

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
    val nameRequest = new NameResolver(request, database, matcherClient)
    nameRequest.resolveExact().as[TwitterFuture[Seq[Response]]]
  }

}
