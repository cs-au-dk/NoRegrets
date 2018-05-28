package distilling.server.commands

import java.nio.file._

import distilling.server.Globals
import distilling.server.commands.ApiTrace._
import distilling.server.commands.Common._
import distilling.server.datastructures._
import distilling.server.package_handling.Instrumentation.ProxyHelpersOptions
import distilling.server.package_handling.PackageHandlingUtils
import distilling.server.regression_typechecking._
import distilling.server.utils.NotationalUtils.SI
import distilling.server.utils._
import org.json4s.Formats
import org.json4s.native.Serialization.read
import scopt.OptionDef

import scala.language.implicitConversions

case class ApiTrace(options: ApiTraceOptions) {
  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)
  private implicit val executor: ExecutionUtils.ProcExecutor =
    ExecutionUtils.rightExecutor(Globals.benchmarksImage)

  val MAX_TRACE_FILE_SIZE: Double = 50.mega

  // compiling the tracer project
  PackageHandlingUtils.tscAll

  options.clientOutdir.toFile.mkdirs()
  options.libraryLearningOutdir.toFile.mkdirs()

  def handleTraceCommand(): TracingResult = {
    val client = options.client
    val library = options.library

    val regressionOutput =
      options.libraryLearningOutdir
        .resolve(s"$client-$library-regression-learn.json")
        .toAbsolutePath

    val proxyHelperOptions =
      ProxyHelpersOptions(
        libraryModuleName = library.packageName,
        output = regressionOutput,
        collectStackTraces = options.collectStackTraces,
        detailedStackTraces = options.detailedStackTraces)

    val success = PackageHandlingUtils.runTracing(
      client,
      library,
      options.clientOutdir,
      proxyHelperOptions,
      ignoreFailingInstallations = options.ignoreFailingInstallations)

    try {
      implicit val formats: Formats = SerializerFormats.commonSerializationFormats
      read[TracingResult](Utils.readFile(regressionOutput))
    } catch {
      case e: Throwable =>
        if (success) {
          LearningFailure(s"Tests succeed but got error reading learned outcome: ${e}")
        } else {
          LearningFailure(s"Tests failed, error reading the outcome")
        }
    }

  }
}

object ApiTrace {
  type SubCommand = List[String]

  case class TraceResult(fullSpec: Path, allTraces: Map[PackageAtVersion, Option[Path]])

  object ApiTraceOptions {
    def make(parser: scopt.OptionParser[Config]): Seq[OptionDef[_, Common.Config]] = {

      implicit def asDep(cmd: Option[Common.CommandOptions]): ApiTraceOptions =
        cmd.get.asInstanceOf[ApiTraceOptions]

      Seq(
        parser
          .arg[String]("client")
          .action((x, c) =>
            c.copy(cmd = Some(
              c.cmd.copy(client = PackageAtVersion(x.split("@")(0), x.split("@")(1))))))
          .text("client whose tests to use"),
        parser
          .arg[String]("library")
          .action((x, c) =>
            c.copy(cmd = Some(
              c.cmd.copy(library = PackageAtVersion(x.split("@")(0), x.split("@")(1))))))
          .text("library and version to use"),
        parser
          .arg[String]("clients-outdir")
          .action((x, c) => c.copy(cmd = Some(c.cmd.copy(clientOutdir = Paths.get(x)))))
          .text("client location"),
        parser
          .arg[String]("library-learning-outdir")
          .action((x, c) =>
            c.copy(cmd = Some(c.cmd.copy(libraryLearningOutdir = Paths.get(x)))))
          .text("client location"))
    }
  }

  case class ApiTraceOptions(client: PackageAtVersion = null,
                             library: PackageAtVersion = null,
                             clientOutdir: Path = null,
                             libraryLearningOutdir: Path = null,
                             collectStackTraces: Boolean = true,
                             detailedStackTraces: Boolean = false,
                             ignoreFailingInstallations: Boolean = false)
      extends CommandOptions

}
