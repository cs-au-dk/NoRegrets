package backend.utils

trait PartiallyComparable[T] {
  def leq(o: T): Boolean
}
