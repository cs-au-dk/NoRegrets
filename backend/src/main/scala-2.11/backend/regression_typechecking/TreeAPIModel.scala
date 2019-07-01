package backend.regression_typechecking

import scala.collection.mutable

/**
  * Auxilliary class used to process the APIModel
  * The processing comprises
  * - Merging invocations where the subtrees of the invocation have the same subtrees.
  */
object TreeAPIModel {
  val EnablePreserveValueOriginPathMaps = true
}

case class TreeAPIModel(obs: List[Observation],
                        testTimeMillis: Number,
                        clientAtVersion: String,
                        preCompressSize: Option[Number] = None) {

  private val observations = obs.sortBy(o => o.path.length)
  private var vops: Option[Set[AccessPath]] = None
  var rootNodes: Option[Map[Label, StdNode]] = None

  //Only called from unit-test (we cannot use apply in the unit-test since apply doesn't call create)
  //I know this is very ugly, but if we keep TreeAPIModel this way we avoid a lot of recomputations
  def this(obs: List[Observation], testTimeMillis: Number) {
    this(obs, testTimeMillis, "customClient@1.0.0")
    create()
  }

  def this(a: APIModel, testTimeMillis: Number, clientName: String) = {
    this(a.observations, testTimeMillis, clientName)
    create()
  }

  def this(a: LearningFailure, testTimeMillis: Number, clientName: String) {
    this(a.observations, testTimeMillis, clientName)
    create()
  }

  private def create(): Unit = {
    //Turn into tree and process bottom-up
    rootNodes =
      if (observations.nonEmpty)
        Some(
          observations
            .takeWhile(_.path.length == 1)
            .map(o ⇒ o.path.labels.head → StdNode(o.path.labels.head, o))
            .toMap)
      else None

    //We take the tail since the require(xyz) is the head
    try {
      observations
        .filter(_.path.length >= 2)
        .foreach(o => {
          val labs = o.path.labels
          rootNodes.get(labs.head).insertChild(labs.tail, o)
        })
    } catch {
      case e: AssertionError ⇒
        assert(
          assertion = false,
          s"TreeAPIModel Construction failed for client ${clientAtVersion} with message ${e.getMessage}")
    }

    vops = Some(observations.flatMap {
      case o: ReadObservation ⇒ o.valueOriginPath
      case o: WriteObservation ⇒ o.valueOriginPath
    }.toSet)

    rootNodes.map(_.values.map(_.markVopSubtrees(vops.getOrElse(Set()))))
  }

  def toAPIModel(): APIModel = {
    //val observations =
    //  rootNodes.map(_.flatMap(_._2.DFSConstructObservations())).getOrElse(List()).toList
    //Notice, coverage doesn't matter here since we don't care about NoRegrets coverage in NoRegrets mode.
    APIModel(observations, List(), testTimeMillis, CoverageObject(), List())
  }

  def prettyString(): String = {
    rootNodes
      .map(_.values.map(_.prettyString()).mkString("\n"))
      .getOrElse("Empty TreeAPIModel")
  }

  //Called from compress

  def compress(requireEqualArgs: Boolean = true): TreeAPIModel = {
    val compressedRoots =
      rootNodes.map(
        _.mapValues(_.compressSubtree(requireEqualArgs).asInstanceOf[StdNode]))

    val compTree = TreeAPIModel(
      compressedRoots
        .map(_.flatMap(_._2.DFSConstructObservations()))
        .getOrElse(List())
        .toList,
      testTimeMillis,
      clientAtVersion,
      Some(size()))
    compTree.rootNodes = rootNodes
    compTree
    //val thisCopy = this.copy()
    //thisCopy.rootNodes = compressedRoots
    //thisCopy
  }

  def size(): Number =
    observations.length //

  //Only for debugging purposes
  def treeSize = rootNodes.map(_.map(_._2.subtreeSize()).sum).getOrElse(0)
}

