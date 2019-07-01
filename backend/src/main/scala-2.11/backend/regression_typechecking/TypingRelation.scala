package backend.regression_typechecking

import backend.regression_typechecking.TaggedObservation.TypeStr

trait TypingRelation {
  def diffTypes(path: AccessPath,
                typesBase: Set[TypeStr],
                typesPost: Set[TypeStr],
                typesBaseMap: Map[TypeStr, Set[Observer]],
                typesPostMap: Map[TypeStr, Set[Observer]]): Option[(AccessPath, TypeDiff)]

  def onlyInBase(
    path: AccessPath,
    baseKnown: Map[AccessPath, Set[TaggedObservation]]): Option[(AccessPath, TypeDiff)]
  def onlyInPost(
    path: AccessPath,
    postKnown: Map[AccessPath, Set[TaggedObservation]]): Option[(AccessPath, TypeDiff)]

}

class ByDiffTypingRelation extends TypingRelation {
  override def diffTypes(
    path: AccessPath,
    typesBase: Set[TypeStr],
    typesPost: Set[TypeStr],
    typesBaseMap: Map[TypeStr, Set[Observer]],
    typesPostMap: Map[TypeStr, Set[Observer]]): Option[(AccessPath, TypeDiff)] = {
    var types: Map[TypeStr, TypeDiff] = Map()
    typesBase.foreach(t =>
      types += (t -> NoObservationInPost(typesBaseMap(t).toList, Set())))
    typesPost.foreach(t =>
      types += (t -> NoObservationInBase(typesPostMap(t).toList, Set())))
    if (types.nonEmpty)
      Some((path -> DifferentObservations(types)))
    None
  }

  override def onlyInBase(path: AccessPath,
                          baseKnown: Map[AccessPath, Set[TaggedObservation]])
    : Option[(AccessPath, TypeDiff)] = {
    val direction = Direction.getDirection(path)

    direction match {
      case Direction.PreMustBeSubType => {
        //If PreMustBeSubtype then π_pre(p) <: π_post(p), but π_post(p) = ◦ so this relation always hold
        None
      }
      case Direction.PostMustBeSubType => {
        Some(
          path -> NoObservationInPost(
            baseKnown(path).flatMap(_.observer).toList,
            baseKnown(path).map(_.`type`)))
      }
    }
  }

  override def onlyInPost(path: AccessPath,
                          postKnown: Map[AccessPath, Set[TaggedObservation]])
    : Option[(AccessPath, TypeDiff)] = {
    val direction = Direction.getDirection(path)

    direction match {
      case Direction.PreMustBeSubType => {
        Some(
          path -> NoObservationInBase(
            postKnown(path).flatMap(_.observer).toList,
            postKnown(path).map(_.`type`)))
      }
      case Direction.PostMustBeSubType => {
        //If PreMustBeSubtype then π_post(p) <: π_pre(p), but π_pre(p) = ◦ so this relation always hold
        //noop
        None
      }
    }
  }
}

object Direction extends Enumeration {
  type Direction = Value
  val PostMustBeSubType, PreMustBeSubType = Value

  def getDirection(path: AccessPath): Direction = {
    val argAccessCount = path.labels.count(p => p.isInstanceOf[ArgLabel])
    argAccessCount % 2 match {
      case 0 => Direction.PostMustBeSubType
      case 1 => Direction.PreMustBeSubType
    }
  }
}

object TypeRegressionPaperTypingRelation extends TypingRelation {

