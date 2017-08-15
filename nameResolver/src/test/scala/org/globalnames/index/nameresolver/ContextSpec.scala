package org.globalnames
package index
package nameresolver

import com.google.inject.Stage
import com.twitter.finatra.thrift.EmbeddedThriftServer
import com.twitter.inject.server.FeatureTestMixin
import com.twitter.util.Future
import thrift.nameresolver.{Context, DataSource, NameInput, Request, Service => NameResolverService}

import scala.util.Properties

class ContextSpec extends SpecConfig with FeatureTestMixin {
  override val server = new EmbeddedThriftServer(
    twitterServer = new Server,
    stage = Stage.PRODUCTION,
    flags = Map(
      NameResolverModule.matcherServiceAddress.name -> Properties.envOrElse("MATCHER_ADDRESS", "")
    )
  )

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
      val request = Request(names = Seq(
        NameInput("Achillea dobrogensis Prod."),
        NameInput("Achnatherum coreanum (Honda) Ohwi"),
        NameInput("Achnatherum coronatum"),
        NameInput("Achnatherum coronatum (Thurb.) Barkworth"),
        NameInput("Homo sapiens")
      ), dataSourceIds = Seq(1)
      )

      client.nameResolve(request).value.context should contain only
        Context(
          dataSource = DataSource(id = 1, title = "Catalogue of Life"),
          clade = "Plantae|Tracheophyta"
        )
    }
  }
}
