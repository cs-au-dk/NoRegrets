package backend

import java.nio.file._

import com.typesafe.config.{Config, ConfigFactory}
import backend.datastructures.PackageAtVersion
import backend.utils.WorkaroundTaskSupport.MyForkJoinTaskSupport

import scala.collection.JavaConverters._
import scala.collection.immutable.List
import scala.concurrent.forkjoin.ForkJoinPool

object Globals {

  case class OsPaths(node8: Option[String] = None)

  case class RegistryServer(host: String,
                            port: Int,
                            username: String,
                            password: String,
                            https: Boolean)

  val clusterName: String = "AkkaCluster"

  val parallelismInSwarm: Int = 50

  val installRetries: Int = 1

  val localParallelism: Int = Runtime.getRuntime.availableProcessors()

  val traceFolder: Path = Paths.get("api-inference").resolve("API-tracer")

  val cacheSizeLimit: Long = 1024 * 1024 * 1024 * 50 //50GB

  val tmpFolder: Path = Paths.get("/tmp")
  val yarnCachePrefix: String = "fresh-yarn-cache_"

  val tracingEntryPoint: Path =
    Globals.traceFolder
      .resolve("build")
      .resolve("API-tracer")
      .resolve("src")
      .resolve("index.js")

  val preambleFileName: String = "preamble_proxy.js"

  val swarmTaskSupport: MyForkJoinTaskSupport = new MyForkJoinTaskSupport(
    new ForkJoinPool(parallelismInSwarm))

  val regressionTypeLearnerlocalTaskSupport: MyForkJoinTaskSupport =
    new MyForkJoinTaskSupport(new ForkJoinPool(localParallelism))

  val successfulsLocalTaskSupport: MyForkJoinTaskSupport = new MyForkJoinTaskSupport(
    //new ForkJoinPool(Runtime.getRuntime.availableProcessors()))
    new ForkJoinPool(localParallelism))

  lazy val nodeProcessWrapper: Path = Paths.get("node-process-wrapper")

  lazy val tscPrebuilt: Boolean = System.getenv().containsKey("PREBUILT")

  private val conf: Config = ConfigFactory.load()

  lazy val githubToken: String = conf.getString("github_token")

  lazy val ciImage: String = conf.getString("ci_image")

  /**
    * Image used for running the benchmarks
    */
  lazy val benchmarksImage: String = conf.getString("benchmarks_image")

  lazy val useDockerExecutors: Boolean = conf.getBoolean("use_docker_executors")

  lazy val usePathExecutors: Boolean = conf.getBoolean("use_path_executors")

  lazy val optimisticEnvLocation: Path =
    Paths.get(conf.getString("optimistic-env-location"))

  lazy val testRunnerFolder: Path =
    Paths.get("api-inference").resolve("test-runner")

  lazy val testRunnerEntry: Path =
    Paths.get("build").resolve("test-runner").resolve("src").resolve("index.js")

  lazy val coverageAggregator: Path =
    Paths
      .get("build")
      .resolve("test-runner")
      .resolve("src")
      .resolve("coverage")
      .resolve("coverage-aggregator.js")

  lazy val genTestFolder: Path = Paths.get("generated-tests")

  lazy val testResultFileName = "test-result.json"

  lazy val testsAuxiliaryDependencies: List[PackageAtVersion] = List(
    PackageAtVersion("chai", "4.0.2"),
    PackageAtVersion("colors", "1.1.2"),
    PackageAtVersion("coffee-script", "1.12.6"))

  def goldenMochaVersion(relativePath: Path): PackageAtVersion =
    PackageAtVersion(
      "mocha",
      "file:" + relativePath.toAbsolutePath
        .relativize(Paths.get("ci").resolve("mocha").toAbsolutePath)
        .toString)

  lazy val registryServers: List[RegistryServer] = conf
    .getConfigList("registry-servers")
    .asScala
    .map { c =>
      RegistryServer(
        host = c.getString("host"),
        port = c.getInt("port"),
        username = c.getString("username"),
        password = c.getString("password"),
        https = c.getBoolean("https"))
    }
    .toList

  lazy val knownSystemPaths: Map[String, OsPaths] = {
    val objPaths = conf.getObject("known_system_paths")
    objPaths
      .keySet()
      .asScala
      .map { os =>
        val c = objPaths.toConfig
        os -> OsPaths(
          node8 =
            if (c.hasPath(s"$os.node8"))
              Some(c.getString(s"$os.node8"))
            else None)
      }
      .toMap
  }


}
