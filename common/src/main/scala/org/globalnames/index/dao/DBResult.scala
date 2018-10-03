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
  case class DataSources(id: Int, title: String,
                         description: Option[String],
                         logoUrl: Option[String],
                         webSiteUrl: Option[String],
                         dataUrl: Option[String],
                         refreshPeriodDays: Option[Int],
                         uniqueNamesCount: Option[Int],
                         createdAt: Option[DateTime],
                         updatedAt: Option[DateTime],
                         dataHash: Option[String],
                         isCurated: Boolean, isAutoCurated: Boolean,
                         recordCount: Int)
  case class DataSourcesLifted(id: Rep[Int], title: Rep[String],
                               description: Rep[Option[String]],
                               logoUrl: Rep[Option[String]],
                               webSiteUrl: Rep[Option[String]],
                               dataUrl: Rep[Option[String]],
                               refreshPeriodDays: Rep[Option[Int]],
                               uniqueNamesCount: Rep[Option[Int]],
                               createdAt: Rep[Option[DateTime]],
                               updatedAt: Rep[Option[DateTime]],
                               dataHash: Rep[Option[String]],
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

  implicit object VernacularShape
    extends CaseClassShape(VernacularLifted.tupled, Vernacular.tupled)
  case class Vernacular(id: UUID,
                        name: String,
                        dataSourceId: Int)
  case class VernacularLifted(id: Rep[UUID],
                              name: Rep[String],
                              dataSourceId: Rep[Int])
}

object DBResultObj {
  def createDatasource(ds: P.DataSources): DataSource = {
    val quality =
      if (ds.isCurated) DataSourceQuality.Curated
      else if (ds.isAutoCurated) DataSourceQuality.AutoCurated
      else DataSourceQuality.Unknown
    val dataSourceRes = DataSource(id = ds.id, title = ds.title,
      description = ds.description,
      logoUrl = ds.logoUrl,
      webSiteUrl = ds.webSiteUrl,
      dataUrl = ds.dataUrl,
      refreshPeriodDays = ds.refreshPeriodDays,
      uniqueNamesCount = ds.uniqueNamesCount,
      createdAt = ds.createdAt.map { _.toString },
      updatedAt = ds.updatedAt.map { _.toString },
      quality = quality,
      recordCount = ds.recordCount)
    dataSourceRes
  }

  def create(dbRes: P.ResultDB,
             matchType: MatchType,
             vernaculars: Seq[P.Vernacular] = Seq()): Result = {
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

    val dataSourceRes = createDatasource(dbRes.ds)

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

    val vs = for (v <- vernaculars) yield {
      thrift.Vernacular(id = v.id, name = v.name, dataSourceId = v.dataSourceId)
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
      updatedAt = dbRes.ds.updatedAt.map { _.toString },
      vernaculars = vs
    )
    result
  }

  def projectDataSources(ds: T.DataSources): P.DataSourcesLifted = {
    P.DataSourcesLifted(ds.id, ds.title,
      ds.description,
      ds.logoUrl,
      ds.webSiteUrl,
      ds.dataUrl,
      ds.refreshPeriodDays,
      ds.uniqueNamesCount,
      ds.createdAt,
      ds.updatedAt,
      ds.dataHash,
      ds.isCurated,
      ds.isAutoCurated, ds.recordCount)
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
    val dsRep = projectDataSources(ds)
    val acpNsRep: Rep[Option[P.NameStringsLifted]] =
      for (nsAcp <- nsAccepted) yield {
        P.NameStringsLifted(nsAcp.id, nsAcp.name, nsAcp.canonicalUuid,
                            nsAcp.canonical, nsAcp.canonicalRanked)
      }
    P.ResultDBLifted(nsRep, nsiRep, dsRep, acpNsRep)
  }

  def projectVernacular(vs: T.VernacularStrings,
                        vsi: T.VernacularStringIndices): Projections.VernacularLifted = {
    P.VernacularLifted(vs.id, vs.name, vsi.dataSourceId)
  }

}

