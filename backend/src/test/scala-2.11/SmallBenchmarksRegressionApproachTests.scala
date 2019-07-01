import backend.commands._
import backend.commands.benchmarking.SmallBenchmarkCollection._
import backend.commands.benchmarking._
import org.scalatest._
import org.scalatest.concurrent.Timeouts._
import org.scalatest.prop.TableDrivenPropertyChecks.{forAll, _}

import scala.concurrent.duration._

class SmallBenchmarksRegressionApproachTests extends FlatSpec with Matchers {
  private val flatBenchmarks =
    SmallBenchmarkCollection.flatBenchmarks

  val table = Table(("a", "b", "c"), flatBenchmarks: _*)

  forAll(table) {
    (a: LibraryEvolutionBenchmark, b: Evolution, c: EvolutionInfoBenchmark) =>
      val name =
        s"${a.library} with clients ${c.clients.mkString(",")} in the evolution ${b.pre} -> ${b.post}"

      name should s"$name should have status ${c.status}\n    expected error: ${c.expectedError}" in {
        failAfter(5.minutes) {
          RegressionTypeLearner(
            RegressionTypeLearnerOptions(
              libraryToCheckName = a.library,
              libraryVersions = List(b.pre, b.post),
              clients = Left(c.clients),
              learningCommandCachePolicy = CommandCachePolicy.REGENERATE_DATA))
            .handleRegressionTypeLearner()
        }
      }
  }
}
