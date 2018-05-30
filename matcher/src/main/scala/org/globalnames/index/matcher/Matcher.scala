package org.globalnames
package index
package matcher

import com.twitter.inject.Logging
import org.globalnames.{matcher => matcherlib}
import org.globalnames.matcher.Candidate
import thrift.matcher.{Response, Result}
import thrift.Name
import javax.inject.{Inject, Singleton}

import parser.{ScientificNameParser => snp}
import index.thrift.{Uuid, MatchKind => MK}
import org.apache.commons.lang3.StringUtils
import util.UuidEnhanced.javaUuid2thriftUuid

import scalaz.syntax.std.boolean._

@Singleton
final case class CanonicalNames(private val namesRaw: Map[String, Set[Int]]) {
  val names: Map[String, Set[Int]] = namesRaw.withDefaultValue(Set())
}

@Singleton
class Matcher @Inject()(matcherLib: matcherlib.Matcher,
                        canonicalNames: CanonicalNames) extends Logging {
  private val FuzzyMatchLimit = 5

  private[Matcher] case class CanonicalNameSplit(name: snp.Result,
                                                 namePartialStr: String,
                                                 isOriginalCanonical: Boolean) {

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

    def nameProvidedUuid: Uuid = name.preprocessorResult.id

    def namePartial: Name =
      Name(uuid = UuidGenerator.generate(namePartialStr), value = namePartialStr)
  }

  private object CanonicalNameSplit {
    def apply(name: snp.Result): CanonicalNameSplit =
      CanonicalNameSplit(name, name.canonized().getOrElse(""), isOriginalCanonical = true)
  }

  private case class FuzzyMatch(canonicalNameSplit: CanonicalNameSplit,
                                candidates: Vector[Candidate])

  private
  def resolveFromPartials(canonicalNameSplits: Seq[CanonicalNameSplit],
                          dataSourceIds: Set[Int],
                          advancedResolution: Boolean): Seq[Response] = {
    logger.info(s"Matcher service start for ${canonicalNameSplits.size} records")
    if (canonicalNameSplits.isEmpty) {
      Seq()
    } else {
      val (nonGenusOrUninomialSplits, genusOnlyMatchesSplits) =
        canonicalNameSplits.partition { cnp =>
          (cnp.size == 1 && cnp.isOriginalCanonical) || cnp.size > 1
        }

      val noFuzzyMatches =
        for (goms <- genusOnlyMatchesSplits) yield {
          val matchKind =
            if (goms.isOriginalCanonical) MK.ExactCanonicalNameMatchByUUID
            else MK.ExactMatchPartialByGenus

          val dsIds = canonicalNames.names(goms.namePartialStr)
          val results =
            (dataSourceIds.isEmpty ? dsIds | dataSourceIds.intersect(dsIds)).nonEmpty.option {
              Result(nameMatched = goms.namePartial, distance = 0, matchKind = matchKind)
            }.toSeq
          Response(inputUuid = goms.nameProvidedUuid, results = results)
        }

      val (exactPartialCanonicalMatches, possibleFuzzyCanonicalMatches) =
        nonGenusOrUninomialSplits
          .map { cns => (cns, canonicalNames.names(cns.namePartialStr)) }
          .partition { case (_, dsids) =>
            (dataSourceIds.isEmpty ? dsids | dataSourceIds.intersect(dsids)).nonEmpty
          }

      val partialByGenusFuzzyResponses =
        for ((canonicalNameSplit, _) <- exactPartialCanonicalMatches) yield {
          val matchKind =
            if (canonicalNameSplit.isOriginalCanonical) MK.ExactCanonicalNameMatchByUUID
            else MK.ExactPartialMatch

          val result = Result(nameMatched = canonicalNameSplit.namePartial,
                              distance = 0,
                              matchKind = matchKind)
          Response(inputUuid = canonicalNameSplit.nameProvidedUuid, results = Seq(result))
        }

      logger.info(s"Matcher library call for ${possibleFuzzyCanonicalMatches.size} records")
      val possibleFuzzyCanonicalsResponses =
        for ((canNmSplit, _) <- possibleFuzzyCanonicalMatches) yield {
          FuzzyMatch(canNmSplit, matcherLib.findMatches(canNmSplit.namePartialStr))
        }
      logger.info(s"matcher library call completed for " +
                  s"${possibleFuzzyCanonicalMatches.size} records")

      val (oonucrNonEmpty, oonucrEmpty) =
        possibleFuzzyCanonicalsResponses.partition { fuzzyMatch =>
          dataSourceIds.isEmpty ?
            fuzzyMatch.candidates.nonEmpty |
            fuzzyMatch.candidates.exists { candidate =>
              canonicalNames.names(candidate.term).intersect(dataSourceIds).nonEmpty
            }
        }

      val responsesYetEmpty =
        if (advancedResolution) {
          resolveFromPartials(oonucrEmpty.map { _.canonicalNameSplit.shorten },
                              dataSourceIds,
                              advancedResolution)
        } else {
          for { fm <- oonucrEmpty } yield {
            Response(inputUuid = fm.canonicalNameSplit.nameProvidedUuid, results = Seq())
          }
        }

      val responsesNonEmpty =
        for (fuzzyMatch <- oonucrNonEmpty) yield {
          if (fuzzyMatch.candidates.size > FuzzyMatchLimit) {
            Response(inputUuid = fuzzyMatch.canonicalNameSplit.nameProvidedUuid, results = Seq())
          } else {
            val results = fuzzyMatch.candidates
              .filter { candidate =>
                dataSourceIds.isEmpty ||
                  canonicalNames.names(candidate.term).intersect(dataSourceIds).nonEmpty
              }
              .map { candidate =>
                val matchKind =
                  if (fuzzyMatch.canonicalNameSplit.isOriginalCanonical) {
                    if (candidate.verbatimEditDistance.getOrElse(0) == 0 &&
                        candidate.stemEditDistance.getOrElse(0) == 0) {
                      MK.ExactCanonicalNameMatchByUUID
                    } else {
                      MK.FuzzyCanonicalMatch
                    }
                  } else {
                    if (candidate.verbatimEditDistance.getOrElse(0) == 0 &&
                        candidate.stemEditDistance.getOrElse(0) == 0) {
                      MK.ExactPartialMatch
                    } else {
                      MK.FuzzyPartialMatch
                    }
                  }
                Result(
                  nameMatched = Name(uuid = UuidGenerator.generate(candidate.term),
                                     value = candidate.term),
                  distance = candidate.verbatimEditDistance.getOrElse(0),
                  matchKind = matchKind
                )
              }
            Response(inputUuid = fuzzyMatch.canonicalNameSplit.nameProvidedUuid, results = results)
          }
        }

      logger.info(s"Matcher service completed for ${canonicalNameSplits.size} records")
      val responses =
        partialByGenusFuzzyResponses ++ responsesNonEmpty ++ responsesYetEmpty ++ noFuzzyMatches
      assert(responses.size == canonicalNameSplits.size)
      responses
    }
  }

  def resolve(names: Seq[String],
              dataSourceIds: Seq[Int],
              advancedResolution: Boolean): Seq[Response] = {
    logger.info("Started. Splitting names")
    val namesParsed = names.map { name => snp.instance.fromString(name) }
    val (namesParsedSuccessfully, namesParsedRest) = namesParsed.partition { np =>
      np.canonized().exists { _.nonEmpty }
    }
    val responsesRest = namesParsedRest.map { np =>
      Response(inputUuid = np.preprocessorResult.id, results = Seq())
    }
    val namesParsedSuccessfullySplits = namesParsedSuccessfully.map { np => CanonicalNameSplit(np) }
    logger.info("Recursive fuzzy match started")
    val responses =
      resolveFromPartials(
        namesParsedSuccessfullySplits, dataSourceIds.toSet, advancedResolution) ++ responsesRest

    if (advancedResolution) {
      responses
    } else {
      for (response <- responses) yield {
        response.copy(results = response.results.filter { res =>
          res.matchKind match {
            case MK.FuzzyCanonicalMatch | MK.ExactCanonicalNameMatchByUUID => true
            case _ => false
          }
        })
      }
    }
  }
}
