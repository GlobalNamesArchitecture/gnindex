package org.globalnames
package index
package dao

import dao.{Tables => T}
import thrift._
import util.UuidEnhanced._
import parser.ScientificNameParser.{instance => SNP}


object DBResult {
  def create(nameString: T.NameStringsRow,
             nameStringIndex: T.NameStringIndicesRow,
             dataSource: T.DataSourcesRow,
             acceptedNameStringRow: Option[T.NameStringsRow],
             acceptedNameStringIndexRow: Option[T.NameStringIndicesRow],
             vernaculars: Seq[(T.VernacularStringIndicesRow, T.VernacularStringsRow)],
             matchType: MatchType): Result = {
    val acceptedNameOpt: Option[(T.NameStringsRow, T.NameStringIndicesRow)] = {
      for (ns <- acceptedNameStringRow; nsi <- acceptedNameStringIndexRow)
        yield (ns, nsi)
    }

    val canonicalNameOpt =
      for {
        canId <- nameString.canonicalUuid
        canNameValue <- nameString.canonical
        canNameValueRanked <- SNP.fromString(nameString.name).canonized(showRanks = true)
      } yield CanonicalName(uuid = canId, value = canNameValue, valueRanked = canNameValueRanked)

    val classification = Classification(
      path = nameStringIndex.classificationPath,
      pathIds = nameStringIndex.classificationPathIds,
      pathRanks = nameStringIndex.classificationPathRanks
    )

    val synonym = acceptedNameOpt.isDefined
    val dataSourceRes = DataSource(id = dataSource.id, title = dataSource.title)

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
          name = Name(uuid = nameString.id, value = nameString.name),
          canonicalName = canonicalNameOpt,
          taxonId = nameStringIndex.taxonId,
          dataSourceId = dataSource.id
        )
      }
    }

    val result = Result(
      name = Name(uuid = nameString.id, value = nameString.name),
      canonicalName = canonicalNameOpt,
      synonym = synonym,
      taxonId = nameStringIndex.taxonId,
      matchType = matchType,
      classification = classification,
      dataSource = dataSourceRes,
      acceptedTaxonId = nameStringIndex.acceptedTaxonId,
      acceptedName = acceptedNameResult,
      updatedAt = dataSource.updatedAt.map { _.toString }
    )
    result
  }
}

