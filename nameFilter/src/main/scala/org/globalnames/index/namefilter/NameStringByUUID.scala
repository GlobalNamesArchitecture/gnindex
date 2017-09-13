package org.globalnames
package index
package namefilter

import java.util.UUID
import javax.inject.{Inject, Singleton}

import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.twitter_util.UtilBijections._
import com.twitter.util.{Future => TwitterFuture}
import index.dao.{DBResultObj, Tables => T}
import index.dao.Projections._
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

    val queryJoin = qry
      .joinLeft(T.NameStringIndices).on { case ((_, nsi_l, _), nsi_r) =>
        nsi_l.acceptedTaxonId =!= "" &&
          nsi_l.dataSourceId === nsi_r.dataSourceId && nsi_l.acceptedTaxonId === nsi_r.taxonId
      }
      .joinLeft(T.NameStrings).on { case ((_, nsi), ns) => ns.id === nsi.map { _.nameStringId } }

    val queryJoin1 =
      for ((((ns, nsi, ds), nsiAccepted), nsAccepted) <- queryJoin) yield {
        DBResultObj.project(ns, nsi, ds, nsAccepted, nsiAccepted)
      }

    val resultFut = database.run(queryJoin1.result).map { (queryResult: Seq[ResultDB]) =>
      queryResult.groupBy { dbR => dbR.ns.id }
                 .map { case (id, ress) =>
                   val matchType =
                     MatchType(MatchKind.ExactNameMatchByUUID, editDistance = 0, score = 0)
                   val results = ress.map { res => DBResultObj.create(res, matchType) }
                   Response(id, results)
                 }.toSeq
    }
    resultFut.as[TwitterFuture[Seq[Response]]]
  }
}
