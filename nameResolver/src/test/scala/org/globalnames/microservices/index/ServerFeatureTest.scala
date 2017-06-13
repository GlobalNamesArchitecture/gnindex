package org.globalnames
package microservices
package index

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import com.google.inject.Stage
import com.twitter.finatra.thrift.EmbeddedThriftServer
import com.twitter.inject.server.FeatureTestMixin
import com.twitter.util.Future
import index.nameresolve.thriftscala.Response
import index.thriftscala.IndexService
import matcher.{MatcherModule, Server => MatcherServer}

class ServerFeatureTest extends SpecConfig with FeatureTestMixin {

  val tempFilePath: Path = {
    def namesFileContent: String = s"""
      |Aaadonta angaurana
      |Aaadonta constricta
      |Aaadonta constricta babelthuapi
      |Abacetus cyclomous
      |Abacetus cyclomus
      |""".stripMargin
    val file = Files.createTempFile("canonical_names", ".txt")
    Files.write(file, namesFileContent.getBytes(StandardCharsets.UTF_8))
  }

  val matcherServer = new EmbeddedThriftServer(
    twitterServer = new MatcherServer,
    stage = Stage.PRODUCTION,
    flags = Map(
      MatcherModule.namesFileKey.name -> tempFilePath.toString
    )
  )

  override val server = new EmbeddedThriftServer(
    twitterServer = new Server,
    stage = Stage.PRODUCTION,
    flags = Map(
      IndexModule.matcherServiceAddress.name -> matcherServer.thriftHostAndPort
    )
  )

  val client: IndexService[Future] =
    server.thriftClient[IndexService[Future]](clientId = "client123")

  "server#startup" in {
    server.assertHealthy()
  }

  "server#nameResolve" in {
    val responses: Seq[Response] = client.nameResolve(Seq()).value
    responses.head.total should be > 0
  }
}
