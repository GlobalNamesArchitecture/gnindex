package org.globalnames
package index
package crossmapper

import com.twitter.inject.Logging
import javax.inject.{Inject, Singleton}

import thrift.{crossmapper => cm}

@Singleton
class CrossMapper @Inject()() extends Logging {
  def resolve(databaseSourceId: Int, databaseSinkIds: Seq[Int], databaseTargetId: Option[Int],
              suppliedIds: Seq[String]): TwitterFuture[Seq[cm.Result]] = {
    logger.info(s"started src:$databaseSourceId sinks:${databaseSinkIds.mkString("|")} " +
      s"trg:$databaseTargetId supps:${suppliedIds.mkString("|")}")
    if (databaseSourceId < 0 || databaseSinkIds.exists { _ < 0 } ||
        databaseTargetId.exists { _ < 0 }) {
      val emptyResult =
        for (lid <- suppliedIds) yield {
          cm.Result(
            source = cm.Source(databaseSourceId, lid),
            target = Seq()
          )
        }
      TwitterFuture.value(emptyResult)
    } else {
      val localIdsSet = Set(suppliedIds: _*)
      val query = for {
        mapping <- T.CrossMaps.filter { cm =>
          cm.cmDataSourceId === databaseSourceId && cm.cmLocalId.inSetBind(localIdsSet)
        }
        target <- T.CrossMaps.filter { cm =>
          cm.dataSourceId === mapping.dataSourceId &&
            cm.taxonId === mapping.taxonId &&
            cm.nameStringId === mapping.nameStringId &&
            (databaseSinkIds.isEmpty.bind || cm.dataSourceId.inSetBind(databaseSinkIds)) &&
            (databaseTargetId.isEmpty.bind || cm.cmDataSourceId === databaseTargetId) &&
            cm.cmDataSourceId =!= databaseSourceId
        }
      } yield (mapping.cmLocalId, target)

      val res = for (mapped <- database.run(query.result)) yield {
        val m = mapped.foldLeft(Map.empty[String, Seq[T.CrossMapsRow]]) { case (acc, (k, v)) =>
          acc.updated(k, v +: acc.getOrElse(k, Seq()))
        }
        for (lid <- suppliedIds) yield {
          cm.Result(
            source = cm.Source(databaseSourceId, lid),
            target = m.getOrElse(lid, Seq()).map { t =>
              cm.Target(t.dataSourceId, t.cmDataSourceId, t.cmLocalId)
            })
        }
      }
      res.as[TwitterFuture[Seq[cm.Result]]]
    }
  }
}
