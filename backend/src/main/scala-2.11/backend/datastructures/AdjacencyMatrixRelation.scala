package backend.datastructures

import scala.collection.mutable
import scala.language.implicitConversions

object AdjacencyMatrixRelation {
  implicit def pairToMatrix[S](s: Set[(S, S)]): AdjacencyMatrixRelation[S] = {
    val matrix = new AdjacencyMatrixRelation[S](s.map(_._1) ++ s.map(_._2))
    s.foreach(matrix += _)
    matrix
  }
}

class AdjacencyMatrixRelation[S](states: Set[S], withAllEdges: Boolean = false)
    extends MutableRelation[S] {
  val initSize = states.size
  val totalSize = initSize * initSize
  val bSet = new mutable.BitSet(totalSize)
  val size = bSet.size.toLong

  val sMap: mutable.HashMap[S, Int] = mutable.HashMap(states.zipWithIndex.map {
    case (s, i) => s -> i
  }.toSeq: _*)
  val iMap: mutable.HashMap[Int, S] = sMap.map { case (s, i) => i -> s }

  def edgesCount: Long = bSet.size.toLong

  if (withAllEdges) {
    for (i <- 0 until totalSize) {
      bSet += i
    }
  }

  def edges(): Stream[(S, S)] = {
    (for {
      s <- states.toStream
      d <- states.toStream
    } yield { if (contains(s, d)) Some(s, d) else None }).flatten
  }

  def +=(edge: (S, S)): Unit = {
    bSet.add(edgeIdx(edge))
  }

  def -=(edge: (S, S)): Unit = {
    bSet.remove(edgeIdx(edge))
  }

  private def edgeIdx(edge: (S, S)): Int = {
    sMap(edge._1) * initSize + sMap(edge._2)
  }

  def contains(elem: (S, S)): Boolean = bSet(edgeIdx(elem))

  def iterator: Iterator[(S, S)] = edges().iterator

  def +(elem: (S, S)): Set[(S, S)] = this.edges().toSet + elem

  def -(elem: (S, S)): Set[(S, S)] = this.edges().toSet - elem
}
