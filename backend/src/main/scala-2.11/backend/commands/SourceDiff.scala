package backend.commands

import java.nio.file._

import backend.commands.Common._
import backend.utils.ExecutionUtils
import scopt.OptionDef

import scala.language.implicitConversions

object SourceDiff {
  def handleSourceDiff(opt: SourceDiffOptions): Unit = {
    val outDir = opt.outDir
    val pkgName = opt.packageName
    val versionPre = opt.packageVersionPre.get
    val versionPost = opt.packageVersionPost.get
    val diffFolder = outDir.resolve(pkgName)
    diffFolder.toFile.mkdirs()

    var res =
      ExecutionUtils.execute(s"yarn init --yes", logStdout = false)(diffFolder)
    if (res.code != 0) throw new RuntimeException(res.log)

    //Install pre
    res = ExecutionUtils.execute(s"yarn add ${pkgName}@${versionPre}", logStdout = false)(
      diffFolder)
    if (res.code != 0) throw new RuntimeException(res.log)

    // Doing the diff magic
    val diffSubfolder = diffFolder.resolve(s"${versionPre}_${versionPost}")
    diffSubfolder.toFile.mkdirs()

    //Git init
    res = ExecutionUtils.execute(s"git init", logStdout = false)(diffSubfolder)
    if (res.code != 0) throw new RuntimeException(res.log)

    //Copy pre
    res =
      ExecutionUtils.execute(s"cp -r ../node_modules/${pkgName}/ .", logStdout = false)(
        diffSubfolder)
    if (res.code != 0) throw new RuntimeException(res.log)

    //Add and commit
    res = ExecutionUtils.execute(s"git add --all", logStdout = false)(diffSubfolder)
    if (res.code != 0) throw new RuntimeException(res.log)
    res = ExecutionUtils.execute(s"git commit -m 'PRE@${versionPre}'", logStdout = false)(
      diffSubfolder)
    if (res.code != 0) throw new RuntimeException(res.log)

    //Install post
    res = ExecutionUtils.execute(
      s"yarn add --ignore-engines ${pkgName}@${versionPost}",
      logStdout = false)(diffFolder)
    if (res.code != 0) throw new RuntimeException(res.log)

    //Copy post
    res =
      ExecutionUtils.execute(s"cp -r ../node_modules/${pkgName}/ .", logStdout = false)(
        diffSubfolder)
    if (res.code != 0) throw new RuntimeException(res.log)

    //Add and commit
    res = ExecutionUtils.execute(s"git add --all", logStdout = false)(diffSubfolder)
    if (res.code != 0) throw new RuntimeException(res.log)
    res =
      ExecutionUtils.execute(s"git commit -m 'POST@${versionPost}'", logStdout = false)(
        diffSubfolder)
    if (res.code != 0) throw new RuntimeException(res.log)

    res = ExecutionUtils.execute(s"diff2html -s side -- HEAD~1")(diffSubfolder)
    if (res.code != 0) throw new RuntimeException(res.log)
  }

  object SourceDiffOptions {
    def make(parser: scopt.OptionParser[Config]): Seq[OptionDef[_, Common.Config]] = {

      implicit def asDep(cmd: Option[Common.CommandOptions]): SourceDiffOptions =
        cmd.get.asInstanceOf[SourceDiffOptions]

      Seq(
        parser
          .arg[String]("outdir")
          .action((x, c) => c.copy(cmd = Some(c.cmd.copy(outDir = Paths.get(x)))))
          .text("output location of cloned packages"),
        parser
          .arg[String]("<package-name>")
          .action((x, c) => c.copy(cmd = Some(c.cmd.copy(packageName = x))))
          .text("package name"),
        parser
          .arg[String]("<version-pre>")
          .optional()
          .action(
            (x, c) =>
              c.copy(
                cmd = Some(c.cmd.get
                  .asInstanceOf[SourceDiffOptions]
                  .copy(packageVersionPre = Some(x)))))
          .text("version pre"),
        parser
          .arg[String]("<version-post>")
          .optional()
          .action(
            (x, c) =>
              c.copy(
                cmd = Some(c.cmd.get
                  .asInstanceOf[SourceDiffOptions]
                  .copy(packageVersionPost = Some(x)))))
          .text("version post"))
    }
  }
  case class SourceDiffOptions(outDir: Path = null,
                               packageName: String = null,
                               visual: Boolean = true,
                               packageVersionPre: Option[String] = None,
                               packageVersionPost: Option[String] = None)
      extends CommandOptions
}
