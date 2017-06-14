package org.globalnames
package index
package matcher

import javax.inject.{Singleton, Inject}

import com.twitter.finatra.thrift.Controller
import com.twitter.finatra.thrift.internal.ThriftMethodService
import com.twitter.util.Future
import thrift.matcher.{MatcherResult, MatcherService}
import thrift.matcher.MatcherService.FindMatches
import org.globalnames.matcher.Matcher

@Singleton
class MatcherController @Inject()(matcher: Matcher)
  extends Controller
     with MatcherService.BaseServiceIface {

  override val findMatches: ThriftMethodService[FindMatches.Args, Seq[MatcherResult]] =
    handle(FindMatches) { args: FindMatches.Args =>
      info(s"Responding to findMatches: ${args.word}")
      val matches = matcher.findMatches(args.word)
      Future.value {
        matches.map { cand => MatcherResult(cand.term, cand.distance.toShort) }
      }
    }
}
