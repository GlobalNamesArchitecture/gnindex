package org.globalnames
package index

import thrift.{MatchKind => MK}

import scalaz.syntax.std.boolean._

object MatchKindTransform {

  private case class MatchKindInfo(score: Int, messageAdvanced: String, messageSimple: String)

  private val UuidLookup =
    MatchKindInfo(1, "UuidLookup", "UuidLookup")
  private val ExactMatch =
    MatchKindInfo(2, "ExactMatch", "Match")
  private val ExactCanonicalMatch =
    MatchKindInfo(3, "ExactCanonicalMatch", "Match")
  private val FuzzyCanonicalMatch =
    MatchKindInfo(4, "FuzzyCanonicalMatch", "Fuzzy")
  private val ExactPartialMatch =
    MatchKindInfo(5, "ExactPartialMatch", "Match")
  private val FuzzyPartialMatch =
    MatchKindInfo(6, "FuzzyPartialMatch", "Fuzzy")
  private val ExactAbbreviatedMatch =
    MatchKindInfo(7, "ExactAbbreviatedMatch", "Match")
  private val FuzzyAbbreviatedMatch =
    MatchKindInfo(8, "FuzzyAbbreviatedMatch", "Fuzzy")
  private val ExactPartialAbbreviatedMatch =
    MatchKindInfo(9, "ExactPartialAbbreviatedMatch", "Match")
  private val FuzzyPartialAbbreviatedMatch =
    MatchKindInfo(10, "FuzzyPartialAbbreviatedMatch", "Fuzzy")
  private val Unknown =
    MatchKindInfo(11, "Unknown", "Unknown")

  private def matchKindInfo(matchKind: MK): MatchKindInfo = matchKind match {
    case _: MK.UuidLookup => UuidLookup
    case _: MK.ExactMatch => ExactMatch
    case _: MK.Unknown | MK.UnknownUnionField(_) => Unknown
    case MK.CanonicalMatch(cm) =>
      (cm.partial, cm.byAbbreviation) match {
        case (false, false) =>
          (cm.stemEditDistance == 0 && cm.verbatimEditDistance == 0) ?
            ExactCanonicalMatch | FuzzyCanonicalMatch
        case (true, false) =>
          (cm.stemEditDistance == 0 && cm.verbatimEditDistance == 0) ?
            ExactPartialMatch | FuzzyPartialMatch
        case (false, true) =>
          (cm.stemEditDistance == 0 && cm.verbatimEditDistance == 0) ?
            ExactAbbreviatedMatch | FuzzyAbbreviatedMatch
        case (true, true) =>
          (cm.stemEditDistance == 0 && cm.verbatimEditDistance == 0) ?
            ExactPartialAbbreviatedMatch | FuzzyPartialAbbreviatedMatch
      }
  }

  def score(matchKind: MK): Int = matchKindInfo(matchKind).score

  def kindName(matchKind: MK, advancedResolution: Boolean): String = {
    val mki = matchKindInfo(matchKind)
    advancedResolution ? mki.messageAdvanced | mki.messageSimple
  }

}
