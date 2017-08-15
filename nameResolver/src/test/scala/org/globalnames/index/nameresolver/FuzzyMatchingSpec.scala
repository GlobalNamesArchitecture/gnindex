package org.globalnames
package index
package nameresolver

import com.google.inject.Stage
import com.twitter.finatra.thrift.EmbeddedThriftServer
import com.twitter.inject.server.FeatureTestMixin
import com.twitter.util.Future
import thrift.MatchKind
import thrift.nameresolver.{NameInput, Request, Service => NameResolverService}

import scala.util.Properties

class FuzzyMatchingSpec extends SpecConfig with FeatureTestMixin {
  override val server = new EmbeddedThriftServer(
    twitterServer = new Server,
    stage = Stage.PRODUCTION,
    flags = Map(
      NameResolverModule.matcherServiceAddress.name -> Properties.envOrElse("MATCHER_ADDRESS", "")
    )
  )

  private val GoodMatchScore = Some(0.69)
  private val FairMatchScore = Some(0.49)

  val client: NameResolverService[Future] =
    server.thriftClient[NameResolverService[Future]](clientId = "nameResolverClient")

  private final case class ExpectedResult(name: String, kind: MatchKind, editDistance: Int,
                                          scoreLowerBound: Option[Double] = None)

  private def nameTest(request: String, expectedResults: ExpectedResult*): Unit = {
    val req = Request(names = Seq(NameInput(request)), dataSourceIds = Seq(1))
    val responses = client.nameResolve(req).value.items

    responses.length shouldBe 1
    val response = responses.headOption.value
    val results = response.results
    results.length shouldBe expectedResults.length

    for ((result, expectedResult) <- results.zip(expectedResults)) {
      result.result.name.value shouldBe expectedResult.name
      result.result.matchType.kind shouldBe expectedResult.kind
      result.result.matchType.editDistance shouldBe expectedResult.editDistance
      result.score.value should be >= expectedResult.scoreLowerBound
    }
  }

