package backend.db

import com.ibm.couchdb.CouchDb
import backend.Globals
import backend.utils._
import org.http4s.Method.GET
import org.http4s.{Request, Status, Uri}

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
        val cdb =
          if (server.username.isEmpty) {
            CouchDb(server.host, server.port, server.https)
          } else {
            CouchDb(
              server.host,
              server.port,
              server.https,
              server.username,
              server.password)
          }
        // trigger some use of the server
        Try {
          cdb.client
            .req(
              Request(
                method = GET,
                uri = Uri.unsafeFromString(s"${cdb.client.baseUri}/registry/lodash"),
                headers = cdb.client.baseHeadersWithAccept),
              Status.Ok)
            .unsafePerformSync
            .as[String]
            .unsafePerformSync

          (server.host, cdb)
        }
      }
      .find(_.isSuccess)
      .get
      .get

    log.info(s"Using registry server: $addresss")
    if (addresss == null) {
      throw new RuntimeException()
    }
    address = addresss
    couchdb = couchdbb

    // Re-enabling logging
    //couchLogger.setLevel(oldLevel)
  } catch {
    case e: Throwable => e.printStackTrace()
  }

}

class CouchClientProvider() {
  val address = CouchClientProvider.address
  val couchdb = CouchClientProvider.couchdb
}
