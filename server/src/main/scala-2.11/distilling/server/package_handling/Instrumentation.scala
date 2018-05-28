package distilling.server.package_handling

import java.nio.file.Path

import distilling.server.datastructures._
import distilling.server.regression_typechecking._
import distilling.server.utils.Utils.writeToFile
import distilling.server.utils._
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

  def patchPackageJson(packageFile: Path,
                       depReplacements: List[PackageAtVersion],
                       devDepReplacements: List[PackageAtVersion] = List()): JValue = {
    val pkgFileDir = packageFile.getParent
    val originalJson: JObject = parse(
      Source.fromFile(packageFile.toFile).getLines.mkString).asInstanceOf[JObject]
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

    var newPackageJson = originalJson merge additionalDependency merge additionalDevDependency merge maskingscripts

    writeToFile(packageFile, pretty(render(newPackageJson)))

    newPackageJson
  }

  case class ProxyHelpersOptions(libraryModuleName: String = null,
                                 output: Path = null,
                                 collectStackTraces: Boolean = true,
                                 detailedStackTraces: Boolean = false,
                                 regressionInfo: Option[Learned] = None) {

    case class Options(output: String,
                       libraryModuleName: String,
                       regressionInfo: Learned,
                       collectStackTraces: Boolean,
                       detailedStackTraces: Boolean = false,
                       collectOwnProperties: Boolean = false,
                       enableUnifications: Boolean = false,
                       withUnifications: Boolean = false)

    def toJson(packageFolder: Path): String = {
      val outputPath =
        if (DEBUG_USE_RELATIVE_PATH_FOR_API_TRACER)
          packageFolder.toAbsolutePath.relativize(output.toAbsolutePath).toString
        else output.toAbsolutePath.toString
      val options =
        Options(
          outputPath,
          libraryModuleName,
          regressionInfo.orNull,
          collectStackTraces,
          detailedStackTraces)

      implicit val formats: Formats = SerializerFormats.commonSerializationFormats
      write(options)
    }
  }

}
