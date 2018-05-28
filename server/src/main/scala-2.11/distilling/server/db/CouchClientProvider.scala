package distilling.server.db

import com.ibm.couchdb.CouchDb
import distilling.server.Globals
import distilling.server.utils._

import scala.concurrent.duration._
import scala.util.Try

object CouchClientProvider {
  private val log =
    Logger(this.getClass.getSimpleName, Log.Level.Info)
  var address: String = null
  var couchdb: CouchDb = null
  try {

    // Temporarily disable logging
    //val couchLogger = org.slf4j.LoggerFactory
    //  .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
    //  .asInstanceOf[ch.qos.logback.classic.Logger]
    //val oldLevel = couchLogger.getLevel
    //couchLogger.setLevel(ch.qos.logback.classic.Level.OFF)

    val (addresss, couchdbb) = Globals.registryServers.toStream
      .map { server =>
        Try {
          val cdb =
            CouchDb(
              server.host,
              server.port,
              https = false,
              server.username,
              server.password)
          // trigger some use of the server
          cdb.server.info.unsafePerformSyncFor(timeout = 3.seconds)

          (server.host, cdb)
        }
      }
      .find(_.isSuccess)
      .get
      .get

    log.info(s"Using registry server: $address")
    address = addresss
    couchdb = couchdbb

    // Re-enabling logging
    //couchLogger.setLevel(oldLevel)
  } catch {
    case e: Exception => e.printStackTrace()
  }

}

class CouchClientProvider() {
  val address = CouchClientProvider.address
  val couchdb = CouchClientProvider.couchdb
}
