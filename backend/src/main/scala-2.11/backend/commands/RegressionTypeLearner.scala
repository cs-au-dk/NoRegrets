package backend.commands

import java.nio.file._

import backend._
import backend.commands.ApiTrace.ApiTraceOptions
import backend.commands.Common._
import backend.commands.benchmarking.BlacklistedClients
import backend.datastructures._
import backend.package_handling._
import backend.regression_typechecking._
import backend.utils.DiskCaching.CacheBehaviour
import backend.utils.NotationalUtils.SI
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import backend.utils._
import scopt.OptionDef

import scala.collection.mutable
import scala.language.implicitConversions
import scala.util.{Either, Left, Right, Try}
import Utils._
import backend.commands.ClientPriority.ClientPriority
import backend.commands.RegressionResult.ClientResults
import backend.datastructures.SemverWithUnnormalized.SemverOrdering

import scala.collection.parallel.ParSeq
import scala.concurrent.TimeoutException

case class RegressionTypeLearner(options: RegressionTypeLearnerOptions) {

  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)
  private implicit val executor: ExecutionUtils.ProcExecutor =
    ExecutionUtils.rightExecutor(Globals.benchmarksImage)
  private implicit val formats = SerializerFormats.commonSerializationFormats

  private val MAX_REGRESSION_FILE_SIZE: Double = 50.mega
  private val outDir: Path = options.outDir.toAbsolutePath
  private val libraryToTraceOutdir: Path =
    outDir.resolve(
      s"${options.libraryToCheckName}-${options.libraryVersions.head}-to-${options.libraryVersions.last}")
  private val clientsWithTestsOutdir: Path = outDir.resolve("_clients")
  private val libraryVersions =
    options.libraryVersions.map(PackageAtVersion(options.libraryToCheckName, _))

  private val diffK = "diff" :: List(
    if (options.NoRegretsPlus_Mode) "NoRegretsPlus" else "NoRegrets",
    if (options.ignoreTagsMode) "IgnoreTags" else "",
    if (options.enableValueChecking) "ValueChecking" else "",
    if (options.withCoverage) "WithCoverage" else "",
    options.clientPriority match {
      case ClientPriority.OnlyOldest ⇒ "only-oldest"
      case ClientPriority.OnlyNewest ⇒ "only-newest"
      case ClientPriority.All ⇒ "all-versions"
    },
    options.libraryToCheckName.toString,
    options.libraryVersions.head.toString,
    options.libraryVersions.last.toString)

  //Maybe create an option field for this?
  private val FilterRegressionsAppearingInInitLibaryVersion: Boolean = true

  /**
    * Path to the folder where the library used in the distilled tests are stored
    */
  private val status = mutable.Map[PackageAtVersion, (PackageAtVersion, String)]()
  val registry = new RegistryReader().loadLazyNpmRegistry()

  libraryToTraceOutdir.toFile.mkdirs()
  clientsWithTestsOutdir.toFile.mkdirs()

  trait RegressionCheckingStrategy {
    def run(): RegressionResult
  }

  def getClients(): ParSeq[PackageAtVersion] = {
    val first = options.libraryVersions.head

    PackageHandlingUtils.tscAll

    val clients = options.clients match {
      case Left(list) => list
      case Right(path) =>
        JsonSerializer().deserialize[List[PackageAtVersion]](path)
    }

    if (clients.isEmpty) {
      log.info(s"Skipping library ${options.libraryToCheckName} due to missing clients")
      throw NoClients()
    }

    val allowedToRunClients =
      clients.filter(pkg => !options.doNotRun.contains(pkg))

    if (allowedToRunClients.isEmpty)
      throw new RuntimeException("No clients to perform regression type checking")

    log.info(
      s"Beginning regression type checking, clients: ${allowedToRunClients.size}, library versions: ${options.libraryVersions.size} with options:\n$options")

    val clientsToLearnFrom =
      allowedToRunClients.filter(!options.doNotRun.contains(_)).par

    // If we are swarming, then increse the parallelism level
    if (options.swarm) {
      clientsToLearnFrom.tasksupport = Globals.swarmTaskSupport
    } else {
      clientsToLearnFrom.tasksupport = Globals.regressionTypeLearnerlocalTaskSupport
    }
    clientsToLearnFrom
  }

  /**
    * Add the clients for which there are no observations to the blacklist.
    * @param learned
    */
  def addToBlacklist(
    learned: Map[PackageAtVersion, Map[PackageAtVersion, TracingResult]]) = {
    // Blacklist library clients that give us no observations
    val blacklistedClients = learned.filter {
      case (client, libResult) =>
        libResult.values.forall {
          case l: APIModel => l.observations.isEmpty
          case _           => false // if it fails we have to look at it
        }
    }.keys
    BlacklistedClients.add(options.libraryToCheckName, blacklistedClients.toSet)
  }

  def saveAggregatedRegressions(results: Map[PackageAtVersion, AnalysisResults]) = {
    DiskCaching.cache(
      results.map(res ⇒ (res._1.toString, res._2)),
      CacheBehaviour.REGENERATE,
      diffK,
      serializer = SerializerFormats.defaultSerializer)
  }

  def updateBenchmarkStatus(diff: Map[PackageAtVersion, RegressionInfo],
                            totalClientCount: Int,
                            totalObservationCount: Int,
                            totalPathsCovered: Int,
                            minorPatchUpdates: Int,
                            majorUpdates: Int) = {
    val diffInfo = BenchmarkStatus(
      diffK.mkString("-"),
      s"${options.libraryToCheckName}@${options.libraryVersions.head}",
      diff.values.foldLeft[Int](0) { case (res, kD) => res + kD.diffObservationCount },
      diff.values.flatMap(_.diffClientCount).toSet.size,
      totalClientCount,
      totalObservationCount,
      totalPathsCovered,
      minorPatchUpdates,
      majorUpdates,
      if (options.NoRegretsPlus_Mode) "NoRegretsPlus" else "NoRegrets",
      options.withCoverage,
      options.ignoreTagsMode)

    BenchmarksStatus.default.addDiff(
      s"${options.libraryToCheckName}@${options.libraryVersions.head} - ${if (options.NoRegretsPlus_Mode) "NoRegretsPlus"
      else "NoRegrets"}" +
        s"${if (options.ignoreTagsMode) " - IgnoreTags" else ""}" +
        s"${if (options.enableValueChecking) " - ValueChecking" else ""}" +
        s"${if (options.withCoverage) " - WithCoverage" else ""}" +
        " - " +
        s"${options.clientPriority match {
          case ClientPriority.OnlyOldest ⇒ "only-oldest"
          case ClientPriority.OnlyNewest ⇒ "only-newest"
          case ClientPriority.All ⇒ "all-versions"
        }}",
      diffInfo)
  }

  object ClientTestBasedStrategy extends RegressionCheckingStrategy {
    override def run(): RegressionResult = {
      val clients = getClients()

      val libraryVersions =
        options.libraryVersions.map(PackageAtVersion(options.libraryToCheckName, _))

      log.info(
        s"Generating models with max threading set to ${clients.tasksupport.parallelismLevel}")
      val learned = clients
        .mapBy { c =>
          learnAndCheckWithClient(c, libraryVersions, options.withCoverage)
        }
        .seq
        .toMap
      addToBlacklist(learned)

      log.info(s"Now diffing")

      val testResultsForEachClient = if (options.useSmartDiff) {
        Aggregator.augmentObservationsSmartly(learned)
      } else {
        learned
      }

      // Save the diff for the entire library
      val regInfo =
        Aggregator.aggregate(testResultsForEachClient).diffs(options.typingRelation)
      val analysisResults = regInfo.map {
        case (pv, regInfo) ⇒
          pv → AnalysisResults(
            regInfo,
            testResultsForEachClient.map {
              case (clientVersion, clientResultMap) ⇒ {
                clientResultMap(pv) match {
                  case a @ APIModel(obs, _, tt, co, _) ⇒
                    ClientDetail(
                      clientVersion,
                      succeeded = true,
                      "",
                      0,
                      tt,
                      obs.length,
                      obs.length,
                      0,
                      0,
                      co,
                      List(),
                      a.clientSize)
                  case l @ LearningFailure(msg, tt, obs, _) ⇒
                    ClientDetail(
                      clientVersion,
                      succeeded = false,
                      msg,
                      0,
                      tt,
                      obs.length,
                      obs.length,
                      0,
                      0,
                      CoverageObject(),
                      List(),
                      l.clientSize)
                }
              }
            }.toList,
            aggregatedCoverage(
              pv,
              Right(testResultsForEachClient.values.map(resMap ⇒ resMap(pv)).toList)))
      }

      saveAggregatedRegressions(analysisResults)

      val totalClientCount = clients.seq.length //testResultsForEachClient.keys.toSet.size
      val totalPathCount = testResultsForEachClient.values
        .foldLeft(List[Observation]()) {
          case (aggClient, mapTRes) => {
            aggClient ++ mapTRes.values.foldLeft(List[Observation]()) {
              case (aggLib, tRes) => {
                aggLib ++ (tRes match {
                  case l: APIModel        => l.observations
                  case l: LearningFailure => l.observations
                })
              }
            }
          }
        }
        .toSet
        .size

      val (minors, majors) = getMinorMajors()

      updateBenchmarkStatus(
        regInfo,
        totalClientCount,
        totalPathCount,
        totalPathCount,
        minors,
        majors)

      // Save the diff for each client
      if (options.saveDiffForEachClient) {
        learned.map {
          case (client, libObs) =>
            val clientDiffs =
              Aggregator.aggregate(Map(client -> libObs)).diffs(options.typingRelation)
            val diffK = "diffClient" :: List(
              client.packageName,
              client.packageVersion,
              options.libraryToCheckName)

            DiskCaching.cache(
              clientDiffs.map(p =>
                AnalysisResults(p._2, List(), CoverageObject(Map(), Map(), 0))),
              CacheBehaviour.REGENERATE,
              diffK,
              serializer = SerializerFormats.defaultSerializer)
        }
      }
      RegressionResult(regInfo, Left(testResultsForEachClient))
    }
  }

  object TestDistillationBasedStrategy extends RegressionCheckingStrategy {
    override def run(): RegressionResult = {
      val clients = getClients()

      val testResultsForEachClient =
        clients
          .mapBy(learnAndCheckWithDistilledTests)
          .seq
          .flatMap { //Remove clients for which the model generation failed
            case (c, res) ⇒
              res match {
                case Some(s) ⇒ Some(c → s)
                case None ⇒ None
              }
          }
          .toMap

      val initLibraryVersionTestResults = testResultsForEachClient.mapValues {
        case (m, res) ⇒ res(libraryVersions.head)
      }

      // testResultsForEachClient - initLibraryVersionTestResults
      val nonInitLibraryVersionTestResults = testResultsForEachClient.mapValues {
        case (apiModel, testResults) ⇒ (apiModel, testResults - libraryVersions.head)
      }

      //Print the test results
      nonInitLibraryVersionTestResults.foreach {
        case (client, (_, results)) ⇒ {
          log.info(
            s"$client results for library ${options.libraryToCheckName} for versions\n\t${libraryVersions.tail //Tail since the head is for the init version
              .map(libVer ⇒ {
                s"${libVer}\t-\t${results(libVer) match {
                  case TestReport(_, pathsTotal, pathsCovered, _, _, _, _) ⇒
                    if (pathsTotal.intValue() > 0) {
                      s"Success with ${pathsCovered} covered / ${pathsTotal} total = ${BigDecimal(pathsCovered.doubleValue() / pathsTotal.doubleValue() * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble}% path coverage"
                    } else {
                      s"Unexpected ${pathsCovered} covered / ${pathsTotal} total path coverage data."
                    }
                  case TestReportError(_, _, _, _) ⇒ "Failed"
                }}"
              })
              .mkString("\n\t")}")
        }
      }

      //Filter regressions appearing in the version of the library used to generate the model
      val filteredTestResults = (if (FilterRegressionsAppearingInInitLibaryVersion) {
                                   nonInitLibraryVersionTestResults.mapValuesWithKey {
                                     case (client, (model, testResults)) ⇒ {
                                       (model, testResults.map {
                                         case (libVersion, testResult) ⇒ {
                                           val regressionsInInitLibVersion =
                                             initLibraryVersionTestResults(client) match {
                                               case TestReport(
                                                   regressions,
                                                   _,
                                                   _,
                                                   _,
                                                   _,
                                                   _,
                                                   _) ⇒
                                                 regressions
                                               case _ ⇒ List()
                                             }
                                           libVersion → (testResult match {
                                             case t @ TestReport(
                                                   regressions,
                                                   pt,
                                                   pc,
                                                   tt,
                                                   et,
                                                   sc,
                                                   cf) ⇒
                                               val newReport = TestReport(
                                                 regressions.filterNot(reg ⇒
                                                   regressionsInInitLibVersion.exists(
                                                     _.equalsModuloPathId(reg))),
                                                 pt,
                                                 pc,
                                                 tt,
                                                 et,
                                                 sc,
                                                 cf)
                                               newReport.modelSizeBytes = t.modelSizeBytes
                                               newReport
                                             case t: TestReportError ⇒ t
                                           })
                                         }
                                       })
                                     }
                                   }
                                 } else {
                                   nonInitLibraryVersionTestResults
                                 })
        .mapValuesWithKey { //Re-add the results from the initial run back to the set of results
          //since we still want the initials results displayed in the web interface
          case (client, (model, results)) ⇒
            (
              model,
              results + (libraryVersions.head → initLibraryVersionTestResults(client)))
        }

      //Transfer TestResults to RegressionInfo
      val diffs: Map[PackageAtVersion, Map[PackageAtVersion, RegressionInfo]] =
        filteredTestResults.map {
          case (client, (_, results)) ⇒ {
            client → results.map {
              case (libVer, result) ⇒ {
                libVer → (result match {
                  case t @ TestReport(typeRegressions, _, _, _, _, _, _) =>
                    RegressionInfo(
                      typeRegressions
                        .map(reg => {
                          val relationFailure =
                            if (reg.covariant) s"${reg.observedType} <: ${reg.modelType}"
                            else s"${reg.modelType} <: ${reg.observedType}"
                          reg.path →
                            RelationFailure(
                              Map(reg.observedType -> List(Observer(client, None))),
                              relationFailure)
                        })
                        .toMap,
                      minimize = true)
                  case t @ TestReportError(msg, _, _, _) =>
                    RegressionInfo(Map())
                })
              }
            }
          }
        }

      // Merge the regressions from different clients
      // such that we have (libVer -> regressions) instead of
      // (client -> (libVer -> regressions))
      val mergedRegressions: Map[PackageAtVersion, RegressionInfo] =
        diffs.foldLeft(Map(): Map[PackageAtVersion, RegressionInfo]) {
          case (agg, (client, regressionInfo)) => {
            regressionInfo.mapValuesWithKey {
              case (libVer, regInfo) =>
                if (agg.contains(libVer))
                  agg(libVer).merge(regInfo)
                else
                  regInfo
            }
          }
        }

      //Filter regressions if they appear at multiple successive library versions
      val filteredRegressions: Map[PackageAtVersion, RegressionInfo] = {
        val libVers = mergedRegressions.keys.toList.sortBy {
          case PackageAtVersion(_, ver) ⇒ Versions.toVersion(ver).get
        }(SemverOrdering)
        libVers.tail
          .foldLeft((libVers.head, Map(libVers.head → mergedRegressions(libVers.head)))) {
            case ((lastVer, aggDiff), curVer) ⇒ {
              val lstDiff = mergedRegressions(lastVer)
              val curDiff = mergedRegressions(curVer)
              val sharedPaths =
                lstDiff.regressions.keys.toSet.intersect(curDiff.regressions.keys.toSet)
              val sameRegsPaths = sharedPaths.filter(p ⇒
                curDiff.regressions(p).equalFailure(lstDiff.regressions(p)))
              (
                curVer,
                aggDiff + (curVer → RegressionInfo(
                  curDiff.regressions.filterKeys(!sameRegsPaths.contains(_)))))
            }
          }
          ._2
      }

      val analysisResults = filteredRegressions.map {
        case (pv, regInfo) ⇒ {
          pv → AnalysisResults(
            regInfo,
            filteredTestResults.map {
              case (client, (mod, resMap)) ⇒
                val (modelSize, compModelSize) =
                  (mod.preCompressSize.getOrElse[Number](0), mod.size)

                resMap(pv) match {
                  case t @ TestReport(_, pathsTotal, pathsCovered, tt, et, sc, cf) ⇒
                    ClientDetail(
                      client,
                      succeeded = true,
                      "",
                      tt,
                      et,
                      modelSize,
                      compModelSize,
                      pathsTotal,
                      pathsCovered,
                      sc,
                      cf,
                      t.modelSizeBytes)
                  case t @ TestReportError(msg, tt, _, et) ⇒
                    ClientDetail(
                      client,
                      succeeded = false,
                      msg,
                      tt,
                      et,
                      modelSize,
                      compModelSize,
                      0,
                      0,
                      CoverageObject(),
                      List(),
                      t.modelSizeBytes)
                }
            }.toList,
            aggregatedCoverage(pv, Left(filteredTestResults.values.map {
              case (_, libRes) ⇒ libRes(pv)
            }.toList)))
        }
      }

      //totalPathsCovered is sum of all paths covered
      val totalPathsCovered: Int = filteredTestResults.map {
        case (_, (_, rest)) ⇒
          rest.values.map {
            case t: TestReport ⇒ t.pathsCovered.intValue()
            case t: TestReportError ⇒ 0
          }.sum
      }.sum

      //Total path count is the path count of all succeeding TestResults
      val totalPathCount: Int = filteredTestResults.map {
        case (_, (_, rest)) ⇒
          rest.values.map {
            case t: TestReport ⇒ t.pathsTotal.intValue()
            case t: TestReportError ⇒ 0
          }.sum
      }.sum

      val (minors, majors) = getMinorMajors()

      saveAggregatedRegressions(analysisResults)
      updateBenchmarkStatus(
        filteredRegressions,
        clients.seq.length,
        totalPathCount,
        totalPathsCovered,
        minors,
        majors)

      if (options.saveDiffForEachClient) {
        diffs.map {
          case (client, libObs) =>
            //val clientDiffs =
            //  Aggregator.aggregate(Map(client -> libObs)).diffs(options.typingRelation)
            val diffK = "diffClient" :: List(
              client.packageName,
              client.packageVersion,
              options.libraryToCheckName)

            DiskCaching.cache(
              libObs.map(
                p =>
                  (
                    p._1.toString,
                    AnalysisResults(p._2, List(), CoverageObject(Map(), Map(), 0)))),
              CacheBehaviour.REGENERATE,
              diffK,
              serializer = SerializerFormats.defaultSerializer)
        }
      }

      RegressionResult(
        filteredRegressions,
        Right(filteredTestResults.mapValues(r ⇒ r._2)))
    }
  }

  def generateStatistics(): Unit = {
    log.separationTitle(
      s"Regression Type Testing on ${options.libraryToCheckName}@${options.libraryVersions.headOption} in statistics mode")
    log.info(s"Running $ApiTrace with options: ${pprint.stringify(options)}")

    val clients = getClients()
    val statsAndModels = clients
      .mapBy(getStatsForClient)
      .seq
      .toMap
      .filter({ case (_, statAndModel) => statAndModel.isDefined })
      // It's safe to use get here due to the filter above
      .map { case (p, statAndModel) => p -> statAndModel.get }

    val aggregateModel = new TreeAPIModel(statsAndModels.flatMap {
      case (_, (_, model)) => model.obs
    }.toList, 0)
    val compressedAggregateModel = aggregateModel.compress(requireEqualArgs = false)
    val combinedStats = new ModelStatistics(aggregateModel, compressedAggregateModel)

    val statsCSV = libraryToTraceOutdir.resolve("stats.csv")
    log.info(
      s"Writing combined stats file for ${options.libraryToCheckName} to ${statsCSV}")
    writeToFile(statsCSV, combinedStats.toCSV.mkString("\n"))

    statsAndModels.foreach {
      case (client, (stats, _)) => {
        val statsCSVClient = libraryToTraceOutdir.resolve(s"${client}-stats.csv")
        log.info(
          s"Writing statsAndModels file for ${options.libraryToCheckName} with client ${client} to ${statsCSVClient}")
        writeToFile(statsCSVClient, stats.toCSV.mkString("\n"))
      }
    }
  }

  /**
    * @param client
    */
  def getStatsForClient(
    client: PackageAtVersion): Option[(ModelStatistics, TreeAPIModel)] = {
    val initLibVersion =
      options.libraryVersions.map(PackageAtVersion(options.libraryToCheckName, _)).head

    getInitModel(client, initLibVersion) match {
      case Left(model) => {
        Some(new ModelStatistics(model, model.compress()), model)
      }
      case Right(_) => None
    }

  }

  def handleRegressionTypeLearner(): RegressionResult = {

    log.separationTitle(
      s"Regression Type Testing on ${options.libraryToCheckName}@${options.libraryVersions.headOption}")
    log.info(s"Running $ApiTrace with options: ${pprint.stringify(options)}")

    if (options.NoRegretsPlus_Mode) {
      TestDistillationBasedStrategy.run()
    } else {
      ClientTestBasedStrategy.run()
    }
  }

  private def learnAndGrabResult(client: PackageAtVersion,
                                 library: PackageAtVersion,
                                 coverage: Boolean = false): TracingResult = {
    val opts =
      ApiTraceOptions(
        library.packageVersion,
        client,
        library,
        clientsWithTestsOutdir,
        libraryToTraceOutdir,
        collectStackTraces = options.collectStackTraces,
        detailedStackTraces = options.detailedStackTraces,
        ignoreFailingInstallations = options.ignoreFailingInstallations,
        testDistillationMode = options.NoRegretsPlus_Mode,
        ignoreTagsMode = options.ignoreTagsMode,
        silent = options.silent,
        coverage = coverage)

    Try {
      val res = Common
        .cmdHandler[TracingResult](Config(Some(opts), options.swarm))
        .get
        .asInstanceOf[TracingResult]
      log.info(s"Successfully loaded tracing result: ${res.getClass}")
      res
    }.recover {
      case e: Throwable =>
        LearningFailure(
          "Failure when running learnAndGrabResult: " + e + "\n" + e.getStackTrace
            .mkString("\n"),
          0)
    }.get

  }

  private def getInitModel(client: PackageAtVersion,
                           initLibVersion: PackageAtVersion,
                           //acceptNonEmptyTestFailures true if non-empty LearningFailures should be returned as TreeAPIModels
                           acceptNonEmptyTestFailures: Boolean = false)
    : Either[TreeAPIModel, LearningFailure] = {
    log.separationTitle(
      s"Entering distillation phase for ${options.libraryToCheckName}@${initLibVersion} against client $client")

    /**
      * The only TracingResult must be the one for the initial version of the library
      */
    learnAndCheckWithClient(client, List(initLibVersion)).head._2 match {
      case l @ LearningFailure(_, tt, obs, _) =>
        if (acceptNonEmptyTestFailures && obs.nonEmpty)
          Left(new TreeAPIModel(l, tt, client.toString))
        else Right(l)
      case l: APIModel => Left(new TreeAPIModel(l, l.testTimeMillis, client.toString))
    }
  }

  def getAuxClientDir(client: PackageAtVersion) = outDir.resolve(s"_auxClient-${client}")

  //Returns (Option[InitModel], Map[libVersion, TestResult])
  private def learnAndCheckWithDistilledTests(client: PackageAtVersion)
    : Option[(TreeAPIModel, Map[PackageAtVersion, TestResult])] = {
    PackageHandlingUtils.tscAll
    val handle = log.startRecord(true)
    val initLibVersion = libraryVersions.head

    log.info(
      s"Starting NoRegretsPlus on ${initLibVersion} -> ${libraryVersions.last} with client ${client}")

    //We do also include head in testLibraryVersions since it is used by the FilterRegressionsAppearingInInitLibaryVersion option
    val testLibVersions = libraryVersions

    val model = getInitModel(client, initLibVersion, acceptNonEmptyTestFailures = true)
    val result = model match {
      case Left(model) => {
        log.info(s"Generated model of size: ${model.size()} for ${client}")
        log.debug(s"model ${model.prettyString()}")

        if (model.obs.isEmpty) {
          //If the initial model is empty, then we just return an empty TestReport for every testLibraryVersion
          Some(
            model,
            testLibVersions
              .map((_, TestReport(List(), 0, 0, 0, 0, CoverageObject(), List())))
              .toMap)
        } else {
          val compressedModel = model.compress()
          log.info(s"Compressed model to size: ${compressedModel.size()}")
          log.debug(s"Compressed model ${compressedModel.prettyString()}")

          val auxClientDir = getAuxClientDir(client)
          Files.createDirectories(auxClientDir)
          val modelFile = auxClientDir.resolve(s"model.json")
          JsonSerializer().serialize(compressedModel.toAPIModel(), modelFile)

          val auxPkgJson = auxClientDir.resolve("package.json")
          val auxPackageJsonGenerator = (version: String) => {
            (("name" -> s"auxiliary-client") ~
              ("version" -> "1.0.0") ~
              ("main" -> "index.html") ~
              ("license" -> "MIT") ~
              ("dependencies" -> (s"${options.libraryToCheckName}" -> version)))
          }

          val modelSizeBytes = org.apache.commons.io.FileUtils.sizeOf(modelFile.toFile)

          Some((
            compressedModel,
            testLibVersions
              .foldLeft[(Option[TestResult], Map[PackageAtVersion, TestResult])](
                (None, Map())) {
                case ((lstTestRes, aggTestResults), testLibVersion) ⇒ {
                  lstTestRes match {
                    //If one test timed out, then we just report the following tests as timeouts as well
                    case Some(t @ TestReportError(_, _, true, _)) ⇒ {
                      (Some(t), aggTestResults + (testLibVersion → t))
                    }
                    case _ ⇒ {
                      //Setup the auxiliary client
                      writeToFile(
                        auxPkgJson,
                        pretty(
                          render(auxPackageJsonGenerator(testLibVersion.packageVersion))))
                      try {
                        PackageHandlingUtils.yarnInstall(auxClientDir)
                      } catch {
                        case e: Exception ⇒
                          TestReportError(
                            s"Yarn install for ${options.libraryToCheckName}@${testLibVersion} failed for ${client} with exception ${e}",
                            0,
                            timeout = false,
                            0)
                      }

                      //Only record coverage of the first two versions
                      val recordCoverage = options.withCoverage && testLibVersions
                        .indexOf(testLibVersion) < 2

                      //Run the test distiller
                      val report = try {
                        PackageHandlingUtils.runTestDistiller(
                          modelFile,
                          options.libraryToCheckName,
                          auxClientDir,
                          testLibVersion.packageVersion,
                          enableValueChecking = options.enableValueChecking,
                          withCoverage = recordCoverage,
                          silent = options.silent)
                        val testReportFile =
                          modelFile.getParent.resolve(Globals.testResultFileName)
                        val testResult =
                          JsonSerializer().deserialize[TestResult](testReportFile)
                        testResult.modelSizeBytes = modelSizeBytes
                        testResult
                      } catch {
                        case e: TimeoutException ⇒ {
                          val t = TestReportError(
                            s"Distilled tests for ${options.libraryToCheckName}@${testLibVersion} failed for ${client} with exception ${e}",
                            0,
                            timeout = true,
                            0)
                          t.modelSizeBytes = modelSizeBytes
                          t
                        }
                        case e: Exception ⇒
                          val t = TestReportError(
                            s"Distilled tests for ${options.libraryToCheckName}@${testLibVersion} failed for ${client} with exception ${e}",
                            0,
                            timeout = false,
                            0)
                          t.modelSizeBytes = modelSizeBytes
                          t
                      }
                      (Some(report), aggTestResults + (testLibVersion → report))
                    }
                  }
                }
              }
              ._2))
        }
      }
      case Right(l) => None //Report None when failing to generate initial model.
    }

    Utils.writeToFile(
      DiskCaching.cacheDir.resolve(s"NoRegretsPlusRes-${initLibVersion}-${client}.log"),
      log.stopRecord(handle).get.toString(),
      silent = true)
    result
  }

  private def learnAndCheckWithClient(
    client: PackageAtVersion,
    libraryVersions: List[PackageAtVersion],
    coverage: Boolean = false): Map[PackageAtVersion, TracingResult] = {

    PackageHandlingUtils.tscAll

    log.separationTitle(
      s"Entering learning phase for ${options.libraryVersions} against client $client")

    // learning using the client for every library update.
    libraryVersions.mapBy { libraryVersionToCheck =>
      // Clone the package locally, useful to save caches even when regenerating the benchmark status
      nowRunning(client, libraryVersionToCheck, "cloning")
      var clientClonedDir: Option[Path] = None
      try {
        clientClonedDir = Some(
          PackageHandlingUtils
            .clonePackageNoRetry(
              clientsWithTestsOutdir,
              registry,
              client,
              cache = true,
              ignoreTags = options.ignoreTagsMode)
            ._1)
      } catch {
        case _: Throwable =>
      }

      val learnKey = "learn" :: List(libraryVersionToCheck.toString, client.toString)

      //Only record coverage for the first two versions of the library
      val recordCoverage = coverage && libraryVersions.indexOf(libraryVersionToCheck) < 2

      val result = DiskCaching.cache(
        {
          if (options.neverRun) {
            LearningFailure(
              "Cache file was not present and received request to avoid running",
              0)
          } else {
            nowRunning(client, libraryVersionToCheck, "tracing")
            val result = learnAndGrabResult(client, libraryVersionToCheck, recordCoverage)
            result
          }
        },
        CommandCachePolicy.getMatchingCachePolicy(options.learningCommandCachePolicy),
        learnKey,
        serializer = SerializerFormats.defaultSerializer,
        threadedLog = true,
        rerunCondition = { learned: TracingResult =>
          options.rerunFailedLearning && learned.isInstanceOf[LearningFailure]
        })
      log.info(
        s"Generated ${result.getClass.getName} when checking library ${libraryVersionToCheck.toString} against client ${client.toString}")

      doneRunning(client, libraryVersionToCheck)

      result match {
        case learned: APIModel =>
          BenchmarksStatus.default.addStatus(
            libraryVersionToCheck,
            client,
            Status(
              "Tests succeeded, learned can be loaded",
              learnKey,
              learned.observations.length))
        case err: LearningFailure =>
          BenchmarksStatus.default.addStatus(
            libraryVersionToCheck,
            client,
            Status(err.msg, learnKey, err.observations.length))
      }

      val clientFolderSize: Long = clientClonedDir
        .map(p ⇒ org.apache.commons.io.FileUtils.sizeOfDirectory(p.toFile))
        .getOrElse(0)

      result.clientSize = clientFolderSize
      result
    }
  }

  private def nowRunning(client: PackageAtVersion,
                         library: PackageAtVersion,
                         phase: String): Unit = {
    synchronized {
      status += client -> (library, phase)
      dumpStatus()
    }
  }

  private def doneRunning(client: PackageAtVersion, library: PackageAtVersion): Unit = {
    synchronized {
      status -= client
      dumpStatus()
    }
  }

  private def dumpStatus(): Unit = {
    val out = options.outDir.resolve("status.txt")
    writeToFile(out, s"$options\n\n${status.mkString("\n")}", silent = true)
  }

  private def getMinorMajors(): (Int, Int) = {
    options.libraryVersions.tail
      .foldLeft[((Int, Int), String)]((0, 0), options.libraryVersions.head) {
        case (((minors, majors), prev), next) ⇒ {
          val nextVer = Versions.toVersion(next)
          val prevVer = Versions.toVersion(prev)
          if (nextVer.get.ver.getMajor > prevVer.get.ver.getMajor) {
            ((minors, majors + 1), next)
          } else {
            ((minors + 1, majors), next)
          }
        }
      }
      ._1
  }

  private def aggregatedCoverage(
    libraryVersion: PackageAtVersion,
    results: Either[List[TestResult], List[TracingResult]]) = {

    val coverageFiles = results
      .fold(
        _.flatMap {
          case TestReport(_, _, _, _, _, _, coverageFiles) ⇒
            Some(coverageFiles)
          case t: TestReportError ⇒ None
        },
        _.flatMap {
          case APIModel(_, _, _, _, coverageFiles) ⇒ Some(coverageFiles)
          case l: LearningFailure ⇒ None
        })
      .flatten

    //group coverage files by module
    val modulePattern = s"(.*)-${libraryVersion.packageVersion}-coverage.json".r
    val groupedCoverageFiles = coverageFiles.groupBy(
      file ⇒
        modulePattern
        //.findAllIn(Paths.get(file).getFileName.toString)
          .findAllIn(file.substring(file.indexOf("coverage/") + "coverage/".length))
          .matchData
          .next()
          .group(1))

    val covMap: Map[String, IstanbulCoverageObject] =
      groupedCoverageFiles.mapValuesWithKey {
        case (module, files) ⇒ {
          val coverageFileFolder = Paths
            .get("out-noregrets")
            .resolve("coverage")
            .resolve(libraryVersion.packageName)
            .resolve(s"$module-${libraryVersion.packageVersion}")
          val coverageFilesFile = coverageFileFolder.resolve("coverageFiles.json")
          if (!coverageFileFolder.toFile.exists()) {
            Files.createDirectories(coverageFileFolder)
          }

          JsonSerializer().serialize[List[String]](files, coverageFilesFile)
          val aggCoverageFile = coverageFileFolder.resolve("aggCoverage.json")
          PackageHandlingUtils.runCoverageAggregator(coverageFilesFile, aggCoverageFile)

          JsonSerializer()
            .deserialize[IstanbulCoverageObject](aggCoverageFile) //.statements.pct
        }
      }
    CoverageObject(
      covMap.mapValues(cov ⇒ cov.statements),
      covMap.mapValues(cov ⇒ cov.lines),
      covMap.values.map(_.lines.covered).sum)
  }
}

