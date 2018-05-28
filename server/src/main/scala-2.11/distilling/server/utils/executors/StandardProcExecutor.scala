package distilling.server.utils.executors

import java.nio.file.Path
import java.util.TimerTask

import distilling.server.Globals
import distilling.server.utils.ExecutionUtils.ProcExecutor
import distilling.server.utils._

import scala.concurrent._
import scala.concurrent.duration._
import scala.sys.process.Process
import scala.util.Try

case class StandardProcExecutor(optimisticEnv: Boolean = true) extends ProcExecutor {
  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)

  private var curPath = System.getenv().get("PATH")
  if (optimisticEnv) {
    if (!Globals.optimisticEnvLocation.toFile.exists())
      throw new RuntimeException(
        s"Unable to find the env directory: ${Globals.optimisticEnvLocation}")
    curPath = s"${Globals.optimisticEnvLocation.toAbsolutePath.toString}:${curPath}"
  }

  def executeCommand(
    cmd: Seq[String],
    timeout: Duration = Duration.Inf,
    logStdout: Boolean = true,
    printFailure: Boolean = true,
    env: Map[String, String] = Map())(implicit cwd: Path): ProcessExecutionResult = {
    val logger = StrLogger(logStdout)
    log.info(s"Executing: (cd ${cwd.toAbsolutePath}; ${cmd.mkString(" ")})")

    val newEnv = env + ("PATH" -> curPath)
    log.debug(s"Environment is: $newEnv")

    val p =
      Process(
        command = Seq("bash", "-c", cmd.mkString(" ")),
        cwd = cwd.toAbsolutePath.toFile,
        extraEnv = newEnv.toSeq: _*).run(logger)
    val res = Try {
      Await.result(Future {
        log.info(s"Waiting for ${cmd.mkString(" ")} to finish")
        p.exitValue()
      }, timeout)
    }.recover {
      case e: TimeoutException =>
        log.error(s"Timeout $timeout for command  ${cmd.mkString(" ")}")
        if (printFailure)
          log.error(s"Log at the moment of timeout: ${logger.result}")

        // Destroying nicely
        Try(p.destroy())

        // and scheduling forced destruction
        new java.util.Timer().schedule(new TimerTask {
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
