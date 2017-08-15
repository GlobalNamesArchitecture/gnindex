package org.globalnames
package index
package namefilter

import java.util.UUID
import javax.inject.{Inject, Singleton}

import akka.http.impl.util._
import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.twitter_util.UtilBijections._
import com.twitter.inject.Logging
import com.twitter.util.{Future => TwitterFuture}
import org.apache.commons.lang3.StringUtils
import dao.{Tables => T}
import thrift.matcher.{Service => MatcherService}
import thrift._
import thrift.namefilter._
import util.UuidEnhanced._
import parser.ScientificNameParser.{Result => SNResult, instance => SNP}
import slick.jdbc.PostgresProfile.api._
import scala.util.{Try, Success, Failure}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future => ScalaFuture}
import scalaz.syntax.std.boolean._
import scalaz.syntax.std.option._
import org.apache.commons.lang3.StringUtils.capitalize
import parser.ScientificNameParser.{Result => SNResult, instance => SNP}

@Singleton
class NameFilter @Inject()(database: Database) {
  import NameFilter._

  private val unaccent = SimpleFunction.unary[String, String]("unaccent")
  private val unaccentOpt = SimpleFunction.unary[Option[String], Option[String]]("unaccent")

  final case class RequestResponse(request: Request, response: ResultScored)
  final case class DBResult(
    nameString: T.NameStringsRow,
    nameStringIndex: T.NameStringIndicesRow,
    dataSource: T.DataSourcesRow,
    acceptedNameOpt: Option[(T.NameStringsRow, T.NameStringIndicesRow)],
    vernaculars: Seq[(T.VernacularStringIndicesRow, T.VernacularStringsRow)])

  private def createResult(nameInputParsed: SNResult,
                           dbResult: DBResult,
                           matchType: MatchType): ResultScored = {
    val canonicalNameOpt =
      for { canId <- dbResult.nameString.canonicalUuid
            canNameValue <- dbResult.nameString.canonical
            canNameValueRanked <- nameInputParsed.canonized(true)}
        yield CanonicalName(uuid = canId, value = canNameValue, valueRanked = "")

    val classification = Classification(
      path = dbResult.nameStringIndex.classificationPath,
      pathIds = dbResult.nameStringIndex.classificationPathIds,
      pathRanks = dbResult.nameStringIndex.classificationPathRanks
    )

    val synonym = {
      val classificationPathIdsSeq =
        dbResult.nameStringIndex.classificationPathIds.map { _.fastSplit('|') }.getOrElse(List())
      if (classificationPathIdsSeq.nonEmpty) {
        dbResult.nameStringIndex.taxonId.some != classificationPathIdsSeq.lastOption
      } else if (dbResult.nameStringIndex.acceptedTaxonId.isDefined) {
        dbResult.nameStringIndex.taxonId.some != dbResult.nameStringIndex.acceptedTaxonId
      } else true
    }

    val dataSource = DataSource(id = dbResult.dataSource.id,
                                title = dbResult.dataSource.title)

    val acceptedNameResult = dbResult.acceptedNameOpt.map { case (ns, nsi) =>
      val canonicalNameOpt =
        for { canId <- ns.canonicalUuid
              canNameValue <- ns.canonical
              canNameValueRanked <- SNP.fromString(ns.name).canonized(true) }
          yield CanonicalName(uuid = canId, value = canNameValue, valueRanked = canNameValueRanked)
      AcceptedName(
        name = Name(uuid = ns.id, value = ns.name),
        canonicalName = canonicalNameOpt,
        taxonId = nsi.taxonId,
        dataSourceId = nsi.dataSourceId
      )
    }

    val result =
      Result(name = Name(uuid = dbResult.nameString.id, value = dbResult.nameString.name),
             canonicalName = canonicalNameOpt,
             synonym = synonym,
             taxonId = dbResult.nameStringIndex.taxonId,
             matchType = matchType,
             classification = classification,
             dataSource = dataSource,
             acceptedTaxonId = dbResult.nameStringIndex.acceptedTaxonId,
             acceptedName = acceptedNameResult
      )
    val score = ResultScores.compute(nameInputParsed, result)
    ResultScored(result, score)
  }