object RegressionResult {
  //Client -> lib -> result
  type NoRegretsResults =
    Map[PackageAtVersion, Map[PackageAtVersion, TracingResult]]
  type NoRegretsPlusResults =
    Map[PackageAtVersion, Map[PackageAtVersion, TestResult]]
  type ClientResults =
    Either[NoRegretsResults, NoRegretsPlusResults]
}

case class RegressionResult(diff: Map[PackageAtVersion, RegressionInfo],
                            clientResults: ClientResults) {}

case class NoClients() extends RuntimeException

object RegressionTypeLearnerOptions {
  def make(parser: scopt.OptionParser[Config]): Seq[OptionDef[_, Common.Config]] = {

    implicit def asRegOpt(
      cmd: Option[Common.CommandOptions]): RegressionTypeLearnerOptions =
      cmd.get.asInstanceOf[RegressionTypeLearnerOptions]

    Seq(
      parser
        .arg[String]("library")
        .action((x, c) => c.copy(cmd = Some(c.cmd.copy(libraryToCheckName = x))))
        .text("library name"),
      parser
        .arg[String]("version")
        .unbounded()
        .action((x, c) =>
          c.copy(cmd = Some(c.cmd.copy(libraryVersions = x :: c.cmd.libraryVersions))))
        .text("library version"),
      parser
        .opt[Unit]("regenerate-cache")
        .action((x, c) =>
          c.copy(cmd = Some(
            c.cmd.copy(learningCommandCachePolicy = CommandCachePolicy.REGENERATE_DATA))))
        .text("file with packages to run trace"),
      parser
        .opt[String]("packages-file")
        .action((x, c) => c.copy(cmd = Some(c.cmd.copy(clients = Right(Paths.get(x))))))
        .text("file with packages to run trace"))
  }
}

