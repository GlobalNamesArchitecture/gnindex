package org.globalnames
package index
package nameresolver

import thrift.{MatchKind => MK, MatchType}

object MatchTypeScores {

  private object ScoreValues {
    val UuidLookup = 1
    val ExactMatch = 2
    val ExactCanonicalMatch = 3
    val FuzzyCanonicalMatch = 4
    val ExactPartialMatch = 5
    val FuzzyPartialMatch = 6
    val ExactAbbreviatedMatch = 7
    val FuzzyAbbreviatedMatch = 8
    val ExactPartialAbbreviatedMatch = 9
    val FuzzyPartialAbbreviatedMatch = 10
  }

  private def score(matchKind: MK): Int = matchKind match {
    case _: MK.UuidLookup => ScoreValues.UuidLookup
    case _: MK.ExactMatch => ScoreValues.ExactMatch
    case MK.CanonicalMatch(cm) =>
      (cm.partial, cm.byAbbreviation) match {
        case (false, false) =>
          if (cm.stemEditDistance == 0 && cm.verbatimEditDistance == 0) {
            ScoreValues.ExactCanonicalMatch
          } else {
            ScoreValues.FuzzyCanonicalMatch
          }
        case (true, false) =>
          if (cm.stemEditDistance == 0 && cm.verbatimEditDistance == 0) {
            ScoreValues.ExactPartialMatch
          } else {
            ScoreValues.FuzzyPartialMatch
          }
        case (false, true) =>
          if (cm.stemEditDistance == 0 && cm.verbatimEditDistance == 0) {
            ScoreValues.ExactAbbreviatedMatch
          } else {
            ScoreValues.FuzzyAbbreviatedMatch
          }
        case (true, true) =>
          if (cm.stemEditDistance == 0 && cm.verbatimEditDistance == 0) {
            ScoreValues.ExactPartialAbbreviatedMatch
          } else {
            ScoreValues.FuzzyPartialAbbreviatedMatch
          }
      }
  }

  def createMatchType(matchKind: MK): MatchType = {
    MatchType(kind = matchKind, score = score(matchKind))
  }
}
