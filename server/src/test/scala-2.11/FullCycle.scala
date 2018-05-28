import data.FilteredClientsNewApproach
import distilling.server.commands.CommandCachePolicy
import distilling.server.regression_typechecking.{TypeRegressionPaperTypingRelation}
import distilling.server.utils.NotationalUtils
import org.scalatest._
import org.scalatest.time.SpanSugar._

class FullCycleVerify extends FullCycle {
  override val swarm: Boolean = false
  override val rerunFailed: Boolean = true
  override val neverRun: Boolean = false
  override val LEARNING_MODE = CommandCachePolicy.VERIFY_PRESENCE_OF_DATA
}

class FullGenerateMissing extends FullCycle {
  override val swarm: Boolean = false
  override val rerunFailed: Boolean = true
  override val neverRun: Boolean = false
  override val LEARNING_MODE =
    CommandCachePolicy.USE_DATA_IF_PRESENT_REGENERATE_OTHERWISE
}

class FullGenerateMissingSwarm extends FullCycle with ParallelTestExecution {
  override val swarm: Boolean = true
  override val rerunFailed: Boolean = true
  override val neverRun: Boolean = false
  override val LEARNING_MODE =
    CommandCachePolicy.USE_DATA_IF_PRESENT_REGENERATE_OTHERWISE
}

class FullRegenerateBenchmarkStatus extends FullCycle {
  override val swarm: Boolean = false
  override val rerunFailed: Boolean = false
  override val neverRun: Boolean = true
  override val LEARNING_MODE =
    CommandCachePolicy.USE_DATA_IF_PRESENT_REGENERATE_OTHERWISE
}

class FullCycleRegenerate extends FullCycle {
  override val swarm: Boolean = false
  override val rerunFailed: Boolean = true
  override val neverRun: Boolean = false
  override val LEARNING_MODE = CommandCachePolicy.REGENERATE_DATA
}

class FullCycleRegenerateSwarm extends FullCycle {
  override val swarm: Boolean = true
  override val rerunFailed: Boolean = true
  override val neverRun: Boolean = false
  override val LEARNING_MODE = CommandCachePolicy.REGENERATE_DATA
}

trait FullCycle extends TestEntries {

  val timeout = 1440.minutes

  val LEARNING_MODE: CommandCachePolicy.Value

  val rerunFailed: Boolean

  val neverRun: Boolean

  val swarm: Boolean

  def perform(s: String, upTo: Option[String]): Unit = {
    try {
      val pv = NotationalUtils.atNotationToPackage(s)
      fullCycleRegressionApproach(
        pv.packageName,
        pv.packageVersion,
        libraryEndVersion = upTo,
        allowedFailures = FilteredClientsNewApproach
          .badClients(s)
          .map(NotationalUtils.atNotationToPackage),
        rerunFailed = rerunFailed,
        typingRelation = TypeRegressionPaperTypingRelation,
        neverRun = neverRun,
        swarm = swarm,
        learningCommandCachePolicy = LEARNING_MODE,
        checkingCommandCachePolicy = CommandCachePolicy.REGENERATE_DATA)
    } catch {
      case e: Throwable =>
        System.err.println(e)
        assert(false)
    }
  }
}
