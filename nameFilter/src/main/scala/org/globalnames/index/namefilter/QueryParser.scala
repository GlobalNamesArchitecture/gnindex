package org.globalnames
package index
package namefilter

import scala.annotation.tailrec
import scala.util.{ Try, Success, Failure }
import scala.io.StdIn
import org.parboiled2._

object QueryParser {
  private[namefilter] val canonicalModifierStr = "can"
  private[namefilter] val authorModifierStr = "au"
  private[namefilter] val yearModifierStr = "yr"
  private[namefilter] val uninomialModifierStr = "uni"
  private[namefilter] val genusModifierStr = "gen"
  private[namefilter] val speciesModifierStr = "sp"
  private[namefilter] val subSpeciesModifierStr = "ssp"
  private[namefilter] val nameStringModifierStr = "ns"
  private[namefilter] val exactStringModifierStr = "exact"
  private[namefilter] val wordModifierStr = "w"

  def parse(query: String): Try[SearchQuery] = {
    new QueryParser(query).searchQuery.run().map { x => SearchQuery(searchPostprocess(x)) }
  }

  private def searchPostprocess(partsAst: Seq[SearchPartAST]) = {
    val searchParts = partsAst.map { partAst =>
      val modifier = partAst.modifier.v match {
        case `canonicalModifierStr` => CanonicalModifier
        case `authorModifierStr` => AuthorModifier
        case `yearModifierStr` => YearModifier
        case `uninomialModifierStr` => UninomialModifier
        case `genusModifierStr` => GenusModifier
        case `speciesModifierStr` => SpeciesModifier
        case `subSpeciesModifierStr` => SubspeciesModifier
        case `nameStringModifierStr` => NameStringModifier
        case `exactStringModifierStr` => ExactModifier
        case `wordModifierStr` => WordModifier
        case _ => UnknownModifier(partAst.modifier.v)
      }
      SearchPart(modifier, partAst.words)
    }
    searchParts
  }

  final case class ModifierAST(v: String)
  final case class SearchPartAST(modifier: ModifierAST, words: Seq[String])

  sealed trait Modifier
  case object ExactModifier extends Modifier
  case object NameStringModifier extends Modifier
  case object CanonicalModifier extends Modifier
  case object UninomialModifier extends Modifier
  case object GenusModifier extends Modifier
  case object SpeciesModifier extends Modifier
  case object SubspeciesModifier extends Modifier
  case object AuthorModifier extends Modifier
  case object YearModifier extends Modifier
  case object WordModifier extends Modifier
  final case class UnknownModifier(v: String) extends Modifier

  final case class SearchPart(modifier: Modifier, words: Seq[String])
  final case class SearchQuery(parts: Seq[SearchPart])
}

@SuppressWarnings(Array("org.wartremover.warts.Null",
                        "org.wartremover.warts.NonUnitStatements",
                        "org.wartremover.warts.Var"))
class QueryParser(val input: ParserInput) extends Parser {
  import QueryParser._

  def searchQuery: Rule1[Seq[SearchPartAST]] = rule {
    searchParts ~ EOI
  }

  private def searchParts = rule { zeroOrMore(searchPart) }

  private def searchPart = rule {
    modifier ~> {(m: ModifierAST) => SearchPartAST(m, Seq()) } ~ spacing.? ~
      zeroOrMore(word ~ spacing.? ~>
        { (sp: SearchPartAST, word: String) => sp.copy(words = sp.words :+ word) }
      )
  }

  private def word = rule {
    !modifier ~ capture(oneOrMore(CharPredicate.Visible))
  }

  private def modifier = rule {
    capture(oneOrMore(CharPredicate.LowerAlpha)) ~ modifierDelimiter ~> ModifierAST
  }

  private def spacing: Rule0 = rule { oneOrMore(space) }

  private val space = " "
  private val modifierDelimiter = ":"

}
