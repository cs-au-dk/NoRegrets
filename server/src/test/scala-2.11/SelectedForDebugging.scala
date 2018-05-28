import java.nio.file.Paths

import distilling.server.commands._
import distilling.server.datastructures.PackageAtVersion
import distilling.server.regression_typechecking._
import distilling.server.utils.{Log, Logger, NotationalUtils}
import org.scalatest._
import org.scalatest.time.SpanSugar._

abstract class SelectedForDebuggingBase extends FlatSpec with Matchers with TestingUtils {
  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)

  val timeout = 600.minutes

  implicit val atNotation: (String => PackageAtVersion) =
    NotationalUtils.atNotationToPackage

  def runEvolution(library: PackageAtVersion,
                   evolution: List[String],
                   client: PackageAtVersion,
                   expect: Map[String, TracingResult] => Boolean,
                   dontCheck: Boolean = false,
                   collectStackTraces: Boolean = true,
                   detailedStackTraces: Boolean = false,
                   ignoreFailingInstallations: Boolean = false,
                   smartAugmentation: Boolean = false): Assertion = {

    val outDir = Paths.get("out/debugging")
    val outDirForSelected =
      outDir.resolve(library.toString + "_" + evolution.mkString("_") + "_" + client)

    outDirForSelected.toFile.mkdirs()

    val opts = RegressionTypeLearnerOptions(
      libraryToCheckName = library.packageName,
      libraryVersions = library.packageVersion :: evolution,
      outDir = outDirForSelected,
      clients = Left(List(client)),
      swarm = false,
      useSmartDiff = smartAugmentation,
      collectStackTraces = collectStackTraces,
      detailedStackTraces = detailedStackTraces,
      ignoreFailingInstallations = ignoreFailingInstallations,
      learningCommandCachePolicy = CommandCachePolicy.REGENERATE_DATA)

    var res = RegressionTypeLearner(opts).handleRegressionTypeLearner()
    val learned = res.learned
    val resMap = learned.head._2.map(p => p._1.toString -> p._2)
    assert(expect(resMap))
  }

  def mkDiff(client: String,
             pre: String,
             post: String,
             obs: Map[String, TracingResult],
             typingRelation: TypingRelation = TypeRegressionPaperTypingRelation)
    : Map[PackageAtVersion, KnowledgeDiff] = {
    Aggregator
      .aggregate(
        Map(
          NotationalUtils.atNotationToPackage(client) -> Map(
            NotationalUtils.atNotationToPackage(pre) -> obs(pre),
            NotationalUtils.atNotationToPackage(post) -> obs(post))))
      .diffs(typingRelation)
  }

  /**
    * Debug@2.4.0 should be an instance of LearningFailed
    */
  //  "debug@2.0.0 to debug@2.4.5 against apper@2.0.0" should "run" in {
  //    runEvolution("debug@2.0.0", List("2.4.0", "2.4.5"), "apper@2.0.0", { result =>
  //      result.values.exists(_.isInstanceOf[LearningFailure]) &&
  //      result.values.exists(_.isInstanceOf[Learned])
  //    }, false, true)
  //  }

  /**
    * Debug@2.4.0 should be an instance of LearningFailed
    */
  "debug@2.0.0 and 2.4.0 against livereloadx@0.3.7" should "run" in {
    runEvolution("debug@2.0.0", List("2.4.0"), "livereloadx@0.3.7", { result =>
      result.values.exists(_.isInstanceOf[LearningFailure]) &&
      result.values.exists(_.isInstanceOf[Learned])
    }, false, true)
  }

  /**
    * It uses value.constructor === Object to decide what to do,
    * so it is crucial not to return a proxy when constructor is looked-up
    * on an object. This is done in the current implementation as the Object
    * is a known function, hence it is not proxified.
    *
    * Issues with this benchmark have been solved by upgrading to node 8.
    **/
  "lodash@4.0.0 against css-info@0.3.0" should "run" in {
    runEvolution("lodash@4.0.0", List(), "css-info@0.3.0", { result =>
      result.values.forall(_.isInstanceOf[Learned])
    }, false, true)
  }

  "lodash@4.17.3 against throttled-web-client@1.0.0" should "run" in {
    runEvolution("lodash@4.17.3", List("4.17.4"), "throttled-web-client@1.0.0", {
      result =>
        result.values.forall(_.isInstanceOf[Learned])
    }, false, true)
  }

  "lodash@4.17.3 against ng-dependencies@0.6.0" should "run" in {
    runEvolution("lodash@4.17.3", List(), "ng-dependencies@0.6.0", { result =>
      result.values.forall(_.isInstanceOf[Learned])
    }, false, true)
  }

  "lodash@4.17.3 against cache-crusher@0.5.2" should "run" in {
    runEvolution("lodash@4.0.0", List(), "cache-crusher@0.5.2", { result =>
      result.values.forall(_.isInstanceOf[Learned])
    }, false, true)
  }

  "lodash@3.0.0 against mdquery@0.1.0" should "run" in {
    runEvolution("lodash@3.0.0", List("3.0.1"), "mdquery@0.1.0", { result =>
      result.values.forall(_.isInstanceOf[Learned])
    }, false, true)
  }

  "debug@2.4.3 against bouchon@0.0.1" should "run" in {
    runEvolution("debug@2.4.3", List(), "bouchon@0.0.1", { result =>
      result.values.forall(_.isInstanceOf[Learned])
    }, false, true)
  }

  /**
    * Used to fix issue with exceptions upon call of toString in debug message.
    */
  "debug@2.0.0 against proxy-agent@2.0.0" should "run" in {
    runEvolution("debug@2.0.0", List(), "proxy-agent@2.0.0", { result =>
      result.values.forall(_.isInstanceOf[Learned])
    }, false, true)
  }

  /**
    * Not finding the correct test files
    */
  "debug@2.3.3 against express-lembro@1.1.0" should "run" in {
    runEvolution("debug@2.3.3", List(), "express-lembro@1.1.0", { result =>
      result.values.forall(_.isInstanceOf[Learned])
    }, false, true)
  }

  /**
    * Looks spurious (probably due to some couchdb issues while the test was being run
    */
  "debug@2.0.0 against autocode-js@0.19.0" should "run" in {
    runEvolution("debug@2.0.0", List(), "autocode-js@0.19.0", { result =>
      result.values.forall(_.isInstanceOf[Learned])
    }, false, true)
  }

  /**
    * Issue with mocha interpreting options file as a test, no longer an issue with the NODE_OPTIONS trick
    */
  "debug@2.3.3 against dns-transmit-service@1.0.0" should "run" in {
    runEvolution("debug@2.3.3", List(), "dns-transmit-service@1.0.0", { result =>
      result.values.forall(_.isInstanceOf[Learned])
    }, false, true)
  }

  /**
    * Spurious timeout error
    * Update: Not always reproducible so the test is now marked as Learned
    */
  "debug@2.3.3 against express-filebin@0.0.1" should "run" in {
    runEvolution("debug@2.3.3", List(), "express-filebin@0.0.1", { result =>
      result.values.forall(_.isInstanceOf[Learned])
    }, false, true)
  }

  /**
    * Spurious problem
    * Update: Marked as Learned
    */
  "debug@2.4.1 against bourse@1.4.0" should "run" in {
    runEvolution("debug@2.4.1", List(), "bourse@1.4.0", { result =>
      result.values.forall(_.isInstanceOf[Learned])
    }, false, true)
  }

  /**
    * Cannot understand what is the problem here
    */
  //  "lodash@4.5.1 against mock-nodemailer@0.0.1" should "run" in {
  //    runEvolution("lodash@4.5.1", List(), "mock-nodemailer@0.0.1", { result =>
  //      result.values.forall(_.isInstanceOf[LearningFailure])
  //    }, false, true)
  //  }

  /**
    * Spurious problem
    * Update: Marked as Learned
    */
  "lodash@4.13.1 against koa-error@2.1.0" should "run" in {
    runEvolution("lodash@4.13.1", List(), "koa-error@2.1.0", { result =>
      result.values.forall(_.isInstanceOf[Learned])
    }, false, true)
  }

  /**
    * deepStrictEqual does not work when one of the objects is a proxy.
    *
    * No longer a problem with the unproxification of __proto__
    */
  "lodash@4.0.0 against javascript-web-client@1.0.0" should "run" in {
    runEvolution("lodash@4.0.0", List(), "javascript-web-client@1.0.0", { result =>
      result.values.forall(_.isInstanceOf[Learned])
    }, false, true)
  }

  /**
    * Spurious (tests are already failing before proxification)
    */
  "lodash@4.0.0 against koa-bouncer@5.0.1" should "run" in {
    runEvolution("lodash@4.0.0", List(), "koa-bouncer@5.0.1", { result =>
      result.values.forall(_.isInstanceOf[LearningFailure])
    }, false, true)
  }

  /**
    * A linter rejects the preamble_proxy file
    *
    * Solved by the fact that in runtime-hacks the getPrototypeOf is unproxifying the object
    */
  "lodash@4.0.0 against eslint-plugin-inflection@1.1.1" should "run" in {
    runEvolution("lodash@4.0.0", List(), "eslint-plugin-inflection@1.1.1", { result =>
      result.values.forall(_.isInstanceOf[Learned])
    }, false, true)
  }

  "lodash@4.14.1 against mocha-tcov-reporter@1.1.4" should "run" in {
    runEvolution("lodash@4.14.1", List("4.14.2"), "mocha-tcov-reporter@1.1.4", { result =>
      result.values.forall(_.isInstanceOf[Learned])
    }, false, true)
  }

  /**
    * There is an issue introduced in Backbone@1.2.2 https://github.com/jashkenas/backbone/issues/3778
    * which seeems to be detected by this client.
    * We observe the read of id attribute in the post version
    * Note, this problem is no longer detected after fixing a bug in proxification mechanism
    */
  "backbone@1.2.2->1.2.3 against solidstate@0.1.1" should "run" in {
    runEvolution("backbone@1.2.2", List("1.2.3"), "solidstate@0.1.1", { result =>
      result.values.forall(_.isInstanceOf[Learned])
    }, false, true)
  }

  "debug@2.3.3 against clustermq@1.1.0" should "run" in {
    runEvolution("debug@2.3.3", List(), "clustermq@1.1.0", { result =>
      result.values.forall(_.isInstanceOf[Learned])
    }, false, true)
  }

  /**
    * We should verify that our infrastructure correctly detect that
    * debug 2.4.0 cause any client test to fail
    */
  "debug@2.0.0 and 2.4.0 against bpium-node-record-model@0.1.0" should "run" in {
    runEvolution("debug@2.0.0", List("2.4.0"), "bpium-node-record-model@0.1.0", {
      result =>
        result("debug@2.0.0").isInstanceOf[LearningFailure] &&
        result("debug@2.4.0").isInstanceOf[LearningFailure]
    }, false, true)
  }

  /**
    * There seems to be an issue where the test cases does not call the done callback when assertions fail.
    */
  "lodash@4.0.0->4.0.1 node-ember-cli-deploy-redis@0.4.0" should "run" in {
    runEvolution("lodash@4.0.0", List("4.0.1"), "node-ember-cli-deploy-redis@0.4.0", {
      result =>
        result.values.forall(_.isInstanceOf[Learned])
    }, false, true)
  }

  "lodash@4.0.0 against mock-nodemailer@0.0.1" should "run" in {
    runEvolution("lodash@4.0.0", List(), "mock-nodemailer@0.0.1", { result =>
      result("lodash@4.0.0").isInstanceOf[Learned]
    }, false, true)
  }

  /**
    * of the long timeouts. For this reason it is commented out.
    */
  //  "lodash@4.6.1 against mock-nodemailer@0.0.1" should "run" in {
  //    runEvolution("lodash@4.6.1", List(), "mock-nodemailer@0.0.1", { result =>
  //      result("lodash@4.6.1").isInstanceOf[LearningFailure]
  //    }, false, true)
  //  }

  /**
    * Debug Function.prototype.toString is not generic error
    */
  "lodash@4.4.0 against cache-crusher@0.5.2" should "run" in {
    runEvolution("lodash@4.0.0", List(), "cache-crusher@0.5.2", { result =>
      result.values.forall(_.isInstanceOf[Learned])
    }, false, true)
  }

  /**
    * The following should be the only diff
    *
    * require(undefined).Router.(0).(3).arg2.(1).arg0
    *
    * Rather it seems like result of the ternary expression
    **
    *var layerError = err === 'route'
    * ? null
    * : err;
    **
    *changes branch when err goes from 'route' to undefined
    *
    * hence the receiver of the function passed as "recover" was undefined.
    * In the new version it is an object because the function is no longer invoked asynchronously.
    *
    */
  "express@4.10.8 and 4.11.0 against express-resourceful.js@0.1.0" should "run" in {
    runEvolution("express@4.10.8", List("4.11.0"), "express-resourceful.js@0.1.0", {
      result =>
        val diff = mkDiff(
          "express-resourceful.js@0.1.0",
          "express@4.10.8",
          "express@4.11.0",
          result)

        val postDiff = diff("express@4.11.0")
        postDiff.paths.forall {
          case (accessPath, pathDiff) =>
            val ap = accessPath.mkString(".")
            //log.info(s"DIFF: $ap -> ${pathDiff.getClass}")
            pathDiff match {
              case NoObservationInPost(obs, _) =>
                false
              case NoObservationInBase(obs, _) =>
                false
              case DifferentObservations(types) =>
                false
              case RelationFailure(_, _) => {
                ap == "require(express).Router.(0).(3).arg2.(1).arg0"
              }
            }
        } && postDiff.paths.nonEmpty
    }, false, true)
  }

  /**
    * Verifying this diff
    *
    * .require(undefined).static.(2).arg1.Symbol(get-prototype-of).* : No observation in post
    *
    * The reason of the diff has to be found in a changed dependency of express@4.0.0: serve-static
    *
    * Line
    *
    * options = options || {};
    *
    * has been changed to
    *
    * options = extend({}, options);
    *
    * extend uses reflection, and hence we get all accesses.
    *
    *
    * The options object is instrumented though this call stack
    *
    * express['static'](..., {clientMaxAge: -1000 * 60 * 60 * 24})
    *
    * where
    *
    *   exports.static = require('serve-static');
    *
    * in the express module
    *
    *
    */
  "express@4.0.0 to 4.1.0 against html2pdf.it@1.7.0" should "run" in {
    runEvolution("express@4.0.0", List("4.1.0"), "html2pdf.it@1.7.0", {
      result =>
        val diff = mkDiff("html2pdf.it@1.7.0", "express@4.0.0", "express@4.1.0", result)
        val postDiff = diff("express@4.1.0")
        postDiff.paths.forall {
          case (accessPath, pathDiff) =>
            val ap = accessPath.mkString(".")
            //log.info(s"DIFF: $ap -> ${pathDiff.getClass}")
            pathDiff match {
              case RelationFailure(_, _) =>
                ap == "require(express).static.(2).arg1.Symbol(get-prototype-of)" || // This is due to the extend
                  // This is also read due to extend (clientMaxAge was always a parameter coming from the client)
                  // clientMaxAge never had any effect. It seems like the client meant to set the maxAge property, but instead mistyped it as clientMaxAge.
                  // Only by chance is it later read by extend since it iterates all properties on an object.
                  ap == "require(express).static.(2).arg1.clientMaxAge"
              case _ => false
            }
        } && postDiff.paths.nonEmpty
    }, false, true)
  }

  /**
    * This one is really hard to debug since the origin of require(express).(0).use.(1).(2).arg0.next is not clear from the stack trace,
    * and the express code which has changed (router/index.js#proto.handle) requires a lot of domain specific knowledge to understand.
    * The best hint is that the changelog includes the line
    * fix `req.next` when inside router instance
    *  indicating that something has changed in regards to how .next is used.
    */
  "express@4.1.2 and 4.2.0 against sutro@0.0.4" should "run" in {
    runEvolution(
      "express@4.1.2",
      List("4.2.0"),
      "sutro@0.0.4", { result =>
        val diff = mkDiff("sutro@0.0.4", "express@4.1.2", "express@4.2.0", result)

        val postDiff = diff("express@4.2.0")
        postDiff.paths.forall {
          case (accessPath, pathDiff) =>
            val ap = accessPath.mkString(".")
            //log.info(s"DIFF: $ap -> ${pathDiff.getClass}")
            pathDiff match {
              case RelationFailure(_, _) =>
                ap == "require(express).(0).use.(1).(2).arg0.next"
              case _ => false
            }
        } && postDiff.paths.nonEmpty
      },
      false,
      true,
      true)
  }

  /**
    *
    * Dispatch a req, res into the router.
    * proto.handle = function(req, res, done) {
    *    var parentUrl = req.baseUrl || "";  <-- has been added
    *
    * All these cases of new options added with default values appears as instance of properties
    * that were not read in the previous version and are now read with value "undefined" plus something else.
    *
    * In this case this is the diff we get:
    *    .require(express).(0).(2).arg0.baseUrl: No observation in base(undefined,string)
    *
    * The only case of breaking change that we can handle is when these added properties are used
    * without being guarded against being undefined. Some of the uses, such as property accesses, would
    * throw an exception, hence the test itself would be able to detect the breaking change without our tool.
    *
    * The remaining breaking changes instaces are when:
    *
    *  - the value is passed to a native function (that doesn't throw) or operator
    *  - the value is coerced
    *
    * We can either:
    *  - ignore these cases alltogether, by not raising a warning for read property that is now read as undefined
    *  - try to detect the possible breaking changes arising from this scenario. This may require to instrument
    *    all operators and all native function call to understand when one of such undefined value passed to native
    *    or coerced.
    *
    */
  "express@4.2.0 and 4.3.0 against express-rest-generator@1.0.3" should "run" in {
    runEvolution(
      "express@4.2.0",
      List("4.3.0"),
      "express-rest-generator@1.0.3", { result =>
        val diff =
          mkDiff("express-rest-generator@1.0.3", "express@4.2.0", "express@4.3.0", result)
        val postDiff = diff("express@4.3.0")
        //log.info("Diff: " + postDiff)
        postDiff.paths.exists(p =>
          p._1.mkString(".") == "require(express).(0).(2).arg0.baseUrl" && (p._2 match {
            case RelationFailure(_, ttype) =>
              ttype.contains("String") && ttype.contains("v") && ttype.contains(
                "Undefined")
            case _ => false
          })) && postDiff.paths.nonEmpty
      },
      false,
      true,
      true)
  }

  /**
    * There are two interesting observations about this change.
    *
    * 1. The _headers.etag changes type from undefined to string.
    *    It happens because express decides to always include an etag.
    *    Before, the etag was only set when the length of the request exceed 1024
    *    This change is described in the changelog.
    *
    * 2. The _headers field is probably private, yet, it's read.
    *    The reason for this is that it's a field added to a client created object
    *    I.e., the require(express).(0).(2).arg1 object is public and coming from the client,
    *    but _headers is added to this object by the library.
    *    The field is then read by a third party library `freshness` which is used to determine the validity
    *    of cached contents.
    *
    * Conclusions: It is probably at best a mild breaking change, and we would not make the observation
    * if we blacklisted properties added to proxies.
    *
    */
  "express@3.4.4 and 3.4.5 against webhooks@0.0.1" should "run" in {
    runEvolution("express@3.4.4", List("3.4.5"), "webhooks@0.0.1", {
      result =>
        val diff =
          mkDiff("webhooks@0.0.1", "express@3.4.4", "express@3.4.5", result)
        val postDiff = diff("express@3.4.5")
        //log.info("Diff: " + postDiff)
        postDiff.paths.forall {
          case (accessPath, pathDiff) =>
            val ap = accessPath.mkString(".")
            //log.info(s"DIFF: $ap -> ${pathDiff.getClass}")
            pathDiff match {
              case NoObservationInPost(obs, _) =>
                false
              case NoObservationInBase(obs, _) =>
                false
              case DifferentObservations(types) =>
                false
              case RelationFailure(_, _) =>
                ap == "require(express).(0).(2).arg1._headers.etag"
            }
        } && postDiff.paths.nonEmpty
    }, false, true, true)
  }

  /**
    * .require(express).(0).(2).arg0.upgrade: No observation in base
    *
    * observation is performed in server-components-express_0.1.0/node_modules/on-finished/index.js:75:24
    *
    *  changelog from on-finished@2.3.0
    *
    *  if (typeof msg.complete === 'boolean') {
    *      // IncomingMessage
    * -    return Boolean(!socket || !socket.readable || (msg.complete && !msg.readable))
    * +    return Boolean(msg.upgrade || !socket || !socket.readable || (msg.complete && !msg.readable))
    *    }
    *
    */
  "express@4.12.4 and 4.13.0 against server-components-express@0.1.0" should "run" in {
    runEvolution(
      "express@4.12.4",
      List("4.13.0"),
      "server-components-express@0.1.0", { result =>
        val diff =
          mkDiff(
            "server-components-express@0.1.0",
            "express@4.12.4",
            "express@4.13.0",
            result)
        val postDiff = diff("express@4.13.0")
        //log.info(postDiff)
        true
      },
      false,
      true,
      true)
  }

  "lodash@3.0.0 against sto-sis-time-parser@1.0.9" should "run" in {
    runEvolution("lodash@3.0.0", List(), "sto-sis-time-parser@1.0.9", { result =>
      true
    }, false, true, true)
  }

  /**
    * Tests failed with assertion error:
    * AssertionError: expected Object { a: 1 } to equal Object { a: 1 } (because A and B have different prototypes)
    *   at Assertion.fail (node_modules/should/cjs/should.js:275:17)
    *
    * The comparison was done between an object and a proxy of the same object.
    * It should no longer fail, after that Object.getPrototypeOf has been replaced by a function that automatically unproxify the argument
    *
    */
  "lodash@3.9.2 against save@0.0.14" should "run" in {
    runEvolution("lodash@3.9.2", List(), "save@0.0.14", { result =>
      result.forall(_._2.isInstanceOf[Learned])
    }, false, true, false)
  }

  /**
    * This client give a variable number of observations at runtime, therefore generating noise
    */
  "lodash@3.0.0 against express-endpoint@2.0.0" should "run" in {
    runEvolution("lodash@3.0.0", List("3.0.1"), "express-endpoint@2.0.0", { result =>
      val diff =
        mkDiff("express-endpoint@2.0.0", "lodash@3.0.0", "lodash@3.0.1", result)
      val postDiff = diff("lodash@3.0.1")
      postDiff.diffObservationCount == 0
    }, false, true, false)
  }

  /**
    * Breaking change!
    * A rework on lodash@3.3.0 causes isIterateeCall to now call toString on the arguments.
    * We observe this through an internal call where the argument is exposed to the client as
    *
    *   .require(lodash).defaults.(3).arg2
    *
    * the bug is fixed in 3.3.1
    *
    *  as also our observations confirm.
    *
    *  From the changelog:
    *
    *     Ensured isIterateeCall doesn’t error if index is missing a toString method
    *
    * I only mark that we observe the call, in case we want later discard properties not read in base/post.
    */
  "lodash@3.2.0 and 3.3.0 against the-works@2.1.0" should "run" in {
    runEvolution(
      "lodash@3.2.0",
      List("3.3.0", "3.3.1"),
      "the-works@2.1.0", { result =>
        val diff1 =
          mkDiff("the-works@2.1.0", "lodash@3.2.0", "lodash@3.3.0", result)
        val postDiff = diff1("lodash@3.3.0")

        val diff2 =
          mkDiff("the-works@2.1.0", "lodash@3.3.0", "lodash@3.3.1", result)
        val postDiff2 = diff2("lodash@3.3.1")
        //log.info(diff2)
        postDiff.paths.exists(p =>
          p._1.mkString(".") == "require(lodash).defaults.(3).arg2.toString.(0)" && (p._2 match {
            case NoObservationInBase(_, s) => false
            case RelationFailure(_, ttype) => ttype.contains("String")
            case _                         => false
          }))
      },
      false,
      true,
      false)
  }

  /*
  "lodash@3.0.0 against soap-noendlessloop@0.15.0" should "run" in {
    runEvolution("lodash@3.0.0", List(), "soap-noendlessloop@0.15.0", { result =>
      true
    }, false, true, false)
  }
   */

  /**
    * List(require(lodash), assign, (2), arg0, b):
    *   lodash 4.0.0 reads the destination object (arg0) properties, whereas lodash 3.* was getting its keys.
    *
    *  //Under investigation
    *
    */
  "lodash@3.10.1 against gulp-text-simple@0.2.1" should "run" in {
    runEvolution("lodash@3.10.1", List("4.0.0"), "gulp-text-simple@0.2.1", { result =>
      val diff =
        mkDiff("gulp-text-simple@0.2.1", "lodash@3.10.1", "lodash@4.0.0", result)
      val postDiff = diff("lodash@4.0.0")
      //log.info(postDiff)
      result.forall(_._2.isInstanceOf[Learned])
    }, false, true, false)
  }

  /**
    * Breaking change
    *
    * path
    *   require(lodash), merge, (4), arg3, [object Object]
    *
    * appears to be read, which is clearly a consequence of a toString called.
    * The observed path disappear in 3.3.1.
    *
    * The root cause is the isIterateeCall as in lodash@3.2.0 and 3.3.0 against the-works@2.1.0
    *
    */
  "lodash@3.2.0 to 3.3.0 against strong-params@0.7.0" should "run" in {
    runEvolution(
      "lodash@3.2.0",
      List("3.3.0"),
      "strong-params@0.7.0", { result =>
        val diff =
          mkDiff("strong-params@0.7.0", "lodash@3.2.0", "lodash@3.3.0", result)
        val postDiff = diff("lodash@3.3.0")

        result.forall(_._2.isInstanceOf[Learned]) &&
        postDiff.paths.exists(
          _._1.mkString(".") == "require(lodash).merge.(4).arg3.[object Object]")

      },
      false,
      true,
      false)
  }

  /**
    * Function require(lodash).pluck has been removed in lodash 4.0.0
    */
  /**
    * The tests are failing here so we observe the breaking change as a lot of NoObservationInPost.
    * However, these missing obeservations are not all directly connected to the breaking change.
    * It would be nice to have another client find the breaking change without its tests failing.
    * However, there are no other validate clients on NPM or on Github.
    */
