package distilling.server

import distilling.server.commands.Common
import distilling.server.utils._

object Cmd extends App {
  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)

  println("Result:\n   " + Common.replParser(args.toList).getOrElse("failed"))
  System.exit(0)
}
