import java.nio.file.Paths

import backend.Globals
import backend.commands.ClientPriority.ClientPriority
import backend.commands.Dependents.DependentOptions
import backend.commands.Successfuls.SuccessfulOptions
import backend.commands._
import backend.datastructures._
import backend.package_handling.PackageHandlingUtils
import backend.regression_typechecking.{TypeRegressionPaperTypingRelation, TypingRelation}
import backend.utils._
import org.scalatest.concurrent.TimeLimits
import org.scalatest.time.Span

trait TestingUtils extends TimeLimits {

  val timeout: Span
  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)

  def findDependencies(libraryName: String, libraryVersion: String)(
    implicit mode: CommandCachePolicy.Value): List[PackageAtVersion] = {
    failAfter(timeout) {
      Dependents.handleDependentsCmd(
        DependentOptions(
          libraryName,
          Some(libraryVersion),
          limit = 1000,
          commandCachePolicy = mode))
    }
  }

  def findSuccessful(libraryName: String, libraryVersion: String)(
    implicit mode: CommandCachePolicy.Value): List[PackageAtVersion] = {
    failAfter(timeout) {
      val dependents = Dependents.handleDependentsCmd(
        DependentOptions(
          libraryName,
          Some(libraryVersion),
          limit = 1000,
          commandCachePolicy = CommandCachePolicy.VERIFY_PRESENCE_OF_DATA))

      val succOpt = SuccessfulOptions(
        libraryName,
        libraryVersion,
        packages = Left(dependents),
        commandCachePolicy = mode)
      new Successfuls(succOpt).handleSuccessfulsCmd()
    }
  }

  def fullCycleRegressionApproach(
    libraryName: String,
    libraryBeginVersion: String,
    libraryEndVersion: Option[String] = None,
    doNotRun: Set[PackageAtVersion] = Set(),
    rerunFailed: Boolean = true,
    neverRun: Boolean = false,
    swarm: Boolean = false,
    typingRelation: TypingRelation = TypeRegressionPaperTypingRelation,
    learningCommandCachePolicy: CommandCachePolicy.Value =
      CommandCachePolicy.USE_DATA_IF_PRESENT_REGENERATE_OTHERWISE,
    checkingCommandCachePolicy: CommandCachePolicy.Value =
      CommandCachePolicy.USE_DATA_IF_PRESENT_REGENERATE_OTHERWISE,
    generateStatistics: Boolean = false,
    NoRegretsPlusMode: Boolean = true,
    ignoreTagsMode: Boolean = false,
    enableValueChecking: Boolean = false,
    clientPriority: ClientPriority = ClientPriority.OnlyOldest,
    withCoverage: Boolean = false): Unit = {

    PackageHandlingUtils.deleteCacheFolderIfExceedingSizeLimit()
    PackageHandlingUtils.killPotentiallySpuriousProcesses(
      ExecutionUtils.rightExecutor(Globals.benchmarksImage))

    val dependents =
      Dependents.handleDependentsCmd(
        DependentOptions(
          libraryName,
          if (ignoreTagsMode) None else Some(libraryBeginVersion),
          limit = 2000,
          commandCachePolicy = CommandCachePolicy.VERIFY_PRESENCE_OF_DATA,
          NoRegretsPlusMode = NoRegretsPlusMode,
          clientPriority = clientPriority))

    log.info(s"Dependents retrieved for $libraryName@$libraryBeginVersion")

    val successfuls =
      Successfuls(
        SuccessfulOptions(
          libraryName,
          libraryBeginVersion,
          packages = Left(dependents),
          commandCachePolicy = CommandCachePolicy.VERIFY_PRESENCE_OF_DATA,
          NoRegretsPlusMode = NoRegretsPlusMode,
          ignoreTagsMode = ignoreTagsMode,
          clientPriority = clientPriority)).handleSuccessfulsCmd()

    log.info(s"Successfuls retrieved for $libraryName@$libraryBeginVersion")

    val baseCacheKey =
      List("full-cycle-regression-approach", libraryName, libraryBeginVersion)
    val libVersionsCacheKey = "lib-versions" :: baseCacheKey
    val libVersions = PackageHandlingUtils.getLibraryVersions(
      libraryName,
      CommandCachePolicy.USE_DATA_IF_PRESENT_REGENERATE_OTHERWISE,
      libVersionsCacheKey)

    log.info(s"Library versions retrieved for $libraryName@$libraryBeginVersion")

    val libBeginVersionSemver = Versions.toVersion(libraryBeginVersion) match {
      case Some(v) => v
      case None =>
        throw new RuntimeException(
          s"Unable to create SemverWithUnnormalized for $libraryName@$libraryBeginVersion")
    }
    val libEndVersionSemver = libraryEndVersion match {
      case Some(endVer) =>
        Versions.toVersion(endVer) match {
          case Some(v) => v
          case None =>
            throw new RuntimeException(
              s"Unable to create SemverWithUnnormalized for $libraryName@$endVer")
        }
      case None => Versions.toVersion(libBeginVersionSemver.ver.nextMajor().getValue).get
    }

    val withAlphas = libEndVersionSemver.ver
      .withClearedSuffixAndBuild() != libEndVersionSemver.ver
    val filtered = PackageHandlingUtils
      .filterRangeAndSortPackages(
        libBeginVersionSemver,
        libEndVersionSemver,
        libVersions.flatMap(Versions.toVersion),
        withAlphas)
      .map(_.ver.getValue)

    log.info(s"Library versions filtered for $libraryName@$libraryBeginVersion")

    if (generateStatistics) {
      RegressionTypeLearner(
        RegressionTypeLearnerOptions(
          libraryName,
          outDir = Paths.get("out-noregrets/full-cycles-new-approach"),
          libraryVersions = filtered,
          clients = Left(successfuls),
          rerunFailedLearning = rerunFailed,
          neverRun = neverRun,
          doNotRun = doNotRun,
          swarm = swarm,
          learningCommandCachePolicy = learningCommandCachePolicy,
          clientPriority = clientPriority)).generateStatistics()
    } else {
      RegressionTypeLearner(
        RegressionTypeLearnerOptions(
          libraryName,
          outDir = Paths.get("out-noregrets/full-cycles-new-approach"),
          libraryVersions = filtered,
          clients = Left(successfuls),
          rerunFailedLearning = rerunFailed,
          neverRun = neverRun,
          doNotRun = doNotRun,
          swarm = swarm,
          learningCommandCachePolicy = learningCommandCachePolicy,
          NoRegretsPlus_Mode = NoRegretsPlusMode,
          ignoreTagsMode = ignoreTagsMode,
          enableValueChecking = enableValueChecking,
          collectStackTraces = false,
          clientPriority = clientPriority,
          withCoverage = withCoverage)).handleRegressionTypeLearner()
    }
  }
}
