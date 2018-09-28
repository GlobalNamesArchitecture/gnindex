package org.globalnames
package index
package crossmapper

import javax.inject.{Inject, Singleton}

import thrift.{crossmapper => cm}
import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.twitter_util.UtilBijections._
import com.twitter.inject.Logging
import com.twitter.util.{Future => TwitterFuture}
import index.dao.{Tables => T}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class CrossMapper @Inject()(database: Database) extends Logging {
  def resolve(databaseSourceId: Int,
              databaseTargetId: Int,
              suppliedIds: Seq[String]): TwitterFuture[Seq[cm.Result]] = {

    val query = for {
      nsSrc <- T.NameStrings
      nsiSrc <- T.NameStringIndices.filter { nsi =>
        nsi.nameStringId === nsSrc.id &&
          nsi.localId.inSetBind(suppliedIds) &&
          nsi.dataSourceId === databaseSourceId
      }
      nsTrg <- T.NameStrings.filter { ns =>
        ns.canonicalUuid === nsSrc.canonicalUuid
      }
      nsiTrg <- T.NameStringIndices.filter { nsi =>
        nsi.nameStringId === nsTrg.id &&
          nsi.dataSourceId === databaseTargetId
      }
    } yield (nsiSrc.dataSourceId, nsiSrc.localId, nsiTrg.dataSourceId, nsiTrg.localId)

    val result =
      database.run(query.distinct.result).map { xs =>
        xs.groupBy { case (srcDbId, srcLocId, _, _) => (srcDbId, srcLocId) }
          .map { case ((srcDbId, srcLocId), trgsData) =>
            val src = cm.Source(srcDbId, srcLocId.getOrElse(""))
            val trgs = trgsData.map { case (_, _, trgDbId, trgLocId) =>
              cm.Target(trgDbId, trgLocId.getOrElse(""))
            }
            cm.Result(
              source = src,
              target = trgs
            )
          }
          .toSeq
      }

    result.as[TwitterFuture[Seq[cm.Result]]]
  }
}
