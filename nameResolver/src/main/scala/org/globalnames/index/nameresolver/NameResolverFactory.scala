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
import index.dao.{DBResult, Tables => T}
import thrift.matcher.{Service => MatcherService}
import thrift._
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
      .joinLeft(T.NameStringIndices).on { case ((_, nsi_l, _), nsi_r) =>
        nsi_l.acceptedTaxonId =!= "" &&
          nsi_l.dataSourceId === nsi_r.dataSourceId && nsi_l.acceptedTaxonId === nsi_r.taxonId
      }
      .joinLeft(T.NameStrings).on { case ((_, nsi), ns) => ns.id === nsi.map { _.nameStringId } }
      .map { case (((ns, nsi, ds), nsiAccepted), nsAccepted) =>
        (ns, nsi, ds, nsiAccepted, nsAccepted)
      }
  }

  private
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
        databaseResults.groupBy { case (ns, _, _, _, _) => ns.id }.withDefaultValue(Seq())
      val canonicalUuidMatches =
        databaseResults.groupBy { case (ns, _, _, _, _) => ns.canonicalUuid }
                       .filterKeys { key => key.isDefined && key != UuidGenerator.EmptyUuid.some }
                       .withDefaultValue(Seq())

      namesParsed.map { nameParsed =>
        val databaseMatches = {
          val nums = nameUuidMatches(nameParsed.parsed.preprocessorResult.id)
          val cums = canonicalUuidMatches(nameParsed.parsed.canonizedUuid().map { _.id })
          (nums ++ cums).distinct
        }

        def composeResult(ns: T.NameStringsRow,
                          nsi: T.NameStringIndicesRow,
                          ds: T.DataSourcesRow,
                          nsiAcptOpt: Option[T.NameStringIndicesRow],
                          nsAcptOpt: Option[T.NameStringsRow]) = {
          val matchKind =
            if (ns.id == nameParsed.parsed.preprocessorResult.id) {
              MatchKind.ExactNameMatchByUUID
            } else if (
              (for (nsCan <- ns.canonicalUuid; npCan <- nameParsed.parsed.canonizedUuid())
                yield nsCan == npCan.id).getOrElse(false)) {
              MatchKind.ExactCanonicalNameMatchByUUID
            } else {
              MatchKind.Unknown
            }
          val dbResult = DBResult.create(ns, nsi, ds, nsAcptOpt, nsiAcptOpt, Seq(),
                                         createMatchType(matchKind, editDistance = 0))
          val score = ResultScores.compute(nameParsed.parsed, dbResult)
          ResultScored(dbResult, score)
        }

        val responseResults = databaseMatches.map { case (ns, nsi, ds, nsiAcptOpt, nsAcptOpt) =>
          composeResult(ns, nsi, ds, nsiAcptOpt, nsAcptOpt)
        }

        val preferredResponseResults = databaseMatches
          .filter { case (_, _, ds, _, _) => request.preferredDataSourceIds.contains(ds.id) }
          .map { case (ns, nsi, ds, nsiAcpt, nsAcpt) =>
            composeResult(ns, nsi, ds, nsiAcpt, nsAcpt)
          }

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
        database.run(queryWithSurrogates(qry).result).map { nameStringsDB =>
          logInfo(s"[Fuzzy match] Database response: ${nameStringsDB.size} records")
          val nameStringsDBMap = nameStringsDB.groupBy { case (ns, _, _, _, _) =>
            ns.canonicalUuid
          }.withDefaultValue(Seq())

          fuzzyMatches.map { fuzzyMatch =>
            val nameInputParsed = namesParsedMap(fuzzyMatch.inputUuid)
            val results = fuzzyMatch.results.flatMap { result =>
              val canId: UUID = result.nameMatched.uuid
              nameStringsDBMap(canId.some).map { case (ns, nsi, ds, ansiOpt, ansOpt) =>
                val dbResult = DBResult.create(ns, nsi, ds, ansOpt, ansiOpt, Seq(),
                                               createMatchType(result.matchKind, result.distance))
                val score = ResultScores.compute(nameInputParsed.parsed, dbResult)
                ResultScored(dbResult, score)
              }
            }
            val preferredResults = fuzzyMatch.results.flatMap { result =>
              val canId: UUID = result.nameMatched.uuid
              nameStringsDBMap(canId.some)
                .filter { case (_, _, ds, _, _) => request.preferredDataSourceIds.contains(ds.id) }
                .map { case (ns, nsi, ds, ansiOpt, ansOpt) =>
                  val dbResult = DBResult.create(ns, nsi, ds, ansOpt, ansiOpt, Seq(),
                                                 createMatchType(result.matchKind, result.distance))
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
        (rearrangeResults _ andThen computeContext)(responses)
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
