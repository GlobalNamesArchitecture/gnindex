package org.globalnames
package index
package dao

import java.util.UUID

import index.dao.{Tables => T}
import thrift._
import util.UuidEnhanced._
import parser.ScientificNameParser.{instance => SNP}
import slick.jdbc.PostgresProfile.api._
import com.github.tototoshi.slick.PostgresJodaSupport._
import org.joda.time.DateTime
import dao.{Projections => P}

object Projections {
  implicit object NameStringsShape
    extends CaseClassShape(NameStringsLifted.tupled, NameStrings.tupled)
  case class NameStrings(id: UUID, name: String,
                         canonicalUuid: Option[UUID], canonical: Option[String])
  case class NameStringsLifted(id: Rep[UUID], name: Rep[String],
                               canonicalUuid: Rep[Option[UUID]], canonical: Rep[Option[String]])

  implicit object NameStringIndicesShape
    extends CaseClassShape(NameStringIndicesLifted.tupled, NameStringIndices.tupled)
  case class NameStringIndices(taxonId: String,
                               acceptedTaxonId: Option[String],
                               classificationPath: Option[String],
                               classificationPathIds: Option[String],
                               classificationPathRanks: Option[String])
  case class NameStringIndicesLifted(taxonId: Rep[String],
                                     acceptedTaxonId: Rep[Option[String]],
                                     classificationPath: Rep[Option[String]],
                                     classificationPathIds: Rep[Option[String]],
                                     classificationPathRanks: Rep[Option[String]])

  implicit object DataSourcesShape
    extends CaseClassShape(DataSourcesLifted.tupled, DataSources.tupled)
  case class DataSources(id: Int, title: String, updatedAt: Option[DateTime])
  case class DataSourcesLifted(id: Rep[Int], title: Rep[String], updatedAt: Rep[Option[DateTime]])

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
      for {
        canId <- dbRes.ns.canonicalUuid
        canNameValue <- dbRes.ns.canonical
        canNameValueRanked <- SNP.fromString(dbRes.ns.name).canonized(showRanks = true)
      } yield CanonicalName(uuid = canId, value = canNameValue, valueRanked = canNameValueRanked)

    val classification = Classification(
      path = dbRes.nsi.classificationPath,
      pathIds = dbRes.nsi.classificationPathIds,
      pathRanks = dbRes.nsi.classificationPathRanks
    )

    val synonym = dbRes.acceptedName.isDefined
    val dataSourceRes = DataSource(id = dbRes.ds.id, title = dbRes.ds.title)

    val acceptedNameResult = {
      val anOpt = for {
        acpNm <- dbRes.acceptedName
      } yield {
        val canonicalNameOpt = for {
          nsCanId <- acpNm.canonicalUuid
          nsCan <- acpNm.canonical
          nsCanRanked <- SNP.fromString(acpNm.name).canonized(true)
        } yield CanonicalName(uuid = nsCanId, value = nsCan, valueRanked = nsCanRanked)
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
    val nsRep = P.NameStringsLifted(ns.id, ns.name, ns.canonicalUuid, ns.canonical)
    val nsiRep = P.NameStringIndicesLifted(nsi.taxonId, nsi.acceptedTaxonId,
      nsi.classificationPath, nsi.classificationPathIds, nsi.classificationPathRanks)
    val dsRep = P.DataSourcesLifted(ds.id, ds.title, ds.updatedAt)
    val acpNsRep: Rep[Option[P.NameStringsLifted]] =
      for (nsAcp <- nsAccepted)
        yield P.NameStringsLifted(nsAcp.id, nsAcp.name, nsAcp.canonicalUuid, nsAcp.canonical)
    P.ResultDBLifted(nsRep, nsiRep, dsRep, acpNsRep)
  }
}

