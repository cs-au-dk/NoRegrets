package backend.regression_typechecking

import backend.datastructures._
import backend.regression_typechecking.TaggedObservation.TypeStr
import backend.utils.{Log, Logger}
import backend.utils.Utils._

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

  /**
    * Say that we have a sequence of test results, some failing, some suceeding where the first one succeeds:
    * succ1, succ2, fail1, fail2, ..., sucn, failn
    * Then we change that list into a sequence of succeeding test results
    *
    * For the example above, it must be the case that fail1 is roughly a subset of succ2 plus some observations related to the failure (plus maybe some observations related to the library change).
    * So we can take observations in succ2 that are not in fail1 (succ2 -- fail1) and pull them into fail1 to create a new test result "pseudo succ (fail1)~.
    * So after running this algorithm, we get the following sequence
    * succ1, succ2, pseudo succ (fail1), pseudo succ (fail2), ..., sucn, pseudo succ (failn)
    *
    * Note, for this algorithm to apply on a failed test, we must have a succeeding test of some prior version of the library.
    * However, currently, we do only start the algorithm if the first version of the library succeeds.
    *
    * @param tracingResults
    * @return
    */
  def augmentObservationsSmartly(
    tracingResults: Map[PackageAtVersion, Map[PackageAtVersion, TracingResult]])
    : Map[PackageAtVersion, Map[PackageAtVersion, TracingResult]] = {
    // first we artificially generate observations for failed runs using
    // the ones in latest successful run with additionally the ones of the failing run, if available
    tracingResults.mapValuesWithKey {
      case (client, libToResult) =>
        val orderedVersions = libToResult.keys
          .filter(x => Versions.toVersion(x.packageVersion).isDefined)
          .toList
          .sortBy(x => Versions.toVersion(x.packageVersion).get)(
            SemverWithUnnormalized.SemverOrdering)

        val firstResult = libToResult(orderedVersions.head)
        val firstSucceeded = firstResult.isInstanceOf[APIModel]
        if (!firstSucceeded) {
          libToResult // preserve the old results, see later why
        } else {
          val newMap = orderedVersions.foldLeft(
            (firstResult, Map[PackageAtVersion, TracingResult]())) {
            case (aggResult, vers) =>
              libToResult(vers) match {
                case success: APIModel =>
                  (success, aggResult._2 + (vers -> success))
                case failure: LearningFailure =>
                  // augment last._1 with observations from failing test
                  val lastSuccessful = aggResult._1
                  // collecting paths that have been observed in the failing post
                  val pathsInFailure = failure.observations.map(_.path).toSet
                  val nonObservedInFailure = lastSuccessful.observations.filter(o =>
                    !pathsInFailure.contains(o.path))

                  // new observations and unifications
                  val inferred = APIModel(
                    nonObservedInFailure ++ failure.observations,
                    lastSuccessful.unifications ++ failure.unifications,
                    failure.testTimeMillis,
                    //Notice, we don't currently forward coverage when augmenting smartly
                    CoverageObject(),
                    List())
                  (lastSuccessful, aggResult._2 + (vers -> inferred))
              }
          }
          newMap._2
        }
    } // ok, so now they should all succeed, except the ones where even the first test was failing
    //.filter {
    //  case (client, libToresult) =>
    //    libToresult.values.forall {
    //      case l: APIModel => true
    //      case _           => false
    //    }
    //}
  }
}

case class AggregateLibraryKnowledge(
  knownPerVersion: Map[PackageAtVersion, AggregatedVersionKnowledge]) {
  private val log = Logger(this.getClass.getSimpleName, Log.Level.Debug)

  def diffs(typingRelation: TypingRelation): Map[PackageAtVersion, RegressionInfo] = {
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

    val resMap = mutable.Map[PackageAtVersion, RegressionInfo]()

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
                    typingRelation: TypingRelation): RegressionInfo = {

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
      val diffsOnPath: Option[(AccessPath, TypeDiff)] =
        typingRelation.diffTypes(path, typesBase, typesPost, typesBaseMap, typesPostMap)
      if (diffsOnPath.isDefined)
        diff += diffsOnPath.get
    }

    RegressionInfo(diff)
  }
}

case class ClientDetail(packageAtVersion: PackageAtVersion,
                        succeeded: Boolean,
                        error: String,
                        //time it took to run the tests of NoRegretsPlus (not including model parsing time)
                        //Always set to 0 for NoRegrets
                        testTimeMillis: Number,
                        //time it took to execute the full tool
                        executionTimeMillis: Number,
                        modelSize: Number,
                        compressedModelSize: Number,
                        pathsTotal: Number,
                        pathsCovered: Number,
                        coverageObject: CoverageObject,
                        coverageFiles: List[String],
                        clientOrModelSizeBytes: Number) {}

