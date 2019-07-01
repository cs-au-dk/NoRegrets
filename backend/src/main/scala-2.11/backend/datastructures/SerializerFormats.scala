package backend.datastructures

import java.nio.file._

import backend.commands.ApiTrace.ApiTraceOptions
import backend.commands.BreakingChanges.BreakingChangesOptions
import backend.commands.Clone.CloneOptions
import backend.commands.Common._
import backend.commands.Dependents.DependentOptions
import backend.commands.RunTests.RunTestsOptions
import backend.commands.SourceDiff.SourceDiffOptions
import backend.commands.Successfuls.SuccessfulOptions
import backend.commands._
import backend.regression_typechecking._
import backend.utils._
import org.json4s.JsonAST.{JLong, JNothing, JNull}
import org.json4s.reflect.TypeInfo
import org.json4s.{
  CustomSerializer,
  DefaultFormats,
  Extraction,
  Formats,
  JArray,
  JBool,
  JInt,
  JObject,
  JString,
  ShortTypeHints
}

import scala.collection.immutable.List

object SerializerFormats {
  val commonSerializationFormats: Formats = DefaultFormats.withHints(ShortTypeHints(List(
    classOf[DependentOptions],
    classOf[SuccessfulOptions],
    classOf[LearningFailure],
    classOf[APIModel],
    classOf[ArgLabel],
    classOf[ApplicationLabel],
    classOf[PropertyLabel],
    classOf[WriteLabel],
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
    classOf[RunTestsOptions],
    classOf[BreakingChangesOptions],
    classOf[SinglePackageOptions],
    classOf[SourceDiffOptions],
    classOf[ProcessExecutionResult],
    GenWebDataOptions.getClass,
    classOf[TestReport],
    classOf[TestReportError],
    classOf[TypeRegression],
    classOf[ClientDetail],
    classOf[AnalysisResults],
    classOf[DifferentObservations]))) + new PathSerialization() + new ObservationSerialization() + new AccessPathSerialization() + new ValueOriginInfoSerialization() + new RegressionInfoSerialization() + new AnalysisResultsSerialization()

  class AnalysisResultsSerialization
      extends CustomSerializer[AnalysisResults](format ⇒
        ({
          case o: JObject ⇒ ??? //Never deserialized
        }, {
          case res: AnalysisResults ⇒ {
            JObject(
              List(
                "regressionInfo" → Extraction.decompose(res.regressionInfo)(
                  SerializerFormats.commonSerializationFormats),
                "clientDetails" → JArray(res.clientDetails.map(detail ⇒
                  JObject(List(
                    "packageAtVersion" → JString(detail.packageAtVersion.toString),
                    "succeeded" → JBool(detail.succeeded),
                    "error" → JString(detail.error),
                    "executionTimeMillis" → JInt(detail.executionTimeMillis.intValue()),
                    "testTimeMillis" → JInt(detail.testTimeMillis.intValue()),
                    "modelSize" → JInt(detail.modelSize.intValue()),
                    "compressedModelSize" → JInt(detail.compressedModelSize.intValue()),
                    "pathsTotal" → JInt(detail.pathsTotal.intValue()),
                    "pathsCovered" → JInt(detail.pathsCovered.intValue()),
                    "coverageObject" → Extraction.decompose(detail.coverageObject)(
                      SerializerFormats.commonSerializationFormats),
                    //  JObject(List(
                    //  "statementCoverage" → JObject(
                    //    detail.coverageObject.statementCoverage
                    //      .mapValues(JDouble(_))
                    //      .toList),
                    //  "lineCoverage" → JObject(
                    //    detail.coverageObject.lineCoverage.mapValues(JDouble(_)).toList),
                    //  "lines" → JInt(detail.coverageObject.linesTotal))),
                    "clientOrModelSizeBytes" → JLong(
                      detail.clientOrModelSizeBytes.longValue()))))),
                "coverageObject" → Extraction.decompose(res.coverageObject)(
                  SerializerFormats.commonSerializationFormats),
                //JObject(List(
                //"statementCoverage" → JObject(
                //  res.coverageObject.statementCoverage
                //    .mapValues(s ⇒
                //      JObject(List(
                //        "covered" → JInt(s.covered),
                //        "pct" → JDouble(s.pct),
                //        "skipped" → JInt(s.skipped),
                //        "total" → JInt(s.total))))
                //    .toList),
                //"lineCoverage" → JObject(
                //  res.coverageObject.lineCoverage
                //    .mapValues(l ⇒
                //      JObject(List(
                //        "covered" → JInt(l.covered),
                //        "pct" → JDouble(l.pct),
                //        "skipped" → JInt(l.skipped),
                //        "total" → JInt(l.total))))
                //    .toList),
                "linesTotal" → JInt(res.coverageObject.linesTotal)))
          }
        }))

