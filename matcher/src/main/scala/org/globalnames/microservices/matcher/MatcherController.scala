package org.globalnames.microservices.matcher

import thriftscala.MatcherService
import thriftscala.MatcherService.FindMatches
import com.twitter.finatra.thrift.Controller
import com.twitter.util.Future
import javax.inject.Singleton

import com.twitter.finatra.thrift.internal.ThriftMethodService

@Singleton
class MatcherController
  extends Controller
    with MatcherService.BaseServiceIface {

  override val findMatches: ThriftMethodService[FindMatches.Args, Seq[String]] =
    handle(FindMatches) { args: FindMatches.Args =>
      info("Responding to findMatches")
      Future.value(Seq("match1", "match2"))
    }
}
