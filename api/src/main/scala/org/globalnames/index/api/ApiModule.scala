package org.globalnames
package index
package api

import com.google.inject.{Provides, Singleton}
import com.twitter.app.Flag
import com.twitter.finagle.ThriftMux
import com.twitter.inject.TwitterModule
import thrift.nameresolver.{Service => NameResolverService}
import thrift.namefilter.{Service => NameFilterService}

import scala.util.Properties

object ApiModule extends TwitterModule {
  val nameResolverServiceAddress: Flag[String] = flag(
    name = "nameresolverServiceAddress",
    help = "Host and port of nameresolver service",
    default = Properties.envOrElse("NAMERESOLVER_ADDRESS", "")
  )

  val namefilterServiceAddress: Flag[String] = flag(
    name = "namefilterServiceAddress",
    help = "Host and port of namefilter service",
    default = Properties.envOrElse("NAMEFILTER_ADDRESS", "")
  )

  @Singleton
  @Provides
  def provideNameResolverClient: NameResolverService.FutureIface =
    ThriftMux.client.newIface[NameResolverService.FutureIface](nameResolverServiceAddress())

  @Singleton
  @Provides
  def provideNameFilterClient: NameFilterService.FutureIface =
    ThriftMux.client.newIface[NameFilterService.FutureIface](namefilterServiceAddress())
}
