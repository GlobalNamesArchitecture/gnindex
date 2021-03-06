package org.globalnames
package index
package namefilter

import java.util.UUID

import biz.neumann.NiceUUID._
import com.google.inject.Stage
import com.twitter.finatra.thrift.EmbeddedThriftServer
import com.twitter.inject.server.FeatureTestMixin
import com.twitter.util.Future
import thrift.{namefilter => nf}
import thrift.namefilter.{Request, ResponseNameStrings}
import util.UuidEnhanced._
import matcher.{MatcherModule, Server => MatcherServer}

class NameFilterSpec extends FunSpecConfig with FeatureTestMixin {

  import QueryParser._

  override def launchConditions: Boolean = matcherServer.isHealthy

  val matcherServer = new EmbeddedThriftServer(
    twitterServer = new MatcherServer,
    stage = Stage.PRODUCTION,
    flags = Map(
      MatcherModule.namesFileKey.name ->
        "db-migration/matcher-data/canonical-names.csv",
      MatcherModule.namesWithDatasourcesFileKey.name ->
        "db-migration/matcher-data/canonical-names-with-data-sources.csv"
    )
  )

  override val server = new EmbeddedThriftServer(
    twitterServer = new Server,
    stage = Stage.PRODUCTION,
    flags = Map(
      NameFilterModule.matcherServiceAddress.name -> matcherServer.thriftHostAndPort
    )
  )

  protected override def afterAll(): Unit = {
    super.afterAll()
    matcherServer.close()
    server.close()
  }

  val nameFilterClient: nf.Service[Future] =
    server.thriftClient[nf.Service[Future]](clientId = "nameFilterClient")

  case class ExpectedValue(nameUuid: UUID)

  def resultUuids(result: ResponseNameStrings): Seq[ExpectedValue] = {
    result.resultNameStrings.map { rns => ExpectedValue(rns.name.uuid) }
  }

  def resultsCount(result: ResponseNameStrings): Seq[Int] = {
    result.resultNameStrings.map { rns => rns.results.size }
  }

  def names(uuid: String): ExpectedValue = {
    ExpectedValue(UUID.fromString(uuid))
  }

