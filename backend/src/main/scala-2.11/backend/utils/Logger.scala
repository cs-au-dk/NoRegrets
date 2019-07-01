package backend.utils

import backend.utils.Log.Level
import backend.utils.Logger._

import scala.collection.mutable

/**
  * A logger.
  */
final case class Logger(var tag: String, level: Log.Level.Level) {

  val TAG_MAX_LEN = 20

  tag = s"[${tag.padTo(TAG_MAX_LEN, ' ').substring(0, TAG_MAX_LEN)}]" //@${Thread.currentThread().getName}]"

  private def log(message: => String, t: Throwable, msgLev: Log.Level.Level): Unit = {
    if (msgLev.id <= level.id || msgLev.id < Log.defaultLevel.id) {
      var account = 0
      val color = msgLev match {
        case Level.Error =>
          account -= 9; Console.BOLD + Console.RED
        case Level.Warn =>
          account -= 9; Console.BOLD + Console.YELLOW
        case Level.Info =>
          account -= 9; Console.BOLD + Console.BLUE
        case _ => account -= 1; Console.RESET
      }
      val preamble = s"$color$tag: " // ${msgLev.toString.toUpperCase}: "
      val space = (0 until (preamble.length + account)).map(_ => " ").mkString("")
      val nmessage = message.replace("\r", "").replace("\n", s"\r\n$space")

      val finalText = (s"$preamble$nmessage\n" + Console.RESET + Console.RESET)

      if (Logger.isRetained)
        Logger.retainedMessages.append(finalText)
      else {
        Logger.recorder.synchronized {
          Logger.recorder.foreach {
            case (handle, builder) =>
              handle match {
                case _: AllThreadsLogHandle => builder.append(finalText)
                case h: ThreadedLogHandle =>
                  if (h.thread == Thread.currentThread()) builder.append(finalText)
              }
          }
        }
        print(finalText)
      }
    }
    if (t != null) t.printStackTrace()

  }

  /**
    * Writes a message to the log at level "error" .
    */
  def error(message: => String): Unit = log(message, null, Log.Level.Error)

  /**
    * Writes a message and a stack trace to the log at level "error".
    */
  def error(message: => String, t: Throwable): Unit =
    log(message, t, Log.Level.Error)

  /**
    * Writes a message to the log at level "warn" .
    */
  def warn(message: => String): Unit = log(message, null, Log.Level.Warn)

  /**
    * Writes a message and a stack trace to the log at level "warn".
    */
  def warn(message: => String, t: Throwable): Unit =
    log(message, t, Log.Level.Warn)

  /**
    * Writes a message to the log at level "info" .
    */
  def info(message: => String): Unit = log(message, null, Log.Level.Info)

  /**
    * Writes a message and a stack trace to the log at level "info".
    */
  def info(message: => String, t: Throwable): Unit =
    log(message, t, Log.Level.Info)

  /**
    * Writes a message to the log at level "debug" .
    */
  def debug(message: => String): Unit = log(message, null, Log.Level.Debug)

  /**
    * Writes a message and a stack trace to the log at level "debug".
    */
  def debug(message: => String, t: Throwable): Unit =
    log(message, t, Log.Level.Debug)

  /**
    * Writes a message to the log at level "verbose" .
    */
  def verb(message: => String): Unit = log(message, null, Log.Level.Verbose)

  /**
    * Writes a message and a stack trace to the log at level "verbose".
    */
  def verb(message: => String, t: Throwable): Unit =
    log(message, t, Log.Level.Verbose)

  /**
    * Create visual separation in the log with a title.
    */
  def separationTitle(message: => String, separator: Char = '='): Unit = {
    val separatorStr = (0 until (message.length)).map(_ => separator).mkString("")
    log(s"\n${separatorStr}\n${message}\n${separatorStr}\n", null, Log.Level.Info)
  }

  val runtime = Runtime.getRuntime
  def printJvmStats(): Unit = {
    val mb = 1024 * 1024

    println("##### Heap utilization statistics [MB] #####")

    //Print used memory
    println(
      "Used Memory:"
        + (runtime.totalMemory() - runtime.freeMemory()) / mb)

    //Print free memory
    println(
      "Free Memory:"
        + runtime.freeMemory() / mb)

    //Print total available memory
    println("Total Memory:" + runtime.totalMemory() / mb)

    //Print Maximum available memory
    println("Max Memory:" + runtime.maxMemory() / mb)
  }

  def startRecord(threadedLog: Boolean = false): LogHandle = {
    val handle =
      if (threadedLog) new ThreadedLogHandle(Thread.currentThread())
      else new AllThreadsLogHandle()
    Logger.recorder.synchronized {
      Logger.recorder.put(handle, new mutable.StringBuilder())
    }
    handle
  }

  def stopRecord(h: LogHandle): Option[mutable.StringBuilder] = {
    Logger.recorder.synchronized {
      Logger.recorder.get(h)
    }
  }
}

object Logger {
  private var retained = 0
  private val retainedMessages = new StringBuilder()

  private val recorder =
    mutable.WeakHashMap[LogHandle, StringBuilder]()

  def isRetained = retained != 0

  def retainMessages(): Unit = { retained += 1 }

  def releaseMessages(): Unit = {
    retained -= 1
    if (!isRetained) {
      print(retainedMessages)
      dropMessages()
    }
  }
  // FIXME: Support nested dropping
  def dropMessages(): Unit = {
    if (retained == 1)
      retainedMessages.clear()
  }

  trait LogHandle
  class AllThreadsLogHandle() extends LogHandle
  class ThreadedLogHandle(val thread: Thread) extends LogHandle
}
