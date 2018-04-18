package org.globalnames
package index
package util

import index.{thrift => t}

object DataSource {
  val ordering: Ordering[t.DataSource] = new Ordering[t.DataSource] {
    private val colDsId = 1
    private val gbifDsId = 11

    override def compare(x: t.DataSource, y: t.DataSource): Int = {
      (x.id, y.id) match {
        case (`colDsId`, `colDsId`) => 0
        case (`gbifDsId`, `gbifDsId`) => 0
        case (`colDsId`, _) => 1
        case (_, `colDsId`) => -1
        case (`gbifDsId`, _) => 1
        case (_, `gbifDsId`) => -1
        case (_, _) => Ordering.String.compare(y.title.toLowerCase, x.title.toLowerCase)
      }
    }
  }
}
