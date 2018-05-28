package distilling.server.commands

import java.nio.file.Paths

import distilling.server.RegistryReader
import distilling.server.commands.Common._
import distilling.server.commands.Dependents._
import distilling.server.datastructures._
import distilling.server.utils.Utils._
import distilling.server.utils._
import scopt.OptionDef

import scala.language.implicitConversions
import scala.util.Try

object MakeStats {
  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)

  def handleMakeStatsCmd(options: MakeStatsOptions): Unit = {

    if (options.countDepenentsWeCanRun) {
      val registry = new RegistryReader().loadNpmRegistry()

      var count = 0

      val matches = registry.par.flatMap {
        case (pName, curDescription) =>
          Try {
            val latestMajor = curDescription.versions.keys
              .flatMap(Versions.toVersion)
              .toList
              .filter(v =>
                v.ver.getPatch == 0 && v.ver.getMinor == 0 && v.ver.withClearedSuffixAndBuild == v.ver)
              .sorted(SemverWithUnnormalized.SemverOrdering)
              .last
              .unnormalized

            val ret = pName -> Dependents
              .executeDependentsCmd(DependentOptions(pName, Some(latestMajor)))
              .length
            this.synchronized {
              count += 1
              if (count % 100 == 0) {
                log.info(s"Handled a total of $count packages")
              }
            }
            ret
          }.toOption
      }.toList

      val sb = new StringBuilder()
      val out = Paths.get("out").resolve("dependents.csv")
      val sorted = matches.sortBy(_._2)
      sorted.foreach {
        case (name, dependents) =>
          sb.append(name).append(",").append(dependents).append("\n")
      }

      writeToFile(out, sb.toString(), false, false)
    }
    if (options.countSuccessfulls) {
      val cache = Paths.get("caching")
      cache.toFile
        .list()
        .filter(f => f.startsWith("successful-") && !f.endsWith(".log"))
        .foreach { file =>
          try {
            val successfuls = SerializerFormats.defaultSerializer
              .deserialize[List[PackageAtVersion]](cache.resolve(file))
            log.info(s"$file: ${successfuls.size}")
          } catch {
            case e: Throwable =>
              log.info(s"$file: error")
          }
        }
    }
  }

}
object MakeStatsOptions {
  def make(parser: scopt.OptionParser[Config]): Seq[OptionDef[_, Common.Config]] = {

    implicit def asDep(cmd: Option[Common.CommandOptions]): MakeStatsOptions =
      cmd.get.asInstanceOf[MakeStatsOptions]

    Seq()
  }
}

case class MakeStatsOptions(countDepenentsWeCanRun: Boolean = false,
                            countSuccessfulls: Boolean = true)
    extends CommandOptions {}
