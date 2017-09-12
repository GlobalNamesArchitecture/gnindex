import Dependencies._
import Settings._
import com.github.tototoshi.sbt.slick.CodegenPlugin
import scoverage.ScoverageKeys._
import io.gatling.sbt.GatlingPlugin

lazy val common = (project in file("common"))
    .enablePlugins(CodegenPlugin)
    .settings(noPublishingSettings: _*)
    .settings(Settings.settings: _*)
    .settings(Settings.commonSettings: _*)
    .settings(libraryDependencies ++= commonDependencies)

lazy val matcher = (project in file("matcher"))
    .dependsOn(common % "compile->compile;test->test")
    .settings(publishingSettings: _*)
    .settings(Settings.settings: _*)
    .settings(Settings.wartremoverSettings: _*)
    .settings(Settings.matcherSettings: _*)
    .settings(libraryDependencies ++= matcherDependencies)

lazy val nameResolver = (project in file("nameResolver"))
    .dependsOn(common % "compile->compile;test->test", matcher)
    .settings(publishingSettings: _*)
    .settings(Settings.settings: _*)
    .settings(Settings.wartremoverSettings: _*)
    .settings(Settings.nameResolverSettings: _*)
    .settings(libraryDependencies ++= nameResolverDependencies)

lazy val nameFilter = (project in file("nameFilter"))
    .dependsOn(common % "compile->compile;test->test", matcher)
    .settings(publishingSettings: _*)
    .settings(Settings.settings: _*)
    .settings(Settings.wartremoverSettings: _*)
    .settings(Settings.nameFilterSettings: _*)
    .settings(libraryDependencies ++= nameFilterDependencies)

lazy val api = (project in file("api"))
    .enablePlugins(BuildInfoPlugin)
    .dependsOn(common % "compile->compile;test->test", nameResolver, matcher)
    .settings(publishingSettings: _*)
    .settings(Settings.settings: _*)
    .settings(Settings.wartremoverSettings: _*)
    .settings(Settings.apiSettings: _*)
    .settings(libraryDependencies ++= apiDependencies)

lazy val benchmark = (project in file("benchmark"))
    .enablePlugins(GatlingPlugin)
    .settings(noPublishingSettings: _*)
    .settings(Settings.settings: _*)
    .settings(Settings.wartremoverSettings: _*)
    .settings(Settings.benchmarkSettings: _*)
    .settings(libraryDependencies ++= benchmarkDependencies)

lazy val `gnindex-root` = project.in(file("."))
    .aggregate(common, nameResolver, nameFilter, matcher, api)
    .settings(noPublishingSettings: _*)
    .settings(
      crossScalaVersions := Seq("2.11.8"),
      coverageEnabled := true
    )
