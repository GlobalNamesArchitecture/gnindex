package org.globalnames
package index
package nameresolver

import com.google.inject.Stage
import com.twitter.finatra.thrift.EmbeddedThriftServer
import com.twitter.inject.server.FeatureTestMixin
import com.twitter.util.Future
import thrift.{MatchKind => MK, nameresolver => nr}
import index.{matcher => m, MatchKindTransform => MKT}
import scalaz.syntax.std.option._

class FuzzyMatchingSpec extends WordSpecConfig with FeatureTestMixin {
  override def launchConditions: Boolean = matcherServer.isHealthy

  val matcherServer = new EmbeddedThriftServer(
    twitterServer = new m.Server,
    stage = Stage.PRODUCTION,
    flags = Map(
      m.MatcherModule.namesFileKey.name ->
        "db-migration/matcher-data/canonical-names.csv",
      m.MatcherModule.namesWithDatasourcesFileKey.name ->
        "db-migration/matcher-data/canonical-names-with-data-sources.csv"
    )
  )

  override val server = new EmbeddedThriftServer(
    twitterServer = new Server,
    stage = Stage.PRODUCTION,
    flags = Map(
      NameResolverModule.matcherServiceAddress.name -> matcherServer.thriftHostAndPort
    )
  )

  protected override def afterAll(): Unit = {
    super.afterAll()
    matcherServer.close()
    server.close()
  }

  sealed trait MatchScoreLevel
  case object GoodMatchScore extends MatchScoreLevel
  case object FairMatchScore extends MatchScoreLevel
  case object PoorMatchScore extends MatchScoreLevel
  object MatchScoreLevel {
    def apply(v: Double): MatchScoreLevel = {
      if (v > 0.69) GoodMatchScore
      else if (v > 0.49) FairMatchScore
      else PoorMatchScore
    }
  }

  val client: nr.Service[Future] =
    server.thriftClient[nr.Service[Future]](clientId = "nameResolverClient")

  private final case class ExpectedResult(name: String,
                                          kind: MKT.MatchKindInfo,
                                          stemEditDistance: Option[Int],
                                          verbatimEditDistance: Option[Int],
                                          scoreLowerBound: Option[MatchScoreLevel] = None)

  private def nameTest(request: String,
                       expectedResults: Seq[ExpectedResult],
                       dataSourceIds: Seq[Int] = Seq(1),
                       advancedResolution: Boolean = true): Unit = {
    val req = nr.Request(nameInputs = Seq(nr.NameInput(request)),
                         dataSourceIds = dataSourceIds,
                         advancedResolution = advancedResolution)
    val responses = client.nameResolve(req).value.responses

    responses.size shouldBe 1
    val response = responses.headOption.value
    val results = response.resultsScored
    results.size shouldBe expectedResults.length

    if (expectedResults.nonEmpty) {
      val resultsProject = results.map { r =>
        val (stemEditDistance, verbatimEditDistance) = r.result.matchType.kind match {
          case MK.CanonicalMatch(cm) => (cm.stemEditDistance.some, cm.verbatimEditDistance.some)
          case _ => (None, None)
        }

        ExpectedResult(
          name = r.result.name.value,
          kind = MKT.matchKindInfo(r.result.matchType.kind),
          stemEditDistance = stemEditDistance,
          verbatimEditDistance = verbatimEditDistance,
          scoreLowerBound = r.score.value.map { MatchScoreLevel.apply }
        )
      }
      resultsProject should contain only (expectedResults.distinct: _*)
    }
  }

