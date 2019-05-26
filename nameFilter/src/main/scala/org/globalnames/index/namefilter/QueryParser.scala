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
    val parser = new QueryParser(query)
    val partsTry = parser.searchQuery.run()
    partsTry.map { x => SearchQuery(searchPostprocess(x)) }
  }

  private def searchPostprocess(partsAst: Seq[AST.SearchPart]) = {
    val searchParts = partsAst.map { partAst =>
      val modifier = partAst.modifier.value match {
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
        case _ => UnknownModifier(partAst.modifier.value)
      }
      val words = partAst.words.map { w => Word(w.value, w.wildcard) }
      SearchPart(modifier, words)
    }
    val searchPartsFiltered = searchParts.filter { p =>
      val hasWords = p.words.nonEmpty
      val firstWordIsWildcardedAndShort = p.words(0).wildcard && p.words(0).value.length < 4
      hasWords && !firstWordIsWildcardedAndShort
    }
    searchPartsFiltered
  }

  object AST {
    final case class Modifier(value: String)
    final case class Word(value: String, wildcard: Boolean)
    final case class SearchPart(modifier: Modifier, words: Seq[Word])
    case object Wildcard
  }

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

  final case class Word(value: String, wildcard: Boolean)
  final case class SearchPart(modifier: Modifier, words: Seq[Word])
  final case class SearchQuery(parts: Seq[SearchPart])
}

@SuppressWarnings(Array("org.wartremover.warts.Null",
                        "org.wartremover.warts.NonUnitStatements",
                        "org.wartremover.warts.Var"))
class QueryParser(val input: ParserInput) extends Parser {
  import QueryParser._

  def searchQuery: Rule1[Seq[AST.SearchPart]] = rule {
    searchParts ~ EOI
  }

  private def searchParts = rule {
    zeroOrMore(searchPart)
  }

  private def searchPart = rule {
    modifier ~> {(m: AST.Modifier) => AST.SearchPart(m, Seq()) } ~ spacing.? ~
      zeroOrMore(word ~ spacing.? ~> {
        (sp: AST.SearchPart, word: AST.Word) => sp.copy(words = sp.words :+ word)
      })
  }

  private def wildcardChar = rule {
    wildcardCh ~ push(AST.Wildcard)
  }

  def word = rule {
    !modifier ~ capture(oneOrMore(anyVisibleChar)) ~ wildcardChar.? ~> {
      (w: String, wildcard: Option[AST.Wildcard.type]) => AST.Word(w, wildcard.isDefined)
    }
  }

  private def modifier = rule {
    capture(oneOrMore(CharPredicate.LowerAlpha)) ~ modifierDelimiter ~> AST.Modifier
  }

  def spacing: Rule0 = rule { oneOrMore(space) }

  private val space = " "
  private val modifierDelimiter = ":"
  private val wildcardCh = '*'

  private final val sciCharsExtended = "æœſàâåãäáçčéèíìïňññóòôøõöúùüŕřŗššşž"
  private final val sciUpperCharExtended = "ÆŒÖ"
  private final val charMiscoded = '�'
  private final val upperChar =
    CharPredicate.UpperAlpha ++ "Ë" ++ sciUpperCharExtended ++ charMiscoded
  private final val lowerChar =
    CharPredicate.LowerAlpha ++ "ë" ++ sciCharsExtended ++ charMiscoded
  private final val anyVisibleChar = upperChar ++ lowerChar ++ CharPredicate.Visible -- wildcardCh

}
