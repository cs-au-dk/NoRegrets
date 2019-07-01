package backend.commands

import java.nio.file.Paths
import java.time.{Duration => JavaDuration, _}

import com.typesafe.config.ConfigFactory
import backend.common.CustomGitHubClient
import backend._
import backend.commands.Common._
import backend.datastructures._
import backend.utils.DiskCaching.CacheBehaviour
import backend.utils._
import org.eclipse.egit.github.core._
import org.eclipse.egit.github.core.service._
import scopt.OptionDef

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.language.implicitConversions
import scala.util._

object CrawlBreakingChanges {

  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)

  val conf = ConfigFactory.load()
  val githubToken = Globals.githubToken
  println(s"Using github token $githubToken")

  def handleCrawlBreakingChanges(): Unit = {

    val githubUrl =
      DiskCaching.cache(
        {
          val registry = new RegistryReader().loadNpmRegistry()

          registry.values
            .filter(_.versions.nonEmpty)
            .flatMap(_.versions.last._2.repository)
            .map(Utils.sshtohttps)
            .filter(_.contains("github"))
        },
        keys = List("all-repos", "github", "urls"),
        regenerationPolicy = CacheBehaviour.USE_IF_EXISTS_GENERATE_OTHERWISE)

    log.info(s"There are ${githubUrl.size} github urls")

    val ownerNamePairs = githubUrl.flatMap { https =>
      val patterns = """https://.*\.com/([^/]+)/([^/]+)\.git/?""".r
      https match {
        case patterns(owner, name) =>
          Some((owner, name))
        case _ =>
          None
      }
    }
    log.info(s"Converted to ${ownerNamePairs.size} owner/name pairs")

    val githubClient = new CustomGitHubClient()
    githubClient.setOAuth2Token(githubToken)
    val repoServ = new RepositoryService(githubClient)
    val userServ = new UserService(githubClient)
    val starServ = new StargazerService(githubClient)
    val dataServ = new ContentsService(githubClient)
    val issueServ = new IssueService(githubClient)

    val issues = ownerNamePairs.toStream
      .flatMap(p => Try(repoServ.getRepository(p._1, p._2)).toOption)
      .flatMap { repo =>
        log.info(s"Getting issues for ${repo.getHtmlUrl}")
        val res = Try(issueServ.searchIssues(repo, "*", "'breaking change'"))
        res match {
          case Success(retIssues) => log.info(s"There are ${retIssues.size()}")
          case Failure(err)       => log.error(err.toString)
        }
        res.toOption
      }
      .map(x => {
        if (githubClient.getRemainingRequests < 500) {
          val sleepTime =
            JavaDuration.between(Instant.now(), githubClient.resetDate).toMillis
          println(s"""
                     |Reached 90% of what Github allows,
                     |  limit: ${githubClient.getRequestLimit}
                     |  remaining: ${githubClient.getRemainingRequests}
                     |taking a pause of ${sleepTime}ms before retrying...
                     |
           |Reset expected: ${githubClient.resetDate}
         """.stripMargin)

          Thread.sleep(sleepTime + 60000)
        }
        x
      })

    var interestingIssues = mutable.MutableList[Issue]()

    issues.foreach(issues => {
      interestingIssues ++= issues.map(Issue(_)).toList
      SerializerFormats.defaultSerializer.serialize(
        interestingIssues,
        Paths.get("out-noregrets/interesting-issues.json"),
        pretty = true)
    })

  }

}

object Issue {
  def apply(searchIssue: SearchIssue): Issue = {
    Issue(searchIssue.getHtmlUrl, searchIssue.getTitle, searchIssue.getBody)
  }
}
case class Issue(url: String, title: String, body: String)

object CrawlBreakingChangesOptions {
  def make(parser: scopt.OptionParser[Config]): Seq[OptionDef[_, Common.Config]] = {

    implicit def asDep(cmd: Option[Common.CommandOptions]): CrawlBreakingChangesOptions =
      cmd.get.asInstanceOf[CrawlBreakingChangesOptions]

    Seq()
  }
}
case class CrawlBreakingChangesOptions() extends CommandOptions
