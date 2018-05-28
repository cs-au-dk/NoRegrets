package distilling.server.db

import com.ibm.couchdb.{CouchDesign, CouchView}
import distilling.server.datastructures.TestOutcome

object ViewCreationsTestsOutcomeProblematic extends App {

  val provider = new CouchClientProvider()
  val testsOutcomeProblematicDB =
    provider.couchdb.db("tests_outcome_problematic", TestOutcome.typeMapping)

  val missingTagProblematicTestsId = "missing-tags"
  val missingTagProblematicTests = CouchView(
    map = """
      | function(doc) {
      |     if (doc.doc.msg[0].indexOf("$Notag") != -1) {
      |         emit(null, doc.doc.packageName);
      |     }
      | }
      """.stripMargin
  )

  val designMissingTagProblematicTests =
    CouchDesign(
      name = missingTagProblematicTestsId,
      views = Map(missingTagProblematicTestsId -> missingTagProblematicTests)
    )

  try {
    testsOutcomeProblematicDB.design
      .deleteByName(missingTagProblematicTestsId)
      .unsafePerformSync
  } catch { case _: Throwable => }
  testsOutcomeProblematicDB.design
    .create(designMissingTagProblematicTests)
    .unsafePerformSync

  System.exit(0)
}
