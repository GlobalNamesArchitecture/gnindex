package org.globalnames
package index
package nameresolver

import com.google.inject.Stage
import com.twitter.finatra.thrift.EmbeddedThriftServer
import com.twitter.inject.server.FeatureTestMixin
import com.twitter.util.Future
import index.{matcher => m, MatchKindTransform => MKT}
import thrift.{nameresolver => nr}

import scalaz.syntax.std.option._

class SimpleMatchScenariosSpec extends WordSpecConfig with FeatureTestMixin {
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

  val client: nr.Service[Future] =
    server.thriftClient[nr.Service[Future]](clientId = "nameResolverClient")

  protected override def afterAll(): Unit = {
    super.afterAll()
    matcherServer.close()
    server.close()
  }

  "index resolve" should {
    val dataSourceId = 1

    "match 'Homo sapiens Linnaeus, 1758' exactly by name UUID" in {
      val response = client.nameResolve(nr.Request(
        nameInputs = Seq(nr.NameInput("Homo sapiens Linnaeus, 1758")),
        dataSourceIds = Seq(dataSourceId))).value.responses
      response should have size 1
      val results = response.headOption.value.resultsScored
      results should have size 1
      val result = results.headOption.value.result
      result.dataSource.id shouldBe dataSourceId
      result.name.value shouldBe "Homo sapiens Linnaeus, 1758"
      result.canonicalName.value.value shouldBe "Homo sapiens"
      result.canonicalName.value.valueRanked shouldBe "Homo sapiens"
      result.acceptedName.canonicalName.value.value shouldBe "Homo sapiens"
      result.acceptedName.canonicalName.value.valueRanked shouldBe "Homo sapiens"
      MKT.matchKindInfo(result.matchType.kind) shouldBe MKT.ExactMatch
    }

    "match 'Homo sapiens XXX, 1999' exactly by canonical UUID" in {
      val response = client.nameResolve(nr.Request(
        nameInputs = Seq(nr.NameInput("Homo sapiens XXX, 1999")),
        dataSourceIds = Seq(dataSourceId))).value.responses
      response should have size 1
      val results = response.headOption.value.resultsScored
      results should have size 1
      val result = results.headOption.value
      result.result.dataSource.id shouldBe dataSourceId
      result.result.name.value shouldBe "Homo sapiens Linnaeus, 1758"
      result.result.canonicalName.value.value shouldBe "Homo sapiens"
      MKT.matchKindInfo(result.result.matchType.kind) shouldBe MKT.ExactCanonicalMatch
    }

    "preserve suppliedInput" in {
      val response = client.nameResolve(nr.Request(
        nameInputs = Seq(nr.NameInput("Homo sapiens Linnaeus, 1758")),
        dataSourceIds = Seq(dataSourceId))).value.responses
      response.headOption.value.suppliedInput shouldBe "Homo sapiens Linnaeus, 1758"
    }

    "results and preferredResults should be sorted according to ordering" in {
      val response = client.nameResolve(nr.Request(
        nameInputs = Seq(nr.NameInput("Homo sapiens Linnaeus, 1758")),
        preferredDataSourceIds = Seq.range(1, 15))).value.responses.head

      response.resultsScored.size should be > 1
      response.resultsScored shouldBe
        response.resultsScored.sorted(NameResolver.resultScoredOrdering.reverse)

      response.preferredResultsScored.size should be > 1
      response.preferredResultsScored shouldBe
        response.preferredResultsScored.sorted(NameResolver.resultScoredOrdering.reverse)
    }

    "not have suppliedId if no is given" in {
      val response = client.nameResolve(nr.Request(
        nameInputs = Seq(nr.NameInput("Homo sapiens Linnaeus, 1758")),
        dataSourceIds = Seq(dataSourceId))).value.responses
      response.headOption.value.suppliedId shouldBe None
    }

    "preserve suppliedId" in {
      val suppliedId = "abc"
      val response = client.nameResolve(nr.Request(
        nameInputs = Seq(nr.NameInput("Homo sapiens Linnaeus, 1758", suppliedId = suppliedId.some)),
        dataSourceIds = Seq(dataSourceId))).value.responses
      response.headOption.value.suppliedId.value shouldBe suppliedId
    }

    "have results from requested data sources" in {
      val inputDataSourceIds = Seq(1, 2, 3)
      val request = nr.Request(nameInputs = Seq(
        nr.NameInput("Actinodontium rhaphidostegium"),
        nr.NameInput("Andreaea heinemanii"),
        nr.NameInput("Homo sapiens"),
        nr.NameInput("Bryum capillare")
      ), dataSourceIds = inputDataSourceIds)
      val response = client.nameResolve(request).value.responses
      val dataSourceIdsResponse =
        response.flatMap { resp => resp.resultsScored.map { res => res.result.dataSource.id }}
      dataSourceIdsResponse should contain only (1, 3)
    }

    "correctly return canonicalRanked" when {
      "canonicalRanked is same as canonical" in {
        val request = nr.Request(nameInputs = Seq(
          nr.NameInput("Homo sapiens Linnaeus, 1758")
        ), dataSourceIds = Seq(1))
        val response = client.nameResolve(request).value
        val canonicalName = response.responses.head.resultsScored.head.result.canonicalName.value
        canonicalName.value shouldBe "Homo sapiens"
        canonicalName.valueRanked shouldBe "Homo sapiens"
      }

      "canonicalRanked differs from canonical" in {
        val name =
          "Gilia ophthalmoides Brand subsp. flavocincta (A. Nelson) A.D. Grant & V.E. Grant"
        val request = nr.Request(nameInputs = Seq(nr.NameInput(name)), dataSourceIds = Seq(169))
        val response = client.nameResolve(request).value
        val result = response.responses.head.resultsScored.head.result
        val canonicalName = result.canonicalName.value
        canonicalName.value shouldNot be (canonicalName.valueRanked)
        canonicalName.value shouldBe "Gilia ophthalmoides flavocincta"
        canonicalName.valueRanked shouldBe "Gilia ophthalmoides ssp. flavocincta"
      }
    }

    "handle preferred data sources" when {
      "data sources are provided" in {
        val inputDataSourceIds = Seq(1)
        val preferredDataSourceIds = Seq(4)
        val request = nr.Request(
          nameInputs = Seq(nr.NameInput("Homo sapiens")),
          dataSourceIds = inputDataSourceIds,
          preferredDataSourceIds = preferredDataSourceIds)
        val response = client.nameResolve(request).value.responses.headOption.value

        val dataSourceIdsResponse = response.resultsScored.map { _.result.dataSource.id }
        dataSourceIdsResponse should contain allElementsOf inputDataSourceIds
        dataSourceIdsResponse shouldNot contain allElementsOf preferredDataSourceIds

        val preferredDataSourceIdsResponse =
          response.preferredResultsScored.map { _.result.dataSource.id}
        preferredDataSourceIdsResponse should contain allElementsOf preferredDataSourceIds
        preferredDataSourceIdsResponse shouldNot contain allElementsOf inputDataSourceIds
      }

      "no data sources are provided" in {
        val preferredDataSourceIds = Seq(1, 4, 8)
        val request = nr.Request(
          nameInputs = Seq(nr.NameInput("Homo sapiens Linnaeus, 1758")),
          preferredDataSourceIds = preferredDataSourceIds)
        val response = client.nameResolve(request).value.responses.headOption.value

        val dataSourceIdsResponse = response.resultsScored.map { _.result.dataSource.id }
        dataSourceIdsResponse should not be empty

        val preferredDataSourceIdsResponse =
          response.preferredResultsScored.map { _.result.dataSource.id}
        preferredDataSourceIdsResponse should not be empty
        preferredDataSourceIds should contain allElementsOf preferredDataSourceIdsResponse
        dataSourceIdsResponse should contain allElementsOf preferredDataSourceIdsResponse
      }
    }

    "handle `bestMatch`" when {
      "looking at core results" in {
        val responseBestMatch = client.nameResolve(
          nr.Request(nameInputs = Seq(nr.NameInput("Homo sapiens Linnaeus, 1758")),
                     bestMatchOnly = true)
        ).value.responses.head
        val responseAll = client.nameResolve(
          nr.Request(nameInputs = Seq(nr.NameInput("Homo sapiens Linnaeus, 1758")))
        ).value.responses.head

        responseBestMatch.resultsScored.size shouldBe 1
        responseAll.resultsScored.size should be > 1

        responseBestMatch.resultsScored.head shouldBe responseAll.resultsScored.head
      }

      "looking at results from preferred data sources" in {
        val prefDSs = Seq.range(1, 15)
        val responseBestMatch = client.nameResolve(
          nr.Request(nameInputs = Seq(nr.NameInput("Homo sapiens Linnaeus, 1758")),
                     bestMatchOnly = true,
                     preferredDataSourceIds = prefDSs)).value.responses.head
        val responseAll = client.nameResolve(
          nr.Request(nameInputs = Seq(nr.NameInput("Homo sapiens Linnaeus, 1758")),
                     preferredDataSourceIds = prefDSs)).value.responses.head

        responseBestMatch.preferredResultsScored.size should be <= prefDSs.size
        responseAll.preferredResultsScored should contain
          allElementsOf(responseBestMatch.preferredResultsScored)
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

        val request = nr.Request(nameInputs = synonymNames.map { n => nr.NameInput(value = n) },
                                 dataSourceIds = Seq(8))
        val response = client.nameResolve(request).value

        response.responses.count { _.resultsScored.nonEmpty } shouldBe synonymNames.size
        val synonymStatuses = response.responses.flatMap { _.resultsScored.map { _.result.synonym }}
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

        val request = nr.Request(
          nameInputs = nonSynonymNames.map { n => nr.NameInput(value = n) },
          dataSourceIds = Seq(1)
        )
        val response = client.nameResolve(request).value

        response.responses.count { _.resultsScored.nonEmpty } shouldBe nonSynonymNames.size
        val synonymStatuses = response.responses.flatMap { _.resultsScored.map { _.result.synonym }}
        synonymStatuses should contain only false

        for { item <- response.responses
              result <- item.resultsScored } {
          result.result.acceptedName.name shouldBe result.result.name
          result.result.acceptedName.canonicalName shouldBe result.result.canonicalName
          result.result.acceptedName.taxonId shouldBe result.result.taxonId
          result.result.acceptedName.dataSourceId shouldBe result.result.dataSource.id
        }
      }
    }

    "reduces score when first word is not normalized" in {
      val correctNameResponses = client.nameResolve(nr.Request(
        nameInputs = Seq(nr.NameInput("Homo sapiens"))
      )).value

      val incorrectGenusNameResponses = client.nameResolve(nr.Request(
        nameInputs = Seq(nr.NameInput("HomO sapiens"))
      )).value

      correctNameResponses.responses.size shouldBe incorrectGenusNameResponses.responses.size
      correctNameResponses.responses should not be empty
      for {
        (correctNameResponse, incorrectGenusNameResponse) <-
          correctNameResponses.responses.zip(incorrectGenusNameResponses.responses)
      } {
        correctNameResponse.resultsScored.size shouldBe
          incorrectGenusNameResponse.resultsScored.size
        correctNameResponse.resultsScored should not be empty

        for {
          (correctName, incorrectGenusName) <-
            correctNameResponse.resultsScored.zip(incorrectGenusNameResponse.resultsScored)
        } {
          correctName.result.name.uuid shouldBe incorrectGenusName.result.name.uuid
          correctName.score.value should be > incorrectGenusName.score.value
        }
      }
    }
  }
}
