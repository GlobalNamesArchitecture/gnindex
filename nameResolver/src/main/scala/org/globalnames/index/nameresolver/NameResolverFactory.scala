package org.globalnames
package index
package nameresolver

import java.util.UUID
import javax.inject.{Inject, Singleton}

import akka.http.impl.util._
import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.twitter_util.UtilBijections._
import com.twitter.inject.Logging
import com.twitter.util.{Future => TwitterFuture}
import org.apache.commons.lang3.StringUtils
import MatchTypeScores._
import dao.{Tables => T}
import thrift.matcher.{Service => MatcherService}
import thrift.nameresolver._
import thrift.{MatchKind, MatchType, Name, CanonicalName}
import util.UuidEnhanced._
import parser.ScientificNameParser.{Result => SNResult, instance => SNP}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future => ScalaFuture}
import scalaz.syntax.std.boolean._
import scalaz.syntax.std.option._

object NameResolver {
  type NameStringsQuery =
    Query[T.NameStrings, T.NameStringsRow, Seq]
  type NameStringWithDataSourceQuery =
    Query[(T.NameStrings, T.NameStringIndices, T.DataSources),
          (T.NameStringsRow, T.NameStringIndicesRow, T.DataSourcesRow), Seq]

  case class NameInputParsed(nameInput: NameInput, parsed: SNResult)
  case class RequestResponse(request: NameInputParsed, response: Response)
  case class DBResult(nameString: T.NameStringsRow,
                      nameStringIndex: T.NameStringIndicesRow,
                      dataSource: T.DataSourcesRow,
                      vernaculars: Seq[(T.VernacularStringIndicesRow, T.VernacularStringsRow)])

  private def createResult(nameInputParsed: NameInputParsed,
                           dbResult: DBResult,
                           matchType: MatchType): ResultScored = {
    val canonicalNameOpt =
      for { canId <- dbResult.nameString.canonicalUuid
            canNameValue <- dbResult.nameString.canonical
            canNameValueRanked <- nameInputParsed.parsed.canonized(true) }
        yield CanonicalName(uuid = canId, value = canNameValue, valueRanked = canNameValueRanked)

    val classification = Classification(
      path = dbResult.nameStringIndex.classificationPath,
      pathIds = dbResult.nameStringIndex.classificationPathIds,
      pathRanks = dbResult.nameStringIndex.classificationPathRanks
    )

    val synonym = {
      val classificationPathIdsSeq =
        dbResult.nameStringIndex.classificationPathIds.map { _.fastSplit('|') }.getOrElse(List())
      if (classificationPathIdsSeq.nonEmpty) {
        dbResult.nameStringIndex.taxonId != classificationPathIdsSeq.last
      } else if (dbResult.nameStringIndex.acceptedTaxonId.isDefined) {
        dbResult.nameStringIndex.taxonId != dbResult.nameStringIndex.acceptedTaxonId.get
      } else true
    }

    val dataSource = DataSource(id = dbResult.dataSource.id,
                                title = dbResult.dataSource.title)

    val result =
      Result(name = Name(uuid = dbResult.nameString.id, value = dbResult.nameString.name),
             canonicalName = canonicalNameOpt,
             synonym = synonym,
             taxonId = dbResult.nameStringIndex.taxonId,
             matchType = matchType,
             classification = classification,
             dataSource = dataSource
      )
    val score = ResultScores.compute(nameInputParsed.parsed, result)
    ResultScored(result, score)
  }
}

