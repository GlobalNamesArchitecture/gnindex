package org.globalnames
package index
package namefilter

import com.twitter.finatra.thrift.ThriftServer
import com.twitter.finatra.thrift.filters._
import com.twitter.finatra.thrift.routing.ThriftRouter

class Server extends ThriftServer {
  override val name = "namefilter-server"

  override val modules = Seq(NameFilterModule)

  override def configureThrift(router: ThriftRouter): Unit = {
    val _ = router
      .filter[LoggingMDCFilter]
      .filter[TraceIdMDCFilter]
      .filter[ThriftMDCFilter]
      .filter[AccessLoggingFilter]
      .filter[StatsFilter]
      .filter[ExceptionTranslationFilter]
      .add[NameFilterController]
  }
}

object ServerMain extends Server
