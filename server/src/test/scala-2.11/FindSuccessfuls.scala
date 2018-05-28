import distilling.server.commands.Dependents.DependentOptions
import distilling.server.commands.Successfuls.SuccessfulOptions
import distilling.server.commands._
import distilling.server.utils.NotationalUtils
import org.scalatest._
import org.scalatest.time.SpanSugar._

class FindSuccessfulVerifyCachePresence extends FindSuccessfuls {
  override implicit val SUCCESSFUL_MODE = CommandCachePolicy.VERIFY_PRESENCE_OF_DATA
  override implicit val swarm = false
  val timeout = 5.minutes
}

class FindSuccessfulVerifyCacheContent extends FindSuccessfuls {
  override implicit val SUCCESSFUL_MODE =
    CommandCachePolicy.VERIFY_PRESENCE_AND_CONTENT_OF_DATA
  override implicit val swarm = false
  val timeout = 120.minutes
}

class FindSuccessfulRegenerate extends FindSuccessfuls {
  override implicit val SUCCESSFUL_MODE =
    CommandCachePolicy.REGENERATE_DATA
  override implicit val swarm = false
  val timeout = 1440.minutes
}

class FindSuccessfulRegenerateSwarm extends FindSuccessfuls with ParallelTestExecution {
  override implicit val SUCCESSFUL_MODE =
    CommandCachePolicy.REGENERATE_DATA
  override implicit val swarm = true
  val timeout = 1440.minutes
}

class FindSuccessfulGenerateIfMissing extends FindSuccessfuls {
  override implicit val SUCCESSFUL_MODE =
    CommandCachePolicy.USE_DATA_IF_PRESENT_REGENERATE_OTHERWISE
  override implicit val swarm = false
  val timeout = 1440.minutes
}

class FindSuccessfulGenerateIfMissingSwarm
    extends FindSuccessfuls
    with ParallelTestExecution {
  override implicit val SUCCESSFUL_MODE =
    CommandCachePolicy.USE_DATA_IF_PRESENT_REGENERATE_OTHERWISE
  override implicit val swarm = true
  val timeout = 1440.minutes
}

trait FindSuccessfuls extends TestEntries {
  val SUCCESSFUL_MODE: CommandCachePolicy.Value
  val DEPENDENTS_MODE: CommandCachePolicy.Value =
    CommandCachePolicy.USE_DATA_IF_PRESENT_REGENERATE_OTHERWISE
  val swarm: Boolean

  implicit val atNotation = NotationalUtils.atNotationToPackage _

  def perform(l: String, upTo: Option[String]): Unit = {
    val pv = NotationalUtils.atNotationToPackage(l)
    val dependents =
      Dependents.handleDependentsCmd(
        DependentOptions(
          pv.packageName,
          Some(pv.packageVersion),
          limit = 1000,
          commandCachePolicy = DEPENDENTS_MODE))

    Successfuls(
      SuccessfulOptions(
        pv.packageName,
        pv.packageVersion,
        packages = Left(dependents),
        swarm = swarm,
        commandCachePolicy = SUCCESSFUL_MODE)).handleSuccessfulsCmd()
  }
}
