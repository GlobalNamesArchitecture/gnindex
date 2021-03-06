package org.globalnames
package index
package api

import javax.inject.{Inject, Singleton}
import com.fasterxml.jackson.databind.JsonNode
import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.twitter_util.UtilBijections._
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.util.{Future => TwitterFuture}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import sangria.execution.Executor
import sangria.marshalling.json4s.jackson._
import sangria.parser.{QueryParser => QueryParserSangria}
import sangria.validation.QueryValidator

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

final case class GraphqlRequest(query: String,
                                variables: Option[JsonNode],
                                operation: Option[JsonNode]) {
  val variablesJson: JValue =
    variables.map { fromJsonNode }.getOrElse(JObject())

  val operationJson: Option[String] =
    operation.map { fromJsonNode }.collect { case JString(op) => op }
}

@Singleton
class ApiController @Inject()(repository: Repository) extends Controller {
  private def errorsResponse(errorMessages: Vector[String]): JValue = {
    val errorsJs =
      for (em <- errorMessages.toList) yield {
        JObject("message" -> JString(em))
      }
    JObject("errors" -> JArray(errorsJs))
  }

  get("/api/version") { _: Request =>
    response.ok.json(org.globalnames.index.BuildInfo.version)
  }

  get("/api/graphql/schema") { _: Request =>
    response.ok.json(SchemaDefinition.schema.renderPretty)
  }

  post("/api/graphql") { graphqlRequest: GraphqlRequest =>
    QueryParserSangria.parse(graphqlRequest.query) match {
      case Success(queryAst) =>
        val violations = QueryValidator.default.validateQuery(SchemaDefinition.schema, queryAst)
        if (violations.isEmpty) {
          val graphqlExecution = Executor.execute(
            schema = SchemaDefinition.schema,
            queryAst = queryAst,
            userContext = repository,
            variables = graphqlRequest.variablesJson,
            operationName = graphqlRequest.operationJson
          )
          graphqlExecution.as[TwitterFuture[JValue]]
                          .map { v => response.ok.json(compact(render(v))) }
        } else {
          val errorsJValue = errorsResponse(violations.map { _.errorMessage })
          response.badRequest.json(compact(render(errorsJValue)))
        }

      case Failure(error) =>
        val errorsJValue = errorsResponse(Vector(error.getMessage))
        response.badRequest.json(compact(render(errorsJValue)))
    }
  }
}
