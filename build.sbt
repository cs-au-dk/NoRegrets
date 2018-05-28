lazy val couchdb = Project("couchdb-scala", file("couchdb-scala"))
  .settings(name := "couchdb-scala")
  .settings(Settings.common: _*)
  .settings(publish := (), publishLocal := ())

/**
  * Server project, generating the cli and the docker for the server
  */
lazy val server = Project("distilledtests-server", file("server"))
  .settings(
    name := "distilledtests-server",
    mainClass in Compile := Some("distilling.server.ClusterMain"))
  .settings(Settings.common: _*)
  .settings(ServerDocker.settings)
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .dependsOn(couchdb)

lazy val multiDocker =
  Project("multi-docker", file(".")).settings(Settings.common: _*).aggregate(server)

// Enable to use Ctrl+C to kill the current run in the sbt repl
// *without* exiting sbt
cancelable in Global := true
