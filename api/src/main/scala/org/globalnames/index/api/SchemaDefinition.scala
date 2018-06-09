package org.globalnames
package index
package api

import sangria.schema._
import sangria.marshalling.{CoercedScalaResultMarshaller, FromInput}
import thrift.{namefilter => nf, nameresolver => nr, namebrowser => nb, MatchKind => MK}
import util.UuidEnhanced.ThriftUuidEnhanced

object Common {
  private def stemEditDistance(mk: MK) = mk match {
    case MK.CanonicalMatch(cm) => Some(cm.stemEditDistance)
    case _ => None
  }

  private def verbatimEditDistance(mk: MK) = mk match {
    case MK.CanonicalMatch(cm) => Some(cm.verbatimEditDistance)
    case _ => None
  }

  private object KindNames {
    val UuidLookup = "UuidLookup"
    val ExactMatch = "ExactMatch"
    val ExactCanonicalMatch = "ExactCanonicalMatch"
    val FuzzyCanonicalMatch = "FuzzyCanonicalMatch"
    val ExactPartialMatch = "ExactPartialMatch"
    val FuzzyPartialMatch = "FuzzyPartialMatch"
    val ExactAbbreviatedMatch = "ExactAbbreviatedMatch"
    val FuzzyAbbreviatedMatch = "FuzzyAbbreviatedMatch"
    val ExactPartialAbbreviatedMatch = "ExactPartialAbbreviatedMatch"
    val FuzzyPartialAbbreviatedMatch = "FuzzyPartialAbbreviatedMatch"
    val Unknown = "Unknown"
  }

  private def kindName(matchKind: MK): String = matchKind match {
    case _: MK.UuidLookup => KindNames.UuidLookup
    case _: MK.ExactMatch => KindNames.ExactMatch
    case MK.CanonicalMatch(cm) =>
      (cm.partial, cm.byAbbreviation) match {
        case (false, false) =>
          if (cm.stemEditDistance == 0 && cm.verbatimEditDistance == 0) {
            KindNames.ExactCanonicalMatch
          } else {
            KindNames.FuzzyCanonicalMatch
          }
        case (true, false) =>
          if (cm.stemEditDistance == 0 && cm.verbatimEditDistance == 0) {
            KindNames.ExactPartialMatch
          } else {
            KindNames.FuzzyPartialMatch
          }
        case (false, true) =>
          if (cm.stemEditDistance == 0 && cm.verbatimEditDistance == 0) {
            KindNames.ExactAbbreviatedMatch
          } else {
            KindNames.FuzzyAbbreviatedMatch
          }
        case (true, true) =>
          if (cm.stemEditDistance == 0 && cm.verbatimEditDistance == 0) {
            KindNames.ExactPartialAbbreviatedMatch
          } else {
            KindNames.FuzzyPartialAbbreviatedMatch
          }
      }
    case _: MK.Unknown => KindNames.Unknown
  }

  val MatchTypeOT = ObjectType(
    "MatchType", fields[Unit, thrift.MatchType](
        Field("kind", StringType, resolve = x => kindName(x.value.kind))
      , Field("score", IntType, resolve = _.value.score)
      , Field("verbatimEditDistance", OptionType(IntType),
              resolve = ctx => verbatimEditDistance(ctx.value.kind))
      , Field("stemEditDistance", OptionType(IntType),
              resolve = ctx => stemEditDistance(ctx.value.kind))
    )
  )

  val NameOT = ObjectType(
    "Name", fields[Unit, thrift.Name](
        Field("id", IDType, resolve = _.value.uuid.string)
      , Field("value", StringType, resolve = _.value.value)
    )
  )

  val CanonicalNameOT = ObjectType(
    "Name", fields[Unit, thrift.CanonicalName](
        Field("id", IDType, resolve = _.value.uuid.string)
      , Field("value", StringType, resolve = _.value.value)
      , Field("valueRanked", StringType, resolve = _.value.valueRanked)
    )
  )

  val AuthorScoreOT = ObjectType(
    "AuthorScore", fields[Unit, thrift.AuthorScore](
        Field("authorshipInput", StringType, resolve = _.value.authorshipInput)
      , Field("authorshipMatch", StringType, resolve = _.value.authorshipMatch)
      , Field("value", FloatType, resolve = _.value.value)
    )
  )

