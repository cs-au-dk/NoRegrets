package backend.datastructures

import akka.http.scaladsl.model.DateTime
import com.ibm.couchdb.TypeMapping

object Solution {

  val typeMapping = TypeMapping(
    classOf[Solution] -> "Solution",
    classOf[VersionSolution] -> "VersionSolution",
    classOf[VersionInfo] -> "VersionInfo"
  )
}

case class Solution(packageName: String,
                    packageVersion: String,
                    releaseDate: Option[DateTime],
                    repository: String,
                    solutions: List[VersionSolution])
