// Slick code generation
// https://github.com/tototoshi/sbt-slick-codegen
lazy val pluginCrossBuild = uri("https://github.com/tototoshi/sbt-slick-codegen.git#16813c65f59b2b2ce317eebbecc833d344937130")

lazy val root = (project in file(".")).dependsOn(pluginCrossBuild)

libraryDependencies ++= Seq(
  "org.postgresql" % "postgresql" % "9.4-1206-jdbc42"
)