case class SoundnessOfLearnedTypeFailed(message: String)
    extends RuntimeException(s"$message")

case class RegressionTypeLearnerOptions(
  libraryToCheckName: String = null,
  libraryVersions: List[String] = List(),
  outDir: Path = Paths.get("out-noregrets/regression-tc"),
  saveDiffForEachClient: Boolean = true,
  clients: Either[List[PackageAtVersion], Path] = null,
  doNotRun: Set[PackageAtVersion] = Set(),
  neverRun: Boolean = false,
  rerunFailedLearning: Boolean = true,
  collectStackTraces: Boolean = true,
  detailedStackTraces: Boolean = false,
  ignoreFailingInstallations: Boolean = false,
  swarm: Boolean = false,
  generateBlacklist: Boolean = true,
  useSmartDiff: Boolean = true,
  NoRegretsPlus_Mode: Boolean = true,
  ignoreTagsMode: Boolean = false,
  enableValueChecking: Boolean = false,
  clientPriority: ClientPriority = ClientPriority.OnlyOldest,
  learningCommandCachePolicy: CommandCachePolicy.Value =
    CommandCachePolicy.USE_DATA_IF_PRESENT_REGENERATE_OTHERWISE,
  typingRelation: TypingRelation = TypeRegressionPaperTypingRelation,
  silent: Boolean = false,
  withCoverage: Boolean = false)
    extends CommandOptions {}
