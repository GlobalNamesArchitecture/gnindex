package org.globalnames
package index
package api

import sangria.schema._
import sangria.marshalling.sprayJson._
import spray.json.{DefaultJsonProtocol, _}
import thrift.nameresolver._

object SchemaDefinition extends DefaultJsonProtocol {

  implicit val nameRequestFormat: RootJsonFormat[NameInput] =
    jsonFormat(NameInput.apply, "value", "suppliedId")

  val MatchTypeOT = ObjectType(
    "MatchType", fields[Unit, MatchType](
        Field("kind", StringType, resolve = _.value.kind.name)
      , Field("score", IntType, resolve = _.value.score)
      , Field("editDistance", IntType, resolve = _.value.editDistance)
    )
  )

  val NameOT = ObjectType(
    "Name", fields[Unit, Name](
        Field("id", IDType, resolve = _.value.uuid.uuidString)
      , Field("name", StringType, resolve = _.value.value)
    )
  )

  val ClassificationOT = ObjectType(
    "Classification", fields[Unit, Classification](
        Field("path", OptionType(StringType), resolve = _.value.path)
      , Field("pathIds", OptionType(StringType), resolve = _.value.pathIds)
      , Field("pathRanks", OptionType(StringType), resolve = _.value.pathRanks)
    )
  )

  val ResultItemOT = ObjectType(
    "ResultItem", fields[Unit, Result](
        Field("name", NameOT, resolve = _.value.name)
      , Field("canonicalName", OptionType(NameOT), resolve = _.value.canonicalName)
      , Field("synonym", BooleanType, resolve = _.value.synonym)
      , Field("taxonId", StringType, resolve = _.value.taxonId)
      , Field("classification", ClassificationOT, resolve = _.value.classification)
      , Field("matchType", MatchTypeOT, resolve = _.value.matchType)
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

  val NameRequestIOT = InputObjectType[NameInput]("name", List(
      InputField("value", StringType)
    , InputField("suppliedId", OptionInputType(StringType))
  ))

  val NamesRequestArg = Argument("names", ListInputType(NameRequestIOT))

  val QueryTypeOT = ObjectType(
    "Query", fields[Repository, Unit](
      Field("nameResolver", ListType(ResponseOT),
        arguments = List(NamesRequestArg),
        resolve = ctx => ctx.withArgs(NamesRequestArg)(ctx.ctx.nameResolver)
      )
    )
  )

  val schema = Schema(QueryTypeOT)
}
