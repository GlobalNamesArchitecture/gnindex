package org.globalnames
package index
package browser

import java.io.File
import com.google.inject.{Provides, Singleton}
import com.twitter.app.Flag
import com.twitter.inject.{Logging, TwitterModule}

import scala.io.Source

import scalaz._
import Scalaz._

import thrift.{namebrowser => nb}

object BrowserModule extends TwitterModule with Logging {

  val browseIndexFilePath: Flag[File] = flag(
    name = "index-path",
    help = "Path to file that contains valid triplets"
  )

  @Singleton
  @Provides
  def provideValidTriplets: Vector[nb.Triplet] = {
    def create(): Vector[nb.Triplet] = {
      val activeTriplets =
        Source.fromFile(browseIndexFilePath()).getLines.zipWithIndex
          .foldLeft(Set.empty[String]) {
            case (set, (line, idx)) =>
              if (idx % 1000 == 0) {
                logger.info(s"Triplets loaded: $idx")
              }
              set + line
          }

      val triplets =
        for (triplet <- ('A' to 'Z').toVector.replicateM(3).map { _.mkString })
          yield nb.Triplet(triplet, active = activeTriplets.contains(triplet))

      triplets
    }

    browseIndexFilePath().exists() ? create() | {
      logger.error(s"${browseIndexFilePath()} file doesn't exist")
      Vector()
    }
  }

}
