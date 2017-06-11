import Dependencies._
import Settings._

lazy val common = (project in file("common"))
    .settings(noPublishingSettings: _*)
    .settings(Settings.settings: _*)
    .settings(Settings.commonSettings: _*)
    .settings(libraryDependencies ++= commonDependencies)

lazy val matcher = (project in file("matcher"))
    .dependsOn(common)
    .settings(publishingSettings: _*)
    .settings(Settings.settings: _*)
    .settings(Settings.matcherSettings: _*)
    .settings(libraryDependencies ++= matcherDependencies)

lazy val index = (project in file("index"))
    .dependsOn(common, matcher)
    .settings(publishingSettings: _*)
    .settings(Settings.settings: _*)
    .settings(Settings.indexSettings: _*)
    .settings(libraryDependencies ++= indexDependencies)

lazy val `index-graphql` = (project in file("index-graphql"))
    .dependsOn(common, index, matcher)
    .settings(publishingSettings: _*)
    .settings(Settings.settings: _*)
    .settings(Settings.indexGraphqlSettings: _*)
    .settings(libraryDependencies ++= indexGraphqlDependencies)

lazy val `gnmicroservices-root` = project.in(file("."))
    .aggregate(common, index, matcher, `index-graphql`)
    .settings(noPublishingSettings: _*)
    .settings(crossScalaVersions := Seq("2.11.8"))