  override def diffTypes(
    path: AccessPath,
    typesBase: Set[TypeStr],
    typesPost: Set[TypeStr],
    typesBaseMap: Map[TypeStr, Set[Observer]],
    typesPostMap: Map[TypeStr, Set[Observer]]): Option[(AccessPath, TypeDiff)] = {

    val direction = Direction.getDirection(path)

    val baseType =
      //RichType.OrType.buildOrType(typesBase.map(RichType.fromAndTypeString(_)).toList)
      RichType.OrType.buildOrType(typesBase.map(RichType.fromTypeString(_)).toList)
    val postType =
      RichType.OrType.buildOrType(typesPost.map(RichType.fromTypeString(_)).toList)
    //RichType.OrType.buildOrType(typesPost.map(RichType.fromAndTypeString(_)).toList)

    direction match {
      case Direction.PostMustBeSubType =>
        if (!RichType.relation(postType, baseType)) {
          return Some(path -> RelationFailure(typesBaseMap.map {
            case (k, v) => k -> v.toList
          }, s" ${postType} <: ${baseType}"))
        }
      case Direction.PreMustBeSubType =>
        if (!RichType.relation(baseType, postType)) {
          return Some(path -> RelationFailure(typesPostMap.map {
            case (k, v) => k -> v.toList
          }, s" ${postType} :> ${baseType}"))
        }
    }
    None
  }

  override def onlyInBase(path: AccessPath,
                          baseKnown: Map[AccessPath, Set[TaggedObservation]])
    : Option[(AccessPath, TypeDiff)] = {
    val direction = Direction.getDirection(path)

    direction match {
      case Direction.PreMustBeSubType => {
        //If PreMustBeSubtype then π_pre(p) <: π_post(p), but π_post(p) = ◦ so this relation always hold
        None
      }
      case Direction.PostMustBeSubType => {
        val baseType = RichType.OrType.buildOrType(
          baseKnown(path).map(obs => RichType.fromTypeString(obs.`type`)).toList)
        Some(
          path -> RelationFailure(
            baseKnown(path).map(obs => obs.`type` -> obs.observer.toList).toMap,
            s" ◦ <: ${baseType}"))
      }
    }
  }

  override def onlyInPost(path: AccessPath,
                          postKnown: Map[AccessPath, Set[TaggedObservation]])
    : Option[(AccessPath, TypeDiff)] = {
    val direction = Direction.getDirection(path)

    direction match {
      case Direction.PreMustBeSubType => {
        val postType = RichType.OrType.buildOrType(
          postKnown(path).map(obs => RichType.fromTypeString(obs.`type`)).toList)
        Some(
          path -> RelationFailure(
            postKnown(path).map(obs => obs.`type` -> obs.observer.toList).toMap,
            s" ◦ <: ${postType}"))
      }
      case Direction.PostMustBeSubType => {
        //If PreMustBeSubtype then π_post(p) <: π_pre(p), but π_pre(p) = ◦ so this relation always hold
        None
      }
    }
  }
}

object RichType extends Enumeration {
  type RichType = Value

  val Object, Function, Number, Boolean, Symbol, String, Undefined, Throws, Array,
  ArrayBuffer, Buffer, DataView, Date, Error, EvalError, Float32Array, Float64Array,
  Int16Array, Int32Array, Int8Array, Map, Promise, RangeError, ReferenceError, RegExp,
  Set, SyntaxError, TypeError, URIError, Uint16Array, Uint32Array, Uint8Array,
  Uint8ClampedArray, WeakMap, WeakSet, EventEmitter, Stream, Ignore, ServerResponse,
  IncomingMessage = Value

  /**
    * ∧, v and BaseType
    */
  trait CompoundType {
    def toString(): String
  }

  //@deprecated
  //We no longer need the intersection type since we model prototypes explicitly
  case class AndType(left: RichType.CompoundType, right: RichType.CompoundType)
      extends CompoundType {
    override def toString: String = s"(${left} ∧ ${right})"
  }

  case class OrType(left: RichType.CompoundType, right: RichType.CompoundType)
      extends CompoundType {
    override def toString: String = s"(${left} v ${right})"
  }

  case class BaseType(t: RichType.RichType) extends CompoundType {
    override def toString: String = s"$t"
  }

