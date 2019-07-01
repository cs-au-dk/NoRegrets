package backend.datastructures

import upickle.Js

object NpmRegistry {

  type NpmRegistry = String => NpmPackageDescription

  type EagerNpmRegistry = Map[String, NpmPackageDescription]

  type LazyNpmRegistry = String => NpmPackageDescription

}

case class NpmPackageDescription(_id: String,
                                 `dist-tags`: Map[String, String],
                                 name: String,
                                 versions: Map[String, VersionDesc], //,
                                 stars: Option[Int])

object NpmPackageDescription {
  // Speed up upickle
  //implicit val pkl = upickle.default.macroRW[NpmPackageDescription]
}

case class VersionDesc(main: Option[Js.Value],
                       scripts: Option[Js.Value],
                       dependencies: Option[Map[String, String]],
                       repository: Option[String],
                       releaseDate: Option[String])

object VersionDesc {
  // Speed up upickle
  implicit val pkl = upickle.default.macroRW[VersionDesc]
}
