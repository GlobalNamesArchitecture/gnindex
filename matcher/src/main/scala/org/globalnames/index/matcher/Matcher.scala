package org.globalnames
package index
package matcher

import org.globalnames.{matcher => matcherlib}
import thrift.matcher.{Response, Result}
import thrift.Name
import javax.inject.{Inject, Singleton}

import parser.ScientificNameParser.{instance => SNP}
import akka.http.impl.util._
import index.thrift.MatchKind
import org.globalnames.matcher.Candidate
import util.UuidEnhanced.javaUuid2thriftUuid

import scala.collection.immutable.LinearSeq

@Singleton
class Matcher @Inject()(matcherLib: matcherlib.Matcher) {
  private val FuzzyMatchLimit = 5

  private case class CanonicalNameSplit(name: String, parts: LinearSeq[String]) {
    val namePartialStr: String = parts.mkString(" ")
    val isOriginalCanonical: Boolean = name.length == parts.map(_.length + 1).sum - 1

    def shortenParts: CanonicalNameSplit = {
      this.copy(parts = this.parts.dropRight(1))
    }

    def nameProvided: Name = {
      Name(uuid = UuidGenerator.generate(name), value = name)
    }

    def namePartial: Name = {
      Name(uuid = UuidGenerator.generate(namePartialStr), value = namePartialStr)
    }
  }

  private case class FuzzyMatch(canonicalNameSplit: CanonicalNameSplit,
                                candidates: Vector[Candidate])

  private
  def resolveFromPartials(canonicalNameSplits: Seq[CanonicalNameSplit]): Seq[Response] = {
    if (canonicalNameSplits.isEmpty) {
      Seq()
    } else {
      val (originalOrNonUninomialCanonicals, partialByGenusCanonicals) =
        canonicalNameSplits.partition { canonicalNameSplit =>
          canonicalNameSplit.isOriginalCanonical || canonicalNameSplit.parts.size > 1
        }

      val partialByGenusFuzzyResponses =
        for (canonicalNameSplit <- partialByGenusCanonicals) yield {
          val result = Result(nameMatched = canonicalNameSplit.namePartial,
                              distance = 0,
                              matchKind = MatchKind.ExactMatchPartialByGenus)
          Response(input = canonicalNameSplit.nameProvided, results = Seq(result))
        }

      val originalOrNonUninomialCanonicalsResponses =
        for (canNmSplit <- originalOrNonUninomialCanonicals) yield {
          FuzzyMatch(canNmSplit, matcherLib.findMatches(canNmSplit.namePartialStr))
        }

      val (oonucrNonEmpty, oonucrEmpty) =
        originalOrNonUninomialCanonicalsResponses.partition { _.candidates.nonEmpty }

      val responsesEmpty =
        resolveFromPartials(oonucrEmpty.map { _.canonicalNameSplit.shortenParts })

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

      partialByGenusFuzzyResponses ++ responsesNonEmpty ++ responsesEmpty
    }
  }

  def resolve(names: Seq[String]): Seq[Response] = {
    val namesParsed = names.map { name => SNP.fromString(name) }
    val (namesParsedSuccessfully, namesParsedRest) = namesParsed.partition { np =>
      np.canonized().isDefined
    }
    val responsesRest = namesParsedRest.map { np =>
      Response(input = Name(uuid = np.preprocessorResult.id,
               value = np.preprocessorResult.verbatim), results = Seq())
    }
    val namesParsedSuccessfullySplits = namesParsedSuccessfully.map { np =>
      CanonicalNameSplit(np.preprocessorResult.verbatim,
        np.preprocessorResult.verbatim.fastSplit(' '))
    }
    resolveFromPartials(namesParsedSuccessfullySplits) ++ responsesRest
  }
}