  private[namefilter] def resolveCanonical(canonicalName: String) = {
    val canonicalNameUuid = UuidGenerator.generate(canonicalName)
    T.NameStrings.filter { ns =>
      ns.canonicalUuid =!= UuidGenerator.EmptyUuid && ns.canonicalUuid === canonicalNameUuid
    }
  }

  private[namefilter] def resolveCanonicalLike(canonicalName: String) = {
    if (canonicalName.length <= 3) {
      T.NameStrings.take(0)
    } else {
      val canonicalNameLike = canonicalName + "%"
      T.NameStrings.filter { x => x.canonical.like(canonicalNameLike) }
    }
  }

  private[namefilter] def resolveAuthor(authorName: String) = {
    val query = T.NameStrings_AuthorWords.filter { aw =>
      aw.authorWord === unaccent(authorName.toUpperCase)
    }.map { _.nameUuid }
    T.NameStrings.filter { ns => ns.id.in(query) }
  }

  private[namefilter] def resolveAuthorWildcard(authorName: String) = {
    if (authorName.isEmpty) {
      T.NameStrings.take(0)
    } else {
      val authorNameLike = authorName + "%"
      val query = T.NameStrings_AuthorWords.filter { aw =>
        aw.authorWord.like(unaccent(authorNameLike.toUpperCase))
      }.map { _.nameUuid }
      T.NameStrings.filter { ns => ns.id.in(query) }
    }
  }

  private[namefilter] def resolveYear(year: String) = {
    Try(year.toInt) match {
      case Success(yr) if yr < 1758 =>
        T.NameStrings.take(0)
      case _ =>
        val query = T.NameStrings_Year.filter { x => x.year === year }.map { _.nameUuid }
        T.NameStrings.filter { ns => ns.id.in(query) }
    }
  }

  private[namefilter] def resolveYearWildcard(year: String) = {
    if (year.isEmpty) {
      T.NameStrings.take(0)
    } else {
      val yearLike = year + "%"
      val query = T.NameStrings_Year.filter { yw => yw.year.like(yearLike) }.map { _.nameUuid }
      T.NameStrings.filter { ns => ns.id.in(query) }
    }
  }

  private[namefilter] def resolveUninomial(uninomial: String) = {
    val query = T.NameStrings_Uninomial.filter { uw =>
      uw.uninomial === unaccent(uninomial.toUpperCase)
    }.map { _.nameUuid }
    T.NameStrings.filter { ns => ns.id.in(query) }
  }

  private[namefilter] def resolveUninomialWildcard(uninomial: String) = {
    if (uninomial.length <= 3) {
      T.NameStrings.take(0)
    } else {
      val uninomialLike = uninomial + "%"
      val query = T.NameStrings_Uninomial.filter { uw =>
        uw.uninomial.like(unaccent(uninomialLike.toUpperCase))
      }.map { _.nameUuid }
      T.NameStrings.filter { ns => ns.id.in(query) }
    }
  }

  private[namefilter] def resolveGenus(genus: String) = {
    val query = T.NameStrings_Genus.filter { uw => uw.genus === unaccent(genus.toUpperCase) }
      .map { _.nameUuid }
    T.NameStrings.filter { ns => ns.id.in(query) }
  }

  private[namefilter] def resolveGenusWildcard(genus: String) = {
    if (genus.length <= 3) {
      T.NameStrings.take(0)
    } else {
      val genusLike = genus + "%"
      val query = T.NameStrings_Genus.filter { uw =>
        uw.genus.like(unaccent(genusLike.toUpperCase))
      }.map { _.nameUuid }
      T.NameStrings.filter { ns => ns.id.in(query) }
    }
  }

