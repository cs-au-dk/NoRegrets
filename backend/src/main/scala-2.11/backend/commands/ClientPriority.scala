package backend.commands

object ClientPriority extends Enumeration {
  type ClientPriority = Value
  val OnlyNewest, OnlyOldest, All = Value
}
