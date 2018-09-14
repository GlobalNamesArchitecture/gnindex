package org.globalnames
package index
package nameresolver

import org.apache.commons.lang3.StringUtils
import thrift._
import thrift.{MatchKind => MK}
import parser.{ScientificNameParser => snp}
import org.globalnames.matcher.{Author, AuthorsMatcher}
import index.{MatchKindTransform => MKT}
import scalaz.syntax.either._
import scalaz.syntax.bifunctor._
import scalaz.syntax.std.boolean._
import scalaz.\/

import scala.annotation.switch
import scala.util.Try

final case class ResultScores(nameInputParsed: NameInputParsed, result: Result) {
  private def sigmoid(x: Double): Double = 1 / (1 + math.exp(-x))

  /**
    * |﻿                             | None | 1    | 2                             |
    * |------------------------------|------|------|-------------------------------|
    * | ExactMatch                   | 0,5  | 2, 4 | 2, 6                          |
    * | ExactCanonicalMatch          | 0    | 2, 1 | 2, 8                          |
    * | FuzzyCanonicalMatch          | 0    | 1, 0 | 1, 1                          |
    * | ExactPartialMatch            | 0    | err  | 0.0, -math.log(1 / 0.988 - 1) |
    * | FuzzyPartialMatch            | 0    | err  | err                           |
    * | ExactAbbreviatedMatch        | 0    | err  | 1, 1                          |
    * | FuzzyAbbreviatedMatch        | 0    | err  | 1, 0.5                        |
    * | ExactPartialAbbreviatedMatch | 0    | err  | err                           |
    * | FuzzyPartialAbbreviatedMatch | 0    | err  | err                           |
    *
    * |﻿                             | >2                            |
    * |------------------------------|-------------------------------|
    * | ExactMatch                   | 2, 8                          |
    * | ExactCanonicalMatch          | 2, 10                         |
    * | FuzzyCanonicalMatch          | 2, 2                          |
    * | ExactPartialMatch            | 0.0, -math.log(1 / 0.988 - 1) |
    * | FuzzyPartialMatch            | 1, 0.5                        |
    * | ExactAbbreviatedMatch        | 1, 1                          |
    * | FuzzyAbbreviatedMatch        | 0.5, 0.5                      |
    * | ExactPartialAbbreviatedMatch | 0.5, 0.5                      |
    * | FuzzyPartialAbbreviatedMatch | 0.5, 0.25                     |
    */
  private def computeScoreMessage(result: Result, authorScore: AuthorScore): String \/ Double = {
    val nameType = result.canonicalName.map { canonicalName =>
      StringUtils.countMatches(canonicalName.value, ' ') + 1
    }

    nameType match {
      case Some(nt) =>
        val mki = MKT.matchKindInfo(result.matchType.kind)
        val res: String \/ (Double, Double) = (nt: @switch) match {
          case 1 =>
            mki match {
              case MKT.ExactMatch => (2.0, 4.0).right
              case MKT.ExactCanonicalMatch => (2.0, 1.0).right
              case MKT.FuzzyCanonicalMatch => (1.0, 0.0).right
              case x => s"Unexpected type of match type $x for nameType $nt".left
            }
          case 2 =>
            mki match {
              case MKT.ExactMatch => (2.0, 6.0).right
              case MKT.ExactCanonicalMatch => (2.0, 8.0).right
              case MKT.FuzzyCanonicalMatch => (1.0, 1.0).right
              case MKT.ExactPartialMatch => (0.0, -math.log(1 / 0.988 - 1)).right
              case MKT.ExactAbbreviatedMatch => (1.0, 1.0).right
              case MKT.FuzzyAbbreviatedMatch => (1.0, 0.5).right
              case x => s"Unexpected type of match type $x for nameType $nt".left
            }
          case _ =>
            mki match {
              case MKT.ExactMatch => (2.0, 8.0).right
              case MKT.ExactCanonicalMatch => (2.0, 10.0).right
              case MKT.FuzzyCanonicalMatch => (2.0, 2.0).right
              case MKT.ExactPartialMatch => (0.0, -math.log(1 / 0.988 - 1)).right
              case MKT.FuzzyPartialMatch => (1.0, 0.5).right
              case MKT.ExactAbbreviatedMatch => (1.0, 1.0).right
              case MKT.FuzzyAbbreviatedMatch => (0.5, 0.5).right
              case MKT.ExactPartialAbbreviatedMatch => (0.5, 0.5).right
              case MKT.FuzzyPartialAbbreviatedMatch => (0.5, 0.25).right
              case x => s"Unexpected type of match type $x for nameType $nt".left
            }
        }
        val firstWordEditPenalty = nameInputParsed.firstWordCorrectlyCapitalised ? .0 | -1.0
        res.rightMap { case (authorCoef, generalCoef) =>
          authorScore.value * authorCoef + generalCoef + firstWordEditPenalty
        }
      case None =>
        result.matchType.kind match {
          case _: MK.ExactMatch => 0.5.right
          case _ => s"No match".left
        }
    }
  }

  def compute: Score = {
    val scientificName = nameInputParsed.parsed
    val authorshipInput = scientificName.authorshipNames
      .map { asn => Author(asn.mkString(" ")) }
    val yearInput = for (yr <- scientificName.yearDelimited; y <- Try(yr.toInt).toOption) yield y

    val resultParsedString = snp.instance.fromString(result.name.value)
    val authorshipMatch = resultParsedString.authorshipNames.map { as => Author(as.mkString(" ")) }
    val yearMatch = for {
      yr <- resultParsedString.yearDelimited
      y <- Try(yr.toInt).toOption
    } yield y
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
