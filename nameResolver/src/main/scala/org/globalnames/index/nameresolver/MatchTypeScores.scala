package org.globalnames
package index
package nameresolver

import thrift.{MatchKind => MK, MatchType}

object MatchTypeScores {

  def createMatchType(matchKind: MK, advancedResolution: Boolean): MatchType = {
    MatchType(kind = matchKind,
              kindString = MatchKindTransform.kindName(matchKind, advancedResolution),
              score = MatchKindTransform.score(matchKind))
  }
}
