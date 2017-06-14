package org.globalnames
package microservices
package api

import com.google.inject.{Provides, Singleton}
import com.twitter.app.Flag
import com.twitter.finagle.ThriftMux
import com.twitter.inject.TwitterModule
import org.globalnames.microservices.index.thriftscala.IndexService

import scala.util.Properties

object ApiModule extends TwitterModule {
  val nameresolverServiceAddress: Flag[String] = flag(
    name = "nameresolverServiceAddress",
    help = "Host and port of nameresolver service",
    default = Properties.envOrElse("NAMERESOLVER_ADDRESS", "")
  )

  @Singleton
  @Provides
  def provideMatcherClient: IndexService.FutureIface =
    ThriftMux.client.newIface[IndexService.FutureIface](nameresolverServiceAddress())

}
