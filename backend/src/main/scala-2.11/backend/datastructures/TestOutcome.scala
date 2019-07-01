package backend.datastructures

import com.ibm.couchdb.TypeMapping

final case class TestOutcome(packageName: String,
                             packageVersion: String,
                             solutions: List[SolutionOutcome],
                             msg: Option[String] = None) {
  override lazy val hashCode = super.hashCode
}

object TestOutcome {
  val typeMapping =
    TypeMapping(
      classOf[TestOutcome] -> "TestOutcome",
      classOf[SolutionOutcome] -> "SolutionOutcome",
      classOf[VersionSolution] -> "VersionSolution"
    )
}

object TOutcome extends Enumeration {
  val OK, KO, BAD = Value
}
