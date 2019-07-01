package backend.utils

import scala.reflect.ClassTag

/**
  * Basic logging functionality.
  */
object Log {

  /**
    * Log levels.
    */
  object Level extends Enumeration {
    type Level = Value
    val None, Error, Warn, Info, Debug, Verbose = Value
  }

  val defaultLevel = Level.None

  /**
    * Constructs a new logger.
    * @param forcedLevel log level
    * @param ct class the logger belongs to
    */
  def logger[A: ClassTag](
    forcedLevel: Level.Level = defaultLevel
  )(implicit ct: ClassTag[A]): Logger = {
    Logger(ct.runtimeClass.getSimpleName, forcedLevel)
  }
}
