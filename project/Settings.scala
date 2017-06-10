import sbt._
import Keys._
import sbtassembly.AssemblyPlugin.autoImport._
import com.typesafe.sbt.SbtPgp.autoImport._
import com.twitter.scrooge.ScroogeSBT.autoImport._
import sbtbuildinfo.BuildInfoKey
import sbtbuildinfo.BuildInfoKeys._

object Settings {

  lazy val settings = Seq(
    version := "0.1.0-SNAPSHOT" + sys.props.get("buildNumber").map { "-" + _ }.getOrElse(""),
    scalaVersion := "2.11.11",
    homepage := Some(new URL("http://globalnames.org/")),
    organization in ThisBuild := "org.globalnames",
    description := "Family of Global Names microservices",
    startYear := Some(2015),
    licenses := Seq("MIT" -> new URL("https://github.com/GlobalNamesArchitecture/gnmicroservices/blob/master/LICENSE")),
    resolvers ++= Seq(
      "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
      Resolver.sonatypeRepo("snapshots")
    ),
    javacOptions ++= Seq(
      "-encoding", "UTF-8",
      "-source", "1.6",
      "-target", "1.6",
      "-Xlint:unchecked",
      "-Xlint:deprecation"),
    scalacOptions ++= List(
      "-encoding", "UTF-8",
      "-feature",
      "-unchecked",
      "-deprecation",
      "-Xlint",
      "-language:_",
      "-target:jvm-1.6",
      "-Xlog-reflective-calls"),

    scalacOptions in Test ++= Seq("-Yrangepos"),

    ivyScala := ivyScala.value.map { _.copy(overrideScalaVersion = true) },
    scroogeThriftDependencies in Compile := Seq("finatra-thrift_2.11"),

    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "org.globalnames.matcher",

    javaOptions ++= Seq(
      "-Dlog.service.output=/dev/stderr",
      "-Dlog.access.output=/dev/stderr")
  )

  val publishingSettings = Seq(
    publishMavenStyle := true,
    useGpg := true,
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
      else                  Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    pomIncludeRepository := { _ => false },
    pomExtra :=
      <scm>
        <url>git@github.com:GlobalNamesArchitecture/gnmicroservices.git</url>
        <connection>scm:git:git@github.com:GlobalNamesArchitecture/gnmicroservices.git</connection>
      </scm>
        <developers>
          <developer>
            <id>dimus</id>
            <name>Dmitry Mozzherin</name>
          </developer>
          <developer>
            <id>alexander-myltsev</id>
            <name>Alexander Myltsev</name>
            <url>http://myltsev.com</url>
          </developer>
        </developers>
  )

  val noPublishingSettings = Seq(
    publishArtifact := false,
    publishTo := Some(Resolver.file("Unused transient repository", file("target/unusedrepo"))))

  lazy val testSettings = Seq(
    fork in Test := false,
    parallelExecution in Test := false
  )

  lazy val itSettings = Defaults.itSettings ++ Seq(
    logBuffered in IntegrationTest := false,
    fork in IntegrationTest := true
  )

  lazy val idlSettings = Seq(
    scroogeThriftDependencies in Compile := Seq("finatra-thrift_2.11")
  )

  lazy val indexGraphqlSettings = Seq(
    assemblyJarName in assembly := "microservices-" + version.value + ".jar",
    test in assembly := {},
    target in assembly := file(baseDirectory.value + "/../bin/"),
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(
      includeScala = false,
      includeDependency = true),
    assemblyMergeStrategy in assembly := {
      case "logback.xml" => MergeStrategy.last
      case PathList("META-INF", xs@_*) => MergeStrategy.discard
      case n if n.startsWith("reference.conf") => MergeStrategy.concat
      case _ => MergeStrategy.first
    }
  )

  lazy val indexSettings = Seq()

  lazy val matcherSettings = Seq()

}