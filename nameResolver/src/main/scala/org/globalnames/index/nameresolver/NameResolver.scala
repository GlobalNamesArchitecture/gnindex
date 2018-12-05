package org.globalnames
package index
package nameresolver

import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.twitter_util.UtilBijections._
import com.twitter.inject.Logging
import com.twitter.util.{Future => TwitterFuture}
import MatchTypeScores._
import index.dao.{DBResultObj, Tables => T}
import index.dao.Projections._
import index.{thrift => t}
import org.globalnames.index.thrift.nameresolver.ResultScored
import thrift.{Context, MatchKind => MK, matcher => m, nameresolver => nr}
import util.{DataSource, UuidEnhanced}
import util.UuidEnhanced._
import slick.jdbc.PostgresProfile.api._
import java.util.UUID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future => ScalaFuture}
import scalaz.syntax.std.boolean._
import scalaz.syntax.std.option._

object NameResolver {
  type NameStringsQuery = Query[T.NameStrings, T.NameStringsRow, Seq]

  final case class ResultVernacular(resultDB: ResultDB,
                                    total: Int,
                                    vernaculars: Seq[Vernacular])

  final case class ResultScoredByNameString(
    name: t.Name,
    canonicalName: Option[t.CanonicalName],
    datasourceBestQuality: t.DataSourceQuality,
    resultsScored: Vector[ResultScored])

  final case class RequestResponse(total: Int,
                                   request: NameInputParsed,
                                   results: Seq[nr.ResultScored],
                                   preferredResults: Seq[nr.ResultScored]) {
    val resultsByNameString: Vector[ResultScoredByNameString] = {
      val rsnss: Vector[ResultScoredByNameString] =
        results
          .groupBy { r => r.result.name.uuid }.values
          .flatMap { results =>
            val resultsVec = results.toVector

            val datasourceBestQuality =
              if (resultsVec.isEmpty) {
                t.DataSourceQuality.Unknown
              } else {
                resultsVec.map { r => r.result.dataSource }.max(util.DataSource.ordering).quality
              }

            for (response <- results.headOption) yield {
              ResultScoredByNameString(
                name = response.result.name,
                canonicalName = response.result.canonicalName,
                datasourceBestQuality = datasourceBestQuality,
                resultsScored = resultsVec
              )
            }
          }
          .toVector
      rsnss
    }
  }

  private[nameresolver] val scoreOrdering: Ordering[t.Score] = new Ordering[t.Score] {
    private val epsilon = 1e-6
    private val doubleOrdering: Ordering[Double] = new Ordering[Double] {
      override def compare(x: Double, y: Double): Int = {
        if (Math.abs(x - y) < epsilon) 0
        else Ordering.Double.compare(x, y)
      }
    }

    override def compare(s1: t.Score, s2: t.Score): Int = {
      Ordering.Option[Double](doubleOrdering).compare(s1.value, s2.value)
    }
  }

  private[nameresolver] val resultScoredOrdering: Ordering[nr.ResultScored] =
    new Ordering[nr.ResultScored] {
      override def compare(x: nr.ResultScored, y: nr.ResultScored): Int = {
        val dataSourceCompare =
          DataSource.ordering.compare(x.result.dataSource, y.result.dataSource)

        if (dataSourceCompare == 0) {
          val scoreOrderingCompare = scoreOrdering.compare(x.score, y.score)
          if (scoreOrderingCompare == 0) {
            Ordering.Int.reverse.compare(x.result.dataSource.recordCount,
                                         y.result.dataSource.recordCount)
          } else {
            Ordering.Option[Double].compare(x.score.value, y.score.value)
          }
        } else {
          dataSourceCompare
        }
      }
    }
}

