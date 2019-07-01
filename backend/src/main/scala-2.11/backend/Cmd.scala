package backend

import backend.commands.Common
import backend.utils._

object Cmd extends App {
  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)

  println("Result:\n   " + Common.replParser(args.toList))
  System.exit(0)
}
