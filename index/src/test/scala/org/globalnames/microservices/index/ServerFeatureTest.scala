package org.globalnames
package microservices.index

import com.google.inject.Stage
import com.twitter.finatra.thrift.EmbeddedThriftServer
import com.twitter.inject.server.FeatureTestMixin
import com.twitter.util.Future
import thriftscala.IndexService

class ServerFeatureTest extends SpecConfig with FeatureTestMixin {

  override val server = new EmbeddedThriftServer(
    twitterServer = new Server,
    stage = Stage.PRODUCTION
  )

  val client: IndexService[Future] =
    server.thriftClient[IndexService[Future]](clientId = "client123")

  "server#startup" in {
    server.assertHealthy()
  }
}
