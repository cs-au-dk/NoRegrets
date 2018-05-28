package distilling.server.commands.benchmarking

import distilling.server.datastructures._
import distilling.server.utils.NotationalUtils._
import distilling.server.utils._

import scala.util.Try

object BlacklistedClients {

  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)

  private val manuallyExcludedClients: Set[PackageAtVersion] =
    Set(
      "clustermq@1.1.0", // don't remember which was the problem
      "apper@2.0.0", // tests don't terminate
      "express-endpoint@2.0.0", // gives variable number of observations, unreliable
      "retell@1.0.0", // tests don't terminate
      "machina@1.0.0" // tests don't terminate
    )

  private val key: List[String] = List("blacklisted", "clients")

  lazy val cachedBlacklist: Map[String, List[PackageAtVersion]] =
    Try(
      SerializerFormats.defaultSerializer
        .deserialize[Map[String, List[PackageAtVersion]]](
          DiskCaching.getPathFromKeys(key))).toOption.getOrElse(Map())

  def add(libraryName: String, blacklisted: Set[PackageAtVersion]): Unit = {
    this.synchronized {
      val saved: Map[String, List[PackageAtVersion]] =
        Try(
          SerializerFormats.defaultSerializer
            .deserialize[Map[String, List[PackageAtVersion]]](
              DiskCaching.getPathFromKeys(key))).toOption.getOrElse(Map())

      val updated =
        saved.updated(
          libraryName,
          (saved.getOrElse(libraryName.toString, List()) ++ blacklisted).distinct)

      SerializerFormats.defaultSerializer
        .serialize(updated, DiskCaching.getPathFromKeys(key))

      log.info(s"Updated blacklist for $libraryName")
    }
  }

  def blacklistedClients(libraryName: String): Set[PackageAtVersion] = {
    val bb = cachedBlacklist.getOrElse(libraryName, List())
    (bb ++ manuallyExcludedClients).toSet
  }

}
