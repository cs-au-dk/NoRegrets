package distilling.server.commands

import java.nio.file._

import distilling.server.Globals
import distilling.server.commands.Common._
import distilling.server.commands.RunTests._
import distilling.server.datastructures._
import distilling.server.package_handling.PackageHandlingUtils
import distilling.server.utils._
import scopt.OptionDef

import scala.language.implicitConversions

case class RunTests(options: RunTestsOptions) {
  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)

  private implicit val executor: ExecutionUtils.ProcExecutor =
    ExecutionUtils.rightExecutor(Globals.benchmarksImage)

  options.clientOutdir.toFile.mkdirs()

  def handleRunTests(): ProcessExecutionResult = {
    PackageHandlingUtils.cloneAndRunTestsWithLibrary(
      options.client,
      options.library,
      options.clientOutdir)
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
                             clientOutdir: Path = null)
      extends CommandOptions

}
