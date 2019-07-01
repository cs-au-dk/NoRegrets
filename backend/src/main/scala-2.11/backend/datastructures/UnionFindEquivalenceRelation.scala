package backend.datastructures

class UnionFindEquivalenceRelation[S](states: Set[S],
                                      ordering: Ordering[S],
                                      withAllEdges: Boolean = false)
    extends MutableRelation[S] {

  val uf = new UnionFind(ordering)

  if (withAllEdges && states.nonEmpty) {
    val head = states.head
    states.foreach(uf.unify(head, _))
  }

  private def sets(): Map[S, Set[S]] = uf.members().toSet.groupBy(uf.find)

  override def edgesCount: Long =
    sets().values.map(s => s.size.toLong * s.size.toLong).sum

  override def edges() = {
    val s = sets()
    for {
      singleSet <- s.toStream
      p <- singleSet._2.toStream
      q <- singleSet._2.toStream
    } yield {
      (p, q)
    }
  }

  override def +=(edge: (S, S)) = uf.unify(edge._1, edge._2)

  override def -=(edge: (S, S)) = ???

  override def contains(elem: (S, S)) = {
    uf.members().contains(elem._1) &&
    uf.members().contains(elem._2) &&
    uf.find(elem._1) == uf.find(elem._2)
  }

  override def +(elem: (S, S)) = ???

  override def -(elem: (S, S)) = ???
}
