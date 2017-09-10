package org.globalnames
package index
package namefilter

import java.util.UUID
import javax.inject.{Inject, Singleton}

import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.twitter_util.UtilBijections._
import com.twitter.util.{Future => TwitterFuture}
import dao.{DBResult, Tables => T}
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
    val resultFut = database.run(qry.result).map { dbResult =>
      dbResult.groupBy { case (ns, _, _) => ns.id }
              .map { case (id, ress) =>
                val matchType =
                  MatchType(MatchKind.ExactNameMatchByUUID, editDistance = 0, score = 0)
                val dbResults = ress.map { case (ns, nsi, ds) =>
                  DBResult.create(ns, nsi, ds, None, None, Seq(), matchType)
                }
                Response(id, dbResults)
              }
              .toSeq
    }
    resultFut.as[TwitterFuture[Seq[Response]]]
  }
}