  private[namefilter] def resolveSpecies(species: String) = {
    val query = T.NameStrings_Species.filter { sw =>
      sw.species === unaccent(species.toUpperCase)
    }.map { _.nameUuid }
    T.NameStrings.filter { ns => ns.id.in(query) }
  }

  private[namefilter] def resolveSpeciesWildcard(species: String) = {
    if (species.length <= 3) {
      T.NameStrings.take(0)
    } else {
      val speciesLike = species + "%"
      val query = T.NameStrings_Species.filter { sw =>
        sw.species.like(unaccent(speciesLike.toUpperCase))
      }.map { _.nameUuid }
      T.NameStrings.filter { ns => ns.id.in(query) }
    }
  }

  private[namefilter] def resolveSubspecies(subspecies: String) = {
    val query = T.NameStrings_Subspecies.filter { ssw =>
      ssw.subspecies === unaccent(subspecies.toUpperCase)
    }.map { _.nameUuid }
    T.NameStrings.filter { ns => ns.id.in(query) }
  }

  private[namefilter] def resolveSubspeciesWildcard(subspecies: String) = {
    if (subspecies.length <= 3) {
      T.NameStrings.take(0)
    } else {
      val subspeciesLike = subspecies + "%"
      val query = T.NameStrings_Subspecies.filter { ssw =>
        ssw.subspecies.like(unaccent(subspeciesLike.toUpperCase))
      }.map { _.nameUuid }
      T.NameStrings.filter { ns => ns.id.in(query) }
    }
  }

  private[namefilter] def resolveNameStrings(nameStringQuery: String) = {
    T.NameStrings.filter { ns => unaccent(ns.name) === unaccent(nameStringQuery) }
  }

  private[namefilter] def resolveNameStringsLike(nameStringQuery: String) = {
    if (nameStringQuery.length <= 3) {
      T.NameStrings.take(0)
    } else {
      val nameStringQueryLike = nameStringQuery + "%"
      T.NameStrings.filter { ns => unaccent(ns.name).like(unaccent(nameStringQueryLike)) }
    }
  }

  private[namefilter] def resolveExact(exact: String) = {
    val exactUuid = UuidGenerator.generate(exact)
    T.NameStrings.filter { ns => ns.id === exactUuid }
  }

  private[namefilter] def resolve(name: String) = {
    T.NameStrings.filter { ns =>
      unaccent(ns.name) === unaccent(name) ||
        (ns.canonicalUuid =!= UuidGenerator.EmptyUuid &&
          unaccentOpt(ns.canonical) === unaccent(name))
    }
  }

  private[namefilter] def resolveWildcard(name: String) = {
    if (name.length <= 3) {
      T.NameStrings.take(0)
    } else {
      val nameLike = name + "%"
      T.NameStrings.filter { ns => unaccent(ns.name).like(unaccent(nameLike)) ||
        (ns.canonicalUuid =!= UuidGenerator.EmptyUuid &&
          unaccentOpt(ns.canonical).like(unaccent(nameLike)))
      }
    }
  }

  private def valueCleaned(value: String, modifier: Modifier): String = {
    val trimmed = value.replaceAll("\\s{2,}", " ").replaceAll("\\%", " ").trim
    modifier match {
      case CanonicalModifier if !trimmed.startsWith("x ") => capitalize(trimmed)
      case NameStringModifier => capitalize(trimmed)
      case NoModifier => capitalize(trimmed)
      case _ => trimmed
    }
  }

  private
  def queryWithSurrogates(nameStringsQuery: NameStringsQuery) = {
    val query = for {
      ns <- nameStringsQuery
      nsi <- T.NameStringIndices.filter { nsi => nsi.nameStringId === ns.id }
      ds <- T.DataSources.filter { ds => ds.id === nsi.dataSourceId }
    } yield (ns, nsi, ds)

    query
      .joinLeft(T.NameStringIndices).on { case ((_, nsi_l, _), nsi_r) =>
        nsi_l.dataSourceId === nsi_r.dataSourceId && nsi_l.acceptedTaxonId === nsi_r.taxonId
      }
      .joinLeft(T.NameStrings).on { case ((_, nsi), ns) => ns.id === nsi.map { _.nameStringId } }
      .map { case (((ns, nsi, ds), nsiAccepted), nsAccepted) =>
        (ns, nsi, ds, nsiAccepted, nsAccepted)
      }
  }

