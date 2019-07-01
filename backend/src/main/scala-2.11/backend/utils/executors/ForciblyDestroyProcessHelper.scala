package backend.utils.executors

import java.lang.{Process => JProcess}
import scala.sys.process.Process

object ForciblyDestroyProcessHelper {
  def forciblyDestroy(p: Process): Unit = {

    // getting inner java process though reflection
    val innerProcesses =
      p.getClass.getDeclaredFields
        .filter(_.getAnnotatedType.getType.getTypeName == classOf[JProcess].getTypeName)
    if (innerProcesses.isEmpty) throw new RuntimeException("Inner process not found")
    innerProcesses.foreach { field =>
      field.setAccessible(true)
      val jp = field.get(p).asInstanceOf[JProcess]
      jp.destroyForcibly()
    }
  }
}
