package org.globalnames
package microservices.matcher

import com.twitter.finatra.thrift.EmbeddedThriftServer
import com.twitter.inject.server.FeatureTestMixin
import com.twitter.util.Future
import org.globalnames.microservices.matcher.thriftscala.MatcherService

class ServerFeatureTest extends SpecConfig with FeatureTestMixin {

  override val server = new EmbeddedThriftServer(new Server)

  val client: MatcherService[Future] =
    server.thriftClient[MatcherService[Future]](clientId = "client123")

  "service#respond to ping" in {
    client.findMatches("Homo sapiens").value shouldBe Seq("match1", "match2")
  }
}
