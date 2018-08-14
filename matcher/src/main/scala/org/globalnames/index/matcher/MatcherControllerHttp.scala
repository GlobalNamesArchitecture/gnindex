package org.globalnames.index.matcher

import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import javax.inject.Inject

class MatcherControllerHttp @Inject()(matcher: Matcher) extends Controller {
  get("/health") { _: Request =>
    if (matcher.matcherLibFut.isCompleted) {
      response.ok.json("matchers are READY")
    } else {
      response.internalServerError.json("matchers are NOT ready")
    }
  }
}
