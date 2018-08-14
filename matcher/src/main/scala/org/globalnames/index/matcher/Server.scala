package org.globalnames
package index
package matcher

import com.twitter.finatra.thrift.ThriftServer
import com.twitter.finatra.thrift.{filters => thriftfilters}
import com.twitter.finatra.thrift.routing.ThriftRouter
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.HttpServer
import com.twitter.finatra.http.{filters => httpfilters}
import com.twitter.finatra.http.routing.HttpRouter

class Server extends ThriftServer with HttpServer {
  override val name = "matcher-server"

  override val modules = Seq(MatcherModule)

  override def configureThrift(router: ThriftRouter): Unit = {
    val _ = router
      .filter[thriftfilters.LoggingMDCFilter]
      .filter[thriftfilters.TraceIdMDCFilter]
      .filter[thriftfilters.ThriftMDCFilter]
      .filter[thriftfilters.AccessLoggingFilter]
      .filter[thriftfilters.StatsFilter]
      .filter[ExceptionTranslationFilter]
      .add[MatcherController]
  }

  override def configureHttp(router: HttpRouter): Unit = {
    val _ = router
      .filter[httpfilters.LoggingMDCFilter[Request, Response]]
      .filter[httpfilters.TraceIdMDCFilter[Request, Response]]
      .filter[httpfilters.CommonFilters]
      .add[MatcherControllerHttp]
  }
}

object ServerMain extends Server
