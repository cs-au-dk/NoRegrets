import backend.commands.ClientPriority.ClientPriority
import backend.commands.Dependents.DependentOptions
import backend.commands.Successfuls.SuccessfulOptions
import backend.commands._
import backend.utils.NotationalUtils
import org.scalatest._
import org.scalatest.time.SpanSugar._

class FindSuccessfulVerifyCachePresenceNoRegrets extends FindSuccessfuls {
  override implicit val SUCCESSFUL_MODE = CommandCachePolicy.VERIFY_PRESENCE_OF_DATA
  override implicit val swarm = false
  val timeout = 5.minutes
}

class FindSuccessfulVerifyCacheContentNoRegrets extends FindSuccessfuls {
  override implicit val SUCCESSFUL_MODE =
    CommandCachePolicy.VERIFY_PRESENCE_AND_CONTENT_OF_DATA
  override implicit val swarm = false
  val timeout = 120.minutes
}

class FindSuccessfulRegenerateNoRegrets extends FindSuccessfuls {
  override implicit val SUCCESSFUL_MODE =
    CommandCachePolicy.REGENERATE_DATA
  override implicit val swarm = false
  val timeout = 1440.minutes
}

class FindSuccessfulRegenerateNoRegretsWithDependentsRegen extends FindSuccessfuls {
  override implicit val DEPENDENTS_MODE = CommandCachePolicy.REGENERATE_DATA
  override implicit val SUCCESSFUL_MODE =
    CommandCachePolicy.REGENERATE_DATA
  override implicit val swarm = false
  val timeout = 1440.minutes
}

class FindSuccessfulRegenerateSwarmNoRegrets
    extends FindSuccessfuls
    with ParallelTestExecution {
  override implicit val SUCCESSFUL_MODE =
    CommandCachePolicy.REGENERATE_DATA
  override implicit val swarm = true
  val timeout = 1440.minutes
}

class FindSuccessfulGenerateIfMissingNoRegrets extends FindSuccessfuls {
  override implicit val SUCCESSFUL_MODE =
    CommandCachePolicy.USE_DATA_IF_PRESENT_REGENERATE_OTHERWISE
  override implicit val swarm = false
  val timeout = 1440.minutes
}

class FindSuccessfulGenerateIfMissingSwarmNoRegrets
    extends FindSuccessfuls
    with ParallelTestExecution {
  override implicit val SUCCESSFUL_MODE =
    CommandCachePolicy.USE_DATA_IF_PRESENT_REGENERATE_OTHERWISE
  override implicit val swarm = true
  val timeout = 1440.minutes
}

class FindSuccessfulRegenerateNoRegretsPlus extends FindSuccessfuls {
  override implicit val SUCCESSFUL_MODE =
    CommandCachePolicy.REGENERATE_DATA
  override implicit val swarm = false
  val timeout = 1440.minutes
  override val NoRegretsPlusMode = true
}

class FindSuccessfulRegenerateNoRegretsPlusUnconstrained extends FindSuccessfuls {
  override implicit val SUCCESSFUL_MODE =
    CommandCachePolicy.REGENERATE_DATA
  override implicit val swarm = false
  val timeout = 1440.minutes
  override val NoRegretsPlusMode = true
  override val ignoreTagsMode = true
  override val clientPriority = ClientPriority.OnlyNewest
}

class FindSuccessfulRegenerateNoRegretsPlusWithDependentsRegen extends FindSuccessfuls {
  override implicit val SUCCESSFUL_MODE =
    CommandCachePolicy.REGENERATE_DATA
  override implicit val DEPENDENTS_MODE =
    CommandCachePolicy.REGENERATE_DATA
  override implicit val swarm = false
  val timeout = 1440.minutes
  override val NoRegretsPlusMode = true
}

class FindSuccessfulRegenerateNoRegretsPlusWithDependentsRegenUnconstrained
    extends FindSuccessfuls {
  override implicit val SUCCESSFUL_MODE =
    CommandCachePolicy.REGENERATE_DATA
  override implicit val DEPENDENTS_MODE =
    CommandCachePolicy.REGENERATE_DATA
  override implicit val swarm = false
  val timeout = 1440.minutes
  override val NoRegretsPlusMode = true
  override val ignoreTagsMode = true
}

trait FindSuccessfuls extends TestEntries {
  val SUCCESSFUL_MODE: CommandCachePolicy.Value
  val DEPENDENTS_MODE: CommandCachePolicy.Value =
    CommandCachePolicy.USE_DATA_IF_PRESENT_REGENERATE_OTHERWISE
  val NoRegretsPlusMode: Boolean = false
  val ignoreTagsMode: Boolean = false
  val swarm: Boolean
  val clientPriority: ClientPriority = ClientPriority.OnlyOldest

  implicit val atNotation = NotationalUtils.atNotationToPackage _

  def perform(l: String, upTo: Option[String]): Unit = {
    val pv = NotationalUtils.atNotationToPackage(l)
    val dependents =
      Dependents.handleDependentsCmd(
        DependentOptions(
          pv.packageName,
          if (ignoreTagsMode) None else Some(pv.packageVersion),
          limit = 2000,
          commandCachePolicy = DEPENDENTS_MODE,
          NoRegretsPlusMode = NoRegretsPlusMode,
          clientPriority = clientPriority))

    Successfuls(
      SuccessfulOptions(
        pv.packageName,
        pv.packageVersion,
        packages = Left(dependents),
        swarm = swarm,
        NoRegretsPlusMode = NoRegretsPlusMode,
        ignoreTagsMode = ignoreTagsMode,
        commandCachePolicy = SUCCESSFUL_MODE,
        clientPriority = clientPriority)).handleSuccessfulsCmd()
  }
}
