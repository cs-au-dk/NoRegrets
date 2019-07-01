package backend.utils
import java.nio.file._
import java.util.concurrent._

import backend.Globals
import backend.utils.WorkaroundTaskSupport._
import backend.utils.executors._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

object ExecutionUtils {
  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)

  trait ProcExecutor {

    implicit val executionContext: ExecutionContext =
      ExecutionContext.fromExecutorService(
        Executors.newCachedThreadPool(new DefaultThreadFactory()))

    def executeCommand(
      cmd: Seq[String],
      timeout: Duration = Duration.Inf,
      logStdout: Boolean = true,
      logFailure: Boolean = true,
      silent: Boolean = false,
      env: Map[String, String] = Map())(implicit cwd: Path): ProcessExecutionResult
  }

  val defaultCustomPathsExecutor = {
    val os = System.getProperty("os.name")
    Globals
      .knownSystemPaths(os)
      .node8
      .map(path => CustomPathsProcExecutor(Paths.get(path)))
      .getOrElse(StandardProcExecutor())
  }

  def rightExecutor(imageName: String): ProcExecutor =
    if (!isDockerEnv && Globals.usePathExecutors)
      defaultCustomPathsExecutor
    else StandardProcExecutor()

  lazy val isDockerEnv = Paths.get("/.dockerenv").toFile.exists()

  def executeWithExecutor(cmd: String,
                          timeout: Duration = Duration.Inf,
                          logStdout: Boolean = true,
                          onlineLogging: Boolean = true,
                          printFailure: Boolean = true,
                          env: Map[String, String] = Map())(
    implicit cwd: Path,
    executor: ProcExecutor): ProcessExecutionResult = {
    executor.executeCommand(cmd.split(" "), timeout, logStdout, printFailure, env = env)
  }

  def execute(
    cmd: String,
    timeout: Duration = Duration.Inf,
    logStdout: Boolean = true,
    onlineLogging: Boolean = true,
    printFailure: Boolean = true,
    env: Map[String, String] = Map())(implicit cwd: Path): ProcessExecutionResult = {
    StandardProcExecutor().executeCommand(
      cmd.split(" "),
      timeout,
      logStdout,
      printFailure,
      env = env)
  }

}
