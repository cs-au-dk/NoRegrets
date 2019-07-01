package infrastructure

import backend.datastructures.SerializerFormats
import backend.regression_typechecking._
import org.json4s.native.Serialization.write
import org.scalatest._

class SampleLearnedJson extends FlatSpec with Matchers with Inspectors {
  "json serialization" should "annotate labels" in {

    implicit val s = SerializerFormats.commonSerializationFormats
    assert(write(RequireLabel("something")).contains("jsonClass"))

  }
}
