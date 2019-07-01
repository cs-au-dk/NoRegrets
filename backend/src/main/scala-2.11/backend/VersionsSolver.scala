package backend
import java.util.concurrent.ConcurrentHashMap

import akka.http.scaladsl.model.DateTime
import com.github.benmanes.caffeine.cache.Caffeine
import backend.datastructures._
import backend.db.CouchClientProvider
import backend.utils.Log.Level
import backend.utils.Logger
import backend.utils.Utils.CachedMemoize
import upickle.default._

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.util._

object VersionsSolver extends App {
  private val log = Logger("VersionSolver", Level.Info)

  log.info("Started")

  entry()

  def entry(): Unit = {

    val registry = new RegistryReader().loadNpmRegistry()

    val computations = {
      implicit val provider = new CouchClientProvider()
      new SolutionSolver()(registry, provider).computeAndSaveSolutions()
    }

    // Prevent main thread to exit
    log.info("Finished, now we can exit")
  }
}

class SolutionSolver()(implicit registry: NpmRegistry.EagerNpmRegistry,
                       couchClientProvider: CouchClientProvider) {

  private val log = Logger("SolutionSolver", Level.Warn)

  implicit val db =
    couchClientProvider.couchdb.db("solutions", Solution.typeMapping)

  val constraintSatisfactionCache =
    Caffeine.newBuilder().build[(String, String, Option[DateTime]), Iterable[String]]()

  val COMBINATION_LIMIT = 5000

  val getVersion = CachedMemoize(Versions.toVersion _)
  val getReleaseDate = CachedMemoize.build2(releaseDate)

  val status = new ConcurrentHashMap[Object, String]()

  def printStatus(): Unit = {
    val sb = new StringBuffer()
    status.foreach {
      case (o: Object, s: String) =>
        sb.append(s"$o  -> $s")
        sb.append("\n")
    }
    log.info(s"Status: \n${sb.toString}")
  }

  def computeAndSaveSolutions(): Unit = {

    var count = 0

    registry.keys.par.foreach { packageName =>
      this.synchronized {
        count += 1

        log.info(s"Starting to work on $packageName ($count/${registry.size})")
      }

      computePackageSolutions(packageName)

      this.synchronized {
        log.info(s"Finished working on on $packageName ($count/${registry.size})")
      }
    }

  }

  def computePackageSolutions(packageName: String): Unit = {

    val packageVersions = registry(packageName)

    packageVersions.versions.filter {
      case (v, desc) =>
        desc.scripts.toString.contains("mocha") &&
          desc.repository.isDefined &&
          getVersion(v).isDefined
    }.par foreach {
      case (packageVersion: String, desc: VersionDesc) =>
        status.put(
          (packageName, packageVersion),
          s"Computing solution at ${DateTime.now}")

        computeVersionSolution(packageName, getVersion(packageVersion).get, desc)
          .foreach { solution =>
            status.put(
              (packageName, packageVersion),
              s"Computed solution, saving ${DateTime.now}")

            log.info(s"Saving solution for $packageName@$packageVersion")
            try {
              db.docs
                .create(solution, packageName + "_" + packageVersion)
                .retry((1 to 10).map(_ => 5.seconds), _ => true)
                .map { ok =>
                  log.info(s"${packageName}_$packageVersion saved, success: ${ok.ok}")
                  printStatus()
                }
                .unsafePerformSyncFor(10.minutes)
            } catch {
              case x: Throwable =>
                log.error(
                  s"Failed saving of a version $packageName@$packageVersion: $x\nCause:\n${x.getCause}")
                x.printStackTrace()
            } finally {
              status.remove((packageName, packageVersion))
            }
          }
    }
  }

  def computeVersionSolution(packageName: String,
                             packageVersion: SemverWithUnnormalized,
                             packageDescription: VersionDesc): Option[Solution] = {
    val dependencies = packageDescription.dependencies

    log.verb(s"Computing possibilities for $packageName@$packageVersion")
    // we gather the possibilities for each package dependency
    // { a -> ">3.0", b -> * , ...}
    //    becomes
    // { a -> [v1, .. vn] , b -> [v1,...,vn] }, ....
    val packageReleaseDate = getReleaseDate(packageName, packageVersion)
    val possibilities = dependencies match {
      case None => Map()
      case Some(deps) =>
        deps.map {
          case (dependencyName, versionConstraint) =>
            dependencyName -> versionsSatisfying(
              dependencyName,
              versionConstraint,
              packageReleaseDate,
              ignoreDate = false).toList
        }
    }

    val maximums: Map[String, SemverWithUnnormalized] = possibilities.flatMap {
      case (packageName, packageVersions) =>
        // we take a maximum, if there are multiple, the choice is non deterministic
        var max: Option[SemverWithUnnormalized] = None
        for (v <- packageVersions) {
          if (max.isEmpty || v.ver.isGreaterThan(max.get.ver)) {
            max = Some(v)
          }
        }
        max.map(packageName -> _)
    }

    val count = possibilities.values.map(x => BigInt(x.size)).sum

    log.verb(s"Done computing possibilities for $packageName@$packageVersion: $count")

    if (count > COMBINATION_LIMIT) {
      log.warn(
        s"Generating too many combinations for $packageName@$packageVersion: $count, excluding")
      None
    } else if (possibilities.values.exists(x => x.isEmpty)) {
      log.warn(s"""
           |Impossible to determine some packages satisfying constraints for $packageName@$packageVersion:
           |
           |  desired: ${dependencies.mkString("\n  ")}
           |  obtained: ${possibilities.mkString("\n  ")}
           |
           |excluding
           |""".stripMargin)
      None
    } else if (maximums.keys.size != possibilities.keys.size) {
      log.warn(s"""
           |Impossible to determine latest versions for dependant of $packageName@$packageVersion:
           |
           |  desired: ${dependencies.mkString("\n  ")}
           |  obtained: ${maximums.keys.toSet}
           |
           |excluding
           |""".stripMargin)
      None
    } else {

      // making the actual product of all the possibilities
      log.verb(s"Performing the cross product for $packageName@$packageVersion: $count")

      val solutionsExceptLatest =
        for {
          (packageName, versions) <- possibilities // foreach dependent package (packageName, [v1, ... vn])
          vi <- versions.toSet - maximums(packageName) // foreach package version vi, except the latest
        } yield {
          (maximums + (packageName -> vi))
            .mapValues(_.unnormalized) // take the latest versions of the others, except that for packageName, which takes vi
        }

      val solutions = maximums.mapValues(_.unnormalized) :: solutionsExceptLatest.toList

      log.verb(s"Done computing solutions for $packageName@$packageVersion")

      val dbEntry = Solution(
        packageName = packageName,
        packageVersion = packageVersion.unnormalized,
        releaseDate = getReleaseDate(packageName, packageVersion),
        repository = packageDescription.repository.get,
        solutions = solutions.map { x =>
          VersionSolution(x.map {
            case (pack, ver) =>
              pack -> VersionInfo(ver, getReleaseDate(pack, getVersion(ver).get))
          })
        })

      log.verb(s"Done converting to json $packageName@$packageVersion")
      Some(dbEntry)
    }
  }

  private def releaseDate(packageName: String,
                          packageVersion: SemverWithUnnormalized): Option[DateTime] = {
    registry.get(packageName) match {
      case Some(p) =>
        p.versions.find(v => getVersion(v._1).contains(packageVersion)) match {
          case Some(m) =>
            val matchingDesc = m._2
            matchingDesc.releaseDate match {
              case None =>
                log.warn(
                  s"No time specified for package version $packageVersion of $packageName")
                None
              case Some(date) =>
                DateTime.fromIsoDateTimeString(date) match {
                  case Some(realDate) =>
                    Some(realDate)
                  case _ => None
                }
            }
          case _ =>
            log.warn(s"No time field for $packageName in the registry")
            None
        }
      case None => None
    }
  }

  private def versionsSatisfying(
    dependencyName: String,
    dependencyConstraint: String,
    existedOn: Option[DateTime],
    ignoreDate: Boolean = true): Iterable[SemverWithUnnormalized] = {

    registry.get(dependencyName) match {
      case None =>
        log.warn(s"Key not found for $dependencyName")
        Set()
      case Some(packageDesc) =>
        // Constraints-based selection
        val semVerKeys = packageDesc.versions.keys.flatMap(x => getVersion(x))
        val satisfyingConstraint = semVerKeys.filter { semversion =>
          val outcome =
            Try(semversion.satisfies(Versions.toConstraint(dependencyConstraint)))
          outcome match {
            case Failure(exception) =>
              log.warn(
                s"Impossible to check for satisfactory $semversion against dependencyConstraint $dependencyConstraint\n$exception")
              false
            case Success(value) =>
              value
          }
        }
        if (satisfyingConstraint.isEmpty || ignoreDate) {
          satisfyingConstraint
        } else {

          val releaseDatesClicks = satisfyingConstraint
            .map { v =>
              val releaseClick = getReleaseDate(dependencyName, v) match {
                case None => -1
                case Some(d) => d.clicks
              }
              (releaseClick, v)
            }
            .toList
            .sortBy(_._1)

          existedOn match {
            case None => satisfyingConstraint
            case Some(afterDate) =>
              // the date of the latest version available at existedOn
              val lidx = releaseDatesClicks.lastIndexWhere { i =>
                i._1 < afterDate.clicks
              }

              // If there was no solution for this package
              if (lidx < 0) return Set()

              val l = releaseDatesClicks(lidx)._1

              // we take all the ones with the same existedOn date, important for taking all the -1
              val lstart = releaseDatesClicks.indexWhere(i => i._1 == l)

              releaseDatesClicks.drop(lstart).map(_._2)
          }
        }
    }
  }

}
