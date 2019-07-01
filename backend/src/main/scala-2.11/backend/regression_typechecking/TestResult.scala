package backend.regression_typechecking

object TestResult {}

trait TestResult {
  var modelSizeBytes: Long = 0
}

//case class Coverage(statementCoverage: Map[String, Double] = Map(),
//                    lineCoverage: Map[String, Double] = Map(),
//                    linesTotal: Int = 0)

case class TestReport(typeRegressions: List[TypeRegression],
                      pathsTotal: Number,
                      pathsCovered: Number,
                      testTimeMillis: Number,
                      executionTimeMillis: Number,
                      coverageObject: CoverageObject,
                      coverageFiles: List[String])
    extends TestResult {}

case class TestReportError(msg: String,
                           testTimeMillis: Number,
                           timeout: Boolean = false,
                           executionTimeMillis: Number)
    extends TestResult {}

case class TypeRegression(path: AccessPath,
                          modelType: String,
                          observedType: String,
                          covariant: Boolean) {

  def equalsModuloPathId(other: TypeRegression): Boolean = {
    this.modelType.equals(other.modelType) &&
    this.observedType.equals(other.observedType) &&
    path.stripIds() == other.path.stripIds()
    //variance is implied by the path
  }
}

case class Lines(covered: Int, pct: Double, skipped: Int, total: Int)
case class Statements(covered: Int, pct: Double, skipped: Int, total: Int)
case class IstanbulCoverageObject(statements: Statements, lines: Lines)