case class CoverageObject(statementCoverage: Map[String, Statements] = Map(),
                          lineCoverage: Map[String, Lines] = Map(),
                          linesTotal: Int = 0)

case class AnalysisResults(regressionInfo: RegressionInfo,
                           clientDetails: List[ClientDetail],
                           coverageObject: CoverageObject) {}

case class RegressionInfo(var regressions: Map[AccessPath, TypeDiff],
                          minimize: Boolean = false) {
  if (minimize) {
    val grped = regressions.groupBy(_._1.stripIds())
    regressions = grped.flatMap {
      case (flatPath, regressions) ⇒ regressions.groupBy(_._2).map(_._2.head)
    }
  }

  def merge(other: RegressionInfo) = {

    //Similar regressions are regressions that are equivalent on the path and TypeDiff, but come from different clients
    //They are merged such that they are only represented by one TypeDiff containing both clients.
    val similarRegressions = regressions
      .map(reg ⇒ {
        val otherEq = other.regressions.find {
          case (p, td) ⇒ reg._1.stripIds() == p.stripIds() && reg._2.equalFailure(td)
        }
        if (otherEq.isDefined) Some(reg, otherEq.get) else None
      })
      .flatten
      .map { case ((p, td1), (_, td2)) ⇒ (p, td1.merge(td2)) }
      .toMap

    val merged = (other.regressions ++ regressions)
      .filterKeys(!similarRegressions.contains(_)) ++ similarRegressions
    RegressionInfo(merged)
  }

  val clients: Set[PackageAtVersion] = regressions.flatMap(p => getClients(p._2)).toSet

  val diffClientCount: Set[PackageAtVersion] =
    regressions.flatMap(p => getClients(p._2)).toSet

  val diffObservationCount: Int = regressions.size

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
    regressions.map { case (ap, td) => s"$ap --> $td" }.mkString("\n")
  }
}

trait TypeDiff {
  def merge(td: TypeDiff): TypeDiff
  def equalFailure(td: TypeDiff): Boolean
}
case class NoObservationInPost(obs: List[Observer], types: Set[TypeStr])
    extends TypeDiff {
  override def merge(td: TypeDiff): TypeDiff = ???
  override def equalFailure(td: TypeDiff) = ???
}
case class NoObservationInBase(obs: List[Observer], types: Set[TypeStr])
    extends TypeDiff {
  override def merge(td: TypeDiff): TypeDiff = ???
  override def equalFailure(td: TypeDiff) = ???
}
case class DifferentObservations(types: Map[TypeStr, TypeDiff]) extends TypeDiff {
  override def merge(td: TypeDiff): TypeDiff = ???
  override def equalFailure(td: TypeDiff): Boolean = ???
}
case class RelationFailure(obs: Map[TypeStr, List[Observer]], relationFailure: String)
    extends TypeDiff {
  override def merge(td: TypeDiff): TypeDiff = td match {
    case RelationFailure(obsOther, rel) ⇒
      RelationFailure(
        obs ++ obsOther ++ obsOther.keySet
          .intersect(obs.keySet)
          .map(key ⇒ key → (obs(key) ++ obsOther(key)))
          .toMap,
        relationFailure)
    case _ ⇒ ???
  }
  override def equalFailure(td: TypeDiff): Boolean = td match {
    case RelationFailure(_, cause) ⇒ cause == relationFailure
    case _ ⇒ false
  }
}

case class AggregatedVersionKnowledge(
  knownPerPath: Map[AccessPath, Set[TaggedObservation]] = Map()) {

  def add(client: PackageAtVersion, result: TracingResult): AggregatedVersionKnowledge = {
    val newM = result.observations
      .filter(_.isInstanceOf[ReadObservation])
      .asInstanceOf[List[ReadObservation]]
      .foldLeft(knownPerPath) {
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
  def apply(observer: PackageAtVersion, o: ReadObservation): TaggedObservation = {
    TaggedObservation(o.`type`, Set(Observer(observer, o.stack)))
  }

  type TypeStr = String
}
case class TaggedObservation(`type`: TypeStr, observer: Set[Observer]) {
  override def toString: String = { s"${`type`} (seen ${observer.size})" }
}

case class Observer(pv: PackageAtVersion, stack: Option[String])
