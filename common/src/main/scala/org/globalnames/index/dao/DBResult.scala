package org.globalnames
package index
package dao

import dao.{Tables => T}
import thrift._
import util.UuidEnhanced._
import parser.ScientificNameParser.{Result => SNResult, instance => SNP}

final case class DBResult(
  nameString: T.NameStringsRow,
  nameStringIndex: T.NameStringIndicesRow,
  dataSource: T.DataSourcesRow,
  acceptedNameOpt: Option[(T.NameStringsRow, T.NameStringIndicesRow)],
  vernaculars: Seq[(T.VernacularStringIndicesRow, T.VernacularStringsRow)])

object DBResult {
  def create(nameInputParsed: SNResult,
             dbResult: DBResult,
             matchType: MatchType): ResultScored = {
    val canonicalNameOpt =
      for { canId <- dbResult.nameString.canonicalUuid
            canNameValue <- dbResult.nameString.canonical
            canNameValueRanked <- nameInputParsed.canonized(showRanks = true) }
      yield CanonicalName(uuid = canId, value = canNameValue, valueRanked = canNameValueRanked)

    val classification = Classification(
      path = dbResult.nameStringIndex.classificationPath,
      pathIds = dbResult.nameStringIndex.classificationPathIds,
      pathRanks = dbResult.nameStringIndex.classificationPathRanks
    )

    val synonym = dbResult.acceptedNameOpt.isDefined
    val dataSource = DataSource(id = dbResult.dataSource.id, title = dbResult.dataSource.title)

    val acceptedNameResult = {
      val anOpt = for ((ns, nsi) <- dbResult.acceptedNameOpt) yield {
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
          name = Name(uuid = dbResult.nameString.id, value = dbResult.nameString.name),
          canonicalName = canonicalNameOpt,
          taxonId = dbResult.nameStringIndex.taxonId,
          dataSourceId = dbResult.dataSource.id
        )
      }
    }

    val result = Result(
      name = Name(uuid = dbResult.nameString.id, value = dbResult.nameString.name),
      canonicalName = canonicalNameOpt,
      synonym = synonym,
      taxonId = dbResult.nameStringIndex.taxonId,
      matchType = matchType,
      classification = classification,
      dataSource = dataSource,
      acceptedTaxonId = dbResult.nameStringIndex.acceptedTaxonId,
      acceptedName = acceptedNameResult,
      updatedAt = dbResult.dataSource.updatedAt.map { _.toString }
    )
    val score = ResultScores.compute(nameInputParsed, result)
    ResultScored(result, score)
  }
}

