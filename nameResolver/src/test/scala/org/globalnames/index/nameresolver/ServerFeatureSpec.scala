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
import index.{nameresolver => nr, matcher => m}

class ServerFeatureSpec extends WordSpecConfig with FeatureTestMixin {
  override def launchConditions: Boolean = matcherServer.isHealthy

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
    twitterServer = new m.Server,
    stage = Stage.PRODUCTION,
    flags = Map(
      m.MatcherModule.namesFileKey.name -> canonicalNamesFilePath.toString,
      m.MatcherModule.namesWithDatasourcesFileKey.name ->
        canonicalNamesWithDatasourcesFilePath.toString
    )
  )

  override val server = new EmbeddedThriftServer(
    twitterServer = new nr.Server,
    stage = Stage.PRODUCTION,
    flags = Map(
      NameResolverModule.matcherServiceAddress.name -> matcherServer.thriftHostAndPort
    )
  )

  protected override def afterAll(): Unit = {
    matcherServer.close()
    server.close()
  }

  val client: NameResolverService[Future] =
    server.thriftClient[NameResolverService[Future]](clientId = "nameResolverClient")

  "server#startup" in {
    server.assertHealthy()
  }

  "server#nameResolve" in {
    val request = Request(nameInputs = Seq(NameInput("Homo sapiens")))
    val responses = client.nameResolve(request).value
    responses.responses.headOption.value.total should be > 0
  }
}
