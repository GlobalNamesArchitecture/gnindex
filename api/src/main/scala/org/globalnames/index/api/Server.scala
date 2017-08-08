package org.globalnames.index.api

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.HttpServer
import com.twitter.finatra.http.filters._
import com.twitter.finatra.http.routing.HttpRouter

class Server extends HttpServer {
  override val name = "api-server"

  override val modules = Seq(ApiModule)

  override def configureHttp(router: HttpRouter): Unit = {
    val _ = router
      .filter[LoggingMDCFilter[Request, Response]]
      .filter[TraceIdMDCFilter[Request, Response]]
      .filter[CommonFilters]
      .add[ApiController]
  }
}

object ServerMain extends Server
