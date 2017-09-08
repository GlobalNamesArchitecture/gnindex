package org.globalnames

import org.scalatest._
import org.scalatest.concurrent.ScalaFutures

trait FunSpecConfig extends FunSpec with Matchers with OptionValues
                       with BeforeAndAfterEach with BeforeAndAfterAll with ScalaFutures
