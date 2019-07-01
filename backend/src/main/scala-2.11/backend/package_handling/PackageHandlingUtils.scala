package backend.package_handling

import java.io._
import java.nio.file._

import backend._
import backend.commands.CommandCachePolicy
import backend.datastructures.NpmRegistry.NpmRegistry
import backend.datastructures._
import backend.package_handling.Instrumentation.ProxyHelpersOptions
import backend.utils.ExecutionUtils.ProcExecutor
import backend.utils.Utils.{recursiveList, sshtohttps}
import backend.utils._
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.merge.MergeStrategy
import org.zeroturnaround.zip.ZipUtil

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.Try

object PackageHandlingUtils {

  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)

  val DISTILLED_TESTS_TIMEOUT: FiniteDuration = 25.second
  val YARN_TEST_TIMEOUT: FiniteDuration = 5.minutes
  val YARN_INSTALL_TIMEOUT: FiniteDuration = 5.minutes

  val yarnCaches: mutable.Map[Thread, Path] = mutable.Map()

  def clonePackage(parentFolder: Path,
                   registry: NpmRegistry,
                   pkgVersion: PackageAtVersion,
                   cache: Boolean = true,
                   ignoreTags: Boolean = false)(
      implicit executor: ProcExecutor): (Path, Boolean) = {
    Utils.retry(2, () => {
      clonePackageNoRetry(parentFolder, registry, pkgVersion, cache, ignoreTags)
    })
  }

  def runTestDistiller(modelFile: Path,
                       libraryToCheckName: String,
                       libraryFolder: Path,
                       libraryVersion: String,
                       timeoutSec: Option[Duration] = Some(
                         DISTILLED_TESTS_TIMEOUT),
                       enableValueChecking: Boolean = false,
                       withCoverage: Boolean = false,
                       silent: Boolean = false)(
      implicit executor: ExecutionUtils.ProcExecutor) = {
    val cmd = List(
      "node",
      s"./${Globals.testRunnerEntry}",
      "-m",
      modelFile.toAbsolutePath.toString,
      "-n",
      libraryToCheckName,
      "--libraryVersion",
      libraryVersion,
      "--coverage",
      if (withCoverage) "true" else "false",
      "-f",
      libraryFolder.toAbsolutePath.toString,
      "--exactValueChecking",
      if (enableValueChecking) "true" else "false"
    )

    executor.executeCommand(cmd,
                            timeout = timeoutSec.getOrElse(Duration.Inf),
                            silent = silent)(Globals.testRunnerFolder)
  }

  def runCoverageAggregator(coverageFilesFile: Path, aggCoverageFile: Path)(
      implicit executor: ExecutionUtils.ProcExecutor): Unit = {
    val cmd = List("node",
                   s"./${Globals.coverageAggregator}",
                   "-c",
                   coverageFilesFile.toAbsolutePath.toString,
                   "-o",
                   aggCoverageFile.toAbsolutePath.toString)
    executor.executeCommand(cmd, timeout = 30.seconds)(Globals.testRunnerFolder)
  }

  def retrievePackageUrl(pkg: PackageAtVersion)(
      implicit executor: ExecutionUtils.ProcExecutor): Option[String] = {

    implicit val pwd = Paths.get(new File(".").getCanonicalPath)
    val res = executor.executeCommand(
      Seq("npm",
          "info",
          s"${pkg.packageName}@${pkg.packageVersion}",
          "repository.url"))
    if (res.log.contains("git")) Some(res.log.trim) else None
  }

  /**
    * Clone the package at the specified version.
    * Returns the path where the package is cloned, and a boolean indicating whether the package was already present or not.
    */
  def clonePackageNoRetry(parentFolder: Path,
                          registry: NpmRegistry,
                          pkgVersion: PackageAtVersion,
                          cache: Boolean = true,
                          ignoreTags: Boolean = false)(
      implicit executor: ProcExecutor): (Path, Boolean) = {

    val packageOutDir =
      parentFolder.resolve(
        s"${pkgVersion.packageName}_${pkgVersion.packageVersion}${if (ignoreTags) "_ignoreTags"
        else ""}")

    log.info(
      s"Cloning ${pkgVersion.packageName}@${pkgVersion.packageVersion} to $parentFolder")
    var skipped = false

    if (!Files.exists(packageOutDir)) {

      val cloneBytes =
        DiskCaching.cache(
          {

            val url = try {
              registry(pkgVersion.packageName)
                .versions(pkgVersion.packageVersion)
                .repository
                .getOrElse(throw new Exception(
                  s"Clone of ${pkgVersion} failed. Registry entry does not have URL"))
            } catch {
              case e: Throwable =>
                //Fallback to npm info if the couchDB registry is down.
                PackageHandlingUtils
                  .retrievePackageUrl(pkgVersion)(
                    ExecutionUtils.defaultCustomPathsExecutor)
                  .getOrElse(throw new Error(
                    "Unable to retrieve package registry URL. npm info failed"));
            }
            val repoUrl = sshtohttps(url)

            val cloneCmd = Git
              .cloneRepository()
              .setURI(repoUrl)
              .setDirectory(packageOutDir.toFile)
              .setBranch("master")
            var git: Git = null
            try {
              git = cloneCmd.call()
            } catch {
              case e: Throwable ⇒
                FileUtils.deleteDirectory(packageOutDir.toFile)
                throw e
            } finally {
              if (git != null)
                git.close()
            }
            val baos = new ByteArrayOutputStream()
            ZipUtil.pack(packageOutDir.toFile, baos)
            baos.toByteArray
          },
          DiskCaching.CacheBehaviour.USE_IF_EXISTS_GENERATE_OTHERWISE,
          List("cloned-repo",
               pkgVersion.packageName,
               pkgVersion.packageVersion,
               if (ignoreTags) "ignoreTags" else ""),
          serializer = FastJavaSerializer
        )

      ZipUtil.unpack(new ByteArrayInputStream(cloneBytes), packageOutDir.toFile)
    } else {
      log.warn(
        s"Skipping clone of ${pkgVersion.packageName}@${pkgVersion.packageVersion}, directory $packageOutDir exists")
      skipped = true
    }
    // Checking out a revision matching the version
    log.debug("Getting taglist")
    var git: Git = null
    try {
      git = Git.open(packageOutDir.toFile)

      if (!ignoreTags) {
        val tagList = git.tagList().call()
        log.debug(
          s"Taglist: ${tagList.asScala.map(_.getName.toLowerCase()).mkString("\n")}")
        val ref = tagList.asScala
          .find(_.getName.toLowerCase().endsWith(pkgVersion.packageVersion))
          .getOrElse {
            throw new Exception(
              s"No tag containing ${pkgVersion.packageVersion} found")
          }

        log.debug(s"Found ref $ref")
        git.checkout().setName(ref.getName).setForce(true).call()
      }
      log.debug(s"Initializing submodules")
      git.submoduleInit().call()
      git.submoduleUpdate().setStrategy(MergeStrategy.RECURSIVE).call()
      log.info(s"Successfully cloned project $pkgVersion in $parentFolder")
    } finally {
      if (git != null)
        git.close()
    }
    (packageOutDir, skipped)
  }

  def yarnInstall(packageFolder: Path,
                  throwOnFailure: Boolean = true,
                  logStdout: Boolean = false)(
      implicit executor: ExecutionUtils.ProcExecutor)
    : ProcessExecutionResult = {
    executor.executeCommand(
      List("npm",
           "config",
           "set",
           "cache",
           Paths.get("npm-cache").toAbsolutePath.toString,
           "--global"))(packageFolder)
    var attempt = 0
    Utils.retry(
      Globals.installRetries,
      () => {
        attempt += 1
        val res =
          executor.executeCommand(
            {
              List("npm", "install") // --global-style") may create a node_modules structure that is more similar to yarns.
            },
            logStdout = logStdout,
            timeout = YARN_INSTALL_TIMEOUT
          )(packageFolder)
        if (throwOnFailure && res.code != 0)
          throw new RuntimeException(s"Failed yarn install: ${res.log}")
        res
      },
      wait = 2.seconds
    )
  }

  def runLTSTest(packageFolder: Path,
                 testPath: Path,
                 throwOnFailure: Boolean = false,
                 logStdout: Boolean = false)(
      implicit executor: ExecutionUtils.ProcExecutor)
    : ProcessExecutionResult = {
    val res = executor.executeCommand(Seq("node",
                                          "--stack-size=320000",
                                          "--max_old_space_size=16384",
                                          testPath.toAbsolutePath.toString),
                                      logStdout = logStdout,
                                      logFailure = false)(packageFolder)

    if (throwOnFailure && res.code != 0)
      throw new RuntimeException(s"Test failed:\n${res.log}")
    res
  }

  def runTestSuite(packageFolder: Path,
                   throwOnFailure: Boolean = false,
                   logStdout: Boolean = false,
                   logFailure: Boolean = true,
                   withInstrumentation: Boolean = false,
                   deleteClientOnReturn: Boolean = false)(
      implicit executor: ExecutionUtils.ProcExecutor)
    : ProcessExecutionResult = {

    val heapOpts = "--max_old_space_size=8192"
    val opts =
      if (withInstrumentation) {
        val tracerPath =
          if (Instrumentation.DEBUG_USE_RELATIVE_PATH_FOR_API_TRACER)
            packageFolder.toAbsolutePath.relativize(
              Globals.tracingEntryPoint.toAbsolutePath)
          else
            Globals.tracingEntryPoint.toAbsolutePath
        heapOpts + " --require " + tracerPath
      } else {
        heapOpts
      }

    if (withInstrumentation) {
      // i will very kindly produce an executable for the tests
      val fl = packageFolder.resolve("debug-run-test")
      Utils.writeToFile(fl,
                        s"""#!/bin/bash
        |export NODE_OPTIONS="$opts"
        |"$$@"
      """.stripMargin)
      fl.toFile.setExecutable(true)
    }

    val res = executor.executeCommand(Seq("yarn", "test"),
                                      timeout = YARN_TEST_TIMEOUT,
                                      logStdout = logStdout,
                                      env = Map("NODE_OPTIONS" -> opts),
                                      logFailure = logFailure)(packageFolder)

    if (throwOnFailure && res.code != 0)
      throw new RuntimeException(s"Yarn test failed:\n${res.log}")
    res
  }

  def findTests(packageFolder: Path): List[Path] = {
    //Find root test file if exists
    val rootTest = packageFolder.resolve("test.js")
    val rootTestList =
      if (rootTest.toFile.exists())
        List(rootTest)
      else
        List()

    //Find test folders
    def testFolderFinder(begin: File) = {
      val potentialTestFolderNames = Set("test", "spec")
      begin.listFiles(new FileFilter {
        override def accept(file: File): Boolean =
          file.isDirectory && potentialTestFolderNames.exists(p =>
            file.getName.contains(p))
      })
    }
    val testSearchBeginFolders =
      Set(packageFolder, packageFolder.resolve("src"))
    val testFolders = testSearchBeginFolders
      .filter(Files.exists(_))
      .map(_.toFile)
      .flatMap(testFolderFinder)

    //Find test files in test folders
    testFolders
      .flatMap(
        testFolder =>
          recursiveList(testFolder)
            .filter(_.getName.endsWith(".js"))
            .map(_.toPath)
            .toList)
      .toList ::: rootTestList
  }

  lazy val tscAll: Unit = {
    val prebuilt = Globals.tscPrebuilt
    val tscer = (folder: Path) =>
      if (!prebuilt) {
        val result = try {
          ExecutionUtils.execute("tsc",
                                 logStdout = false,
                                 printFailure = true,
                                 timeout = 1.minute)(folder)
        } catch {
          case _: InterruptedException =>
            throw new RuntimeException(s"tsc on ${folder} timeout")
        }
        if (result.code != 0) {
          throw new RuntimeException(
            s"tsc on ${folder} failed, check your typescript types")
        }
        log.info("tsc done")
      } else if (prebuilt) {
        log.info("Skipping tsc, it is prebuilt by the enviroment")
    }
    tscer(Globals.traceFolder)
    tscer(Globals.testRunnerFolder)
  }

  def cloneAndRunTestsWithLibrary(client: PackageAtVersion,
                                  library: PackageAtVersion,
                                  clientParentFolder: Path,
                                  deleteDirOnExit: Boolean,
                                  //See RunTestsOptions for an explanation of the NoRegretsPlusMode parameter
                                  ignoreTags: Boolean = false)(
      implicit executor: ProcExecutor): ProcessExecutionResult = {
    val registry = new RegistryReader().loadLazyNpmRegistry()

    val (clientDir, _) =
      PackageHandlingUtils.clonePackage(clientParentFolder,
                                        registry,
                                        client,
                                        ignoreTags = ignoreTags)

    // Patch package.json to use the right version of libraryToTrace
    val newJson =
      Instrumentation.patchPackageJson(
        clientDir.resolve("package.json"),
        List(library, Globals.goldenMochaVersion(clientDir)),
        List(Globals.goldenMochaVersion(clientDir)),
        if (ignoreTags) Some(library) else None
      )

    val deleter = () ⇒ {
      log.info(s"Deleting ${clientDir}")
      FileUtils.deleteDirectory(clientDir.toFile)
    }

    try {
      PackageHandlingUtils.yarnInstall(clientDir)
      PackageHandlingUtils.runTestSuite(clientDir,
                                        throwOnFailure = true,
                                        deleteClientOnReturn = true)
    } catch {
      case e: OutOfMemoryError ⇒
        log.info(
          s"Failed with out of memory error when running successfuls on ${client.toString}")
        throw e
      case e: Throwable ⇒
        deleter()
        throw e
    } finally {
      if (deleteDirOnExit) {
        deleter()
      }
    }
  }

  def getLibraryVersions(libraryName: String,
                         cacheStrategy: CommandCachePolicy.Value,
                         cacheKey: List[String]): List[String] = {
    DiskCaching.cache({
      new RegistryReader()
        .loadLazyNpmRegistry()(libraryName)
        .versions
        .keys
        .toList
    }, CommandCachePolicy.getMatchingCachePolicy(cacheStrategy), cacheKey)
  }

  def filterRangeAndSortPackages(
      init: SemverWithUnnormalized,
      end: SemverWithUnnormalized,
      packages: List[SemverWithUnnormalized],
      withAlphas: Boolean = false): List[SemverWithUnnormalized] = {
    packages
      .filter { version =>
        (version.ver.isGreaterThan(init.ver) || version.ver.isEqualTo(init.ver)) &&
        (version.ver.isLowerThan(end.ver) || version.ver.isEqualTo(end.ver)) &&
        (withAlphas || version.ver.withClearedSuffixAndBuild() == version.ver)
      }
      .sorted(SemverWithUnnormalized.SemverOrdering)
  }

  def runTracing(
      client: PackageAtVersion,
      library: PackageAtVersion,
      clientOut: Path,
      proxyHelpersOptions: ProxyHelpersOptions,
      ignoreTagsMode: Boolean,
      skipInstrumentation: Boolean = false,
      ignoreFailingInstallations: Boolean = false,
      silent: Boolean = true)(implicit executor: ProcExecutor): Boolean = {
    try {
      //Clone package
      val registry = new RegistryReader().loadLazyNpmRegistry()
      val (clientDir, _) =
        PackageHandlingUtils.clonePackage(clientOut,
                                          registry,
                                          client,
                                          ignoreTags = ignoreTagsMode)

      // Patch package.json to use the right version of libraryToTrace and a recent mocha
      Instrumentation.patchPackageJson(
        clientDir.resolve("package.json"),
        List(library, Globals.goldenMochaVersion(clientDir)),
        List(Globals.goldenMochaVersion(clientDir)))

      yarnInstall(clientDir, throwOnFailure = !ignoreFailingInstallations)

      if (!skipInstrumentation) {
        Try(proxyHelpersOptions.output.toFile.delete())
        Utils.writeToFile(clientDir.resolve("__options.json"),
                          proxyHelpersOptions.toJson(clientDir))
      }

      //Execute the API tracer
      log.info(s"Running tests of $client")
      val res = runTestSuite(clientDir,
                             logStdout = !silent,
                             logFailure = !silent,
                             withInstrumentation = !skipInstrumentation)
      val failed = res.code != 0
      if (failed && !silent) {
        log.warn(s"Tests of $clientDir failed:\n ${res.log}")
      } else if (!silent) {
        log.info(s"Test output of $clientDir:\n ${res.log}")
      }
      !failed
    } catch {
      case e: Throwable =>
        log.error(
          s"Failed at Api-trace of $client: $e:\n${e.getStackTrace.mkString("\n")}")
        false
    }
  }

  /**
    * Delete all the cache directories if their size exceeds limit
    * @param limit in bytes
    */
  def deleteCacheFolderIfExceedingSizeLimit(
      limit: Long = Globals.cacheSizeLimit): Unit = {
    val tmpFiles = Files.list(Globals.tmpFolder).iterator().asScala.toList

    val names = tmpFiles.map(_.getFileName.toString)
    val caches =
      tmpFiles.filter(
        _.getFileName.toString.startsWith(Globals.yarnCachePrefix))

    log.debug(s"cacheFiles: ${caches.mkString(", ")}")

    if (caches.nonEmpty) {
      val size =
        caches
          .map(cache ⇒ FileUtils.sizeOfDirectory(cache.toFile))
          .reduce[Long] {
            case (s1, s2) ⇒ s1 + s2
          }

      val asGb = (x: Long) ⇒ x / (Math.pow(x.toDouble, 3))

      if (size > limit) {
        PackageHandlingUtils.synchronized {
          log.info(s"Deleting yarn cache folders: size of cache folders (${asGb(
            size)}GB) exceeds limit of ${asGb(limit)}GB")
          caches.foreach(cache ⇒ FileUtils.deleteDirectory(cache.toFile))
          yarnCaches.clear()
        }
      }
    }
  }

  /**
    * Some test-suite leave child processes that are not terminated when a test-suite itself is terminated.
    * The node-process-wrapper will try to terminate as many of the processes as possible,
    * but sometimes detached processes cannot easily be terminated.
    *
    * This method uses killall to kill some of these processes
    * Notice, will kill all node instances on the system.
    * @param executor
    * @return
    */
  def killPotentiallySpuriousProcesses(
      implicit executor: ExecutionUtils.ProcExecutor) = {
    implicit val cwd = Paths.get("/tmp")
    executor.executeCommand(Seq("killall", "node"))
    executor.executeCommand(Seq("killall", "mongod"))
  }

}