  "fuzzy matcher" should {
    "match 'Actinodontium rhaphidostegium'" in {
      nameTest(
        request = "Actinodontium rhaphidostegium",
        Seq(ExpectedResult(
          name = "Actinodontium rhaphidostegum Bosch & Sande Lacoste, 1862",
          kind = MKT.FuzzyCanonicalMatch,
          stemEditDistance = 1.some,
          verbatimEditDistance = 1.some,
          scoreLowerBound = GoodMatchScore.some
        ))
      )
    }

    "match 'Andreaea heinemanii'" in nameTest(
      request = "Andreaea heinemanii",
      Seq(ExpectedResult(
        // scalastyle:off non.ascii.character.disallowed
        name = "Andreaea heinemannii Hampe & C. Müller, 1846",
        // scalastyle:on
        kind = MKT.FuzzyCanonicalMatch,
        stemEditDistance = 1.some,
        verbatimEditDistance = 1.some,
        scoreLowerBound = GoodMatchScore.some
      ))
    )

    "match 'Aplodon wormskjoldii'" in nameTest(
      request = "Aplodon wormskjoldii",
      Seq(ExpectedResult(
        name = "Aplodon wormskioldii R. Brown, 1823",
        kind = MKT.FuzzyCanonicalMatch,
        stemEditDistance = 0.some,
        verbatimEditDistance = 1.some,
        scoreLowerBound = GoodMatchScore.some
      ))
    )

    "match 'Arctoa andersonii'" in nameTest(
      request = "Arctoa andersonii",
      Seq(ExpectedResult(
        name = "Arctoa anderssonii Wichura, 1859",
        kind = MKT.FuzzyCanonicalMatch,
        stemEditDistance = 1.some,
        verbatimEditDistance = 1.some,
        scoreLowerBound = GoodMatchScore.some
      ))
    )

    "match 'Bryum funckii'" in nameTest(
      request = "Bryum funckii",
      Seq(ExpectedResult(
        name = "Bryum funkii Schwaegrichen, 1816",
        kind = MKT.FuzzyCanonicalMatch,
        stemEditDistance = 1.some,
        verbatimEditDistance = 1.some,
        scoreLowerBound = GoodMatchScore.some
      ))
    )

    "match 'Bryum marattii'" in nameTest(
      request = "Bryum marattii",
      Seq(ExpectedResult(
        name = "Bryum",
        kind = MKT.ExactPartialMatch,
        stemEditDistance = 0.some,
        verbatimEditDistance = 0.some,
        scoreLowerBound = GoodMatchScore.some
      ))
    )

    "match 'Bryum minutum'" in {
      nameTest(
        request = "Bryum minutum",
        Seq(ExpectedResult(
          name = "Bryum",
          kind = MKT.ExactPartialMatch,
          stemEditDistance = 0.some,
          verbatimEditDistance = 0.some,
          scoreLowerBound = GoodMatchScore.some
        ))
      )
    }

    "match 'Calyptrochaete japonica'" in nameTest(
      request = "Calyptrochaete japonica",
      Seq(ExpectedResult(
        name = "Calyptrochaeta japonica Iwatsuki & Noguchi, 1979",
        kind = MKT.FuzzyCanonicalMatch,
        stemEditDistance = 0.some,
        verbatimEditDistance = 1.some,
        scoreLowerBound = GoodMatchScore.some
      ))
    )

    "match 'Dicranella schreberiana'" in nameTest(
      request = "Dicranella schreberiana",
      Seq(ExpectedResult(
        name = "Dicranella",
        kind = MKT.ExactPartialMatch,
        stemEditDistance = 0.some,
        verbatimEditDistance = 0.some,
        scoreLowerBound = GoodMatchScore.some
      ))
    )

    "match 'Didymodon maschalogenus'" in nameTest(
      request = "Didymodon maschalogenus",
      Seq(ExpectedResult(
        name = "Didymodon",
        kind = MKT.ExactPartialMatch,
        stemEditDistance = 0.some,
        verbatimEditDistance = 0.some,
        scoreLowerBound = GoodMatchScore.some
      ))
    )

    "match 'Ditrichum cylindricum'" in nameTest(
      request = "Ditrichum cylindricum",
      Seq(ExpectedResult(
        name = "Ditrichum",
        kind = MKT.ExactPartialMatch,
        stemEditDistance = 0.some,
        verbatimEditDistance = 0.some,
        scoreLowerBound = GoodMatchScore.some
      ))
    )

    "match 'Encalypta brevicolla'" in nameTest(
      request = "Encalypta brevicolla",
      Seq(ExpectedResult(
        name = "Encalypta",
        kind = MKT.ExactPartialMatch,
        stemEditDistance = 0.some,
        verbatimEditDistance = 0.some,
        scoreLowerBound = GoodMatchScore.some
      ))
    )

    "match 'Entosthodon muehlenbergii'" in nameTest(
      request = "Entosthodon muehlenbergii",
      Seq(ExpectedResult(
        name = "Entosthodon muhlenbergii Fife, 1985",
        kind = MKT.FuzzyCanonicalMatch,
        stemEditDistance = 1.some,
        verbatimEditDistance = 1.some,
        scoreLowerBound = GoodMatchScore.some
      ))
    )

    "match 'Hymenoloma crispulum'" in nameTest(
      request = "Hymenoloma crispulum",
      Seq()
    )

    "match 'Hymenoloma intermedium'" in nameTest(
      request = "Hymenoloma intermedium",
      Seq()
    )

    "match 'Hypnum cupressiforme var. brevisetum'" in nameTest(
      request = "Hypnum cupressiforme var. brevisetum",
      Seq(ExpectedResult(
        name = "Hypnum cupressiforme Hedwig, 1801",
        kind = MKT.ExactPartialMatch,
        stemEditDistance = 0.some,
        verbatimEditDistance = 0.some,
        scoreLowerBound = GoodMatchScore.some
      ))
    )

    "match 'Philonotis americana'" in nameTest(
      request = "Philonotis americana",
      Seq(ExpectedResult(
        name = "Philonotis",
        kind = MKT.ExactPartialMatch,
        stemEditDistance = 0.some,
        verbatimEditDistance = 0.some,
        scoreLowerBound = GoodMatchScore.some
      ))
    )

    "match 'Myrinia rotundifolia'" in nameTest(
      request = "Myrinia rotundifolia",
      Seq(
        ExpectedResult(
          name = "Myrinia", // animalia
          kind = MKT.ExactPartialMatch,
          stemEditDistance = 0.some,
          verbatimEditDistance = 0.some,
          scoreLowerBound = GoodMatchScore.some
        ),
        ExpectedResult(
          name = "Myrinia", // plantae
          kind = MKT.ExactPartialMatch,
          stemEditDistance = 0.some,
          verbatimEditDistance = 0.some,
          scoreLowerBound = GoodMatchScore.some
        )
      )
    )

    "match 'Pterobryum arbuscula'" in nameTest(
      request = "Pterobryum arbuscula",
      Seq()
    )

    "match 'Sanionia georgico-uncinata'" in nameTest(
      request = "Sanionia georgico-uncinata",
      Seq(ExpectedResult(
        name = "Sanionia georgicouncinata Ochyra, 1998",
        kind = MKT.FuzzyCanonicalMatch,
        stemEditDistance = 1.some,
        verbatimEditDistance = 1.some,
        scoreLowerBound = GoodMatchScore.some
      ))
    )

    "handle fuzzy match on genus" when {
      "don't match genus for multinomials (possibly matched 'Abarys' is genus)" in nameTest(
        request = "Adarys robusulus",
        Seq()
      )

      "match species for multinomials" in nameTest(
        request = "Abarys robusulus",
        Seq(ExpectedResult(
          name = "Abarys robustulus",
          kind = MKT.FuzzyCanonicalMatch,
          stemEditDistance = 1.some,
          verbatimEditDistance = 1.some,
          scoreLowerBound = GoodMatchScore.some
        )),
        dataSourceIds = Seq(168)
      )

      "match genus when there's no species" in nameTest(
        request = "Abarys rabasulus",
        Seq(
          ExpectedResult(
            name = "Abarys Turner, 1947",
            kind = MKT.ExactPartialMatch,
            stemEditDistance = 0.some,
            verbatimEditDistance = 0.some,
            scoreLowerBound = GoodMatchScore.some
          ),
          ExpectedResult(
            name = "Abarys Agassiz, 1846",
            kind = MKT.ExactPartialMatch,
            stemEditDistance = 0.some,
            verbatimEditDistance = 0.some,
            scoreLowerBound = GoodMatchScore.some
          ),
          ExpectedResult(
            name = "Abarys",
            kind = MKT.ExactPartialMatch,
            stemEditDistance = 0.some,
            verbatimEditDistance = 0.some,
            scoreLowerBound = GoodMatchScore.some
          )
        ),
        dataSourceIds = Seq(7, 8)
      )

      "match uninomials" in nameTest(
        request = "Abarys",
        Seq(
          ExpectedResult(
            name = "Abarys",
            kind = MKT.ExactMatch,
            stemEditDistance = None,
            verbatimEditDistance = None,
            scoreLowerBound = GoodMatchScore.some
          )
        ),
        dataSourceIds = Seq(7)
      )
    }
  }
}
