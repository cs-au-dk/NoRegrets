package backend.datastructures

import com.vdurmont.semver4j.Semver
import com.vdurmont.semver4j.Semver.SemverType
import backend.utils.PartiallyComparable
import backend.utils.Utils._

final case class SolutionOutcome(solution: VersionSolution,
                                 testOutcome: Int,
                                 msg: String)
    extends PartiallyComparable[SolutionOutcome] {

  override def leq(o: SolutionOutcome) = SolutionOutcome.isLeq(this, o)

  override lazy val hashCode = super.hashCode
}

object SolutionOutcome {
  val getVersion = CachedMemoize { x: String =>
    new Semver(x, SemverType.NPM)
  }

  val isLeq = CachedMemoize.build2(uncachedIsLeq)

  def uncachedIsLeq(a: SolutionOutcome, b: SolutionOutcome): java.lang.Boolean = {
    if (a.solution.dependencies.keys.toSet
          .subsetOf(b.solution.dependencies.keys.toSet)) {
      a.solution.dependencies.keys.forall { dep =>
        val thisVersion =
          SolutionOutcome.getVersion(a.solution.dependencies(dep).version)
        val otherVersion =
          SolutionOutcome.getVersion(b.solution.dependencies(dep).version)
        Boolean.box(
          thisVersion.isEquivalentTo(otherVersion) || thisVersion
            .isLowerThan(otherVersion)
        )
      }
    } else {
      Boolean.box(false)
    }
  }
}
