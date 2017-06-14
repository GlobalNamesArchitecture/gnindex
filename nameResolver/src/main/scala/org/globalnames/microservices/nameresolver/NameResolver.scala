package org.globalnames
package microservices
package nameresolver

import javax.inject.{Inject, Singleton}

import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.twitter_util.UtilBijections._
import com.twitter.util.{Future => TwitterFuture}
import dao.Tables
import matcher.thriftscala.MatcherService
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future => ScalaFuture}

@Singleton
class NameResolver @Inject()(database: Database,
                             matcherClient: MatcherService.FutureIface) {

  def resolveExact(names: Seq[String]): TwitterFuture[Seq[String]] = {
    val fuzzyMatchesFuts = names.map { name =>
      matcherClient.findMatches(name).map { mtchs =>
        mtchs.map { _.value }
      }
    }
    val fuzzyMatchesFut = TwitterFuture.collect(fuzzyMatchesFuts).map { _.flatten }

    val nameStringsFut: ScalaFuture[Seq[Tables.NameStringsRow]] =
      database.run(dao.Tables.NameStrings.take(10).result)
    val databaseMatchesFut =
      nameStringsFut.as[TwitterFuture[Seq[Tables.NameStringsRow]]]
                    .map { nameStrings => nameStrings.map { _.name } }
    for {
      fuzzyMatches <- fuzzyMatchesFut
      databaseMatches <- databaseMatchesFut
    } yield fuzzyMatches ++ databaseMatches
  }
}
