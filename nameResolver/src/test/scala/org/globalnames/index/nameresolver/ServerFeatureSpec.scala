package org.globalnames
package index
package nameresolver

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import com.google.inject.Stage
import com.twitter.finatra.thrift.EmbeddedThriftServer
import com.twitter.inject.server.FeatureTestMixin
import com.twitter.util.Future
import thrift.nameresolver.{NameInput, Service => NameResolverService, Request}
import matcher.{MatcherModule, Server => MatcherServer}

class ServerFeatureSpec extends SpecConfig with FeatureTestMixin {

  val canonicalNamesFilePath: Path = {
    val namesFileContent: String = s"""
      |Aaadonta angaurana
      |Aaadonta constricta
      |Aaadonta constricta babelthuapi
      |Abacetus cyclomous
      |Abacetus cyclomus
      |""".stripMargin
    val file = Files.createTempFile("canonical_names", ".txt")
    Files.write(file, namesFileContent.getBytes(StandardCharsets.UTF_8))
  }

  val canonicalNamesWithDatasourcesFilePath: Path = {
    val namesFileContent: String = StringContext.processEscapes(
   """|Aaadonta angaurana\t168
      |Aaadonta angaurana\t10
      |Aaadonta angaurana\t163
      |Aaadonta angaurana\t179
      |Aaadonta angaurana\t12
      |Aaadonta angaurana\t169
      |Aaadonta constricta\t169
      |Aaadonta constricta\t168
      |Aaadonta constricta\t10
      |Aaadonta constricta\t163
      |Aaadonta constricta\t179
      |Aaadonta constricta\t12
      |Aaadonta constricta\t169
      |Aaadonta constricta babelthuapi\t168
      |Aaadonta constricta babelthuapi\t169
      |Abacetus cyclomous\t168
      |Abacetus cyclomus\t1
      |Abacetus cyclomus\t11""".stripMargin)
    val file = Files.createTempFile("canonical_names_with_datasource", ".txt")
    Files.write(file, namesFileContent.getBytes(StandardCharsets.UTF_8))
  }

  val matcherServer = new EmbeddedThriftServer(
    twitterServer = new MatcherServer,
    stage = Stage.PRODUCTION,
    flags = Map(
      MatcherModule.namesFileKey.name -> canonicalNamesFilePath.toString,
      MatcherModule.namesWithDatasourcesFileKey.name ->
        canonicalNamesWithDatasourcesFilePath.toString
    )
  )

  override val server = new EmbeddedThriftServer(
    twitterServer = new Server,
    stage = Stage.PRODUCTION,
    flags = Map(
      NameResolverModule.matcherServiceAddress.name -> matcherServer.thriftHostAndPort
    )
  )

  val client: NameResolverService[Future] =
    server.thriftClient[NameResolverService[Future]](clientId = "nameResolverClient")

  "server#startup" in {
    server.assertHealthy()
  }

  "server#nameResolve" in {
    val request = Request(names = Seq(NameInput("Aaadonta angaurana")))
    val responses = client.nameResolve(request).value.items
    responses.headOption.value.total should be > 0
  }
}
