package backend

import java.lang.ref.SoftReference
import java.nio.file._

import com.ibm.couchdb.TypeMapping
import backend.RegistryReader._
import backend.datastructures.{Status => _, _}
import backend.db.CouchClientProvider
import backend.utils.Log.Level
import backend.utils._
import org.http4s.Method.GET
import org.http4s._
import upickle._
import scala.collection.JavaConverters._

object RegistryReader {
  private val log = Logger("RegistryReader", Level.Info)

  // Open and navigate http://localhost:5984/_utils/ to explore the database
  private lazy val provider = new CouchClientProvider()
  private lazy val db = provider.couchdb.db("registry", TypeMapping.empty)
  private var loadedRegistry: SoftReference[NpmRegistry.EagerNpmRegistry] =
    new SoftReference(null)
  private val registryDir = Paths.get("out/registry/")
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
      if (loadedRegistry.get() != null) {
        loadedRegistry.get()
      } else {
        if (Files.exists(registryDir)) {
          log.info(s"Loading npm registry from directory: ${registryDir}...")
          val registry = deserializeRegistry()

          log.info(s"Done, registry contains ${registry.size} elements")
          loadedRegistry = new SoftReference(registry)
          registry
        } else {
          log.info(s"Performing document request")

          val docsIds =
            RegistryReader.db.docs.getMany.build.query.unsafePerformSync.rows

          log.info(s"Found ${docsIds.size} documents")

          //We run into memory issues when trying to generate one big map to hold the full registry
          //So, now we try to partially serialize the documents as they are gathered in sets of 1000
          docsIds
            .map(_.id)
            .toList
            .grouped(1000)
            .zipWithIndex
            .toList
            .par
            .map { case (ids, idx) ⇒ (ids.flatMap(getDocument(_)).toMap, idx) }
            .map { case (pkgs, id) ⇒ serializeRegistryPart(pkgs, id) }
            .toList //Force the lazy iterator to evaluate

          //val registry = docsIds.map(_.id).par.flatMap(getDocument).seq.toMap

          //log.info(s"Converted ${registry.size} objects, now writing to disk")

          //serializeRegistry(registry)

          log.info("   Done")

          //This is a bit stupid since we have just serialized the full registry
          //but we need to do this to avoid running in to memory issues.
          val registry = deserializeRegistry()
          loadedRegistry = new SoftReference(registry) //new SoftReference(registry)
          registry
        }
      }
    }
  }

  private def serializeRegistryPart(pkgs: Map[String, NpmPackageDescription],
                                    idx: Int) = {
    if (!Files.exists(registryDir)) {
      Files.createDirectory(registryDir)
    }

    FastJavaSerializer.serialize(pkgs, registryDir.resolve(s"registry-${idx}.bin"))
  }

  //@deprecated
  //private def serializeRegistry(reg: NpmRegistry.EagerNpmRegistry): Unit = {
  //  if (!Files.exists(registryDir)) {
  //    Files.createDirectory(registryDir)
  //  }

  //  val pkgGrps = reg.toList.grouped(1000)
  //  pkgGrps.zipWithIndex.foreach {
  //    case (grp, idx) ⇒ {
  //      FastJavaSerializer.serialize(grp, registryDir.resolve(s"registry-${idx}.bin"))
  //    }
  //  }
  //}

  private def deserializeRegistry(): NpmRegistry.EagerNpmRegistry = {
    val regFiles = Files
      .list(registryDir)
      .iterator()
      .asScala
      .filter(_.getFileName.toString.startsWith("registry"))
    regFiles
      .flatMap(regFile ⇒ {
        FastJavaSerializer.deserialize[Map[String, NpmPackageDescription]](regFile)
      })
      .toMap
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

    //Apparently the users listed in the users field are the ones who starred the package
    val stars = objMap.get("users").map(_.asInstanceOf[Js.Obj].value.length)

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

    NpmPackageDescription(id, `dist-tags`, name, versions.map(identity), stars)
  }

  private def getDocument(doc: String): Option[(String, NpmPackageDescription)] = {
    log.info(s"Getting ${doc}")

    // Doing the request manually,
    val uri = Uri.unsafeFromString(s"${provider.couchdb.client.baseUri}/registry/${doc}")

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
    var retry = 0
    while (!done) {
      try {
        if (retry > 2) {
          done = true
        } else {
          resp = retrieve()
          done = true
        }
      } catch {
        case x: Throwable =>
          retry += 1
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
