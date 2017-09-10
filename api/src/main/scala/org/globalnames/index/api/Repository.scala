package org.globalnames
package index
package api

import javax.inject.Inject

import thrift.{Result, Uuid}
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
                   advancedResolution: Boolean): ScalaFuture[ns.Responses] = {
    val req = ns.Request(names = namesInput,
                         dataSourceIds = dataSourceIds.getOrElse(Seq()),
                         preferredDataSourceIds = preferredDataSourceIds.getOrElse(Seq()),
                         advancedResolution = advancedResolution)
    nameResolverClient.nameResolve(req).as[ScalaFuture[ns.Responses]]
  }

  def nameStrings(searchTerm: String): ScalaFuture[Seq[Result]] = {
    val req = nf.Request(searchTerm = searchTerm)
    nameFilterClient.nameString(req).as[ScalaFuture[Seq[Result]]]
  }

  def nameStringsByUuids(uuids: Seq[String]): ScalaFuture[Seq[nf.Response]] = {
    nameFilterClient.nameStringByUuid(uuids.map { u => UUID.fromString(u): Uuid })
                    .as[ScalaFuture[Seq[nf.Response]]]
  }

}
