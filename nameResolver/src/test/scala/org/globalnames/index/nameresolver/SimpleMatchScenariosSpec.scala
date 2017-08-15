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
import scalaz.syntax.std.option._

class SimpleMatchScenariosSpec extends SpecConfig with FeatureTestMixin {
  override val server = new EmbeddedThriftServer(
    twitterServer = new Server,
    stage = Stage.PRODUCTION,
    flags = Map(
      NameResolverModule.matcherServiceAddress.name -> Properties.envOrElse("MATCHER_ADDRESS", "")
    )
  )

  val client: NameResolverService[Future] =
    server.thriftClient[NameResolverService[Future]](clientId = "nameResolverClient")

  "index resolve" should {
    val dataSourceId = 1

    "match 'Homo sapiens Linnaeus, 1758' exactly by name UUID" in {
      val response = client.nameResolve(Request(
        names = Seq(NameInput("Homo sapiens Linnaeus, 1758")),
        dataSourceIds = Seq(dataSourceId))).value.items
      response should have size 1
      val results = response.headOption.value.results
      results should have size 1
      val result = results.headOption.value
      result.result.dataSource.id shouldBe dataSourceId
      result.result.name.value shouldBe "Homo sapiens Linnaeus, 1758"
      result.result.canonicalName.value.value shouldBe "Homo sapiens"
      result.result.matchType.kind shouldBe MatchKind.ExactNameMatchByUUID
    }

    "match 'Homo sapiens XXX, 1999' exactly by canonical UUID" in {
      val response = client.nameResolve(Request(
        names = Seq(NameInput("Homo sapiens XXX, 1999")),
        dataSourceIds = Seq(dataSourceId))).value.items
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
        dataSourceIds = Seq(dataSourceId))).value.items
      response.headOption.value.suppliedInput.value shouldBe "Homo sapiens Linnaeus, 1758"
    }

    "not have suppliedId if not given" in {
      val response = client.nameResolve(Request(
        names = Seq(NameInput("Homo sapiens Linnaeus, 1758")),
        dataSourceIds = Seq(dataSourceId))).value.items
      response.headOption.value.suppliedId shouldBe None
    }

    "preserve suppliedId" in {
      val suppliedId = "abc"
      val response = client.nameResolve(Request(
        names = Seq(NameInput("Homo sapiens Linnaeus, 1758", suppliedId = suppliedId.some)),
        dataSourceIds = Seq(dataSourceId))).value.items
      response.headOption.value.suppliedId.value shouldBe suppliedId
    }

    "have results from requested data sources" in {
      val inputDataSourceIds = Seq(1, 2, 3)
      val request = Request(names = Seq(
        NameInput("Actinodontium rhaphidostegium"),
        NameInput("Andreaea heinemanii"),
        NameInput("Homo sapiens"),
        NameInput("Bryum capillare")
      ), dataSourceIds = inputDataSourceIds)
      val response = client.nameResolve(request).value.items
      val dataSourceIdsResponse =
        response.flatMap { resp => resp.results.map { res => res.result.dataSource.id }}
      dataSourceIdsResponse should contain only (1, 3)
    }

    "handle preferred data sources" when {
      "data sources are provided" in {
        val inputDataSourceIds = Seq(1, 2)
        val preferredDataSourceIds = Seq(3, 4)
        val request = Request(
          names = Seq(NameInput("Homo sapiens Linnaeus, 1758")),
          dataSourceIds = inputDataSourceIds,
          preferredDataSourceIds = preferredDataSourceIds)
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
          preferredDataSourceIds = preferredDataSourceIds)
        val response = client.nameResolve(request).value.items.headOption.value

        val dataSourceIdsResponse = response.results.map { _.result.dataSource.id }
        val preferredDataSourceIdsResponse = response.preferredResults.map { _.result.dataSource.id}
        dataSourceIdsResponse should not be empty
        preferredDataSourceIdsResponse should not be empty
        preferredDataSourceIds should contain allElementsOf dataSourceIdsResponse
        preferredDataSourceIds should contain allElementsOf preferredDataSourceIdsResponse
      }
    }
  }
}