case class FunctionStats(path: AccessPath,
                         dynamicCalls: Int,
                         returnTypes: Int,
                         synthesizableReturnTypes: Int) {
  def toCSV: String =
    s"${path.labels.mkString(".")},${dynamicCalls},${returnTypes},${synthesizableReturnTypes}"

  override def toString: String =
    s"${path.labels.mkString(".")}\tdyncalls ${dynamicCalls}\treturnTypes ${returnTypes}\tsynthesizableReturnTypes ${synthesizableReturnTypes}"
}

class ModelStatistics() {

  type Stats = Set[FunctionStats]
  var functionStats: Stats = Set()

  def this(uncompressedModel: TreeAPIModel, compressedModel: TreeAPIModel) = {
    this()
    functionStats = {
      def walk(n: Node, nComp: Node, covariant: Boolean = true): Stats = {
        (n, nComp) match {
          case (n: StdNode, nComp: StdNode) =>
            val covariantMod = if (n.l.isInstanceOf[ArgLabel]) !covariant else covariant
            nComp.children.flatMap {
              case (l, c) =>
                walk(n.children(l), c, covariantMod)
            }.toSet
          case (n: InvocationNode, nComp: InvocationNode) => {
            val childStats = nComp.children.flatMap {
              case (l, c) =>
                walk(n.children(l), c, covariant)
            }.toSet
            if (covariant) {
              val dynamicCalls = n.children.size
              val returnTypes = nComp.children.size
              val synthesizeAbleReturnTypes = nComp.children.count {
                case (_, c) => c.synthesizable()
              }
              childStats + FunctionStats(
                nComp.path,
                dynamicCalls,
                returnTypes,
                synthesizeAbleReturnTypes)
            } else {
              childStats
            }
          }
          case _ =>
            throw new RuntimeException(
              "Unexpectedly saw nodes of different types in function stats walk")
        }
      }
      (uncompressedModel.rootNodes, compressedModel.rootNodes) match {
        case (Some(rootNodeMap), Some(compRootNodeMap)) =>
          rootNodeMap
            .flatMap(rootNode ⇒ walk(rootNode._2, compRootNodeMap(rootNode._1)))
            .toSet
        case _ => Set() //This case should only be possible with (None, None) matches
      }
    }
  }

  //The compiler won't accept the Stats type alias at this place for some reason
  def this(stats: Set[FunctionStats]) = {
    this()
    functionStats = stats
  }

  def toCSV: List[String] = {
    functionStats.map(_.toCSV).toList
  }

}

trait Node {
  def markVopSubtrees(vops: Set[AccessPath]): Boolean
  def prettyString(indent: Int = 0): String
  def compressSubtree(requireEqualArgs: Boolean): Node
  def DFSConstructObservations(): List[Observation]
  def subtreeSize(): Int
  def deepEquals(n: Node, requireEqualArguments: Boolean): Boolean
  def synthesizable(covariant: Boolean = true): Boolean
  def requiresVop(): Boolean
}

case class IdLabel(id: Number) extends Label {}

