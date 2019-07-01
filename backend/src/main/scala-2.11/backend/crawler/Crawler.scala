package crawler

import java.time.{Duration => JavaDuration, _}

import com.typesafe.config.ConfigFactory
import backend.common.CustomGitHubClient
import backend.datastructures.GithubRepo
import backend.db.CouchClientProvider
import backend.utils.Utils
import org.eclipse.egit.github.core._
import org.eclipse.egit.github.core.client.PageIterator
import org.eclipse.egit.github.core.service._
import org.json4s._
import org.json4s.native.JsonMethods._

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.{Failure, Try}

object Crawler extends App {
  val CORE_SIZE = 1
  val PARLEVEL = 3 // keep it low to avoid triggering the anti abuse mechanism detection
  val PAGES = 1
  val DAYS_FROM_LAST_UPDATE: Long = 365

  val conf = ConfigFactory.load()
  val githubToken = conf.getString("githubToken")
  println(s"Using github token $githubToken")

  val client = new CustomGitHubClient()
  implicit val db = new CouchClientProvider()

  client.setOAuth2Token(githubToken)
  val repoServ = new RepositoryService(client)
  val userServ = new UserService(client)
  val starServ = new StargazerService(client)
  val dataServ = new ContentsService(client)

  // Finding a core set of javascript project to begin the crawling
  println(s"Finding a core set")
  val coreSet: mutable.Set[SearchRepository] = mutable.Set()
  var missingSize = CORE_SIZE
  var page = 0
  while (missingSize > 0) {
    val jsRepos = repoServ.searchRepositories(
      Map("fork" -> "false", "sort" -> "stars", "language" -> "javascript"),
      page)
    page += 1
    coreSet ++= jsRepos
    missingSize -= jsRepos.size()
    println(s"Missing $missingSize to complete the core set or repos")
  }
  println(s"Found ${coreSet.size} repos for the core set")

  val saved = db.couchdb
    .db("github_registry", GithubRepo.typeMapping)
    .docs
    .getMany
    .build
    .query
    .unsafePerformSync
    .rows
    .map(_.id)
    .toSet

  val coreRepositories = coreSet.par.map { repoServ.getRepository(_) }.seq

  val withoutSaved =
    coreRepositories.filter(x => !saved.contains(s"${x.getOwner.getLogin}_${x.getName}"))

  crawl(withoutSaved.toSet)

  def crawl(projectsToVisit: Set[Repository]): Unit = {
    println(s"Crawl start now")

    val visitedRepos: mutable.Set[String] = mutable.Set()
    val visitedUsers: mutable.Set[String] = mutable.Set()

    val stuffToVisit: mutable.SortedSet[Either[User, Repository]] =
      mutable.SortedSet.empty(CrawlOrdering)
    val repos = projectsToVisit.map(Right(_)).seq
    println(s"Adding ${repos.size} elements to the set of things to visit")
    stuffToVisit ++= repos

    while (stuffToVisit.nonEmpty) {

      println(s"""
           |Visit status
           |  projects visited: ${visitedRepos.size}
           |  users visited: ${visitedUsers.size}
           |  things to visit: ${stuffToVisit.size}
           |""".stripMargin)

      val toVisitAtThisRound = stuffToVisit.subsets(PARLEVEL).next()

      stuffToVisit --= toVisitAtThisRound

      visitedRepos ++= toVisitAtThisRound.collect { case Right(x) => x.generateId() }
      visitedUsers ++= toVisitAtThisRound.collect { case Left(x) => x.getLogin }

      val outcomes = toVisitAtThisRound.toList.par.map {
        case Left(user) =>
          visitUser(user)
        case Right(repo) =>
          visitRepo(repo)
      }

      outcomes.foreach {
        case Failure(x) =>
          println(s"Fail $x")
          x.printStackTrace()
        case _ =>
      }

      val okOutcome = outcomes.flatMap(_.toOption)

      // Saving onto the database
      okOutcome.foreach { visitOutcome =>
        visitOutcome.pickedRepos.foreach { repo: GithubRepo =>
          val repoUid = s"${repo.author}/${repo.name}"
          println(s"Saving new repo $repoUid")
          saveRepo(repo)
        }
      }

      val jointUpdate = okOutcome.foldLeft(VisitOutcome()) { (acc, curr) =>
        VisitOutcome(acc.newUsers ++ curr.newUsers, acc.newRepos ++ curr.newRepos)
      }

      stuffToVisit ++= jointUpdate.newRepos
        .filter(x => !visitedRepos.contains(x.generateId()))
        .map(Right(_))
      stuffToVisit ++= jointUpdate.newUsers
        .filter(x => !visitedUsers.contains(x.getLogin))
        .map(Left(_))

      // Github allows 5000 requests / hour
      // we try hard to stay within 90% of them (just to be on the safe side)
      if (client.getRemainingRequests < 500) {
        val sleepTime = JavaDuration.between(Instant.now(), client.resetDate).toMillis
        println(s"""
           |Reached 90% of what Github allows,
           |  limit: ${client.getRequestLimit}
           |  remaining: ${client.getRemainingRequests}
           |taking a pause of ${sleepTime}ms before retrying...
           |
           |Reset expected: ${client.resetDate}
         """.stripMargin)

        Thread.sleep(sleepTime + 60000)

      }
    }

  }

