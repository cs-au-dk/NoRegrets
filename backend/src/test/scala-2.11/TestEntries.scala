import org.scalatest._

trait TestEntries extends FlatSpec with Matchers with TestingUtils {

  def perform(s: String, upTo: Option[String] = None): Unit

  "top-10-debug@2.0.0" should "run" in {
    perform("debug@2.0.0", Some("3.0.0"))
  }

  "top-10-lodash@3.0.0" should "run" in {
    perform("lodash@3.0.0", Some("4.0.0"))
  }

  "top-10-async@2.0.0" should "run" in {
    perform("async@2.0.0", Some("3.0.0"))
  }

  "top-10-moment@2.0.0" should "run" in {
    perform("moment@2.0.0", Some("3.0.0"))
  }

  "top-10-express@3.0.0" should "run" in {
    perform("express@3.0.0", Some("4.0.0"))
  }

  "top1000-boxen@1.0.0" should "run" in {
    perform("boxen@1.0.0", Some("2.1.0"))
  }

  "top1000-react-onclickoutside@4.0.1" should "run" in {
    perform("react-onclickoutside@4.0.1", Some("6.7.1"))
  }

  "top1000-d3-shape@1.0.0" should "run" in {
    perform("d3-shape@1.0.0", Some("1.2.2"))
  }

  "top1000-webpack-stream@2.0.0" should "run" in {
    perform("webpack-stream@2.0.0", Some("5.2.1"))
  }

  "top1000-qiniu@1.2.0" should "run" in {
    perform("qiniu@1.2.0", Some("7.2.1"))
  }

  "top1000-koa-send@1.0.0" should "run" in {
    perform("koa-send@1.0.0", Some("5.0.0"))
  }

  "top1000-twilio@1.0.0" should "run" in {
    perform("twilio@1.0.0", Some("3.26.1"))
  }

  "top1000-big-integer@1.0.0" should "run" in {
    perform("big-integer@1.0.0", Some("1.6.40"))
  }

  "top1000-wreck@12.0.0" should "run" in {
    perform("wreck@12.0.0", Some("14.1.3"))
  }

  "top1000-node-rest-client@1.0.0" should "run" in {
    perform("node-rest-client@1.0.0", Some("3.1.0"))
  }

  "ttop100-mime@1.0.0" should "run" in {
    perform("mime@1.0.0", Some("2.4.0"))
  }

  "ttop100-immutable@1.0.0" should "run" in {
    perform("immutable@1.0.0", Some("3.8.2"))
  }

  "ttop100-aws-sdk@2.0.1" should "run" in {
    perform("aws-sdk@2.0.1", Some("2.395.0"))
  }

  "ttop100-mysql@2.0.0" should "run" in {
    perform("mysql@2.0.0", Some("2.16.0"))
  }

  "ttop100-minimatch@1.0.0" should "run" in {
    perform("minimatch@1.0.0", Some("3.0.4"))
  }

  "ttop100-qs@1.0.0" should "run" in {
    perform("qs@1.0.0", Some("6.6.0"))
  }

  "ttop100-joi@11.0.0" should "run" in {
    perform("joi@11.0.0", Some("14.3.1"))
  }

  "ttop100-ora@1.0.0" should "run" in {
    perform("ora@1.0.0", Some("3.0.0"))
  }

  "ttop100-autoprefixer@1.2.0" should "run" in {
    perform("autoprefixer@1.2.0", Some("9.4.7"))
  }

  "ttop100-mongoose@1.0.0" should "run" in {
    perform("mongoose@1.0.0", Some("5.4.8"))
  }
}
