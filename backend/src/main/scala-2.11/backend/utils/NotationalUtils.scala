package backend.utils

import backend.datastructures.PackageAtVersion
import scala.language.implicitConversions

object NotationalUtils {

  /*
   *  Transform a packagename@version into a PackageAtVersion
   */
  implicit def atNotationToPackage(s: String): PackageAtVersion = {
    val split = s.split("@")
    PackageAtVersion(split(0), split(1))
  }

  implicit class SI(x: Double) {
    def mega: Double = {
      x * Math.pow(10, 6)
    }
  }

}