  val ScoreOT = ObjectType(
    "Score", fields[Unit, thrift.Score](
        Field("nameType", OptionType(IntType), resolve = _.value.nameType)
      , Field("authorScore", AuthorScoreOT, resolve = _.value.authorScore)
      , Field("parsingQuality", IntType, resolve = _.value.parsingQuality)
      , Field("value", OptionType(FloatType), resolve = _.value.value)
      , Field("message", OptionType(StringType), resolve = _.value.message)
    )
  )

  val ClassificationOT = ObjectType(
    "Classification", fields[Unit, thrift.Classification](
        Field("path", OptionType(StringType), resolve = _.value.path)
      , Field("pathIds", OptionType(StringType), resolve = _.value.pathIds)
      , Field("pathRanks", OptionType(StringType), resolve = _.value.pathRanks)
    )
  )

  val DataSourceOT = ObjectType(
    "DataSource", fields[Unit, thrift.DataSource](
        Field("id", IntType, resolve = _.value.id)
      , Field("title", StringType, resolve = _.value.title)
      , Field("description", OptionType(StringType), resolve = _.value.description)
      , Field("logoUrl", OptionType(StringType), resolve = _.value.logoUrl)
      , Field("webSiteUrl", OptionType(StringType), resolve = _.value.webSiteUrl)
      , Field("dataUrl", OptionType(StringType), resolve = _.value.dataUrl)
      , Field("refreshPeriodDays", OptionType(IntType), resolve = _.value.refreshPeriodDays)
      , Field("uniqueNamesCount", OptionType(IntType), resolve = _.value.uniqueNamesCount)
      , Field("createdAt", OptionType(StringType), resolve = _.value.createdAt)
      , Field("updatedAt", OptionType(StringType), resolve = _.value.updatedAt)
      , Field("dataHash", OptionType(StringType), resolve = _.value.dataHash)
      , Field("quality", StringType, resolve = _.value.quality.toString)
    )
  )

  val AcceptedNameOT = ObjectType(
    "AcceptedName", fields[Unit, thrift.AcceptedName](
        Field("name", NameOT, resolve = _.value.name)
      , Field("canonicalName", OptionType(CanonicalNameOT), resolve = _.value.canonicalName)
      , Field("taxonId", StringType, resolve = _.value.taxonId)
      , Field("dataSourceId", IntType, resolve = _.value.dataSourceId)
    )
  )

  val ContextOT = ObjectType(
    "Context", fields[Unit, thrift.Context](
        Field("dataSource", DataSourceOT, resolve = _.value.dataSource)
      , Field("clade", StringType, resolve = _.value.clade)
    )
  )

  val ResultItemOT = ObjectType(
    "ResultItem", fields[Unit, thrift.Result](
        Field("name", NameOT, resolve = _.value.name)
      , Field("canonicalName", OptionType(CanonicalNameOT), resolve = _.value.canonicalName)
      , Field("synonym", BooleanType, resolve = _.value.synonym)
      , Field("taxonId", StringType, resolve = _.value.taxonId)
      , Field("localId", OptionType(StringType), resolve = _.value.localId)
      , Field("url", OptionType(StringType), resolve = _.value.url)
      , Field("classification", ClassificationOT, resolve = _.value.classification)
      , Field("matchType", MatchTypeOT, resolve = _.value.matchType)
      , Field("dataSource", DataSourceOT, resolve = _.value.dataSource)
      , Field("acceptedName", OptionType(AcceptedNameOT), resolve = _.value.acceptedName)
    )
  )

  val NameStringOT = ObjectType(
    "NameString", fields[Unit, thrift.Result](
        Field("name", NameOT, resolve = _.value.name)
      , Field("canonicalName", OptionType(CanonicalNameOT), resolve = _.value.canonicalName)
      , Field("synonym", BooleanType, resolve = _.value.synonym)
      , Field("taxonId", StringType, resolve = _.value.taxonId)
      , Field("classification", ClassificationOT, resolve = _.value.classification)
      , Field("matchType", MatchTypeOT, resolve = _.value.matchType)
      , Field("dataSource", DataSourceOT, resolve = _.value.dataSource)
      , Field("acceptedName", OptionType(AcceptedNameOT), resolve = _.value.acceptedName)
      , Field("updatedAt", OptionType(StringType), resolve = _.value.updatedAt)
    )
  )
}

