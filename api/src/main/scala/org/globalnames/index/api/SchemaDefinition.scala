package org.globalnames
package index
package api

import sangria.schema._
import sangria.marshalling.{CoercedScalaResultMarshaller, FromInput}
import thrift.{Context => ResponsesContext, _}
import thrift.{namefilter => nf, nameresolver => nr}
import util.UuidEnhanced.ThriftUuidEnhanced

object SchemaDefinition {

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

  val MatchTypeOT = ObjectType(
    "MatchType", fields[Unit, MatchType](
        Field("kind", StringType, resolve = _.value.kind.name)
      , Field("score", IntType, resolve = _.value.score)
      , Field("editDistance", IntType, resolve = _.value.editDistance)
    )
  )

  val NameOT = ObjectType(
    "Name", fields[Unit, Name](
        Field("id", IDType, resolve = _.value.uuid.string)
      , Field("value", StringType, resolve = _.value.value)
    )
  )

  val CanonicalNameOT = ObjectType(
    "Name", fields[Unit, CanonicalName](
        Field("id", IDType, resolve = _.value.uuid.string)
      , Field("value", StringType, resolve = _.value.value)
      , Field("valueRanked", StringType, resolve = _.value.valueRanked)
    )
  )

  val AuthorScoreOT = ObjectType(
    "AuthorScore", fields[Unit, AuthorScore](
        Field("authorshipInput", StringType, resolve = _.value.authorshipInput)
      , Field("authorshipMatch", StringType, resolve = _.value.authorshipMatch)
      , Field("value", FloatType, resolve = _.value.value)
    )
  )

  val ScoreOT = ObjectType(
    "Score", fields[Unit, Score](
        Field("nameType", OptionType(IntType), resolve = _.value.nameType)
      , Field("authorScore", AuthorScoreOT, resolve = _.value.authorScore)
      , Field("parsingQuality", IntType, resolve = _.value.parsingQuality)
      , Field("value", OptionType(FloatType), resolve = _.value.value)
      , Field("message", OptionType(StringType), resolve = _.value.message)
    )
  )

  val ClassificationOT = ObjectType(
    "Classification", fields[Unit, Classification](
        Field("path", OptionType(StringType), resolve = _.value.path)
      , Field("pathIds", OptionType(StringType), resolve = _.value.pathIds)
      , Field("pathRanks", OptionType(StringType), resolve = _.value.pathRanks)
    )
  )

  val DataSourceOT = ObjectType(
    "DataSource", fields[Unit, DataSource](
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
    "AcceptedName", fields[Unit, AcceptedName](
        Field("name", NameOT, resolve = _.value.name)
      , Field("canonicalName", OptionType(CanonicalNameOT), resolve = _.value.canonicalName)
      , Field("taxonId", StringType, resolve = _.value.taxonId)
      , Field("dataSourceId", IntType, resolve = _.value.dataSourceId)
    )
  )

  val ContextOT = ObjectType(
    "Context", fields[Unit, ResponsesContext](
        Field("dataSource", DataSourceOT, resolve = _.value.dataSource)
      , Field("clade", StringType, resolve = _.value.clade)
    )
  )

  val ResultItemOT = ObjectType(
    "ResultItem", fields[Unit, ResultScored](
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

  val ResponseOT = ObjectType(
    "Response", fields[Unit, nr.Response](
        Field("total", IntType, None, resolve = _.value.total)
      , Field("suppliedInput", OptionType(StringType), None, resolve = _.value.suppliedInput)
      , Field("suppliedId", OptionType(StringType), None, resolve = _.value.suppliedId)
      , Field("results", ListType(ResultItemOT), None, resolve = _.value.results)
      , Field("preferredResults", ListType(ResultItemOT), resolve = _.value.preferredResults)
    )
  )

  val ResponsesOT = ObjectType(
    "Responses", fields[Unit, nr.Responses](
        Field("responses", ListType(ResponseOT), resolve = _.value.items)
      , Field("context", ListType(ContextOT), resolve = _.value.context)
    )
  )

  val NameStringOT = ObjectType(
    "NameString", fields[Unit, Result](
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

  val ResponseNameStringsOT = ObjectType(
    "ResponseNameStrings", fields[Unit, nf.ResponseNameStrings](
        Field("name", NameOT, resolve = _.value.name)
      , Field("canonicalName", OptionType(CanonicalNameOT), resolve = _.value.canonicalName)
      , Field("synonym", BooleanType, resolve = _.value.synonym)
      , Field("taxonId", StringType, resolve = _.value.taxonId)
      , Field("classification", ClassificationOT, resolve = _.value.classification)
      , Field("matchType", MatchTypeOT, resolve = _.value.matchType)
      , Field("dataSources", ListType(DataSourceOT), resolve = _.value.dataSources)
      , Field("acceptedName", OptionType(AcceptedNameOT), resolve = _.value.acceptedName)
      , Field("updatedAt", OptionType(StringType), resolve = _.value.updatedAt)
    )
  )

  val NameResponseOT = ObjectType(
    "NameResponse", fields[Unit, namefilter.Response](
        Field("inputId", IDType, resolve = _.value.uuid.string)
      , Field("names", ListType(NameStringOT), resolve = _.value.names)
    )
  )

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

  val nameUuidsArg = Argument("uuids", ListInputType(IDType))

  val QueryTypeOT = ObjectType(
    "Query", fields[Repository, Unit](
      Field("nameResolver", ResponsesOT,
        arguments = List(NamesRequestArg, DataSourceIdsArg, PreferredDataSourceIdsArg,
                         AdvancedResolutionArg, BestMatchOnlyArg),
        resolve = ctx =>
          ctx.withArgs(NamesRequestArg, DataSourceIdsArg, PreferredDataSourceIdsArg,
                       AdvancedResolutionArg, BestMatchOnlyArg)
                      (ctx.ctx.nameResolver)
      ),
      Field("nameStrings", ListType(ResponseNameStringsOT),
        arguments = List(SearchTermArg),
        resolve = ctx => ctx.withArgs(SearchTermArg)(ctx.ctx.nameStrings)
      ),
      Field("nameStringsByUuid", ListType(NameResponseOT),
        arguments = List(nameUuidsArg),
        resolve = ctx => ctx.withArgs(nameUuidsArg)(ctx.ctx.nameStringsByUuids)
      ),
      Field("dataSourceById", ListType(DataSourceOT),
        arguments = List(DataSourceIdsArg),
        resolve = ctx => ctx.withArgs(DataSourceIdsArg)(ctx.ctx.dataSourceById)
      )
    )
  )

  val schema = Schema(QueryTypeOT)
}
