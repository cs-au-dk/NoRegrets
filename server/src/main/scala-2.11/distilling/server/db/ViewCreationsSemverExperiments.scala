package distilling.server.db

import com.ibm.couchdb.{CouchDesign, CouchView}
import distilling.server.datastructures.TestOutcome

object ViewCreationsSemverExperiments extends App {

  val provider = new CouchClientProvider()
  val registryDB = provider.couchdb.db("semver-experiments", TestOutcome.typeMapping)

  hasConstraintDegreeQuery("UNDER", "has-under-constraint")
  hasConstraintDegreeQuery("OVER", "has-over-constraint")
  hasConstraintDegreeQuery("IDEAL", "has-ideal-constraint")

  averageQuery("UNDER", "average-under-constraint")
  averageQuery("OVER", "average-over-constraint")
  averageQuery("IDEAL", "average-ideal-constraint")

  /**
    * Computes the number of packages that have at least one dependency with the specified constraintDegree
    * @param constraintDegree
    * @param queryName
    */
  def hasConstraintDegreeQuery(constraintDegree: String, queryName: String) = {
    val query = CouchView(reduce = "_count", map = s"""
      |function(doc) {
      |  var dependencies = doc.doc.dependencies;
      |  for (var key in dependencies) {
      |     if (dependencies.hasOwnProperty(key)) {
      |         var constraintDegree = dependencies[key]["semVerConstraintDegree"];
      |         if (constraintDegree === ${constraintDegree}) {
      |           emit (doc.id, 1);
      |           break;
      |         }
      |      }
      |    }
      |}
    """.stripMargin)

    val design = CouchDesign(name = queryName, views = Map(queryName -> query))

    try { registryDB.design.deleteByName(queryName).unsafePerformSync } catch {
      case _: Throwable =>
    }
    registryDB.design.create(design).unsafePerformSync
  }

  /**
    * Computes the fraction of dependencies that have the specified constraintDegree
    * @param constraintDegree
    * @param queryName
    * @return
    */
  def averageQuery(constraintDegree: String, queryName: String) = {
    val query = CouchView(
      map = s"""
    |function(doc){
    |  var dependencies = doc.doc.dependencies;
    |  var dependenciesCount = 0;
    |  var constraintDegreeCount = 0;
    |  for (var key in dependencies) {
    |     if (dependencies.hasOwnProperty(key)) {
    |         dependenciesCount += 1;
    |         var constraintDegree = dependencies[key]["semVerConstraintDegree"];
    |         if (constraintDegree === ${constraintDegree}) {
    |             constraintDegreeCount += 1;
    |         }
    |      }
    |    }
    |  if (dependenciesCount != 0) {
    |     emit(null, [dependenciesCount, constraintDegreeCount]);
    |  }
    |}
    """.stripMargin,
      reduce = """
    |function(keys, values, rereduce) {
    |   //if (!rereduce){
    |      var length = values.length;
    |      var accum = values.reduce(function(acc, val) {
    |         return [acc[0] + val[0], acc[1] + val[1]];
    |      });
    |      if (accum[0] != 0) {
    |         return accum[1] / accum[0];
    |      }
    |      return 0;
    |   // return [sum(values) / length, length];
    |   // else {
    |   //   var length = sum(values.map(function(v){return v[1]}));
    |   //   var avg = sum(values.map(function(v){
    |   //     return v[0] * (v[1] / length);
    |   //   }));
    |   //   return [avg, length];
    |   //}
    |}
    """.stripMargin
    )

    val design = CouchDesign(name = queryName, views = Map(queryName -> query))

    try { registryDB.design.deleteByName(queryName).unsafePerformSync } catch {
      case _: Throwable =>
    }
    registryDB.design.create(design).unsafePerformSync
  }

  System.exit(0)
}
