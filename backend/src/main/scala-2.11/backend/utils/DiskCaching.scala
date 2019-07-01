package backend.utils

import java.io.FileNotFoundException
import java.nio.file._

import backend.datastructures.SerializerFormats

import scala.util._

object DiskCaching {
  val cacheDir = Paths.get("caching")
  cacheDir.toFile.mkdirs()

  def getPathFromKeys(keys: List[String],
                      serializer: Serializer = JsonSerializer()(
                        SerializerFormats.commonSerializationFormats)): Path = {
    val kstr = keys.mkString("-") + serializer.extension
    cacheDir.resolve(kstr)
  }

  def cache[B <: AnyRef](procedure: => B,
                         regenerationPolicy: CacheBehaviour.Value,
                         keys: List[String],
                         threadedLog: Boolean = false,
                         serializer: Serializer =
                           JsonSerializer()(SerializerFormats.commonSerializationFormats),
                         silent: Boolean = false,
                         rerunCondition: B => Boolean = { _: B =>
                           false
                         })(implicit m: Manifest[B]): B = {

    val log =
      Logger(this.getClass.getSimpleName, if (silent) Log.Level.Error else Log.Level.Info)

    val cacheFile = getPathFromKeys(keys, serializer)

    val deser =
      regenerationPolicy match {
        case CacheBehaviour.REGENERATE =>
          log.info(
            s"Cache $cacheFile will be re-generated, according to policy $regenerationPolicy")
          None
        case CacheBehaviour.VERIFY_EXISTS_AND_USE_THAT |
            CacheBehaviour.USE_IF_EXISTS_GENERATE_OTHERWISE =>
          Try(serializer.deserialize[B](cacheFile)) match {
            case Failure(e) =>
              // Printing de-serialization errors since they may arise from real
              // problem with serialization rather than missing cache file
              e match {
                case _: FileNotFoundException =>
                  log.info(s"Cache file $cacheFile not present")
                case _ =>
                  log.error(s"Failed deserialization of cache $cacheFile, not usable: $e")
              }

              if (regenerationPolicy == CacheBehaviour.VERIFY_EXISTS_AND_USE_THAT)
                throw RequiresCacheMissing(
                  s"Required cache $cacheFile is missing, error according to policy $regenerationPolicy",
                  e)
              None
            case Success(v) =>
              log.info(s"Using cache $cacheFile, according to policy $regenerationPolicy")
              Some(v)
          }
      }

    def performComputation() = {
      // Record the log as a remainder of what has been done
      val handle = log.startRecord(threadedLog)
      val computed = try {
        procedure
      } finally {
        val logCopy = log.stopRecord(handle)
        Utils.writeToFile(
          cacheFile.getParent.resolve(cacheFile.getFileName + ".log"),
          logCopy.get.toString(),
          silent = true)
      }
      serializer.serialize[B](computed, cacheFile)
      log.info(s"Saved cache $cacheFile")
      computed
    }

    deser
      .map { value =>
        if (rerunCondition(value)) {
          log.info(s"Rerunning $cacheFile because cache content satisfies condition")
          performComputation()
        } else {
          value
        }
      }
      .getOrElse { performComputation() }

  }

  case class RequiresCacheMissing(msg: String, e: Throwable)
      extends RuntimeException(msg, e)

  object CacheBehaviour extends Enumeration {
    val REGENERATE, VERIFY_EXISTS_AND_USE_THAT, USE_IF_EXISTS_GENERATE_OTHERWISE = Value
  }

}
