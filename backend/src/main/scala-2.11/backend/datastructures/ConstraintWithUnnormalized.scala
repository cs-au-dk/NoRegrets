package backend.datastructures

case class ConstraintWithUnnormalized(ver: String, unnormalized: String) {
  override def toString = s"(${ver}, $unnormalized)"
}
