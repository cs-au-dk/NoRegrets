package backend.commands.benchmarking

import backend.datastructures.PackageAtVersion

import scala.language.implicitConversions

object SmallBenchmarkCollection {
  implicit def string2pv(s: String): PackageAtVersion =
    PackageAtVersion(s.split("@")(0), s.split("@")(1))

  case class Evolution(pre: String, post: String) {
    override def toString = s"$pre -> $post"
  }

  case class EvolutionInfoBenchmark(breakingReason: Set[ProblemKind.Value] = Set(),
                                    status: Set[EvolutionStatus.Value] = Set(),
                                    clients: List[PackageAtVersion],
                                    expectedError: MessagesAlgebra =
                                      MessagesAlgebra.ANYERR) {
    override def toString =
      s"${if (!status.contains(EvolutionStatus.NOT_IGNORED)) "IGNORED, " else ""}" +
        s"${if (breakingReason.isEmpty) "NON BREAKING" else "BREAKING"}"
  }

  case class LibraryEvolutionBenchmark(
    library: String,
    libraryIncludeString: Option[String] = None,
    evolutions: Map[Evolution, EvolutionInfoBenchmark]) {
    override def toString = library
  }

  object EvolutionStatus extends Enumeration {
    val NOT_IGNORED, SPEC_GENERATION_OK, TEST_GENERATION_OK, PRE_VERSION_PASS,
    PRE_VERSION_FAIL, POST_VERSION_PASS, POST_VERSION_FAIL, NON_PROXY_TEST_OK,
    POST_FAIL_WITH_RIGHT_MESSAGES =
      Value
  }

  val classicalBreakingStatus = Set(
    EvolutionStatus.NON_PROXY_TEST_OK,
    EvolutionStatus.NOT_IGNORED,
    EvolutionStatus.SPEC_GENERATION_OK,
    EvolutionStatus.TEST_GENERATION_OK,
    EvolutionStatus.PRE_VERSION_PASS,
    EvolutionStatus.POST_VERSION_FAIL,
    EvolutionStatus.POST_FAIL_WITH_RIGHT_MESSAGES)

  val classicalNonBreakingStatus = Set(
    EvolutionStatus.NON_PROXY_TEST_OK,
    EvolutionStatus.NOT_IGNORED,
    EvolutionStatus.SPEC_GENERATION_OK,
    EvolutionStatus.TEST_GENERATION_OK,
    EvolutionStatus.PRE_VERSION_PASS,
    EvolutionStatus.POST_VERSION_PASS)

  object ProblemKind extends Enumeration {
    val MODULE, API, SEMANTICS = Value
  }

  trait MessagesAlgebra {

    def and(s2: MessagesAlgebra) = {
      MessagesAlgebra.OR(this, s2)
    }

    def or(s2: MessagesAlgebra) = {
      MessagesAlgebra.AND(this, s2)
    }

    def not() = {
      MessagesAlgebra.NOT(this)
    }

    def check(log: String): Boolean
  }

  object MessagesAlgebra {

    case class OR(s1: MessagesAlgebra, s2: MessagesAlgebra) extends MessagesAlgebra {
      def check(log: String): Boolean = log.contains(s1) || log.contains(s2)
    }

    case class AND(s1: MessagesAlgebra, s2: MessagesAlgebra) extends MessagesAlgebra {
      def check(log: String): Boolean = log.contains(s1) && log.contains(s2)
    }

    case class ATOM(s: String) extends MessagesAlgebra {
      def check(log: String): Boolean = log.contains(s)
    }

    case object ANYERR extends MessagesAlgebra {
      def check(log: String): Boolean = true
    }

    case object ANYTHINGISWRONG extends MessagesAlgebra {
      def check(log: String): Boolean = false
    }

    case class NOT(s: MessagesAlgebra) extends MessagesAlgebra {
      def check(log: String): Boolean = !log.contains(s)
    }

    implicit def toAtom(s: String): MessagesAlgebra = MessagesAlgebra.ATOM(s)
  }