object NameFilter {
  import Common._

  val ResultsPerDataSourceOT = ObjectType(
    "ResultsPerDataSourceItem", fields[Unit, nf.ResultPerDataSource](
        Field("dataSource", DataSourceOT, resolve = _.value.dataSource)
      , Field("results", ListType(ResultItemOT), resolve = _.value.results)
    )
  )

  val ResultNameStringsOT = ObjectType(
    "ResultNameStrings", fields[Unit, nf.ResultNameStrings](
        Field("name", NameOT, resolve = _.value.name)
      , Field("canonicalName", OptionType(CanonicalNameOT), resolve = _.value.canonicalName)
      , Field("resultsPerDataSource", ListType(ResultsPerDataSourceOT),
              resolve = _.value.resultsPerDataSource)
    )
  )

  val ResponseNameStringsOT = ObjectType(
    "ResponseNameStrings", fields[Unit, nf.ResponseNameStrings](
        Field("page", IntType, resolve = _.value.page)
      , Field("perPage", IntType, resolve = _.value.perPage)
      , Field("pagesCount", IntType, resolve = _.value.pagesCount)
      , Field("resultsCount", IntType, resolve = _.value.resultsCount)
      , Field("names", ListType(ResultNameStringsOT), resolve = _.value.resultNameStrings)
    )
  )

  val NameResponseOT = ObjectType(
    "NameResponse", fields[Unit, nf.Response](
        Field("inputId", IDType, resolve = _.value.uuid.string)
      , Field("names", ListType(NameStringOT), resolve = _.value.results)
    )
  )
}

object NameResolver {
  import Common._

  val ResultItemScoredOT = ObjectType(
    "ResultItem", fields[Unit, nr.ResultScored](
        Field("name", NameOT, resolve = _.value.result.name)
      , Field("canonicalName", OptionType(CanonicalNameOT), resolve = _.value.result.canonicalName)
      , Field("synonym", BooleanType, resolve = _.value.result.synonym)
      , Field("taxonId", StringType, resolve = _.value.result.taxonId)
      , Field("localId", OptionType(StringType), resolve = _.value.result.localId)
      , Field("url", OptionType(StringType), resolve = _.value.result.url)
      , Field("classification", ClassificationOT, resolve = _.value.result.classification)
      , Field("matchType", MatchTypeOT, resolve = _.value.result.matchType)
      , Field("score", ScoreOT, resolve = _.value.score)
      , Field("dataSource", DataSourceOT, resolve = _.value.result.dataSource)
      , Field("acceptedName", OptionType(AcceptedNameOT), resolve = _.value.result.acceptedName)
      , Field("updatedAt", OptionType(StringType), resolve = _.value.result.updatedAt)
    )
  )

  val ResultsPerDataSourceOT = ObjectType(
    "ResultsPerDataSourceItem", fields[Unit, nr.ResultScoredPerDataSource](
        Field("dataSource", DataSourceOT, resolve = _.value.dataSource)
      , Field("results", ListType(ResultItemScoredOT),
              resolve = _.value.resultsScored)
    )
  )

  val ResultScoredNameStringOT = ObjectType(
    "ResultScoredNameString", fields[Unit, nr.ResultScoredNameString](
        Field("name", NameOT, resolve = _.value.name)
      , Field("canonicalName", OptionType(CanonicalNameOT), resolve = _.value.canonicalName)
      , Field("resultsPerDataSource", ListType(ResultsPerDataSourceOT),
              resolve = _.value.resultsScoredPerDataSource)
    )
  )

  val ResponseOT = ObjectType(
    "Response", fields[Unit, nr.Response](
        Field("total", IntType, None, resolve = _.value.total)
      , Field("suppliedInput", OptionType(StringType), None, resolve = _.value.suppliedInput)
      , Field("suppliedId", OptionType(StringType), None, resolve = _.value.suppliedId)
      , Field("results", ListType(ResultScoredNameStringOT), None,
              resolve = _.value.resultScoredNameStrings)
      , Field("preferredResults", ListType(ResultItemScoredOT),
              resolve = _.value.preferredResultsScored)
    )
  )

