package org.globalnames
package index
package namefilter

import javax.inject.{Inject, Singleton}

import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.twitter_util.UtilBijections._
import com.twitter.util.{Future => TwitterFuture}
import org.apache.commons.lang3.StringUtils.capitalize
import index.dao.{DBResult, Tables => T}
import thrift._
import thrift.namefilter._
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Try}

@Singleton
class NameFilter @Inject()(database: Database) {
  import NameFilter._

  private val unaccent = SimpleFunction.unary[String, String]("unaccent")
  private val unaccentOpt = SimpleFunction.unary[Option[String], Option[String]]("unaccent")

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
      case Success(yr) if yr < 1753 =>
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
  def queryComplete(nameStringsQuery: NameStringsQuery) = {
    val query = for {
      ns <- nameStringsQuery
      nsi <- T.NameStringIndices.filter { nsi => nsi.nameStringId === ns.id }
      ds <- T.DataSources.filter { ds => ds.id === nsi.dataSourceId }
    } yield (ns, nsi, ds)

    val queryJoined = query
      .joinLeft(T.NameStringIndices).on { case ((_, nsi_l, _), nsi_r) =>
        nsi_l.dataSourceId === nsi_r.dataSourceId && nsi_l.acceptedTaxonId === nsi_r.taxonId
      }
      .joinLeft(T.NameStrings).on { case ((_, nsi), ns) => ns.id === nsi.map { _.nameStringId } }
      .map { case (((ns, nsi, ds), nsiAccepted), nsAccepted) =>
        (ns, nsi, ds, nsiAccepted, nsAccepted)
      }
      .take(1000)

    database.run(queryJoined.result).map { rs =>
      rs.map { case (ns, nsi, ds, nsiAcp, nsAcp) => DBResult(ns, nsi, ds, nsiAcp, nsAcp) }
    }
  }

  def resolveString(request: Request): TwitterFuture[Seq[Result]] = {
    val search = QueryParser.result(request.searchTerm)
    val resolverFunction: (String) => Query[T.NameStrings, T.NameStringsRow, Seq] =
      search.modifier match {
        case NoModifier =>
          if (search.wildcard) resolveWildcard
          else resolve
        case ExactModifier =>
//          paramsRes = paramsRes.copy(matchType = MatchKind.ExactNameMatchByUUID)
          resolveExact
        case NameStringModifier =>
          if (search.wildcard) resolveNameStringsLike
          else {
//            paramsRes = paramsRes.copy(matchType = MatchKind.ExactNameMatchByString)
            resolveNameStrings
          }
        case CanonicalModifier =>
          if (search.wildcard) {
//            paramsRes = paramsRes.copy(matchType = MatchKind.ExactCanonicalNameMatchByUUID)
            resolveCanonicalLike
          } else {
//            paramsRes = paramsRes.copy(matchType = MatchKind.ExactCanonicalNameMatchByString)
            resolveCanonical
          }
        case UninomialModifier =>
          if (search.wildcard) resolveUninomialWildcard
          else resolveUninomial
        case GenusModifier =>
          if (search.wildcard) resolveGenusWildcard
          else resolveGenus
        case SpeciesModifier =>
          if (search.wildcard) resolveSpeciesWildcard
          else resolveSpecies
        case SubspeciesModifier =>
          if (search.wildcard) resolveSubspeciesWildcard
          else resolveSubspecies
        case AuthorModifier =>
          if (search.wildcard) resolveAuthorWildcard
          else resolveAuthor
        case YearModifier =>
          if (search.wildcard) resolveYearWildcard
          else resolveYear
      }
    val nameStrings = resolverFunction(valueCleaned(search.contents, search.modifier))
    val resultFuture = queryComplete(nameStrings).map { dbResults =>
      val results = dbResults.map { dbResult =>
        val matchType = MatchType(
          kind = MatchKind.Unknown,
          editDistance = 0,
          score = 0
        )
        DBResult.create(dbResult, matchType)
      }
      results
    }
    resultFuture.as[TwitterFuture[Seq[Result]]]
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
