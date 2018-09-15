package org.globalnames
package index
package api

import com.google.inject.{Provides, Singleton}
import com.twitter.app.Flag
import com.twitter.finagle.ThriftMux
import com.twitter.inject.TwitterModule
import thrift.{nameresolver => nr, namefilter => nf, namebrowser => nb, crossmapper => cm}

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

  val namebrowserServiceAddress: Flag[String] = flag(
    name = "namebrowserServiceAddress",
    help = "Host and port of namebrowser service",
    default = Properties.envOrElse("NAMEBROWSER_ADDRESS", "")
  )

  val crossmapperServiceAddress: Flag[String] = flag(
    name = "crossmapperServiceAddress",
    help = "Host and port of crossmapper service",
    default = Properties.envOrElse("CROSSMAPPER_ADDRESS", "")
  )

  @Singleton
  @Provides
  def provideNameResolverClient: nr.Service.MethodPerEndpoint =
    ThriftMux.client.build[nr.Service.MethodPerEndpoint](nameResolverServiceAddress())

  @Singleton
  @Provides
  def provideNameFilterClient: nf.Service.MethodPerEndpoint =
    ThriftMux.client.build[nf.Service.MethodPerEndpoint](namefilterServiceAddress())

  @Singleton
  @Provides
  def provideBrowserClient: nb.Service.MethodPerEndpoint =
    ThriftMux.client.build[nb.Service.MethodPerEndpoint](namebrowserServiceAddress())

  @Singleton
  @Provides
  def provideCrossmapperClient: cm.Service.MethodPerEndpoint =
    ThriftMux.client.build[cm.Service.MethodPerEndpoint](crossmapperServiceAddress())
}
