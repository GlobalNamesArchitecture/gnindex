package org.globalnames
package index
package namefilter

import javax.inject.{Inject, Singleton}

import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.twitter_util.UtilBijections._
import com.twitter.inject.Logging
import com.twitter.util.{Future => TwitterFuture}
import scala.concurrent.{Future => ScalaFuture}
import org.apache.commons.lang3.StringUtils.capitalize
import index.dao.{DBResultObj, Tables => T}
import index.dao.Projections._
import thrift._
import thrift.namefilter._
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Try}

import scalaz.syntax.std.boolean._

@Singleton
class NameFilter @Inject()(database: Database) extends Logging {
  import NameFilter._

  private val unaccent = SimpleFunction.unary[String, String]("unaccent")
  private val fUnaccent = SimpleFunction.unary[String, String]("f_unaccent")
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
    if (nameStringQuery.length < 3) {
      T.NameStrings.take(0)
    } else {
      val nameStringQueryLike = nameStringQuery + "%"
      T.NameStrings.filter { ns => fUnaccent(ns.name).like(fUnaccent(nameStringQueryLike)) }
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
  def queryComplete(nameStringsQuery: NameStringsQuery): ScalaFuture[Seq[ResultDB]] = {
    val query = for {
      ns <- nameStringsQuery
      nsi <- T.NameStringIndices.filter { nsi => nsi.nameStringId === ns.id }
      ds <- T.DataSources.filter { ds => ds.id === nsi.dataSourceId }
    } yield (ns, nsi, ds)

    val queryJoined = query
      .joinLeft(T.NameStringIndices).on { case ((_, nsi_l, _), nsi_r) =>
        nsi_l.acceptedTaxonId =!= "" &&
          nsi_l.dataSourceId === nsi_r.dataSourceId && nsi_l.acceptedTaxonId === nsi_r.taxonId
      }
      .joinLeft(T.NameStrings).on { case ((_, nsi), ns) => ns.id === nsi.map { _.nameStringId } }
      .map { case (((ns, nsi, ds), nsiAccepted), nsAccepted) =>
        DBResultObj.project(ns, nsi, ds, nsAccepted, nsiAccepted)
      }
      .take(1000)
    database.run(queryJoined.result)
  }

  private val dataSourceOrdering: Ordering[DataSource] = new Ordering[DataSource] {
    override def compare(x: DataSource, y: DataSource): Int = {
      (x.id, y.id) match {
        case (1, 1) => 0
        case (11, 11) => 0
        case (1, _) => 1
        case (_, 1) => -1
        case (11, _) => 1
        case (_, 11) => -1
        case (_, _) => Ordering.String.compare(y.title.toLowerCase, x.title.toLowerCase)
      }
    }
  }

  def resolveString(request: Request): TwitterFuture[ResponseNameStrings] = {
    val search = QueryParser.result(request.searchTerm)
    logger.info(s"query: ${search.toString}")
    val matchKind: MatchKind = search.modifier match {
      case ExactModifier =>
        MatchKind.ExactNameMatchByUUID
      case NameStringModifier if !search.wildcard =>
        MatchKind.ExactNameMatchByUUID
      case CanonicalModifier =>
        if (search.wildcard) MatchKind.ExactCanonicalNameMatchByUUID
        else MatchKind.ExactCanonicalNameMatchByString
      case _ =>
        MatchKind.ExactNameMatchByString
    }
    val resolverFunction = search.modifier match {
      case NoModifier =>
        search.wildcard ? resolveWildcard _ | resolve
      case ExactModifier =>
        resolveExact _
      case NameStringModifier =>
        search.wildcard ? resolveNameStringsLike _ | resolveNameStrings
      case CanonicalModifier =>
        search.wildcard ? resolveCanonicalLike _ | resolveCanonical
      case UninomialModifier =>
        search.wildcard ? resolveUninomialWildcard _ | resolveUninomial
      case GenusModifier =>
        search.wildcard ? resolveGenusWildcard _ | resolveGenus
      case SpeciesModifier =>
        search.wildcard ? resolveSpeciesWildcard _ | resolveSpecies
      case SubspeciesModifier =>
        search.wildcard ? resolveSubspeciesWildcard _ | resolveSubspecies
      case AuthorModifier =>
        search.wildcard ? resolveAuthorWildcard _ | resolveAuthor
      case YearModifier =>
        search.wildcard ? resolveYearWildcard _ | resolveYear
    }
    val nameStrings = resolverFunction(valueCleaned(search.contents, search.modifier))
    val resultFuture = queryComplete(nameStrings).map { dbResults =>
      val results = dbResults.map { dbResult =>
        val matchType = MatchType(
          kind = matchKind,
          editDistance = 0,
          score = 0
        )
        DBResultObj.create(dbResult, matchType)
      }
      .groupBy { r => r.name.uuid }
      .values.flatMap { results =>
        val rpdss =
         for ((ds, vs) <- results.groupBy { r => r.dataSource }) yield {
           ResultsPerDataSource(
             dataSource = ds,
             results = vs
           )
         }
        for (response <- results.headOption) yield {
          ResultNameStrings(
            name = response.name,
            canonicalName = response.canonicalName,
            results = rpdss.toVector.sortBy { rpds => rpds.dataSource }(dataSourceOrdering.reverse)
          )
        }
      }
      .toVector
      .sortBy { r => r.name.value }

      val pagesCount =
        results.size / request.perPage + (results.size % request.perPage > 0).compare(false)
      ResponseNameStrings(
        page = request.page,
        perPage = request.perPage,
        pagesCount = pagesCount,
        resultsCount = results.size,
        names = results.slice(request.perPage * request.page,
                              request.perPage * (request.page + 1))
      )
    }
    resultFuture.as[TwitterFuture[ResponseNameStrings]]
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
