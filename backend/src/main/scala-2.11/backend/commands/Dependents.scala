package backend.commands

import backend.RegistryReader
import backend.commands.Common._
import backend.datastructures._
import backend.utils.Utils.CachedMemoize
import backend.utils._
import scopt.OptionDef
import akka.http.scaladsl.model.DateTime
import backend.commands.ClientPriority.ClientPriority

import scala.language.implicitConversions

object Dependents {
  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)

  val getVersion: CachedMemoize[String, Option[SemverWithUnnormalized]] = CachedMemoize(
    Versions.toVersion)
  val getConstraint: CachedMemoize[String, ConstraintWithUnnormalized] = CachedMemoize(
    Versions.toConstraint)

  def handleDependentsCmd(dependentOptions: DependentOptions): List[PackageAtVersion] = {

    log.info(s"Running dependents with options $dependentOptions")
    DiskCaching.cache(
      executeDependentsCmd(dependentOptions),
      CommandCachePolicy.getMatchingCachePolicy(dependentOptions.commandCachePolicy),
      dependentOptions.toCacheKey)
  }

  private case class libraryClient(pv: PackageAtVersion,
                                   stars: Option[Int],
                                   lastUpdate: Long)

  /**
    * Returns an iterable of the clientVersions that has the library as a dependency
    * @param dependencyCandidate
    * @param libraryName
    * @param libraryVersion If None, then it accepts all library versions
    * @param usingMocha
    * @return
    */
  private def matchClient(dependencyCandidate: NpmPackageDescription,
                          libraryName: String,
                          libraryVersion: Option[SemverWithUnnormalized],
                          usingMocha: Boolean): Iterable[libraryClient] = {
    lazy val newestVersion = dependencyCandidate.versions.maxBy(x ⇒
      getVersion(x._1).getOrElse(Versions.earliestVersion))(
      SemverWithUnnormalized.SemverOrdering)
    val epoch = DateTime.MinValue.clicks

    //Date to time since epoch
    val clicks: (Option[String] ⇒ Long) = (date: Option[String]) ⇒ {
      date
        .map(DateTime.fromIsoDateTimeString(_).map(_.clicks).getOrElse(epoch))
        .getOrElse(epoch)
    }
    lazy val clickNewest = clicks(newestVersion._2.releaseDate)

    dependencyCandidate.versions.flatMap {
      case (version, desc) =>
        // whose dependencies
        desc.dependencies match {
          case Some(v) =>
            // include the packageName

            v.get(libraryName) match {
              case None => List()
              case Some(constraint) =>
                libraryVersion match {
                  case Some(v) =>
                    try {
                      val toPick = v.satisfies(getConstraint(constraint)) &&
                        (!usingMocha || desc.scripts.toString.contains("mocha"))
                      if (toPick) {
                        List(
                          libraryClient(
                            PackageAtVersion(dependencyCandidate.name, version),
                            dependencyCandidate.stars,
                            clickNewest))
                      } else
                        List()
                    } catch {
                      case _: Exception =>
                        log.error(
                          s"Error while evaluating satisfability of $v againts $constraint")
                        List()
                    }
                  case None =>
                    // match!
                    List(
                      libraryClient(
                        PackageAtVersion(dependencyCandidate.name, version),
                        dependencyCandidate.stars,
                        clickNewest))
                }
            }
          case None =>
            List()
        }
    }
  }

  def executeDependentsCmd(dependentOptions: DependentOptions): List[PackageAtVersion] = {
    val registry = new RegistryReader().loadNpmRegistry()

    val libraryVersion =
      dependentOptions.libraryVersion.map(getVersion(_).get)

    val matches = registry.par.flatMap {
      case (_, curDescription) =>
        matchClient(
          curDescription,
          dependentOptions.packageName,
          libraryVersion,
          dependentOptions.usingMocha)
    }

    //Remove clients with pre-release identifiers
    val matchesWOPreRelease = matches.flatMap { m =>
      val v = getVersion(m.pv.packageVersion)
      v.flatMap { v =>
        if (v.ver.withClearedSuffixAndBuild() == v.ver)
          Some(
            libraryClient(
              PackageAtVersion(m.pv.packageName, v.unnormalized),
              m.stars,
              m.lastUpdate))
        else
          None
      }
    }

    val filtered = dependentOptions.clientPriority match {
      case ClientPriority.OnlyOldest ⇒ {
        matchesWOPreRelease
          .groupBy(_.pv.packageName)
          .mapValues(versions =>
            versions.minBy(m => getVersion(m.pv.packageVersion).get.ver))
          .values
      }
      case ClientPriority.OnlyNewest ⇒ {
        matchesWOPreRelease
          .groupBy(_.pv.packageName)
          .mapValues(versions =>
            versions.maxBy(m => getVersion(m.pv.packageVersion).get.ver))
          .values
      }
      case _ ⇒ matchesWOPreRelease
    }

    val result =
      filtered.toList.sortBy(p =>
        (p.pv.packageName, getVersion(p.pv.packageVersion).get.ver))

    if (dependentOptions.limit > 0)
      result
        .sortWith {
          //Sort by most stars. If equal, then take newest.
          case (libraryClient(_, Some(stars1), _), libraryClient(_, Some(stars2), _)) ⇒ {
            stars1 < stars2
          }
          case (libraryClient(_, None, _), libraryClient(_, Some(stars), _)) ⇒ {
            true
          }
          case (libraryClient(_, Some(stars), _), libraryClient(_, None, _)) ⇒ {
            false
          }
          case (libraryClient(_, None, age1), libraryClient(_, None, age2)) ⇒ {
            age1 < age2
          }
        }
        .takeRight(dependentOptions.limit)
        .map(_.pv)
    else
      result.map(_.pv)

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
          .action((x, c) => c.copy(cmd = Some(c.cmd.copy(libraryVersion = Some(x)))))
          .text("package version"),
        parser
          .opt[Int]("limit")
          .action((x, c) => c.copy(cmd = Some(c.cmd.copy(limit = x))))
          .text("limit on the number of retrieved packages"),
        parser
          .opt[Unit]("only-newest")
          .action((x, c) =>
            c.copy(cmd = Some(c.cmd.copy(clientPriority = ClientPriority.OnlyNewest))))
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
    libraryVersion: Option[String] = None,
    clientPriority: ClientPriority = ClientPriority.OnlyOldest,
    usingMocha: Boolean = true,
    commandCachePolicy: CommandCachePolicy.Value =
      CommandCachePolicy.USE_DATA_IF_PRESENT_REGENERATE_OTHERWISE,
    limit: Int = -1,
    NoRegretsPlusMode: Boolean = true)
      extends CommandOptions {

    override def toCacheKey: List[String] = {
      List(
        "dependents",
        if (NoRegretsPlusMode) "NoRegrets2" else "",
        packageName,
        libraryVersion.getOrElse("all"),
        "limit",
        if (limit >= 0) limit.toString else "all",
        clientPriority match {
          case ClientPriority.OnlyOldest ⇒ "only-oldest"
          case ClientPriority.OnlyNewest ⇒ "only-newest"
          case ClientPriority.All ⇒ "all-versions"
        },
        if (usingMocha) "only-mocha" else "")
    }

    private def verify(): Unit = {
      if (commandCachePolicy == CommandCachePolicy.VERIFY_PRESENCE_AND_CONTENT_OF_DATA)
        throw new RuntimeException("Unsupported")
    }
    verify()
  }

}
