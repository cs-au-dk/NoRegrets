package backend.commands

import backend.commands.Common.CommandOptions
import backend.datastructures._
import backend.db.CouchClientProvider
import backend.utils._

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
