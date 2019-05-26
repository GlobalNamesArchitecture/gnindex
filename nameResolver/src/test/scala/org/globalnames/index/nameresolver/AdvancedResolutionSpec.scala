package org.globalnames
package index
package nameresolver

import com.google.inject.Stage
import com.twitter.finatra.thrift.EmbeddedThriftServer
import com.twitter.inject.server.FeatureTestMixin
import com.twitter.util.Future
import thrift.{MatchKind => MK, nameresolver => nr}
import index.{matcher => m, MatchKindTransform => MKT}

class AdvancedResolutionSpec extends FunSpecConfig with FeatureTestMixin {
  override def launchConditions: Boolean = matcherServer.isHealthy

  var matcherServer: EmbeddedThriftServer = new EmbeddedThriftServer(
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

  val client: nr.Service[Future] =
    server.thriftClient[nr.Service[Future]](clientId = "nameResolverClient")

  protected override def afterAll(): Unit = {
    super.afterAll()
    matcherServer.close()
    server.close()
  }

  def request(name: String, dataSources: Seq[Int], advancedResolution: Boolean): nr.Request = {
    nr.Request(nameInputs = Seq(nr.NameInput(name)),
               dataSourceIds = dataSources,
               advancedResolution = advancedResolution)
  }

  describe("NameResolver advanced resolution") {
    describe("support `advancedResolution` flag") {
      it("handles `false` flag") {
        val responses = client.nameResolve(request(
          name = "Abarys rabastulus", dataSources = Seq(), advancedResolution = false)).value
        responses.responses.head.total shouldBe 0
        responses.responses.head.resultsScored shouldBe empty
      }

      it("handles `true` flag") {
        val responses = client.nameResolve(request(
          name = "Abarys rabastulus", dataSources = Seq(7), advancedResolution = true)).value
        responses.responses.size shouldBe 1
        val result = responses.responses.head.resultsScored.head
        result.result.name.value shouldBe "Abarys"
        MKT.matchKindInfo(result.result.matchType.kind) shouldBe MKT.ExactPartialMatch
        result.result.matchType.kind match { case MK.CanonicalMatch(cm) =>
          cm.stemEditDistance shouldBe 0
          cm.verbatimEditDistance shouldBe 0
        }
      }
    }

    describe("correctly transform MatchKind for `simplified resolution`") {
      it("transforms `ExactMatch` correctly") {
        val name = "Homo sapiens Linnaeus, 1758"
        val dataSourceIds = Seq(1)

        val responsesAdvanced = client.nameResolve(request(
          name = name, dataSources = dataSourceIds, advancedResolution = true)).value
        val resultAdvanced = responsesAdvanced.responses.head.resultsScored.head
        MKT.matchKindInfo(resultAdvanced.result.matchType.kind) shouldBe MKT.ExactMatch

        val responsesSimple = client.nameResolve(request(
          name = name, dataSources = dataSourceIds, advancedResolution = false)).value
        val resultSimple = responsesSimple.responses.head.resultsScored.head
        MKT.matchKindInfo(resultSimple.result.matchType.kind) shouldBe MKT.ExactMatch
      }

      it("transforms `ExactCanonicalMatch` correctly") {
        val name = "Homo sapiens Xxxxxx"
        val dataSourceIds = Seq(1)

        val responsesAdvanced = client.nameResolve(request(
          name = name, dataSources = dataSourceIds, advancedResolution = true)).value
        val resultAdvanced = responsesAdvanced.responses.head.resultsScored.head
        MKT.matchKindInfo(resultAdvanced.result.matchType.kind) shouldBe MKT.ExactCanonicalMatch
        resultAdvanced.result.matchType.kind match { case MK.CanonicalMatch(cm) =>
          cm.stemEditDistance shouldBe 0
          cm.verbatimEditDistance shouldBe 0
        }

        val responsesSimple = client.nameResolve(request(
          name = name, dataSources = dataSourceIds, advancedResolution = false)).value
        val resultSimple = responsesSimple.responses.head.resultsScored.head
        MKT.matchKindInfo(resultSimple.result.matchType.kind) shouldBe MKT.ExactCanonicalMatch
        resultSimple.result.matchType.kind match { case MK.CanonicalMatch(cm) =>
          cm.stemEditDistance shouldBe 0
          cm.verbatimEditDistance shouldBe 0
        }
      }

      it("transforms `FuzzyCanonicalMatch` correctly") {
        val name = "Homo sxpiens"
        val dataSourceIds = Seq(1)

        val responsesAdvanced = client.nameResolve(request(
          name = name, dataSources = dataSourceIds, advancedResolution = true)).value
        val resultAdvanced = responsesAdvanced.responses.head.resultsScored.head
        MKT.matchKindInfo(resultAdvanced.result.matchType.kind) shouldBe MKT.FuzzyCanonicalMatch
        resultAdvanced.result.matchType.kind match { case MK.CanonicalMatch(cm) =>
          cm.stemEditDistance shouldBe 1
          cm.verbatimEditDistance shouldBe 1
        }

        val responsesSimple = client.nameResolve(
          request(name = name, dataSources = dataSourceIds, advancedResolution = false)).value
        val resultSimple = responsesSimple.responses.head.resultsScored.head
        MKT.matchKindInfo(resultSimple.result.matchType.kind) shouldBe MKT.FuzzyCanonicalMatch
        resultSimple.result.matchType.kind match { case MK.CanonicalMatch(cm) =>
          cm.stemEditDistance shouldBe 1
          cm.verbatimEditDistance shouldBe 1
        }
      }

      it("transforms `ExactPartialMatch` correctly") {
        val name = "Hoffmannius punctatus pxxctatus"
        val dataSourceIds = Seq(168)

        val responsesAdvanced = client.nameResolve(request(
          name = name, dataSources = dataSourceIds, advancedResolution = true)).value
        val resultAdvanced = responsesAdvanced.responses.head.resultsScored.head
        MKT.matchKindInfo(resultAdvanced.result.matchType.kind) shouldBe MKT.ExactPartialMatch
        resultAdvanced.result.matchType.kind match { case MK.CanonicalMatch(cm) =>
          cm.stemEditDistance shouldBe 0
          cm.verbatimEditDistance shouldBe 0
        }

        val responsesSimple = client.nameResolve(request(
          name = name, dataSources = dataSourceIds, advancedResolution = false)).value
        val responseSimple = responsesSimple.responses.head
        responseSimple.total shouldBe 0
        responseSimple.resultsScored shouldBe empty
      }
    }
  }
}