  def resolveString(request: Request): TwitterFuture[Seq[ResultScored]] = {
    val search = QueryParser.result(request.searchTerm)
    val wildcard = search.wildcard
    val modifier = search.modifier
    val nameFilter = this

    val resolverFunction: (String) => Query[T.NameStrings, T.NameStringsRow, Seq] =
      modifier match {
        case NoModifier =>
          if (wildcard) nameFilter.resolveWildcard
          else nameFilter.resolve
        case ExactModifier =>
//          paramsRes = paramsRes.copy(matchType = MatchKind.ExactNameMatchByUUID)
          nameFilter.resolveExact
        case NameStringModifier =>
          if (wildcard) nameFilter.resolveNameStringsLike
          else {
//            paramsRes = paramsRes.copy(matchType = MatchKind.ExactNameMatchByString)
            nameFilter.resolveNameStrings
          }
        case CanonicalModifier =>
          if (wildcard) {
//            paramsRes = paramsRes.copy(matchType = MatchKind.ExactCanonicalNameMatchByUUID)
            nameFilter.resolveCanonicalLike
          } else {
//            paramsRes = paramsRes.copy(matchType = MatchKind.ExactCanonicalNameMatchByString)
            nameFilter.resolveCanonical
          }
        case UninomialModifier =>
          if (wildcard) nameFilter.resolveUninomialWildcard
          else nameFilter.resolveUninomial
        case GenusModifier =>
          if (wildcard) nameFilter.resolveGenusWildcard
          else nameFilter.resolveGenus
        case SpeciesModifier =>
          if (wildcard) nameFilter.resolveSpeciesWildcard
          else nameFilter.resolveSpecies
        case SubspeciesModifier =>
          if (wildcard) nameFilter.resolveSubspeciesWildcard
          else nameFilter.resolveSubspecies
        case AuthorModifier =>
          if (wildcard) nameFilter.resolveAuthorWildcard
          else nameFilter.resolveAuthor
        case YearModifier =>
          if (wildcard) nameFilter.resolveYearWildcard
          else nameFilter.resolveYear
      }
    val nameStrings = resolverFunction(valueCleaned(request.searchTerm, modifier))
    val resultFuture = database.run(queryWithSurrogates(nameStrings).result).map { dbResults =>
      val results = dbResults.map { case (ns, nsi, ds, nsiAcptOpt, nsAcptOpt) =>
        val acceptedName = for (ns <- nsAcptOpt; nsi <- nsiAcptOpt) yield (ns, nsi)
        val dbResult = DBResult(ns, nsi, ds, acceptedName, Seq())
        val matchType = MatchType(
          kind = MatchKind.Unknown,
          editDistance = 0,
          score = 0
        )
        createResult(SNP.fromString(ns.name), dbResult, matchType)
      }
      results
    }
    resultFuture.as[TwitterFuture[Seq[ResultScored]]]
  }
}

object NameFilter {
  type NameStringsQuery =
    Query[T.NameStrings, T.NameStringsRow, Seq]

  sealed trait Modifier
  case object NoModifier extends Modifier
  case object ExactModifier extends Modifier
  case object NameStringModifier extends Modifier
  case object CanonicalModifier extends Modifier
  case object UninomialModifier extends Modifier
  case object GenusModifier extends Modifier
  case object SpeciesModifier extends Modifier
  case object SubspeciesModifier extends Modifier
  case object AuthorModifier extends Modifier
  case object YearModifier extends Modifier
}
