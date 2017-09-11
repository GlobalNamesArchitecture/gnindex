package org.globalnames
package index
package dao

import index.dao.{Tables => T}
import thrift._
import util.UuidEnhanced._
import parser.ScientificNameParser.{instance => SNP}
import slick.lifted.Rep

case class DBResult(ns: T.NameStringsRow, nsi: T.NameStringIndicesRow, ds: T.DataSourcesRow,
                    nsiAcp: Option[T.NameStringIndicesRow], nsAcp: Option[T.NameStringsRow],
                    vernaculars: Seq[(T.VernacularStringIndicesRow, T.VernacularStringsRow)] =
                      Seq())

object DBResult {
  def create(dbRes: DBResult, matchType: MatchType): Result = {
    val acceptedNameOpt: Option[(T.NameStringsRow, T.NameStringIndicesRow)] = {
      for (ns <- dbRes.nsAcp; nsi <- dbRes.nsiAcp)
        yield (ns, nsi)
    }

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

    val synonym = acceptedNameOpt.isDefined
    val dataSourceRes = DataSource(id = dbRes.ds.id, title = dbRes.ds.title)

    val acceptedNameResult = {
      val anOpt = for ((ns, nsi) <- acceptedNameOpt) yield {
        val canonicalNameOpt =
          for { canId <- ns.canonicalUuid
                canNameValue <- ns.canonical
                canNameValueRanked <- SNP.fromString(ns.name).canonized(true) } yield {
            CanonicalName(uuid = canId, value = canNameValue, valueRanked = canNameValueRanked)
          }
        AcceptedName(
          name = Name(uuid = ns.id, value = ns.name),
          canonicalName = canonicalNameOpt,
          taxonId = nsi.taxonId,
          dataSourceId = nsi.dataSourceId
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

  def ofDb(ns: T.NameStrings, nsi: T.NameStringIndices, ds: T.DataSources,
           nsAccepted: Rep[Option[T.NameStrings]],
           nsiAccepted: Rep[Option[T.NameStringIndices]]) = {
    ns
  }
}

