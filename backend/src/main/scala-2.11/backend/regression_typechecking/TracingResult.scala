package backend.regression_typechecking

import org.json4s.JValue

object TracingResult {
  implicit class PathOps(path: AccessPath) {}

  object PathOrdering extends Ordering[AccessPath] {
    override def compare(x: AccessPath, y: AccessPath): Int = {
      val common = Math.min(x.length, y.length)
      val firstDiff = x.labels.take(common).zip(y.labels.take(common)).find {
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
  var clientSize: Long = 0
}

case class LearningFailure(msg: String,
                           testTimeMillis: Number,
                           observations: List[Observation] = List(),
                           unifications: List[Unification] = List())
    extends TracingResult {
  override def toString =
    s"tests failed: $msg\n observations:\n${observations.mkString("\n")}\nunifications:${unifications
      .mkString("\n")}"
}

//TODO
//Consider deprecating the unifications
case class APIModel(observations: List[Observation] = List(),
                    unifications: List[Unification] = List(),
                    testTimeMillis: Number,
                    coverageObject: CoverageObject,
                    coverageFiles: List[String])
    extends TracingResult {
  override def toString =
    s"observations:\n${observations.mkString("\n")}\nunifications:${unifications.mkString("\n")}"
}

trait PrimitiveValue
//case class JString(s: String) extends PrimitiveValue
//case class JNumber(i: Number) extends PrimitiveValue
//case class JBool(b: Boolean) extends PrimitiveValue
//case class JUndefined() extends PrimitiveValue
//case class JNull() extends PrimitiveValue
//case class JSymbol() extends PrimitiveValue

trait Observation {
  val path: AccessPath
  val id: Option[Int]
  val stack: Option[String]
  val `type`: String
  val valueOriginPath: Option[AccessPath]

  def isVopObs = valueOriginPath.isDefined
}

case class WrittenValue(`type`: String, value: JValue)
case class WriteObservation(override val path: AccessPath,
                            override val id: Option[Int],
                            override val stack: Option[String],
                            override val valueOriginPath: Option[AccessPath],
                            writtenValue: WrittenValue)
    extends Observation {
  override val `type` = "write"
  override def toString: String = s"path ${path} = ${writtenValue}"
}

case class ReadObservation(override val path: AccessPath,
                           `type`: String,
                           override val stack: Option[String],
                           propertyTypes: Map[String, String],
                           value: Option[JValue],
                           override val id: Option[Int],
                           override val valueOriginPath: Option[AccessPath],
                           isKnown: Boolean,
                           isNative: Boolean,
                           didThrow: Boolean)
    extends Observation {
  override def toString = s"$path: ${`type`} @\n${stack.getOrElse("")}"
}

//case class SideEffects(sideEffects: Map[String, JValue])
case class ValueOriginInfo(valueOriginPath: AccessPath, sideEffects: String) {}

case class Unification()

trait Label

// Use a case class to get structural equality
case class AccessPath(labels: List[Label]) {
  override def toString: String = labels.mkString(".")

  lazy val length = labels.length

  /**
    * @returns true if the path represents an argument of a callBack
    *         Note, callback args are in covariant position
    */
  def isCallBackArg: Boolean = {
    val argLabs = labels.count(_.isInstanceOf[ArgLabel])
    argLabs >= 2 && argLabs % 2 == 0
  }

  //Transform all ApplicationLabels to StrippedApplicationLabels to make comparisons easier
  def stripIds(): AccessPath = {
    AccessPath(labels.map {
      case ApplicationLabel(n, c, _) ⇒ StrippedApplicationLabel(n, c)
      case l: Label ⇒ l
    })
  }
}

case class ArgLabel(idx: Int) extends Label {
  override def toString = s"arg$idx"
}

case class ApplicationLabel(numArgs: Int, constructorCall: Boolean, identifier: Number)
    extends Label {
  //override def toString = s"${if (constructorCall) "new" else ""}($numArgs)_${identifier}"
  //We leave out the identifier since printing it is going to break all the SFD tests
  override def toString = s"${if (constructorCall) "new" else ""}($numArgs)"
}

/**
  * A version of ApplicationLabel without a specific invocation identifier
  * @param numArgs
  * @param constructorCall
  */
case class StrippedApplicationLabel(numArgs: Int, constructorCall: Boolean)
    extends Label {
  override def toString = s"${if (constructorCall) "new" else ""}($numArgs)"
}

case class PropertyLabel(name: String) extends Label {
  override def toString: String = name
}

case class WriteLabel(name: String, identifier: Number) extends Label {
  override def toString: String = s"write($name)"
}

case class RequireLabel(name: String) extends Label {
  override def toString: String = s"require($name)"
}

case class AccessLabel() extends Label {
  override def toString: String = s"AccessLabel"
}

// FIXME: Not implmented yet
case class JsSymbol(kind: String)
