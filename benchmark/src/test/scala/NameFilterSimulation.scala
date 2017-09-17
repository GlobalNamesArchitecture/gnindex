import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder

import scala.concurrent.duration._
import Helper.formatGraphQL

class NameFilterSimulation extends Simulation {
  val baseUrl: String = "http://index-api.globalnames.org"

  val httpConf: HttpProtocolBuilder = http
    .baseURL(baseUrl)
    .acceptHeader("application/json;q=0.9,*/*;q=0.8")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .acceptEncodingHeader("gzip, deflate")

  val canonicalModifier: ScenarioBuilder = {
    val graphql =
      """
        |{
        |  nameStrings(searchTerm: "can:Aaadonta constricta") {
        |    name {
        |      id
        |      value
        |    }
        |  }
        |}""".stripMargin
    scenario("nameFilter: canonical modifier")
      .exec(http("nameFilter: canonical modifier")
        .post("/api/graphql")
        .body(StringBody(
          s"""{"query":"${formatGraphQL(graphql)}","variables":null,"operationName":null}"""))
        .asJSON
        .header("Content-Type", "application/json")
        .check(
            status.is(200)
          , jsonPath("$..name.id").exists
        )
      )
  }

  val authorModifier: ScenarioBuilder = {
    val graphql =
      """
        |{
        |  nameStrings(searchTerm: "au:Abakar-Ousman") {
        |    name {
        |      id
        |      value
        |    }
        |  }
        |}""".stripMargin
    scenario("nameFilter: author modifier")
      .exec(http("nameFilter: author modifier")
        .post("/api/graphql")
        .body(StringBody(
          s"""{"query":"${formatGraphQL(graphql)}","variables":null,"operationName":null}"""))
        .asJSON
        .header("Content-Type", "application/json")
        .check(
            status.is(200)
          , jsonPath("$..name.id").exists
        )
      )
  }

  val yearModifier: ScenarioBuilder = {
    val graphql =
      """
        |{
        |  nameStrings(searchTerm: "yr:1753") {
        |    name {
        |      id
        |      value
        |    }
        |  }
        |}""".stripMargin
    scenario("nameFilter: year modifier")
      .exec(http("nameFilter: year modifier")
        .post("/api/graphql")
        .body(StringBody(
          s"""{"query":"${formatGraphQL(graphql)}","variables":null,"operationName":null}"""))
        .asJSON
        .header("Content-Type", "application/json")
        .check(
            status.is(200)
          , jsonPath("$..name.id").exists
        )
      )
  }

  val uninomialModifier: ScenarioBuilder = {
    val graphql =
      """
        |{
        |  nameStrings(searchTerm: "uni:Aalenirhynchia") {
        |    name {
        |      id
        |      value
        |    }
        |  }
        |}""".stripMargin
    scenario("nameFilter: uninomial modifier")
      .exec(http("nameFilter: uninomial modifier")
        .post("/api/graphql")
        .body(StringBody(
          s"""{"query":"${formatGraphQL(graphql)}","variables":null,"operationName":null}"""))
        .asJSON
        .header("Content-Type", "application/json")
        .check(
            status.is(200)
          , jsonPath("$..name.id").exists
        )
      )
  }

  val genusModifier: ScenarioBuilder = {
    val graphql =
      """
        |{
        |  nameStrings(searchTerm: "gen:Buxela") {
        |    name {
        |      id
        |      value
        |    }
        |  }
        |}""".stripMargin
    scenario("nameFilter: genus modifier")
      .exec(http("nameFilter: genus modifier")
        .post("/api/graphql")
        .body(StringBody(
          s"""{"query":"${formatGraphQL(graphql)}","variables":null,"operationName":null}"""))
        .asJSON
        .header("Content-Type", "application/json")
        .check(
            status.is(200)
          , jsonPath("$..name.id").exists
        )
      )
  }

  val speciesModifier: ScenarioBuilder = {
    val graphql =
      """
        |{
        |  nameStrings(searchTerm: "sp:cynoscion") {
        |    name {
        |      id
        |      value
        |    }
        |  }
        |}""".stripMargin
    scenario("nameFilter: species modifier")
      .exec(http("nameFilter: species modifier")
        .post("/api/graphql")
        .body(StringBody(
          s"""{"query":"${formatGraphQL(graphql)}","variables":null,"operationName":null}"""))
        .asJSON
        .header("Content-Type", "application/json")
        .check(
            status.is(200)
          , jsonPath("$..name.id").exists
        )
      )
  }

  val subSpeciesModifier: ScenarioBuilder = {
    val graphql =
      """
        |{
        |  nameStrings(searchTerm: "ssp:pubescens") {
        |    name {
        |      id
        |      value
        |    }
        |  }
        |}""".stripMargin
    scenario("nameFilter: subspecies modifier")
      .exec(http("nameFilter: subspecies modifier")
        .post("/api/graphql")
        .body(StringBody(
          s"""{"query":"${formatGraphQL(graphql)}","variables":null,"operationName":null}"""))
        .asJSON
        .header("Content-Type", "application/json")
        .check(
            status.is(200)
          , jsonPath("$..name.id").exists
        )
      )
  }

  val nameStringsModifier: ScenarioBuilder = {
    val graphql =
      """
        |{
        |  nameStrings(searchTerm: "ns:Aaadonta constricta babelthuapi") {
        |    name {
        |      id
        |      value
        |    }
        |  }
        |}""".stripMargin
    scenario("nameFilter: name_strings modifier")
      .exec(http("nameFilter: name_strings modifier")
        .post("/api/graphql")
        .body(StringBody(
          s"""{"query":"${formatGraphQL(graphql)}","variables":null,"operationName":null}"""))
        .asJSON
        .header("Content-Type", "application/json")
        .check(
            status.is(200)
          , jsonPath("$..name.id").exists
        )
      )
  }

  val exactModifier: ScenarioBuilder = {
    val graphql =
      """
        |{
        |  nameStrings(searchTerm: "exact:Aalenirhynchia") {
        |    name {
        |      id
        |      value
        |    }
        |  }
        |}""".stripMargin
    scenario("nameFilter: exact modifier")
      .exec(http("nameFilter: exact modifier")
        .post("/api/graphql")
        .body(StringBody(
          s"""{"query":"${formatGraphQL(graphql)}","variables":null,"operationName":null}"""))
        .asJSON
        .header("Content-Type", "application/json")
        .check(
            status.is(200)
          , jsonPath("$..name.id").exists
        )
      )
  }

  setUp(
       canonicalModifier.inject(constantUsersPerSec(10) during 5.seconds)
     , authorModifier.inject(constantUsersPerSec(10) during 5.seconds)
     , yearModifier.inject(constantUsersPerSec(10) during 5.seconds)
     , uninomialModifier.inject(constantUsersPerSec(10) during 5.seconds)
     , genusModifier.inject(constantUsersPerSec(10) during 5.seconds)
     , speciesModifier.inject(constantUsersPerSec(10) during 5.seconds)
     , subSpeciesModifier.inject(constantUsersPerSec(10) during 5.seconds)
     , nameStringsModifier.inject(constantUsersPerSec(10) during 5.seconds)
     , exactModifier.inject(constantUsersPerSec(10) during 5.seconds)
  ).protocols(httpConf)
}
