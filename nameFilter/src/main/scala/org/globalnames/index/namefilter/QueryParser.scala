package org.globalnames
package index
package namefilter

import scala.util.parsing.combinator.RegexParsers
import NameFilter._

object QueryParser extends RegexParsers {
  case class SearchPart(modifier: Modifier, contents: String, wildcard: Boolean)

  private[namefilter] val canonicalModifierStr = "can"
  private[namefilter] val authorModifierStr = "au"
  private[namefilter] val yearModifierStr = "yr"
  private[namefilter] val uninomialModifierStr = "uni"
  private[namefilter] val genusModifierStr = "gen"
  private[namefilter] val speciesModifierStr = "sp"
  private[namefilter] val subSpeciesModifierStr = "ssp"
  private[namefilter] val nameStringModifierStr = "ns"
  private[namefilter] val exactStringModifierStr = "exact"

  private val exactModifier = exactStringModifierStr ^^ { _ => ExactModifier }
  private val nameStringModifier = nameStringModifierStr ^^ { _ => NameStringModifier }
  private val canonicalModifier = canonicalModifierStr ^^ { _ => CanonicalModifier }
  private val uninomialModifier = uninomialModifierStr ^^ { _ => UninomialModifier }
  private val genusModifier = genusModifierStr ^^ { _ => GenusModifier }
  private val speciesModifier = speciesModifierStr ^^ { _ => SpeciesModifier }
  private val subspeciesModifier = subSpeciesModifierStr ^^ { _ => SubspeciesModifier }
  private val authorModifier = authorModifierStr ^^ { _ => AuthorModifier }
  private val yearModifier = yearModifierStr ^^ { _ => YearModifier }

  private def word = s"""[^$wildcard]*""".r

  private def modifier: Parser[Modifier] =
    exactModifier | nameStringModifier | canonicalModifier |
      uninomialModifier | genusModifier | speciesModifier |
      subspeciesModifier | authorModifier | yearModifier

  private def wildcard: String = "*"

  private def searchPartModifier: Parser[SearchPart] =
    modifier ~ ":" ~ word ~ opt(wildcard) ^^ { case mod ~ _ ~ word ~ wildcard =>
      SearchPart(mod, word, wildcard.isDefined)
    }

  private def searchPartNoModifier: Parser[SearchPart] =
    word ~ opt(wildcard) ^^ { case contents ~ wildcard =>
      SearchPart(NoModifier, contents, wildcard.isDefined)
    }

  private def searchPart = searchPartModifier | searchPartNoModifier

  def result(text: String): SearchPart = parse(searchPart, text) match {
    case Success(sp, _) => sp
    case Failure(msg, _) => throw new Exception("Unknown modifier: " + msg)
    case Error(msg, _) => throw new Exception("Unknown modifier: " + msg)
  }
}
