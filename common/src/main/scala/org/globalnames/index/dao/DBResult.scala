package org.globalnames
package index
package dao

import java.util.UUID

import index.dao.{Tables => T}
import thrift._
import util.UuidEnhanced._
import slick.jdbc.PostgresProfile.api._
import com.github.tototoshi.slick.PostgresJodaSupport._
import org.joda.time.DateTime
import index.dao.{Projections => P}

object Projections {
  implicit object NameStringsShape
    extends CaseClassShape(NameStringsLifted.tupled, NameStrings.tupled)
  case class NameStrings(id: UUID, name: String, canonicalUuid: Option[UUID],
                         canonical: Option[String], canonicalRanked: Option[String])
  case class NameStringsLifted(id: Rep[UUID], name: Rep[String],
                               canonicalUuid: Rep[Option[UUID]], canonical: Rep[Option[String]],
                               canonicalRanked: Rep[Option[String]])

  implicit object NameStringIndicesShape
    extends CaseClassShape(NameStringIndicesLifted.tupled, NameStringIndices.tupled)
  case class NameStringIndices(taxonId: String,
                               acceptedTaxonId: Option[String],
                               localId: Option[String],
                               url: Option[String],
                               classificationPath: Option[String],
                               classificationPathIds: Option[String],
                               classificationPathRanks: Option[String])
  case class NameStringIndicesLifted(taxonId: Rep[String],
                                     acceptedTaxonId: Rep[Option[String]],
                                     localId: Rep[Option[String]],
                                     url: Rep[Option[String]],
                                     classificationPath: Rep[Option[String]],
                                     classificationPathIds: Rep[Option[String]],
                                     classificationPathRanks: Rep[Option[String]])

  implicit object DataSourcesShape
    extends CaseClassShape(DataSourcesLifted.tupled, DataSources.tupled)
  case class DataSources(id: Int, title: String, updatedAt: Option[DateTime],
                         isCurated: Boolean, isAutoCurated: Boolean,
                         recordCount: Int)
  case class DataSourcesLifted(id: Rep[Int], title: Rep[String], updatedAt: Rep[Option[DateTime]],
                               isCurated: Rep[Boolean], isAutoCurated: Rep[Boolean],
                               recordCount: Rep[Int])

  implicit object ResultDBShape
    extends CaseClassShape(ResultDBLifted.tupled, ResultDB.tupled)
  case class ResultDB(ns: P.NameStrings,
                      nsi: P.NameStringIndices,
                      ds: P.DataSources,
                      acceptedName: Option[P.NameStrings])
  case class ResultDBLifted(ns: P.NameStringsLifted,
                            nsi: P.NameStringIndicesLifted,
                            ds: P.DataSourcesLifted,
                            acceptedName: Rep[Option[P.NameStringsLifted]])
}

object DBResultObj {
  def create(dbRes: P.ResultDB, matchType: MatchType): Result = {
    val canonicalNameOpt =
      for (canId <- dbRes.ns.canonicalUuid; canNameValue <- dbRes.ns.canonical) yield {
        val canonicalRanked = dbRes.ns.canonicalRanked.getOrElse(canNameValue)
        CanonicalName(uuid = canId, value = canNameValue, valueRanked = canonicalRanked)
      }

    val classification = Classification(
      path = dbRes.nsi.classificationPath,
      pathIds = dbRes.nsi.classificationPathIds,
      pathRanks = dbRes.nsi.classificationPathRanks
    )

    val synonym = dbRes.acceptedName.isDefined

    val quality =
      if (dbRes.ds.isCurated) DataSourceQuality.Curated
      else if (dbRes.ds.isAutoCurated) DataSourceQuality.AutoCurated
      else DataSourceQuality.Unknown
    val dataSourceRes = DataSource(id = dbRes.ds.id, title = dbRes.ds.title,
                                   quality = quality, recordCount = dbRes.ds.recordCount)

    val acceptedNameResult = {
      val anOpt = for {
        acpNm <- dbRes.acceptedName
      } yield {
        val canonicalNameOpt =
          for (nsCanId <- acpNm.canonicalUuid; nsCan <- acpNm.canonical) yield {
            val canonicalRanked = dbRes.ns.canonicalRanked.getOrElse(nsCan)
            CanonicalName(uuid = nsCanId, value = nsCan, valueRanked = canonicalRanked)
          }
        AcceptedName(
          name = Name(uuid = acpNm.id, value = acpNm.name),
          canonicalName = canonicalNameOpt,
          taxonId = dbRes.nsi.taxonId,
          dataSourceId = dbRes.ds.id
        )
      }
      anOpt.getOrElse {
        AcceptedName(
          name = Name(uuid = dbRes.ns.id, value = dbRes.ns.name),
          canonicalName = canonicalNameOpt,
          taxonId = dbRes.nsi.taxonId,
          dataSourceId = dbRes.ds.id
        )
      }
    }

    val result = Result(
      name = Name(uuid = dbRes.ns.id, value = dbRes.ns.name),
      canonicalName = canonicalNameOpt,
      synonym = synonym,
      taxonId = dbRes.nsi.taxonId,
      localId = dbRes.nsi.localId,
      url = dbRes.nsi.url,
      matchType = matchType,
      classification = classification,
      dataSource = dataSourceRes,
      acceptedTaxonId = dbRes.nsi.acceptedTaxonId,
      acceptedName = acceptedNameResult,
      updatedAt = dbRes.ds.updatedAt.map { _.toString }
    )
    result
  }

  def project(ns: T.NameStrings,
              nsi: T.NameStringIndices,
              ds: T.DataSources,
              nsAccepted: Rep[Option[T.NameStrings]],
              nsiAccepted: Rep[Option[T.NameStringIndices]]): P.ResultDBLifted = {
    val nsRep = P.NameStringsLifted(ns.id, ns.name, ns.canonicalUuid,
                                    ns.canonical, ns.canonicalRanked)
    val nsiRep = P.NameStringIndicesLifted(nsi.taxonId, nsi.acceptedTaxonId, nsi.localId, nsi.url,
                                           nsi.classificationPath, nsi.classificationPathIds,
                                           nsi.classificationPathRanks)
    val dsRep = P.DataSourcesLifted(ds.id, ds.title, ds.updatedAt, ds.isCurated,
                                    ds.isAutoCurated, ds.recordCount)
    val acpNsRep: Rep[Option[P.NameStringsLifted]] =
      for (nsAcp <- nsAccepted) yield {
        P.NameStringsLifted(nsAcp.id, nsAcp.name, nsAcp.canonicalUuid,
                            nsAcp.canonical, nsAcp.canonicalRanked)
      }
    P.ResultDBLifted(nsRep, nsiRep, dsRep, acpNsRep)
  }
}