  object AndType {
    def buildAndType(seq: List[RichType.CompoundType]): CompoundType = {
      seq match {
        case b :: Nil => b
        case b :: bs  => AndType(b, buildAndType(bs))
        case Nil      => throw new RuntimeException("Unexpected empty list of compundTypes")
      }
    }
  }

  object OrType {
    def buildOrType(seq: List[RichType.CompoundType]): CompoundType = {
      seq match {
        case b :: Nil => b
        case b :: bs  => OrType(b, buildOrType(bs))
        case Nil      => throw new RuntimeException("Unexpected empty list of compundTypes")
      }
    }
  }

  /**
    * return t1 <: t2, and optionally the AndType that should be blamed if the relation fails
    *
    * @param t1
    * @param t2
    */
  def relation(t1: RichType.CompoundType, t2: RichType.CompoundType): Boolean = {
    def baseRelation(t1: BaseType, t2: BaseType): Boolean = {
      var t1SuperTypes = scala.collection.mutable.Set(t1)
      var fixpoint = false
      while (!fixpoint) {
        val closedTypes = t1SuperTypes.flatMap(t => subtypeClosure(t)) ++ t1SuperTypes
        if (t1SuperTypes.sameElements(closedTypes)) {
          fixpoint = true
        }
        t1SuperTypes = closedTypes
      }

      t1SuperTypes contains t2
    }

    def subtypeClosure(t: BaseType): Set[RichType.BaseType] = {
      t.t match {
        case Object ⇒ scala.collection.immutable.Set(BaseType(Undefined))
        case Function ⇒ scala.collection.immutable.Set(BaseType(Object))
        case Number ⇒ scala.collection.immutable.Set()
        case Boolean ⇒ scala.collection.immutable.Set()
        case Symbol ⇒ scala.collection.immutable.Set()
        case String ⇒ scala.collection.immutable.Set()
        case Undefined ⇒ scala.collection.immutable.Set()
        case Throws ⇒ scala.collection.immutable.Set()
        case Array ⇒ scala.collection.immutable.Set(BaseType(Object))
        case ArrayBuffer ⇒ scala.collection.immutable.Set(BaseType(Array)) //?
        case Buffer ⇒ scala.collection.immutable.Set()
        case DataView ⇒ scala.collection.immutable.Set(BaseType(Object))
        case Date ⇒ scala.collection.immutable.Set(BaseType(Object))
        case Error ⇒ scala.collection.immutable.Set()
        case EvalError ⇒ scala.collection.immutable.Set()
        case Float32Array ⇒ scala.collection.immutable.Set(BaseType(Array))
        case Float64Array ⇒ scala.collection.immutable.Set(BaseType(Array))
        case Int16Array ⇒ scala.collection.immutable.Set(BaseType(Array))
        case Int32Array ⇒ scala.collection.immutable.Set(BaseType(Array))
        case Int8Array ⇒ scala.collection.immutable.Set(BaseType(Array))
        case Map ⇒ scala.collection.immutable.Set(BaseType(Object))
        case Promise ⇒ scala.collection.immutable.Set(BaseType(Object))
        case RangeError ⇒ scala.collection.immutable.Set(BaseType(Error)) //?
        case ReferenceError ⇒ scala.collection.immutable.Set(BaseType(Error)) //?
        case RegExp ⇒ scala.collection.immutable.Set(BaseType(Object))
        case Set ⇒ scala.collection.immutable.Set(BaseType(Object))
        case SyntaxError ⇒ scala.collection.immutable.Set(BaseType(Error)) //?
        case TypeError ⇒ scala.collection.immutable.Set(BaseType(Error)) //?
        case URIError ⇒ scala.collection.immutable.Set(BaseType(Error)) //?
        case Uint16Array ⇒ scala.collection.immutable.Set(BaseType(Array))
        case Uint32Array ⇒ scala.collection.immutable.Set(BaseType(Array))
        case Uint8Array ⇒ scala.collection.immutable.Set(BaseType(Array))
        case Uint8ClampedArray ⇒ scala.collection.immutable.Set(BaseType(Array))
        case WeakMap ⇒ scala.collection.immutable.Set(BaseType(Object))
        case WeakSet ⇒ scala.collection.immutable.Set(BaseType(Object))
        case EventEmitter ⇒ scala.collection.immutable.Set(BaseType(Object))
        case Stream ⇒ scala.collection.immutable.Set(BaseType(Object))
        case Ignore ⇒ RichType.values.map(BaseType(_))
        case IncomingMessage ⇒ scala.collection.immutable.Set()
        case ServerResponse ⇒ scala.collection.immutable.Set()
      }
    }

    (t1 == t2) || (t1 match {
      case OrType(l, r) => {
        relation(l, t2) && relation(r, t2)
      }
      case a @ AndType(l, r) => {
        relation(l, t1) || relation(r, t2)
      }
      case b1 @ BaseType(_) =>
        t2 match {
          case OrType(l, r) => {
            relation(b1, l) || relation(b1, r)
          }
          case a @ AndType(l, r) => {
            relation(b1, l) && relation(b1, r)
          }
          case b2 @ BaseType(_) => baseRelation(b1, b2)
        }
    })
  }