  val ResponsesOT = ObjectType(
    "Responses", fields[Unit, nr.Responses](
        Field("responses", ListType(ResponseOT), resolve = _.value.responses)
      , Field("context", ListType(ContextOT), resolve = _.value.contexts)
    )
  )
}

object NameBrowser {
  val TripletOT = ObjectType(
    "Triplet", fields[Unit, nb.Triplet](
        Field("value", StringType, resolve = _.value.value)
      , Field("active", BooleanType, resolve = _.value.active)
    )
  )
}

object SchemaDefinition {
  private val nameStringsMaxCount = 50

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf",
                          "org.wartremover.warts.Throw"))
  private implicit val nameInputFromInput: FromInput[nr.NameInput] = new FromInput[nr.NameInput] {
    val marshaller: CoercedScalaResultMarshaller = CoercedScalaResultMarshaller.default

    def fromResult(node: marshaller.Node): nr.NameInput = node match {
      case nodeMap: Map[String, Any] @unchecked =>
        nr.NameInput(
          value = nodeMap("value").asInstanceOf[String],
          suppliedId = nodeMap.get("suppliedId").flatMap { _.asInstanceOf[Option[String]] }
        )
      case _ =>
        throw sangria.schema.SchemaMaterializationException(s"$node has inappropriate type")
    }
  }

  val DataSourceIdsArg = Argument("dataSourceIds", OptionInputType(ListInputType(IntType)))
  val PreferredDataSourceIdsArg =
    Argument("preferredDataSourceIds", OptionInputType(ListInputType(IntType)))
  val AdvancedResolutionArg = Argument("advancedResolution", OptionInputType(BooleanType), false)
  val BestMatchOnlyArg = Argument("bestMatchOnly", OptionInputType(BooleanType), false)
  val NameRequestIOT = InputObjectType[nr.NameInput]("name", List(
      InputField("value", StringType)
    , InputField("suppliedId", OptionInputType(StringType))
  ))
  val NamesRequestArg = Argument("names", ListInputType(NameRequestIOT))
  val SearchTermArg = Argument("searchTerm", StringType)
  val PageArg = Argument("page", OptionInputType(IntType), 0)
  val PerPageArg = Argument("perPage", OptionInputType(IntType), nameStringsMaxCount)
  val LetterArg = Argument("letter", StringType)

  val nameUuidsArg = Argument("uuids", ListInputType(IDType))

  val QueryTypeOT = ObjectType(
    "Query", fields[Repository, Unit](
      Field("nameResolver", NameResolver.ResponsesOT,
        arguments = List(NamesRequestArg, DataSourceIdsArg, PreferredDataSourceIdsArg,
                         AdvancedResolutionArg, BestMatchOnlyArg, PageArg, PerPageArg),
        resolve = ctx =>
          ctx.withArgs(NamesRequestArg, DataSourceIdsArg, PreferredDataSourceIdsArg,
                       AdvancedResolutionArg, BestMatchOnlyArg, PageArg, PerPageArg)
                      (ctx.ctx.nameResolver)
      ),
      Field("nameStrings", NameFilter.ResponseNameStringsOT,
        arguments = List(SearchTermArg, PageArg, PerPageArg, DataSourceIdsArg),
        resolve = ctx =>
          ctx.withArgs(SearchTermArg, PageArg, PerPageArg, DataSourceIdsArg)
                      (ctx.ctx.nameStrings)
      ),
      Field("nameStringsByUuid", ListType(NameFilter.NameResponseOT),
        arguments = List(nameUuidsArg),
        resolve = ctx => ctx.withArgs(nameUuidsArg)(ctx.ctx.nameStringsByUuids)
      ),
      Field("dataSourceById", ListType(Common.DataSourceOT),
        arguments = List(DataSourceIdsArg),
        resolve = ctx => ctx.withArgs(DataSourceIdsArg)(ctx.ctx.dataSourceById)
      ),
      Field("nameBrowser_triplets", ListType(NameBrowser.TripletOT),
        arguments = List(LetterArg),
        resolve = ctx => ctx.withArgs(LetterArg)(ctx.ctx.tripletsStartingWith)
      )
    )
  )

  val schema = Schema(QueryTypeOT)
}