  class RegressionInfoSerialization
      extends CustomSerializer[RegressionInfo](format ⇒
        ({
          case o: JObject ⇒ ??? //Never deserialized
        }, {
          case kd: RegressionInfo ⇒
            JObject(List("regressions" → JArray(kd.regressions.map {
              case pathRegression ⇒
                Extraction.decompose(pathRegression)(
                  SerializerFormats.commonSerializationFormats)
            }.toList)))
        }))

  class PathSerialization
      extends CustomSerializer[Path](format =>
        ({
          case o: JObject => Paths.get(o.obj.head._2.asInstanceOf[JString].s)
        }, {
          case p: Path => JObject(List("path" -> JString(p.toString)))
        }))

  class AccessPathSerialization
      extends CustomSerializer[AccessPath](format ⇒
        ({
          case o: JArray ⇒
            AccessPath(o.arr.map(o => {
              val oMap = o.asInstanceOf[JObject].obj.toMap
              lazy val name = oMap("name").asInstanceOf[JString].s
              lazy val idx = oMap("idx").asInstanceOf[JInt].num.toInt
              lazy val numArgs = oMap("numArgs").asInstanceOf[JInt].num.toInt
              lazy val isConstructor =
                oMap("constructorCall").asInstanceOf[JBool].value
              lazy val identifier = oMap("identifier").asInstanceOf[JInt].num.toInt
              oMap("jsonClass").asInstanceOf[JString].s match {
                case "RequireLabel" => RequireLabel(name)
                case "ArgLabel"     => ArgLabel(idx)
                case "AccessLabel"  => AccessLabel()
                case "ApplicationLabel" =>
                  ApplicationLabel(numArgs, isConstructor, identifier)
                case "PropertyLabel" => PropertyLabel(name)
                case "WriteLabel"    => WriteLabel(name, identifier)
              }
            }))
        }, {
          case p: AccessPath ⇒
            JArray(p.labels.map(lab =>
              Extraction.decompose(lab)(SerializerFormats.commonSerializationFormats)))

        }))

  class ValueOriginInfoSerialization
      extends CustomSerializer[ValueOriginInfo](format ⇒
        ({
          case o: JObject ⇒
            val oMap = o.obj.toMap
            ValueOriginInfo(
              valueOriginPath = Extraction
                .extract(oMap("valueOriginPath"), TypeInfo(classOf[AccessPath], None))(
                  SerializerFormats.commonSerializationFormats)
                .asInstanceOf[AccessPath],
              sideEffects = oMap("sideEffects").asInstanceOf[JString].s)
        }, {
          case voi: ValueOriginInfo ⇒
            JObject(
              List(
                "valueOriginPath" →
                  Extraction.decompose(voi.valueOriginPath)(
                    SerializerFormats.commonSerializationFormats),
                "sideEffects" → JString(voi.sideEffects)))
        }))

  val UNDEF_STRING = "__UNDEFINED__"
  val NULL_STRING = "__NULL__"
  val NON_EXIST = "__NON-EXISTING__"
  val IGNORE = "__IGNORE__"

  //class WriteObservationSerialization
  //    extends CustomSerializer[WriteObservation](format ⇒
  //      ({
  //        case o: JObject ⇒ {
  //          val oMap = o.obj.toMap
  //          WriteObservation(
  //            Extraction
  //              .extract(oMap("Path"), TypeInfo(classOf[AccessPath], None))(
  //                SerializerFormats.commonSerializationFormats)
  //              .asInstanceOf[AccessPath],
  //            if (oMap.contains("id")) Some(oMap("id").asInstanceOf[JInt].num.toInt)
  //            else None,
  //            if (oMap.contains("stack")) Some(oMap("stack").asInstanceOf[JString].s)
  //            else None,
  //            oMap("writtenValue").asInstanceOf[JString].s)
  //        }
  //      }, {
  //        case o: WriteObservation ⇒ {
  //          JObject(
  //            List(
  //              "path" ->
  //                Extraction.decompose(o.path)(
  //                  SerializerFormats.commonSerializationFormats),
  //              "id" -> o.id.map(JInt(_)).getOrElse(JNothing),
  //              "writtenValue" → JString(o.writtenValue),
  //              "stack" → o.stack.map(JString(_)).getOrElse(JNothing)))
  //        }
  //      }))

