package backend.commands

import java.nio.file._

import backend._
import backend.commands.ClientPriority.ClientPriority
import backend.commands.Common._
import backend.commands.RunTests.RunTestsOptions
import backend.commands.Successfuls.SuccessfulOptions
import backend.commands.benchmarking.BlacklistedClients
import backend.datastructures._
import backend.utils._
import scopt.OptionDef

import scala.language.implicitConversions
import scala.util._

case class Successfuls(successfulOptions: SuccessfulOptions) {
  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)
  implicit val executor = ExecutionUtils.rightExecutor(Globals.benchmarksImage)

  val forLibrary =
    PackageAtVersion(successfulOptions.withLibrary, successfulOptions.atVersion)

  def handleSuccessfulsCmd(): List[PackageAtVersion] = {

    val selectedPackages = successfulOptions.packages match {
      case Right(file) =>
        SerializerFormats.defaultSerializer.deserialize[List[PackageAtVersion]](file)
      case Left(list) => list
    }

    log.info(s"Testing ${selectedPackages.size} packages")

    val result = DiskCaching.cache(
      executeSuccessfulsCmd(
        if (successfulOptions.limit > 0) selectedPackages.take(successfulOptions.limit)
        else selectedPackages),
      CommandCachePolicy.getMatchingCachePolicy(successfulOptions.commandCachePolicy),
      successfulOptions.toCacheKey)

    if (successfulOptions.commandCachePolicy == CommandCachePolicy.VERIFY_PRESENCE_AND_CONTENT_OF_DATA) {
      val successful = executeSuccessfulsCmd(result)
      val preSet = result.toSet
      val postSet = successful.toSet
      if (preSet != postSet) {
        throw CommandCachePolicy.DataContentVerificationFailure(
          s"Some packages in the cache are no longer successful:\n  ${preSet.diff(postSet).mkString("\n  ")}")
      }
    }
    result
  }

  def executeSuccessfulsCmd(packages: List[PackageAtVersion]): List[PackageAtVersion] = {

    log.info(s"Running successfuls with options $packages")
    val filteredPackages = packages.toSet -- BlacklistedClients.blacklistedClients(
      forLibrary.packageName)

    val parallelPackages = filteredPackages.par

    if (successfulOptions.swarm) {
      parallelPackages.tasksupport = Globals.swarmTaskSupport
    } else {
      parallelPackages.tasksupport = Globals.successfulsLocalTaskSupport
    }

    val result = parallelPackages
      .map { client =>
        processClient(client)
      }
      .seq
      .collect { case Success(v) => v._1 }
      .toList

    result
  }

  def processClient(
    client: PackageAtVersion): Try[(PackageAtVersion, ProcessExecutionResult)] = {
    val attempt = Try {
      val opts =
        RunTestsOptions(
          client,
          forLibrary,
          Paths.get("out-noregrets").resolve("plain-tests"),
          ignoreTags = successfulOptions.ignoreTagsMode)

      val testResult = Common
        .cmdHandler[ProcessExecutionResult](Config(Some(opts), successfulOptions.swarm))
        .get
        .asInstanceOf[ProcessExecutionResult]

      if (testResult.code != 0)
        throw new RuntimeException("Clone or test failed")
      (client, testResult)
    }
    attempt.recoverWith {
      case error: Throwable =>
        log.error(s"""
                     |Failed clone and run tests of $client:
                     |   $error
                     |""".stripMargin)
        //error.printStackTrace()
        Failure(error)
    }
    attempt
  }
}

object Successfuls {

  object SuccessfulOptions {
    def make(parser: scopt.OptionParser[Config]): Seq[OptionDef[_, Common.Config]] = {

      implicit def asDep(cmd: Option[Common.CommandOptions]): SuccessfulOptions =
        cmd.get.asInstanceOf[SuccessfulOptions]

      Seq(
        parser
          .arg[String]("package-name")
          .action((x, c) => c.copy(cmd = Some(c.cmd.copy(withLibrary = x))))
          .text("name of a specific library to change version"),
        parser
          .arg[String]("version")
          .action((x, c) => c.copy(cmd = Some(c.cmd.copy(atVersion = x))))
          .text("version of the specific library desired"),
        parser
          .opt[Unit]("regenerate-cache")
          .action((x, c) =>
            c.copy(cmd =
              Some(c.cmd.copy(commandCachePolicy = CommandCachePolicy.REGENERATE_DATA))))
          .text("regenerate the successful cache"),
        parser
          .opt[Int]("limit")
          .action((x, c) => c.copy(cmd = Some(c.cmd.copy(limit = x))))
          .text("limit"),
        parser
          .opt[String]("packages-file")
          .action(
            (x, c) => c.copy(cmd = Some(c.cmd.copy(packages = Right(Paths.get(x))))))
          .text("package file"))
    }
  }

  case class SuccessfulOptions(
    withLibrary: String = null,
    atVersion: String = null,
    packages: Either[List[PackageAtVersion], Path] = null,
    limit: Int = -1,
    swarm: Boolean = false,
    //We have both NoRegretsPlusMode and IgnoreTagsMode since we want the possibility of not ignoring tags always in NoRegretsPlus
    NoRegretsPlusMode: Boolean = false,
    ignoreTagsMode: Boolean = false,
    clientPriority: ClientPriority = ClientPriority.OnlyOldest,
    commandCachePolicy: CommandCachePolicy.Value =
      CommandCachePolicy.USE_DATA_IF_PRESENT_REGENERATE_OTHERWISE)
      extends CommandOptions {

    override def toCacheKey = {
      val cacheList = List(
        "successful",
        if (NoRegretsPlusMode) "NoRegrets2" else "",
        if (ignoreTagsMode) "IgnoreTags" else "",
        withLibrary,
        atVersion,
        "limit",
        if (limit >= 0) limit.toString else "all",
        packages.fold(
          list => list.map(_.hashCode().toLong).sum.toHexString,
          path => path.relativize(Paths.get("")).toString))

      // Note, we should use a separate identifier for only-oldest and all as well
      // However, the default for a long time
      // was not to include the client priority identifier in the successfuls cache key.
      // So to stay compatible with old cache files, we use the convention that no identifier means only-oldest.
      if (clientPriority == ClientPriority.OnlyNewest) {
        cacheList ++ List("only-newest")
      } else {
        cacheList
      }
    }
  }

}
