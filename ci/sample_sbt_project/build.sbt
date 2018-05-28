lazy val couchdb = Project("couchdb-scala", file("couchdb-scala"))
  .settings(name := "couchdb-scala")
  .settings(Settings.common: _*)
  .settings(publish := (), publishLocal := ())
/*
lazy val worker = Project("distilledtests-worker", file("server"))
  .settings(
    name := "distilledtests-worker",
    mainClass in Compile := Some("distilling.worker.NpmRunnerWorker"),
    target := file("build-sub/worker")
  )
  .settings(Settings.common: _*)
  .settings(WorkerDocker.settings)
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .dependsOn(couchdb)

lazy val testWorker = Project("distilledtests-test-worker", file("server"))
  .settings(
    name := "distilledtests-test-worker",
    mainClass in Compile := Some("distilling.worker.LocalTestingMain"),
    target := file("build-sub/test-worker")
  )
  .settings(Settings.common: _*)
  .settings(WorkerDocker.settings)
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .dependsOn(couchdb)

lazy val testWorkerNoServer = Project("distilledtests-test-worker-no-server", file("server"))
  .settings(
    name := "distilledtests-test-worker-no-server",
    mainClass in Compile := Some("distilling.worker.LocalTestingMain"),
    target := file("build-sub/local-worker")
  )
  .settings(Settings.common: _*)
  .settings(WorkerDocker.settings)
  .settings(NoServer.settings)
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .dependsOn(couchdb)
 */

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

/*lazy val dockerForBenchmarks =
  Project("distilledtests-benchmarks-docker", file("server"))
    .settings(
      name := "distilledtests-benchmarks-docker",
      mainClass in Compile := Some("distilling.server.ClusterMain"),
      target := file("build-sub/benchmarks-docker")
    )
    .settings(Settings.common: _*)
    .settings(BenchmarksDocker.settings)
    .enablePlugins(JavaAppPackaging, DockerPlugin)
    .dependsOn(couchdb)
 */

/*
lazy val githubCrawler = Project("github-crawler", file("server"))
  .settings(
    name := "github-crawler",
    mainClass in Compile := Some("distilling.github_crawler.Crawler"),
    target := file("build-sub/crawler")
  )
  .settings(Settings.common: _*)
  .enablePlugins(JavaAppPackaging)
  .dependsOn(couchdb)

lazy val multiDocker = Project("multi-docker", file("."))
  .settings(Settings.common: _*)
  .aggregate(worker, server, testWorker, githubCrawler, couchdb, testWorkerNoServer)
 */
lazy val multiDocker =
  Project("multi-docker", file(".")).settings(Settings.common: _*).aggregate(server)

// Enable to use Ctrl+C to kill the current run in the sbt repl
// *without* exiting sbt
cancelable in Global := true