  val benchmarksFromGoogleDocs = List(
    //Test ok
    LibraryEvolutionBenchmark(
      library = "validate",
      evolutions = Map(
        Evolution("2.1.2", "2.1.3") -> EvolutionInfoBenchmark(
          breakingReason = Set(ProblemKind.API),
          status = classicalBreakingStatus,
          clients = List("rhom@0.2.5")),
        Evolution("2.1.1", "2.1.3") -> EvolutionInfoBenchmark(
          breakingReason = Set(ProblemKind.API),
          status = classicalBreakingStatus,
          clients = List("rhom@0.2.5")),
        Evolution("2.1.0", "2.1.1") -> EvolutionInfoBenchmark(
          breakingReason = Set(),
          status = classicalNonBreakingStatus,
          clients = List("rhom@0.2.5")),
        Evolution("2.1.1", "2.1.2") -> EvolutionInfoBenchmark(
          breakingReason = Set(),
          status = classicalNonBreakingStatus,
          clients = List("rhom@0.2.5")),
        Evolution("2.1.0", "2.1.2") -> EvolutionInfoBenchmark(
          breakingReason = Set(),
          status = classicalNonBreakingStatus,
          clients = List("rhom@0.2.5")),
        Evolution("2.1.3", "2.1.4") -> EvolutionInfoBenchmark(
          breakingReason = Set(),
          status = classicalNonBreakingStatus,
          clients = List("rhom@0.2.10")))),
    //The problem is semantic (i.e., it cannot be handled by our technique)
    LibraryEvolutionBenchmark(
      library = "backbone",
      evolutions = Map(
        Evolution("1.1.2", "1.2.0") -> EvolutionInfoBenchmark(
          breakingReason = Set(ProblemKind.SEMANTICS), // This is a semantic problem
          status = Set(),
          clients = List(
            //"backbone-virtual-collection@0.5.1" // the tests of this library rely on the proxy identity
          )))),
    // This benchmark is using coffeescript.
    // The results from the benchmark collection is not reproducibel (even with node 6)
    // Apparently, both the good and the bad version fails with the problem:
    //  TypeError: Class constructor Adapter cannot be invoked without 'new'
    // LibraryEvolutionBenchmark(
    //   library = "hubot",
    //   evolutions = Map(
    //     Evolution("2.8.3", "2.9.0") -> EvolutionInfoBenchmark(
    //       breakingReason = Set(ProblemKind.MODULE),
    //       status = classicalBreakingStatus,
    //       clients = List("actuator@0.0.2")))),
    LibraryEvolutionBenchmark(
      library = "debug",
      evolutions = Map(
        Evolution("2.3.3", "2.4.0") -> EvolutionInfoBenchmark(
          breakingReason = Set(ProblemKind.MODULE),
          status = classicalBreakingStatus,
          clients = List(
            //          "apn@1.7.8",
            //          "assemble-render-file@0.5.3",
            //          "avenger@0.1.5",
            //          "baiji-entity@1.0.2",
            //          "broccoli-caching-writer@3.0.3",
            //          "bunyan-express-middleware@0.0.2",
            //          "charming-circle@3.1.0",
            //          "christacheio@2.1.0",
            //          "clientlinker@6.0.2",
            //          "coap@0.15.0",
            //          "connect-injector@0.4.4",
            //          "connect-session-sequelize@3.1.0",
            //          "couchbase-driver@0.1.7",
            //          "debug-callback@0.0.5",
            //          "devcaps@1.0.0",
            //          "duplicate-files@0.1.4",
            //          "ebay-api@1.6.2",
            //          "es6-class-mixin@1.0.2",
            //          "express-reaccess@2.0.0",
            //          "feathers-errors@2.3.0",
            //          "feathers-primus@1.3.0",
            "ilp@4.0.2",
            //          "immutable-di@1.3.2",
            //          "jwks-rsa@1.0.0",
            //          "koa-websocket@0.1.2",
            //          "kopeer@1.0.0"                                // debug used during install
            "kox@0.1.4",
            "kwiz@1.0.0",
            //          "lark-router@0.5.0"                           // unknown problem
            //          "loopback-boot@2.6.4"                         // instrumentation breaks linter
            //          "mako-babel@0.9.0",
            //          "meshblu-http@7.2.1",
            //          "metalsmith-paths@1.0.0",
            //          "meteor-ping@0.2.3"                           // instrumentation breaks linter
            "mocha-junit-reporter@1.12.1",
            "mongodb-instance-model@1.0.1"
            //          "mosaic-dataset-index@0.3.4",
            //          "nanocyte-configuration-generator@1.22.5"
          ),
          expectedError = "ReferenceError: namespae is not defined"),
        //There are problems with all clients
        Evolution("2.4.5", "2.5.0") -> EvolutionInfoBenchmark(
          breakingReason = Set(ProblemKind.MODULE),
          status = Set(),
          clients = List(
            //"basictracer@0.3.0"                                     // Tests in weird folder
            //"electron-react-boilerplate@0.5.2"                      // Module error occurs in pre-processing of the test files (babel compilation)
            //"slate@0.16.6"                                          // There are some problems with the installation of this client
          )))),
    //This does not look like an API change.
    LibraryEvolutionBenchmark(
      library = "broccoli-filter",
      evolutions = Map(
        Evolution("0.1.7", "0.1.8") -> EvolutionInfoBenchmark(
          breakingReason = Set(ProblemKind.API),
          status = Set(),
          clients = List("broccoli-traceur@0.6.0")))),
    //Both clients use proxy-require to require clix so the proxification does not work.
    LibraryEvolutionBenchmark(
      library = "clix",
      evolutions = Map(
        Evolution("1.1.2", "1.1.4") -> EvolutionInfoBenchmark(
          breakingReason = Set(ProblemKind.API),
          status = Set(),
          clients = List(
          /* Disabled since they use a mocha wrapper that will not correctly our preamble
            "bumpery@1.0.0"
            "bundly@1.1.4"
           */
          )))),
    LibraryEvolutionBenchmark(
      library = "wav-encoder",
      evolutions = Map(
        Evolution("1.0.0", "1.1.0") -> EvolutionInfoBenchmark(
          breakingReason = Set(ProblemKind.API),
          status = Set(),
          clients = List(
            // Disabled due to issues with the babel compiler
            //"ciseaux@0.4.0"
          )))),
    // Test OK
    LibraryEvolutionBenchmark(
      library = "cli-compiler",
      evolutions = Map(
        Evolution("0.1.21", "0.1.22") -> EvolutionInfoBenchmark(
          breakingReason = Set(ProblemKind.API),
          status = classicalBreakingStatus,
          clients = List("cli-system@0.1.16")))),
    // This is probably not an API change.
    // The server passes objects as strings, which means that the changed value is just another string (same type)
    // There is no way our tool can handle that.
    LibraryEvolutionBenchmark(
      library = "http-proxy",
      evolutions = Map(
        Evolution("1.15.2", "1.16.0") -> EvolutionInfoBenchmark(
          breakingReason = Set(ProblemKind.API),
          status = Set(),
          clients = List("clydeio@0.2.1")))),
    LibraryEvolutionBenchmark(
      library = "buffer-crc32",
      evolutions = Map(
        Evolution("0.2.7", "0.2.8") -> EvolutionInfoBenchmark(
          breakingReason = Set(ProblemKind.API),
          status = classicalBreakingStatus,
          clients = List("crc32-stream@0.3.3")))),
    LibraryEvolutionBenchmark(
      library = "async",
      libraryIncludeString = Some("async"),
      evolutions = Map(
        Evolution("2.0.1", "2.1.0") -> EvolutionInfoBenchmark(
          breakingReason = Set(ProblemKind.MODULE),
          status = classicalBreakingStatus,
          clients = List("dbc-node-community-client@3.0.9"),
          expectedError = "Cannot find module 'async/forever'"))),
    //Currently disabled due to missing Map implementation in test.ts
    //LibraryEvolutionBenchmark(
    //  library = "bip32-utils",
    //  evolutions = Map(
    //    Evolution("0.4.2", "0.4.3") -> EvolutionInfoBenchmark(
    //      breakingReason = Set(ProblemKind.MODULE),
    //      status = classicalBreakingStatus,
    //      clients = List("cb-wallet@0.10.1")))),
    //Transitive dependency issue (react cannot be required)
    LibraryEvolutionBenchmark(
      library = "react-element-to-jsx-string",
      evolutions = Map(
        Evolution("2.4.0", "2.5.0") -> EvolutionInfoBenchmark(
          breakingReason = Set(ProblemKind.MODULE),
          status = Set(),
          clients = List("chai-equal-jsx@1.0.3")))),
    //We need a way to handle transitive failing dependencies
    LibraryEvolutionBenchmark(
      library = "mocha",
      evolutions = Map(
        Evolution("2.4.5", "2.5.0") -> EvolutionInfoBenchmark(
          breakingReason = Set(ProblemKind.API),
          status = Set(),
          clients = List("ember-cli-deploy-firebase@0.0.1")))),
    //"karma-yaml-preprocessor@1.0.6",
    //"musicjson2abc@0.3.0")))),
    //Transitive dependency issue (react cannot be required)
    LibraryEvolutionBenchmark(
      library = "react",
      evolutions = Map(
        Evolution("15.3.2", "15.4.0") -> EvolutionInfoBenchmark(
          breakingReason = Set(ProblemKind.API),
          status = Set(),
          clients = List("instantsearch.js@1.8.13")),
        Evolution("15.3.1", "15.3.2") -> EvolutionInfoBenchmark(
          breakingReason = Set(ProblemKind.API),
          status = Set(),
          clients = List("react-binary-clock@1.0.2")))),
    //Disabled due to extremely slow bisimulation
    LibraryEvolutionBenchmark(
      library = "uglify-js",
      evolutions = Map(
        Evolution("2.4.15", "2.4.16") -> EvolutionInfoBenchmark(
          breakingReason = Set(ProblemKind.API),
          status = Set(),
          clients = List("plumber-uglifyjs@0.2.2")))),
    //The generated tests throws a segmentation fault or runs out of memory
    LibraryEvolutionBenchmark(
      library = "share",
      evolutions = Map(
        Evolution("0.7.12", "0.7.13") -> EvolutionInfoBenchmark(
          breakingReason = Set(ProblemKind.API),
          status = Set(),
          clients = List("racer@0.6.0-alpha24")))),
    //Test succeeds
    LibraryEvolutionBenchmark(
      library = "chalk",
      evolutions = Map(
        Evolution("1.1.1", "1.1.2") -> EvolutionInfoBenchmark(
          breakingReason = Set(ProblemKind.API),
          status = classicalBreakingStatus,
          clients = List("seeli@1.0.1")))),
    //There is some proxy interference making the tracer fail before the tests have finished
    LibraryEvolutionBenchmark(
      library = "url-resolver-fs",
      evolutions = Map(
        Evolution("1.1.0", "1.1.1") -> EvolutionInfoBenchmark(
          breakingReason = Set(ProblemKind.API),
          status = Set(),
          clients = List("svn-dav-fs@2.2.2")))),
    //There is a termination issue with DataGenerator.acyclicPaths.
    //(This is probably just because the LTS is so large that it takes a long time to terminate)
    LibraryEvolutionBenchmark(
      library = "immutable",
      evolutions = Map(
        Evolution("3.6.2", "3.6.3") ->
          EvolutionInfoBenchmark(
            breakingReason = Set(ProblemKind.API),
            status = Set(),
            clients = List("commonform-merkleize@0.1.1")))),
    //This error occurs on installation time (not at test time) so even if our tool can detect it, it will still look like a failed installation
    LibraryEvolutionBenchmark(
      library = "chromedriver",
      evolutions = Map(
        Evolution("2.27.1", "2.27.2") ->
          EvolutionInfoBenchmark(
            breakingReason = Set(ProblemKind.API),
            status = Set(),
            clients = List("sw-testing-helpers@0.0.4")))),
    // We cannot catch this error at the moment since the client tests reassigns the entries in multidimensional arrays to values of a different type.
    // There is also a description of this in the paper.
    LibraryEvolutionBenchmark(
      library = "ml-matrix",
      evolutions = Map(
        Evolution("1.1.2", "1.1.3") ->
          EvolutionInfoBenchmark(
            breakingReason = Set(ProblemKind.API),
            status = Set(),
            clients = List("ml-polynomial-kernel@1.0.0")))),
    //The failure of this test looks like it has something to do with a missing redis server (Not investigated deeply)
    LibraryEvolutionBenchmark(
      library = "redlock",
      evolutions = Map(
        Evolution("2.0.2", "2.1.0") ->
          EvolutionInfoBenchmark(
            breakingReason = Set(ProblemKind.API),
            status = Set(),
            clients = List("interval-worker@2.10.0")))),
    //This looks like a C++/V8 problem
    LibraryEvolutionBenchmark(
      library = "assemble-handle",
      evolutions = Map(
        Evolution("0.1.0", "0.1.1") ->
          EvolutionInfoBenchmark(
            breakingReason = Set(ProblemKind.API),
            status = Set(),
            clients = List("assemble-fs@0.4.3")))),
    //This problem occurs at installation time
    LibraryEvolutionBenchmark(
      library = "node-sass",
      evolutions = Map(
        Evolution("3.10.1", "3.11.0") ->
          EvolutionInfoBenchmark(
            breakingReason = Set(ProblemKind.API),
            status = Set(),
            clients = List("apptension-tools@5.3.7"))))
    //LibraryEvolutionBenchmark(
    //  library = "lodash",
    //  evolutions = Map(
    //    Evolution("3.0.0", "3.0.1") ->
    //      EvolutionInfoBenchmark(
    //        breakingReason = Set(ProblemKind.API),
    //        status = classicalNonBreakingStatus,
    //        clients = List("candi@0.2.0"))))
  )
  val otherBenchmarks = List(
    LibraryEvolutionBenchmark(
      library = "lodash",
      evolutions = Map(
        Evolution("3.0.0", "3.0.1") ->
          EvolutionInfoBenchmark(
            breakingReason = Set(ProblemKind.API),
            status = Set(),
            clients = List("contributors@0.3.1")))),
    LibraryEvolutionBenchmark(
      library = "debug",
      evolutions = Map(
        Evolution("2.0.0", "2.0.0") ->
          EvolutionInfoBenchmark(
            breakingReason = Set(ProblemKind.API),
            status = Set(),
            clients = List("https-proxy-agent@0.3.6")))))

  val benchmarks = benchmarksFromGoogleDocs ++ otherBenchmarks

  val flatBenchmarks
    : List[(LibraryEvolutionBenchmark, Evolution, EvolutionInfoBenchmark)] = {

    /*
     * Since tests are ran in parallel across libraries
     * there should be no client everlaps across multiple benchmarks
     */
    val clientsPerLibrary = SmallBenchmarkCollection.benchmarks.flatMap(leb =>
      leb.evolutions.flatMap(_._2.clients).toSet.toList)

//    val duplicates =
//      clientsPerLibrary.groupBy(identity).collect { case (x, List(_, _, _ *)) => x }
//    if (duplicates.nonEmpty)
//      throw new RuntimeException(
//        s"Duplicated clients found in the benchmarks: ${duplicates}")

    val flattened = SmallBenchmarkCollection.benchmarks.flatMap { bench =>
      bench.evolutions.map { ev =>
        (bench, ev._1, ev._2)
      }
    }

    flattened

  }
}