//  "validate@2.1.2 and 2.1.3 against rhom@0.2.5" should "run" in {
//    runEvolution(
//      "validate@2.1.2",
//      List("2.1.3"),
//      "rhom@0.2.5", { result =>
//        val diff = mkDiff("rhom@0.2.5", "validate@2.1.2", "validate@2.1.3", result)
//
//        val postDiff = diff("validate@2.1.3")
//
//        postDiff.paths.forall {
//          case (accessPath, pathDiff) =>
//            val ap = accessPath.mkString(".")
//            log.info(s"DIFF: $ap -> ${pathDiff.getClass}")
//            pathDiff match {
//              case NoObservationInPost(obs, _) =>
//                false
//              case NoObservationInBase(obs, _) =>
//                false
//              case DifferentObservations(types) =>
//                true
//            }
//        } //&& postDiff.paths.length == 16
//      },
//      false,
//      true,
//      true,
//      smartAugmentation = true)
//  }

  /**
    * Undetectable. The error is in private API.
    * There are also issue with installations which are not fixed by the ignore problems in installation option.
    */
  "wav-encoder@1.0.0 and 1.1.0 against ciseaux@0.4.0" should "run" in {
    runEvolution("wav-encoder@1.0.0", List("1.1.0"), "ciseaux@0.4.0", { result =>
      val diff = mkDiff("ciseaux@0.4.0", "wav-encoder@1.0.0", "wav-encoder@1.1.0", result)

      val postDiff = diff("wav-encoder@1.1.0")

      postDiff.paths.foreach {
        case (accessPath, pathDiff) =>
          val ap = accessPath.mkString(".")
        //log.info(s"DIFF: $ap -> ${pathDiff.getClass}")
      }

      true
    }, false, true, true, ignoreFailingInstallations = true)
  }

  /**
    * The ConverterMap function is removed in the update of cli-compiler.
    */
  "cli-compiler@0.1.21 and 0.1.22 against cli-system@0.1.16" should "run" in {
    runEvolution("cli-compiler@0.1.21", List("0.1.22"), "cli-system@0.1.16", {
      result =>
        val diff = mkDiff(
          "cli-system@0.1.16",
          "cli-compiler@0.1.21",
          "cli-compiler@0.1.22",
          result)

        val postDiff = diff("cli-compiler@0.1.22")

        postDiff.paths.forall {
          case (accessPath, pathDiff) =>
            val ap = accessPath.mkString(".")
            //log.info(s"DIFF: $ap -> ${pathDiff.getClass}")
            pathDiff match {
              case NoObservationInPost(obs, _) =>
                false
              case NoObservationInBase(obs, _) =>
                false
              case DifferentObservations(types) =>
                false
              case RelationFailure(_, ttype) => {
                ap == "require(cli-compiler).ConverterMap" && ttype
                  .contains("Undefined") && ttype.contains("Function")
              }
            }
        } && postDiff.paths.nonEmpty
    }, false, true, true, ignoreFailingInstallations = true, smartAugmentation = false)
  }

  /**
    * There is a breaking introduced in version 0.2.8 of buffer-crc32.
    *
    * We tried looking for other clients to expose the error without failing,
    * but unfortunately all of these clients either do not reveal the error, or
    * only reveal the error due the failing tests which implies "throws" observations in post.
    *
    * It is unclear if the error is semantic or API related
    */
  "buffer-crc32@0.2.7 and 0.2.8 against crc32-stream@0.3.3" should "run" in {
    runEvolution(
      "buffer-crc32@0.2.7",
      List("0.2.8"),
      "crc32-stream@0.3.3", { result =>
        val diff =
          mkDiff("crc32-stream@0.3.3", "buffer-crc32@0.2.7", "buffer-crc32@0.2.8", result)

        result.values.forall(_.isInstanceOf[Learned])

        val postDiff = diff("buffer-crc32@0.2.8")
        //log.info(postDiff)
        postDiff.paths.forall {
          case (accessPath, pathDiff) =>
            val ap = accessPath.mkString(".")
            //log.info(s"DIFF: $ap -> ${pathDiff.getClass}")
            pathDiff match {
              case RelationFailure(_, ttype) =>
                ttype.contains("Throws")
              case _ => false
            }
        } //&& postDiff.paths.length == 7
      },
      false,
      true,
      true)
  }

  /**
    * The error is due to missing modules and are therefore only detected by failing tests
    * which of course implies missing observations.
    */
  "bip32-utils@0.4.2 and 0.4.3 against cb-wallet@0.10.1" should "run" in {
    runEvolution("bip32-utils@0.4.2", List("0.4.3"), "cb-wallet@0.10.1", { result =>
      val diff =
        mkDiff("cb-wallet@0.10.1", "bip32-utils@0.4.2", "bip32-utils@0.4.3", result)

      val postDiff = diff("bip32-utils@0.4.3")
      true
    }, false, true, true)
  }

