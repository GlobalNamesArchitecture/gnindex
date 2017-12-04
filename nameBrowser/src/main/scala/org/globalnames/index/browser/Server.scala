package org.globalnames
package index
package browser

import com.twitter.finatra.thrift.ThriftServer
import com.twitter.finatra.thrift.filters._
import com.twitter.finatra.thrift.routing.ThriftRouter

class Server extends ThriftServer {
  override val name = "browser-server"

  override val modules = Seq(BrowserModule)

  override def configureThrift(router: ThriftRouter): Unit = {
    val _ = router
      .filter[LoggingMDCFilter]
      .filter[TraceIdMDCFilter]
      .filter[ThriftMDCFilter]
      .filter[AccessLoggingFilter]
      .filter[StatsFilter]
      .filter[ExceptionTranslationFilter]
      .add[BrowserController]
  }
}

object ServerMain extends Server
