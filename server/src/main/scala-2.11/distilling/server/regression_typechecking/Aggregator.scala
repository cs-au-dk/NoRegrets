package distilling.server.regression_typechecking

import distilling.server.datastructures._
import distilling.server.regression_typechecking.TaggedObservation.TypeStr
import distilling.server.regression_typechecking.TracingResult.{AccessPath, PathOrdering}
import distilling.server.utils.{Log, Logger}
import distilling.server.utils.Utils._

import scala.collection.mutable

object Aggregator {

  /**
    * Data are in the format:
    * client -> library version -> tracing-results
    *
    * Returns a mapping:
    * library version -> aggregated version knowledge
    */
  def aggregate(data: Map[PackageAtVersion, Map[PackageAtVersion, TracingResult]])
    : AggregateLibraryKnowledge = {

    val swappedKeysData = data.swapKeys()

    val newM = swappedKeysData
      .mapValuesWithKey {
        case (_, clientResults) =>
          clientResults.foldLeft(AggregatedVersionKnowledge()) {
            case (agg, (client, result)) => agg.add(client, result)
          }
      }
      .mapValues(_.reduce())

    AggregateLibraryKnowledge(newM)
  }

  def augmentObservationsSmartly(
    learned: Map[PackageAtVersion, Map[PackageAtVersion, TracingResult]])
    : Map[PackageAtVersion, Map[PackageAtVersion, TracingResult]] = {
    // first we artificially generate observations for failed runs using
    // the ones in latest successful run with additionally the ones of the failing run, if available
    learned
      .mapValuesWithKey {
        case (client, libToResult) =>
          val orderedVersions = libToResult.keys
            .filter(x => Versions.toVersion(x.packageVersion).isDefined)
            .toList
            .sortBy(x => Versions.toVersion(x.packageVersion).get)(
              SemverWithUnnormalized.SemverOrdering)

          val firstResult = libToResult(orderedVersions.head)
          val firstSucceeded = firstResult.isInstanceOf[Learned]
          if (!firstSucceeded) {
            libToResult // preserve the old results, see later why
          } else {
            val newMap = orderedVersions.foldLeft(
              (firstResult, Map[PackageAtVersion, TracingResult]())) {
              case (last, cur) =>
                libToResult(cur) match {
                  case success: Learned =>
                    (success, last._2 + (cur -> success))
                  case failure: LearningFailure =>
                    // augment last._1 with observations from failing test
                    val lastSuccessful = last._1
                    // collecting paths that have been observed in the failing post
                    val pathsInFailure = failure.observations.map(_.path).toSet
                    val nonObservedInFailure = lastSuccessful.observations.filter(o =>
                      !pathsInFailure.contains(o.path))

                    // new observations and unifications
                    val inferred = Learned(
                      nonObservedInFailure ++ failure.observations,
                      lastSuccessful.unifications ++ failure.unifications)
                    (lastSuccessful, last._2 + (cur -> inferred))
                }
            }
            newMap._2
          }
      } // ok, so now they should all succeed, except the ones where even the first test was failing
      .filter {
        case (client, libToresult) =>
          libToresult.values.forall {
            case l: Learned => true
            case _          => false
          }
      }
  }
}

