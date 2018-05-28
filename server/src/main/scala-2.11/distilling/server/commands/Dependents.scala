package distilling.server.commands

import distilling.server.RegistryReader
import distilling.server.commands.Common._
import distilling.server.datastructures._
import distilling.server.utils.Utils.CachedMemoize
import distilling.server.utils._
import scopt.OptionDef

import scala.language.implicitConversions

object Dependents {
  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)

  val getVersion = CachedMemoize(Versions.toVersion)
  val getConstraint = CachedMemoize(Versions.toConstraint)

  def handleDependentsCmd(dependentOptions: DependentOptions): List[PackageAtVersion] = {

    log.info(s"Running dependents with options $dependentOptions")
    DiskCaching.cache(
      executeDependentsCmd(dependentOptions),
      CommandCachePolicy.getMatchingCachePolicy(dependentOptions.commandCachePolicy),
      dependentOptions.toCacheKey)
  }

  private def dependingVersions(dependencyCandidate: NpmPackageDescription,
                                dependentName: String,
                                dependentVersion: Option[SemverWithUnnormalized],
                                usingMocha: Boolean): Iterable[PackageAtVersion] = {
    dependencyCandidate.versions.flatMap {
      case (version, desc) =>
        // whose dependencies
        desc.dependencies match {
          case Some(v) =>
            // include the packageName
            v.get(dependentName) match {
              case None => List()
              case Some(constraint) =>
                dependentVersion match {
                  case Some(v) =>
                    try {
                      val toPick = v.satisfies(getConstraint(constraint)) &&
                        (!usingMocha || desc.scripts.toString.contains("mocha"))
                      if (toPick)
                        List(PackageAtVersion(dependencyCandidate.name, version))
                      else
                        List()
                    } catch {
                      case _: Exception =>
                        log.error(
                          s"Error while evaluating satisfability of $v againts $constraint")
                        List()
                    }
                  case None =>
                    // match!
                    List(PackageAtVersion(dependencyCandidate.name, version))
                }
            }
          case None =>
            List()
        }
    }
  }

  def executeDependentsCmd(dependentOptions: DependentOptions): List[PackageAtVersion] = {
    val registry = new RegistryReader().loadNpmRegistry()

    val dependentVersion =
      dependentOptions.packageVersion.map(getVersion(_).get)

    val matches = registry.par.flatMap {
      case (_, curDescription) =>
        dependingVersions(
          curDescription,
          dependentOptions.packageName,
          dependentVersion,
          dependentOptions.usingMocha)
    }

    val convertibles = matches.flatMap { m =>
      val v = getVersion(m.packageVersion)
      v.flatMap { v =>
        if (v.ver.withClearedSuffixAndBuild() == v.ver)
          Some(PackageAtVersion(m.packageName, v.unnormalized))
        else
          None
      }
    }

    val filtered = if (dependentOptions.onlyOldest) {
      convertibles
        .groupBy(_.packageName)
        .mapValues(versions => versions.minBy(m => getVersion(m.packageVersion).get.ver))
        .values
    } else if (dependentOptions.onlyNewest) {
      convertibles
        .groupBy(_.packageName)
        .mapValues(versions => versions.maxBy(m => getVersion(m.packageVersion).get.ver))
        .values
    } else convertibles

    val result =
      filtered.toList.sortBy(p => (p.packageName, getVersion(p.packageVersion).get.ver))

    if (dependentOptions.limit > 0)
      result.take(dependentOptions.limit)
    else
      result

  }

  object DependentOptions {
    def make(parser: scopt.OptionParser[Config]): Seq[OptionDef[_, Common.Config]] = {

      implicit def asDep(cmd: Option[Common.CommandOptions]): DependentOptions =
        cmd.get.asInstanceOf[DependentOptions]

      Seq(
        parser
          .arg[String]("package-name")
          .action((x, c) => c.copy(cmd = Some(c.cmd.copy(packageName = x))))
          .text("package name"),
        parser
          .arg[String]("package-version")
          .optional()
          .action((x, c) => c.copy(cmd = Some(c.cmd.copy(packageVersion = Some(x)))))
          .text("package version"),
        parser
          .opt[Int]("limit")
          .action((x, c) => c.copy(cmd = Some(c.cmd.copy(limit = x))))
          .text("limit on the number of retrieved packages"),
        parser
          .opt[Unit]("only-newest")
          .action((x, c) => c.copy(cmd = Some(c.cmd.copy(onlyOldest = true))))
          .text("filter packages that are not of the newest version"),
        parser
          .opt[Unit]("regenerate-cache")
          .optional()
          .action((x, c) =>
            c.copy(cmd =
              Some(c.cmd.copy(commandCachePolicy = CommandCachePolicy.REGENERATE_DATA))))
          .text("regenerate the cache for this command"))
    }
  }

  case class DependentOptions(
    packageName: String = null,
    packageVersion: Option[String] = None,
    onlyOldest: Boolean = true,
    onlyNewest: Boolean = false,
    usingMocha: Boolean = true,
    commandCachePolicy: CommandCachePolicy.Value =
      CommandCachePolicy.USE_DATA_IF_PRESENT_REGENERATE_OTHERWISE,
    limit: Int = -1)
      extends CommandOptions {

    override def toCacheKey: List[String] = {
      List(
        "dependents",
        packageName,
        packageVersion.getOrElse("all"),
        "limit",
        if (limit >= 0) limit.toString else "all",
        if (onlyOldest) "only-oldest" else "all-versions",
        if (usingMocha) "only-mocha" else "")
    }

    private def verify(): Unit = {
      assert(!(onlyOldest && onlyNewest))
      if (commandCachePolicy == CommandCachePolicy.VERIFY_PRESENCE_AND_CONTENT_OF_DATA)
        throw new RuntimeException("Unsupported")
    }

    verify()

  }

}
