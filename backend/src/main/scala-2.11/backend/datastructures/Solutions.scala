package backend.datastructures

import akka.http.scaladsl.model.DateTime

case class VersionSolution(dependencies: Map[String, VersionInfo]) extends AnyVal

case class VersionInfo(version: String, releaseDate: Option[DateTime])
