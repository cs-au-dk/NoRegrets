package UnitTests
import backend.regression_typechecking._
import org.scalatest.{FlatSpec, Matchers}

class ModelCompressTest extends FlatSpec with Matchers {
  val TM = TestModel(_: String)
  val AL = (args: Int) ⇒ ApplicationLabel(args, constructorCall = false, TestModel.getId())
  val PL = PropertyLabel(_)
  val AG = ArgLabel(_)


  val `SimpleFuncApp_num→num` =
    TM("number")
      .withChild(AG(0), TM("number"))

  val `SimpleFuncApp_str→num` =
    TM("number")
      .withChild(AG(0), TM("string"))


  "compress1" should "compress two calls with equal arguments and return types" in {
    val model1Compressed = TM("function")
      .withChild(AL(1), `SimpleFuncApp_num→num`)

    val model1Uncompressed = model1Compressed
      .withChild(AL(1), `SimpleFuncApp_num→num`)

    val compressedAPIModel = model1Uncompressed.toTreeAPIModel().compress().toAPIModel()

    assert(compressedAPIModel.observations.length == model1Compressed.size)
  }

  val unequalArgsModel = TM("function")
    .withChild(AL(1), `SimpleFuncApp_num→num`)
    .withChild(AL(1), `SimpleFuncApp_str→num`)
  "compress2" should "should not compress two calls with unequal arguments by default" in {
    val compressedAPIModel = unequalArgsModel.toTreeAPIModel().compress().toAPIModel()

    assert(compressedAPIModel.observations.length == unequalArgsModel.size)
  }

  val unequalArgsModelCompressed = TM("function")
    .withChild(AL(1), `SimpleFuncApp_num→num`)
  "compress3" should "should compress two calls with unequal arguments by default if option is enabled" in {
    val compressedAPIModel = unequalArgsModel.toTreeAPIModel().compress(requireEqualArgs = false).toAPIModel()

    assert(compressedAPIModel.observations.length == unequalArgsModelCompressed.size)
  }

}

object TestModel {
  var id = 0;

  def getId() = {
    id += 1
    id
  }
}
case class TestModel(`type`: String, vop: Option[AccessPath] = None, children: Map[Label, TestModel] = Map()) {

  def withChild(lab: Label, t: TestModel): TestModel = {
    TestModel(`type`, vop, children + (lab → t))
  }

  def toTreeAPIModel(): TreeAPIModel = {
    new TreeAPIModel(getObs(), 0)
  }

  def size(): Int = 1 + children.values.map(_.size).sum

  def getObs(
    path: AccessPath = AccessPath(List(new RequireLabel("lib")))): List[Observation] = {
    ReadObservation(
      path,
      `type`,
      None,
      Map(),
      None,
      Some(TestModel.getId),
      vop,
      isKnown = false,
      isNative = false,
      didThrow = false) :: children.flatMap {
      case (lab, c) ⇒ c.getObs(AccessPath(path.labels ++ List(lab)))
    }.toList
  }

}
