package distilling.server.utils.executors

import java.nio.file._

import com.google.common.collect.ImmutableList
import com.spotify.docker.client._
import com.spotify.docker.client.messages._
import distilling.server.utils.ExecutionUtils._
import distilling.server.utils._

import scala.collection.JavaConverters._
import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.util.Try

case class ContainerizedProcExecutor(imageName: String,
                                     additionalMounts: Seq[(Path, Path)] = Seq())
    extends ProcExecutor {

  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)

  // Create a client based on DOCKER_HOST and DOCKER_CERT_PATH env vars
  val docker = DefaultDockerClient.fromEnv.build

  // Pull the image
  Try {
    log.info(s"Pulling $imageName")
    docker.pull(imageName)
  }

  var mounts = List((Paths.get(""), Paths.get(""))) ++ additionalMounts

  val containerName =
    s"${imageName.replaceAll(":", "_").replaceAll("/", "_").replaceAll("\\.", "_")}_executor"

  /**
    */
  def executeCommand(
    cmd: Seq[String],
    timeout: Duration = Duration.Inf,
    logStdout: Boolean = true,
    printFailure: Boolean = true,
    env: Map[String, String] = Map())(implicit cwd: Path): ProcessExecutionResult = {

    val id = findOrCreateDocker()

    // Inspect container
    val info = docker.inspectContainer(id)

    // Exec command inside running container with attached STDOUT and STDERR
    log.info(s"Executing (DOCKER): (cd ${cwd.toAbsolutePath}; ${cmd.mkString(" ")})")
    val execCreation = docker.execCreate(
      id,
      Array("bash", "-c", s"cd ${cwd.toAbsolutePath} && ${cmd.mkString(" ")}"),
      DockerClient.ExecCreateParam.attachStdout,
      DockerClient.ExecCreateParam.attachStderr)
    val output = docker.execStart(execCreation.id)

    try {
      val (execOutput, exitCode) = Await.result(Future {
        log.info(s"Waiting for ${cmd.mkString(" ")} to finish")
        val out = output.readFully
        val code = docker.execInspect(execCreation.id()).exitCode()
        (out, code)
      }, timeout)

      ProcessExecutionResult(exitCode, execOutput)
    } catch {
      case e: InterruptedException =>
        log.info(s"Timeout for ${cmd.mkString(" ")}")
        throw e
      case e: Throwable => throw e
    }
  }

  override def finalize() = {
    // Close the docker client
    docker.close()
  }

  /**
    */
  def yarnCacheMounts(imageName: String): Seq[(Path, Path)] = {
    val hostYarnCacheOut = execute("yarn cache dir", logStdout = false)(Paths.get("/"))
    val containerYarnCacheOut =
      ContainerizedProcExecutor(imageName)
        .executeCommand(Seq("yarn", "cache", "dir"), logStdout = false)(Paths.get("/"))

    if (hostYarnCacheOut.code == 0 && containerYarnCacheOut.code == 0) {
      val bind =
        (Paths.get(hostYarnCacheOut.log.trim), Paths.get(containerYarnCacheOut.log.trim))
      log.info(s"Using yarn cache $bind")
      Seq(bind)
    } else {
      if (hostYarnCacheOut.code != 0)
        log.warn(s"Yarn cache path could not be found on the host")
      if (containerYarnCacheOut.code != 0)
        log.warn(s"Yarn cache path could not be found on the docker host")
      Seq()
    }
  }

  def findOrCreateDocker(): String = {
    val matching =
      docker.listContainers().asScala.filter(_.names().contains("/" + containerName))

    if (matching.isEmpty) {
      val volumes = ImmutableList.copyOf(
        mounts
          .map(pair =>
            s"${pair._1.toAbsolutePath.toString}:${pair._2.toAbsolutePath.toString}")
          .asJava)

      val hostConfig = HostConfig.builder.binds(volumes).build()

      // Create container with exposed ports
      val containerConfig = ContainerConfig.builder
        .hostConfig(hostConfig)
        .image(imageName)
        .cmd("sh", "-c", "while :; do sleep 1; done") // keep it alive
        .build

      val creation = docker.createContainer(containerConfig, containerName)
      log.info(s"Created new container for $imageName: ${creation.id()}")

      docker.startContainer(creation.id())

      creation.id
    } else {
      val id = matching.head.id()
      val info = docker.inspectContainer(id)
      if (!info.state().running()) {
        Try(docker.startContainer(id))
        val t = new Thread {
          override def run = {
            log.info(s"Stopping container $id")
            stopContainer(id)
          }
        }
        Runtime.getRuntime.addShutdownHook(t)
      }
      id
    }
  }

  def stopContainer(id: String)(): Unit = {
    // Stop container
    docker.stopContainer(id, 60)
  }

}
