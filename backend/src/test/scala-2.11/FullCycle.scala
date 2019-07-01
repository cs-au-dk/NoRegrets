import data.FilteredClientsNewApproach
import backend.commands.{ClientPriority, CommandCachePolicy}
import backend.regression_typechecking.TypeRegressionPaperTypingRelation
import backend.utils.NotationalUtils
import org.scalatest._
import org.scalatest.time.SpanSugar._

class FullCycleVerifyNoRegretsPlus extends FullCycle {
  override val swarm: Boolean = false
  override val rerunFailed: Boolean = true
  override val neverRun: Boolean = false
  override val LEARNING_MODE = CommandCachePolicy.VERIFY_PRESENCE_OF_DATA
}

class FullGenerateMissingNoRegretsPlus extends FullCycle {
  override val swarm: Boolean = false
  override val rerunFailed: Boolean = true
  override val neverRun: Boolean = false
  override val LEARNING_MODE =
    CommandCachePolicy.USE_DATA_IF_PRESENT_REGENERATE_OTHERWISE
}

class FullGenerateMissingSwarmNoRegretsPlus extends FullCycle with ParallelTestExecution {
  override val swarm: Boolean = true
  override val rerunFailed: Boolean = true
  override val neverRun: Boolean = false
  override val LEARNING_MODE =
    CommandCachePolicy.USE_DATA_IF_PRESENT_REGENERATE_OTHERWISE
}

class FullRegenerateBenchmarkStatusNoRegretsPlus extends FullCycle {
  override val swarm: Boolean = false
  override val rerunFailed: Boolean = false
  override val neverRun: Boolean = true
  override val LEARNING_MODE =
    CommandCachePolicy.USE_DATA_IF_PRESENT_REGENERATE_OTHERWISE
}

class FullCycleRegenerateNoRegretsPlus extends FullCycle {
  override val swarm: Boolean = false
  override val rerunFailed: Boolean = true
  override val neverRun: Boolean = false
  override val LEARNING_MODE = CommandCachePolicy.REGENERATE_DATA
}

class FullCycleRegenerateNoRegretsPlusIgnoreTagsValueChecking extends FullCycle {
  override val swarm: Boolean = false
  override val rerunFailed: Boolean = true
  override val neverRun: Boolean = false
  override val LEARNING_MODE = CommandCachePolicy.REGENERATE_DATA
  override val enableValueChecking = true
  override val ignoreTagsMode = true
}

class FullCycleRegenerateNoRegretsPlusIgnoreTags extends FullCycle {
  override val swarm: Boolean = false
  override val rerunFailed: Boolean = true
  override val neverRun: Boolean = false
  override val LEARNING_MODE = CommandCachePolicy.REGENERATE_DATA
  override val ignoreTagsMode = true
}

class FullCycleRegenerateNoRegretsPlusUnconstrained extends FullCycle {
  override val swarm: Boolean = false
  override val rerunFailed: Boolean = true
  override val neverRun: Boolean = false
  override val LEARNING_MODE = CommandCachePolicy.REGENERATE_DATA
  override val ignoreTagsMode = true
  override val clientPriority = ClientPriority.OnlyNewest
}

class FullCycleRegenerateNoRegretsPlusUnconstrainedWithCoverage extends FullCycle {
  override val swarm: Boolean = false
  override val rerunFailed: Boolean = true
  override val neverRun: Boolean = false
  override val LEARNING_MODE = CommandCachePolicy.REGENERATE_DATA
  override val ignoreTagsMode = true
  override val clientPriority = ClientPriority.OnlyNewest
  override val withCoverage: Boolean = true
}

class FullCycleRegenerateNoRegrets extends FullCycle {
  override val swarm: Boolean = false
  override val rerunFailed: Boolean = true
  override val neverRun: Boolean = false
  override val LEARNING_MODE = CommandCachePolicy.REGENERATE_DATA
  override val NoRegretsPlusMode = false
}

class FullCycleRegenerateNoRegretsWithCoverage extends FullCycle {
  override val swarm: Boolean = false
  override val rerunFailed: Boolean = true
  override val neverRun: Boolean = false
  override val LEARNING_MODE = CommandCachePolicy.REGENERATE_DATA
  override val NoRegretsPlusMode = false
  override val withCoverage = true
}

class FullCycleRegenerateSwarmNoRegretsPlus extends FullCycle {
  override val swarm: Boolean = true
  override val rerunFailed: Boolean = true
  override val neverRun: Boolean = false
  override val LEARNING_MODE = CommandCachePolicy.REGENERATE_DATA
}

class FullCycleStatisticsNoRegretsPlus extends FullCycle {
  override val swarm: Boolean = false
  override val rerunFailed: Boolean = true
  override val neverRun: Boolean = false
  override val LEARNING_MODE = CommandCachePolicy.REGENERATE_DATA
  override val generateStatistics: Boolean = true
}

trait FullCycle extends TestEntries {

  val timeout = 1440.minutes

  val LEARNING_MODE: CommandCachePolicy.Value

  val rerunFailed: Boolean

  val neverRun: Boolean

  val swarm: Boolean

  val generateStatistics = false

  val NoRegretsPlusMode = true

  val ignoreTagsMode = false

  val enableValueChecking = false

  val clientPriority = ClientPriority.OnlyOldest

  val withCoverage = false

  def perform(s: String, upTo: Option[String]): Unit = {
    try {
      val pv = NotationalUtils.atNotationToPackage(s)
      fullCycleRegressionApproach(
        pv.packageName,
        pv.packageVersion,
        libraryEndVersion = upTo,
        doNotRun = FilteredClientsNewApproach
          .badClients(s)
          .map(NotationalUtils.atNotationToPackage),
        rerunFailed = rerunFailed,
        typingRelation = TypeRegressionPaperTypingRelation,
        neverRun = neverRun,
        swarm = swarm,
        learningCommandCachePolicy = LEARNING_MODE,
        checkingCommandCachePolicy = CommandCachePolicy.REGENERATE_DATA,
        generateStatistics = generateStatistics,
        NoRegretsPlusMode = NoRegretsPlusMode,
        ignoreTagsMode = ignoreTagsMode,
        enableValueChecking = enableValueChecking,
        clientPriority = clientPriority,
        withCoverage = withCoverage)
    } catch {
      case e: Throwable =>
        System.err.println(e)
        e.getStackTrace.foreach(s => System.err.println(s.toString))
        assert(false)
    }
  }
}