case class InvocationNode(path: AccessPath) extends Node {
  val children: mutable.Map[Number, StdNode] = mutable.Map()
  val l = path.labels.last

  def getSpecificInvocation(id: Number): Option[StdNode] = {
    children.get(id)
  }

  def insertSubtree(i: Number, n: StdNode) = children += (i -> n)

  def prettyString(indent: Int): String =
    s"${"  " * indent}${l} :{\n${children.map { case (_, n) => n.prettyString(indent + 1) }.mkString("\n")}\n${"  " * indent}}"

  /**
    * Remove the subtrees that have a similar structural type (deepCompare).
    * This is done to avoid redundant calls in the model checking that don't provide any new type information.
    *
    *
    * We want to remove two trees if their return type is equivalent (the arguments can be different)
    * What's important for the arguments, is that none of them vop args.
    */
  override def compressSubtree(requireEqualArgs: Boolean): Node = {
    val subtrees = children.values.toSet

    val compressedSubtrees =
      subtrees.map(_.compressSubtree(requireEqualArgs)).asInstanceOf[Set[StdNode]]

    val toRemoveSubtrees = compressedSubtrees.foldLeft(Set[StdNode]()) {
      case (acc, t1) => {
        if (!acc.contains(t1)) {
          val isCallbackWithVopArgs =
            (n: StdNode) ⇒ if (n.o.path.isCallBackArg) n.hasVopArgs() else false
          lazy val t1IsCallbackWithVopArgs = isCallbackWithVopArgs(t1)
          val equalTrees = compressedSubtrees.filter(
            t2 =>
              t1 != t2 &&
                //We cannot remove a callback invocation taking a VOP arg since we otherwise would not be able to choose the correct application value
                //for the case where the callback is called with a value that has the removed VOP
                //Note, the logic is path.isCallBackArg => !t1.hasVopArgs
                !t1IsCallbackWithVopArgs &&
                !isCallbackWithVopArgs(t2) &&

                !t1.hasVopArgs() &&
                !t2.hasVopArgs() &&
                t1.deepEquals(t2, requireEqualArgs)) + t1
          //We remove the element with the min id since that's the subtree which we want to use in the model checking
          acc ++ (equalTrees - equalTrees.minBy(t => t.o.id))
        } else acc
      }
    }

    val remainingSubtrees = compressedSubtrees -- toRemoveSubtrees

    val resNode = InvocationNode(path)
    remainingSubtrees.foreach(t => resNode.insertSubtree(t.l.asInstanceOf[IdLabel].id, t))
    resNode
  }

  override def deepEquals(n: Node, requireEqualArguments: Boolean): Boolean = {
    if (n.isInstanceOf[InvocationNode]) {
      val nCast = n.asInstanceOf[InvocationNode]
      children.values.size == nCast.children.values.size &&
      children.forall {
        case (l, c) =>
          nCast.children.contains(l) &&
            c.o.valueOriginPath.isEmpty && //Do not merge functions if they return vops since these may internally have different types
            nCast.children(l).o.valueOriginPath.isEmpty && //Instance of the above comment
            c.deepEquals(nCast.children(l), requireEqualArguments)
      }
    } else {
      false
    }
  }

  override def DFSConstructObservations(): List[Observation] = {
    children.values.toList.sortBy(n => n.o.id).foldLeft(List(): List[Observation]) {
      case (acc, n) => acc ::: n.DFSConstructObservations()
    }
  }

  override def subtreeSize(): Int = children.values.foldLeft(1) {
    case (acc, n) => acc + n.subtreeSize()
  }

  override def synthesizable(covariant: Boolean = true): Boolean =
    children.values.forall(c => c.synthesizable(covariant))

  override def markVopSubtrees(vops: Set[AccessPath]): Boolean =
    this.children.values.foldLeft(false) {
      case (agg, child) ⇒ child.markVopSubtrees(vops) || agg
    }
  override def requiresVop(): Boolean = children.values.exists(_.requiresVop)
}

