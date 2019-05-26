package org.globalnames
package index
package nameresolver

import com.google.inject.Stage
import com.twitter.finatra.thrift.EmbeddedThriftServer
import com.twitter.inject.server.FeatureTestMixin
import com.twitter.util.Future
import thrift.{MatchKind => MK}
import thrift.nameresolver.{NameInput, Request, Service => NameResolverService}
import matcher.{MatcherModule, Server => MatcherServer}

class AdvancedResolutionSpec extends FunSpecConfig with FeatureTestMixin {
  override def launchConditions: Boolean = matcherServer.isHealthy

  val matcherServer = new EmbeddedThriftServer(
    twitterServer = new MatcherServer,
    stage = Stage.PRODUCTION,
    flags = Map(
      MatcherModule.namesFileKey.name ->
        "db-migration/matcher-data/canonical-names.csv",
      MatcherModule.namesWithDatasourcesFileKey.name ->
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

  val client: NameResolverService[Future] =
    server.thriftClient[NameResolverService[Future]](clientId = "nameResolverClient")

  def request(name: String, dataSources: Seq[Int], advancedResolution: Boolean): Request = {
    Request(names = Seq(NameInput(name)),
            dataSourceIds = dataSources,
            advancedResolution = advancedResolution)
  protected override def afterAll(): Unit = {
    super.afterAll()
    matcherServer.close()
    server.close()
  }

  }

  describe("NameResolver advanced resolution") {
    describe("support `advancedResolution` flag") {
      it("handles `false` flag") {
        val responses = client.nameResolve(request("Abarys rabastulus", Seq(), false)).value
        responses.items(0).total shouldBe 0
        responses.items(0).results.size shouldBe 0
      }

      it("handles `true` flag") {
        val responses = client.nameResolve(request("Abarys rabastulus", Seq(7), true)).value
        responses.items.size shouldBe 1
        val result = responses.items(0).results(0)
        result.result.name.value shouldBe "Abarys"
        result.result.matchType.kind shouldBe MK.ExactMatchPartialByGenus
        result.result.matchType.editDistance shouldBe 0
      }
    }

    describe("correctly transform MatchKind for `simplified resolution`") {
      it("transforms `ExactNameMatchByUUID` correctly") {
        val name = "Homo sapiens Linnaeus, 1758"
        val dataSourceIds = Seq(1)

        val responsesAdvanced = client.nameResolve(request(name, dataSourceIds, true)).value
        val resultAdvanced = responsesAdvanced.items(0).results(0)
        resultAdvanced.result.matchType.kind shouldBe MK.ExactNameMatchByUUID
        resultAdvanced.result.matchType.editDistance shouldBe 0

        val responsesSimple = client.nameResolve(request(name, dataSourceIds, false)).value
        val resultSimple = responsesSimple.items(0).results(0)
        resultSimple.result.matchType.kind shouldBe MK.Match
        resultSimple.result.matchType.editDistance shouldBe 0
      }

      it("transforms `ExactCanonicalNameMatchByUUID` correctly") {
        val name = "Homo sapiens Xxxxxx"
        val dataSourceIds = Seq(1)

        val responsesAdvanced = client.nameResolve(request(name, dataSourceIds, true)).value
        val resultAdvanced = responsesAdvanced.items(0).results(0)
        resultAdvanced.result.matchType.kind shouldBe MK.ExactCanonicalNameMatchByUUID
        resultAdvanced.result.matchType.editDistance shouldBe 0

        val responsesSimple = client.nameResolve(request(name, dataSourceIds, false)).value
        val resultSimple = responsesSimple.items(0).results(0)
        resultSimple.result.matchType.kind shouldBe MK.Match
        resultSimple.result.matchType.editDistance shouldBe 0
      }

      it("transforms `FuzzyCanonicalMatch` correctly") {
        val name = "Homo sxpiens"
        val dataSourceIds = Seq(1)

        val responsesAdvanced = client.nameResolve(request(name, dataSourceIds, true)).value
        val resultAdvanced = responsesAdvanced.items(0).results(0)
        resultAdvanced.result.matchType.kind shouldBe MK.FuzzyCanonicalMatch
        resultAdvanced.result.matchType.editDistance shouldBe 1

        val responsesSimple = client.nameResolve(request(name, dataSourceIds, false)).value
        val resultSimple = responsesSimple.items(0).results(0)
        resultSimple.result.matchType.kind shouldBe MK.FuzzyMatch
        resultSimple.result.matchType.editDistance shouldBe 1
      }

      it("transforms `ExactMatchPartialByGenus` correctly") {
        val name = "Hoffmannius punctatus pxxctatus"
        val dataSourceIds = Seq(168)

        val responsesAdvanced = client.nameResolve(request(name, dataSourceIds, true)).value
        val resultAdvanced = responsesAdvanced.items(0).results(0)
        resultAdvanced.result.matchType.kind shouldBe MK.ExactMatchPartialByGenus
        resultAdvanced.result.matchType.editDistance shouldBe 0

        val responsesSimple = client.nameResolve(request(name, dataSourceIds, false)).value
        val responseSimple = responsesSimple.items(0)
        responseSimple.total shouldBe 0
        responseSimple.results.size shouldBe 0
      }

      it("transforms `ExactPartialMatch` correctly") {
        val name = "Involucrothele velutinoides xxlutinoides"
        val dataSourceIds = Seq(169)

        val responsesAdvanced = client.nameResolve(request(name, dataSourceIds, true)).value
        val resultAdvanced = responsesAdvanced.items(0).results(0)
        resultAdvanced.result.matchType.kind shouldBe MK.ExactPartialMatch
        resultAdvanced.result.matchType.editDistance shouldBe 0

        val responsesSimple = client.nameResolve(request(name, dataSourceIds, false)).value
        val responseSimple = responsesSimple.items(0)
        responseSimple.total shouldBe 0
        responseSimple.results.size shouldBe 0
      }

      it("transforms `FuzzyPartialMatch` correctly") {
        val name = "Brassica oleraceae var. capitata"
        val dataSourceIds = Seq(4)

        val responsesAdvanced = client.nameResolve(request(name, dataSourceIds, true)).value
        val resultAdvanced = responsesAdvanced.items(0).results(0)
        resultAdvanced.result.matchType.kind shouldBe MK.FuzzyPartialMatch
        resultAdvanced.result.matchType.editDistance shouldBe 1

        val responsesSimple = client.nameResolve(request(name, dataSourceIds, false)).value
        val responseSimple = responsesSimple.items(0)
        responseSimple.total shouldBe 0
        responseSimple.results.size shouldBe 0
      }
    }
  }
}
