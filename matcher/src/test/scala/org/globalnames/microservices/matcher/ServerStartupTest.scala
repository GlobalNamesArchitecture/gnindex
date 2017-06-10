package org.globalnames
package microservices.matcher

import com.google.inject.Stage
import com.twitter.finatra.thrift.EmbeddedThriftServer
import com.twitter.inject.server.FeatureTestMixin

class ServerStartupTest extends SpecConfig with FeatureTestMixin {

  val server = new EmbeddedThriftServer(
    twitterServer = new Server,
    stage = Stage.PRODUCTION)

  "server#startup" in {
    server.assertHealthy()
  }
}
