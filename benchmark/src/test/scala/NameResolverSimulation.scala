import io.gatling.core.Predef._
import io.gatling.core.feeder.Record
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder

import scala.concurrent.duration._
import scala.io.Source
import scala.util.Random
import Helper._

class NameResolverSimulation extends Simulation {

  val baseUrl: String = "http://index-api.globalnames.org"

  val customFeeder = new Feeder[String] {
    val rnd = new Random(42)
    val lines =
      Source.fromURL(getClass.getResource("/scientific_names.csv"), "UTF-8").getLines.map {
        line => line.split(",")(1)
      }.toArray

    override def hasNext = true

    override def next(): Record[String] = {
      val namesBatch = Array.fill(500)(lines(rnd.nextInt(lines.length)))

      val graphql = """
          |query ($names: [name!]!, $dataSourceIds: [Int!]) {
          |  nameResolver(names: $names, dataSourceIds: $dataSourceIds, advancedResolution: false) {
          |    responses {
          |      results {
          |        name {
          |          id
          |          value
          |        }
          |        synonym
          |        dataSource {
          |          id
          |          title
          |        }
          |        matchType {
          |          editDistance
          |          kind
          |        }
          |      }
          |    }
          |  }
          |}""".stripMargin
      val variables = s"""
          |{
          |  "names": [
          |    ${namesBatch.map { n => s""" { "value": "$n" } """ }.mkString(", ")}
          |  ],
          |  "dataSourceIds": [1]
          |}""".stripMargin.replace("\n", "")
      val qry =
        s"""{"query":"${formatGraphQL(graphql)}","variables":${variables}, "operationName":null}"""

      Map(
        "graphql-query" -> qry
      )
    }
  }

  val httpConf: HttpProtocolBuilder = http
    .baseURL(baseUrl)
    .acceptHeader("application/json;q=0.9,*/*;q=0.8")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .acceptEncodingHeader("gzip, deflate")

  val versionScenario: ScenarioBuilder = scenario("api/version")
    .exec(http("api/version")
      .get("/api/version")
      .check(status.is(200), bodyString.is("0.1.0-SNAPSHOT"))
    )

  val exactNameMatchByUuid: ScenarioBuilder = {
    val graphql =
      """
        |{
        |  nameResolver(names: {value: "Homo sapiens Linnaeus, 1758"}, dataSourceIds: [1]) {
        |    responses {
        |      results {
        |        name {
        |          id
        |          value
        |        }
        |        matchType {
        |          kind
        |        }
        |      }
        |    }
        |  }
        |}""".stripMargin
    scenario("nameResolver: exact name by UUID")
      .exec(http("nameResolver: exact name by UUID")
        .post("/api/graphql")
        .body(StringBody(
          s"""{"query":"${formatGraphQL(graphql)}","variables":null,"operationName":null}"""))
        .asJSON
        .header("Content-Type", "application/json")
        .check(
          status.is(200),
          jsonPath("$..name.id").is("7db4f8a2-aafe-56b6-8838-89522c67d9f0"),
          jsonPath("$..matchType.kind").is("ExactNameMatchByUUID")
        )
      )
  }

  val exactCanonicalNameMatchByUuid: ScenarioBuilder = {
    val graphql =
      """
        |{
        |  nameResolver(names: {value: "Homo sapiens"}, dataSourceIds: [1]) {
        |    responses {
        |      results {
        |        name {
        |          id
        |          value
        |        }
        |        matchType {
        |          kind
        |        }
        |      }
        |    }
        |  }
        |}""".stripMargin
    scenario("nameResolver: exact canonical name by UUID")
      .exec(http("nameResolver: exact canonical name by UUID")
        .post("/api/graphql")
        .body(StringBody(
          s"""{"query":"${formatGraphQL(graphql)}","variables":null,"operationName":null}"""))
        .asJSON
        .header("Content-Type", "application/json")
        .check(
          status.is(200),
          jsonPath("$..name.id").is("7db4f8a2-aafe-56b6-8838-89522c67d9f0"),
          jsonPath("$..matchType.kind").is("ExactCanonicalNameMatchByUUID")
        )
      )
  }

  val feedData: ScenarioBuilder = {
    scenario("nameResolver: name requests batch")
      .feed(customFeeder)
      .exec(http("nameResolver: name requests batch")
        .post("/api/graphql")
        .body(StringBody("${graphql-query}"))
        .asJSON
        .check(
          status.is(200),
          jsonPath("$..results").exists
        )
      )
  }

  setUp(
      versionScenario.inject(constantUsersPerSec(10) during 5.seconds)
    , exactNameMatchByUuid.inject(constantUsersPerSec(10) during 5.seconds)
    , exactCanonicalNameMatchByUuid.inject(constantUsersPerSec(10) during 5.seconds)
    , feedData.inject(constantUsersPerSec(2) during 10.seconds)
  ).protocols(httpConf)
}
