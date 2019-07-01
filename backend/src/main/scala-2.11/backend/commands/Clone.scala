package backend.commands

import java.nio.file._

import backend.{Globals, RegistryReader}
import backend.commands.Common._
import backend.datastructures.PackageAtVersion
import backend.package_handling.PackageHandlingUtils
import backend.utils._
import scopt.OptionDef

import scala.language.implicitConversions

object Clone {
  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)

  private implicit val executor: ExecutionUtils.ProcExecutor =
    ExecutionUtils.rightExecutor(Globals.benchmarksImage)

  def handleCloneCmd(cloneOptions: CloneOptions): List[Path] = {
    log.info(s"Invoking subcommand ${cloneOptions.subCmd} first")
    val repoVersions = Common
      .replParser(cloneOptions.subCmd)
      .asInstanceOf[Option[List[PackageAtVersion]]]
      .get

    val registry = new RegistryReader().loadLazyNpmRegistry()

    repoVersions.par.flatMap { pkgVersion =>
      try {
        Some(
          PackageHandlingUtils
            .clonePackage(cloneOptions.outDir, registry, pkgVersion)
            ._1)
      } catch {
        case e: Exception =>
          log.error(s"Failed to clone project $pkgVersion: $e")
          None
      }
    }.toList

  }

  object CloneOptions {
    def make(parser: scopt.OptionParser[Config])
      : Seq[OptionDef[_, Common.Config]] = {

      implicit def asDep(cmd: Option[Common.CommandOptions]): CloneOptions =
        cmd.get.asInstanceOf[CloneOptions]

      Seq(
        parser
          .arg[String]("outdir")
          .action((x, c) =>
            c.copy(cmd = Some(c.cmd.copy(outDir = Paths.get(x)))))
          .text("output location of cloned packages"),
        parser
          .arg[String]("command")
          .unbounded()
          .action((x, c) =>
            c.copy(cmd = Some(c.cmd.copy(subCmd = c.cmd.subCmd ::: List(x)))))
          .text("sub command for package selection")
      )
    }
  }
  case class CloneOptions(outDir: Path = null, subCmd: List[String] = List())
      extends CommandOptions
}
