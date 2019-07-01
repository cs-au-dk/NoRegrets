package backend.package_handling

import java.nio.file.Path

import backend.datastructures._
import backend.regression_typechecking._
import backend.utils.Utils.writeToFile
import backend.utils._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.write
import org.json4s.{JValue, _}

import scala.io.Source

object Instrumentation {

  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)

  /**
    * Uses relative paths for output file and api-tracer path, so allowing
    * an instrumented package to be run both outside and inside docker containers.
    * This is a debugging options because relative paths are more sensible to cwd
    * of running tests.
    */
  val DEBUG_USE_RELATIVE_PATH_FOR_API_TRACER: Boolean = false

  def patchPackageJson(packageFile: Path, depReplacement: PackageAtVersion): JValue = {
    patchPackageJson(packageFile, List(depReplacement))
  }

  def patchPackageJson(
    packageFile: Path,
    depReplacements: List[PackageAtVersion],
    devDepReplacements: List[PackageAtVersion] = List(),
    //verifyExistenceOfPackageDep is used in NoRegretsPlus mode where we ignore tags
    //and therefore may end up with a version of a client not having the required library dependency
    //In that case we throw an exception such that the client is not included in successfuls
    verifyExistenceOfPackageDep: Option[PackageAtVersion] = None): JValue = {
    val pkgFileDir = packageFile.getParent
    val originalJson: JObject = parse(
      Source.fromFile(packageFile.toFile).getLines.mkString).asInstanceOf[JObject]

    if (verifyExistenceOfPackageDep.isDefined &&
        !originalJson.obj.toMap
          .apply("dependencies")
          .asInstanceOf[JObject]
          .obj
          .toMap
          .contains(verifyExistenceOfPackageDep.get.packageName)) {
      throw new Exception(
        s"package.json did not contain required dependency ${verifyExistenceOfPackageDep.get}")
    }

    val additionalDependency: JValue = "dependencies" ->
      depReplacements.map(pver => pver.packageName -> pver.packageVersion).toMap
    val additionalDevDependency: JValue = "devDependencies" ->
      devDepReplacements.map(pver => pver.packageName -> pver.packageVersion).toMap
    val maskingscripts: JValue = "scripts" ->
      Map(
        "lint" -> "echo lint",
        "eslint" -> "echo eslint",
        "jslint" -> "echo jslint",
        "standard" -> "echo standard")

    val newPackageJson = originalJson merge additionalDependency merge additionalDevDependency merge maskingscripts

    writeToFile(packageFile, pretty(render(newPackageJson)))

    newPackageJson
  }

  case class ProxyHelpersOptions(libraryVersion: String,
                                 libraryModuleName: String = null,
                                 output: Path = null,
                                 collectStackTraces: Boolean = true,
                                 detailedStackTraces: Boolean = false,
                                 regressionInfo: Option[APIModel] = None,
                                 testDistillationMode: Boolean = true,
                                 blacklistSideEffects: Boolean = true,
                                 coverage: Boolean = false) {

    case class Options(libraryVersion: String,
                       output: String,
                       libraryModuleName: String,
                       regressionInfo: APIModel,
                       collectStackTraces: Boolean,
                       detailedStackTraces: Boolean = false,
                       collectOwnProperties: Boolean = false,
                       enableUnifications: Boolean = false,
                       withUnifications: Boolean = false,
                       testDistillationMode: Boolean = true,
                       blacklistSideEffects: Boolean = true,
                       coverage: Boolean = false)

    def toJson(packageFolder: Path): String = {
      val outputPath =
        if (DEBUG_USE_RELATIVE_PATH_FOR_API_TRACER)
          packageFolder.toAbsolutePath.relativize(output.toAbsolutePath).toString
        else output.toAbsolutePath.toString
      val options =
        Options(
          libraryVersion,
          outputPath,
          libraryModuleName,
          regressionInfo.orNull,
          collectStackTraces,
          detailedStackTraces,
          testDistillationMode = testDistillationMode,
          blacklistSideEffects = blacklistSideEffects,
          coverage = coverage)
      implicit val formats: Formats = SerializerFormats.commonSerializationFormats
      write(options)
    }
  }

}
