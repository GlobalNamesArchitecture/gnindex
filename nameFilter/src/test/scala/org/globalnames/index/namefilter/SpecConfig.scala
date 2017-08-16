package org.globalnames
package index.namefilter

import org.scalatest._
import org.scalatest.concurrent.ScalaFutures

trait SpecConfig extends FunSpec with Matchers with OptionValues
                 with BeforeAndAfterEach with BeforeAndAfterAll with ScalaFutures
