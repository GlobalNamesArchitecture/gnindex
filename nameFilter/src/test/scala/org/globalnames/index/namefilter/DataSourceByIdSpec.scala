package org.globalnames
package index
package namefilter

import com.google.inject.Stage
import com.twitter.finatra.thrift.EmbeddedThriftServer
import com.twitter.inject.server.FeatureTestMixin
import com.twitter.util.Future
import org.globalnames.index.matcher.{MatcherModule, Server => MatcherServer}
import org.globalnames.index.thrift.namefilter.{Service => NameFilterService}

class DataSourceByIdSpec extends FunSpecConfig with FeatureTestMixin {
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
      NameFilterModule.matcherServiceAddress.name -> matcherServer.thriftHostAndPort
    )
  )

  protected override def afterAll(): Unit = {
    super.afterAll()
    matcherServer.close()
    server.close()
  }

  val nameFilterClient: NameFilterService[Future] =
    server.thriftClient[NameFilterService[Future]](clientId = "nameFilterClient")

  describe("NameFilter") {
    describe(".resolveDataSourceById") {
      it("returns all data sources when no IDs are provided") {
        val results = nameFilterClient.dataSourceById(dataSourceIds = Seq()).value
        results.size should be >= 100
      }

      it("handles existing ID") {
        val dataSourceId = 1

        val results = nameFilterClient.dataSourceById(dataSourceIds = Seq(dataSourceId)).value
        results.size shouldBe 1

        val result = results.headOption.value
        result.id shouldBe dataSourceId
      }

      it("handles multiple existing IDs") {
        val dataSourceIds = Seq(1, 2)

        val results = nameFilterClient.dataSourceById(dataSourceIds).value
        results.size shouldBe 2

        results.map { _.id } should contain theSameElementsAs dataSourceIds
      }

      it("handles repeated IDs") {
        val dataSourceId = 1
        val dataSourceIds = Seq(dataSourceId, dataSourceId)

        val results = nameFilterClient.dataSourceById(dataSourceIds).value
        results.size shouldBe 1

        val result = results.headOption.value
        result.id shouldBe dataSourceId
      }

      it("handles non-existing ID") {
        val results1 = nameFilterClient.dataSourceById(dataSourceIds = Seq(7777)).value
        results1.size shouldBe 0

        val results2 = nameFilterClient.dataSourceById(dataSourceIds = Seq(-1, 7777)).value
        results2.size shouldBe 0

        val results3 = nameFilterClient.dataSourceById(dataSourceIds = Seq(1, -1, 7777)).value
        results3.size shouldBe 1
      }
    }
  }
}
