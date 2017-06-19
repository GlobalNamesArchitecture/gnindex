package org.globalnames
package index
package matcher

import javax.inject.{Singleton, Inject}

import com.twitter.finatra.thrift.Controller
import com.twitter.finatra.thrift.internal.ThriftMethodService
import com.twitter.util.Future
import thrift.{ matcher => tmatcher }
import org.globalnames.matcher.Matcher

@Singleton
class MatcherController @Inject()(matcher: Matcher)
  extends Controller
     with tmatcher.Service.BaseServiceIface {

  override val findMatches: ThriftMethodService[tmatcher.Service.FindMatches.Args,
                                                Seq[tmatcher.Response]] =
    handle(tmatcher.Service.FindMatches) { args: tmatcher.Service.FindMatches.Args =>
      info(s"Responding to findMatches: ${args.words.mkString(", ")}")
      val responses = args.words.map { word =>
        val matches = matcher.findMatches(word)
                             .map { cand => tmatcher.Result(cand.term, cand.distance.toShort) }
        tmatcher.Response(input = word, results = matches)
      }

      Future.value { responses }
    }
}
