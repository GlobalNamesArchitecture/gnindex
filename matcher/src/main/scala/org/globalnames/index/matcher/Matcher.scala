package org.globalnames
package index
package matcher

import com.twitter.inject.Logging
import org.globalnames.{matcher => matcherlib}
import org.globalnames.matcher.Candidate
import thrift.matcher.{Response, Result}
import thrift.{Name, Uuid, MatchKind => MK}
import javax.inject.{Inject, Singleton}
import parser.{ScientificNameParser => snp}
import org.apache.commons.lang3.StringUtils
import util.UuidEnhanced.javaUuid2thriftUuid
import util.Strings._
import scalaz.syntax.std.boolean._
import scalaz.syntax.std.option._

import scala.collection.parallel.ParSeq
import scala.concurrent.Future
import scala.util.Success
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
final case class CanonicalNames(private val namesRaw: Map[String, Set[Int]]) {
  val nameToDatasources: Map[String, Set[Int]] = namesRaw.withDefaultValue(Set())
}

@Singleton
class Matcher @Inject()(canonicalNamesFut: Future[CanonicalNames]) extends Logging {

  val matcherLibFut: Future[matcherlib.Matcher] =
    canonicalNamesFut.map { canonicalNames => matcherlib.Matcher(canonicalNames.nameToDatasources) }

  private def matcherLib: matcherlib.Matcher = matcherLibFut.value match {
    case Some(Success(m)) => m
    case _ => matcherlib.Matcher(Map.empty)
  }

  private def canonicalNames: CanonicalNames = canonicalNamesFut.value match {
    case Some(Success(cn)) => cn
    case _ => CanonicalNames(Map.empty)
  }

  private[Matcher] case class CanonicalNameSplit(nameProvidedUuid: Uuid,
                                                 namePartialStr: String,
                                                 isOriginalCanonical: Boolean,
                                                 advancedResolutionAllowed: Boolean) {

    val size: Int =
      namePartialStr.isEmpty ? 0 | (StringUtils.countMatches(namePartialStr, ' ') + 1)

    val isUninomial: Boolean = size == 1

    def shorten: CanonicalNameSplit =
      if (size > 1) {
        this.copy(namePartialStr = namePartialStr.substring(0, namePartialStr.lastIndexOf(' ')),
                  isOriginalCanonical = false)
      } else {
        this.copy(namePartialStr = "", isOriginalCanonical = false)
      }

    def namePartial: Name =
      Name(uuid = UuidGenerator.generate(namePartialStr), value = namePartialStr)
  }

  private object CanonicalNameSplit {
    def create(name: parser.Result): Option[CanonicalNameSplit] = {
      for (can <- name.canonical) yield {
        CanonicalNameSplit(
          nameProvidedUuid = name.preprocessorResult.id,
          namePartialStr = can.value,
          isOriginalCanonical = true,
          advancedResolutionAllowed = true
        )
      }
    }

    def genusAndInfraspeciesOnly(name: parser.Result): Option[CanonicalNameSplit] = {
      name.canonical.flatMap { can =>
        val canSplit = can.value.fastSplit(' ')
        (canSplit.headOption, canSplit.lastOption) match {
          case (Some(hd), Some(tl)) =>
            val can1 = s"$hd $tl"
            val cns = CanonicalNameSplit(
              nameProvidedUuid = name.preprocessorResult.id,
              namePartialStr = can1,
              isOriginalCanonical = false,
              advancedResolutionAllowed = false
            )
            cns.some
          case _ => None
        }
      }
    }
  }

  private case class FuzzyMatch(canonicalNameSplit: CanonicalNameSplit,
                                candidates: Vector[Candidate])

