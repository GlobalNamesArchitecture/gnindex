// Slick code generation
// https://github.com/tototoshi/sbt-slick-codegen
lazy val pluginCrossBuild = uri("https://github.com/tototoshi/sbt-slick-codegen.git#c30c3dea84399402dfeef44d8cff1d33bbcc0984")

lazy val root = (project in file(".")).dependsOn(pluginCrossBuild)

libraryDependencies ++= Seq(
  "org.postgresql" % "postgresql" % "9.4-1206-jdbc42"
)
