import sbt._

object Dependencies {

  lazy val version = new {
    val sangria = "1.4.2"
    val sangriaJackson = "1.0.0"
    val finatra = "18.8.0"
    val guice = "4.1.0"
    val junit = "4.12"
    val pegdown = "1.6.0"
    val niceUuid = "1.3"
    val logback = "1.2.3"
    val mockito = "1.10.19"
    val scalatest = "3.0.5"
    val liblevenshtein = "3.0.0"
    val scalaz = "7.2.21"
    val json4sJackson = "3.5.2"
    val slick = "3.2.3"
    val parboiled2 = "2.1.4"
    val jodaMapper = "2.3.0"
    val postgres = "42.1.1.jre7"
    val gnmatcher = "0.1.3-20180616_2132-SNAPSHOT"
    val gnparser = "1.0.3-SNAPSHOT"
    val gatling = "2.3.0"
    val twitterBijection = "0.9.5"
  }

  lazy val library = new {
    val gnparser             = "org.globalnames"               %% "gnparser"                     % version.gnparser
    val `gnparser-render`    = "org.globalnames"               %% "gnparser-render"              % version.gnparser
    val gnmatcher            = "org.globalnames"               %% "gnmatcher"                    % version.gnmatcher
    val twitterBijectionCore = "com.twitter"                   %% "bijection-core"               % version.twitterBijection
    val twitterBijectionUtil = "com.twitter"                   %% "bijection-util"               % version.twitterBijection
    val slick                = "com.typesafe.slick"            %% "slick"                        % version.slick
    val slickJodaMapper      = "com.github.tototoshi"          %% "slick-joda-mapper"            % version.jodaMapper
    val hikariSlick          = "com.typesafe.slick"            %% "slick-hikaricp"               % version.slick
    val postgresql           = "org.postgresql"                %  "postgresql"                   % version.postgres
    val scalaz               = "org.scalaz"                    %% "scalaz-core"                  % version.scalaz
    val liblevenshtein       = "com.github.universal-automata" %  "liblevenshtein"               % version.liblevenshtein
    val finatraThrift        = "com.twitter"                   %% "finatra-thrift"               % version.finatra
    val finatraHttp          = "com.twitter"                   %% "finatra-http"                 % version.finatra
    val finatraJackson       = "com.twitter"                   %% "finatra-jackson"              % version.finatra
    val parboiled2           = "org.parboiled"                 %% "parboiled"                    % version.parboiled2
    val sangria              = "org.sangria-graphql"           %% "sangria"                      % version.sangria
    val sangriaJackson       = "org.sangria-graphql"           %% "sangria-json4s-jackson"       % version.sangriaJackson
    val json4sJackson        = "org.json4s"                    %% "json4s-jackson"               % version.json4sJackson
    val logback              = "ch.qos.logback"                %  "logback-classic"              % version.logback
    val guice                = "com.google.inject.extensions"  %  "guice-testlib"                % version.guice          % Test
    val scalatest            = "org.scalatest"                 %% "scalatest"                    % version.scalatest      % Test
    val twitterInjApp        = "com.twitter"                   %% "inject-app"                   % version.finatra        % Test
    val twitterInjCor        = "com.twitter"                   %% "inject-core"                  % version.finatra        % Test
    val twitterInjMod        = "com.twitter"                   %% "inject-modules"               % version.finatra        % Test
    val twitterInjSer        = "com.twitter"                   %% "inject-server"                % version.finatra        % Test
    val mockito              = "org.mockito"                   %  "mockito-core"                 % version.mockito        % Test
    val junit                = "junit"                         %  "junit"                        % version.junit          % Test
    val pegdown              = "org.pegdown"                   %  "pegdown"                      % version.pegdown        % Test
    val niceUuid             = "biz.neumann"                   %% "nice-uuid"                    % version.niceUuid       % Test
    val gatlingCharts        = "io.gatling.highcharts"         %  "gatling-charts-highcharts"    % version.gatling        % Test
    val gatlingTestFramework = "io.gatling"                    %  "gatling-test-framework"       % version.gatling        % Test
  }

  val finatraDeps = Seq(library.finatraThrift, library.logback)
  val finatraTestDeps = Seq(
    library.junit, library.mockito, library.guice,
    library.twitterInjApp, library.twitterInjCor, library.twitterInjMod, library.twitterInjSer,
    library.finatraThrift % Test classifier "tests",
    library.twitterInjApp classifier "tests", library.twitterInjCor classifier "tests",
    library.twitterInjMod classifier "tests", library.twitterInjSer classifier "tests"
  )
  val testDeps = Seq(library.scalatest, library.pegdown, library.niceUuid)

  val commonDependencies: Seq[ModuleID] = testDeps ++ Seq(
    library.scalaz,
    library.gnparser, library.`gnparser-render`,
    library.finatraThrift,
    library.slick, library.slickJodaMapper, library.postgresql, library.hikariSlick
  )

  val nameResolverDependencies: Seq[ModuleID] = finatraDeps ++ finatraTestDeps ++ testDeps ++ Seq(
    library.twitterBijectionCore, library.twitterBijectionUtil
  )

  val nameFilterDependencies: Seq[ModuleID] = finatraDeps ++ finatraTestDeps ++ testDeps ++ Seq(
    library.twitterBijectionCore, library.twitterBijectionUtil
  )

  val nameBrowserDependencies: Seq[ModuleID] = finatraDeps ++ finatraTestDeps ++ testDeps ++ Seq(
    library.twitterBijectionCore, library.twitterBijectionUtil
  )

  val crossMapperDependencies: Seq[ModuleID] = finatraDeps ++ finatraTestDeps ++ testDeps ++ Seq(
    library.twitterBijectionCore, library.twitterBijectionUtil
  )

  val matcherDependencies: Seq[ModuleID] = finatraDeps ++ finatraTestDeps ++ testDeps ++ Seq(
    library.finatraHttp, library.finatraJackson,
    library.gnmatcher
  )

  val apiDependencies: Seq[ModuleID] = finatraDeps ++ finatraTestDeps ++ testDeps ++ Seq(
    library.finatraHttp, library.finatraJackson,
    library.sangria, library.sangriaJackson, library.json4sJackson
  )

  val benchmarkDependencies: Seq[ModuleID] = testDeps ++ Seq(
    library.gatlingCharts, library.gatlingTestFramework
  )
}
