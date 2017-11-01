package org.globalnames
package index
package namefilter

import javax.inject.{Inject, Singleton}
import thrift.DataSource
import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.twitter_util.UtilBijections._
import com.twitter.inject.Logging
import com.twitter.util.{Future => TwitterFuture}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future => ScalaFuture}
import index.dao.{DBResultObj, Tables => T}
import index.dao.Projections._
import slick.jdbc.PostgresProfile.api._
import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.twitter_util.UtilBijections._
import com.twitter.inject.Logging
import com.twitter.util.{Future => TwitterFuture}
import index.dao.{DBResultObj, Tables => T}
import index.dao.Projections._
import thrift.matcher.{Service => MatcherService}
import thrift._
import thrift.nameresolver._
import thrift.{MatchKind => MK}
import util.UuidEnhanced._
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future => ScalaFuture}
import scalaz.syntax.std.boolean._
import scalaz.syntax.std.option._
import scalaz.Lens

@Singleton
class DataSourceByIdResolver @Inject()(database: Database) {
  def resolve(ids: Seq[Int]): TwitterFuture[Seq[DataSource]] = {
    val result =
      if (ids.isEmpty) {
        T.DataSources
      } else {
        T.DataSources.filter { ds => ds.id.inSetBind(ids) }
      }
    val r = database.run(result.map { ds => DBResultObj.projectDataSources(ds) }.result)
                    .map { xs => xs.map { x => DBResultObj.createDatasource(x) } }
    r.as[TwitterFuture[Seq[DataSource]]]
  }
}
