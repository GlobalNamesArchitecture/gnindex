package org.globalnames.index.api

import sangria.schema._

object SchemaDefinition {
  val ResponseOT = ObjectType(
    "Response", fields[Repository, Unit](
      Field("total", LongType, None, resolve = _ => 42L)
    )
  )

  val NamesRequestArg = Argument("names", ListInputType(StringType))

  val QueryTypeOT = ObjectType(
    "Query", fields[Repository, Unit](
      Field("nameResolver", ResponseOT,
        arguments = List(NamesRequestArg),
        resolve = ctx => ()
      )
    ))

  val schema = Schema(QueryTypeOT)
}
