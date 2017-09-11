package org.globalnames
package index
package nameresolver

import java.util.UUID
import javax.inject.{Inject, Singleton}

import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.twitter_util.UtilBijections._
import com.twitter.inject.Logging
import com.twitter.util.{Future => TwitterFuture}
import org.apache.commons.lang3.StringUtils
import MatchTypeScores._
import index.dao.{DBResult, Tables => T}
import thrift.matcher.{Service => MatcherService}
import thrift._
import thrift.nameresolver._
import thrift.{MatchKind => MK}
import util.UuidEnhanced._
import parser.ScientificNameParser.{Result => SNResult, instance => SNP}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future => ScalaFuture}
import scalaz.syntax.std.boolean._
import scalaz.syntax.std.option._
import scalaz.Lens

object NameResolver {
  type NameStringsQuery =
    Query[T.NameStrings, T.NameStringsRow, Seq]
  type NameStringWithDataSourceQuery =
    Query[(T.NameStrings, T.NameStringIndices, T.DataSources),
          (T.NameStringsRow, T.NameStringIndicesRow, T.DataSourcesRow), Seq]

  final case class NameInputParsed(nameInput: NameInput, parsed: SNResult)
  final case class RequestResponse(request: NameInputParsed, response: Response)
}

class NameResolver private[nameresolver](request: Request,
                                         database: Database,
                                         matcherClient: MatcherService.FutureIface)
  extends Logging {

  import NameResolver._

  private def logInfo(message: String): Unit = {
    logger.info(s"(Request hash code: ${request.hashCode}) $message")
  }

  private val resultScoredToMatchKindLens: (ResultScored) => ResultScored = {
    val resultScoredToResult = Lens.lensu[ResultScored, Result](
      (a, value) => a.copy(result = value), _.result
    )
    val resultToMatchType = Lens.lensu[Result, MatchType](
      (a, value) => a.copy(matchType = value), _.matchType
    )
    val matchTypeToKind = Lens.lensu[MatchType, MatchKind](
      (a, value) => a.copy(kind = value), _.kind
    )
    (resultScoredToResult >=> resultToMatchType >=> matchTypeToKind) =>= {
      case MK.ExactNameMatchByUUID | MK.ExactCanonicalNameMatchByUUID => MK.Match
      case MK.FuzzyCanonicalMatch => MK.FuzzyMatch
      case _ => MK.Unknown
    }
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
  def queryWithSurrogates(nameStringsQuery: NameStringsQuery): ScalaFuture[Seq[DBResult]] = {
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

    val queryJoin = query
      .joinLeft(T.NameStringIndices).on { case ((_, nsi_l, _), nsi_r) =>
        nsi_l.acceptedTaxonId =!= "" &&
          nsi_l.dataSourceId === nsi_r.dataSourceId && nsi_l.acceptedTaxonId === nsi_r.taxonId
      }
      .joinLeft(T.NameStrings).on { case ((_, nsi), ns) => ns.id === nsi.map { _.nameStringId } }
      .map { case (((ns, nsi, ds), nsiAccepted), nsAccepted) =>
        (ns, nsi, ds, nsAccepted, nsiAccepted)
      }

    database.run(queryJoin.result).map { rs =>
      rs.map { case (ns, nsi, ds, nsAcp, nsiAcp) => DBResult(ns, nsi, ds, nsiAcp, nsAcp) }
    }
  }

  private
  def queryExactMatchesByUuid(): ScalaFuture[Seq[RequestResponse]] = {
    val canonicalUuids = namesParsed.flatMap { _.parsed.canonizedUuid().map { _.id } }.distinct
    val nameUuids = namesParsed.map { _.parsed.preprocessorResult.id }.distinct
    logInfo(s"[Exact match] Name UUIDs: ${nameUuids.size}. Canonical UUIDs: ${canonicalUuids.size}")
    val qry = T.NameStrings.filter { ns =>
      ns.id.inSetBind(nameUuids) || ns.canonicalUuid.inSetBind(canonicalUuids)
    }
    val reqResp = queryWithSurrogates(qry).map { databaseResults =>
      logInfo(s"[Exact match] Database fetched")
      val nameUuidMatches = databaseResults.groupBy { r => r.ns.id }.withDefaultValue(Seq())
      val canonicalUuidMatches =
        databaseResults.groupBy { r => r.ns.canonicalUuid }
                       .filterKeys { key => key.isDefined && key != UuidGenerator.EmptyUuid.some }
                       .withDefaultValue(Seq())

      namesParsed.map { nameParsed =>
        val databaseMatches = {
          val nums = nameUuidMatches(nameParsed.parsed.preprocessorResult.id)
          val cums = canonicalUuidMatches(nameParsed.parsed.canonizedUuid().map { _.id })
          (nums ++ cums).distinct
        }

        def composeResult(r: DBResult) = {
          val matchKind =
            if (r.ns.id == nameParsed.parsed.preprocessorResult.id) {
              MK.ExactNameMatchByUUID
            } else if (
              (for (nsCan <- r.ns.canonicalUuid; npCan <- nameParsed.parsed.canonizedUuid())
                yield nsCan == npCan.id).getOrElse(false)) {
              MK.ExactCanonicalNameMatchByUUID
            } else {
              MK.Unknown
            }
          val dbResult = DBResult.create(r, createMatchType(matchKind, editDistance = 0))
          val score = ResultScores.compute(nameParsed.parsed, dbResult)
          ResultScored(dbResult, score)
        }

        val responseResults = databaseMatches.map { r => composeResult(r) }
        val preferredResponseResults = databaseMatches
          .filter { r => request.preferredDataSourceIds.contains(r.ds.id) }
          .map { r => composeResult(r) }

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
  def fuzzyMatch(scientificNames: Seq[String],
                 advancedResolution: Boolean): ScalaFuture[Seq[RequestResponse]] = {
    logInfo(s"[Fuzzy match] Names: ${scientificNames.size}")
    if (scientificNames.isEmpty) {
      ScalaFuture.successful(Seq())
    } else {
      matcherClient.findMatches(scientificNames, dataSourceIds, advancedResolution)
                           .as[ScalaFuture[Seq[thrift.matcher.Response]]].flatMap { fuzzyMatches =>
        val uuids =
          fuzzyMatches.flatMap { fm => fm.results.map { r => r.nameMatched.uuid: UUID } }.distinct
        logInfo("[Fuzzy match] Matcher service response received. " +
                s"Database request for ${uuids.size} records")
        val qry = T.NameStrings.filter { ns => ns.canonicalUuid.inSetBind(uuids) }
        queryWithSurrogates(qry).map { nameStringsDB =>
          logInfo(s"[Fuzzy match] Database response: ${nameStringsDB.size} records")
          val nameStringsDBMap = nameStringsDB.groupBy { r => r.ns.canonicalUuid }
                                              .withDefaultValue(Seq())

          fuzzyMatches.map { fuzzyMatch =>
            val nameInputParsed = namesParsedMap(fuzzyMatch.inputUuid)
            val results = fuzzyMatch.results.flatMap { result =>
              val canId: UUID = result.nameMatched.uuid
              nameStringsDBMap(canId.some).map { r =>
                val dbResult =
                  DBResult.create(r, createMatchType(result.matchKind, result.distance))
                val score = ResultScores.compute(nameInputParsed.parsed, dbResult)
                ResultScored(dbResult, score)
              }
            }
            val preferredResults = fuzzyMatch.results.flatMap { result =>
              val canId: UUID = result.nameMatched.uuid
              nameStringsDBMap(canId.some)
                .filter { r => request.preferredDataSourceIds.contains(r.ds.id) }
                .map { r =>
                  val dbResult =
                    DBResult.create(r, createMatchType(result.matchKind, result.distance))
                  val score = ResultScores.compute(nameInputParsed.parsed, dbResult)
                  ResultScored(dbResult, score)
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
  def computeContext(responses: Seq[Response]): Responses = {
    val context =
      responses.flatMap { response => response.results }
               .groupBy { _.result.dataSource }
               .mapValues { results =>
                 ContextFinder.find(results.flatMap { _.result.classification.path })
               }
               .toSeq.map { case (ds, path) => Context(ds, path) }
    Responses(items = responses, context = context)
  }

  private
  def transformMatchType(responses: Seq[Response]): Seq[Response] = {
    if (request.advancedResolution) {
      responses
    } else {
      for (response <- responses) yield {
        response.copy(results = response.results.map(resultScoredToMatchKindLens))
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

  def resolveExact(): ScalaFuture[Responses] = {
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
      val fuzzyMatchesFut = fuzzyMatch(namesForFuzzyMatch, request.advancedResolution)

      fuzzyMatchesFut.map { fuzzyMatches =>
        logInfo(s"[Resolution] Fuzzy matches count: ${fuzzyMatches.size}")
        val reqResps = exactMatchesByUuid ++ fuzzyMatches ++ unmatchedNotParsed
        val responses = reqResps.map { _.response }
        (transformMatchType _ andThen rearrangeResults andThen computeContext)(responses)
      }
    }
  }
}

@Singleton
class NameResolverFactory @Inject()(database: Database,
                                    matcherClient: MatcherService.FutureIface) {

  def resolveExact(request: Request): TwitterFuture[Responses] = {
    val nameRequest = new NameResolver(request, database, matcherClient)
    nameRequest.resolveExact().as[TwitterFuture[Responses]]
  }

}
