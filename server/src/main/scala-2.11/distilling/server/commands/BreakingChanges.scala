package distilling.server.commands

import distilling.server.commands.Common.CommandOptions
import distilling.server.datastructures._
import distilling.server.db.CouchClientProvider
import distilling.server.utils._

object BreakingChanges {
  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)

  def handleBreakingChangesCommand(
    options: BreakingChangesOptions): List[PackageAtVersion] = {

    val provider = new CouchClientProvider()

    val db = provider.couchdb.db("tests_outcome_ran", TestOutcome.typeMapping)

    val interesting = db.query
      .view[String, Map[String, Int]]("interesting-tests", "interesting-tests")
      .get

    val allInteresting = interesting.build.query.unsafePerformSync.rows.map(_.id)

    val allInterestingDocs =
      allInteresting.map(db.docs.get[TestOutcome](_).unsafePerformSync.doc)

    allInterestingDocs
      .map(to => PackageAtVersion(to.packageName, to.packageVersion))
      .toList
  }

  case class BreakingChangesOptions() extends CommandOptions
}
