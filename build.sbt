ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.12"

lazy val core = (project in file("core"))
  .settings(
    name := "scala-dvvset",
    libraryDependencies += "org.scalactic" %% "scalactic" % "3.2.17",
    libraryDependencies +=
      "org.scalatest" %% "scalatest" % "3.2.17" % "test"
  )

lazy val demo = (project in file("demo"))
  .settings(
    name := "scala-dvvset",
    libraryDependencies += "org.scalactic" %% "scalactic" % "3.2.17",
    libraryDependencies +=
      "org.scalatest" %% "scalatest" % "3.2.17" % "test"
  ).dependsOn(core)

addCommandAlias("fmt", "; Compile / scalafmt; Test / scalafmt; scalafmtSbt")
