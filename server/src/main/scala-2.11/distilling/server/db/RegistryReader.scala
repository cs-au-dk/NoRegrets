package distilling.server

import java.lang.ref.SoftReference
import java.nio.file._

import com.ibm.couchdb.TypeMapping
import distilling.server.RegistryReader._
import distilling.server.datastructures.{Status => _, _}
import distilling.server.db.CouchClientProvider
import distilling.server.utils.Log.Level
import distilling.server.utils._
import org.http4s.Method.GET
import org.http4s._
import upickle._

object RegistryReader {
  private val log = Logger("RegistryReader", Level.Info)

  // Open and navigate http://localhost:5984/_utils/ to explore the database
  private lazy val provider = new CouchClientProvider()
  private lazy val db = provider.couchdb.db("registry", TypeMapping.empty)
  private var loadedRegistry: SoftReference[NpmRegistry.EagerNpmRegistry] =
    new SoftReference(null)
}

/**
  * This class reads an NpmRegistry.NpmRegistry from remote or from disk
  *
  * Open and navigate http://localhost:5984/_utils/ to explore the database
  */
class RegistryReader() {

  def allNpmPackages(
    limit: Option[Int] = None): Stream[Option[(String, NpmPackageDescription)]] = {

    log.info("Connected to database registry")

    val docsIds = limit match {
      case Some(x) =>
        RegistryReader.db.docs.getMany.limit(x).build.query.unsafePerformSync.rows
      case None => RegistryReader.db.docs.getMany.build.query.unsafePerformSync.rows
    }

    log.info(s"Found ${docsIds.size} documents")

    docsIds.map(_.id).toStream.map { projId =>
      getDocument(projId)
    }
  }

  def loadLazyNpmRegistry(): NpmRegistry.LazyNpmRegistry = { (s: String) =>
    getDocument(s).get._2
  }

  def loadNpmRegistry(): NpmRegistry.EagerNpmRegistry = {
    loadedRegistry.synchronized {
      val file = Paths.get("out/npm-json.bin")
      if (loadedRegistry.get() != null) {
        loadedRegistry.get()
      } else {
        if (Files.exists(file)) {
          log.info(s"Loading npm registry from file: ${file}...")
          val registry: NpmRegistry.EagerNpmRegistry =
            FastJavaSerializer.deserialize[NpmRegistry.EagerNpmRegistry](file)

          log.info(s"Done, registry contains ${registry.size} elements")
          loadedRegistry = new SoftReference(registry)
          registry
        } else {
          log.info(s"Performing document request")

          val docsIds =
            RegistryReader.db.docs.getMany.build.query.unsafePerformSync.rows

          log.info(s"Found ${docsIds.size} documents")

          val registry = docsIds.map(_.id).par.flatMap(getDocument).seq.toMap

          log.info(s"Converted ${registry.size} objects, now writing to disk")

          FastJavaSerializer.serialize(registry, file)

          log.info("   Done")

          loadedRegistry = new SoftReference(registry)
          registry

        }
      }
    }
  }

  private def readDoc(resp: String): NpmPackageDescription = {

    val repoJs = json.read(resp)
    val obj = repoJs.asInstanceOf[Js.Obj]
    val objMap = obj.value.toMap

    val name = objMap("name").asInstanceOf[Js.Str].value
    val id = objMap("_id").asInstanceOf[Js.Str].value
    val `dist-tags` = objMap("dist-tags").asInstanceOf[Js.Obj].value.toMap.map {
      case (k, v) => k -> v.value.asInstanceOf[String]
    }

    val versionsMap =
      objMap("versions").asInstanceOf[Js.Obj].value.toMap

    val time = objMap.get("time") match {
      case None => None
      case Some(timeObj: Js.Obj) =>
        val realTimeObjMap = timeObj.value.toMap.map {
          case (v, date) => v -> date.asInstanceOf[Js.Str].value
        }
        Some(realTimeObjMap)
      case Some(_) => None
    }

    val versions = versionsMap.map {
      case (versionsNumber, v) =>
        val vDesc = v.asInstanceOf[Js.Obj].value.toMap

        val main = if (vDesc.contains("main")) {
          Some(vDesc("main"))
        } else {
          None
        }

        val releaseDate = time.flatMap(m => m.get(versionsNumber))

        val scripts = if (vDesc.contains("scripts")) {
          Some(vDesc("scripts"))
        } else {
          None
        }

        val deps = vDesc.get("dependencies") match {
          case Some(depObj: Js.Obj) =>
            val deps = depObj.value.filter { case (_, v) => v.isInstanceOf[Js.Str] }.toMap
            Some(deps.mapValues(v => v.asInstanceOf[Js.Str].value).map(identity))
          case _ =>
            None
        }

        val repo = if (vDesc.contains("repository")) {
          vDesc("repository") match {
            case obj: Js.Obj =>
              val objMap = obj.value.toMap
              if (objMap.contains("url"))
                Some(objMap("url").asInstanceOf[Js.Str].value)
              else
                None
            case l: Js.Arr =>
              log.warn(s"Found $l as repository")
              None
            case _ => None
          }
        } else {
          None
        }

        versionsNumber -> VersionDesc(main, scripts, deps, repo, releaseDate)
    }

    NpmPackageDescription(id, `dist-tags`, name, versions.map(identity))
  }

  private def getDocument(doc: String): Option[(String, NpmPackageDescription)] = {
    log.info(s"Getting ${doc}")

    // Doing the request manually,
    val uri = Uri
      .fromString(s"http://${provider.address}:5984/registry/${doc}")
      .getOrElse(throw new RuntimeException)

    def retrieve() =
      provider.couchdb.client
        .req(
          Request(
            method = GET,
            uri = uri,
            headers = provider.couchdb.client.baseHeadersWithAccept),
          Status.Ok)
        .unsafePerformSync
        .as[String]
        .unsafePerformSync

    var done = false
    var resp: String = null
    while (!done) {
      try {
        resp = retrieve()
        done = true
      } catch {
        case x: Throwable =>
          log.warn(
            s"Error while retrieving document: ${x}, retrying\n"
              + x.getStackTrace.mkString("\n"))
          Thread.sleep(2000)
      }
    }

    log.info(s"Done getting ${doc}") // ($completed/${docsIds.size}), ongoing: ${ongoing.size}")

    val ret = try {
      val desc = readDoc(resp)
      log.info(s"Converted ${doc}")
      Some(desc._id -> desc)
    } catch {
      case x: Throwable =>
        log.error(s"Error converting $resp: $x")
        None
    }

    ret
  }
}
