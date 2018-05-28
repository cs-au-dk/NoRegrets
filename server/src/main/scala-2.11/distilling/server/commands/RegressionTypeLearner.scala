package distilling.server.commands

import java.nio.file._

import distilling.server._
import distilling.server.commands.ApiTrace.ApiTraceOptions
import distilling.server.commands.Common._
import distilling.server.commands.benchmarking.BlacklistedClients
import distilling.server.datastructures._
import distilling.server.package_handling._
import distilling.server.regression_typechecking._
import distilling.server.utils.DiskCaching.CacheBehaviour
import distilling.server.utils.NotationalUtils.SI
import distilling.server.utils.Utils._
import distilling.server.utils._
import scopt.OptionDef

import scala.collection.mutable
import scala.language.implicitConversions
import scala.util.{Either, Left, Right, Try}
import Utils._

case class RegressionTypeLearner(options: RegressionTypeLearnerOptions) {

  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)
  private implicit val executor: ExecutionUtils.ProcExecutor =
    ExecutionUtils.rightExecutor(Globals.benchmarksImage)
  private implicit val formats = SerializerFormats.commonSerializationFormats

  private val MAX_REGRESSION_FILE_SIZE: Double = 50.mega
  private val outDir: Path = options.outDir.toAbsolutePath
  private val libraryToTraceOutdir: Path =
    outDir.resolve(
      s"${options.libraryToCheckName}-${options.libraryVersions.head}-to-${options.libraryVersions.last}")
  private val clientsWithTestsOutdir: Path = outDir.resolve("_clients")

  private val status = mutable.Map[PackageAtVersion, (PackageAtVersion, String)]()
  val registry = new RegistryReader().loadLazyNpmRegistry()

  libraryToTraceOutdir.toFile.mkdirs()
  clientsWithTestsOutdir.toFile.mkdirs()

  def handleRegressionTypeLearner(): RegressionResult = {

    log.separationTitle(
      s"Regression Type Testing on ${options.libraryToCheckName}@${options.libraryVersions.headOption}")
    log.info(s"Running $ApiTrace with options: ${pprint.stringify(options)}")

    val first = options.libraryVersions.head

    PackageHandlingUtils.tscAll

    val clients = options.clients match {
      case Left(list) => list
      case Right(path) =>
        JsonSerializer().deserialize[List[PackageAtVersion]](path)
    }

    if (clients.isEmpty) {
      log.info(s"Skipping library ${options.libraryToCheckName} due to missing clients")
      throw NoClients()
    }

    val allowedToRunClients =
      clients.filter(pkg => !options.doNotRun.contains(pkg))

    if (allowedToRunClients.isEmpty)
      throw new RuntimeException("No clients to perform regression type checking")

    log.info(
      s"Beginning regression type checking, clients: ${allowedToRunClients.size}, library versions: ${options.libraryVersions.size} with options:\n$options")

    val clientsToLearnFrom = allowedToRunClients.filter(!options.doNotRun.contains(_)).par

    // If we are swarming, then increse the parallelism level
    if (options.swarm) {
      clientsToLearnFrom.tasksupport = Globals.swarmTaskSupport
    } else {
      clientsToLearnFrom.tasksupport = Globals.localTaskSupport
    }

    var learned = clientsToLearnFrom
      .mapBy {
        learnAndCheckWithClient
      }
      .seq
      .toMap

    log.debug("Running killall node to clean unstopped garbage processes")
    executor.executeCommand(List("killall", "node"))(Paths.get(""))

    // Blacklist library clients that give us no observations
    val blacklistedClients = learned.filter {
      case (client, libToresult) =>
        libToresult.values.forall {
          case l: Learned => l.observations.isEmpty
          case _          => false // if it fails we have to look at it
        }
    }.keys
    BlacklistedClients.add(options.libraryToCheckName, blacklistedClients.toSet)

    log.info(s"Now computing type regressions")

    val smartGlobalLearned = if (options.useSmartDiff) {
      Aggregator.augmentObservationsSmartly(learned)
    } else {
      learned
    }

    // Save the diff for the entire library
    val diffK = "diff" :: List(
      options.libraryToCheckName.toString,
      options.libraryVersions.head.toString,
      options.libraryVersions.last.toString)
    val diff = DiskCaching.cache(
      Aggregator
        .aggregate(smartGlobalLearned)
        .diffs(options.typingRelation)
        .map(p => (p._1.toString, p._2)),
      CacheBehaviour.REGENERATE,
      diffK,
      serializer = SerializerFormats.defaultSerializer)

    val totalClientCount = smartGlobalLearned.keys.toSet.size
    val totalObservationCount = smartGlobalLearned.values
      .foldLeft(List[Observation]()) {
        case (aggClient, mapTRes) => {
          aggClient ++ mapTRes.values.foldLeft(List[Observation]()) {
            case (aggLib, tRes) => {
              aggLib ++ (tRes match {
                case l: Learned         => l.observations
                case l: LearningFailure => l.observations
              })
            }
          }
        }
      }
      .toSet
      .size

    val diffInfo = DiffInfo(
      diffK.mkString("-"),
      diff.values.foldLeft[Int](0) { case (res, kD) => res + kD.diffObservationCount },
      diff.values.flatMap(_.diffClientCount).toSet.size,
      totalClientCount,
      totalObservationCount)

    BenchmarksStatus.default.addDiff(
      PackageAtVersion(options.libraryToCheckName, options.libraryVersions.head),
      diffInfo)

    // Save the diff for each client
    if (options.computeDiffForEachClient) {
      learned.map {
        case (client, libObs) =>
          val clientDiffs =
            Aggregator.aggregate(Map(client -> libObs)).diffs(options.typingRelation)
          val diffK = "diffClient" :: List(
            client.packageName,
            client.packageVersion,
            options.libraryToCheckName)

          DiskCaching.cache(
            clientDiffs.map(p => (p._1.toString, p._2)),
            CacheBehaviour.REGENERATE,
            diffK,
            serializer = SerializerFormats.defaultSerializer)

      }
    }

    RegressionResult(learned, diff)
  }

  private def learnAndGrabResult(client: PackageAtVersion,
                                 library: PackageAtVersion): TracingResult = {
    val opts =
      ApiTraceOptions(
        client,
        library,
        clientsWithTestsOutdir,
        libraryToTraceOutdir,
        collectStackTraces = options.collectStackTraces,
        detailedStackTraces = options.detailedStackTraces,
        ignoreFailingInstallations = options.ignoreFailingInstallations)

    Try {
      val res = Common
        .cmdHandler[TracingResult](Config(Some(opts), options.swarm))
        .get
        .asInstanceOf[TracingResult]
      log.info(s"Successfully loaded tracing result: ${res.getClass}")
      res
    }.recover {
      case e: Throwable =>
        LearningFailure(
          "Failure while running kue job: " + e + "\n" + e.getStackTrace.mkString("\n"))
    }.get

  }

  private def learnAndCheckWithClient(
    client: PackageAtVersion): Map[PackageAtVersion, TracingResult] = {

    // compiling the tracer project
    PackageHandlingUtils.tscAll

    log.separationTitle(
      s"Entering learning phase for ${options.libraryVersions} against client $client")

    val libraryVersions =
      options.libraryVersions.map(PackageAtVersion(options.libraryToCheckName, _))

    // learning using the client for every library update.
    libraryVersions.mapBy { libraryVersionToCheck =>
      // Clone the package locally, useful to save caches even when regenerating the benchmark status
      nowRunning(client, libraryVersionToCheck, "cloning")
      try {
        PackageHandlingUtils.clonePackageNoRetry(
          clientsWithTestsOutdir,
          registry,
          client,
          true)
      } catch {
        case _: Throwable =>
      }

      val learnKey = "learn" :: List(libraryVersionToCheck.toString, client.toString)
      val result = DiskCaching.cache(
        {
          if (options.neverRun) {
            LearningFailure(
              "Cache file was not present and received request to avoid running")
          } else {
            nowRunning(client, libraryVersionToCheck, "tracing")
            val result = learnAndGrabResult(client, libraryVersionToCheck)
            result
          }
        },
        CommandCachePolicy.getMatchingCachePolicy(options.learningCommandCachePolicy),
        learnKey,
        serializer = SerializerFormats.defaultSerializer,
        threadedLog = true,
        rerunCondition = { learned: TracingResult =>
          options.rerunFailedLearning && learned.isInstanceOf[LearningFailure]
        })

      doneRunning(client, libraryVersionToCheck)

      result match {
        case learned: Learned =>
          BenchmarksStatus.default.addStatus(
            libraryVersionToCheck,
            client,
            Status(
              "Tests succeeded, learned can be loaded",
              learnKey,
              learned.observations.length))
        case err: LearningFailure =>
          BenchmarksStatus.default.addStatus(
            libraryVersionToCheck,
            client,
            Status(err.msg, learnKey, err.observations.length))
      }

      result
    }
  }

  private def nowRunning(client: PackageAtVersion,
                         library: PackageAtVersion,
                         phase: String): Unit = {
    synchronized {
      status += client -> (library, phase)
      dumpStatus()
    }
  }

  private def doneRunning(client: PackageAtVersion, library: PackageAtVersion): Unit = {
    synchronized {
      status -= client
      dumpStatus()
    }
  }

  private def dumpStatus(): Unit = {
    val out = options.outDir.resolve("status.txt")
    writeToFile(out, s"$options\n\n${status.mkString("\n")}", silent = true)
  }
}

