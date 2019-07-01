package backend.datastructures

trait MutableRelation[S] {

  def edgesCount: Long

  def edges(): Stream[(S, S)]

  def +=(edge: (S, S)): Unit

  def -=(edge: (S, S)): Unit

  def contains(elem: (S, S)): Boolean

  def +(elem: (S, S)): Set[(S, S)]

  def -(elem: (S, S)): Set[(S, S)]
}
