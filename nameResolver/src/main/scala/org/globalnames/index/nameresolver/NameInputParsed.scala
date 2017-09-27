package org.globalnames
package index
package nameresolver

import org.apache.commons.lang.WordUtils
import thrift.nameresolver.NameInput
import parser.{ScientificNameParser => snp}

class NameInputParsed private (ni: NameInput) {
  require(ni.value.nonEmpty)

  val (firstWordCorrectlyCapitalised: Boolean, valueCapitalised: String) = {
    val parts = ni.value.split("\\s", 2)
    val firstWord = parts(0)
    val rest = parts.drop(1).headOption.map { x =>
      ni.value.charAt(firstWord.length).toString + x
    }.getOrElse("")
    val firstWordCapitalised = WordUtils.capitalize(firstWord.toLowerCase)
    val firstWordIsCapitalised = firstWordCapitalised == firstWord
    val valueCapitalised = firstWordCapitalised + rest
    (firstWordIsCapitalised, valueCapitalised)
  }

  val parsed: snp.Result =
    snp.instance.fromString(valueCapitalised)

  val nameInput: NameInput = ni.copy(suppliedId = ni.suppliedId.map { _.trim })
}

object NameInputParsed {
  def apply(nameInput: NameInput): NameInputParsed =
    new NameInputParsed(nameInput)
}
