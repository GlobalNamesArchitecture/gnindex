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

@Singleton
class Matcher @Inject()(matcherLib: matcherlib.Matcher) extends Logging {
  private val FuzzyMatchLimit = 5

  private case class CanonicalNameSplit(name: String, namePartialStr: String) {

    private val size: Int = StringUtils.countMatches(namePartialStr, ' ') + 1

    val isOriginalCanonical: Boolean = name.length == namePartialStr.length
    val isUninomial: Boolean = size == 1

    def shorten: CanonicalNameSplit = {
      this.copy(namePartialStr = namePartialStr.substring(0, namePartialStr.lastIndexOf(' ')))
    }

    def nameProvided: Name = {
      Name(uuid = UuidGenerator.generate(name), value = name)
    }

    def namePartial: Name = {
      Name(uuid = UuidGenerator.generate(namePartialStr), value = namePartialStr)
    }
  }

  private object CanonicalNameSplit {
    def apply(name: String): CanonicalNameSplit = CanonicalNameSplit(name, name)
  }

  private case class FuzzyMatch(canonicalNameSplit: CanonicalNameSplit,
                                candidates: Vector[Candidate])

  private
  def resolveFromPartials(canonicalNameSplits: Seq[CanonicalNameSplit]): Seq[Response] = {
    logger.info(s"Matcher service start for ${canonicalNameSplits.size} records")
    if (canonicalNameSplits.isEmpty) {
      Seq()
    } else {
      val (originalOrNonUninomialCanonicals, partialByGenusCanonicals) =
        canonicalNameSplits.partition { canonicalNameSplit =>
          canonicalNameSplit.isOriginalCanonical || canonicalNameSplit.isUninomial
        }

      val partialByGenusFuzzyResponses =
        for (canonicalNameSplit <- partialByGenusCanonicals) yield {
          val result = Result(nameMatched = canonicalNameSplit.namePartial,
                              distance = 0,
                              matchKind = MatchKind.ExactMatchPartialByGenus)
          Response(input = canonicalNameSplit.nameProvided, results = Seq(result))
        }

      logger.info(s"Matcher library call for ${originalOrNonUninomialCanonicals.size} records")
      val originalOrNonUninomialCanonicalsResponses =
        for (canNmSplit <- originalOrNonUninomialCanonicals) yield {
          FuzzyMatch(canNmSplit, matcherLib.findMatches(canNmSplit.namePartialStr))
        }
      logger.info(s"matcher library call completed for " +
                  s"${originalOrNonUninomialCanonicals.size} records")

      val (oonucrNonEmpty, oonucrEmpty) =
        originalOrNonUninomialCanonicalsResponses.partition { _.candidates.nonEmpty }

      val responsesEmpty =
        resolveFromPartials(oonucrEmpty.map { _.canonicalNameSplit.shorten })

      val responsesNonEmpty =
        for (fuzzyMatch <- oonucrNonEmpty) yield {
          if (fuzzyMatch.candidates.size > FuzzyMatchLimit) {
            Response(input = fuzzyMatch.canonicalNameSplit.nameProvided, results = Seq())
          } else {
            val results = fuzzyMatch.candidates.map { candidate =>
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
      partialByGenusFuzzyResponses ++ responsesNonEmpty ++ responsesEmpty
    }
  }

  def resolve(names: Seq[String]): Seq[Response] = {
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
    resolveFromPartials(namesParsedSuccessfullySplits) ++ responsesRest
  }
}
