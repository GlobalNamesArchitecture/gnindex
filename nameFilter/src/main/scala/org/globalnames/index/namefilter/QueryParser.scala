package org.globalnames
package index
package namefilter

import scala.util.parsing.combinator.RegexParsers
import NameFilter._

object QueryParser extends RegexParsers {
  case class SearchPart(modifier: Modifier, contents: String, wildcard: Boolean)

  private val exactModifier = "exact" ^^ { _ => ExactModifier }
  private val nameStringModifier = "ns" ^^ { _ => NameStringModifier }
  private val canonicalModifier = "can" ^^ { _ => CanonicalModifier }
  private val uninomialModifier = "uni" ^^ { _ => UninomialModifier }
  private val genusModifier = "gen" ^^ { _ => GenusModifier }
  private val speciesModifier = "sp" ^^ { _ => SpeciesModifier }
  private val subspeciesModifier = "ssp" ^^ { _ => SubspeciesModifier }
  private val authorModifier = "au" ^^ { _ => AuthorModifier }
  private val yearModifier = "yr" ^^ { _ => YearModifier }

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
