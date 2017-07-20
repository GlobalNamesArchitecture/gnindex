package org.globalnames
package index
package matcher

import java.io.File
import com.google.inject.{Provides, Singleton}
import com.twitter.app.Flag
import com.twitter.inject.{Logging, TwitterModule}
import org.globalnames.{matcher => matcherlib}

import scala.io.Source

import scalaz._
import Scalaz._

object MatcherModule extends TwitterModule with Logging {

  val namesFileKey: Flag[File] = flag(
    name = "names-path",
    help = "Path to file that contains canonical names for matcher"
  )

  val namesWithDatasourcesFileKey: Flag[File] = flag(
    name = "names-datasources-path",
    help = "Path to file that contains canonical names with datasources for matcher"
  )

  @Singleton
  @Provides
  def prodiveMatcherLib: matcherlib.Matcher = {
    val names = namesFileKey().exists ?
      Source.fromFile(namesFileKey()).getLines.toVector |
      { logger.error(s"${namesFileKey()} file doesn't exist"); Vector.empty[String] }
    matcherlib.Matcher(names)
  }

  @Singleton
  @Provides
  def provideCanonicalNames: CanonicalNames = {
    def create(): CanonicalNames = {
      val nameToDatasourceIdsMap =
        Source.fromFile(namesWithDatasourcesFileKey()).getLines.zipWithIndex
          .foldLeft(Map.empty[String, Set[Int]].withDefaultValue(Set.empty)) {
            case (mp, (line, idx)) =>
              if (idx % 100000 == 0) {
                logger.info(s"Names with datasources loaded in total: $idx")
              }
              val Array(canonicalName, dataSourceId) = line.split('\t')
              mp + (canonicalName -> (mp(canonicalName) + dataSourceId.toInt))
          }
      CanonicalNames(nameToDatasourceIdsMap)
    }

    namesWithDatasourcesFileKey().exists ? create() |
      { logger.error(s"${namesWithDatasourcesFileKey()} file doesn't exist")
        CanonicalNames(Map.empty.withDefaultValue(Set.empty)) }
  }

}
