package org.globalnames
package index
package namefilter

import java.util.UUID
import javax.inject.{Inject, Singleton}

import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.twitter_util.UtilBijections._
import com.twitter.util.{Future => TwitterFuture}
import index.dao.{DBResult, Tables => T}
import thrift.{Uuid, MatchKind, MatchType}
import thrift.namefilter.Response
import util.UuidEnhanced._
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class NameStringByUUID @Inject()(database: Database) {
  def resolve(nameUuids: Seq[Uuid]): TwitterFuture[Seq[Response]] = {
    val nameUuidsJava: Seq[UUID] = nameUuids.map { u => u: UUID }
    val qry = for {
      ns <- T.NameStrings if ns.id.inSetBind(nameUuidsJava)
      nsi <- T.NameStringIndices if ns.id === nsi.nameStringId
      ds <- T.DataSources if nsi.dataSourceId === ds.id
    } yield (ns, nsi, ds)
    val resultFut = database.run(qry.result).map { queryResult =>
      queryResult.map { case (ns, nsi, ds) => DBResult(ns, nsi, ds, None, None) }
                 .groupBy { dbR => dbR.ns.id }
                 .map { case (id, ress) =>
                   val matchType =
                     MatchType(MatchKind.ExactNameMatchByUUID, editDistance = 0, score = 0)
                   val results = ress.map { res => DBResult.create(res, matchType) }
                   Response(id, results)
                 }.toSeq
    }
    resultFut.as[TwitterFuture[Seq[Response]]]
  }
}