//  /**
//    *
//    */
//  "uglify-js@2.4.15 and 2.4.16 against plumber-uglifyjs@0.2.2" should "run" in {
//    runEvolution(
//      "uglify-js@2.4.15",
//      List("2.4.16"),
//      "plumber-uglifyjs@0.2.2", { result =>
//        val diff =
//          mkDiff("plumber-uglifyjs@0.2.2", "uglify-js@2.4.15", "uglify-js@2.4.16", result)
//
//        val postDiff = diff("uglify-js@2.4.16")
//        true
//      },
//      collectStackTraces = true,
//      detailedStackTraces = true,
//      ignoreFailingInstallations = false)
//  }

  /**
    * Due to some odd behavior def.references instanceof Array on line 82 in analyze.js fails for linty@2.4.24 but succeeds in 2.5.0
    * Strangely, it seems like def.references.__proto__.toString refers to the native toString method in 2.4.24,
    * but Array.prototype.toString refers to the one we set in runtime hacks.
    * This indicates that def.references.__proto__ is not the same object as Array.prototype in 2.4.24, but why?
    */
  "uglify-js@2.4.24 and 2.5.0 against linty@0.1.0" should "run" in {
    runEvolution("uglify-js@2.4.24", List("2.5.0"), "linty@0.1.0", { result =>
      val diff =
        mkDiff("linty@0.1.0", "uglify-js@2.4.24", "uglify-js@2.5.0", result)

      val postDiff = diff("uglify-js@2.5.0")
      postDiff.paths.nonEmpty && postDiff.paths.forall {
        case (_, diff) => diff.isInstanceOf[RelationFailure]
      }
    }, false, true, false)
  }

  /**
    * Non-breaking extra check on value in lodash@4.0.0
    * The value must be of type object and it is only checked if object has property toString.
    * The comment above the change mentions IE-9 compatibility as the reason for the check.
    * It is unclear if it is a semantic breaking change.
    *
    * The extend case seems to be similar to the _.defaults case described below.
    * However, unlike the _.defaults case, it does not seem to cause any breaking changes
    */
  "lodash@3.10.1 and 4.0.0 against save@0.0.14" should "run" in {
    runEvolution("lodash@3.10.1", List("4.0.0"), "save@0.0.14", {
      result =>
        val diff =
          mkDiff("save@0.0.14", "lodash@3.10.1", "lodash@4.0.0", result)

        val postDiff = diff("lodash@4.0.0")
        postDiff.paths.forall {
          case (accessPath, pathDiff) =>
            val ap = accessPath.mkString(".")
            //log.info(s"DIFF: $ap -> ${pathDiff.getClass}")
            pathDiff match {
              case RelationFailure(_, ttype) =>
                (ap == "require(lodash).clone.(1).arg0.toString" && ttype.contains(
                  "Function") && ttype.contains("Object")) ||
                  (ap.startsWith("require(lodash).extend"))
              case _ => false
            }
        } && postDiff.paths.nonEmpty
    }, false, true, true)
  }

  /**
    * This is a change of the _.defaults function which will assign default values to properties that resolve to undefined.
    * In 3.x.x defaults would just check if the value was undefined and then assign the default value in that case.
    * In 4.0.0 the behavior was updated such that defaults reads the property value from the object itself and the object containing the default value.
    * Lodash then compares these two values and in cases where the original value is different from undefined Lodash reassigns the value back to the object.
    * This has the effect that the property value is proxified in 4.0.0 but not in 3.x.x which explains why there are more observations.
    *
    * The reason for this change is that there are cases where the default value is picked even if the property value is different from undefined ... elaborate.
    * For example, if the property value is on the prototype, then the default value is picked anyway.
    * In lodash@3.10.1 the `o` object in this example would eventuall be { } with o.__proto__.a == 42
    * In lodash@4.0.0 the `o` object in the same example would eventually be { a : 'foo'} with o.__proto__.a == 42
    *
    *    var o = { };
    *    var oP = Object.getPrototypeOf(o).a = 42;
    *
    *    var a = { a : 'foo'};
    *    lodash.defaults(o, a);
    *
    * *
    */
  "lodash@3.10.1 and 4.0.0 against shipit-cli@1.1.0" should "run" in {
    runEvolution(
      "lodash@3.10.1",
      List("4.0.0"),
      "shipit-cli@1.1.0", { result =>
        val diff =
          mkDiff("shipit-cli@1.1.0", "lodash@3.10.1", "lodash@4.0.0", result)

        val postDiff = diff("lodash@4.0.0")
        postDiff.paths.forall {
          case (accessPath, pathDiff) =>
            val ap = accessPath.mkString(".")
            //log.info(s"DIFF: $ap -> ${pathDiff.getClass}")
            pathDiff match {
              case RelationFailure(_, _) =>
                ap.startsWith("require(lodash).defaults") ||
                  ap.startsWith("require(lodash).extend") //Investigated above
              case _ => false
            }
        } && postDiff.paths.nonEmpty
      },
      false,
      true,
      true)
  }

  /**
    * Only the .each case is investigated here.
    * The other observations are likely due the premature termination caused by the exception thrown by each.
    *
    * This is a major breaking change.
    * Note that .each is an alias for .forEach
    *
    * In lodash@3.10.1 the .each method had the following API.
    * _.forEach(collection, [iteratee=_.identity], [thisArg])
    * where the thisArg would be used as the thisArg of iteratee.
    *
    * However, in lodash@4.0.0 the option of setting the thisArg was removed.
    * This means that clients that depend on this now see undefined instead of whatever they put as the thisArg.
    *
    *
    */
  "lodash@3.10.1 and 4.0.0 against songdown-compiler@0.2.5" should "run" in {
    runEvolution("lodash@3.10.1", List("4.0.0"), "songdown-compiler@0.2.5", {
      result =>
        val diff =
          mkDiff("songdown-compiler@0.2.5", "lodash@3.10.1", "lodash@4.0.0", result)

        val postDiff = diff("lodash@4.0.0")
        postDiff.paths.forall {
          case (accessPath, pathDiff) =>
            val ap = accessPath.mkString(".")
            log.info(s"DIFF: $ap -> ${pathDiff.getClass}")
            pathDiff match {
              case RelationFailure(_, _) =>
                ap.startsWith("require(lodash).map") ||
                  ap.startsWith("require(lodash).first") ||
                  ap.startsWith("require(lodash).filter") ||
                  ap.startsWith("require(lodash).size") ||
                  ap.startsWith("require(lodash).each") ||
                  ap.startsWith("require(lodash).clone") ||
                  ap.startsWith("require(lodash).each")
              case _ => false
            }
        } && postDiff.paths.nonEmpty
    }, false, true, true)
  }

  /**
    * This case is similar to the .each case of the songdown-compiler benchmark
    * They have removed the option of setting the thisArg of the iteratee.
    */
  "lodash@3.10.1 and 4.0.0 against tobase64-json@2.0.0" should "run" in {
    runEvolution("lodash@3.10.1", List("4.0.0"), "tobase64-json@2.0.0", {
      result =>
        val diff =
          mkDiff("tobase64-json@2.0.0", "lodash@3.10.1", "lodash@4.0.0", result)

        val postDiff = diff("lodash@4.0.0")
        postDiff.paths.forall {
          case (accessPath, pathDiff) =>
            val ap = accessPath.mkString(".")
            log.info(s"DIFF: $ap -> ${pathDiff.getClass}")
            pathDiff match {
              case RelationFailure(_, _) =>
                ap.startsWith("require(lodash).filter") ||
                  ap.startsWith("require(lodash).each") || //due to premature termination
                  ap.startsWith("require(lodash).filter")
              case _ => false

            }
        } && postDiff.paths.nonEmpty
    }, false, true, true)
  }

  /**
    * We only investigate the .isEmpty case. The .pluck is just a removed function.
    *
    * They change the function such that it checks for the .splice property on the argument
    * to determine if the argument is an array.
    * It's only used if the argument is a non-null object so no type errors can be thrown.
    * It's only breaking if .splice is a getter with side-effects.
    */
  "lodash@3.10.1 and 4.0.0 against loopback-ds-changed-mixin@1.0.1" should "run" in {
    runEvolution(
      "lodash@3.10.1",
      List("4.0.0"),
      "loopback-ds-changed-mixin@1.0.1", { result =>
        val diff =
          mkDiff(
            "loopback-ds-changed-mixin@1.0.1",
            "lodash@3.10.1",
            "lodash@4.0.0",
            result)

        val postDiff = diff("lodash@4.0.0")
        postDiff.paths.forall {
          case (accessPath, pathDiff) =>
            val ap = accessPath.mkString(".")
            log.info(s"DIFF: $ap -> ${pathDiff.getClass}")
            pathDiff match {
              case RelationFailure(_, _) =>
                ap.startsWith("require(lodash).pluck") || //removed function
                  ap.startsWith("require(lodash).isEmpty")
              case _ => false
            }
        } && postDiff.paths.nonEmpty
      },
      false,
      true,
      true)
  }

  /**
    * isEqual uses the isHost function in 4.0.0 to check if an update is a host object (roughly if it's non-native).
    * isHost reads the toString property of its argument which is why we seed the additional observation.
    * Reading toString is not directly breaking, and it is unclear if the modification of isEqual has caused
    * any other breakages.
    */
  "lodash@3.10.1 and 4.0.0 against mock-nodemailer@0.0.1" should "run" in {
    runEvolution(
      "lodash@3.10.1",
      List("4.0.0"),
      "mock-nodemailer@0.0.1", { result =>
        val diff =
          mkDiff("mock-nodemailer@0.0.1", "lodash@3.10.1", "lodash@4.0.0", result)

        val postDiff = diff("lodash@4.0.0")
        postDiff.paths.forall {
          case (accessPath, pathDiff) =>
            val ap = accessPath.mkString(".")
            log.info(s"DIFF: $ap -> ${pathDiff.getClass}")
            pathDiff match {
              case RelationFailure(_, _) =>
                ap.startsWith("require(lodash).isEqual") ||
                  ap.startsWith("require(lodash).clone") //Not investigated here
              case _ => false
            }
        } && postDiff.paths.nonEmpty
      },
      false,
      true,
      true)
  }

  /**
    * isPlainObject also start to call isHostObject in lodash@4.0.0
    * which is why we suddenly see observations of .toString reads.
    *
    * See the `lodash@3.10.1 and 4.0.0 against mock-nodemailer@0.0.1` for a more through description.
    */
  "lodash@3.10.1 and 4.0.0 against create-error@0.1.0" should "run" in {
    runEvolution(
      "lodash@3.10.1",
      List("4.0.0"),
      "create-error@0.1.0", { result =>
        val diff =
          mkDiff("create-error@0.1.0", "lodash@3.10.1", "lodash@4.0.0", result)

        val postDiff = diff("lodash@4.0.0")
        postDiff.paths.forall {
          case (accessPath, pathDiff) =>
            val ap = accessPath.mkString(".")
            log.info(s"DIFF: $ap -> ${pathDiff.getClass}")
            pathDiff match {
              case RelationFailure(_, _) =>
                ap.startsWith("require(lodash).isPlainObject")
              case _ => false
            }
        } && postDiff.paths.nonEmpty
      },
      false,
      true,
      true)
  }

  /**
    * In lodash@3.10.1 uniq supported supplying a custom callback for determining uniqueness.
    * That functionality was moved to a separate function uniqBy in lodash@4.0.0.
    * Consequently, the array returned by uniq in _@4.0.0 is different from the one
    * returned by uniq i @3.10.1 for the gulp-dashboard client.
    *
    * This breaking change is extra strange since the documentation says that
    * the callback must be supplied as a third argument, but gulp-dashboard passes
    * it as a second argument instead. Lodash, however, seems to handle this behavior even though it is undocumented.
    *
    * Also, note that gulp-dashboard passes a string and not a function as the callback.
    * Lodash automatically interprets this as a callback doing a comparsion on the property equal to that string.
    */
  "lodash@3.10.1 and 4.0.0 against gulp-dashboard@1.0.0" should "run" in {
    runEvolution("lodash@3.10.1", List("4.0.0"), "gulp-dashboard@1.0.0", {
      result =>
        val diff =
          mkDiff("gulp-dashboard@1.0.0", "lodash@3.10.1", "lodash@4.0.0", result)

        val postDiff = diff("lodash@4.0.0")
        postDiff.paths.forall {
          case (accessPath, pathDiff) =>
            val ap = accessPath.mkString(".")
            log.info(s"DIFF: $ap -> ${pathDiff.getClass}")
            pathDiff match {
              case RelationFailure(_, _) =>
                ap.startsWith("require(lodash).uniq") ||
                  ap.startsWith("require(lodash).isEmpty") || //Not investigated here
                  ap.startsWith("require(lodash).extend") //Not investigated here
              case _ =>
                false
            }
        } && postDiff.paths.nonEmpty
    }, false, true, true)
  }

  /**
    * The implementation of isPlainObject changed slightly in 3.10.0
    * In the old version it used a fallback function called isPlainObjectShim if Object.getPrototypeOf was overwritten.
    * In the new version, the shim function is the default behavior.
    * The shim function has a quite odd mechanism where it iterates the properties of the object
    * and based on which property is iterated last, determines if the object is plain, i.e., does not have a prototype.
    *
    * It's probably a minor breaking change at most.
    */
  "lodash@3.9.3 and 3.10.0 against mailchecker@1.2.0" should "run" in {
    runEvolution(
      "lodash@3.9.3",
      List("3.10.0"),
      "mailchecker@1.2.0", { result =>
        val diff =
          mkDiff("mailchecker@1.2.0", "lodash@3.9.3", "lodash@3.10.0", result)

        val postDiff = diff("lodash@3.10.0")
        postDiff.paths.forall {
          case (accessPath, pathDiff) =>
            val ap = accessPath.mkString(".")
            log.info(s"DIFF: $ap -> ${pathDiff.getClass}")
            pathDiff match {
              case RelationFailure(_, _) =>
                ap.startsWith("require(lodash).isPlainObject")
              case _ => false
            }
        } && postDiff.paths.nonEmpty
      },
      false,
      true,
      true)
  }

  /**
    * Seems to be an instance of the same problem as lodash@3.9.3 and 3.10.0 against mailchecker@1.2.0
    * since merge calls isPlainObject
    */
  "lodash@3.9.3 and 3.10.0 against hercule@0.1.2" should "run" in {
    runEvolution("lodash@3.9.3", List("3.10.0"), "hercule@0.1.2", { result =>
      val diff =
        mkDiff("hercule@0.1.2", "lodash@3.9.3", "lodash@3.10.0", result)

      val postDiff = diff("lodash@3.10.0")
      // Gone after updated typing relation.
      postDiff.paths.isEmpty
    //postDiff.paths.forall {
    //  case (accessPath, pathDiff) =>
    //    val ap = accessPath.mkString(".")
    //    log.info(s"DIFF: $ap -> ${pathDiff.getClass}")
    //    pathDiff match {
    //      case NoObservationInPost(obs, _) =>
    //        ap.startsWith("require(lodash).merge")
    //      case NoObservationInBase(obs, _) =>
    //        false
    //      case DifferentObservations(types) =>
    //        false
    //    }
    //} && postDiff.paths.nonEmpty
    }, false, true, true)
  }

  /**
    * The constructor of value returned by moment.utc changes type from object to function.
    * Specifically, from the Object value to the Moment value.
    * It's reported as a missing observation in base and not a type mistmatch since the constructor is a "Known value" in version 2.2.1.
    * Normally, this wouldn't be reported as a breaking change by our tool since we have the function <: object relation,
    * but in this case it is reported and it is a breaking change as demonstrated by the failing client
    */
  "moment@2.1.0 and 2.2.1 against pgsubst@0.0.1" should "run" in {
    runEvolution(
      "moment@2.1.0",
      List("2.2.1"),
      "pgsubst@0.0.1", { result =>
        val diff =
          mkDiff("pgsubst@0.0.1", "moment@2.1.0", "moment@2.2.1", result)

        val postDiff = diff("moment@2.2.1")
        postDiff.paths.forall {
          case (accessPath, pathDiff) =>
            val ap = accessPath.mkString(".")
            log.info(s"DIFF: $ap -> ${pathDiff.getClass}")
            pathDiff match {
              case RelationFailure(_, _) =>
                ap.startsWith("require(moment).utc")
              case _ => false
            }
        } && postDiff.paths.nonEmpty
      },
      false,
      true,
      true)
  }

  /**
    * moment starts using hasOwnProperty on objects.
    * However, this method is not present on objects in IE8, so it could result in type errors.
    * https://github.com/moment/moment/pull/1874
    */
  "moment@2.5.0 and 2.5.1 against pgsubst@0.0.1" should "run" in {
    runEvolution(
      "moment@2.5.0",
      List("2.5.1"),
      "pgsubst@0.0.1", { result =>
        val diff =
          mkDiff("pgsubst@0.0.1", "moment@2.5.0", "moment@2.5.1", result)

        val postDiff = diff("moment@2.5.1")
        postDiff.paths.forall {
          case (accessPath, pathDiff) =>
            val ap = accessPath.mkString(".")
            log.info(s"DIFF: $ap -> ${pathDiff.getClass}")
            pathDiff match {
              case RelationFailure(_, _) =>
                ap.startsWith("require(moment).isMoment") ||
                  ap.startsWith("require(moment).(1).arg0")
              case _ =>
                false
            }
        } && postDiff.paths.nonEmpty
      },
      false,
      true,
      true)
  }

  /**
    * This is probably not a problem.
    * They check if an object is moment by reading a special _isAMomentObject key.
    * In the old version they used Object.prototype.hasOwnProperty.call instead,
    * which is not trapped by the has handler on proxies unfortunately,
    * so we spuriously report the property reads as warnings.
    * */
  "moment@2.10.2 and 2.10.3 against pgsubst@0.0.1" should "run" in {
    runEvolution(
      "moment@2.10.2",
      List("2.10.3"),
      "pgsubst@0.0.1", { result =>
        val diff =
          mkDiff("pgsubst@0.0.1", "moment@2.10.2", "moment@2.10.3", result)

        val postDiff = diff("moment@2.10.3")
        postDiff.paths.forall {
          case (accessPath, pathDiff) =>
            val ap = accessPath.mkString(".")
            log.info(s"DIFF: $ap -> ${pathDiff.getClass}")
            pathDiff match {
              case RelationFailure(_, _) =>
                ap.startsWith("require(moment).(1).arg0") ||
                  ap.startsWith("require(moment).isMoment")
              case _ => false
            }
        } && postDiff.paths.nonEmpty
      },
      false,
      true,
      true)
  }

  /**
    * Diff should be empty after fixing receiver problem with Date objects.
    */
  "moment@2.10.3 and 2.10.5 against pgsubst@0.0.1" should "run" in {

    runEvolution("moment@2.10.3", List("2.10.5"), "pgsubst@0.0.1", {
      result =>
        val diff =
          mkDiff("pgsubst@0.0.1", "moment@2.10.3", "moment@2.10.5", result)

        val postDiff = diff("moment@2.10.5")
        postDiff.paths.forall {
          case (accessPath, pathDiff) =>
            val ap = accessPath.mkString(".")
            log.info(s"DIFF: $ap -> ${pathDiff.getClass}")
            pathDiff match {
              case RelationFailure(_, _) =>
                ap.startsWith("require(moment).(1)") ||
                  ap.startsWith("require(moment).(1).arg0") ||
                  ap.startsWith("require(moment).(1).utc")
              case _ => false
            }
        } && postDiff.paths.nonEmpty
    }, false, true, true)
  }

  /**
    * Many async files were moved from the root to a build folder.
    * They are probably intended to be private,
    * but it still counts as a breaking change since a client depend upon the files being in the correct location.
    */
  "async@2.0.1 and 2.1.0 against aku-aku@1.0.1" should "run" in {
    runEvolution(
      "async@2.0.1",
      List("2.1.0"),
      "aku-aku@1.0.1", { result =>
        val diff =
          mkDiff("aku-aku@1.0.1", "async@2.0.1", "async@2.1.0", result)

        val postDiff = diff("async@2.1.0")
        postDiff.paths.forall {
          case (accessPath, pathDiff) =>
            val ap = accessPath.mkString(".")
            log.info(s"DIFF: $ap -> ${pathDiff.getClass}")
            pathDiff match {
              case RelationFailure(obs, _) =>
                ap.startsWith("require(async/asyncify)")
              case _ => false
            }
        } && postDiff.paths.nonEmpty
      },
      false,
      true,
      true)
  }

  /**
    * This is probably a false positive.
    * each is also exported on the ECMA 6 default object in the updated version,
    * which is apparently chosen with higher priority than the directly exported each.
    * Hence, all the observations also exists in the previous version, but without default in the path.
    */
  "async@2.1.1 and 2.1.2 against rollbar-sourcemap-webpack-plugin@1.2.1" should "run" in {
    runEvolution("async@2.1.1", List("2.1.2"), "rollbar-sourcemap-webpack-plugin@1.2.1", {
      result =>
        val diff =
          mkDiff(
            "rollbar-sourcemap-webpack-plugin@1.2.1",
            "async@2.1.1",
            "async@2.1.2",
            result)

        val postDiff = diff("async@2.1.2")
        postDiff.paths.forall {
          case (accessPath, pathDiff) =>
            val ap = accessPath.mkString(".")
            log.info(s"DIFF: $ap -> ${pathDiff.getClass}")
            pathDiff match {
              case RelationFailure(_, _) =>
                ap.startsWith("require(async).__esModule") || //Spurious
                  ap.startsWith("require(async).default") ||
                  ap.startsWith("require(async).each") //Read by other client
              case _ => false
            }
        } && postDiff.paths.nonEmpty
    }, false, true, true)
  }

  /**
    * Should produce empty diff after fixing problem with hasHandler
    */
  "async@2.1.2 and 2.1.4 against kj@1.0.1" should "run" in {
    runEvolution("async@2.1.2", List("2.1.4"), "kj@1.0.1", { result =>
      val diff =
        mkDiff("kj@1.0.1", "async@2.1.2", "async@2.1.4", result)

      val postDiff = diff("async@2.1.4")
      postDiff.paths.isEmpty
    }, false, true, true)
  }

  /**
    * Should produce empty diff since debug is never required by ssl-redirect.
    * This client was spuriously classifying debug@2.4.0 as non-breaking since one if its
    * dependencies was transitively requiring another version of debug.
    * This problem should no longer be present after not proxifying transitive dependencies.
    */
  "debug@2.3.3 and 2.4.0 against ssl-redirect@0.1.0" should "run" in {
    runEvolution("debug@2.3.3", List("2.4.0"), "ssl-redirect@0.1.0", { result =>
      val diff =
        mkDiff("ssl-redirect@0.1.0", "debug@2.3.3", "debug@2.4.0", result)

      val postDiff = diff("debug@2.4.0")
      postDiff.paths.isEmpty
    }, false, true, true)
  }

  /**
    * Should be empty after updated typing relation
    */
  "express@3.1.0 and 3.1.1 against circumflex-cors@0.0.2" should "run" in {
    runEvolution("express@3.1.0", List("3.1.1"), "circumflex-cors@0.0.2", { result =>
      val diff =
        mkDiff("circumflex-cors@0.0.2", "express@3.1.0", "express@3.1.1", result)

      val postDiff = diff("express@3.1.1")
      postDiff.paths.isEmpty
    //postDiff.paths.forall {
    //  case (accessPath, pathDiff) =>
    //    val ap = accessPath.mkString(".")
    //    log.info(s"DIFF: $ap -> ${pathDiff.getClass}")
    //    pathDiff match {
    //      case NoObservationInPost(obs, _) =>
    //        ap.startsWith("require(express).(0).(2).arg1.viewCallbacks")
    //      case NoObservationInBase(obs, _) =>
    //        false
    //      case DifferentObservations(types) =>
    //        false
    //    }
    //} && postDiff.paths.nonEmpty
    }, false, true, true)
  }

  /**
    * The _headers.etag changes type from undefined to string.
    * It happens because express decides to always include an etag.
    * Before, the etag was only set when the length of the request exceed 1024
    * This change is described in the changelog.
    */
  "express@3.4.4 and 3.4.5 against express-resourceful.js@0.0.7" should "run" in {
    runEvolution(
      "express@3.4.4",
      List("3.4.5"),
      "express-resourceful.js@0.0.7", { result =>
        val diff =
          mkDiff("express-resourceful.js@0.0.7", "express@3.4.4", "express@3.4.5", result)

        val postDiff = diff("express@3.4.5")
        postDiff.paths.forall {
          case (accessPath, pathDiff) =>
            val ap = accessPath.mkString(".")
            //log.info(s"DIFF: $ap -> ${pathDiff.getClass}")
            pathDiff match {
              case RelationFailure(_, _) =>
                ap == "require(express).(0).(2).arg1._headers.etag"
              case _ => false
            }
        } && postDiff.paths.nonEmpty
      },
      false,
      true)
  }

  /**
    * debugging..
    */
  "express@3.5.3 and 3.6.0 against express-resourceful.js@0.0.7" should "run" in {
    runEvolution(
      "express@3.5.3",
      List("3.6.0"),
      "express-resourceful.js@0.0.7", { result =>
        val diff =
          mkDiff("express-resourceful.js@0.0.7", "express@3.5.3", "express@3.6.0", result)

        val postDiff = diff("express@3.6.0")
        postDiff.paths.forall {
          case (accessPath, pathDiff) =>
            val ap = accessPath.mkString(".")
            //log.info(s"DIFF: $ap -> ${pathDiff.getClass}")
            pathDiff match {
              case RelationFailure(_, _) =>
                ap == "require(express).(0).(2).arg1.headerSent"
            }
        } && postDiff.paths.nonEmpty
      },
      false,
      true)
  }
}

class SelectedForDebugging extends SelectedForDebuggingBase {}

class SelectedForDebuggingPar
    extends SelectedForDebuggingBase
    with ParallelTestExecution {}
