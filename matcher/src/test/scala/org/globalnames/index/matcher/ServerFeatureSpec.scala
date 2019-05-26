package org.globalnames
package index
package matcher

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import com.twitter.finatra.thrift.EmbeddedThriftServer
import com.twitter.inject.server.FeatureTestMixin
import com.twitter.util.Future
import thrift.matcher.{Service => MatcherService}

class ServerFeatureSpec extends WordSpecConfig with FeatureTestMixin {

  val canonicalNamesFilePath: Path = {
    def namesFileContent: String =
       """Aaadonta angaurana
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
       """Aaadonta angaurana\t168
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

  override val server = new EmbeddedThriftServer(
    twitterServer = new Server,
    flags = Map(
      MatcherModule.namesFileKey.name -> canonicalNamesFilePath.toString,
      MatcherModule.namesWithDatasourcesFileKey.name ->
        canonicalNamesWithDatasourcesFilePath.toString
    )
  )

  protected override def afterAll(): Unit = {
    super.afterAll()
    server.close()
  }

  val client: MatcherService[Future] =
    server.thriftClient[MatcherService[Future]](clientId = "matcherClient")

  "server#startup" in {
    server.assertHealthy()
  }

  "service#findMatches returns results" in {
    val word = "Abacetus cyclomux"
    val matches = client.findMatches(Seq(word), advancedResolution = true).value
    matches.size shouldBe 1
    matches.headOption.value.results.map { _.nameMatched.value } should
      contain only "Abacetus cyclomus"
  }
}
