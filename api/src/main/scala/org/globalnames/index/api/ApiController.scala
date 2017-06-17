package org.globalnames
package index
package api

import javax.inject.{Inject, Singleton}

import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.twitter_util.UtilBijections._
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.util.{Future => TwitterFuture}
import sangria.execution.Executor
import sangria.marshalling.InputUnmarshaller
import sangria.parser.{QueryParser => QueryParserSangria}
import spray.json.JsValue

import scala.concurrent.ExecutionContext.Implicits.global
import sangria.marshalling.sprayJson._

case class GraphqlRequest(query: String,
                          variables: Option[Map[String, Any]] = None,
                          operation: Option[String] = None)

@Singleton
class ApiController @Inject()(repository: Repository) extends Controller {
  get("/") { req: Request =>
    response.ok.json("ready")
  }

  post("/api/graphql") { graphqlRequest: GraphqlRequest =>
    QueryParserSangria.parse(graphqlRequest.query) match {
      case util.Success(queryAst) =>
        val graphqlExecution = Executor.execute(
          schema = SchemaDefinition.schema,
          queryAst = queryAst,
          userContext = repository,
          variables = InputUnmarshaller.mapVars(graphqlRequest.variables.getOrElse(Map.empty)),
          operationName = graphqlRequest.operation
        )
        graphqlExecution.as[TwitterFuture[JsValue]]
                        .map { v => response.ok.json(v.prettyPrint) }

      case util.Failure(error) =>
        response.badRequest(error.getMessage)
    }
  }
}
