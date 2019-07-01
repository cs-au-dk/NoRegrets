package backend.datastructures

import com.vdurmont.semver4j.Semver
import com.vdurmont.semver4j.Semver.SemverType

object Versions {
  def toVersion(s: String): Option[SemverWithUnnormalized] = {
    try {
      val v = SemverWithUnnormalized(new Semver(normalizeVersion(s), SemverType.NPM), s)
      Some(v)
    } catch {
      case _: Throwable =>
        None
    }
  }

  def toConstraint(s: String): ConstraintWithUnnormalized =
    ConstraintWithUnnormalized(normalizeConstraint(s), s)

  private def normalizeVersion(s: String) = {
    var news = s.replaceAll("([0-9]+\\.[0-9]+\\.[0-9]+-)([0-9])", "$1r$2")
    news = news.replaceAll("([0-9]+\\.[0-9]+\\.[0-9]+)([a-zA-z])", "$1-$2")
    news
  }

  private def normalizeConstraint(s: String) = {
    var news = normalizeVersion(s)
    if (!news.matches("^[0-9]")) {
      news = news.replaceAll("\\.x+", "\\.0")
    }
    news
  }

  lazy val earliestVersion =
    SemverWithUnnormalized(new Semver(normalizeVersion("0.0.0"), SemverType.NPM), "0.0.0")

}
