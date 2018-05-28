package distilling.server.regression_typechecking

object TracingResult {
  implicit class PathOps(path: AccessPath) {}

  type AccessPath = List[Label]

  object PathOrdering extends Ordering[AccessPath] {
    override def compare(x: AccessPath, y: AccessPath): Int = {
      val common = Math.min(x.length, y.length)
      val firstDiff = x.take(common).zip(y.take(common)).find {
        case (xel, yel) =>
          xel.toString.compareTo(yel.toString) != 0
      }

      firstDiff match {
        case None if x.length == y.length => 0
        case None if x.length > y.length  => 1
        case None                         => -1
        case Some((xel, yel))             => xel.toString.compareTo(yel.toString)
      }

    }
  }
}

trait TracingResult extends AnyRef {
  def observations: List[Observation]
  def unifications: List[Unification]
}

case class LearningFailure(msg: String,
                           observations: List[Observation] = List(),
                           unifications: List[Unification] = List())
    extends TracingResult {
  override def toString =
    s"tests failed: $msg\n observations:\n${observations.mkString("\n")}\nunifications:${unifications
      .mkString("\n")}"
}

case class Learned(observations: List[Observation] = List(),
                   unifications: List[Unification] = List())
    extends TracingResult {
  override def toString =
    s"observations:\n${observations.mkString("\n")}\nunifications:${unifications.mkString("\n")}"
}

case class Observation(path: List[Label],
                       `type`: String,
                       stack: Option[String],
                       propertyTypes: Map[String, String]) {
  override def toString = s"${path.mkString(".")}: ${`type`} @\n${stack.getOrElse("")}"
}

case class Unification()

trait Label

case class ArgLabel(idx: Int) extends Label {
  override def toString = s"arg$idx"
}

case class ApplicationLabel(numArgs: Int, constructorCall: Boolean) extends Label {
  override def toString = s"${if (constructorCall) "new" else ""}($numArgs)"
}

case class PropertyLabel(name: String) extends Label {
  override def toString: String = name
}

case class RequireLabel(name: String) extends Label {
  override def toString: String = s"require($name)"
}

case class AccessLabel() extends Label {
  override def toString: String = s"AccessLabel"
}

case class JsSymbol(kind: String)
