package org.globalnames
package index
package api

import javax.inject.Inject

import thrift.nameresolver.{NameInput, Request, Response, Service => NameResolverService}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future => ScalaFuture}
import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.twitter_util.UtilBijections._

class Repository @Inject() (nameResolverClient: NameResolverService.FutureIface) {
  def nameResolver(namesInput: Seq[NameInput],
                   dataSourceIds: Option[Seq[Int]],
                   preferredDataSourceIds: Option[Seq[Int]]): ScalaFuture[Seq[Response]] = {
    val req = Request(names = namesInput,
                      dataSourceIds = dataSourceIds.getOrElse(Seq()),
                      preferredDataSourceIds = preferredDataSourceIds.getOrElse(Seq())
              )
    nameResolverClient.nameResolve(req).as[ScalaFuture[Seq[Response]]]
  }
}
