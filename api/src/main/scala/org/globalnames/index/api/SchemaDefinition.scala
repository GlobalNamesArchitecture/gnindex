package org.globalnames
package index
package api

import sangria.schema._
import thrift.nameresolver.{Name, Result, Response}

object SchemaDefinition {
  val NameOT = ObjectType(
    "Name", fields[Unit, Name](
        Field("id", IDType, resolve = _.value.uuid.uuidString)
      , Field("name", StringType, resolve = _.value.value)
    )
  )

  val ResultItemOT = ObjectType(
    "ResultItem", fields[Unit, Result](
        Field("name", NameOT, resolve = _.value.name)
      , Field("canonicalName", OptionType(NameOT), resolve = _.value.canonicalName)
    )
  )

  val ResponseOT = ObjectType(
    "Response", fields[Unit, Response](
        Field("total", IntType, None, resolve = _.value.total)
      , Field("results", ListType(ResultItemOT), None, resolve = _.value.results)
    )
  )

  val NamesRequestArg = Argument("names", ListInputType(StringType))

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
