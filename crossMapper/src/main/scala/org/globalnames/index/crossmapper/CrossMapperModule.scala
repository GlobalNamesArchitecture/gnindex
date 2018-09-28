package org.globalnames
package index
package crossmapper

import com.google.inject.{Provides, Singleton}
import com.twitter.inject.{Logging, TwitterModule}
import slick.jdbc.PostgresProfile.api._

import scala.util.Properties

object CrossMapperModule extends TwitterModule with Logging {
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
}
