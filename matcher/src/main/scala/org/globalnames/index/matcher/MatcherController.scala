package org.globalnames
package index
package matcher

import javax.inject.{Singleton, Inject}

import com.twitter.finatra.thrift.Controller
import com.twitter.finatra.thrift.internal.ThriftMethodService
import com.twitter.util.Future
import thrift.{ matcher => tmatcher }

@Singleton
class MatcherController @Inject()(matcher: Matcher)
  extends Controller
     with tmatcher.Service.BaseServiceIface {

  override val findMatches: ThriftMethodService[tmatcher.Service.FindMatches.Args,
                                                Seq[tmatcher.Response]] =
    handle(tmatcher.Service.FindMatches) { args: tmatcher.Service.FindMatches.Args =>
      info("Responding to findMatches")
      Future.value { matcher.resolve(args.names, args.dataSourceIds) }
    }
}