case class StdNode(l: Label, o: Observation, isVop: Boolean = false) extends Node {

  var thisIsVop = isVop

  override def markVopSubtrees(vops: Set[AccessPath]): Boolean = {
    val hasChildVop = this.children.values.foldLeft(false) {
      case (agg, child) ⇒ child.markVopSubtrees(vops) || agg
    }
    if (hasChildVop || vops.contains(o.path)) {
      thisIsVop = true
    }
    thisIsVop
  }

  val children: mutable.Map[Label, Node] = mutable.Map()

  def compressSubtree(requireEqualArgs: Boolean): Node = {
    val res = StdNode(l, o, thisIsVop)
    children.foreach {
      case (l, n) => res.insertSubTree(l, n.compressSubtree(requireEqualArgs))
    }
    res
  }

  /**
    *
    * @return true if at least one of the argument requires a VOP to be synthesized
    */
  def hasVopArgs(): Boolean = {
    children
      .filter { case (k, _) ⇒ k.isInstanceOf[ArgLabel] }
      .values
      .exists(_.requiresVop)
  }

  /**
    *
    * @return true if the node recursively requires a VOP to be synthesized
    */
  override def requiresVop(): Boolean = {
    this.o.valueOriginPath.isDefined || this.children.values.exists(_.requiresVop())
  }

  private def insertSubTree(l: Label, n: Node) = children += (l -> n)

  def deepEquals(n: Node, requireEqualArguments: Boolean): Boolean = {
    if (n.isInstanceOf[StdNode]) {
      val nCast = n.asInstanceOf[StdNode]
      if (!requireEqualArguments && l.isInstanceOf[ArgLabel] && nCast.l
            .isInstanceOf[ArgLabel]) {
        true
      } else {
        val oneNodeIsVop = if (TreeAPIModel.EnablePreserveValueOriginPathMaps) {
          thisIsVop || nCast.thisIsVop
        } else {
          false
        }
        !oneNodeIsVop &&
        o.`type` == nCast.o.`type` &&
        children.values.size == nCast.children.values.size &&
        children.forall {
          case (l, c) =>
            nCast.children.contains(l) &&
              c.deepEquals(nCast.children(l), requireEqualArguments)
        }
      }
    } else {
      false
    }
  }

  //Notice, observations must be sorted.
  //That's why we assert that ls == Nil whenever we create a new StdNode
  def insertChild(path: List[Label], o: Observation): Unit = {
    path match {
      case l :: ls => {
        val n = l match {
          case ApplicationLabel(numArgs, cCall, id) => {
            val lStripped = StrippedApplicationLabel(numArgs, cCall)
            children.get(lStripped) match {
              case Some(app: InvocationNode) => {
                app.getSpecificInvocation(id) match {
                  case Some(n) => n
                  case None => {
                    assert(
                      ls == Nil,
                      s"Failed in TreeAPIModel child insertion of ${o.path} on ${this.o.path} - assert 1")
                    val idNode = StdNode(IdLabel(id), o)
                    app.insertSubtree(id, idNode)
                    idNode
                  }
                }
              }
              case _ => {
                //Strip all of the application identifiers
                val path = o.path.labels.map {
                  case l: ApplicationLabel =>
                    StrippedApplicationLabel(l.numArgs, l.constructorCall)
                  case l => l
                }
                assert(
                  ls == Nil,
                  s"Failed in TreeAPIModel child insertion of ${o.path} on ${this.o.path} - assert 2")
                val nInv = InvocationNode(AccessPath(path))
                val nId = StdNode(IdLabel(id), o)
                nInv.insertSubtree(id, nId)
                children += (lStripped -> nInv)
                nId
              }
            }
          }
          case _ => {
            children.get(l) match {
              case Some(node) =>
                //This cast is safe since we already handled the case for InvocationNodes above
                node.asInstanceOf[StdNode]
              case _ => {
                assert(
                  ls == Nil,
                  s"Failed in TreeAPIModel child insertion of ${o.path} on ${this.o.path} - assert 3")
                val n = StdNode(l, o)
                children += (l -> n)
                n
              }
            }
          }
        }

        ls match {
          case l :: _ => n.insertChild(ls, o)
          case Nil    => //We are done
        }
      }

      case nil =>
      //Some error
    }
  }

  override def prettyString(indent: Int): String =
    s"${"  " * indent}${l} - ${o.`type`} :{\n${children
      .map { case (_, n) => n.prettyString(indent + 1) }
      .mkString("\n")}\n${"  " * indent}}"

  override def DFSConstructObservations(): List[Observation] = {
    children.values.toList.foldLeft(List(o)) {
      case (acc, n) => acc ::: n.DFSConstructObservations()
    }
  }

  override def subtreeSize(): Int = {
    children.values.foldLeft(1) { case (acc, n) => acc + n.subtreeSize() }
  }

  override def synthesizable(covariant: Boolean = true): Boolean =
    (covariant || !o.`type`.contains("function")) &&
      children.forall {
        case (l, n) =>
          n.synthesizable(l match {
            case l: ArgLabel => !covariant
            case _           => covariant
          })
      }
}
