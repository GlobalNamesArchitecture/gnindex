package org.globalnames
package index
package api

import com.google.inject.{Provides, Singleton}
import com.twitter.app.Flag
import com.twitter.finagle.ThriftMux
import com.twitter.inject.TwitterModule
import thrift.nameresolver.{Service => NameResolverService}

import scala.util.Properties

object ApiModule extends TwitterModule {
  val nameresolverServiceAddress: Flag[String] = flag(
    name = "nameresolverServiceAddress",
    help = "Host and port of nameresolver service",
    default = Properties.envOrElse("NAMERESOLVER_ADDRESS", "")
  )

  @Singleton
  @Provides
  def provideMatcherClient: NameResolverService.FutureIface =
    ThriftMux.client.newIface[NameResolverService.FutureIface](nameresolverServiceAddress())

}
