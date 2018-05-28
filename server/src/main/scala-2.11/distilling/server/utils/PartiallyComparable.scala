package distilling.server.utils

trait PartiallyComparable[T] {
  def leq(o: T): Boolean
}
