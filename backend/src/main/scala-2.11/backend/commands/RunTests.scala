package backend.commands

import java.nio.file._

import backend.Globals
import backend.commands.Common._
import backend.commands.RunTests._
import backend.datastructures._
import backend.package_handling.PackageHandlingUtils
import backend.utils._
import scopt.OptionDef

import scala.language.implicitConversions

case class RunTests(options: RunTestsOptions) {
  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)

  private implicit val executor: ExecutionUtils.ProcExecutor =
    ExecutionUtils.rightExecutor(Globals.benchmarksImage)

  options.clientOutdir.toFile.mkdirs()

  def handleRunTests(): ProcessExecutionResult = {
    try {
      val pRes = PackageHandlingUtils.cloneAndRunTestsWithLibrary(
        options.client,
        options.library,
        options.clientOutdir,
        options.deleteDirOnExit,
        ignoreTags = options.ignoreTags)
      pRes
    } catch {
      case e: Exception ⇒
        throw e
    }
  }
}

object RunTests {
  type SubCommand = List[String]

  object RunTestsOptions {
    def make(parser: scopt.OptionParser[Config]): Seq[OptionDef[_, Common.Config]] = {

      implicit def asDep(cmd: Option[Common.CommandOptions]): RunTestsOptions =
        cmd.get.asInstanceOf[RunTestsOptions]

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
          .text("client location"))
    }
  }

  case class RunTestsOptions(client: PackageAtVersion = null,
                             library: PackageAtVersion = null,
                             clientOutdir: Path = null,
                             // Config used we can use in NoRegretsPlus when we do do not care if client's library dependency constraint satisfies the library version
                             ignoreTags: Boolean = false,
                             deleteDirOnExit: Boolean = true)
      extends CommandOptions

}