  class ObservationSerialization
      extends CustomSerializer[Observation](format =>
        ({
          case o: JObject => {
            val oMap = o.obj.toMap
            if (oMap("jsonClass").asInstanceOf[JString].s == "ReadObservation") {
              val jValue = oMap.contains("value") match {
                case true => {
                  Some(oMap("value") match {
                    case a @ JString(s) => {
                      s match {
                        case NULL_STRING  => JNull
                        case UNDEF_STRING => JNothing
                        case NON_EXIST ⇒ JString(NON_EXIST)
                        case IGNORE ⇒ JString(IGNORE)
                        case _ => a
                      }
                    }
                    case i => i
                  })
                }
                case false => None
              }
              ReadObservation(
                Extraction
                  .extract(oMap("path"), TypeInfo(classOf[AccessPath], None))(
                    SerializerFormats.commonSerializationFormats)
                  .asInstanceOf[AccessPath],
                oMap("type").asInstanceOf[JString].s,
                if (oMap.contains("stack")) Some(oMap("stack").asInstanceOf[JString].s)
                else None,
                oMap("propertyTypes")
                  .asInstanceOf[JObject]
                  .obj
                  .map { case (k, v) => k -> v.asInstanceOf[JString].s }
                  .toMap,
                jValue,
                if (oMap.contains("id")) Some(oMap("id").asInstanceOf[JInt].num.toInt)
                else None,
                if (oMap.contains("valueOriginPath"))
                  Some(
                    Extraction
                      .extract(
                        oMap("valueOriginPath"),
                        TypeInfo(classOf[AccessPath], None))(
                        SerializerFormats.commonSerializationFormats)
                      .asInstanceOf[AccessPath])
                else None,
                oMap("isKnownValue").asInstanceOf[JBool].value,
                oMap("isNative").asInstanceOf[JBool].value,
                oMap("didThrow").asInstanceOf[JBool].value)
            } else {
              WriteObservation(
                Extraction
                  .extract(oMap("path"), TypeInfo(classOf[AccessPath], None))(
                    SerializerFormats.commonSerializationFormats)
                  .asInstanceOf[AccessPath],
                if (oMap.contains("id")) Some(oMap("id").asInstanceOf[JInt].num.toInt)
                else None,
                if (oMap.contains("stack")) Some(oMap("stack").asInstanceOf[JString].s)
                else None,
                if (oMap.contains("valueOriginPath"))
                  Some(
                    Extraction
                      .extract(
                        oMap("valueOriginPath"),
                        TypeInfo(classOf[AccessPath], None))(
                        SerializerFormats.commonSerializationFormats)
                      .asInstanceOf[AccessPath])
                else None, {
                  val wValMap = oMap("writtenValue").asInstanceOf[JObject].obj.toMap
                  WrittenValue(wValMap("type").asInstanceOf[JString].s, wValMap("value"))
                })
            }
          }
        }, {
          case o: Observation => {
            o match {
              case o: ReadObservation ⇒ {
                val jValue = o.value match {
                  case Some(JNull)    => JString(NULL_STRING)
                  case Some(JNothing) => JString(UNDEF_STRING)
                  case Some(s @ JString(NON_EXIST)) ⇒ s
                  case Some(s @ JString(IGNORE)) ⇒ s
                  case Some(x) => x
                  case None    => JNothing //Serialized as nothing
                }
                JObject(
                  List(
                    "path" ->
                      Extraction.decompose(o.path)(
                        SerializerFormats.commonSerializationFormats),
                    "type" -> JString(o.`type`),
                    "stack" -> o.stack.map(JString(_)).getOrElse(JNothing),
                    "propertyTypes" -> JObject(o.propertyTypes.map {
                      case (k, v) => k -> JString(v)
                    }.toList),
                    "value" -> jValue,
                    "id" -> o.id.map(JInt(_)).getOrElse(JNothing),
                    "valueOriginPath" → o.valueOriginPath
                      .map(Extraction.decompose(_)(
                        SerializerFormats.commonSerializationFormats))
                      .getOrElse(JNothing),
                    "isKnownValue" → JBool(o.isKnown),
                    "jsonClass" → JString("ReadObservation"),
                    "isNative" → JBool(o.isNative),
                    "didThrow" → JBool(o.didThrow)))
              }
              case o: WriteObservation ⇒ {
                JObject(
                  List(
                    "path" ->
                      Extraction.decompose(o.path)(
                        SerializerFormats.commonSerializationFormats),
                    "id" -> o.id.map(JInt(_)).getOrElse(JNothing),
                    "writtenValue" → JObject(
                      List(
                        "type" → JString(o.writtenValue.`type`),
                        "value" → o.writtenValue.value)),
                    "stack" → o.stack.map(JString(_)).getOrElse(JNothing),
                    "jsonClass" → JString("WriteObservation"),
                    "valueOriginPath" → o.valueOriginPath
                      .map(Extraction.decompose(_)(
                        SerializerFormats.commonSerializationFormats))
                      .getOrElse(JNothing)))
              }
            }
          }
        }))

//  class ReadObservationSerialization
//      extends CustomSerializer[ReadObservation](format =>
//        ({
//          case o: JObject => {
//            val oMap = o.obj.toMap
//            val jValue = oMap.contains("value") match {
//              case true => {
//                Some(oMap("value") match {
//                  case a @ JString(s) => {
//                    s match {
//                      case NULL_STRING  => JNull
//                      case UNDEF_STRING => JNothing
//                      case NON_EXIST ⇒ JString(NON_EXIST)
//                      case IGNORE ⇒ JString(IGNORE)
//                      case _ => a
//                    }
//                  }
//                  case i => i
//                })
//              }
//              case false => None
//            }
//            ReadObservation(
//              Extraction
//                .extract(oMap("path"), TypeInfo(classOf[AccessPath], None))(
//                  SerializerFormats.commonSerializationFormats)
//                .asInstanceOf[AccessPath],
//              oMap("type").asInstanceOf[JString].s,
//              if (oMap.contains("stack")) Some(oMap("stack").asInstanceOf[JString].s)
//              else None,
//              oMap("propertyTypes")
//                .asInstanceOf[JObject]
//                .obj
//                .map { case (k, v) => k -> v.asInstanceOf[JString].s }
//                .toMap,
//              jValue,
//              if (oMap.contains("id")) Some(oMap("id").asInstanceOf[JInt].num.toInt)
//              else None,
//              if (oMap.contains("valueOriginPath"))
//                Some(Extraction
//                  .extract(oMap("valueOriginPath"), TypeInfo(classOf[AccessPath], None))(
//                    SerializerFormats.commonSerializationFormats)
//                  .asInstanceOf[AccessPath])
//              else None,
//              oMap("isKnownValue").asInstanceOf[JBool].value)
//          }
//        }, {
//          case o: ReadObservation => {
//            val jValue = o.value match {
//              case Some(JNull)    => JString(NULL_STRING)
//              case Some(JNothing) => JString(UNDEF_STRING)
//              case Some(s @ JString(NON_EXIST)) ⇒ s
//              case Some(s @ JString(IGNORE)) ⇒ s
//              case Some(x) => x
//              case None    => JNothing //Serialized as nothing
//            }
//            JObject(
//              List(
//                "path" ->
//                  Extraction.decompose(o.path)(
//                    SerializerFormats.commonSerializationFormats),
//                "type" -> JString(o.`type`),
//                "stack" -> o.stack.map(JString(_)).getOrElse(JNothing),
//                "propertyTypes" -> JObject(o.propertyTypes.map {
//                  case (k, v) => k -> JString(v)
//                }.toList),
//                "value" -> jValue,
//                "id" -> o.id.map(JInt(_)).getOrElse(JNothing),
//                "valueOriginInfo" → o.valueOriginPath
//                  .map(
//                    Extraction.decompose(_)(SerializerFormats.commonSerializationFormats))
//                  .getOrElse(JNothing),
//                "isKnownValue" → JBool(o.isKnown)))
//          }
//        }))

  val defaultSerializer = JsonSerializer()(SerializerFormats.commonSerializationFormats)
}
