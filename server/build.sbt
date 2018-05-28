libraryDependencies += "com.lihaoyi" %% "pprint" % "0.4.3"
libraryDependencies += "com.github.scopt" % "scopt_2.11" % "3.6.0"
libraryDependencies += "org.scalactic" %% "scalactic" % "3.0.1"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test"
libraryDependencies += "com.typesafe.play" % "twirl-compiler_2.11" % "1.3.2"
libraryDependencies += "junit" % "junit" % "4.10" % "test"
//libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test"
libraryDependencies += "commons-io" % "commons-io" % "2.5"
libraryDependencies += "com.github.zafarkhaja" % "java-semver" % "0.9.0"
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.16"
libraryDependencies += "com.typesafe.akka" %% "akka-cluster" % "2.4.16"
// libraryDependencies += "com.vdurmont" % "semver4j" % "2.0.1" // waiting for patches
libraryDependencies += "com.github.ben-manes.caffeine" % "caffeine" % "2.3.5"
libraryDependencies += "com.google.code.findbugs" % "jsr305" % "3.0.1"
libraryDependencies += "com.lihaoyi" %% "upickle" % "0.4.3"
libraryDependencies += "com.typesafe.akka" % "akka-http-core_2.11" % "10.0.3"
libraryDependencies += "org.json4s" %% "json4s-native" % "3.5.2"
libraryDependencies += "org.specs2" %% "specs2-core" % "3.8.8" % "test"
libraryDependencies += "org.http4s" % "http4s-dsl_2.11" % "0.15.16a"
libraryDependencies += "org.eclipse.jgit" % "org.eclipse.jgit" % "4.6.0.201612231935-r"
resolvers += "Eclipse Releases" at "https://repo.eclipse.org/content/groups/releases/"
libraryDependencies += "org.eclipse.mylyn.github" % "org.eclipse.egit.github.core" % "4.6.0.201612231935-r"
libraryDependencies += "com.typesafe" % "config" % "1.3.1"
libraryDependencies += "com.google.code.gson" % "gson" % "2.8.0"
libraryDependencies += "com.spotify" % "docker-client" % "8.8.0"
libraryDependencies += "me.tongfei" % "progressbar" % "0.5.5"
libraryDependencies += "org.pegdown" % "pegdown" % "1.6.0"
libraryDependencies += "com.github.jnr" % "jnr-posix" % "3.0.41"
libraryDependencies += "org.zeroturnaround" % "zt-zip" % "1.12"
libraryDependencies += "org.scalaj" %% "scalaj-http" % "2.3.0"

// Spec2 option
scalacOptions in Test ++= Seq("-Yrangepos")
libraryDependencies += "de.ruedigermoeller" % "fst" % "2.40"

// forking in tests to enable us to use custom JVM memory settings
fork in Test := true
// Making sure that tests in this projects are running as if the working directory is the global project folder
baseDirectory in Test := file(".")

// no tests when assembling
test in assembly := {}

// testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-C", "OurReporter")
testOptions in Test ++= Seq(
  Tests.Argument(TestFrameworks.ScalaTest, "-o"),
  Tests.Argument(TestFrameworks.ScalaTest, "-h", "out/test-reports"))

// avoiding deduplication errors
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _ *) => MergeStrategy.discard
  case x => MergeStrategy.first
}

// testForkedParallel in Test := true
scalafmtOnCompile := true

parallelExecution in Test := true
