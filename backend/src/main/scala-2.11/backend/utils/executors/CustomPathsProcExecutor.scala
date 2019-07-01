package backend.utils.executors

import java.nio.file.Path
import java.util.{Timer, TimerTask}

import backend.Globals
import backend.utils.ExecutionUtils.ProcExecutor
import backend.utils._

import scala.concurrent._
import scala.concurrent.duration._
import scala.sys.process.Process
import scala.util.Try

case class CustomPathsProcExecutor(nodePath: Path, optimisticEnv: Boolean = true)
    extends ProcExecutor {
  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)

  private val curPath = System.getenv().get("PATH")

  private var newPath = List(nodePath.toAbsolutePath.toString, curPath)

  if (optimisticEnv) {
    if (!Globals.optimisticEnvLocation.toFile.exists())
      throw new RuntimeException(
        s"Unable to find the env directory: ${Globals.optimisticEnvLocation}")
    newPath = Globals.optimisticEnvLocation.toAbsolutePath.toString :: newPath
  }

  def executeCommand(
    cmd: Seq[String],
    timeout: Duration = Duration.Inf,
    logStdout: Boolean = true,
    printFailure: Boolean = true,
    silent: Boolean = false,
    env: Map[String, String] = Map())(implicit cwd: Path): ProcessExecutionResult = {

    val extendedPath =
      if (env.contains("PATH"))
        env("PATH") :: newPath
      else
        newPath

    val extEnv = env + ("PATH" -> extendedPath.mkString(":"))
    val logger = StrLogger(logStdout)

    log.info(s"Executing (PATHIFIED): (cd ${cwd.toAbsolutePath}; ${cmd.mkString(" ")})")
    val p = Process(
      command = Seq("bash", "-c", cmd.mkString(" ")), //FIXME: bash -c needed for PATH to be considered, how can we handle spaces ??
      cwd = cwd.toAbsolutePath.toFile,
      extraEnv = extEnv.toSeq: _*).run(logger)

    val res = Try {
      Await.result(Future({
        log.info(s"Waiting for ${cmd.mkString(" ")} to finish")
        p.exitValue()
      }), timeout)
    }.recover {
      case e: TimeoutException =>
        log.error(s"Timeout $timeout for command ${cmd.mkString(" ")}")

        if (printFailure)
          log.info(s"Partial log at the moment of the timeout:\n${logger.result}")

        // Destroying nicely
        Try(p.destroy())

        // and scheduling forced destruction
        new Timer().schedule(new TimerTask {
          override def run(): Unit = Try {
            log.error(s"Forcing destruction of process ${cmd.mkString(" ")}")
            ForciblyDestroyProcessHelper.forciblyDestroy(p)
          }
        }, 5000)

        throw e
      case e =>
        log.error(e.toString)
        if (printFailure)
          log.error(s"Log at the moment of error: ${logger.result}")
        throw e
    }.get

    if (res != 0 && printFailure)
      log.info(s"Result:${cmd.mkString(" ")}: $res\n${logger.result}")

    ProcessExecutionResult(res, logger.result)
  }
}
