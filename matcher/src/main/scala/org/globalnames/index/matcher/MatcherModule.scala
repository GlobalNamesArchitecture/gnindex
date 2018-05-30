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
      logger.info("`provideCanonicalNames` launched")
      val namesWithDatasourcesLines =
        Source.fromFile(namesWithDatasourcesFileKey()).getLines.toVector
      logger.info("file loaded")
      var counter = 0
      var nameToDatasourceIdsMap: Map[String, Set[Int]] = Map.empty[String, Set[Int]]
      var canonicalNameCurrent = ""
      var canonicalNameCurrentDataSourceIds = Set.empty[Int]
      for (line <- namesWithDatasourcesLines) {
        if (counter > 0 && counter % 100000 == 0) {
          logger.info(s"Names with datasources loaded: " +
            s"$counter of ${namesWithDatasourcesLines.size}")
        }
        counter += 1
        val Array(canonicalName, dataSourceId) = line.split("\t")
        if (canonicalName != canonicalNameCurrent) {
          if (canonicalNameCurrent != "") {
            nameToDatasourceIdsMap += canonicalNameCurrent -> canonicalNameCurrentDataSourceIds
          }
          canonicalNameCurrent = canonicalName
          canonicalNameCurrentDataSourceIds = Set.empty[Int]
        }

        canonicalNameCurrentDataSourceIds += dataSourceId.toInt
      }
      logger.info(s"""FINISHED.
                  |Total keys: ${nameToDatasourceIdsMap.size}
                  |Total values: ${nameToDatasourceIdsMap.values.map { _.size }.sum}""".stripMargin)
      CanonicalNames(nameToDatasourceIdsMap)
    }

    namesWithDatasourcesFileKey().exists ? create() |
      { logger.error(s"${namesWithDatasourcesFileKey()} file doesn't exist")
        CanonicalNames(Map.empty.withDefaultValue(Set.empty)) }
  }

}
