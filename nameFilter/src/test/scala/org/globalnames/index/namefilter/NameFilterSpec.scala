package org.globalnames
package index
package namefilter

import java.util.UUID

import biz.neumann.NiceUUID._
import com.google.inject.Stage
import com.twitter.finatra.thrift.EmbeddedThriftServer
import com.twitter.inject.server.FeatureTestMixin
import com.twitter.util.Future
import thrift.ResultScored
import thrift.namefilter.{Request, Service => NameFilterService}
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

  val nameFilterClient: NameFilterService[Future] =
    server.thriftClient[NameFilterService[Future]](clientId = "nameFilterClient")

  case class ExpectedValue(nameUuid: UUID)

  def project(resultScored: ResultScored): ExpectedValue = {
    ExpectedValue(resultScored.result.name.uuid)
  }

  val names: Map[String, ExpectedValue] = Map(
    u"03e71643-d238-5859-af6f-b98a129ebe12" ->
      ExpectedValue(u"03e71643-d238-5859-af6f-b98a129ebe12".uuid.get),

    u"04009880-0824-59aa-aa64-66c045d5d00f" ->
      ExpectedValue(u"04009880-0824-59aa-aa64-66c045d5d00f".uuid.get),

    u"05375e93-f74c-5bf4-8815-1cc363c1b98c" ->
      ExpectedValue(u"05375e93-f74c-5bf4-8815-1cc363c1b98c".uuid.get),

    u"073bab60-1816-5b5c-b018-87b4193db6f7" ->
      ExpectedValue(u"073bab60-1816-5b5c-b018-87b4193db6f7".uuid.get),

    u"14414d49-b321-5aa3-9da1-32ca0ba45614" ->
      ExpectedValue(u"14414d49-b321-5aa3-9da1-32ca0ba45614".uuid.get),

    u"173848c3-baa3-5662-ba1c-40b1a363e182" ->
      ExpectedValue(u"173848c3-baa3-5662-ba1c-40b1a363e182".uuid.get),

    u"278b8361-cdb1-5a0b-8e21-9bba57880121" ->
      ExpectedValue(u"278b8361-cdb1-5a0b-8e21-9bba57880121".uuid.get),

    u"2dfec1f4-0f12-562a-83d3-8aa3ed7ebef8" ->
      ExpectedValue(u"2dfec1f4-0f12-562a-83d3-8aa3ed7ebef8".uuid.get),

    u"2fb5da60-3b02-5605-9411-a6634c4d535a" ->
      ExpectedValue(u"2fb5da60-3b02-5605-9411-a6634c4d535a".uuid.get),

    u"30d121d1-3538-54a9-9554-2c2e75382311" ->
      ExpectedValue(u"30d121d1-3538-54a9-9554-2c2e75382311".uuid.get),

    u"4168bf10-7462-53c6-b350-f6052c082965" ->
      ExpectedValue(u"4168bf10-7462-53c6-b350-f6052c082965".uuid.get),

    u"47712a8d-2dd2-5e0a-b980-f1e47e38d498" ->
      ExpectedValue(u"47712a8d-2dd2-5e0a-b980-f1e47e38d498".uuid.get),

    u"51b7b1b2-07ba-5a0e-a65d-c5ca402b58de" ->
      ExpectedValue(u"51b7b1b2-07ba-5a0e-a65d-c5ca402b58de".uuid.get),

    u"5272e0e6-ad53-5f1e-826b-8a024b01ce27" ->
      ExpectedValue(u"5272e0e6-ad53-5f1e-826b-8a024b01ce27".uuid.get),

    u"5a68f4ec-6121-553e-8843-3d602089ec88" ->
      ExpectedValue(u"5a68f4ec-6121-553e-8843-3d602089ec88".uuid.get),

    u"64d3585d-714c-5fcf-b3b9-b1790af61baa" ->
      ExpectedValue(u"64d3585d-714c-5fcf-b3b9-b1790af61baa".uuid.get),

    u"83acdb3f-5b1c-5d05-be72-bdbfac2d5af2" ->
      ExpectedValue(u"83acdb3f-5b1c-5d05-be72-bdbfac2d5af2".uuid.get),

    u"84d6d0af-0c32-5f22-a653-592ac3d9bb63" ->
      ExpectedValue(u"84d6d0af-0c32-5f22-a653-592ac3d9bb63".uuid.get),

    u"86bef8f4-f562-5ce3-a796-4911cf148c8f" ->
      ExpectedValue(u"86bef8f4-f562-5ce3-a796-4911cf148c8f".uuid.get),

    u"8898d85f-49a9-5029-aea9-9fbed1467d46" ->
      ExpectedValue(u"8898d85f-49a9-5029-aea9-9fbed1467d46".uuid.get),

    u"90efc796-95c8-59a4-b5e1-971853e50696" ->
      ExpectedValue(u"90efc796-95c8-59a4-b5e1-971853e50696".uuid.get),

    u"94fc8bf8-098c-5d49-a766-b7a71296024a" ->
      ExpectedValue(u"94fc8bf8-098c-5d49-a766-b7a71296024a".uuid.get),

    u"a2842c8d-2681-5840-8e33-6c1daf9acb24" ->
      ExpectedValue(u"a2842c8d-2681-5840-8e33-6c1daf9acb24".uuid.get),

    u"b2cf575f-ec53-50ec-96b4-da94de2d926f" ->
      ExpectedValue(u"b2cf575f-ec53-50ec-96b4-da94de2d926f".uuid.get),

    u"b498336e-5673-57bf-bcf8-f4d50eed583c" ->
      ExpectedValue(u"b498336e-5673-57bf-bcf8-f4d50eed583c".uuid.get),

    u"b7ea5458-c845-50cc-bb4e-dac4b662c456" ->
      ExpectedValue(u"b7ea5458-c845-50cc-bb4e-dac4b662c456".uuid.get),

    u"bbd784e1-298f-5501-ad11-d41ff195621a" ->
      ExpectedValue(u"bbd784e1-298f-5501-ad11-d41ff195621a".uuid.get),

    u"c91b7662-42ea-59ae-8b08-91832939f5e1" ->
      ExpectedValue(u"c91b7662-42ea-59ae-8b08-91832939f5e1".uuid.get),

    u"c96fd1c5-c5cb-50ed-afd1-63bd1368896b" ->
      ExpectedValue(u"c96fd1c5-c5cb-50ed-afd1-63bd1368896b".uuid.get),

    u"d0cf534d-0785-576b-87a8-960e5e6ce374" ->
      ExpectedValue(u"d0cf534d-0785-576b-87a8-960e5e6ce374".uuid.get),

    u"d22a4752-cb0b-5f9b-a705-7580098ad362" ->
      ExpectedValue(u"d22a4752-cb0b-5f9b-a705-7580098ad362".uuid.get),

    u"d2c76b8f-f558-5df9-a96c-e900df95e188" ->
      ExpectedValue(u"d2c76b8f-f558-5df9-a96c-e900df95e188".uuid.get),

    u"d710a5b8-ecd8-5222-a57f-21c1e8aa9166" ->
      ExpectedValue(u"d710a5b8-ecd8-5222-a57f-21c1e8aa9166".uuid.get),

    u"dc6e0eb5-3632-54aa-aa16-bfa8de8b92db" ->
      ExpectedValue(u"dc6e0eb5-3632-54aa-aa16-bfa8de8b92db".uuid.get),

    u"dd4f37b7-911b-5374-9b51-aedcd7a42d97" ->
      ExpectedValue(u"dd4f37b7-911b-5374-9b51-aedcd7a42d97".uuid.get),

    u"e529d978-6a13-578b-b3eb-bd9b8ad50a53" ->
      ExpectedValue(u"e529d978-6a13-578b-b3eb-bd9b8ad50a53".uuid.get),

    u"e852a1e4-3b87-56b5-9c40-94e8fb165b52" ->
      ExpectedValue(u"e852a1e4-3b87-56b5-9c40-94e8fb165b52".uuid.get),

    u"e8a4da14-b793-55f2-8609-44bac11576f5" ->
      ExpectedValue(u"e8a4da14-b793-55f2-8609-44bac11576f5".uuid.get),

    u"ea69f5c2-340b-5ec3-8b1c-5931ba1ce179" ->
      ExpectedValue(u"ea69f5c2-340b-5ec3-8b1c-5931ba1ce179".uuid.get),

    u"edd01cc8-0e7a-5370-8d90-173d24c9341c" ->
      ExpectedValue(u"edd01cc8-0e7a-5370-8d90-173d24c9341c".uuid.get),

    u"f36a6f4d-ddfc-509c-a652-5b50e8372006" ->
      ExpectedValue(u"f36a6f4d-ddfc-509c-a652-5b50e8372006".uuid.get),

    u"231467e3-5822-5ce6-8a93-8bf539957900" ->
      ExpectedValue(u"231467e3-5822-5ce6-8a93-8bf539957900".uuid.get),

    u"4323d4d8-1e8d-5e4a-966a-e3f831d2c3eb" ->
      ExpectedValue(u"4323d4d8-1e8d-5e4a-966a-e3f831d2c3eb".uuid.get),

    u"5a041add-35ee-5380-ae65-980bc834de70" ->
      ExpectedValue(u"5a041add-35ee-5380-ae65-980bc834de70".uuid.get),

    u"7a74b27e-51f5-5b09-b2d6-43264cc812ca" ->
      ExpectedValue(u"7a74b27e-51f5-5b09-b2d6-43264cc812ca".uuid.get),

    u"9df9e622-26ff-51d6-a007-a41279d09cf6" ->
      ExpectedValue(u"9df9e622-26ff-51d6-a007-a41279d09cf6".uuid.get),

    u"b48f2da8-82d8-5cdf-b4be-c83f7438715b" ->
      ExpectedValue(u"b48f2da8-82d8-5cdf-b4be-c83f7438715b".uuid.get),

    u"d31f59fb-a5bb-53aa-bacc-08294378ba27" ->
      ExpectedValue(u"d31f59fb-a5bb-53aa-bacc-08294378ba27".uuid.get)
  )

  describe("NameFilter") {
    describe(".resolveCanonical") {
      it("resolves exact") {
        val request = Request(searchTerm = s"$canonicalModifierStr:Aaadonta constricta")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 6
        result.map { project } should contain only(
          names(u"b2cf575f-ec53-50ec-96b4-da94de2d926f"),
          names(u"e529d978-6a13-578b-b3eb-bd9b8ad50a53")
        )
      }

      it("resolves exact with multiple spaces input") {
        val request = Request(searchTerm = s"$canonicalModifierStr:  \t  Aaadonta   constricta    ")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 6
        result.map { project } should contain only(
          names(u"b2cf575f-ec53-50ec-96b4-da94de2d926f"),
          names(u"e529d978-6a13-578b-b3eb-bd9b8ad50a53")
        )
      }

      it("resolves exact with wildcard inside input") {
        val request = Request(searchTerm = s"$canonicalModifierStr:Aaadonta %constricta")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 0
      }

      it("resolves exact with wildcard as input") {
        val request = Request(searchTerm = s"$canonicalModifierStr:%")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 0
      }

      it("resolves wildcard") {
        val request = Request(searchTerm = s"$canonicalModifierStr:Aaadonta constricta ba*")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 2
        result.map { project } should contain only(
          names(u"5a68f4ec-6121-553e-8843-3d602089ec88"),
          names(u"073bab60-1816-5b5c-b018-87b4193db6f7")
        )
      }

      it("resolves no mathches when string request of length less than 4 is provided") {
        val request = Request(searchTerm = s"$canonicalModifierStr:Aaa")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 0
      }

      it("returns no wildcarded matches when empty string is provided") {
        val request = Request(searchTerm = s"$canonicalModifierStr:")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 0
      }
    }

    describe(".resolveAuthor") {
      it("resolves") {
        val request = Request(searchTerm = s"$authorModifierStr:Abakar-Ousman")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 15
        result.map { project } should contain only(
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
        result.size shouldBe 0
      }
    }

    describe(".resolveYear") {
      it("resolves") {
        val request = Request(searchTerm = s"$yearModifierStr:1752")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 16
        result.map { project } should contain only(
          names(u"64d3585d-714c-5fcf-b3b9-b1790af61baa"),
          names(u"84d6d0af-0c32-5f22-a653-592ac3d9bb63"),
          names(u"04009880-0824-59aa-aa64-66c045d5d00f"),
          names(u"d22a4752-cb0b-5f9b-a705-7580098ad362"),
          names(u"30d121d1-3538-54a9-9554-2c2e75382311"),
          names(u"e8a4da14-b793-55f2-8609-44bac11576f5"),
          names(u"2dfec1f4-0f12-562a-83d3-8aa3ed7ebef8"),
          names(u"bbd784e1-298f-5501-ad11-d41ff195621a"),
          names(u"83acdb3f-5b1c-5d05-be72-bdbfac2d5af2"),
          names(u"86bef8f4-f562-5ce3-a796-4911cf148c8f")
        )
      }

      it("returns no matches on non-existing year") {
        val request = Request(searchTerm = s"$yearModifierStr:3000")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 0
      }

      it("returns no matches when empty string is provided") {
        val request = Request(searchTerm = s"$yearModifierStr:")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 0
      }
    }

    describe(".resolveUninomial") {
      it("resolves") {
        val request = Request(searchTerm = s"$uninomialModifierStr:Aalenirhynchia")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 6
        result.map { project } should contain only(
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
        result.size shouldBe 0
      }
    }

    describe(".resolveGenus") {
      it("resolves") {
        val request = Request(searchTerm = s"$genusModifierStr:Buxela")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 5
        result.map { project } should contain only(
          names(u"94fc8bf8-098c-5d49-a766-b7a71296024a"),
          names(u"c91b7662-42ea-59ae-8b08-91832939f5e1"),
          names(u"d710a5b8-ecd8-5222-a57f-21c1e8aa9166")
        )
      }

      it("resolves with wildcard") {
        val request = Request(searchTerm = s"$genusModifierStr:Buxet*")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 19
        result.map { project } should contain only(
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
        result.size shouldBe 11
        result.map { project } should contain only(
          names(u"2fb5da60-3b02-5605-9411-a6634c4d535a"),
          names(u"b7ea5458-c845-50cc-bb4e-dac4b662c456"),
          names(u"d2c76b8f-f558-5df9-a96c-e900df95e188"),
          names(u"dd4f37b7-911b-5374-9b51-aedcd7a42d97")
        )
      }

      it("resolves binomial") {
        val request = Request(searchTerm = s"$uninomialModifierStr:Aalenirhynchia ab")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 0
      }

      it("returns no matches on non-existing input") {
        val request = Request(searchTerm = s"$genusModifierStr:Aalenirhynchia1")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 0
      }

      it("returns no matches when empty string is provided") {
        val request = Request(searchTerm = s"$genusModifierStr:")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 0
      }
    }

    describe(".resolveSpecies") {
      it("resolves") {
        val request = Request(searchTerm = s"$speciesModifierStr:cynoscion")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 8
        result.map { project } should contain only(
          names(u"173848c3-baa3-5662-ba1c-40b1a363e182"),
          names(u"47712a8d-2dd2-5e0a-b980-f1e47e38d498"),
          names(u"90efc796-95c8-59a4-b5e1-971853e50696")
        )
      }

      it("returns no matches when empty string is provided") {
        val request = Request(searchTerm = s"$speciesModifierStr:")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 0
      }
    }

    describe(".resolveSubspecies") {
      it("resolves") {
        val request = Request(searchTerm = s"$subSpeciesModifierStr:Abacantha")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 4
        result.map { project } should contain only(
          names(u"03e71643-d238-5859-af6f-b98a129ebe12"),
          names(u"b498336e-5673-57bf-bcf8-f4d50eed583c"),
          names(u"d0cf534d-0785-576b-87a8-960e5e6ce374"),
          names(u"dc6e0eb5-3632-54aa-aa16-bfa8de8b92db")
        )
      }

      it("returns no matches when empty string is provided") {
        val request = Request(searchTerm = s"$subSpeciesModifierStr:")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 0
      }
    }

    describe(".resolveNameStrings") {
      it("resolves exact") {
        val request =
          Request(searchTerm = s"$nameStringModifierStr:Aaadonta constricta babelthuapi")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 1
        result.map { project } should contain only
          names(u"5a68f4ec-6121-553e-8843-3d602089ec88")
      }

      it("returns no matches when empty string is provided") {
        val request = Request(searchTerm = s"$nameStringModifierStr:")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 0
      }

      it("resolves exact with non-existing input") {
        val request = Request(searchTerm = s"$nameStringModifierStr:Pararara")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 0
      }

      it("resolves no matches when empty string is provided") {
        val request = Request(searchTerm = s"$nameStringModifierStr:")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 0
      }

      it("resolves wildcard") {
        val request = Request(searchTerm = s"$nameStringModifierStr:Aaadonta constricta komak*")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 2
        result.map { project } should contain only(
          names(u"51b7b1b2-07ba-5a0e-a65d-c5ca402b58de"),
          names(u"edd01cc8-0e7a-5370-8d90-173d24c9341c")
        )
      }

      it("returns no wildcarded matches when empty string is provided") {
        val request = Request(searchTerm = s"$nameStringModifierStr:")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 0
      }

      it("resolves nothing wildcard with non-existing stirng") {
        val request = Request(searchTerm = s"$nameStringModifierStr:Pararara*")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 0
      }

      it("resolves wildcard with wildcard in begin of input") {
        val request = Request(searchTerm = s"$nameStringModifierStr:%Aaadonta constricta ba*")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 2
        result.map { project } should contain only(
          names(u"073bab60-1816-5b5c-b018-87b4193db6f7"),
          names(u"5a68f4ec-6121-553e-8843-3d602089ec88")
        )
      }

      it("resolves wildcard with wildcard in middle of input") {
        val request = Request(searchTerm = s"$nameStringModifierStr:Aaadonta constricta * ba*")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 10
        result.map { project } should contain only(
          names(u"073bab60-1816-5b5c-b018-87b4193db6f7"),
          names(u"51b7b1b2-07ba-5a0e-a65d-c5ca402b58de"),
          names(u"5a68f4ec-6121-553e-8843-3d602089ec88"),
          names(u"b2cf575f-ec53-50ec-96b4-da94de2d926f"),
          names(u"e529d978-6a13-578b-b3eb-bd9b8ad50a53"),
          names(u"edd01cc8-0e7a-5370-8d90-173d24c9341c")
        )
      }

      it("resolves no matches when string request of length less than 4 is provided") {
        val request = Request(searchTerm = s"$nameStringModifierStr:Aaa*")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 0
      }
    }

    describe(".resolveExact") {
      it("resolves") {
        val request = Request(searchTerm = s"$exactStringModifierStr:Aalenirhynchia")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 1
        result.map { project } should contain only
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
          response.names.size shouldBe 2
          response.names.map { _.dataSource.id } should contain only (168, 169)
          response.names.map { _.name.uuid: UUID } should contain only uuid
        }
      }
    }
  }
}
