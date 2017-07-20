package org.globalnames
package index
package api

import sangria.schema._
import sangria.marshalling.{CoercedScalaResultMarshaller, FromInput}
import thrift.{Name, MatchType}
import thrift.nameresolver._
import util.UuidEnhanced.ThriftUuidEnhanced

object SchemaDefinition {
  implicit val nameInputFromInput = new FromInput[NameInput] {
    val marshaller: CoercedScalaResultMarshaller = CoercedScalaResultMarshaller.default

    def fromResult(node: marshaller.Node): NameInput = {
      val nodeMap = node.asInstanceOf[Map[String, Any]]
      NameInput(
        value = nodeMap("value").asInstanceOf[String],
        suppliedId = nodeMap.get("suppliedId").flatMap { _.asInstanceOf[Option[String]] }
      )
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
      , Field("name", StringType, resolve = _.value.value)
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
    )
  )

  val ResultItemOT = ObjectType(
    "ResultItem", fields[Unit, ResultScored](
        Field("name", NameOT, resolve = _.value.result.name)
      , Field("canonicalName", OptionType(NameOT), resolve = _.value.result.canonicalName)
      , Field("synonym", BooleanType, resolve = _.value.result.synonym)
      , Field("taxonId", StringType, resolve = _.value.result.taxonId)
      , Field("classification", ClassificationOT, resolve = _.value.result.classification)
      , Field("matchType", MatchTypeOT, resolve = _.value.result.matchType)
      , Field("score", ScoreOT, resolve = _.value.score)
      , Field("dataSource", DataSourceOT, resolve = _.value.result.dataSource)
    )
  )

  val ResponseOT = ObjectType(
    "Response", fields[Unit, Response](
        Field("total", IntType, None, resolve = _.value.total)
      , Field("suppliedInput", OptionType(StringType), None, resolve = _.value.suppliedInput)
      , Field("suppliedId", OptionType(StringType), None, resolve = _.value.suppliedId)
      , Field("results", ListType(ResultItemOT), None, resolve = _.value.results)
    )
  )

  val DataSourceIdsArg = Argument("dataSourceIds", OptionInputType(ListInputType(IntType)))
  val NameRequestIOT = InputObjectType[NameInput]("name", List(
      InputField("value", StringType)
    , InputField("suppliedId", OptionInputType(StringType))
  ))
  val NamesRequestArg = Argument("names", ListInputType(NameRequestIOT))

  val QueryTypeOT = ObjectType(
    "Query", fields[Repository, Unit](
      Field("nameResolver", ListType(ResponseOT),
        arguments = List(NamesRequestArg, DataSourceIdsArg),
        resolve = ctx => ctx.withArgs(NamesRequestArg, DataSourceIdsArg)(ctx.ctx.nameResolver)
      )
    )
  )

  val schema = Schema(QueryTypeOT)
}
