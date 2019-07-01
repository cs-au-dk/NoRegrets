package infrastructure

import java.nio.file.Paths

import backend.utils.ExecutionUtils
import org.scalatest._

class PathifiedExecution extends FlatSpec with Matchers with Inspectors {

  implicit val executor: ExecutionUtils.ProcExecutor =
    ExecutionUtils.defaultCustomPathsExecutor
  println(s"Executor: ${executor}")
  implicit val cwd = Paths.get("")

  "PATH" should "should contains the optimistic env" in {
    println(executor.executeCommand(Seq("printenv")).log)
  }

  "node" should "should be version 6.x in pathified executions" in {
    assert(executor.executeCommand(Seq("node", "--version")).log.contains("v6."))
  }

  "jshint" should "be available and do nothing" in {
    assert(ExecutionUtils.executeWithExecutor("jshint").log.contains("Fake jshint"))
  }

  "node should have increased heap size" should "" in {
    assert(
      ExecutionUtils
        .executeWithExecutor(
          "node allocate.js",
          env = Map("NODE_OPTIONS" -> "--max_old_space_size=8192"))
        .code == 0)
  }

}
