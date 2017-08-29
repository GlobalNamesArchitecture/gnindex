package org.globalnames
package index
package api

import javax.inject.Inject

import thrift.ResultScored
import thrift.namefilter.{Request => FSRequest, Service => NameFilterService}
import thrift.nameresolver.{Request => NRRequest, Service => NameResolverService, _}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future => ScalaFuture}
import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.twitter_util.UtilBijections._

class Repository @Inject() (nameResolverClient: NameResolverService.FutureIface,
                            nameFilterClient: NameFilterService.FutureIface) {
  def nameResolver(namesInput: Seq[NameInput],
                   dataSourceIds: Option[Seq[Int]],
                   preferredDataSourceIds: Option[Seq[Int]],
                   advancedResolution: Boolean): ScalaFuture[Responses] = {
    val req = NRRequest(names = namesInput,
                        dataSourceIds = dataSourceIds.getOrElse(Seq()),
                        preferredDataSourceIds = preferredDataSourceIds.getOrElse(Seq()),
                        advancedResolution = advancedResolution)
    nameResolverClient.nameResolve(req).as[ScalaFuture[Responses]]
  }

  def nameStrings(searchTerm: String): ScalaFuture[Seq[ResultScored]] = {
    val req = FSRequest(searchTerm = searchTerm)
    nameFilterClient.nameString(req).as[ScalaFuture[Seq[ResultScored]]]
  }
}
