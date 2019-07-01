package backend.commands

import java.io.File
import java.nio.file.Paths

import backend.commands.ApiTrace.ApiTraceOptions
import backend.commands.BreakingChanges.BreakingChangesOptions
import backend.commands.Clone.CloneOptions
import backend.commands.Dependents.DependentOptions
import backend.commands.RunTests.RunTestsOptions
import backend.commands.SourceDiff.SourceDiffOptions
import backend.commands.Successfuls.SuccessfulOptions
import backend.datastructures.PackageAtVersion
import backend.utils._
import org.json4s.JValue
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import scopt.OptionDef

import scala.language.implicitConversions
import scala.util.Try

object Common {
  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)

  val parser = new scopt.OptionParser[Config]("scopt") {
    head("npmprj", "0.0.0")
    opt[Unit]("schedule")
      .action((_, c) => c.copy(schedule = true))
      .text("schedule the job on Kue, waits for termination")
    cmd("dependents")
      .action((_, c) => c.copy(cmd = Some(DependentOptions())))
      .text("search for the dependents of the given package name and version")
      .children(DependentOptions.make(this): _*)
    cmd("successful")
      .action((_, c) => c.copy(cmd = Some(SuccessfulOptions())))
      .text("search for the packages with successful tests")
      .children(SuccessfulOptions.make(this): _*)
    cmd("source-diff")
      .action((_, c) => c.copy(cmd = Some(SourceDiffOptions())))
      .text("Compute the source diff between two versions of a NPM package")
      .children(SourceDiffOptions.make(this): _*)
    cmd("interesting-breaking-changes")
      .action((_, c) => c.copy(cmd = Some(BreakingChangesOptions())))
      .text("search all the interesting breaking changes")
    cmd("api-trace")
      .action((_, c) => c.copy(cmd = Some(ApiTraceOptions())))
      .text("trace the API interaction of modules specified in the subcommand")
      .children(ApiTraceOptions.make(this): _*)
    cmd("clone")
      .action((_, c) => c.copy(cmd = Some(CloneOptions())))
      .text("clone the repositories specified in the subcommand")
      .children(CloneOptions.make(this): _*)
    cmd("single-package")
      .action((_, c) => c.copy(cmd = Some(SinglePackageOptions())))
      .text("Construct a single package from the argument")
      .children(SinglePackageOptions.make(this): _*)
    cmd("regression-tc")
      .action((_, c) => c.copy(cmd = Some(RegressionTypeLearnerOptions())))
      .text("Run the regression type checker")
      .children(RegressionTypeLearnerOptions.make(this): _*)
    cmd("crawl-breaking-changes")
      .action((_, c) => c.copy(cmd = Some(CrawlBreakingChangesOptions())))
      .text("Crawl for breaking changes issues")
    cmd("cli-job")
      .action((_, c) => c.copy(cmd = Some(CliJobLoaderOptions())))
      .children(CliJobLoaderOptions.make(this): _*)
      .text("Start the job specified in the file")
    cmd("run-tests")
      .action((_, c) => c.copy(cmd = Some(RunTestsOptions())))
      .children(RunTestsOptions.make(this): _*)
      .text("Run the tests of the given client with the given library")
    cmd("make-stats")
      .action((_, c) => c.copy(cmd = Some(MakeStatsOptions())))
      .children(MakeStatsOptions.make(this): _*)
      .text("Generate stats files on # dependents")
  }

  def replParser(args: List[String]): Option[Any] = {
    parser.parse(args, Config()) match {
      case Some(config) =>
        cmdHandler[AnyRef](config)
      case None =>
        None
    }
  }
  def cmdHandler[T <: AnyRef](config: Config)(implicit m: Manifest[T]): Option[T] = {
    config.cmd match {
      case Some(x) =>
        if (config.schedule) {
          log.info("Scheduling job on kue")
          val jobFile = File.createTempFile(
            x.getClass.getSimpleName,
            ".json",
            Paths.get("out/").toFile)
          Try(jobFile.deleteOnExit())
          val jobOutFile = File.createTempFile(
            x.getClass.getSimpleName,
            ".json",
            Paths.get("out-noregrets/").toFile)
          val executor: ExecutionUtils.ProcExecutor =
            ExecutionUtils.defaultCustomPathsExecutor
          val serializer = JsonSerializer()
          serializer.serialize(JobInfo(x, x.outputFiles), jobFile.toPath, pretty = true)
          var r: ProcessExecutionResult = null
          while (r == null || r.code == 5) {
            r = executor.executeCommand(
              List(
                "node",
                "kue/runnpmcli.js",
                jobFile.toPath.toAbsolutePath.toString,
                jobOutFile.toPath.toAbsolutePath.toString),
              logStdout = false,
              logFailure = false)(Paths.get(""))
          }
          Try(jobOutFile.deleteOnExit())
          log.info("Scheduled job finished, result: \n" + r.log)
          if (r.code != 0) {
            throw new RuntimeException("Job execution failed")
          } else Some(serializer.deserialize[T](jobOutFile.toPath))
        } else {
          x match {
            case opt: DependentOptions =>
              Some(Dependents.handleDependentsCmd(opt).asInstanceOf[T])
            case opt: SuccessfulOptions =>
              Some(Successfuls(opt).handleSuccessfulsCmd().asInstanceOf[T])
            case opt: SourceDiffOptions =>
              Some(SourceDiff.handleSourceDiff(opt).asInstanceOf[T])
            case opt: BreakingChangesOptions =>
              Some(BreakingChanges.handleBreakingChangesCommand(opt).asInstanceOf[T])
            case opt: CloneOptions =>
              Some(Clone.handleCloneCmd(opt).asInstanceOf[T])
            case opt: ApiTraceOptions =>
              Some(ApiTrace(opt).handleTraceCommand().asInstanceOf[T])
            case opt: SinglePackageOptions =>
              Some(
                List(PackageAtVersion(opt.packageName, opt.packageVersion))
                  .asInstanceOf[T])
            case opt: RegressionTypeLearnerOptions =>
              Some(
                RegressionTypeLearner(opt).handleRegressionTypeLearner().asInstanceOf[T])
            case opt: CrawlBreakingChangesOptions =>
              Some(CrawlBreakingChanges.handleCrawlBreakingChanges().asInstanceOf[T])
            case opt: CliJobLoaderOptions =>
              Some(CliJobLoader(opt).handleCliJobLoader().asInstanceOf[T])
            case opt: RunTestsOptions =>
              Some(RunTests(opt).handleRunTests().asInstanceOf[T])
            case opt: MakeStatsOptions =>
              Some(MakeStats.handleMakeStatsCmd(opt).asInstanceOf[T])
          }
        }
      case None =>
        parser.showUsageAsError()
        None
    }
  }

  def pathDiffer(preJson: JValue, postJson: JValue): Unit = {
    val diff = preJson.diff(postJson)
    val diffJson
      : JValue = ("added" -> diff.added) ~ ("changed" -> diff.changed) ~ ("deleted" -> diff.deleted)
    val diffStr = pretty(render(diffJson))

    log.info(diffStr)
  }

  trait CommandOptions {
    def toCacheKey: List[String] = List(this.getClass.getSimpleName)
    def outputFiles: List[String] = List()
  }

  case class Config(cmd: Option[CommandOptions] = None, schedule: Boolean = false)

  case object GenWebDataOptions extends CommandOptions

  object SinglePackageOptions {
    def make(parser: scopt.OptionParser[Config]): Seq[OptionDef[_, Common.Config]] = {

      implicit def asDep(cmd: Option[Common.CommandOptions]): SinglePackageOptions =
        cmd.get.asInstanceOf[SinglePackageOptions]

      Seq(
        parser
          .arg[String]("package-name")
          .action((x, c) => c.copy(cmd = Some(c.cmd.copy(packageName = x))))
          .text("name of the package to look for"),
        parser
          .arg[String]("version")
          .action((x, c) => c.copy(cmd = Some(c.cmd.copy(packageVersion = x))))
          .text("version"))
    }
  }

  case class SinglePackageOptions(packageName: String = null,
                                  packageVersion: String = null)
      extends CommandOptions
}
