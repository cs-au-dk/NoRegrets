lazy val couchdb = Project("couchdb-scala", file("couchdb-scala"))
  .settings(name := "couchdb-scala")
  .settings(Settings.common: _*)
  .settings(publish := (), publishLocal := ())

/**
  * Server project, generating the cli and the docker for the server
  */
lazy val backend = Project("backend", file("backend"))
  .settings(
    name := "backend",
    mainClass in Compile := Some("backend.ClusterMain"))
  .settings(Settings.common: _*)
//  .settings(ServerDocker.settings)
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .dependsOn(couchdb)

lazy val typeRegression =
  Project("type-regression", file(".")).settings(Settings.common: _*).aggregate(backend)

// Enable to use Ctrl+C to kill the current run in the sbt repl
// *without* exiting sbt
cancelable in Global := true