class NameResolver private[nameresolver](request: Request,
                                         database: Database,
                                         matcherClient: MatcherService.FutureIface)
  extends Logging {

  import NameResolver._

  private def logInfo(message: String): Unit = {
    logger.info(s"(Request hash code: ${request.hashCode}) $message")
  }

  private val dataSourceIds: Seq[Int] =
    (request.preferredDataSourceIds ++ request.dataSourceIds).distinct
  private val takeCount: Int = request.perPage.min(1000).max(0)
  private val dropCount: Int = (request.page * request.perPage).max(0)
  private val namesParsed: Vector[NameInputParsed] =
    request.names.toVector.map { ni =>
      val capital = StringUtils.capitalize(ni.value)
      NameInputParsed(nameInput = ni.copy(value = capital,
                                          suppliedId = ni.suppliedId.map { _.trim }),
                      parsed = SNP.fromString(capital))
    }
  private val namesParsedMap: Map[UUID, NameInputParsed] =
    namesParsed.map { np => np.parsed.preprocessorResult.id -> np }.toMap

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
        dataSourceIds.isEmpty ? ds | ds.filter { ds => ds.id.inSetBind(dataSourceIds) }
      }
    } yield (ns, nsi, ds)

    query
  }

  def queryExactMatchesByUuid(): ScalaFuture[Seq[RequestResponse]] = {
    val canonicalUuids = namesParsed.flatMap { _.parsed.canonizedUuid().map { _.id } }.distinct
    val nameUuids = namesParsed.map { _.parsed.preprocessorResult.id }.distinct
    logInfo(s"[Exact match] Name UUIDs: ${nameUuids.size}. Canonical UUIDs: ${canonicalUuids.size}")
    val qry = queryWithSurrogates(T.NameStrings.filter { ns =>
      ns.id.inSetBind(nameUuids) || ns.canonicalUuid.inSetBind(canonicalUuids)
    })
    val reqResp = database.run(qry.result).map { databaseResults =>
      logInfo(s"[Exact match] Database fetched")
      val nameUuidMatches =
        databaseResults.groupBy { case (ns, _, _) => ns.id }.withDefaultValue(Seq())
      val canonicalUuidMatches =
        databaseResults.groupBy { case (ns, _, _) => ns.canonicalUuid }
                       .filterKeys { key => key.isDefined && key.get != UuidGenerator.EmptyUuid }
                       .withDefaultValue(Seq())

      namesParsed.map { nameParsed =>
        val databaseMatches = {
          val nums = nameUuidMatches(nameParsed.parsed.preprocessorResult.id)
          val cums = canonicalUuidMatches(nameParsed.parsed.canonizedUuid().map { _.id })
          (nums ++ cums).distinct
        }

        def composeResult(ns: T.NameStringsRow,
                          nsi: T.NameStringIndicesRow,
                          ds: T.DataSourcesRow) = {
          val matchKind =
            if (ns.id == nameParsed.parsed.preprocessorResult.id) {
              MatchKind.ExactNameMatchByUUID
            } else if (ns.canonicalUuid.isDefined &&
              nameParsed.parsed.canonizedUuid().isDefined &&
              ns.canonicalUuid.get == nameParsed.parsed.canonizedUuid().get.id) {
              MatchKind.ExactCanonicalNameMatchByUUID
            } else {
              MatchKind.Unknown
            }
          val dbResult = DBResult(ns, nsi, ds, Seq())
          createResult(nameParsed, dbResult, createMatchType(matchKind, editDistance = 0))
        }

        val responseResults = databaseMatches
          .map { case (ns, nsi, ds) => composeResult(ns, nsi, ds) }

        val preferredResponseResults = databaseMatches
          .filter { case (_, _, ds) => request.preferredDataSourceIds.contains(ds.id) }
          .map { case (ns, nsi, ds) => composeResult(ns, nsi, ds) }

        val response = Response(
          total = responseResults.size,
          results = responseResults,
          preferredResults = preferredResponseResults,
          suppliedId = nameParsed.nameInput.suppliedId,
          suppliedInput = nameParsed.nameInput.value.some
        )
        RequestResponse(nameParsed, response = response)
      }
    }
    reqResp
  }

  private
  def fuzzyMatch(scientificNames: Seq[String]): ScalaFuture[Seq[RequestResponse]] = {
    logInfo(s"[Fuzzy match] Names: ${scientificNames.size}")
    if (scientificNames.isEmpty) {
      ScalaFuture.successful(Seq())
    } else {
      matcherClient.findMatches(scientificNames, dataSourceIds)
                           .as[ScalaFuture[Seq[thrift.matcher.Response]]].flatMap { fuzzyMatches =>
        val uuids = fuzzyMatches.flatMap { fm => fm.results.map { r => r.nameMatched.uuid: UUID } }
        logInfo("[Fuzzy match] Matcher service response received. " +
                s"Database request for ${uuids.size} records")
        val qry = T.NameStrings.filter { ns => ns.canonicalUuid.inSetBind(uuids) }
        database.run(queryWithSurrogates(qry).result).map { nameStringsDB =>
          logInfo(s"[Fuzzy match] Database response: ${nameStringsDB.size} records")
          val nameStringsDBMap = nameStringsDB.map { case (ns, nsi, ds) =>
            DBResult(ns, nsi, ds, Seq())
          }.groupBy { dbr => dbr.nameString.canonicalUuid }.withDefaultValue(Seq())

          fuzzyMatches.map { fuzzyMatch =>
            val nameInputParsed = namesParsedMap(fuzzyMatch.inputUuid)
            val results = fuzzyMatch.results.flatMap { result =>
              val canId: UUID = result.nameMatched.uuid
              nameStringsDBMap(canId.some).map {
                dbRes => createResult(nameInputParsed, dbRes,
                                      createMatchType(result.matchKind, result.distance))
              }
            }
            val preferredResults = fuzzyMatch.results.flatMap { result =>
              val canId: UUID = result.nameMatched.uuid
              nameStringsDBMap(canId.some)
                .filter { resp => request.preferredDataSourceIds.contains(resp.dataSource.id) }
                .map { dbRes =>
                  createResult(nameInputParsed, dbRes,
                               createMatchType(result.matchKind, result.distance))
                }
              }
            val response = Response(
              total = fuzzyMatch.results.size,
              results = results,
              preferredResults = preferredResults,
              suppliedId = nameInputParsed.nameInput.suppliedId,
              suppliedInput = nameInputParsed.nameInput.value.some
            )
            RequestResponse(request = nameInputParsed, response = response)
          }
        }
      }
    }
  }

  private
  def rearrangeResults(responses: Seq[Response]): Seq[Response] = {
    responses.map { response =>
      val results = response.results.sortBy { _.score.value.getOrElse(0.0) }
      response.copy(results = results)
    }
  }

  def resolveExact(): ScalaFuture[Seq[Response]] = {
    logInfo(s"[Resolution] Resolution started for ${request.names.size} names")
    queryExactMatchesByUuid().flatMap { names =>
      val (exactMatchesByUuid, exactUnmatchesByUuid) =
        names.partition { resp => resp.response.results.nonEmpty }
      val (unmatched, unmatchedNotParsed) = exactUnmatchesByUuid.partition { reqResp =>
        reqResp.request.parsed.canonizedUuid().isDefined
      }
      logInfo(s"[Resolution] Exact match done. Matches count: ${exactMatchesByUuid.size}. " +
              s"Unparsed count: ${unmatchedNotParsed.size}")

      val namesForFuzzyMatch = unmatched.map { reqResp => reqResp.request.nameInput.value }
      val fuzzyMatchesFut = fuzzyMatch(namesForFuzzyMatch)

      fuzzyMatchesFut.map { fuzzyMatches =>
        logInfo(s"[Resolution] Fuzzy matches count: ${fuzzyMatches.size}")
        val reqResps = exactMatchesByUuid ++ fuzzyMatches ++ unmatchedNotParsed
        val responses = reqResps.map { _.response }
        rearrangeResults(responses)
      }
    }
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
