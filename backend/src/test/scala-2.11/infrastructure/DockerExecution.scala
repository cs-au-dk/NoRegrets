package infrastructure

import java.nio.file.Paths

import backend.Globals
import backend.utils.ExecutionUtils
import org.scalatest._

class DockerExecution extends FlatSpec with Matchers with Inspectors {

  implicit val executor = ExecutionUtils.rightExecutor(Globals.benchmarksImage)
  println(s"Executor: ${executor}")
  implicit val cwd = Paths.get("")

  "node" should "should be version 6.x inside docker" in {
    assert(ExecutionUtils.executeWithExecutor("node --version").log.contains("v6."))
  }

  "current working directory" should "be the correct" in {
    assert(
      ExecutionUtils
        .executeWithExecutor("pwd")
        .log
        .contains(Paths.get("").toAbsolutePath.toString))
  }

}
