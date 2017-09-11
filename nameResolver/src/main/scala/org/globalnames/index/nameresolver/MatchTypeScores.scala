package org.globalnames
package index
package nameresolver

import thrift.{MatchKind => MK, MatchType}

object MatchTypeScores {

  private def score(matchKind: MK): Int = matchKind match {
    case MK.UUIDLookup => 1
    case MK.ExactNameMatchByUUID => 2
    case MK.ExactNameMatchByString => 3
    case MK.ExactCanonicalNameMatchByUUID => 4
    case MK.ExactCanonicalNameMatchByString => 5
    case MK.FuzzyCanonicalMatch => 6
    case MK.ExactPartialMatch => 7
    case MK.FuzzyPartialMatch => 8
    case MK.ExactMatchPartialByGenus => 9

    case MK.Match => 2
    case MK.FuzzyMatch => 6

    case MK.Unknown => 10
    case _: MK.EnumUnknownMatchKind => 10
  }

  def createMatchType(matchKind: MK, editDistance: Int): MatchType = {
    MatchType(kind = matchKind,
              score = score(matchKind),
              editDistance = editDistance)
  }
}
