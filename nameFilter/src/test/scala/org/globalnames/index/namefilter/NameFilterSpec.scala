package org.globalnames
package index
package namefilter

import util.UuidEnhanced._
import java.util.UUID
import com.google.inject.Stage
import com.twitter.finatra.thrift.EmbeddedThriftServer
import com.twitter.inject.server.FeatureTestMixin
import com.twitter.util.Future
import thrift.Uuid
import thrift.namefilter.{Request, Service => NameFilterService}
import slick.jdbc.PostgresProfile.api._
import dao.{Tables => T}
import org.globalnames.index.namefilter.NameFilter.CanonicalModifier

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Properties
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures

class NameFilterSpec extends org.globalnames.index.namefilter.SpecConfig with FeatureTestMixin {
  import QueryParser._

  override val server = new EmbeddedThriftServer(
    twitterServer = new Server,
    stage = Stage.PRODUCTION,
    flags = Map(
      NameFilterModule.matcherServiceAddress.name -> Properties.envOrElse("MATCHER_ADDRESS", "")
    )
  )

  val nameFilterClient: NameFilterService[Future] =
    server.thriftClient[NameFilterService[Future]](clientId = "nameFilterClient")

  describe("NameFilter") {
    describe(".resolveCanonical") {
      it("resolves exact") {
        val request = Request(searchTerm = s"$canonicalModifierStr:Aaadonta constricta")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 6
        result.map { rs => (rs.result.name.uuid: UUID).toString } should contain only(
          "b2cf575f-ec53-50ec-96b4-da94de2d926f",
          "e529d978-6a13-578b-b3eb-bd9b8ad50a53"
        )
      }

      it("resolves exact with multiple spaces input") {
        val request = Request(searchTerm = s"$canonicalModifierStr:  \t  Aaadonta   constricta    ")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 6
        result.map { rs => (rs.result.name.uuid: UUID).toString } should contain only(
          "b2cf575f-ec53-50ec-96b4-da94de2d926f",
          "e529d978-6a13-578b-b3eb-bd9b8ad50a53"
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
        result.map { rs => (rs.result.name.uuid: UUID).toString } should contain only(
          "5a68f4ec-6121-553e-8843-3d602089ec88",
          "073bab60-1816-5b5c-b018-87b4193db6f7"
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
        result.map { rs => (rs.result.name.uuid: UUID).toString } should contain only(
          "f36a6f4d-ddfc-509c-a652-5b50e8372006",
          "8898d85f-49a9-5029-aea9-9fbed1467d46",
          "ea69f5c2-340b-5ec3-8b1c-5931ba1ce179",
          "a2842c8d-2681-5840-8e33-6c1daf9acb24",
          "4168bf10-7462-53c6-b350-f6052c082965",
          "5272e0e6-ad53-5f1e-826b-8a024b01ce27"
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
        result.map { rs => (rs.result.name.uuid: UUID).toString } should contain only(
          "64d3585d-714c-5fcf-b3b9-b1790af61baa",
          "84d6d0af-0c32-5f22-a653-592ac3d9bb63",
          "04009880-0824-59aa-aa64-66c045d5d00f",
          "d22a4752-cb0b-5f9b-a705-7580098ad362",
          "30d121d1-3538-54a9-9554-2c2e75382311",
          "e8a4da14-b793-55f2-8609-44bac11576f5",
          "2dfec1f4-0f12-562a-83d3-8aa3ed7ebef8",
          "bbd784e1-298f-5501-ad11-d41ff195621a",
          "83acdb3f-5b1c-5d05-be72-bdbfac2d5af2",
          "86bef8f4-f562-5ce3-a796-4911cf148c8f"
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
        result.map { rs => (rs.result.name.uuid: UUID).toString } should contain only(
          "05375e93-f74c-5bf4-8815-1cc363c1b98c",
          "14414d49-b321-5aa3-9da1-32ca0ba45614",
          "278b8361-cdb1-5a0b-8e21-9bba57880121",
          "c96fd1c5-c5cb-50ed-afd1-63bd1368896b",
          "e852a1e4-3b87-56b5-9c40-94e8fb165b52"
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
        result.map { rs => (rs.result.name.uuid: UUID).toString } should contain only(
          "94fc8bf8-098c-5d49-a766-b7a71296024a",
          "c91b7662-42ea-59ae-8b08-91832939f5e1",
          "d710a5b8-ecd8-5222-a57f-21c1e8aa9166"
        )
      }

      it("resolves lowercase") {
        val request = Request(searchTerm = s"$uninomialModifierStr:buxela")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 11
        result.map { rs => (rs.result.name.uuid: UUID).toString } should contain only(
          "2fb5da60-3b02-5605-9411-a6634c4d535a",
          "b7ea5458-c845-50cc-bb4e-dac4b662c456",
          "d2c76b8f-f558-5df9-a96c-e900df95e188",
          "dd4f37b7-911b-5374-9b51-aedcd7a42d97"
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
        result.map { rs => (rs.result.name.uuid: UUID).toString } should contain only(
          "173848c3-baa3-5662-ba1c-40b1a363e182",
          "47712a8d-2dd2-5e0a-b980-f1e47e38d498",
          "90efc796-95c8-59a4-b5e1-971853e50696"
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
        result.map { rs => (rs.result.name.uuid: UUID).toString } should contain only(
          "03e71643-d238-5859-af6f-b98a129ebe12",
          "b498336e-5673-57bf-bcf8-f4d50eed583c",
          "d0cf534d-0785-576b-87a8-960e5e6ce374",
          "dc6e0eb5-3632-54aa-aa16-bfa8de8b92db"
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
        result.map { rs => (rs.result.name.uuid: UUID).toString } should contain only
          "5a68f4ec-6121-553e-8843-3d602089ec88"
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
        result.map { rs => (rs.result.name.uuid: UUID).toString } should contain only(
          "51b7b1b2-07ba-5a0e-a65d-c5ca402b58de",
          "edd01cc8-0e7a-5370-8d90-173d24c9341c"
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
        result.map { rs => (rs.result.name.uuid: UUID).toString } should contain only(
          "073bab60-1816-5b5c-b018-87b4193db6f7",
          "5a68f4ec-6121-553e-8843-3d602089ec88"
        )
      }

      it("resolves wildcard with wildcard in middle of input") {
        val request = Request(searchTerm = s"$nameStringModifierStr:Aaadonta constricta * ba*")
        val result = nameFilterClient.nameString(request = request).value
        result.size shouldBe 10
        result.map { rs => (rs.result.name.uuid: UUID).toString } should contain only(
          "073bab60-1816-5b5c-b018-87b4193db6f7",
          "51b7b1b2-07ba-5a0e-a65d-c5ca402b58de",
          "5a68f4ec-6121-553e-8843-3d602089ec88",
          "b2cf575f-ec53-50ec-96b4-da94de2d926f",
          "e529d978-6a13-578b-b3eb-bd9b8ad50a53",
          "edd01cc8-0e7a-5370-8d90-173d24c9341c"
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
        result.map { rs => (rs.result.name.uuid: UUID).toString } should contain only
          "c96fd1c5-c5cb-50ed-afd1-63bd1368896b"
      }
    }

    /*
    describe(".resolveDataSources") {
      it("resolves") {
        whenReady(faceted.resolveDataSources(
          UUID.fromString("b2cf575f-ec53-50ec-96b4-da94de2d926f"))) { res =>
          res should have size 2
          val res1 = res.map { case (nsi, ds) => (nsi.nameStringId, ds.id) }
          res1 should contain only (
            (UUID.fromString("b2cf575f-ec53-50ec-96b4-da94de2d926f"), 168),
            (UUID.fromString("b2cf575f-ec53-50ec-96b4-da94de2d926f"), 169))
        }
      }
    }

    describe(".findNameStringByUuid") {
      it("resolves") {
        whenReady(faceted.findNameStringByUuid(
          UUID.fromString("073bab60-1816-5b5c-b018-87b4193db6f7"), parameters)) { res =>
          res.matches.map {
            _.nameString
          } should contain only ns073bab6018165b5cb01887b4193db6f7
        }
      }
    }
    */
  }
}
