package distilling.server.db

import com.ibm.couchdb.{CouchDesign, CouchView}
import distilling.server.datastructures.TestOutcome

object ViewCreationsTestsOutcomeRan extends App {

  val provider = new CouchClientProvider()
  val testsOutcomeRanDB =
    provider.couchdb.db("tests_outcome_ran", TestOutcome.typeMapping)

  val solutionSizeId = "solution-size"
  val solutionsSize = CouchView(map = """
      |function(doc) {
      |   emit(null, doc.doc.solutions.length);
      |}
    """.stripMargin)

  val interestingTestsId = "interesting-tests"
  val interesting = CouchView(map = """
      |function(doc) {
      |  try {
      |  var goods = 0;
      |  var bads = 0;
      |  var i = 0;
      |    for(var idx in doc.doc.solutions) {
      |      if(doc.doc.solutions[idx].testOutcome == 0) {
      |        goods++;
      |      }
      |      else {
      |        bads++;
      |      }
      |    }
      |
      |  if(goods > 0 && bads > 0) {
      |    emit(null, {"goods": goods, "bads": bads});
      |  }
      |  }
      |  catch(err){}
      |}
      """.stripMargin)

  val uniquePackagesId = "unique-packages"
  val uniquePackages = CouchView(map = """
      |function(doc) {
      |    try {
      |        if (Object.names === undefined) {
      |            Object.names = {};
      |        }
      |        var name = doc.doc.packageName;
      |        if (Object.names[name] === undefined) {
      |            Object.names[name] = true;
      |            emit(null, doc.doc);
      |        }
      |    }
      |    catch(err){}
      |}
      """.stripMargin)

  val totalUpdatesId = "totalUpdates"
  val totalUpdates = CouchView(
    map = s"""
    |function(doc) {
    |  try {
    |      if (Object.total === undefined) {
    |          Object.total = 0;
    |      }
    |
    |      var total = 0;
    |      total += doc.doc.solutions.length - 1;
    |      Object.total += total;
    |      emit(null, {"updates": total});
    |     }
    |  catch(err){}
    |}
    """.stripMargin,
    reduce = """
    |function(keys, values, rereduce) {
    |  var total = values.reduce(function(acc, val) {
    |          return {"updates": acc["updates"] + val["updates"]};
    |  });
    |  return total;
    |}
    """.stripMargin)

  val onlyFailingTestsId = "only-failing-tests"
  val onlyFailingTests = CouchView(
    map = """
      |function(doc) {
      |  try {
      |  var goods = 0;
      |  var bads = 0;
      |  var i = 0;
      |    for(var idx in doc.doc.solutions) {
      |      if(doc.doc.solutions[idx].testOutcome == 0) {
      |        goods++;
      |      }
      |      else {
      |        bads++;
      |      }
      |    }
      |
      |  if(goods == 0 && bads > 0) {
      |    emit(null, {"goods": goods, "bads": bads});
      |  }
      |  }
      |  catch(err){}
      |}
      """.stripMargin)

  val someSucceedingTestsId = "some-succeeding-tests"
  val someSucceedingTests = CouchView(
    map = """
     |function(doc) {
     |  try {
     |  var goods = 0;
     |  var bads = 0;
     |  var i = 0;
     |    for(var idx in doc.doc.solutions) {
     |      if(doc.doc.solutions[idx].testOutcome == 0) {
     |        goods++;
     |      }
     |      else {
     |        bads++;
     |      }
     |    }
     |
     |  if(goods > 0) {
     |    emit(null, {"goods": goods, "bads": bads});
     |  }
     |  }
     |  catch(err){}
     |}
   """.stripMargin)

  val designSolutionSize =
    CouchDesign(name = solutionSizeId, views = Map(solutionSizeId -> solutionsSize))
  val designInterestingTests =
    CouchDesign(name = interestingTestsId, views = Map(interestingTestsId -> interesting))
  val designUniquePackages =
    CouchDesign(name = uniquePackagesId, views = Map(uniquePackagesId -> uniquePackages))
  val designTotalUpdates =
    CouchDesign(name = totalUpdatesId, views = Map(totalUpdatesId -> totalUpdates))
  val designOnlyFailingTests = CouchDesign(
    name = onlyFailingTestsId,
    views = Map(onlyFailingTestsId -> onlyFailingTests))
  val designSomeSucceedingTests = CouchDesign(
    name = someSucceedingTestsId,
    views = Map(someSucceedingTestsId -> someSucceedingTests))

  try { testsOutcomeRanDB.design.deleteByName(solutionSizeId).unsafePerformSync } catch {
    case _: Throwable =>
  }
  testsOutcomeRanDB.design.create(designSolutionSize).unsafePerformSync

  try { testsOutcomeRanDB.design.deleteByName(interestingTestsId).unsafePerformSync } catch {
    case _: Throwable =>
  }
  testsOutcomeRanDB.design.create(designInterestingTests).unsafePerformSync

  try { testsOutcomeRanDB.design.deleteByName(uniquePackagesId).unsafePerformSync } catch {
    case _: Throwable =>
  }
  testsOutcomeRanDB.design.create(designUniquePackages).unsafePerformSync

  try { testsOutcomeRanDB.design.deleteByName(totalUpdatesId).unsafePerformSync } catch {
    case _: Throwable =>
  }
  testsOutcomeRanDB.design.create(designTotalUpdates).unsafePerformSync

  try { testsOutcomeRanDB.design.deleteByName(onlyFailingTestsId).unsafePerformSync } catch {
    case _: Throwable =>
  }
  testsOutcomeRanDB.design.create(designOnlyFailingTests).unsafePerformSync

  try { testsOutcomeRanDB.design.deleteByName(someSucceedingTestsId).unsafePerformSync } catch {
    case _: Throwable =>
  }
  testsOutcomeRanDB.design.create(designSomeSucceedingTests).unsafePerformSync

  System.exit(0)
}
