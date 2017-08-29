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
import index.thrift.{MatchKind, Uuid}
import org.apache.commons.lang3.StringUtils
import util.UuidEnhanced.javaUuid2thriftUuid

import scalaz.syntax.std.boolean._

@Singleton
final case class CanonicalNames(names: Map[String, Set[Int]])

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

    def nameProvidedUuid: Uuid = UuidGenerator.generate(name)

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
                          dataSourceIds: Set[Int],
                          advancedResolution: Boolean): Seq[Response] = {
    logger.info(s"Matcher service start for ${canonicalNameSplits.size} records")
    if (canonicalNameSplits.isEmpty) {
      Seq()
    } else {
      val (nonGenusSplits, genusOnlyMatchesSplits) =
        canonicalNameSplits.partition { cnp => cnp.size > 1 }

      val noFuzzyMatches =
        for (goms <- genusOnlyMatchesSplits) yield {
          val matchKind =
            if (goms.isOriginalCanonical) MatchKind.ExactCanonicalNameMatchByUUID
            else MatchKind.ExactMatchPartialByGenus

          val dsIds = canonicalNames.names(goms.namePartialStr)
          val results =
            (dataSourceIds.isEmpty ? dsIds | dataSourceIds.intersect(dsIds)).nonEmpty.option {
              Result(nameMatched = goms.namePartial, distance = 0, matchKind = matchKind)
            }.toSeq
          Response(inputUuid = goms.nameProvidedUuid, results = results)
        }

      val (exactPartialCanonicalMatches, possibleFuzzyCanonicalMatches) =
        nonGenusSplits
          .map { cns => (cns, canonicalNames.names(cns.namePartialStr)) }
          .partition { case (_, dsids) =>
            (dataSourceIds.isEmpty ? dsids | dataSourceIds.intersect(dsids)).nonEmpty
          }

      val partialByGenusFuzzyResponses =
        for ((canonicalNameSplit, _) <- exactPartialCanonicalMatches) yield {
          val matchKind =
            if (canonicalNameSplit.isOriginalCanonical) MatchKind.ExactCanonicalNameMatchByUUID
            else MatchKind.FuzzyCanonicalMatch

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
    val namesParsed = names.map { name => SNP.fromString(name) }
    val (namesParsedSuccessfully, namesParsedRest) = namesParsed.partition { np =>
      np.canonized().isDefined
    }
    val responsesRest = namesParsedRest.map { np =>
      Response(inputUuid = np.preprocessorResult.id, results = Seq())
    }
    val namesParsedSuccessfullySplits = namesParsedSuccessfully.map { np =>
      CanonicalNameSplit(np.preprocessorResult.verbatim)
    }
    logger.info("Recursive fuzzy match started")
    resolveFromPartials(
      namesParsedSuccessfullySplits, dataSourceIds.toSet, advancedResolution) ++ responsesRest
  }
}
