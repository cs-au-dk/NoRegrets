package backend.commands

import backend.utils.DiskCaching

object CommandCachePolicy extends Enumeration {
  val REGENERATE_DATA, VERIFY_PRESENCE_OF_DATA, VERIFY_PRESENCE_AND_CONTENT_OF_DATA,
  USE_DATA_IF_PRESENT_REGENERATE_OTHERWISE = Value

  def getMatchingCachePolicy(
    commandCachePolicy: CommandCachePolicy.Value): DiskCaching.CacheBehaviour.Value = {
    commandCachePolicy match {
      case REGENERATE_DATA => DiskCaching.CacheBehaviour.REGENERATE
      case VERIFY_PRESENCE_OF_DATA | VERIFY_PRESENCE_AND_CONTENT_OF_DATA =>
        DiskCaching.CacheBehaviour.VERIFY_EXISTS_AND_USE_THAT
      case USE_DATA_IF_PRESENT_REGENERATE_OTHERWISE =>
        DiskCaching.CacheBehaviour.USE_IF_EXISTS_GENERATE_OTHERWISE
    }
  }

  case class DataContentVerificationFailure(msg: String) extends RuntimeException(msg)
}