case class RegressionResult(
  learned: Map[PackageAtVersion, Map[PackageAtVersion, TracingResult]],
  diff: Map[String, KnowledgeDiff])

case class NoClients() extends RuntimeException

object RegressionTypeLearnerOptions {
  def make(parser: scopt.OptionParser[Config]): Seq[OptionDef[_, Common.Config]] = {

    implicit def asRegOpt(
      cmd: Option[Common.CommandOptions]): RegressionTypeLearnerOptions =
      cmd.get.asInstanceOf[RegressionTypeLearnerOptions]

    Seq(
      parser
        .arg[String]("library")
        .action((x, c) => c.copy(cmd = Some(c.cmd.copy(libraryToCheckName = x))))
        .text("library name"),
      parser
        .arg[String]("version")
        .unbounded()
        .action((x, c) =>
          c.copy(cmd = Some(c.cmd.copy(libraryVersions = x :: c.cmd.libraryVersions))))
        .text("library version"),
      parser
        .opt[Unit]("regenerate-cache")
        .action((x, c) =>
          c.copy(cmd = Some(
            c.cmd.copy(learningCommandCachePolicy = CommandCachePolicy.REGENERATE_DATA))))
        .text("file with packages to run trace"),
      parser
        .opt[String]("packages-file")
        .action((x, c) => c.copy(cmd = Some(c.cmd.copy(clients = Right(Paths.get(x))))))
        .text("file with packages to run trace"))
  }
}

case class SoundnessOfLearnedTypeFailed(message: String)
    extends RuntimeException(s"$message")

case class RegressionTypeLearnerOptions(
  libraryToCheckName: String = null,
  libraryVersions: List[String] = List(),
  outDir: Path = Paths.get("out/regression-tc"),
  computeDiffForEachClient: Boolean = true,
  clients: Either[List[PackageAtVersion], Path] = null,
  doNotRun: Set[PackageAtVersion] = Set(),
  neverRun: Boolean = false,
  rerunFailedLearning: Boolean = true,
  collectStackTraces: Boolean = true,
  detailedStackTraces: Boolean = false,
  ignoreFailingInstallations: Boolean = false,
  swarm: Boolean = false,
  generateBlacklist: Boolean = true,
  useSmartDiff: Boolean = true,
  learningCommandCachePolicy: CommandCachePolicy.Value =
    CommandCachePolicy.USE_DATA_IF_PRESENT_REGENERATE_OTHERWISE,
  typingRelation: TypingRelation = TypeRegressionPaperTypingRelation)
    extends CommandOptions {}
