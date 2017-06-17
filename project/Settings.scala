import sbt._
import Keys._
import sbtassembly.AssemblyPlugin.autoImport._
import com.typesafe.sbt.SbtPgp.autoImport._
import com.twitter.scrooge.ScroogeSBT.autoImport._
import sbtbuildinfo.BuildInfoKey
import sbtbuildinfo.BuildInfoKeys._
import slick.codegen.SourceCodeGenerator
import slick.{ model => m }
import com.github.tototoshi.sbt.slick.CodegenPlugin._
import spray.revolver.RevolverPlugin.autoImport._

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

    test in assembly := {},
    target in assembly := file(baseDirectory.value + "/../bin/"),
    assemblyMergeStrategy in assembly := {
      case "logback.xml" => MergeStrategy.last
      case PathList("META-INF", xs@_*) => MergeStrategy.discard
      case n if n.startsWith("reference.conf") => MergeStrategy.concat
      case _ => MergeStrategy.first
    },

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
    publishTo := Some(Resolver.file("Unused transient repository", file("target/unusedrepo")))
  )

  lazy val testSettings = Seq(
    fork in Test := false,
    parallelExecution in Test := false
  )

  lazy val itSettings = Defaults.itSettings ++ Seq(
    logBuffered in IntegrationTest := false,
    fork in IntegrationTest := true
  )

  lazy val commonSettings = Seq(
    scroogeThriftDependencies in Compile := Seq("finatra-thrift_2.11")
  )

  //////////////////
  // API settings //
  //////////////////
  lazy val apiSettings = Seq(
    assemblyJarName in assembly := "gnindexapi-" + version.value + ".jar"
  )

  ///////////////////////////
  // NameResolver settings //
  ///////////////////////////
  lazy val databaseUrl = {
    val host = sys.env.getOrElse("DB_HOST", "localhost")
    val port = sys.env.getOrElse("DB_PORT", "5432")
    val database = sys.env.getOrElse("DB_DATABASE", "development")
    s"jdbc:postgresql://$host:$port/$database"
  }
  lazy val databaseUser = sys.env.getOrElse("DB_USER", "postgres")
  lazy val databasePassword = sys.env.getOrElse("DB_USER_PASS", "")
  lazy val nameResolverSettings = slickCodegenSettings ++ Seq(
    assemblyJarName in assembly := "gnnameresolver-" + version.value + ".jar",

    slickCodegenDatabaseUrl := databaseUrl,
    slickCodegenDatabaseUser := databaseUser,
    slickCodegenDatabasePassword := databasePassword,
    slickCodegenDriver := slick.driver.PostgresDriver,
    slickCodegenJdbcDriver := "org.postgresql.Driver",
    slickCodegenOutputPackage := "org.globalnames.index.nameresolver.dao",
    slickCodegenExcludedTables := Seq("schema_version"),
    slickCodegenCodeGenerator := { (model:  m.Model) =>
      new SourceCodeGenerator(model) {
        override def code =
          s"""import com.github.tototoshi.slick.PostgresJodaSupport._
             |import org.joda.time.DateTime
             |${super.code}""".stripMargin
        override def Table = new Table(_) {
          override def Column = new Column(_) {
            override def rawType = model.tpe match {
              case "java.sql.Timestamp" => "DateTime" // kill j.s.Timestamp
              case _ => super.rawType
            }
          }
        }
      }
    },
    sourceGenerators in Compile += slickCodegen.taskValue,

    Revolver.enableDebugging(port = 5006, suspend = false)
  )

  //////////////////////
  // Matcher settings //
  //////////////////////
  lazy val matcherSettings = Seq(
    assemblyJarName in assembly := "gnmatcher-" + version.value + ".jar"
  )

}