class NameResolver(request: nr.Request)
                  (implicit database: Database,
                            matcherClient: m.Service.MethodPerEndpoint)
  extends Logging {

  import NameResolver._

  private def logInfo(message: String): Unit = {
    logger.info(s"(Request hash code: ${request.hashCode}) $message")
  }

  private val dataSourceIds: Seq[Int] =
    request.dataSourceIds.nonEmpty ?
      (request.preferredDataSourceIds ++ request.dataSourceIds).distinct | Seq()
  private val takeCount: Int = request.perPage.min(1000).max(0)
  private val dropCount: Int = (request.page * request.perPage).max(0)
  private val namesParsed: Vector[NameInputParsed] =
    request.nameInputs
           .withFilter { ni => ni.value.nonEmpty }
           .map { ni => NameInputParsed(ni) }
           .toVector
  private val namesParsedMap: Map[UUID, NameInputParsed] =
    namesParsed.map { np => np.parsed.preprocessorResult.id -> np }.toMap

  private def exactNamesQuery(nameUuid: Rep[UUID], canonicalNameUuid: Rep[UUID]) = {
    T.NameStrings.filter { ns => ns.id === nameUuid || ns.canonicalUuid === canonicalNameUuid }
  }

  private def exactNamesQueryWildcard(query: Rep[String]) = {
    T.NameStrings.filter { ns => ns.name.like(query) || ns.canonical.like(query) }
  }

  private def vernacularsFetch(portion: Seq[ResultDB]): ScalaFuture[Seq[Seq[Vernacular]]] = {
    val vernaculars =
      if (request.withVernaculars) {
        val qrys = portion.map { resDb =>
          val qry = for {
            vsi <- T.VernacularStringIndices.filter { vsi =>
              vsi.dataSourceId === resDb.ds.id && vsi.taxonId === resDb.nsi.taxonId
            }
            vs <- T.VernacularStrings.filter { vs => vs.id === vsi.vernacularStringId }
          } yield DBResultObj.projectVernacular(vs, vsi)
          qry.result
        }
        database.run(DBIO.sequence(qrys))
      } else {
        ScalaFuture.successful(Seq.fill(portion.size)(Seq()))
      }
    vernaculars
  }

  private def queryWithSurrogates(nameStringsQuery: NameStringsQuery):
      ScalaFuture[Seq[ResultVernacular]] = {
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

    val queryCut = query.take(30000)

    val queryJoin = queryCut
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

    val portionFut = database.run(queryJoin.result)
    val countFut = database.run(query.length.result)
    val result =
      for {
        portion <- portionFut
        count <- countFut
        vernaculars <- vernacularsFetch(portion)
      } yield {
        for ((p, vs) <- portion.zip(vernaculars)) yield {
          ResultVernacular(
            resultDB = p,
            total = count,
            vernaculars = vs
          )
        }
      }
    result
  }

  private def queryExactMatchesByUuid(): ScalaFuture[Seq[RequestResponse]] = {
    val canonicalUuids = namesParsed.flatMap { _.parsed.canonizedUuid().map { _.id } }.distinct
    val nameUuids = namesParsed.map { _.parsed.preprocessorResult.id }.distinct
    logInfo(s"[Exact match] Name UUIDs: ${nameUuids.size}. Canonical UUIDs: ${canonicalUuids.size}")
    val qry = T.NameStrings.filter { ns =>
      ns.id.inSetBind(nameUuids) || ns.canonicalUuid.inSetBind(canonicalUuids)
    }
    val reqResp = queryWithSurrogates(qry).map { databaseResults =>
      logInfo(s"[Exact match] Database fetched")
      val nameUuidMatches =
        databaseResults.groupBy { r => r.resultDB.ns.id }.withDefaultValue(Seq())
      val canonicalUuidMatches =
        databaseResults.groupBy { r => r.resultDB.ns.canonicalUuid }
                       .filterKeys { key => key.isDefined && key != UuidGenerator.EmptyUuid.some }
                       .withDefaultValue(Seq())

      namesParsed.map { nameParsed =>
        val databaseMatches = {
          val nums = nameUuidMatches(nameParsed.parsed.preprocessorResult.id)
          val cums = canonicalUuidMatches(nameParsed.parsed.canonizedUuid().map { _.id })
          (nums ++ cums).distinct
        }

        def composeResult(rv: ResultVernacular): ResultScored = {
          val r = rv.resultDB
          val matchKind: MK =
            if (r.ns.id == nameParsed.parsed.preprocessorResult.id) {
              MK.ExactMatch(thrift.ExactMatch())
            } else if (
              (for (nsCan <- r.ns.canonicalUuid; npCan <- nameParsed.parsed.canonizedUuid())
                yield nsCan == npCan.id).getOrElse(false)) {
              MK.CanonicalMatch(thrift.CanonicalMatch())
            } else {
              MK.Unknown(thrift.Unknown())
            }
          val dbResult =
            DBResultObj.create(
              dbRes = r,
              vernaculars = rv.vernaculars,
              matchType = createMatchType(matchKind, request.advancedResolution)
            )
          val score = ResultScores(nameParsed, dbResult).compute
          nr.ResultScored(dbResult, score)
        }

        val responseResults =
          for {
            res <- databaseMatches
            if dataSourceIds.isEmpty || dataSourceIds.contains(res.resultDB.ds.id)
          } yield composeResult(res)
        val preferredResponseResults =
          for {
            result <- databaseMatches
            if request.preferredDataSourceIds.contains(result.resultDB.ds.id)
          }
          yield composeResult(result)

        RequestResponse(total = responseResults.size,
                        request = nameParsed,
                        results = responseResults,
                        preferredResults = preferredResponseResults)
      }
    }
    reqResp
  }

  private def fuzzyMatch(scientificNames: Seq[String],
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
          val nameStringsDBMap = nameStringsDB.groupBy { r => r.resultDB.ns.canonicalUuid }
                                              .withDefaultValue(Seq())

          fuzzyMatches.map { fuzzyMatch =>
            val nameInputParsed = namesParsedMap(fuzzyMatch.inputUuid)
            val results =
              for {
                fuzzyResult <- fuzzyMatch.results
                canId = UuidEnhanced.thriftUuid2javaUuid(fuzzyResult.nameMatched.uuid)
                result <- nameStringsDBMap(canId.some)
                if dataSourceIds.isEmpty || dataSourceIds.contains(result.resultDB.ds.id)
                dbResult = DBResultObj.create(
                  dbRes = result.resultDB,
                  vernaculars = result.vernaculars,
                  matchType = createMatchType(fuzzyResult.matchKind, request.advancedResolution)
                )
                score = ResultScores(nameInputParsed, dbResult).compute
              } yield {
                nr.ResultScored(dbResult, score)
              }
            val preferredResults =
              for {
                fuzzyResult <- fuzzyMatch.results
                canId = UuidEnhanced.thriftUuid2javaUuid(fuzzyResult.nameMatched.uuid)
                result <- nameStringsDBMap(canId.some)
                if request.preferredDataSourceIds.contains(result.resultDB.ds.id)
                dbResult = DBResultObj.create(
                  dbRes = result.resultDB,
                  vernaculars = result.vernaculars,
                  matchType = createMatchType(fuzzyResult.matchKind, request.advancedResolution)
                )
                score = ResultScores(nameInputParsed, dbResult).compute
              } yield {
                nr.ResultScored(dbResult, score)
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

  private def computeContext(responses: Seq[RequestResponse]): Vector[Context] = {
    val contexts =
      responses.flatMap { response => response.results }
               .groupBy { _.result.dataSource }
               .mapValues { results =>
                 ContextFinder.find(results.flatMap { _.result.classification.path })
               }
               .filter { case (_, path) => !(path == null || path.isEmpty) }
               .toVector.map { case (ds, path) => t.Context(ds, path) }
    contexts
  }

  private def computeResponses(responses: Seq[RequestResponse]): Seq[nr.Response] = {
    val responsesGrouped = responses.groupBy { rr => rr.request }.map { case (nip, rrs) =>
      val rrsNew = RequestResponse(total = rrs.map { _.total }.sum,
                                   request = rrs.head.request,
                                   results = rrs.flatMap { _.results },
                                   preferredResults = rrs.flatMap { _.preferredResults})
      nip.nameInput -> rrsNew
    }

    val resps =
      for (nameInput <- request.nameInputs) yield {
        val reqResp = responsesGrouped(nameInput)

        val results = {
          val dataSourceIdsSet = request.dataSourceIds.toSet
          reqResp.results.filter { rs =>
            dataSourceIdsSet.isEmpty || dataSourceIdsSet.contains(rs.result.dataSource.id)
          }
        }

        val preferredResults = {
          val preferredDataSourceIdsSet = request.preferredDataSourceIds.toSet
          val preferredResultsSorted =
            reqResp.results.filter { rs =>
              preferredDataSourceIdsSet.contains(rs.result.dataSource.id)
            }.sorted(resultScoredOrdering.reverse)

          if (request.bestMatchOnly) {
            for {
              pdsId <- request.preferredDataSourceIds
              rs <- preferredResultsSorted.find { _.result.dataSource.id == pdsId }
            } yield rs
          } else {
            preferredResultsSorted.slice(dropCount, dropCount + takeCount)
          }
        }

        val datasourceBestQuality =
          if (results.isEmpty) {
            t.DataSourceQuality.Unknown
          } else {
            results
              .maxBy { _.result.dataSource }(util.DataSource.ordering)
              .result.dataSource.quality
          }

        val matchedDataSources = results.map { _.result.dataSource.id }.distinct.size

        val resultsBestMatchApplied =
          if (request.bestMatchOnly) {
             results.nonEmpty ? Seq(results.max(resultScoredOrdering)) | Seq()
          } else {
            results.sorted(resultScoredOrdering.reverse).slice(dropCount, dropCount + takeCount)
          }

        nr.Response(
          total = resultsBestMatchApplied.size,
          suppliedInput = reqResp.request.nameInput.value,
          suppliedId = reqResp.request.nameInput.suppliedId,
          resultsScored = resultsBestMatchApplied,
          datasourceBestQuality = datasourceBestQuality,
          preferredResultsScored = preferredResults,
          matchedDataSources = matchedDataSources
        )
      }
    resps
  }

  def resolveExact(): TwitterFuture[nr.Responses] = {
    logInfo(s"[Resolution] Resolution started for ${request.nameInputs.size} names")
    val resultFuture = queryExactMatchesByUuid().flatMap { names =>
      val (exactMatchesByUuid, exactUnmatchesByUuid) =
        names.partition { resp => resp.results.nonEmpty }

      val dirtyExactMatchesByUuid =
        exactMatchesByUuid.filter { reqResp =>
          val resultScoredNameStrings = reqResp.resultsByNameString
          if (resultScoredNameStrings.isEmpty) {
            false
          } else {
            val dsq = resultScoredNameStrings.map { _.datasourceBestQuality }.minBy { _.value }
            dsq.value == t.DataSourceQuality.Unknown.value &&
              reqResp.request.parsed.canonizedUuid().isDefined
          }
        }
      val dirtyExactMatchesPromotedToCuratedFuzzyFut =
        fuzzyMatch(dirtyExactMatchesByUuid.map { _.request.valueCapitalised },
                   request.advancedResolution)
          .map { reqResps =>
            reqResps.filter { reqResp =>
              val resultScoredNameStrings = reqResp.resultsByNameString
              if (resultScoredNameStrings.isEmpty) {
                false
              } else {
                val r = resultScoredNameStrings.minBy { _.datasourceBestQuality.value }
                                               .datasourceBestQuality
                r.value == t.DataSourceQuality.Curated.value ||
                  r.value == t.DataSourceQuality.AutoCurated.value
              }
            }
          }

      val (unmatched, unmatchedNotParsed) =
        exactUnmatchesByUuid.partition { reqResp =>
          reqResp.request.parsed.canonizedUuid().isDefined
        }
      logInfo(s"[Resolution] Exact match done. Matches count: ${exactMatchesByUuid.size}. " +
              s"Unparsed count: ${unmatchedNotParsed.size}")

      val namesForFuzzyMatch = unmatched.map { reqResp => reqResp.request.valueCapitalised }
      val fuzzyMatchesFut = fuzzyMatch(namesForFuzzyMatch, request.advancedResolution)

      for {
        fuzzyMatches <- fuzzyMatchesFut
        dirtyExactMatchesPromotedToCuratedFuzzy <- dirtyExactMatchesPromotedToCuratedFuzzyFut
      } yield {
        logInfo(s"[Resolution] Fuzzy matches count: ${fuzzyMatches.size}")
        val reqResps = exactMatchesByUuid ++ fuzzyMatches ++ unmatchedNotParsed ++
          dirtyExactMatchesPromotedToCuratedFuzzy
        val contexts = computeContext(reqResps)
        val responses = computeResponses(reqResps)
        nr.Responses(responses = responses, contexts = contexts)
      }
    }
    resultFuture.as[TwitterFuture[nr.Responses]]
  }
}
