package org.globalnames

import java.io.{BufferedOutputStream, OutputStreamWriter, PrintWriter}

import org.joda.time.format.PeriodFormatterBuilder
import org.joda.time.{Duration, DurationFieldType, PeriodType}
import org.scalatest.Reporter
import org.scalatest.events._

import scala.concurrent.duration._

class TestReporter extends Reporter {
  import TestReporter.{SuiteInfo, TestInfo, formatDuration, printlnGreen}

  private var suites = Map.empty[String, SuiteInfo]

  def apply(event: Event): Unit = event match {
    case ts: TestStarting =>
      suites(ts.suiteId).tests += ts.testName -> TestInfo(ts.testName, ts.suiteName)

    case ts: TestSucceeded =>
      suites(ts.suiteId).tests(ts.testName).success()

    case ts: TestFailed =>
      suites(ts.suiteId).tests(ts.testName).failed()

    case ss: SuiteStarting =>
      suites += ss.suiteId -> SuiteInfo()

    case sc: RunCompleted =>
      val minimumDuration = 500.millis

      val slowTests = suites.values
        .flatMap { _.tests.values }
        .filter { test => test.succeeded && test.duration >= minimumDuration }
        .toVector
        .sortBy { -_.duration }

      if (slowTests.nonEmpty) {
        printlnGreen(s"Slowest tests above ${minimumDuration.toMillis}ms")
        slowTests.foreach { ti =>
          printlnGreen(f"${formatDuration(ti.duration)}%9s - ${ti.suiteName}: ${ti.testName}")
        }
      }

      suites = Map.empty[String, SuiteInfo]

    case _ => ()
  }
}

object TestReporter {
  private[TestReporter] sealed trait TestStatus
  private[TestReporter] case object Pending   extends TestStatus
  private[TestReporter] case object Succeeded extends TestStatus
  private[TestReporter] case object Failed    extends TestStatus

  private[TestReporter] case class TestInfo(testName: String, suiteName: String) {
    private final val nanoStartTime: Long = System.nanoTime()
    private final var nanoEndTime: Long = 0
    private final var status: TestStatus = Pending

    def success(): Unit = {
      nanoEndTime = System.nanoTime()
      status = Succeeded
    }

    def failed(): Unit = {
      nanoEndTime = System.nanoTime()
      status = Failed
    }

    def duration: FiniteDuration = (nanoEndTime - nanoStartTime).nanos
    def succeeded: Boolean = status == Succeeded
  }

  private val GreenColor = "\u001b[32m"
  private val stdOut: PrintWriter = {
    val bufferSize = 4096

    new PrintWriter(
      new OutputStreamWriter(
        new BufferedOutputStream(Console.out, bufferSize)
      )
    )
  }

  private[TestReporter] def printlnGreen(str: String): Unit = {
    // scalastyle:off println
    stdOut.println(s"$GreenColor$str")
    // scalastyle:on println
    stdOut.flush()
  }

  private[TestReporter] case class SuiteInfo() {
    var tests = Map.empty[String, TestInfo]
  }

  private val formatter = new PeriodFormatterBuilder()
    .appendSeconds
    .appendSuffix("s")
    .appendSeparator(" ")
    .appendMillis
    .appendSuffix("ms")
    .toFormatter

  private[TestReporter] def formatDuration(f: FiniteDuration) = {
    val duration = new Duration(f.toMillis)
      .toPeriod(PeriodType.forFields(Array(DurationFieldType.seconds, DurationFieldType.millis)))
    formatter.print(duration)
  }
}
