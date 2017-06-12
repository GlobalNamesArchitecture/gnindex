package org.globalnames
package microservices.index

import java.io.File
import com.google.inject.{Provides, Singleton}
import com.twitter.app.Flag
import com.twitter.inject.TwitterModule

import scala.io.Source

import resolver.Resolver

import scalaz._
import Scalaz._

object IndexModule extends TwitterModule {

  @Singleton
  @Provides
  def prodiveResolver: Resolver = null

}
