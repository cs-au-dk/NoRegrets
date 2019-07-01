package backend.datastructures

import backend.utils.DiskCaching

object BenchmarksStatus {
  val key = List("benchmark-status")

  private def load(): BenchmarksStatus = {
    this.synchronized {
      DiskCaching.cache(
      BenchmarksStatus(Map(), Map(), Map()),
      DiskCaching.CacheBehaviour.USE_IF_EXISTS_GENERATE_OTHERWISE,
      key,
      silent = true)
    }
  }

  lazy val default: BenchmarksStatus = load()
}

/**
  * Map the library -> to client -> to the execution status
  */
case class BenchmarksStatus(var observations: Map[String, Map[String, Status]],
                            var diffKeys: Map[String, BenchmarkStatus],
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

  def addDiff(benchName: String, info: BenchmarkStatus): Unit = {
    this.synchronized {
      diffKeys = diffKeys.updated(benchName, info)
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

case class BenchmarkStatus(key: String, // e.g., async@2.0.0 - NoRegrets1 - only-oldest
                           name: String, //library@version
                           pathCountInDiff: Int,
                           clientCountInDiff: Int,
                           totalClientCount: Int,
                           totalPathCount: Int,
                           totalPathsCovered: Int,
                           minorPatchUpdates: Int,
                           majorUpdates: Int,
                           tool: String,
                           withCoverage: Boolean,
                           withIgnoreTags: Boolean)

case class Status(short: String, cacheFileKey: List[String], observations: Int = 0)
