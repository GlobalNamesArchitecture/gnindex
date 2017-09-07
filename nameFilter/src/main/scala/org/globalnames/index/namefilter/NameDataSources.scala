package org.globalnames
package index
package namefilter

import java.util.UUID
import javax.inject.{Inject, Singleton}

import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.twitter_util.UtilBijections._
import com.twitter.util.{Future => TwitterFuture}
import dao.{Tables => T}
import thrift._
import thrift.namefilter._
import util.UuidEnhanced._
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class NameDataSources @Inject()(database: Database) {
  def resolve(nameUuids: Seq[Uuid]): TwitterFuture[Seq[NameStringUuidDataSources]] = {
    val nameUuidsJava: Seq[UUID] = nameUuids.map { u => u: UUID }
    val qry = for {
      nsi <- T.NameStringIndices if nsi.nameStringId.inSetBind(nameUuidsJava)
    } yield (nsi.nameStringId, nsi.dataSourceId)
    val resultFut = database.run(qry.result).map { dbResult =>
      dbResult.groupBy { case (uuid, _) => uuid }
              .mapValues { rs => rs.map { case (_, dsId) => dsId } }
              .map { case (uuid, dsIds) => NameStringUuidDataSources(uuid, dsIds) }
              .toSeq
    }
    resultFut.as[TwitterFuture[Seq[NameStringUuidDataSources]]]
  }
}
