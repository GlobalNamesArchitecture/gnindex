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
  def nameResolver(names: Seq[String]): ScalaFuture[Seq[Response]] = {
    val nameInputs = names.map { n => NameInput(value = n) }
    val req = Request(names = nameInputs)
    nameResolverClient.nameResolve(req).as[ScalaFuture[Seq[Response]]]
  }
}
