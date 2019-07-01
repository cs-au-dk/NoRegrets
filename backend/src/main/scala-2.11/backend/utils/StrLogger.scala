package backend.utils

import scala.sys.process.ProcessLogger

case class StrLogger(print: Boolean = false) extends ProcessLogger {
  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)
  private val stringBuffer: StringBuffer = new StringBuffer()
  def out(s: => String): Unit = {
    if (print) log.info(s)
    stringBuffer.append(s)
    stringBuffer.append("\n")
  }
  def err(s: => String): Unit = {
    if (print) log.info(s)
    stringBuffer.append(s)
    stringBuffer.append("\n")
  }

  def buffer[T](f: => T): T = f

  def result = stringBuffer.toString
}
