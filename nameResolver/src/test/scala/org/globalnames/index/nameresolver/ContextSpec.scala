package org.globalnames
package index
package nameresolver

import com.google.inject.Stage
import com.twitter.finatra.thrift.EmbeddedThriftServer
import com.twitter.inject.server.FeatureTestMixin
import com.twitter.util.Future
import matcher.{MatcherModule, Server => MatcherServer}
import thrift.DataSourceQuality
import thrift.nameresolver.{NameInput, Request, Service => NameResolverService}

class ContextSpec extends WordSpecConfig with FeatureTestMixin {
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

  protected override def afterAll(): Unit = {
    super.afterAll()
    matcherServer.close()
    server.close()
  }

  val client: NameResolverService[Future] =
    server.thriftClient[NameResolverService[Future]](clientId = "nameResolverClient")

  "Context" should {
    "find context path correctly with Object" when {
      "single name is given" in {
        val name =
          "Plantae|Tracheophyta|Liliopsida|Poales|Poaceae|Digitaria|Digitaria divaricatissima"
        ContextFinder.find(Seq(name)) shouldBe name
      }

      "leaf is path" in {
        val names = Seq(
          "Plantae|Tracheophyta|Liliopsida|Poales|Poaceae|Digitaria|Digitaria divaricatissima",
          "Plantae|Tracheophyta|Liliopsida|Poales|Poaceae|Digitaria|Digitaria divaricatissima",
          "Plantae|Tracheophyta|Liliopsida|Poales|Poaceae|Digitaria|Digitaria divaricatissima",
          "Plantae|Tracheophyta|Liliopsida|Poales|Poaceae|Digitaria|Digitaria divaricatissima",
          "Plantae|Tracheophyta|Liliopsida|Poales|Poaceae|Digitaria|Digitaria divaricatissima",
          "Plantae|Tracheophyta|Magnoliopsida|Asterales|Campanulaceae|Downingia|Downingia concolor"
        )

        ContextFinder.find(names) shouldBe
          "Plantae|Tracheophyta|Liliopsida|Poales|Poaceae|Digitaria|Digitaria divaricatissima"
      }

      "branch is path" in {
        val names = Seq(
          "Plantae|Tracheophyta|Liliopsida|Poales|Poaceae|Digitaria|Digitaria divaricatissima",
          "Plantae|Tracheophyta|Magnoliopsida|Asterales|Campanulaceae|Downingia|Downingia concolor"
        )
        ContextFinder.find(names) shouldBe "Plantae|Tracheophyta"
      }
    }

    "return Context in response" in {
      val request = Request(nameInputs = Seq(
        NameInput("Achillea dobrogensis Prod."),
        NameInput("Achillea dobrogensis Prod."),
        NameInput("Achnatherum coreanum (Honda) Ohwi"),
        NameInput("Achnatherum coronatum"),
        NameInput("Achnatherum coronatum"),
        NameInput("Achnatherum coronatum (Thurb.) Barkworth"),
        NameInput("Homo sapiens")
      ), dataSourceIds = Seq(1))

      val response = client.nameResolve(request).value
      response.contexts.size shouldBe 1

      val ctx = response.contexts.headOption.value
      ctx.dataSource.id shouldBe 1
      ctx.dataSource.title shouldBe "Catalogue of Life"
      ctx.dataSource.quality shouldBe DataSourceQuality.Curated
      ctx.clade shouldBe "Plantae|Tracheophyta"
    }
  }
}