  //@Deprecated
  //Do not use. See definition of the and type (intersection type)
  def fromAndTypeString(s: TypeStr): RichType.CompoundType = {
    //remove the [ ]
    val andTypeString = s.substring(1).substring(0, s.size - 2)
    val baseTypeStrings = andTypeString.split(", ")
    val baseTypes = baseTypeStrings.map(fromTypeString(_))
    RichType.AndType.buildAndType(baseTypes.toList)
  }

  def fromTypeString(s: String): RichType.CompoundType = {
    val enum = s match {
      case "object" ⇒ Object
      case "function" ⇒ Function
      case "number" ⇒ Number
      case "boolean" ⇒ Boolean
      case "symbol" ⇒ Symbol
      case "string" ⇒ String
      case "undefined" ⇒ Undefined
      case "throws" ⇒ Throws
      case "Array" ⇒ Array
      case "ArrayBuffer" ⇒ ArrayBuffer
      case "Buffer" ⇒ Buffer
      case "DataView" ⇒ DataView
      case "Date" ⇒ Date
      case "Error" ⇒ Error
      case "EvalError" ⇒ EvalError
      case "Float32Array" ⇒ Float32Array
      case "Float64Array" ⇒ Float64Array
      case "Int16Array" ⇒ Int16Array
      case "Int32Array" ⇒ Int32Array
      case "Int8Array" ⇒ Int8Array
      case "Map" ⇒ Map
      case "Promise" ⇒ Promise
      case "RangeError" ⇒ RangeError
      case "ReferenceError" ⇒ ReferenceError
      case "RegExp" ⇒ RegExp
      case "Set" ⇒ Set
      case "SyntaxError" ⇒ SyntaxError
      case "TypeError" ⇒ TypeError
      case "URIError" ⇒ URIError
      case "Uint16Array" ⇒ Uint16Array
      case "Uint32Array" ⇒ Uint32Array
      case "Uint8Array" ⇒ Uint8Array
      case "Uint8ClampedArray" ⇒ Uint8ClampedArray
      case "WeakMap" ⇒ WeakMap
      case "WeakSet" ⇒ WeakSet
      case "EventEmitter" ⇒ EventEmitter
      case "Stream" ⇒ Stream
      case "ServerResponse" ⇒ ServerResponse
      case "IncomingMessage" ⇒ IncomingMessage
      case "__IGNORE__" ⇒ Ignore
      case "__NON-EXISTING__" ⇒ Undefined
      case _ => throw new RuntimeException(s"Unknown TypeString $s")
    }
    BaseType(enum)
  }
}
