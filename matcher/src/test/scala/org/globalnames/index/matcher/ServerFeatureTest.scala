package org.globalnames
package index
package matcher

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import com.twitter.finatra.thrift.EmbeddedThriftServer
import com.twitter.inject.server.FeatureTestMixin
import com.twitter.util.Future
import thrift.matcher.{Service => MatcherService, Result, Response }

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

  override val server = new EmbeddedThriftServer(
    twitterServer = new Server,
    flags = Map(
      MatcherModule.namesFileKey.name -> tempFilePath.toString
    )
  )

  val client: MatcherService[Future] =
    server.thriftClient[MatcherService[Future]](clientId = "client123")

  "server#startup" in {
    server.assertHealthy()
  }

  "service#respond to ping" in {
    val word = "Abacetus cyclomoXX"
    client.findMatches(Seq(word)).value should contain only
      Response(input = word, results = Seq(
        Result("Abacetus cyclomus", 3),
        Result("Abacetus cyclomous", 2)
      ))
  }
}
