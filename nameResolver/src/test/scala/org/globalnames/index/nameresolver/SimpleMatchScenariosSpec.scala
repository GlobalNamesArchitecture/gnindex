package org.globalnames
package index
package nameresolver

import com.google.inject.Stage
import com.twitter.finatra.thrift.EmbeddedThriftServer
import com.twitter.inject.server.FeatureTestMixin
import com.twitter.util.Future
import thrift.MatchKind
import thrift.nameresolver.{NameInput, Request, Service => NameResolverService}
import matcher.{MatcherModule, Server => MatcherServer}

import scalaz.syntax.std.option._

class SimpleMatchScenariosSpec extends WordSpecConfig with FeatureTestMixin {
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

  "index resolve" should {
    val dataSourceId = 1

    "match 'Homo sapiens Linnaeus, 1758' exactly by name UUID" in {
      val response = client.nameResolve(Request(
        names = Seq(NameInput("Homo sapiens Linnaeus, 1758")),
        advancedResolution = true, dataSourceIds = Seq(dataSourceId))).value.items
      response should have size 1
      val results = response.headOption.value.results
      results should have size 1
      val result = results.headOption.value.result
      result.dataSource.id shouldBe dataSourceId
      result.name.value shouldBe "Homo sapiens Linnaeus, 1758"
      result.canonicalName.value.value shouldBe "Homo sapiens"
      result.canonicalName.value.valueRanked shouldBe "Homo sapiens"
      result.acceptedName.canonicalName.value.value shouldBe "Homo sapiens"
      result.acceptedName.canonicalName.value.valueRanked shouldBe "Homo sapiens"
      result.matchType.kind shouldBe MatchKind.ExactNameMatchByUUID
    }

    "match 'Homo sapiens XXX, 1999' exactly by canonical UUID" in {
      val response = client.nameResolve(Request(
        names = Seq(NameInput("Homo sapiens XXX, 1999")),
        dataSourceIds = Seq(dataSourceId), advancedResolution = true)).value.items
      response should have size 1
      val results = response.headOption.value.results
      results should have size 1
      val result = results.headOption.value
      result.result.dataSource.id shouldBe dataSourceId
      result.result.name.value shouldBe "Homo sapiens Linnaeus, 1758"
      result.result.canonicalName.value.value shouldBe "Homo sapiens"
      result.result.matchType.kind shouldBe MatchKind.ExactCanonicalNameMatchByUUID
    }

    "preserve suppliedInput" in {
      val response = client.nameResolve(Request(
        names = Seq(NameInput("Homo sapiens Linnaeus, 1758")),
        dataSourceIds = Seq(dataSourceId), advancedResolution = true)).value.items
      response.headOption.value.suppliedInput.value shouldBe "Homo sapiens Linnaeus, 1758"
    }

    "not have suppliedId if not given" in {
      val response = client.nameResolve(Request(
        names = Seq(NameInput("Homo sapiens Linnaeus, 1758")),
        dataSourceIds = Seq(dataSourceId), advancedResolution = true)).value.items
      response.headOption.value.suppliedId shouldBe None
    }

    "preserve suppliedId" in {
      val suppliedId = "abc"
      val response = client.nameResolve(Request(
        names = Seq(NameInput("Homo sapiens Linnaeus, 1758", suppliedId = suppliedId.some)),
        dataSourceIds = Seq(dataSourceId), advancedResolution = true)).value.items
      response.headOption.value.suppliedId.value shouldBe suppliedId
    }

    "have results from requested data sources" in {
      val inputDataSourceIds = Seq(1, 2, 3)
      val request = Request(names = Seq(
        NameInput("Actinodontium rhaphidostegium"),
        NameInput("Andreaea heinemanii"),
        NameInput("Homo sapiens"),
        NameInput("Bryum capillare")
      ), dataSourceIds = inputDataSourceIds, advancedResolution = true)
      val response = client.nameResolve(request).value.items
      val dataSourceIdsResponse =
        response.flatMap { resp => resp.results.map { res => res.result.dataSource.id }}
      dataSourceIdsResponse should contain only (1, 3)
    }

    "correctly return canonicalRanked" when {
      "canonicalRanked is same as canonical" in {
        val request = Request(names = Seq(
          NameInput("Homo sapiens Linnaeus, 1758")
        ), dataSourceIds = Seq(1), advancedResolution = true)
        val response = client.nameResolve(request).value
        response.items(0).results(0).result.canonicalName.value.value shouldBe "Homo sapiens"
        response.items(0).results(0).result.canonicalName.value.valueRanked shouldBe "Homo sapiens"
      }

      "canonicalRanked differs from canonical" in {
        val request = Request(names = Seq(
          NameInput("Geonoma pohliana subsp. wittigiana (Glaz. ex Drude) A.J.Hend.")
        ), dataSourceIds = Seq(1), advancedResolution = true)
        val response = client.nameResolve(request).value
        val result = response.items(0).results(0).result
        result.canonicalName.value.value shouldBe "Geonoma pohliana wittigiana"
        result.canonicalName.value.valueRanked shouldBe "Geonoma pohliana ssp. wittigiana"
      }
    }

    "handle preferred data sources" when {
      "data sources are provided" in {
        val inputDataSourceIds = Seq(1, 2)
        val preferredDataSourceIds = Seq(3, 4)
        val request = Request(
          names = Seq(NameInput("Homo sapiens Linnaeus, 1758")),
          dataSourceIds = inputDataSourceIds,
          preferredDataSourceIds = preferredDataSourceIds,
          advancedResolution = true)
        val response = client.nameResolve(request).value.items.headOption.value

        val dataSourceIdsResponse = response.results.map { _.result.dataSource.id }
        val preferredDataSourceIdsResponse = response.preferredResults.map { _.result.dataSource.id}
        dataSourceIdsResponse should not be empty
        preferredDataSourceIdsResponse should not be empty
        (inputDataSourceIds ++ preferredDataSourceIds) should contain allElementsOf
          dataSourceIdsResponse
        preferredDataSourceIds should contain allElementsOf preferredDataSourceIdsResponse
      }

      "no data sources are provided" in {
        val preferredDataSourceIds = Seq(3, 4)
        val request = Request(
          names = Seq(NameInput("Homo sapiens Linnaeus, 1758")),
          preferredDataSourceIds = preferredDataSourceIds,
          advancedResolution = true)
        val response = client.nameResolve(request).value.items.headOption.value

        val dataSourceIdsResponse = response.results.map { _.result.dataSource.id }
        val preferredDataSourceIdsResponse = response.preferredResults.map { _.result.dataSource.id}
        dataSourceIdsResponse should not be empty
        preferredDataSourceIdsResponse should not be empty
        preferredDataSourceIds should contain allElementsOf dataSourceIdsResponse
        preferredDataSourceIds should contain allElementsOf preferredDataSourceIdsResponse
      }
    }

    "return correct value for synonym field" when {
      "name is a synonym" in {
        val synonymNames = Seq(
          // scalastyle:off non.ascii.character.disallowed
          "Macrosporium septosporum (Preuss) Rabenh., 1851",
          "Gloeosporium hawaiense Thüm.",
          "Creonectria Seaver, 1909",
          "Argomyces Arthur, 1912"
          // scalastyle:on non.ascii.character.disallowed
        )

        val request = Request(names = synonymNames.map { n => NameInput(value = n) },
                              advancedResolution = true,
                              dataSourceIds = Seq(8))
        val response = client.nameResolve(request).value

        response.items.count { _.results.nonEmpty } shouldBe synonymNames.size
        val synonymStatuses = response.items.flatMap { _.results.map { _.result.synonym } }
        synonymStatuses should contain only true
      }

      "name is not a synonym" in {
        val nonSynonymNames = Seq(
          "Macrobiotus harmsworthi subsp. obscurus Dastych, 1985",
          "Macrobiotus islandicus subsp. islandicus Richters, 1904",
          "Macrobiotus hufelandi subsp. hufelandi C.A.S. Schultze, 1834",
          "Macrobiotus harmsworthi subsp. harmsworthi Murray, 1907",
          "Macrobiotus harmsworthi subsp. obscurus Dastych, 1985",
          "Hypechiniscus gladiator subsp. gladiator (Murray, 1905)"
        )

        val request = Request(
          names = nonSynonymNames.map { n => NameInput(value = n) },
          dataSourceIds = Seq(1), advancedResolution = true
        )
        val response = client.nameResolve(request).value

        response.items.count { _.results.nonEmpty } shouldBe nonSynonymNames.size
        val synonymStatuses = response.items.flatMap { _.results.map { _.result.synonym } }
        synonymStatuses should contain only false

        for { item <- response.items
              result <- item.results } {
          result.result.acceptedName.name shouldBe result.result.name
          result.result.acceptedName.canonicalName shouldBe result.result.canonicalName
          result.result.acceptedName.taxonId shouldBe result.result.taxonId
          result.result.acceptedName.dataSourceId shouldBe result.result.dataSource.id
        }
      }
    }
  }
}
