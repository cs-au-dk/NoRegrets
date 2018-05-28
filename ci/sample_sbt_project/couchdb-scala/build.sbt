organization := "com.ibm"

name := "couchdb-scala"

version := "0.8.0-SNAPSHOT"

scalaVersion := "2.11.12"

description := "A purely functional Scala client for CouchDB"

homepage := Some(url("https://github.com/beloglazov/couchdb-scala"))

licenses := Seq(
  "The Apache Software License, Version 2.0"
    -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")
)

libraryDependencies ++= Seq(
  "org.scalaz" %% "scalaz-core" % "7.2.8",
  "org.scalaz" %% "scalaz-effect" % "7.2.8",
  "org.http4s" %% "http4s-core" % "0.15.4a",
  "org.http4s" %% "http4s-client" % "0.15.4a",
  "org.http4s" %% "http4s-blaze-client" % "0.15.4a",
  "com.lihaoyi" %% "upickle" % "0.4.3",
  "com.github.julien-truffaut" %% "monocle-core" % "1.4.0",
  "com.github.julien-truffaut" %% "monocle-macro" % "1.4.0",
  "org.log4s" %% "log4s" % "1.3.0",
  "ch.qos.logback" % "logback-classic" % "1.1.7",
  "org.specs2" %% "specs2" % "3.7" % "test",
  "org.typelevel" %% "scalaz-specs2" % "0.3.0" % "test",
  "org.scalacheck" %% "scalacheck" % "1.13.0" % "test",
  "org.scalaz" %% "scalaz-scalacheck-binding" % "7.2.1" % "test",
  "org.scala-lang.modules" % "scala-java8-compat_2.11" % "0.8.0"
)

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-unchecked",
  "-Xfatal-warnings",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Xfuture",
  "-Ywarn-unused-import"
)

scalacOptions in (Compile, console) ~= (_ filterNot (List("-Ywarn-unused-import", "-Xfatal-warnings").contains(_)))

testFrameworks := Seq(TestFrameworks.Specs2, TestFrameworks.ScalaCheck)

unmanagedSourceDirectories in Compile += baseDirectory.value / "examples" / "src" / "main" / "scala"

initialCommands in console := "import scalaz._, Scalaz._, com.ibm.couchdb._"

initialCommands in console in Test := "import scalaz._, Scalaz._, scalacheck.ScalazProperties._, " +
  "scalacheck.ScalazArbitrary._,scalacheck.ScalaCheckBinding._"

logBuffered := false
