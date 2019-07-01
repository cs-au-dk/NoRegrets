package backend.commands

import java.nio.file._

import backend.commands.Common._
import backend.datastructures.SerializerFormats
import backend.utils._
import scopt.OptionDef

import scala.language.implicitConversions

case class CliJobLoader(options: CliJobLoaderOptions) {

  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)
  val serializer = JsonSerializer()

  def handleCliJobLoader(): Any = {
    val opts = serializer.deserialize[CommandOptions](options.file)

    try {
      val res = Common.cmdHandler[AnyRef](Common.Config(cmd = Some(opts))).get
      SerializerFormats.defaultSerializer.serialize(res, options.resultOut, pretty = true)
    } catch {
      case e: Throwable =>
        println(e)
        e.printStackTrace()
        System.exit(42)
    }

  }

}

case class JobInfo(jobOptions: CommandOptions, filesToSave: List[String])

object CliJobLoaderOptions {
  def make(parser: scopt.OptionParser[Config]): Seq[OptionDef[_, Common.Config]] = {

    implicit def asDep(cmd: Option[Common.CommandOptions]): CliJobLoaderOptions =
      cmd.get.asInstanceOf[CliJobLoaderOptions]

    Seq(
      parser
        .arg[String]("job")
        .action((x, c) => c.copy(cmd = Some(c.cmd.copy(file = Paths.get(x)))))
        .text("file containing job information"),
      parser
        .arg[String]("result-out")
        .action((x, c) => c.copy(cmd = Some(c.cmd.copy(resultOut = Paths.get(x)))))
        .text("output for the result"))
  }
}
case class CliJobLoaderOptions(file: Path = null, resultOut: Path = null)
    extends CommandOptions
