package backend.utils.executors

import java.nio.file.Path
import java.util.TimerTask

import backend.Globals
import backend.utils.ExecutionUtils.ProcExecutor
import backend.utils.{Log, Logger, ProcessExecutionResult, StrLogger}

import scala.concurrent._
import scala.concurrent.duration._
import scala.sys.process.Process
import scala.util.Try

case class StandardProcExecutor(optimisticEnv: Boolean = true) extends ProcExecutor {
  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)

  //private var curPath = System.getenv().get("PATH")
  //if (optimisticEnv) {
  //  if (!Globals.optimisticEnvLocation.toFile.exists())
  //    throw new RuntimeException(
  //      s"Unable to find the env directory: ${Globals.optimisticEnvLocation}")
  //  curPath =
  //    s"${Globals.optimisticEnvLocation.toAbsolutePath.toString}:${curPath}"
  //}

  def executeCommand(
    cmd: Seq[String],
    timeout: Duration = Duration.Inf,
    logStdout: Boolean = true,
    printFailure: Boolean = true,
    silent: Boolean = false,
    env: Map[String, String] = Map())(implicit cwd: Path): ProcessExecutionResult = {
    val logger = StrLogger(logStdout)
    val cmdMod =
      if (silent) (cmd.toList ::: List("&>", "/dev/null"))
      else {
        cmd
      }

    log.info(s"Executing: (cd ${cwd.toAbsolutePath}; ${cmdMod.mkString(" ")})")

    var curPath = if (env.contains("PATH")) env("PATH") else System.getenv().get("PATH")
    if (optimisticEnv) {
      if (!Globals.optimisticEnvLocation.toFile.exists())
        throw new RuntimeException(
          s"Unable to find the env directory: ${Globals.optimisticEnvLocation}")
      curPath = s"${Globals.optimisticEnvLocation.toAbsolutePath.toString}:${curPath}"
    }

    val newEnv = env + ("PATH" -> curPath)
    log.debug(s"Environment is: $newEnv")

    val cmdProcessWrapper = List(
      "node index.js",
      "--cmd",
      cmdMod.head,
      "--arguments",
      s"'[${cmdMod.tail.mkString(" ")}]'",
      "--nodeProcessWrapperTimeout",
      if (timeout == Duration.Inf) 10.hours.toMillis.toString
      else timeout.toMillis.toString,
      "--cwd",
      cwd.toAbsolutePath.toString)

    val p =
      Process(
        command = Seq("bash", "-c", cmdProcessWrapper.mkString(" ")), //FIXME: bash -c needed for PATH to be considered, how can we handle spaces ??
        cwd = Globals.nodeProcessWrapper.resolve("src").toFile,
        extraEnv = newEnv.toSeq: _*).run(logger)
    val res = Try {
      Await.result(
        Future {
          log.info(s"Waiting for ${cmdMod.mkString(" ")} to finish")
          log.debug(
            s"With node-process-wrapper (cd ${Globals.nodeProcessWrapper.resolve("src").toAbsolutePath} && ${cmdProcessWrapper
              .mkString(" ")})")
          p.exitValue()
        },
        timeout)
    }.recover {
      case e: TimeoutException =>
        log.error(s"Timeout $timeout for command  ${cmdMod.mkString(" ")}")
        if (printFailure)
          log.error(s"Log at the moment of timeout: ${logger.result}")

        // Destroying nicely
        Try(p.destroy())

        // and scheduling forced destruction
        new java.util.Timer()
          .schedule(new TimerTask {
            override def run(): Unit = Try {
              log.error(
                s"Forcing destruction of process ${cmdMod.mkString(" ")} in cwd ${cwd.toAbsolutePath.toString}")
              ForciblyDestroyProcessHelper.forciblyDestroy(p)
              log.error(
                s"process ${cmdMod.mkString(" ")} in cwd ${cwd.toAbsolutePath.toString} destroyed")
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
      log.info(s"Result:${cmdMod.mkString(" ")}: $res\n${logger.result}")
    ProcessExecutionResult(res, logger.result)
  }
}