  private def resolveFromPartials(canonicalNameSplits: ParSeq[CanonicalNameSplit],
                                  dataSourceIds: Set[Int],
                                  advancedResolution: Boolean): ParSeq[Response] = {
    logger.info(s"Matcher service start for ${canonicalNameSplits.size} records")
    if (canonicalNameSplits.isEmpty) {
      ParSeq()
    } else {
      val (nonGenusOrUninomialSplits, genusOnlyMatchesSplits) =
        canonicalNameSplits.partition { cnp =>
          (cnp.size == 1 && cnp.isOriginalCanonical) || cnp.size > 1
        }
      logger.info(s"Split: nonGenusOrUninomialSplits - ${nonGenusOrUninomialSplits.size}" +
                  s" and genusOnlyMatchesSplits - ${genusOnlyMatchesSplits.size}")

      val noFuzzyMatchesResponses =
        for (goms <- genusOnlyMatchesSplits) yield {
          val matchKind = MK.CanonicalMatch(thrift.CanonicalMatch(partial = true))
          val dsIds = canonicalNames.nameToDatasources(goms.namePartialStr)
          val results =
            (dataSourceIds.isEmpty ? dsIds | dataSourceIds.intersect(dsIds)).nonEmpty.option {
              Result(nameMatched = goms.namePartial, matchKind = matchKind)
            }.toSeq
          Response(inputUuid = goms.nameProvidedUuid, results = results)
        }

      val (exactPartialCanonicalMatches, possibleFuzzyCanonicalMatches) =
        nonGenusOrUninomialSplits
          .map { cns => (cns, canonicalNames.nameToDatasources(cns.namePartialStr)) }
          .partition { case (_, dsids) =>
            (dataSourceIds.isEmpty ? dsids | dataSourceIds.intersect(dsids)).nonEmpty
          }
      logger.info(s"Split: exactPartialCanonicalMatches - ${exactPartialCanonicalMatches.size}" +
                  s" and possibleFuzzyCanonicalMatches - ${possibleFuzzyCanonicalMatches.size}")

      val partialByGenusFuzzyResponses =
        for ((canonicalNameSplit, _) <- exactPartialCanonicalMatches) yield {
          val matchKind =
            MK.CanonicalMatch(thrift.CanonicalMatch(
              partial = !canonicalNameSplit.isOriginalCanonical))

          val result = Result(nameMatched = canonicalNameSplit.namePartial,
                              matchKind = matchKind)
          Response(inputUuid = canonicalNameSplit.nameProvidedUuid, results = Seq(result))
        }

      logger.info(s"Matcher library call for ${possibleFuzzyCanonicalMatches.size} records")
      val possibleFuzzyCanonicalsResponses =
        for ((canNmSplit, _) <- possibleFuzzyCanonicalMatches) yield {
          val matches = matcherLib.findMatches(canNmSplit.namePartialStr, dataSourceIds)
          FuzzyMatch(canNmSplit, matches)
        }
      logger.info(s"matcher library call completed for " +
                  s"${possibleFuzzyCanonicalMatches.size} records")

      val (oonucrNonEmpty, oonucrEmpty) =
        possibleFuzzyCanonicalsResponses.partition { fuzzyMatch =>
          dataSourceIds.isEmpty ?
            fuzzyMatch.candidates.nonEmpty |
            fuzzyMatch.candidates.exists { candidate =>
              canonicalNames.nameToDatasources(candidate.term).intersect(dataSourceIds).nonEmpty
            }
        }

      val responsesYetEmpty =
        if (advancedResolution) {
          val (oonucrEmptyAllowed, oonucrEmptyRest) =
            oonucrEmpty.partition { n => n.canonicalNameSplit.advancedResolutionAllowed }

          val partials = resolveFromPartials(
            canonicalNameSplits = oonucrEmptyAllowed.map { _.canonicalNameSplit.shorten },
            dataSourceIds = dataSourceIds,
            advancedResolution = advancedResolution)
          val rest = for (fm <- oonucrEmptyRest) yield {
            Response(inputUuid = fm.canonicalNameSplit.nameProvidedUuid, results = Seq())
          }
          partials ++ rest
        } else {
          for (fm <- oonucrEmpty) yield {
            Response(inputUuid = fm.canonicalNameSplit.nameProvidedUuid, results = Seq())
          }
        }

      val responsesNonEmpty =
        for (fuzzyMatch <- oonucrNonEmpty) yield {
          val results = fuzzyMatch.candidates
            .filter { candidate =>
              dataSourceIds.isEmpty ||
                canonicalNames.nameToDatasources(candidate.term).intersect(dataSourceIds).nonEmpty
            }
            .map { candidate =>
              val matchKind =
                MK.CanonicalMatch(thrift.CanonicalMatch(
                  partial = !fuzzyMatch.canonicalNameSplit.isOriginalCanonical,
                  stemEditDistance = candidate.stemEditDistance.getOrElse(0),
                  verbatimEditDistance = candidate.verbatimEditDistance.getOrElse(0)))
              Result(
                nameMatched = Name(uuid = UuidGenerator.generate(candidate.term),
                                   value = candidate.term),
                matchKind = matchKind
              )
            }
          Response(inputUuid = fuzzyMatch.canonicalNameSplit.nameProvidedUuid, results = results)
        }

      logger.info(s"Matcher service completed for ${canonicalNameSplits.size} records")
      val responses = partialByGenusFuzzyResponses ++ responsesNonEmpty ++
                      responsesYetEmpty ++ noFuzzyMatchesResponses
      assert(responses.size == canonicalNameSplits.size)
      responses
    }
  }

  def resolve(names: Seq[String],
              dataSourceIds: Seq[Int],
              advancedResolution: Boolean): Seq[Response] = {
    logger.info("Started. Splitting names")
    val namesParsed = names.par.map { name => snp.instance.fromString(name) }
    val (namesParsedSuccessfully, namesParsedRest) = namesParsed.partition { nameParsed =>
      val isAbbreviated = {
        val verbatim = nameParsed.result.preprocessorResult.verbatim
        if (verbatim.isEmpty) {
          true
        } else {
          val firstSpaceIndex = verbatim.indexOf(' ')
          val firstWordLastIndex: Int = (firstSpaceIndex == -1) ? verbatim.length | firstSpaceIndex
          verbatim(firstWordLastIndex - 1) == '.'
        }
      }

      !isAbbreviated && nameParsed.result.canonical.exists { _.value.nonEmpty }
    }
    val responsesRest = namesParsedRest.map { np =>
      Response(inputUuid = np.result.preprocessorResult.id, results = Seq())
    }
    val namesParsedSuccessfullySplits = namesParsedSuccessfully.flatMap { np =>
      val cns = CanonicalNameSplit.create(name = np.result)
      val cnsNoMiddle = CanonicalNameSplit.genusAndInfraspeciesOnly(name = np.result)
      List(cns, cnsNoMiddle).flatten
    }
    logger.info("Recursive fuzzy match started")
    val responsesFromPartitials = resolveFromPartials(
      canonicalNameSplits = namesParsedSuccessfullySplits,
      dataSourceIds = dataSourceIds.toSet,
      advancedResolution = advancedResolution)
    val responses: Vector[Response] =
      (responsesFromPartitials ++ responsesRest)
        .groupBy { _.inputUuid }
        .mapValues { vs => vs.head.copy(results = vs.flatMap { _.results }.seq) }
        .values.toVector

    if (advancedResolution) {
      responses
    } else {
      val result =
        for (response <- responses) yield {
          response.copy(results = response.results.filter { res =>
            res.matchKind match {
              case MK.CanonicalMatch(cm) =>
                cm.partial || cm.stemEditDistance > 0 || cm.verbatimEditDistance > 0
              case _ => false
            }
          })
        }
      result
    }
  }
}
