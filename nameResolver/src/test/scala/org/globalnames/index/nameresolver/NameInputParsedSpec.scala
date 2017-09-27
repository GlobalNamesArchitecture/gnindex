package org.globalnames
package index
package nameresolver

import thrift.nameresolver.NameInput

class NameInputParsedSpec extends FunSpecConfig {
  private def subject(name: String): NameInputParsed = NameInputParsed(NameInput(name))

  describe("NameInputParsed") {
    it("doesn't parse correct name") {
      val s = subject("Homo sapiens")
      s.firstWordCorrectlyCapitalised shouldBe true
      s.valueCapitalised shouldBe "Homo sapiens"
    }

    describe("single-word name parsing") {
      it("doesn't parse correct name") {
        val s = subject("Homo")
        s.firstWordCorrectlyCapitalised shouldBe true
        s.valueCapitalised shouldBe "Homo"
      }

      it("cleans incorrect input") {
        val s = subject("HomO")
        s.firstWordCorrectlyCapitalised shouldBe false
        s.nameInput.value shouldBe "HomO"
        s.valueCapitalised shouldBe "Homo"
      }
    }

    it("cleans uppercased name") {
      val s = subject("HOMO sapiens")
      s.firstWordCorrectlyCapitalised shouldBe false
      s.valueCapitalised shouldBe "Homo sapiens"
    }

    it("cleans lowercased name") {
      val s = subject("homo sapiens")
      s.firstWordCorrectlyCapitalised shouldBe false
      s.valueCapitalised shouldBe "Homo sapiens"
    }

    it("doesn't touch second word") {
      val s = subject("Homo SapIens")
      s.firstWordCorrectlyCapitalised shouldBe true
      s.valueCapitalised shouldBe "Homo SapIens"
    }

    describe("non-standard spaces") {
      it("handles multiple spaces") {
        val s = subject("HoMo  sapiens")
        s.firstWordCorrectlyCapitalised shouldBe false
        s.valueCapitalised shouldBe "Homo  sapiens"
      }

      it("handles tabs") {
        val s = subject("HoMo\tsapiens")
        s.firstWordCorrectlyCapitalised shouldBe false
        s.valueCapitalised shouldBe "Homo\tsapiens"
      }

      it("handles newlines") {
        val s = subject("HoMo\nsapiens")
        s.firstWordCorrectlyCapitalised shouldBe false
        s.valueCapitalised shouldBe "Homo\nsapiens"
      }

      it("handles vertical tabs") {
        val s = subject("HoMo\u000bsapiens")
        s.firstWordCorrectlyCapitalised shouldBe false
        s.valueCapitalised shouldBe "Homo\u000bsapiens"
      }
    }

    it("preserves nameInput value") {
      val s = subject("HomO sapiens")
      s.valueCapitalised shouldBe "Homo sapiens"
      s.nameInput.value shouldBe "HomO sapiens"
    }
  }
}
