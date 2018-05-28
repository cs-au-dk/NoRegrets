package distilling.server.datastructures

import java.nio.file._

import distilling.server.commands.ApiTrace.ApiTraceOptions
import distilling.server.commands.BreakingChanges.BreakingChangesOptions
import distilling.server.commands.Clone.CloneOptions
import distilling.server.commands.Common._
import distilling.server.commands.Dependents.DependentOptions
import distilling.server.commands.RunTests.RunTestsOptions
import distilling.server.commands.SourceDiff.SourceDiffOptions
import distilling.server.commands.Successfuls.SuccessfulOptions
import distilling.server.commands._
import distilling.server.regression_typechecking._
import distilling.server.utils._
import org.json4s.{
  CustomSerializer,
  DefaultFormats,
  Formats,
  JObject,
  JString,
  ShortTypeHints
}

import scala.collection.immutable.List

object SerializerFormats {
  val commonSerializationFormats: Formats = DefaultFormats.withHints(
    ShortTypeHints(List(
      classOf[DependentOptions],
      classOf[SuccessfulOptions],
      classOf[LearningFailure],
      classOf[Learned],
      classOf[ArgLabel],
      classOf[ApplicationLabel],
      classOf[PropertyLabel],
      classOf[RequireLabel],
      classOf[AccessLabel],
      classOf[TypeDiff],
      NoObservationInBase.getClass,
      NoObservationInPost.getClass,
      classOf[CommandOptions],
      classOf[RegressionTypeLearnerOptions],
      classOf[CliJobLoaderOptions],
      classOf[ApiTraceOptions],
      classOf[CrawlBreakingChangesOptions],
      classOf[CloneOptions],
      classOf[DependentOptions],
      classOf[SuccessfulOptions],
      classOf[RunTestsOptions],
      classOf[BreakingChangesOptions],
      classOf[RegressionTypeLearnerOptions],
      classOf[SinglePackageOptions],
      classOf[SourceDiffOptions],
      classOf[ProcessExecutionResult],
      GenWebDataOptions.getClass,
      classOf[DifferentObservations]))) + new PathSerialization()

  class PathSerialization
      extends CustomSerializer[Path](format =>
        ({
          case o: JObject => Paths.get(o.obj.head._2.asInstanceOf[JString].s)
        }, {
          case p: Path => JObject(List("path" -> JString(p.toString)))
        }))

  val defaultSerializer = JsonSerializer()(SerializerFormats.commonSerializationFormats)
}
