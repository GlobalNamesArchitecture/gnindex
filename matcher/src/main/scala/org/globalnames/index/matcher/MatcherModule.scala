package org.globalnames
package index
package matcher

import java.io.File
import com.google.inject.{Provides, Singleton}
import com.twitter.app.Flag
import com.twitter.inject.TwitterModule
import org.globalnames.matcher.Matcher

import scala.io.Source

import scalaz._
import Scalaz._

object MatcherModule extends TwitterModule {

  val namesFileKey: Flag[File] = flag(
    name = "names-path",
    help = "Path to file that contains canonical names for matcher"
  )

  @Singleton
  @Provides
  def prodiveMatcher: Matcher = {
    val names = namesFileKey().exists ?
      Source.fromFile(namesFileKey()).getLines.toVector |
      { logger.error("names files doesn't exist"); Vector.empty[String] }
    Matcher(names)
  }

}
