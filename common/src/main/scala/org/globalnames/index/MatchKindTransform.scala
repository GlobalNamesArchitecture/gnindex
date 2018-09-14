package org.globalnames
package index

import thrift.{MatchKind => MK}

import scalaz.syntax.std.boolean._

object MatchKindTransform {

  sealed trait MatchKindInfo {
    val score: Int
    val messageAdvanced: String
    val messageSimple: String
  }

  case object UuidLookup extends MatchKindInfo {
    val score: Int = 1
    val messageAdvanced: String = "UuidLookup"
    val messageSimple: String = "UuidLookup"
  }

  case object ExactMatch extends MatchKindInfo {
    val score: Int = 2
    val messageAdvanced: String = "ExactMatch"
    val messageSimple: String = "Match"
  }

  case object ExactCanonicalMatch extends MatchKindInfo {
    val score: Int = 3
    val messageAdvanced: String = "ExactCanonicalMatch"
    val messageSimple: String = "Match"
  }

  case object FuzzyCanonicalMatch extends MatchKindInfo {
    val score: Int = 4
    val messageAdvanced: String = "FuzzyCanonicalMatch"
    val messageSimple: String = "Fuzzy"
  }

  case object ExactPartialMatch extends MatchKindInfo {
    val score: Int = 5
    val messageAdvanced: String = "ExactPartialMatch"
    val messageSimple: String = "Match"
  }

  case object FuzzyPartialMatch extends MatchKindInfo {
    val score: Int = 6
    val messageAdvanced: String = "FuzzyPartialMatch"
    val messageSimple: String = "Fuzzy"
  }

  case object ExactAbbreviatedMatch extends MatchKindInfo {
    val score: Int = 7
    val messageAdvanced: String = "ExactAbbreviatedMatch"
    val messageSimple: String = "Match"
  }

  case object FuzzyAbbreviatedMatch extends MatchKindInfo {
    val score: Int = 8
    val messageAdvanced: String = "FuzzyAbbreviatedMatch"
    val messageSimple: String = "Fuzzy"
  }

  case object ExactPartialAbbreviatedMatch extends MatchKindInfo {
    val score: Int = 9
    val messageAdvanced: String = "ExactPartialAbbreviatedMatch"
    val messageSimple: String = "Match"
  }

  case object FuzzyPartialAbbreviatedMatch extends MatchKindInfo {
    val score: Int = 0
    val messageAdvanced: String = "FuzzyPartialAbbreviatedMatch"
    val messageSimple: String = "Fuzzy"
  }

  case object Unknown extends MatchKindInfo {
    val score: Int = 1
    val messageAdvanced: String = "Unknown"
    val messageSimple: String = "Unknown"
  }

  def matchKindInfo(matchKind: MK): MatchKindInfo = matchKind match {
    case _: MK.UuidLookup => UuidLookup
    case _: MK.ExactMatch => ExactMatch
    case _: MK.Unknown | MK.UnknownUnionField(_) => Unknown
    case MK.CanonicalMatch(cm) =>
      (cm.partial, cm.byAbbreviation) match {
        case (false, false) =>
          (cm.stemEditDistance == 0 && cm.verbatimEditDistance == 0) ?
            (ExactCanonicalMatch: MatchKindInfo) | FuzzyCanonicalMatch
        case (true, false) =>
          (cm.stemEditDistance == 0 && cm.verbatimEditDistance == 0) ?
            (ExactPartialMatch: MatchKindInfo) | FuzzyPartialMatch
        case (false, true) =>
          (cm.stemEditDistance == 0 && cm.verbatimEditDistance == 0) ?
            (ExactAbbreviatedMatch: MatchKindInfo) | FuzzyAbbreviatedMatch
        case (true, true) =>
          (cm.stemEditDistance == 0 && cm.verbatimEditDistance == 0) ?
            (ExactPartialAbbreviatedMatch: MatchKindInfo) | FuzzyPartialAbbreviatedMatch
      }
  }

  def score(matchKind: MK): Int = matchKindInfo(matchKind).score

  def kindName(matchKind: MK, advancedResolution: Boolean): String = {
    val mki = matchKindInfo(matchKind)
    advancedResolution ? mki.messageAdvanced | mki.messageSimple
  }

}
