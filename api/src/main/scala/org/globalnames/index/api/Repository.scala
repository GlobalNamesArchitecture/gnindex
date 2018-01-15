package org.globalnames
package index
package api

import javax.inject.Inject

import thrift.{Uuid, DataSource}
import thrift.{namefilter => nf, nameresolver => ns}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future => ScalaFuture}
import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.twitter_util.UtilBijections._
import util.UuidEnhanced._
import java.util.UUID

class Repository @Inject() (nameResolverClient: ns.Service.FutureIface,
                            nameFilterClient: nf.Service.FutureIface) {

  def nameResolver(namesInput: Seq[ns.NameInput],
                   dataSourceIds: Option[Seq[Int]],
                   preferredDataSourceIds: Option[Seq[Int]],
                   advancedResolution: Boolean,
                   bestMatchOnly: Boolean): ScalaFuture[ns.Responses] = {
    val req = ns.Request(names = namesInput,
                         dataSourceIds = dataSourceIds.getOrElse(Seq()),
                         preferredDataSourceIds = preferredDataSourceIds.getOrElse(Seq()),
                         advancedResolution = advancedResolution,
                         bestMatchOnly = bestMatchOnly)
    nameResolverClient.nameResolve(req).as[ScalaFuture[ns.Responses]]
  }

  def nameStrings(searchTerm: String, page: Int, perPage: Int):
      ScalaFuture[Seq[nf.ResponseNameStrings]] = {
    val req = nf.Request(searchTerm = searchTerm, page = page, perPage = perPage)
    nameFilterClient.nameString(req).as[ScalaFuture[Seq[nf.ResponseNameStrings]]]
  }

  def nameStringsByUuids(uuids: Seq[String]): ScalaFuture[Seq[nf.Response]] = {
    nameFilterClient.nameStringByUuid(uuids.map { u => UUID.fromString(u): Uuid })
                    .as[ScalaFuture[Seq[nf.Response]]]
  }

  def dataSourceById(idsOpt: Option[Seq[Int]]): ScalaFuture[Seq[DataSource]] = {
    val ids = idsOpt.getOrElse(Seq())
    nameFilterClient.dataSourceById(ids)
                    .as[ScalaFuture[Seq[DataSource]]]
  }

}