  def mkSet[T](i: PageIterator[T]) = {
    i.iterator().toList.foldLeft(Set[T]()) { case (a, c) => a ++ c }
  }

  def visitUser(user: User): Try[VisitOutcome] = {
    Try {
      println(s"Processing user ${user.getLogin}")
      val owner = user.getLogin
      val ownerPagedRepos = repoServ.getRepositories(owner)
      //println(s"Got repositories of ${owner}")
      val followers = userServ.getFollowers(owner)
      //println(s"Got followers of ${owner}")
      val following = userServ.getFollowing(owner)
      //println(s"Got following of ${owner}")
      val starred = starServ.getStarred(owner)
      println(s"Processed user ${user.getLogin}")
      VisitOutcome(Set() ++ followers ++ following, Set() ++ starred ++ ownerPagedRepos)
    }
  }

  def visitRepo(repo: Repository): Try[VisitOutcome] = {
    // page* has a more deterministic behaviour for number of request made,
    // however, we would damage our exploration capabilities
    Try {
      println(s"Processing repo ${repo.getOwner.getLogin}/${repo.getName}")
      val owner = repo.getOwner
      val ownerPagedRepos = repoServ.getRepositories(owner.getLogin) // mkSet(repoServ.pageRepositories(owner.getLogin, PAGES))
      //println(s"Got repos of the owner of ${repo.getName}")
      val starrers = starServ.getStargazers(repo) // mkSet(starServ.pageStargazers(repo, PAGES))
      //println(s"Got starrers of the repo ${repo.getName}")
      println(s"Processed repo ${repo.getOwner.getLogin}/${repo.getName}")
      VisitOutcome(Set(owner) ++ starrers, Set() ++ ownerPagedRepos, pickRepo(repo))
    }
  }

  def pickRepo(repo: Repository): Option[GithubRepo] = {
    val attempt = Try {
      val cloneUrl = repo.getCloneUrl
      // It should contain a package.json at the very least
      val jsonData = dataServ.getContents(repo, "package.json").head
      if (jsonData.getEncoding != "base64")
        sys.error(s"Unexpected non-base64 encoding for repo ${repo.getName}")
      val packageJson = Utils.fromGithubBase64(jsonData.getContent)
      val packageName = parse(packageJson)
        .asInstanceOf[JObject]
        .obj
        .find(_._1 == "name")
        .map(_._2.toString)

      // and have issues
      if (!repo.isHasIssues)
        sys.error(s"Repo ${repo.getName} has no issues")

      val tags = repoServ.getTags(repo).map(_.getName).toList

      val watchers = repo.getWatchers
      val openIssues = repo.getOpenIssues
      val forks = repo.getForks
      val size = repo.getSize
      val lastUpdate = repo.getUpdatedAt

      // and be updated within DAYS_FROM_LAST_UPDATE from now
      if (JavaDuration
            .between(lastUpdate.toInstant, Instant.now())
            .compareTo(JavaDuration.ofDays(DAYS_FROM_LAST_UPDATE)) >= 0)
        sys.error(s"Outdated repository: last updated: $lastUpdate")

      //FIXME: Use github to collect the following stats
      // More than one contributors
      // Open issues from the owner
      // Open issues not from the owner
      // Closed issues from the owner
      // Closed issues not from the owner
      // With al least two stars

      GithubRepo(
        name = repo.getName,
        author = repo.getOwner.getLogin,
        packageName = packageName,
        packageJson = packageJson,
        cloneUrl = cloneUrl,
        tags = tags,
        watchers = watchers,
        openIssues = openIssues,
        forks = forks,
        size = size,
        lastUpdate = lastUpdate.toString)
    }
    attempt match {
      case Failure(x) =>
        println(
          s"Attempt to pick repo ${repo.getOwner.getLogin}/${repo.getName} failed\n$x")
      case _ =>
    }
    attempt.toOption
  }

  def saveRepo(repo: GithubRepo): Try[Unit] = {
    Try {
      GithubRepo
        .append(repo)
        .retry((1 to 2).map(_ => 5.seconds), _ => true)
        .map { ok =>
          println(s"${repo.author}/${repo.name} saved, success: ${ok.ok}")
        }
        .unsafePerformSyncFor(10.minutes)
    }
  }

  case class VisitOutcome(newUsers: Set[User] = Set(),
                          newRepos: Set[Repository] = Set(),
                          pickedRepos: Option[GithubRepo] = None)

  object CrawlOrdering extends Ordering[Either[User, Repository]] {
    def compare(a: Either[User, Repository], b: Either[User, Repository]) =
      (a, b) match {
        case (Left(_), Right(_)) => +1
        case (Right(_), Left(_)) => -1
        case (Right(x), Right(y)) => x.generateId().compareTo(y.generateId())
        case (Left(x), Left(y)) => x.getLogin.compareTo(y.getLogin)
      }
  }
}
