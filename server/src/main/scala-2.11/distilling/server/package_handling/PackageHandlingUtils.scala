package distilling.server.package_handling

import java.io._
import java.nio.file._

import distilling.server._
import distilling.server.commands.CommandCachePolicy
import distilling.server.datastructures.NpmRegistry.NpmRegistry
import distilling.server.datastructures._
import distilling.server.package_handling.Instrumentation.ProxyHelpersOptions
import distilling.server.utils.ExecutionUtils.ProcExecutor
import distilling.server.utils.Utils.{recursiveList, sshtohttps}
import distilling.server.utils._
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.merge.MergeStrategy
import org.zeroturnaround.zip.ZipUtil

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.Try

object PackageHandlingUtils {

  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)

  val YARN_TEST_TIMEOUT: FiniteDuration = 10.minutes
  val YARN_INSTALL_TIMEOUT: FiniteDuration = 6.minutes

  val yarnCaches: mutable.Map[Thread, Path] = mutable.Map()

  def clonePackage(parentFolder: Path,
                   registry: NpmRegistry,
                   pkgVersion: PackageAtVersion,
                   cache: Boolean = true): (Path, Boolean) = {
    Utils.retry(2, () => {
      clonePackageNoRetry(parentFolder, registry, pkgVersion, cache)
    })
  }

  /**
    * Clone the package at the specified version.
    * Returns the path where the package is cloned, and a boolean indicating whether the package was already present or not.
    */
  def clonePackageNoRetry(parentFolder: Path,
                          registry: NpmRegistry,
                          pkgVersion: PackageAtVersion,
                          cache: Boolean = true): (Path, Boolean) = {

    val packageOutDir =
      parentFolder.resolve(s"${pkgVersion.packageName}_${pkgVersion.packageVersion}")

    log.info(
      s"Cloning ${pkgVersion.packageName}@${pkgVersion.packageVersion} to $parentFolder")
    var skipped = false

    if (!Files.exists(packageOutDir)) {

      val cloneBytes =
        DiskCaching.cache(
          {
            val repoUrl = sshtohttps(
              registry(pkgVersion.packageName)
                .versions(pkgVersion.packageVersion)
                .repository
                .get)

            val cloneCmd = Git
              .cloneRepository()
              .setURI(sshtohttps(repoUrl))
              .setDirectory(packageOutDir.toFile)
              .setBranch("master")
            var git: Git = null
            try {
              git = cloneCmd.call()
            } finally {
              if (git != null)
                git.close()
            }
            val baos = new ByteArrayOutputStream()
            ZipUtil.pack(packageOutDir.toFile, baos)
            baos.toByteArray
          },
          DiskCaching.CacheBehaviour.USE_IF_EXISTS_GENERATE_OTHERWISE,
          List("cloned-repo", pkgVersion.packageName, pkgVersion.packageVersion),
          serializer = FastJavaSerializer)

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
      val tagList = git.tagList().call()
      log.debug(
        s"Taglist: ${tagList.asScala.map(_.getName.toLowerCase()).mkString("\n")}")
      val ref = tagList.asScala
        .find(_.getName.toLowerCase().endsWith(pkgVersion.packageVersion))
        .getOrElse {
          throw new Exception(s"No tag containing ${pkgVersion.packageVersion} found")
        }

      log.debug(s"Found ref $ref")
      git.checkout().setName(ref.getName).setForce(true).call()
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
    implicit executor: ExecutionUtils.ProcExecutor): ProcessExecutionResult = {
    val currentThreadCache = PackageHandlingUtils.synchronized {
      val res = yarnCaches.getOrElse(
        Thread.currentThread(),
        Files.createDirectories(
          Paths.get("/tmp").resolve(s"fresh-yarn-cache_${yarnCaches.size}")))
      yarnCaches.update(Thread.currentThread(), res)
      res
    }
    var attempt = 0
    Utils.retry(
      3,
      () => {
        attempt += 1
        val res =
          executor.executeCommand({
            val baseCmd = List("yarn", "install", "--ignore-engines", "--non-interactive")
            val cmd = if (attempt <= 2) {
              // first attempt with no additional options
              baseCmd ::: List(
                "--cache-folder",
                currentThreadCache.toAbsolutePath.toString)
            } else {
              // last attempt using a custom cache folder
              val p = Files.createTempDirectory("fresh-yarn-cache")
              baseCmd ::: List("--cache-folder", p.toAbsolutePath.toString)
            }
            cmd
          }, logStdout = logStdout, timeout = YARN_INSTALL_TIMEOUT)(packageFolder)
        if (throwOnFailure && res.code != 0)
          throw new RuntimeException(s"Failed yarn install: ${res.log}")
        res
      },
      wait = 2.seconds)
  }

  def runLTSTest(packageFolder: Path,
                 testPath: Path,
                 throwOnFailure: Boolean = false,
                 logStdout: Boolean = false)(
    implicit executor: ExecutionUtils.ProcExecutor): ProcessExecutionResult = {
    val res = executor.executeCommand(
      Seq(
        "node",
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
                   withInstrumentation: Boolean = false)(
    implicit executor: ExecutionUtils.ProcExecutor): ProcessExecutionResult = {

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
      Utils.writeToFile(
        fl,
        s"""#!/bin/bash
        |export NODE_OPTIONS="$opts"
        |"$$@"
      """.stripMargin)
      fl.toFile.setExecutable(true)
    }

    val res = executor.executeCommand(
      Seq("yarn", "test"),
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
    val testSearchBeginFolders = Set(packageFolder, packageFolder.resolve("src"))
    val testFolders = testSearchBeginFolders
      .filter(Files.exists(_))
      .map(_.toFile)
      .flatMap(testFolderFinder)

    //Find test files in test folders
    testFolders
      .flatMap(testFolder =>
        recursiveList(testFolder).filter(_.getName.endsWith(".js")).map(_.toPath).toList)
      .toList ::: rootTestList
  }

  lazy val tscAll: Unit = {
    val prebuilt = Globals.tscPrebuilt
    if (!prebuilt) {
      val result = try {
        ExecutionUtils.execute(
          "tsc",
          logStdout = false,
          printFailure = true,
          timeout = 1.minute)(Globals.tracerFolder)
      } catch {
        case _: InterruptedException =>
          throw new RuntimeException(s"tsc on ${Globals.tracerFolder} timeout")
      }
      if (result.code != 0) {
        throw new RuntimeException(
          s"tsc on ${Globals.tracerFolder} failed, check your typescript types")
      }
      log.info("tsc done")
    } else if (prebuilt) {
      log.info("Skipping tsc, it is prebuilt by the enviroment")
    }
  }

  def cloneAndRunTestsWithLibrary(
    client: PackageAtVersion,
    library: PackageAtVersion,
    clientParentFolder: Path)(implicit executor: ProcExecutor): ProcessExecutionResult = {
    val registry = new RegistryReader().loadLazyNpmRegistry()
    val (clientDir, _) =
      PackageHandlingUtils.clonePackage(clientParentFolder, registry, client)

    // Patch package.json to use the right version of libraryToTrace
    val newJson =
      Instrumentation.patchPackageJson(
        clientDir.resolve("package.json"),
        List(library, Globals.goldenMochaVersion(clientDir)),
        List(Globals.goldenMochaVersion(clientDir)))

    PackageHandlingUtils.yarnInstall(clientDir)
    PackageHandlingUtils.runTestSuite(clientDir, throwOnFailure = true)
  }

  def getLibraryVersions(libraryName: String,
                         cacheStrategy: CommandCachePolicy.Value,
                         cacheKey: List[String]): List[String] = {
    DiskCaching.cache({
      new RegistryReader().loadLazyNpmRegistry()(libraryName).versions.keys.toList
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

  def runTracing(client: PackageAtVersion,
                 library: PackageAtVersion,
                 clientOut: Path,
                 proxyHelpersOptions: ProxyHelpersOptions,
                 skipInstrumentation: Boolean = false,
                 ignoreFailingInstallations: Boolean = false,
                 verbose: Boolean = true)(implicit executor: ProcExecutor): Boolean = {
    try {
      //Clone package
      val registry = new RegistryReader().loadLazyNpmRegistry()
      val (clientDir, _) =
        PackageHandlingUtils.clonePackage(clientOut, registry, client)

      // Patch package.json to use the right version of libraryToTrace and a recent mocha
      Instrumentation.patchPackageJson(
        clientDir.resolve("package.json"),
        List(library, Globals.goldenMochaVersion(clientDir)),
        List(Globals.goldenMochaVersion(clientDir)))

      yarnInstall(clientDir, throwOnFailure = !ignoreFailingInstallations)

      if (!skipInstrumentation) {
        Try(proxyHelpersOptions.output.toFile.delete())
        Utils.writeToFile(
          clientDir.resolve("__options.json"),
          proxyHelpersOptions.toJson(clientDir))
      }

      //Execute the API tracer
      log.info(s"Running tests of $client")
      val res = runTestSuite(
        clientDir,
        logFailure = true,
        withInstrumentation = !skipInstrumentation)
      val failed = res.code != 0
      if (failed) {
        log.warn(s"Tests of $clientDir failed:\n ${res.log}")
      } else if (verbose) {
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
}
