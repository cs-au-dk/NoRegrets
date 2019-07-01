package backend.datastructures

import com.ibm.couchdb.{Res, TypeMapping}
import backend.db.CouchClientProvider

import scalaz.concurrent.Task

object GithubRepo {
  val typeMapping =
    TypeMapping(classOf[GithubRepo] -> GithubRepo.getClass.getName)

  def append(
    githubRepository: GithubRepo
  )(implicit couch: CouchClientProvider): Task[Res.DocOk] = {
    val repoId = s"${githubRepository.author}_${githubRepository.name}"
    val db = couch.couchdb.db("github_registry", GithubRepo.typeMapping)
    db.docs.create(githubRepository, repoId)
  }

}

case class GithubRepo(name: String,
                      author: String,
                      packageName: Option[String],
                      packageJson: String,
                      cloneUrl: String,
                      tags: List[String],
                      watchers: Int,
                      openIssues: Int,
                      forks: Int,
                      size: Int,
                      lastUpdate: String)
