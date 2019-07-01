package UnitTests
import backend.regression_typechecking._
import org.scalatest.{FlatSpec, Matchers}

class AccessPathTests extends FlatSpec with Matchers {
  it should "be able to identify a path as an callback arg path" in {
    val labs = List(
      new RequireLabel("lib"),
      new PropertyLabel("f"),
      new ApplicationLabel(1, false, 0),
      new ArgLabel(0),
      new ApplicationLabel(1, false, 0),
      new ArgLabel(0),
      new PropertyLabel("g"),
      new ApplicationLabel(1, false, 0),
      new ArgLabel(0))

    assert(!AccessPath(labs.slice(0, 1)).isCallBackArg)
    assert(!AccessPath(labs.slice(0, 2)).isCallBackArg)
    assert(!AccessPath(labs.slice(0, 3)).isCallBackArg)
    assert(!AccessPath(labs.slice(0, 4)).isCallBackArg)
    assert(!AccessPath(labs.slice(0, 5)).isCallBackArg)
    assert(AccessPath(labs.slice(0, 6)).isCallBackArg)
    assert(AccessPath(labs.slice(0, 7)).isCallBackArg)
    assert(AccessPath(labs.slice(0, 8)).isCallBackArg)
    assert(!AccessPath(labs.slice(0, 9)).isCallBackArg)
  }

}