  "fuzzy matcher" should {
    "match 'Actinodontium rhaphidostegium'" in nameTest(
      request = "Actinodontium rhaphidostegium",
      ExpectedResult(
        name = "Actinodontium rhaphidostegum Bosch & Sande Lacoste, 1862",
        kind = MatchKind.FuzzyCanonicalMatch,
        editDistance = 1,
        scoreLowerBound = GoodMatchScore
      )
    )


    "match 'Andreaea heinemanii'" in nameTest(
      request = "Andreaea heinemanii",
      ExpectedResult(
        name = "Andreaea heinemannii Hampe & C. Müller, 1846",
        kind = MatchKind.FuzzyCanonicalMatch,
        editDistance = 1,
        scoreLowerBound = GoodMatchScore
      )
    )

    "match 'Aplodon wormskjoldii'" in nameTest(
      request = "Aplodon wormskjoldii",
      ExpectedResult(
        name = "Aplodon wormskioldii R. Brown, 1823",
        kind = MatchKind.FuzzyCanonicalMatch,
        editDistance = 1,
        scoreLowerBound = GoodMatchScore
      )
    )

    "match 'Arctoa andersonii'" in nameTest(
      request = "Arctoa andersonii",
      ExpectedResult(
        name = "Arctoa anderssonii Wichura, 1859",
        kind = MatchKind.FuzzyCanonicalMatch,
        editDistance = 1,
        scoreLowerBound = GoodMatchScore
      )
    )

    "match 'Bryum funckii'" in nameTest(
      request = "Bryum funckii",
      ExpectedResult(
        name = "Bryum funkii Schwaegrichen, 1816",
        kind = MatchKind.FuzzyCanonicalMatch,
        editDistance = 1,
        scoreLowerBound = GoodMatchScore
      )
    )

    "match 'Bryum marattii'" in nameTest(
      request = "Bryum marattii",
      ExpectedResult(
        name = "Bryum",
        kind = MatchKind.FuzzyCanonicalMatch,
        editDistance = 0,
        scoreLowerBound = FairMatchScore
      )
    )

    "match 'Bryum minutum'" in {
      pending // fix api kind
      nameTest(
        request = "Bryum minutum",
        ExpectedResult(
          name = "Bryum",
          kind = MatchKind.ExactMatchPartialByGenus,
          editDistance = 0,
          scoreLowerBound = GoodMatchScore
        )
      )
    }

    "match 'Calyptrochaete japonica'" in nameTest(
      request = "Calyptrochaete japonica",
      ExpectedResult(
        name = "Calyptrochaeta japonica Iwatsuki & Noguchi, 1979",
        kind = MatchKind.FuzzyCanonicalMatch,
        editDistance = 1,
        scoreLowerBound = FairMatchScore
      )
    )

    "match 'Dicranella schreberiana'" in {
      pending // fix api kind
      nameTest(
        request = "Dicranella schreberiana",
        ExpectedResult(
          name = "Dicranella",
          kind = MatchKind.ExactMatchPartialByGenus,
          editDistance = 0,
          scoreLowerBound = FairMatchScore
        )
      )
    }

    "match 'Didymodon maschalogenus'" in {
      pending // fix api kind
      nameTest(
        request = "Didymodon maschalogenus",
        ExpectedResult(
          name = "Dicranella",
          kind = MatchKind.ExactMatchPartialByGenus,
          editDistance = 0,
          scoreLowerBound = FairMatchScore
        )
      )
    }

    "match 'Ditrichum cylindricum'" in {
      pending // fix api kind
      nameTest(
        request = "Ditrichum cylindricum",
        ExpectedResult(
          name = "Ditrichum",
          kind = MatchKind.ExactMatchPartialByGenus,
          editDistance = 0,
          scoreLowerBound = FairMatchScore
        )
      )
    }

    "match 'Encalypta brevicolla'" in {
      pending // fix api kind
      nameTest(
        request = "Encalypta brevicolla",
        ExpectedResult(
          name = "Encalypta",
          kind = MatchKind.ExactMatchPartialByGenus,
          editDistance = 0,
          scoreLowerBound = FairMatchScore
        )
      )
    }

    "match 'Entosthodon muehlenbergii'" in nameTest(
      request = "Entosthodon muehlenbergii",
      ExpectedResult(
        name = "Entosthodon muhlenbergii Fife, 1985",
        kind = MatchKind.FuzzyCanonicalMatch,
        editDistance = 1,
        scoreLowerBound = GoodMatchScore
      )
    )

    "match 'Hymenoloma crispulum'" in {
      pending // Hymenoloma is far from Hymenoloma
      nameTest(
        request = "Hymenoloma crispulum",
        ExpectedResult(
          name = "Hymenosoma",
          kind = MatchKind.FuzzyPartialMatch,
          editDistance = 1
        )
      )
    }

    "match 'Hymenoloma intermedium'" in {
      pending // Hymenoloma is far from Hymenoloma
      nameTest(
        request = "Hymenoloma intermedium",
        ExpectedResult(
          name = "Hymenosoma",
          kind = MatchKind.FuzzyPartialMatch,
          editDistance = 1
        )
      )
    }

    "match 'Hypnum cupressiforme var. brevisetum'" in {
      pending // should be `Partial canonical form match`
      nameTest(
        request = "Hypnum cupressiforme var. brevisetum",
        ExpectedResult(
          name = "Hypnum cupressiforme Hedwig, 1801",
          kind = MatchKind.ExactPartialMatch,
          editDistance = 1
        )
      )
    }

    "match 'Philonotis americana'" in {
      pending // fix kind api
      nameTest(
        request = "Philonotis americana",
        ExpectedResult(
          name = "Philonotis",
          kind = MatchKind.ExactMatchPartialByGenus,
          editDistance = 0
        )
      )
    }

    "match 'Myrinia rotundifolia'" in {
      pending // fix kind api
      nameTest(
        request = "Myrinia rotundifolia",
        ExpectedResult(
          name = "Myrinia", // plantae
          kind = MatchKind.ExactMatchPartialByGenus,
          editDistance = 0
        ),
        ExpectedResult(
          name = "Myrinia", // animalia
          kind = MatchKind.ExactMatchPartialByGenus,
          editDistance = 0
        )
      )
    }

    "match 'Pterobryum arbuscula'" in nameTest(
      request = "Pterobryum arbuscula",
      Seq(): _*
    )

    "match 'Sanionia georgico-uncinata'" in {
      pending // fix kind api
      nameTest(
        request = "Sanionia georgico-uncinata",
        ExpectedResult(
          name = "Sanionia georgicouncinata Ochyra, 1998",
          kind = MatchKind.ExactPartialMatch,
          editDistance = 1
        )
      )
    }
  }
}
