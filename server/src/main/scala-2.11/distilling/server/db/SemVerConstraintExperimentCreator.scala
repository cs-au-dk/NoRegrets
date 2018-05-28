package distilling.server.db

import com.ibm.couchdb.TypeMapping
import distilling.server.RegistryReader
import distilling.server.datastructures.NpmRegistry.EagerNpmRegistry
import distilling.server.datastructures._
import distilling.server.utils._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
import scala.sys.process.Process

object SemVerConstraintExperimentCreator extends App {
  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)
  val provider = new CouchClientProvider()
  val semverDb = provider.couchdb.db("semver-experiments", SemVerMapping.typeMapping)
  val registryReader = new RegistryReader()
  var globalRegistry: EagerNpmRegistry = null

  log.info(s"Running${this.getClass.getSimpleName} with argsç ${args.toList}")

  //val packages = registryReader.allNpmPackages(Some(1000))
  //val computations = Futures.groupedCollect(packages, 64){case Some((name, desc)) => createMapping(desc)}
  //Await.ready(computations, Duration.Inf)

  val registry = registryReader.loadNpmRegistry()

  val computations = {
    globalRegistry = registry
    println("Registry obtained, now starting making requests")
    Futures.groupedCollect(registry.values.toSeq, 64)(createMapping)
  }

  Await.ready(computations, Duration.Inf)
  log.info("Done, exiting!")

  def resolveConstraintDegrees(versDesc: VersionDesc): Map[String, ConstraintInfo] = {
    versDesc.dependencies match {
      case Some(dependencies) =>
        dependencies.map {
          case (dep, dependencyConstraint) =>
            //registryReader.getDocument(dep) match {
            globalRegistry.get(dep) match {
              case Some(pkgDesc) =>
                pkgDesc.`dist-tags`.get("latest") match {
                  case Some(latest) =>
                    val cmd = Seq(
                      "node",
                      "./constraint-resolver/runner.js",
                      dependencyConstraint,
                      latest)
                    val resolvedOutput = Process(cmd).lineStream
                    log.debug(
                      s"Resolved output [${resolvedOutput}] for ${pkgDesc.name}:${dependencyConstraint} with latest version ${latest}")
                    if (resolvedOutput.nonEmpty) {
                      val lst = resolvedOutput.last
                      resolvedOutput.last match {
                        case "UNDER" => lst
                        case "OVER"  => lst
                        case "IDEAL" => lst
                        case _       => "UNKNOWN"
                      }
                      dep -> ConstraintInfo(dependencyConstraint, resolvedOutput.last)
                    } else {
                      dep -> ConstraintInfo(dependencyConstraint, "UNKNOWN")
                    }
                  case None =>
                    log.info(s"Could not find latest version for dependency $dep")
                    dep -> ConstraintInfo(dependencyConstraint, "UNKNOWN")
                }
              case None =>
                log.info(s"Unable to find package $dep in the registry")
                dep -> ConstraintInfo(dependencyConstraint, "UNKNOWN")
            }
        }
      case None =>
        Map()
    }
  }

  def createMapping(packageDesc: NpmPackageDescription): Future[Unit] = {
    Future {
      try {
        packageDesc.`dist-tags`.get("latest") match {
          case Some(vers) =>
            packageDesc.versions.get(vers) match {
              case Some(versDesc) =>
                val name = packageDesc.name
                val res = try {
                  resolveConstraintDegrees(versDesc)
                } catch {
                  case e: Throwable =>
                    log.info(s"Resolving constraint degree failed for package $name")
                    e.printStackTrace()
                    Map[String, ConstraintInfo]()
                }
                semverDb.docs
                  .create(SemVerMapping(name, vers, res), name)
                  .retry((1 to 10).map(_ => 5.seconds), _ => true)
                  .map { ok =>
                    log.info(s"${name} saved, success: ${ok.ok}")
                  }
                  .unsafePerformSync
              case None =>
                log.error(s"Unable to get version $vers from ${packageDesc.name}")
            }
          case None =>
            log.info(s"No latest version found for package ${packageDesc.name}")
        }
      } catch {
        case e: Throwable =>
          log.error(s"makeRequest for ${packageDesc.name} failed with exception $e")
      }
    }
  }

  object SemVerMapping {
    val typeMapping = TypeMapping(classOf[SemVerMapping] -> "SemVerMapping")
  }

  case class SemVerMapping(packageName: String,
                           packageVersion: String,
                           dependencies: Map[String, ConstraintInfo])

  case class ConstraintInfo(constraint: String, semVerConstraintDegree: String)

}
