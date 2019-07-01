import java.nio.file.Paths

import backend.commands._
import backend.datastructures.PackageAtVersion
import backend.regression_typechecking._
import backend.utils.{Log, Logger, NotationalUtils}
import org.scalatest._
import org.scalatest.time.SpanSugar._

abstract class DebuggingBase extends FlatSpec with Matchers {
  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)

  val timeout = 600.minutes
  protected val onlyNoRegretsPlus = false;

  implicit val atNotation: (String => PackageAtVersion) =
    NotationalUtils.atNotationToPackage

  def runEvolution(library: PackageAtVersion,
                   evolution: List[String],
                   client: PackageAtVersion,
                   expect: Map[PackageAtVersion, RegressionInfo] => Boolean,
                   dontCheck: Boolean = false,
                   collectStackTraces: Boolean = true,
                   detailedStackTraces: Boolean = false,
                   ignoreFailingInstallations: Boolean = false,
                   smartAugmentation: Boolean = false,
                   NoRegretsPlusMode: Boolean = false,
                   silent: Boolean = true,
                   assertOnFailure: Boolean = false,
                   unconstrainedMode: Boolean = false,
                   enableValueChecking: Boolean = false,
                   withCoverage: Boolean = false): Assertion = {

    if (onlyNoRegretsPlus && !NoRegretsPlusMode) {
      assert(true)
    } else {

      val outDir = Paths.get("out-type-regression/debugging")
      val outDirForSelected =
        outDir.resolve(
          library.toString + "_" + evolution.mkString("_") + "_" + client)

      outDirForSelected.toFile.mkdirs()

      val opts = RegressionTypeLearnerOptions(
        libraryToCheckName = library.packageName,
        libraryVersions = library.packageVersion :: evolution,
        outDir = outDirForSelected,
        clients = Left(List(client)),
        swarm = false,
        useSmartDiff = smartAugmentation,
        collectStackTraces = collectStackTraces,
        detailedStackTraces = detailedStackTraces,
        ignoreFailingInstallations = ignoreFailingInstallations,
        NoRegretsPlus_Mode = NoRegretsPlusMode,
        learningCommandCachePolicy = CommandCachePolicy.REGENERATE_DATA,
        silent = silent,
        enableValueChecking = enableValueChecking,
        ignoreTagsMode = unconstrainedMode,
        withCoverage = withCoverage
      )

      val res = RegressionTypeLearner(opts).handleRegressionTypeLearner()
      val hasFailures = res.clientResults match {
        case Left(a) ⇒ a(client).values.exists(_.isInstanceOf[LearningFailure])
        case Right(a) ⇒ a(client).values.exists(_.isInstanceOf[TestReportError])
      }
      if (hasFailures && !assertOnFailure) {
        assert(false)
      } else {
        assert(expect(res.diff))
      }
    }
  }

  def mkDiff(client: String,
             pre: String,
             post: String,
             obs: Map[String, TracingResult],
             typingRelation: TypingRelation = TypeRegressionPaperTypingRelation)
    : Map[PackageAtVersion, RegressionInfo] = {
    Aggregator
      .aggregate(
        Map(
          NotationalUtils.atNotationToPackage(client) -> Map(
            NotationalUtils.atNotationToPackage(pre) -> obs(pre),
            NotationalUtils.atNotationToPackage(post) -> obs(post))))
      .diffs(typingRelation)
  }

  "big-integer-against-deposit-iban" should "run" in {
    runEvolution("big-integer@1.4.6", List("1.4.7"), "deposit-iban@1.1.0", {
      diff =>
        true
    }, NoRegretsPlusMode = true, withCoverage = false)
  }
}

class Debugging extends DebuggingBase {}
