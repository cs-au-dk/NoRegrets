package backend.datastructures

import scala.collection.mutable

class UnionFind[A](ordering: Ordering[A]) {

  private val parent = mutable.Map[A, A]()

  def unify(t1: A, t2: A): Unit = {
    mkSet(t1)
    mkSet(t2)
    val rep1 = find(t1)
    val rep2 = find(t2)

    if (rep1 == rep2) return

    mkUnion(rep1, rep2)
  }

  def find(t: A): A = {
    mkSet(t)
    if (parent(t) != t)
      parent += t -> find(parent(t))
    parent(t)
  }

  def members(): collection.Set[A] = parent.keySet

  private def mkUnion(t1: A, t2: A): Unit = {
    if (ordering.lteq(t1, t2)) {
      parent += t1 -> t2
    } else {
      parent += t2 -> t1
    }
  }

  private def mkSet(t: A): Unit = {
    if (!parent.contains(t))
      parent += (t -> t)
  }
}
