package org.globalnames
package index
package browser

import com.twitter.inject.Logging
import javax.inject.{Inject, Singleton}

import thrift.{namebrowser => nb}

@Singleton
class Browser @Inject()(triplets: Vector[nb.Triplet]) extends Logging {

  private val tripletsHash: Map[Char, Seq[nb.Triplet]] =
    triplets.groupBy { _.value.charAt(0) }
            .withDefaultValue(Seq())

  def tripletsStartingWith(ch: Char): Seq[nb.Triplet] = tripletsHash(ch.toUpper)

}
