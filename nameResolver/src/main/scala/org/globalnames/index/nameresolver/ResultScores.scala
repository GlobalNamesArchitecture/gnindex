package org.globalnames
package index
package nameresolver

import org.apache.commons.lang3.StringUtils
import thrift.nameresolver._
import thrift.nameresolver.MatchKind._
import parser.ScientificNameParser.{Result => SNResult, instance => SNP}
import org.globalnames.matcher.{Author, AuthorsMatcher}

import scalaz.syntax.either._
import scalaz.syntax.bifunctor._
import scalaz.\/
import scala.util.Try

object ResultScores {
  private def sigmoid(x: Double) = 1 / (1 + math.exp(-x))

  private def computeScoreMessage(result: Result, authorScore: AuthorScore): String \/ Double = {
    val nameType = result.canonicalName.map { canonicalName =>
      StringUtils.countMatches(canonicalName.value, ' ') + 1
    }

    nameType match {
      case Some(nt) =>
        val res = nt match {
          case 1 =>
            result.matchType.kind match {
              case ExactNameMatchByUUID => (2.0, 4.0).right
              case ExactCanonicalNameMatchByUUID => (2.0, 1.0).right
              case FuzzyCanonicalMatch => (1.0, 0.0).right
              case ExactMatchPartialByGenus => (0.0, -math.log(1.0 / 3)).right
              case ExactPartialMatch => (0.0, -math.log(1 / 0.988 - 1)).right
              case _ => s"Unexpected type of match type ${result.matchType} for nameType $nt".left
            }
          case 2 =>
            result.matchType.kind match {
              case ExactNameMatchByUUID => (2.0, 8.0).right
              case ExactCanonicalNameMatchByUUID => (2.0, 3.0).right
              case FuzzyCanonicalMatch | ExactMatchPartialByGenus => (1.0, 1.0).right
              case ExactPartialMatch => (0.0, -math.log(1 / 0.988 - 1)).right
              case _ => s"Unexpected type of match type ${result.matchType} for nameType $nt".left
            }
          case _ =>
            result.matchType.kind match {
              case ExactNameMatchByUUID => (2.0, 8.0).right
              case ExactCanonicalNameMatchByUUID => (2.0, 7.0).right
              case FuzzyCanonicalMatch | FuzzyPartialMatch | ExactMatchPartialByGenus =>
                (1.0, 0.5).right
              case ExactPartialMatch => (0.0, -math.log(1 / 0.988 - 1)).right
              case _ => s"Unexpected type of match type ${result.matchType} for nameType $nt".left
            }
        }
        res.rightMap { case (authorCoef, generalCoef) =>
          authorScore.value * authorCoef + generalCoef
        }
      case None =>
        result.matchType.kind match {
          case ExactNameMatchByUUID => 0.5.right
          case _ => s"No match".left
        }
    }
  }

  def compute(scientificName: SNResult, result: Result): Score = {
    val authorshipInput = scientificName.authorshipNames
                                        .map { asn => Author(asn.mkString(" ")) }
    val yearInput = for (yr <- scientificName.yearDelimited; y <- Try(yr.toInt).toOption) yield y

    val resultParsedString = SNP.fromString(result.name.value)
    val authorshipMatch = resultParsedString.authorshipNames.map { as => Author(as.mkString(" ")) }
    val yearMatch = for (yr <- resultParsedString.yearDelimited; y <- Try(yr.toInt).toOption) yield y
    val authorScore =
      AuthorScore(
        authorshipInput = s"${authorshipInput.map { _.name }.mkString(" | ")} || year: $yearInput",
        authorshipMatch = s"${authorshipMatch.map { _.name }.mkString(" | ")} || year: $yearMatch",
        value = AuthorsMatcher.score(authorshipInput, yearInput, authorshipMatch, yearMatch)
      )

    val scoreMsg = computeScoreMessage(result, authorScore)
    val nameType = result.canonicalName.map { can => StringUtils.countMatches(can.value, " ") + 1 }
    val score =
      Score(nameType = nameType,
            authorScore = authorScore,
            parsingQuality = resultParsedString.scientificName.quality,
            value = scoreMsg.rightMap { x => sigmoid(x) }.toOption,
            message = scoreMsg.swap.toOption)
    score
  }
}
