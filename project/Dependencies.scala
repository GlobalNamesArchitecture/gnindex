import sbt._
import Keys._

object Dependencies {

  lazy val version = new {
    val finatra = "2.11.0"
    val guice = "4.1.0"
    val junit = "4.12"
    val logback = "1.2.3"
    val mockito = "1.10.19"
    val scalatest = "3.0.3"
    val liblevenshtein = "3.0.0"
  }

  lazy val library = new {
    val liblevenshtein = "com.github.universal-automata" %  "liblevenshtein"  % version.liblevenshtein
    val finatra        = "com.twitter"                   %% "finatra-thrift"  % version.finatra
    val logback        = "ch.qos.logback"                %  "logback-classic" % version.logback
    val guice          = "com.google.inject.extensions"  %  "guice-testlib"   % version.guice          % Test
    val scalatest      = "org.scalatest"                 %% "scalatest"       % version.scalatest      % Test
    val twitterInjApp  = "com.twitter"                   %% "inject-app"      % version.finatra        % Test
    val twitterInjCor  = "com.twitter"                   %% "inject-core"     % version.finatra        % Test
    val twitterInjMod  = "com.twitter"                   %% "inject-modules"  % version.finatra        % Test
    val twitterInjSer  = "com.twitter"                   %% "inject-server"   % version.finatra        % Test
    val mockito        = "org.mockito"                   %  "mockito-core"    % version.mockito        % Test
    val junit          = "junit"                         %  "junit"           % version.junit          % Test
  }

  val finatraDeps = Seq(library.finatra, library.logback)
  val finatraTestDeps = Seq(
    library.twitterInjApp, library.twitterInjCor, library.twitterInjMod, library.twitterInjSer,
    library.finatra % Test classifier "tests",
    library.twitterInjApp classifier "tests", library.twitterInjCor classifier "tests",
    library.twitterInjMod classifier "tests", library.twitterInjSer classifier "tests"
  )
  val testDeps = Seq(library.junit, library.scalatest, library.mockito, library.guice)

  val idlDependencies: Seq[ModuleID] = Seq(library.finatra)

  val indexDependencies: Seq[ModuleID] = finatraDeps ++ finatraTestDeps ++ testDeps

  val matcherDependencies: Seq[ModuleID] = finatraDeps ++ finatraTestDeps ++ testDeps

  val indexGraphqlDependencies: Seq[ModuleID] = finatraDeps ++ finatraTestDeps ++ testDeps

}