  describe("NameFilter") {
    describe(".resolveCanonical") {
      it("resolves exact") {
        val request = Request(searchTerm = s"$canonicalModifierStr:Aaadonta constricta")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe Seq(5, 2)
        resultUuids(result) should contain only(
          names(u"b2cf575f-ec53-50ec-96b4-da94de2d926f"),
          names(u"e529d978-6a13-578b-b3eb-bd9b8ad50a53")
        )
      }

      it("resolves exact with multiple spaces input") {
        val request = Request(searchTerm = s"$canonicalModifierStr:  \t  Aaadonta   constricta    ")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe Seq(5, 2)
        resultUuids(result) should contain only(
          names(u"b2cf575f-ec53-50ec-96b4-da94de2d926f"),
          names(u"e529d978-6a13-578b-b3eb-bd9b8ad50a53")
        )
      }

      it("resolves exact with wildcard inside input") {
        val request = Request(searchTerm = s"$canonicalModifierStr:Aaadonta %constricta")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe Seq(5, 2)
        resultUuids(result) should contain only(
          names(u"b2cf575f-ec53-50ec-96b4-da94de2d926f"),
          names(u"e529d978-6a13-578b-b3eb-bd9b8ad50a53")
        )
      }

      it("resolves exact with wildcard as input") {
        val request = Request(searchTerm = s"$canonicalModifierStr:%")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe empty
      }

      it("resolves wildcard") {
        val request = Request(searchTerm = s"$canonicalModifierStr:Aaadonta constricta ba*")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe Seq(1, 1)
        resultUuids(result) should contain only(
          names(u"5a68f4ec-6121-553e-8843-3d602089ec88"),
          names(u"073bab60-1816-5b5c-b018-87b4193db6f7")
        )
      }

      it("resolves no mathches when string request of length less than 4 is provided") {
        val request = Request(searchTerm = s"$canonicalModifierStr:Aaa")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe empty
      }

      it("returns no wildcarded matches when empty string is provided") {
        val request = Request(searchTerm = s"$canonicalModifierStr:")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe empty
      }
    }

    describe(".resolveAuthor") {
      it("resolves") {
        val request = Request(searchTerm = s"$authorModifierStr:Abakar-Ousman")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe Seq(1, 4, 1, 4, 1, 4)
        resultUuids(result) should contain only(
          names(u"4168bf10-7462-53c6-b350-f6052c082965"),
          names(u"5272e0e6-ad53-5f1e-826b-8a024b01ce27"),
          names(u"8898d85f-49a9-5029-aea9-9fbed1467d46"),
          names(u"a2842c8d-2681-5840-8e33-6c1daf9acb24"),
          names(u"ea69f5c2-340b-5ec3-8b1c-5931ba1ce179"),
          names(u"f36a6f4d-ddfc-509c-a652-5b50e8372006")
        )
      }

      it("returns no matches when empty string is provided") {
        val request = Request(searchTerm = s"$authorModifierStr:")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe empty
      }
    }

    describe(".resolveYear") {
      it("resolves") {
        val request = Request(searchTerm = s"$yearModifierStr:1753")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe Seq(3, 3, 1, 2)
        resultUuids(result) should contain only(
          names(u"00091866-0b4e-5934-bb64-0e6cdd4187c1"),
          names(u"000ec063-ea1b-5ce0-b8c5-f94b310fc25d"),
          names(u"00123893-2568-56e6-844c-5204b282c87c"),
          names(u"0014bd8e-a419-5db1-bf53-ddcedebf0e0c")
        )
      }

      it("returns no matches on non-existing year") {
        val request = Request(searchTerm = s"$yearModifierStr:3000")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe empty
      }

      it("returns no matches when empty string is provided") {
        val request = Request(searchTerm = s"$yearModifierStr:")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe empty
      }
    }

    describe(".resolveUninomial") {
      it("resolves") {
        val request = Request(searchTerm = s"$uninomialModifierStr:Aalenirhynchia")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe Seq(2, 1, 1, 3, 1)
        resultUuids(result) should contain only(
          names(u"05375e93-f74c-5bf4-8815-1cc363c1b98c"),
          names(u"14414d49-b321-5aa3-9da1-32ca0ba45614"),
          names(u"278b8361-cdb1-5a0b-8e21-9bba57880121"),
          names(u"c96fd1c5-c5cb-50ed-afd1-63bd1368896b"),
          names(u"e852a1e4-3b87-56b5-9c40-94e8fb165b52")
        )
      }

      it("returns no matches when empty string is provided") {
        val request = Request(searchTerm = s"$uninomialModifierStr:")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe empty
      }
    }

    describe(".resolveGenus") {
      it("resolves") {
        val request = Request(searchTerm = s"$genusModifierStr:Buxela")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe Seq(2, 1, 2)
        resultUuids(result) should contain only(
          names(u"94fc8bf8-098c-5d49-a766-b7a71296024a"),
          names(u"c91b7662-42ea-59ae-8b08-91832939f5e1"),
          names(u"d710a5b8-ecd8-5222-a57f-21c1e8aa9166")
        )
      }

      it("resolves with wildcard") {
        val request = Request(searchTerm = s"$genusModifierStr:Buxet*")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe Seq(2, 4, 3, 2, 2, 4, 4)
        resultUuids(result) should contain only(
          names(u"231467e3-5822-5ce6-8a93-8bf539957900"),
          names(u"7a74b27e-51f5-5b09-b2d6-43264cc812ca"),
          names(u"4323d4d8-1e8d-5e4a-966a-e3f831d2c3eb"),
          names(u"5a041add-35ee-5380-ae65-980bc834de70"),
          names(u"9df9e622-26ff-51d6-a007-a41279d09cf6"),
          names(u"b48f2da8-82d8-5cdf-b4be-c83f7438715b"),
          names(u"d31f59fb-a5bb-53aa-bacc-08294378ba27")
        )
      }

      it("resolves lowercase") {
        val request = Request(searchTerm = s"$uninomialModifierStr:buxela")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe Seq(4, 3, 2, 4)
        resultUuids(result) should contain only(
          names(u"2fb5da60-3b02-5605-9411-a6634c4d535a"),
          names(u"b7ea5458-c845-50cc-bb4e-dac4b662c456"),
          names(u"d2c76b8f-f558-5df9-a96c-e900df95e188"),
          names(u"dd4f37b7-911b-5374-9b51-aedcd7a42d97")
        )
      }

      it("resolves binomial") {
        val request = Request(searchTerm = s"$uninomialModifierStr:Aalenirhynchia ab")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe empty
      }

      it("returns no matches on non-existing input") {
        val request = Request(searchTerm = s"$genusModifierStr:Aalenirhynchia1")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe empty
      }

      it("returns no matches when empty string is provided") {
        val request = Request(searchTerm = s"$genusModifierStr:")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe empty
      }
    }

    describe(".resolveSpecies") {
      it("resolves") {
        val request = Request(searchTerm = s"$speciesModifierStr:cynoscion")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe Seq(2, 1, 6)
        resultUuids(result) should contain only(
          names(u"173848c3-baa3-5662-ba1c-40b1a363e182"),
          names(u"47712a8d-2dd2-5e0a-b980-f1e47e38d498"),
          names(u"90efc796-95c8-59a4-b5e1-971853e50696")
        )
      }

      it("returns no matches when empty string is provided") {
        val request = Request(searchTerm = s"$speciesModifierStr:")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe empty
      }
    }

    describe(".resolveSubspecies") {
      it("resolves") {
        val request = Request(searchTerm = s"$subSpeciesModifierStr:Albiflorum")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe Seq(1, 1)
        resultUuids(result) should contain only(
          names(u"000c1fa5-1592-586d-a72a-a86edffbb13f"),
          names(u"0004b821-9a29-504e-a3b6-2f6964a3eee8")
        )
      }

      it("returns no matches when empty string is provided") {
        val request = Request(searchTerm = s"$subSpeciesModifierStr:")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe empty
      }
    }

    describe(".resolveNameStrings") {
      it("resolves exact") {
        val request =
          Request(searchTerm = s"$nameStringModifierStr:Aaadonta constricta babelthuapi")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe Seq(1)
        resultUuids(result) should contain only
          names(u"5a68f4ec-6121-553e-8843-3d602089ec88")
      }

      it("returns no matches when empty string is provided") {
        val request = Request(searchTerm = s"$nameStringModifierStr:")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe empty
      }

      it("resolves exact with non-existing input") {
        val request = Request(searchTerm = s"$nameStringModifierStr:Pararara")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe empty
      }

      it("resolves no matches when empty string is provided") {
        val request = Request(searchTerm = s"$nameStringModifierStr:")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe empty
      }

      it("resolves wildcard") {
        val request = Request(searchTerm = s"$nameStringModifierStr:Aaadonta constricta komak*")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe Seq(1, 1)
        resultUuids(result) should contain only(
          names(u"51b7b1b2-07ba-5a0e-a65d-c5ca402b58de"),
          names(u"edd01cc8-0e7a-5370-8d90-173d24c9341c")
        )
      }

      it("returns no wildcarded matches when empty string is provided") {
        val request = Request(searchTerm = s"$nameStringModifierStr:")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe empty
      }

      it("resolves nothing wildcard with non-existing stirng") {
        val request = Request(searchTerm = s"$nameStringModifierStr:Pararara*")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe empty
      }

      it("resolves wildcard with wildcard in begin of input") {
        val request = Request(searchTerm = s"$nameStringModifierStr:%Aaadonta constricta ba*")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe Seq(1, 1)
        resultUuids(result) should contain only(
          names(u"073bab60-1816-5b5c-b018-87b4193db6f7"),
          names(u"5a68f4ec-6121-553e-8843-3d602089ec88")
        )
      }

      it("resolves wildcard with wildcard in middle of input") {
        val request = Request(searchTerm = s"$nameStringModifierStr:Aaadonta constricta * ba*")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe Seq(1, 1)
        resultUuids(result) should contain only(
          names(u"5a68f4ec-6121-553e-8843-3d602089ec88"),
          names(u"073bab60-1816-5b5c-b018-87b4193db6f7")
        )
      }

      it("resolves no matches when string request of length less than 4 is provided") {
        val request = Request(searchTerm = s"$nameStringModifierStr:Aaa*")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe empty
      }
    }

    describe(".resolveExact") {
      it("resolves") {
        val request = Request(searchTerm = s"$exactStringModifierStr:Aalenirhynchia")
        val result = nameFilterClient.nameString(request = request).value
        resultsCount(result) shouldBe Seq(2)
        resultUuids(result) should contain only
          names(u"c96fd1c5-c5cb-50ed-afd1-63bd1368896b")
      }
    }

    describe("nameStringsByUuid") {
      it("resolves") {
        val scala.util.Success(uuid) = u"b2cf575f-ec53-50ec-96b4-da94de2d926f".uuid
        val responses = nameFilterClient.nameStringByUuid(Seq(uuid)).value
        responses.size shouldBe 1
        for (response <- responses) {
          (response.uuid: UUID) shouldBe uuid
          response.results.size shouldBe 2
          response.results.map { _.dataSource.id } should contain only (168, 169)
          response.results.map { _.name.uuid: UUID } should contain only uuid
        }
      }
    }
  }
}
