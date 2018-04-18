package org.globalnames
package index
package nameresolver

import java.util.UUID

import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.twitter_util.UtilBijections._
import com.twitter.inject.Logging
import com.twitter.util.{Future => TwitterFuture}
import MatchTypeScores._
import index.dao.{DBResultObj, Tables => T}
import index.dao.Projections._
import index.{thrift => t}
import thrift.{nameresolver => nr, matcher => m, MatchKind => MK}
import util.UuidEnhanced._
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future => ScalaFuture}
import scalaz.syntax.std.boolean._
import scalaz.syntax.std.option._
import scalaz.Lens

object NameResolver {
  type NameStringsQuery = Query[T.NameStrings, T.NameStringsRow, Seq]

  final case class RequestResponse(total: Int,
                                   request: NameInputParsed,
                                   results: Seq[nr.ResultScored],
                                   preferredResults: Seq[nr.ResultScored]) {
    val response: nr.Response = {
      val rsnss =
        results
          .groupBy { r => r.result.name.uuid }
            .values.flatMap { results =>
            val rpdss =
              for ((ds, vs) <- results.groupBy { r => r.result.dataSource }) yield {
                nr.ResultScoredPerDataSource(
                  dataSource = ds,
                  results = vs
                )
              }
            for (response <- results.headOption) yield {
              nr.ResultScoredNameString(
                name = response.result.name,
                canonicalName = response.result.canonicalName,
                results = rpdss.toVector
                  .sortBy { rpds => rpds.dataSource }(util.DataSource.ordering.reverse)
              )
            }
          }
          .toVector
          .sortBy { r => r.name.value }

      nr.Response(total = total,
        suppliedInput = request.nameInput.value,
        suppliedId = request.nameInput.suppliedId,
        results = rsnss,
        preferredResults = preferredResults
      )
    }
  }

  private val resultScoredToMatchKindLens: nr.ResultScored => nr.ResultScored = {
    val resultScoredToResult = Lens.lensu[nr.ResultScored, thrift.Result](
      (a, value) => a.copy(result = value), _.result
    )
    val resultToMatchType = Lens.lensu[t.Result, t.MatchType](
      (a, value) => a.copy(matchType = value), _.matchType
    )
    val matchTypeToKind = Lens.lensu[t.MatchType, t.MatchKind](
      (a, value) => a.copy(kind = value), _.kind
    )
    (resultScoredToResult >=> resultToMatchType >=> matchTypeToKind) =>= {
      case MK.ExactNameMatchByUUID | MK.ExactCanonicalNameMatchByUUID => MK.Match
      case MK.FuzzyCanonicalMatch => MK.FuzzyMatch
      case _ => MK.Unknown
    }
  }
}

