package org.globalnames
package index
package nameresolver

import com.google.inject.{Provides, Singleton}
import com.twitter.app.Flag
import com.twitter.finagle.ThriftMux
import com.twitter.inject.TwitterModule
import thrift.matcher.{Service => MatcherService}
import slick.jdbc.PostgresProfile.api._
import scala.util.Properties

object NameResolverModule extends TwitterModule {
  val matcherServiceAddress: Flag[String] = flag(
    name = "matcherServiceAddress",
    help = "Host and port of matcher service",
    default = Properties.envOrElse("MATCHER_ADDRESS", "")
  )

  @Singleton
  @Provides
  def provideDatabase: Database = {
    val host = Properties.envOrElse("DB_HOST", "localhost")
    val port = Properties.envOrElse("DB_PORT", "5432")
    val database = Properties.envOrElse("DB_DATABASE", "development")
    val url = s"jdbc:postgresql://$host:$port/$database"
    Database.forURL(
      url = url,
      user = Properties.envOrElse("DB_USER", "postgres"),
      password = Properties.envOrElse("DB_USER_PASS", ""),
      driver = "org.postgresql.Driver"
    )
  }

  @Singleton
  @Provides
  def provideMatcherClient: MatcherService.FutureIface =
    ThriftMux.client.newIface[MatcherService.FutureIface](matcherServiceAddress())

}