case class AggregateLibraryKnowledge(
  knownPerVersion: Map[PackageAtVersion, AggregatedVersionKnowledge]) {
  private val log = Logger(this.getClass.getSimpleName, Log.Level.Debug)

  def diffs(typingRelation: TypingRelation): Map[PackageAtVersion, KnowledgeDiff] = {
    log.debug(s"computing diff with typingRelation ${typingRelation.toString}")

    val sortedKnownPerVersion =
      knownPerVersion.keys
        .filter(x => Versions.toVersion(x.packageVersion).isDefined)
        .toList
        .sortBy(x => Versions.toVersion(x.packageVersion).get)(
          SemverWithUnnormalized.SemverOrdering)

    val sortedKnownPerVersionWithKnowledge = sortedKnownPerVersion.map(pkg => {
      (pkg, knownPerVersion(pkg))
    })

    val resMap = mutable.Map[PackageAtVersion, KnowledgeDiff]()

    if (sortedKnownPerVersionWithKnowledge.nonEmpty) {
      val first = sortedKnownPerVersionWithKnowledge.head
      sortedKnownPerVersionWithKnowledge.foldLeft(first) {
        case (last, cur) =>
          resMap.put(cur._1, diffKnowledge(last._2, cur._2, typingRelation))
          cur
      }

      resMap.toMap
    } else {
      Map()
    }
  }

  def diffKnowledge(base: AggregatedVersionKnowledge,
                    post: AggregatedVersionKnowledge,
                    typingRelation: TypingRelation): KnowledgeDiff = {

    var diff: Map[AccessPath, TypeDiff] = Map()

    val shared = base.knownPerPath.keys.toSet intersect post.knownPerPath.keys.toSet
    val onlyInBase = base.knownPerPath.keys.toSet -- post.knownPerPath.keys.toSet
    val onlyInPost = post.knownPerPath.keys.toSet -- base.knownPerPath.keys.toSet
    onlyInBase.foreach { p =>
      val pDiff = typingRelation.onlyInBase(p, base.knownPerPath)
      pDiff match {
        case Some(d) => diff += d
        case None    => //noop
      }
    }
    onlyInPost.foreach { p =>
      val pDiff = typingRelation.onlyInPost(p, post.knownPerPath)
      pDiff match {
        case Some(d) => diff += d
        case None    => //noop
      }
    }

    for (path <- shared) {
      val obase = base.knownPerPath(path)
      val opost = post.knownPerPath(path)
      val typesBase = obase.map(_.`type`)
      val typesPost = opost.map(_.`type`)
      val typesBaseMap = obase.map(p => p.`type` -> p.observer).toMap
      val typesPostMap = opost.map(p => p.`type` -> p.observer).toMap
      val typesOnlyInBase = typesBase -- typesPost
      val typesOnlyInPost = typesPost -- typesBase
      val diffsOnPath: Option[(AccessPath, TypeDiff)] =
        typingRelation.diffTypes(path, typesBase, typesPost, typesBaseMap, typesPostMap)
      if (diffsOnPath.isDefined)
        diff += diffsOnPath.get
    }

    KnowledgeDiff(diff.toList.sortBy(_._1)(PathOrdering))
  }
}

case class KnowledgeDiff(paths: List[(AccessPath, TypeDiff)]) {

  val clients: Set[PackageAtVersion] = paths.flatMap(p => getClients(p._2)).toSet

  val diffClientCount: Set[PackageAtVersion] = paths.flatMap(p => getClients(p._2)).toSet

  val diffObservationCount: Int = paths.size

  def getClients(t: TypeDiff): List[PackageAtVersion] = {
    t match {
      case n: NoObservationInPost => n.obs.map(_.pv)
      case n: NoObservationInBase => n.obs.map(_.pv)
      case n: RelationFailure =>
        n.obs.values
          .flatMap(observers => observers.map(obs => obs.pv))
          .toList //n.obs.map(_.pv)
      case n: DifferentObservations =>
        n.types.values.flatMap(t => getClients(t)).toList
    }
  }

  override def toString: String = {
    paths.map { case (ap, td) => s"$ap --> $td" }.mkString("\n")
  }
}

trait TypeDiff
case class NoObservationInPost(obs: List[Observer], types: Set[TypeStr]) extends TypeDiff
case class NoObservationInBase(obs: List[Observer], types: Set[TypeStr]) extends TypeDiff
case class DifferentObservations(types: Map[TypeStr, TypeDiff]) extends TypeDiff
case class RelationFailure(obs: Map[TypeStr, List[Observer]], relationFailure: String)
    extends TypeDiff

case class AggregatedVersionKnowledge(
  knownPerPath: Map[AccessPath, Set[TaggedObservation]] = Map()) {

  def add(client: PackageAtVersion, result: TracingResult): AggregatedVersionKnowledge = {
    val newM = result.observations.foldLeft(knownPerPath) {
      case (acc, o) =>
        val prevObservationList = acc.getOrElse(o.path, Set())
        acc + (o.path -> (prevObservationList + TaggedObservation(client, o)))
    }
    AggregatedVersionKnowledge(newM)
  }

  def reduce(): AggregatedVersionKnowledge = {
    val newM = knownPerPath.mapValues { vs =>
      vs.groupBy(_.`type`)
        .mapValues { vs =>
          vs.reduce { (o1, o2) =>
            o1.copy(observer = o1.observer ++ o2.observer)
          }
        }
        .values
        .toSet
    }
    AggregatedVersionKnowledge(newM)
  }

  override def toString: String = {
    knownPerPath.mkString("\n")
  }
}

object TaggedObservation {
  def apply(observer: PackageAtVersion, o: Observation): TaggedObservation = {
    TaggedObservation(o.`type`, Set(Observer(observer, o.stack)))
  }

  type TypeStr = String
}
case class TaggedObservation(`type`: TypeStr, observer: Set[Observer]) {
  override def toString: String = { s"${`type`} (seen ${observer.size})" }
}

case class Observer(pv: PackageAtVersion, stack: Option[String])
