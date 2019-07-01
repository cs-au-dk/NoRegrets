package backend.datastructures

import com.vdurmont.semver4j.Semver

object SemverWithUnnormalized {
  object SemverOrdering extends Ordering[SemverWithUnnormalized] {
    override def compare(x: SemverWithUnnormalized, y: SemverWithUnnormalized) =
      x.ver.compareTo(y.ver)
  }
}

case class SemverWithUnnormalized(ver: Semver, unnormalized: String) {
  override def toString = s"(${ver.getOriginalValue}, $unnormalized)"
  def satisfies(c: ConstraintWithUnnormalized) = ver.satisfies(c.ver)
}
