package org.globalnames
package index
package matcher

import com.twitter.inject.Logging
import org.globalnames.{matcher => matcherlib}
import org.globalnames.matcher.Candidate
import thrift.matcher.{Response, Result}
import thrift.Name
import javax.inject.{Inject, Singleton}

import parser.ScientificNameParser.{instance => SNP}
import index.thrift.MatchKind
import org.apache.commons.lang3.StringUtils
import util.UuidEnhanced.javaUuid2thriftUuid

import scalaz.syntax.std.boolean._

@Singleton
case class CanonicalNames(names: Map[String, Set[Int]])

@Singleton
class Matcher @Inject()(matcherLib: matcherlib.Matcher,
                        canonicalNames: CanonicalNames) extends Logging {
  private val FuzzyMatchLimit = 5

  private[Matcher] case class CanonicalNameSplit(name: String, namePartialStr: String) {

    val size: Int =
      namePartialStr.isEmpty ? 0 | (StringUtils.countMatches(namePartialStr, ' ') + 1)

    val isOriginalCanonical: Boolean = name.length == namePartialStr.length

    val isUninomial: Boolean = size == 1

    def shorten: CanonicalNameSplit =
      if (size > 1) {
        this.copy(namePartialStr = namePartialStr.substring(0, namePartialStr.lastIndexOf(' ')))
      } else {
        this.copy(namePartialStr = "")
      }

    def nameProvided: Name = Name(uuid = UuidGenerator.generate(name), value = name)

    def namePartial: Name =
      Name(uuid = UuidGenerator.generate(namePartialStr), value = namePartialStr)
  }

  private object CanonicalNameSplit {
    def apply(name: String): CanonicalNameSplit = CanonicalNameSplit(name, name)
  }

  private case class FuzzyMatch(canonicalNameSplit: CanonicalNameSplit,
                                candidates: Vector[Candidate])

  private
  def resolveFromPartials(canonicalNameSplits: Seq[CanonicalNameSplit],
                          dataSourceIds: Set[Int]): Seq[Response] = {
    logger.info(s"Matcher service start for ${canonicalNameSplits.size} records")
    if (canonicalNameSplits.isEmpty) {
      Seq()
    } else {
      val (nonEmptySplits, noFuzzyMatchesSplits) =
        canonicalNameSplits.partition { cnp => cnp.size > 0 }

      val noFuzzyMatches = noFuzzyMatchesSplits.map { cns =>
        Response(input = cns.nameProvided, results = Seq())
      }

      val (exactPartialCanonicalMatches, possibleFuzzyCanonicalMatches) =
        nonEmptySplits
          .map { cns => (cns, canonicalNames.names(cns.namePartialStr)) }
          .partition { case (_, dsids) =>
            dataSourceIds.isEmpty ? dsids.nonEmpty | dataSourceIds.intersect(dsids).nonEmpty
          }

      val partialByGenusFuzzyResponses =
        for ((canonicalNameSplit, _) <- exactPartialCanonicalMatches) yield {
          val matchKind =
            if (canonicalNameSplit.isOriginalCanonical) MatchKind.ExactCanonicalNameMatchByUUID
            else MatchKind.FuzzyCanonicalMatch

          val result = Result(nameMatched = canonicalNameSplit.namePartial,
                              distance = 0,
                              matchKind = matchKind)
          Response(input = canonicalNameSplit.nameProvided, results = Seq(result))
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
        resolveFromPartials(oonucrEmpty.map { _.canonicalNameSplit.shorten }, dataSourceIds)

      val responsesNonEmpty =
        for (fuzzyMatch <- oonucrNonEmpty) yield {
          if (fuzzyMatch.candidates.size > FuzzyMatchLimit) {
            Response(input = fuzzyMatch.canonicalNameSplit.nameProvided, results = Seq())
          } else {
            val results = fuzzyMatch.candidates
              .filter { candidate =>
                canonicalNames.names(candidate.term).intersect(dataSourceIds).nonEmpty
              }
              .map { candidate =>
                val matchKind =
                  if (fuzzyMatch.canonicalNameSplit.isOriginalCanonical) {
                    if (candidate.distance == 0) MatchKind.ExactCanonicalNameMatchByUUID
                    else MatchKind.FuzzyCanonicalMatch
                  } else {
                    if (candidate.distance == 0) MatchKind.ExactPartialMatch
                    else MatchKind.FuzzyPartialMatch
                  }
                Result(
                  nameMatched = Name(uuid = UuidGenerator.generate(candidate.term),
                                     value = candidate.term),
                  distance = candidate.distance,
                  matchKind = matchKind
                )
              }
            Response(input = fuzzyMatch.canonicalNameSplit.nameProvided, results = results)
          }
        }

      logger.info(s"Matcher service completed for ${canonicalNameSplits.size} records")
      partialByGenusFuzzyResponses ++ responsesNonEmpty ++ responsesYetEmpty ++ noFuzzyMatches
    }
  }

  def resolve(names: Seq[String], dataSourceIds: Seq[Int]): Seq[Response] = {
    logger.info("Started. Splitting names")
    val namesParsed = names.map { name => SNP.fromString(name) }
    val (namesParsedSuccessfully, namesParsedRest) = namesParsed.partition { np =>
      np.canonized().isDefined
    }
    val responsesRest = namesParsedRest.map { np =>
      Response(input = Name(uuid = np.preprocessorResult.id,
               value = np.preprocessorResult.verbatim), results = Seq())
    }
    val namesParsedSuccessfullySplits = namesParsedSuccessfully.map { np =>
      CanonicalNameSplit(np.preprocessorResult.verbatim)
    }
    logger.info("Recursive fuzzy match started")
    resolveFromPartials(namesParsedSuccessfullySplits, dataSourceIds.toSet) ++ responsesRest
  }
}