class NameResolver(request: nr.Request)
                  (implicit database: Database,
                            matcherClient: m.Service.FutureIface)
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
    request.names.toVector.map { ni => NameInputParsed(ni) }
  private val namesParsedMap: Map[UUID, NameInputParsed] =
    namesParsed.map { np => np.parsed.preprocessorResult.id -> np }.toMap

  private def exactNamesQuery(nameUuid: Rep[UUID], canonicalNameUuid: Rep[UUID]) = {
    T.NameStrings.filter { ns => ns.id === nameUuid || ns.canonicalUuid === canonicalNameUuid }
  }

  private def exactNamesQueryWildcard(query: Rep[String]) = {
    T.NameStrings.filter { ns => ns.name.like(query) || ns.canonical.like(query) }
  }

  private
  def queryWithSurrogates(nameStringsQuery: NameStringsQuery): ScalaFuture[Seq[ResultDB]] = {
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
      .joinLeft(T.NameStrings).on { case ((_, nsi), ns) =>
        ns.id === nsi.map { _.nameStringId }
      }
      .map { case (((ns, nsi, ds), nsiAccepted), nsAccepted) =>
        DBResultObj.project(ns, nsi, ds, nsAccepted, nsiAccepted)
      }

    database.run(queryJoin.result)
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

        def composeResult(r: ResultDB) = {
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
          val dbResult = DBResultObj.create(r, createMatchType(matchKind, editDistance = 0))
          val score = ResultScores(nameParsed, dbResult).compute
          nr.ResultScored(dbResult, score)
        }

        val responseResults = databaseMatches.map { r => composeResult(r) }
        val preferredResponseResults = databaseMatches
          .filter { r => request.preferredDataSourceIds.contains(r.ds.id) }
          .map { r => composeResult(r) }

        RequestResponse(total = responseResults.size,
                        request = nameParsed,
                        results = responseResults,
                        preferredResults = preferredResponseResults)
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
                  DBResultObj.create(r, createMatchType(result.matchKind, result.distance))
                val score = ResultScores(nameInputParsed, dbResult).compute
                nr.ResultScored(dbResult, score)
              }
            }
            val preferredResults = fuzzyMatch.results.flatMap { result =>
              val canId: UUID = result.nameMatched.uuid
              nameStringsDBMap(canId.some)
                .filter { r => request.preferredDataSourceIds.contains(r.ds.id) }
                .map { r =>
                  val dbResult =
                    DBResultObj.create(r, createMatchType(result.matchKind, result.distance))
                  val score = ResultScores(nameInputParsed, dbResult).compute
                  nr.ResultScored(dbResult, score)
                }
              }
            RequestResponse(total = fuzzyMatch.results.size,
                            request = nameInputParsed,
                            results = results,
                            preferredResults = preferredResults)
          }
        }
      }
    }
  }

  private
  def computeContext(responses: Seq[RequestResponse]): nr.Responses = {
    val context =
      responses.flatMap { response => response.results }
               .groupBy { _.result.dataSource }
               .mapValues { results =>
                 ContextFinder.find(results.flatMap { _.result.classification.path })
               }
               .toSeq.map { case (ds, path) => t.Context(ds, path) }
    nr.Responses(items = responses.map { _.response },
                 context = context)
  }

  private
  def transformMatchType(responses: Seq[RequestResponse]): Seq[RequestResponse] = {
    if (request.advancedResolution) {
      responses
    } else {
      for (response <- responses) yield {
        response.copy(results = response.results.map(resultScoredToMatchKindLens))
      }
    }
  }

  private implicit val resultScoredOrdering: Ordering[nr.ResultScored] =
    new Ordering[nr.ResultScored] {
      override def compare(x: nr.ResultScored, y: nr.ResultScored): Int = {
        if (x.result.dataSource.quality == y.result.dataSource.quality) {
          if (x.score.value == y.score.value) {
            Ordering.Int.compare(x.result.dataSource.recordCount, y.result.dataSource.recordCount)
          } else {
            Ordering.Option[Double].compare(y.score.value, x.score.value)
          }
        } else {
          Ordering.Int.compare(x.result.dataSource.quality.value, y.result.dataSource.quality.value)
        }
      }
    }

  private
  def rearrangeResults(responses: Seq[RequestResponse]): Seq[RequestResponse] =
    responses.map { response =>
      val results =
        request.bestMatchOnly ?
          { response.results.nonEmpty ? Seq(response.results.min) | Seq() } |
          response.results.sorted
      val preferredResultsSorted = response.preferredResults.sorted
      val preferredResults =
        if (request.bestMatchOnly) {
          for {
            pdsId <- request.preferredDataSourceIds
            rs <- preferredResultsSorted.find { _.result.dataSource.id == pdsId }
          } yield rs
        } else {
          preferredResultsSorted
        }
      response.copy(results = results.slice(request.perPage * request.page,
                                            request.perPage * (request.page + 1)),
                    preferredResults = preferredResults)
    }

  def resolveExact(): TwitterFuture[nr.Responses] = {
    logInfo(s"[Resolution] Resolution started for ${request.names.size} names")
    val resultFuture = queryExactMatchesByUuid().flatMap { names =>
      val (exactMatchesByUuid, exactUnmatchesByUuid) =
        names.partition { resp => resp.results.nonEmpty }
      val (unmatched, unmatchedNotParsed) = exactUnmatchesByUuid.partition { reqResp =>
        reqResp.request.parsed.canonizedUuid().isDefined
      }
      logInfo(s"[Resolution] Exact match done. Matches count: ${exactMatchesByUuid.size}. " +
              s"Unparsed count: ${unmatchedNotParsed.size}")

      val namesForFuzzyMatch = unmatched.map { reqResp => reqResp.request.valueCapitalised }
      val fuzzyMatchesFut = fuzzyMatch(namesForFuzzyMatch, request.advancedResolution)

      fuzzyMatchesFut.map { fuzzyMatches =>
        logInfo(s"[Resolution] Fuzzy matches count: ${fuzzyMatches.size}")
        val reqResps = exactMatchesByUuid ++ fuzzyMatches ++ unmatchedNotParsed
        (transformMatchType _ andThen rearrangeResults andThen computeContext)(reqResps)
      }
    }
    resultFuture.as[TwitterFuture[nr.Responses]]
  }
}
