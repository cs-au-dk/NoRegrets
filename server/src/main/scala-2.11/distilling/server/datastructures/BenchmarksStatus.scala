package distilling.server.datastructures

import distilling.server.utils.DiskCaching

object BenchmarksStatus {
  val key = List("benchmark-status")

  private def load(): BenchmarksStatus = {
    DiskCaching.cache(
      BenchmarksStatus(Map(), Map(), Map()),
      DiskCaching.CacheBehaviour.USE_IF_EXISTS_GENERATE_OTHERWISE,
      key,
      silent = true)
  }

  lazy val default: BenchmarksStatus = load()
}

/**
  * Map the library -> to client -> to the execution status
  */
case class BenchmarksStatus(var observations: Map[String, Map[String, Status]],
                            var diffKeys: Map[String, DiffInfo],
                            var syntheticData: Map[String, Int]) {

  private def generateSyntheticData(): Unit = {
    val allStatuses = observations.values.flatMap(_.values)
    syntheticData = Map(
      "failed" -> allStatuses.count(_.short.contains("Tests failed")),
      "not run" -> allStatuses.count(
        _.short.contains(
          "Cache file was not present and received request to avoid running")),
      "succeded" -> allStatuses.count(
        _.short.contains("Tests succeeded, learned can be loaded")),
      "error reading outcome" -> allStatuses.count(
        _.short.contains("Tests succeed but got error reading learned outcome")),
      "kue failure" -> allStatuses.count(
        _.short.contains("Failure while running kue job")))
  }

  private lazy val ensureRegistration: Unit = {
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run(): Unit = {
        try {
          generateSyntheticData()
          BenchmarksStatus.default.dumpStatus()
        } catch {
          case e: Throwable => println(e)
        }
      }
    })
  }

  def addStatus(library: PackageAtVersion,
                client: PackageAtVersion,
                status: Status): Unit = {
    this.synchronized {
      val m = observations.getOrElse(library.toString, Map())
      val mup = m.updated(client.toString, status)
      observations = observations.updated(library.toString, mup)
      ensureRegistration
    }
  }

  def addDiff(startLib: PackageAtVersion, info: DiffInfo): Unit = {
    this.synchronized {
      diffKeys = diffKeys.updated(startLib.toString, info)
      ensureRegistration
    }
  }

  private def dumpStatus(): Unit = {
    DiskCaching.cache(
      this,
      DiskCaching.CacheBehaviour.REGENERATE,
      BenchmarksStatus.key,
      threadedLog = true,
      silent = true)
  }
}

case class DiffInfo(key: String, diffObservationCount: Int, diffClientCount: Int, totalClientCount: Int, totalObservationCount: Int)

case class Status(short: String, cacheFileKey: List[String], observations: Int = 0)
