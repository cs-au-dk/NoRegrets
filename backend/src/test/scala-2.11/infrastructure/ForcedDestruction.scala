package infrastructure

import backend.utils.executors.ForciblyDestroyProcessHelper
import org.scalatest._

import scala.sys.process.Process

class ForcedDestruction extends FlatSpec with Matchers with Inspectors {

  "force process destruction" should "work" in {

    val p = Process(Seq("bash", "-c", "sleep 5")).run()
    ForciblyDestroyProcessHelper.forciblyDestroy(p)
    p.exitValue()
  }
}
