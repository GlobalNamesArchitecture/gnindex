package org.globalnames
package index
package util

import index.{thrift => t}

object DataSource {
  val ordering: Ordering[t.DataSource] = new Ordering[t.DataSource] {
    private val colDsId = 1
    private val gbifDsId = 11

    private def databaseLevel(ds: t.DataSource): Int = ds.id match {
      case `colDsId` => 50
      case _ if ds.quality.value == t.DataSourceQuality.Curated.value => 40
      case `gbifDsId` => 30
      case _ if ds.quality.value == t.DataSourceQuality.AutoCurated.value => 20
      case _ => 10
    }

    override def compare(ds1: t.DataSource, ds2: t.DataSource): Int = {
      val dbl1 = databaseLevel(ds1)
      val dbl2 = databaseLevel(ds2)
      if (dbl1 != dbl2) {
        Ordering.Int.compare(dbl1, dbl2)
      } else {
        Ordering.String.compare(ds1.title, ds2.title)
      }
    }
  }
}
