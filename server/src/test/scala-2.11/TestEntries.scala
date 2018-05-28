import org.scalatest._

trait TestEntries extends FlatSpec with Matchers with TestingUtils {

  def perform(s: String, upTo: Option[String] = None): Unit
  "debug@2.0.0" should "run" in {
    perform("debug@2.0.0", Some("3.0.0"))
  }
  "lodash@3.0.0" should "run" in {
    perform("lodash@3.0.0", Some("4.0.0"))
  }
  "async@2.0.0" should "run" in {
    perform("async@2.0.0", Some("3.0.0"))
  }
  "moment@2.0.0" should "run" in {
    perform("moment@2.0.0", Some("3.0.0"))
  }
  "express@3.0.0" should "run" in {
    perform("express@3.0.0", Some("4.0.0"))
  }
  "chalk@1.0.0" should "run" in {
    perform("chalk@1.0.0", Some("2.0.0"))
  }
  "bluebird@3.0.0" should "run" in {
    perform("bluebird@3.0.0", Some("4.0.0"))
  }
  "react@15.0.0" should "run" in {
    perform("react@15.0.0", Some("16.0.0"))
  }
  "commander@2.0.0" should "run" in {
    perform("commander@2.0.0", Some("3.0.0"))
  }
  "request@2.0.0" should "run" in {
    perform("request@2.0.0", Some("3.0.0"))
  }
  "body-parser@1.0.0" should "run" in {
    perform("body-parser@1.0.0", Some("2.0.0"))
  }
  "q@1.0.0" should "run" in {
    perform("q@1.0.0", Some("2.0.0"))
  }
}
