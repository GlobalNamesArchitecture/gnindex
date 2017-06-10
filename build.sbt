import Dependencies._
import Settings._

lazy val idl = (project in file("idl"))
    .settings(noPublishingSettings: _*)
    .settings(Settings.settings: _*)
    .settings(Settings.idlSettings: _*)
    .settings(libraryDependencies ++= idlDependencies)

lazy val matcher = (project in file("matcher"))
    .dependsOn(idl)
    .settings(publishingSettings: _*)
    .settings(Settings.settings: _*)
    .settings(Settings.matcherSettings: _*)
    .settings(libraryDependencies ++= matcherDependencies)

lazy val index = (project in file("index"))
    .dependsOn(idl, matcher)
    .settings(publishingSettings: _*)
    .settings(Settings.settings: _*)
    .settings(Settings.indexSettings: _*)
    .settings(libraryDependencies ++= indexDependencies)

lazy val `index-graphql` = (project in file("index-graphql"))
    .dependsOn(idl, index, matcher)
    .settings(publishingSettings: _*)
    .settings(Settings.settings: _*)
    .settings(Settings.indexGraphqlSettings: _*)
    .settings(libraryDependencies ++= indexGraphqlDependencies)

lazy val `gnmicroservices-root` = project.in(file("."))
    .aggregate(idl, index, matcher, `index-graphql`)
    .settings(noPublishingSettings: _*)
    .settings(crossScalaVersions := Seq("2.11.8"))
