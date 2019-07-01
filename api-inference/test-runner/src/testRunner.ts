import * as assert from "assert";
import {AssertionError} from "assert";
import * as path from "path";
import {none, Option, option, some} from "ts-option";

import {Constants} from "../../API-tracer/src/constants";
import {GlobalState} from "../../API-tracer/src/global_state";
import {KnownValuesNode} from "../../API-tracer/src/known-values";
import {
  Observation,
  ObservationAuxInfo
} from "../../API-tracer/src/observations";
import {
  AccessLabel,
  AccessPath,
  ApplicationLabel,
  ArgLabel,
  PropertyLabel,
  ProtoLab,
  ReceiverLab,
  RequireLabel,
  WriteLabel,
} from "../../API-tracer/src/paths";
import {ProxyOperations} from "../../API-tracer/src/ProxyOperations";
import {
  restoreNativeToStringResults,
  unproxyOnProblematicNativeCalls
} from "../../API-tracer/src/runtime-hacks";
import {UnionType} from "../../API-tracer/src/runtime-types";
import {
  getRuntimeType,
  IgnoreUnionType,
  isPrimitive,
  NonExistingUnionType
} from "../../API-tracer/src/runtime-types-operations";
import {ThrownError} from "../../API-tracer/src/throwError";
import {APIModel} from "../../API-tracer/src/tracing-results";

import {ArgumentProxyHandler} from "./argumentProxyHandler";
import {StuckTestException, UnSynthesizableException} from "./exceptions";
import {ModelValue} from "./modelValue";
import {Options, OptionsOps} from "./option";
import {TestReport, TypeRegression} from "./testResult";
import {ValueCreator} from "./typeCreator";

import _ = require("lodash");
import PriorityQueue = require("priorityqueuejs");
import {Checker, TypeChecker, ValueChecker} from "./checker";
import istanbul = require('istanbul');
import {TypeInfer} from "./typeInfer";

export interface TestRunner {
  runTest(): TestReport;
  isVop(path: AccessPath|string): boolean;
  addVopValue(path: AccessPath|string, value: any): void;
  deleteVop(path: AccessPath): void;
  synthesize(modelValue: ModelValue, report: TestReport): any;
  addModelValueTest(modelValueTest: ModelValueTest): void;
  testModelValue(mValue: ModelValue, runtimeValue: any, receiver: Option<any>,
                 report: TestReport, fromThrow: boolean);
  getGreatestCheckedId(): number
}

export class TestRunnerImpl implements TestRunner {
  static LOG_CATEGORY = "test-runner";
  private static winston = require("winston");
  private static fs = require("fs");
  private readonly rootModelValues: ModelValue[];
  private learnedModel: APIModel;

  // We need to process the model in the same order as the observations were
  // originally obtained. Otherwise, we risk inducing type regressions due to
  // invalid ordering of side-effects and we may get stuck at some point if test
  // A depends on B and we try to execute B before A
  private pQueue: PriorityQueue<ModelValueTest>;
  private valueOriginMap: Map<string, Option<any>> =
      new Map<string, Option<any>>();

  // A set of pathStrs for which a value has been written by a write action
  // We should avoid checking values that have these pathStrs as prefixes, since we
  // provided the values.
  private clientWrittenPaths: Set<string> = new Set();
  private greatestCheckedId: number = 0;
  private checker: Checker;

  constructor(private options: Options, private initTime: number) {
    TestRunnerImpl.winston.info(
        `${TestRunnerImpl.LOG_CATEGORY} - running with options ${
            OptionsOps.toString(this.options)}`);

    //@ts-ignore
    // Used by some custom libraries that have divergent behavior when used in
    // test mode.
    global.TEST_MODE = true;

    // NOTE, must be called early (otherwise, isKnown calls may fail)
    KnownValuesNode.init();

    // Must called, otherwise toString and exec may throw type errors when the
    // receiver is a proxy
    unproxyOnProblematicNativeCalls();

    restoreNativeToStringResults();

    this.checker = options.exactValueChecking == "true" ? new ValueChecker()
                                                        : new TypeChecker();
    // this.checker = new ValueChecker();

    this.learnedModel = APIModel.fromJson(
        JSON.parse(TestRunnerImpl.fs.readFileSync(this.options.modelFile)));

    if (this.learnedModel.observations.length == 0) {
      throw new Error("No observations in model");
    }

    // Models can be based on multiple modules
    // For example, if a client uses both require(lib) and require(lib.a).
    // We group the observations by module and test each in separation.
    const observationsByModule =
        _.groupBy(this.learnedModel.observations,
                  (obs: Observation) => obs.path.getLast().name);

    this.rootModelValues =
        _.map(observationsByModule, (obs) => ModelValue.buildModel(obs));

    // Initialize all the valueOriginPaths to none
    for (let o of this.learnedModel.observations) {
      if (o.valueOriginPath.isDefined) {
        this.valueOriginMap.set(o.valueOriginPath.get.toString(), none);
      }
    }
  }

  // Starts the NoRegretsPlus testing
  public runTest(): TestReport {
    const libFolder = this.options.libraryFolder;

    const report = new TestReport(
        this.learnedModel.observations
            .filter(obs => obs.path.shouldIncludeInPathCoverarge())
            .map(o => o.path),
        this.initTime);

    for (const model of this.rootModelValues) {
      // The module name is always the name of the first action, i.e., the name
      // of the require action.
      const moduleName = model.path.getLast().name;
      const reqPath = `${
          (path.isAbsolute(libFolder)
               ? libFolder
               : `./${libFolder}`)}/node_modules/${moduleName}`;

      const instrumenter = new istanbul.Instrumenter();

      // Applies the instrumentation when the following predicate is true.
      const doInstrumentPred = (filePath: string) =>
          this.options.coverage === 'true' &&
          filePath.substring(filePath.indexOf("node_modules"))
              .includes(this.options.libraryName);
      // filePath.includes(this.options.libraryName);

      const instrumenterFunc = (code: string, file: string) => {
        try {
          return instrumenter.instrumentSync(code, file);
        } catch (e) {
          console.log("error instrumenting for coverage");
        }
      };
      //@ts-ignore
      istanbul.hook.hookRequire(doInstrumentPred, instrumenterFunc);

      let library;
      try {
        delete require.cache[require.resolve(reqPath)];
        library = require(reqPath);
      } catch (e) {
        report.ignorePath(model.path);
        report.addTypeRegression(
            new TypeRegression(model.path, model.type.toTypeString(),
                               getExceptionType(e).toTypeString()));
        this.recordStatementCoverage(report, moduleName);
        continue;
      }
      // pQueue (lowest id first)
      this.pQueue = new PriorityQueue(ModelValueTest.compare);

      this.addModelValueTest(new ModelValueTest(
          () => this.testModelValue(model, library, none, report, false),
          model.id));
      while (!this.pQueue.isEmpty()) {
        const mValueTest = this.pQueue.deq();
        mValueTest.cont();
        this.updateGreatestCheckedId(mValueTest.id);
      }
      this.recordStatementCoverage(report, moduleName);
    }
    return report;
  }

  private recordStatementCoverage(report: TestReport, module: string) {
    if (this.options.coverage === 'true') {
      //@ts-ignore
      //@ts-ignore
      for (var file in global.__coverage__) {
        const moduleName = path.relative(
          path.resolve(this.options.libraryFolder, "node_modules"), file);
        // const moduleName = path.basename(file, ".js");
        const collector = new istanbul.Collector();
        //@ts-ignore
        collector.add({file : global.__coverage__[file]});
        //@ts-ignore
        const coverageReport = istanbul.Report.create('json');
        coverageReport.opts.dir =
          path.resolve(this.options.libraryFolder, "coverage");
        coverageReport.opts.file =
          `${moduleName}-${this.options.libraryVersion}-coverage.json`;
        coverageReport.writeReport(collector, true);
        report.addCoverage(
          moduleName,
          istanbul
            .utils
            //@ts-ignore
            .summarizeCoverage({moduleName : global.__coverage__[file]}));
        report.addCoverageFile(
          `${coverageReport.opts.dir}/${coverageReport.opts.file}`);
      }
      //@ts-ignore
      global.__coverage__ = undefined
    }
  }

  private updateGreatestCheckedId(id: number) {
    if (id > this.greatestCheckedId) {
      this.greatestCheckedId = id;
    }
  }

  public addModelValueTest(modelValueTest: ModelValueTest) {
    this.pQueue.enq(modelValueTest);
  }

  // NOTE, keep the receiver object since it makes debugging easier sometimes
  public testModelValue(mValue: ModelValue, runtimeValue: any,
                        receiver: Option<any>, report: TestReport,
                        fromThrow: boolean) {
    this.addVopValueIfNeeded(mValue.path, runtimeValue);

    // Check if we are testing a path prefixed by a client written path
    // Testing this path is redundant since it's the test-runner that wrote the
    // value.
    let pathPtr = mValue.path;
    while (!(pathPtr.getName() instanceof RequireLabel)) {
      const pathPtrStr = pathPtr.toString();
      if (this.clientWrittenPaths.has(pathPtrStr)) {
        // We are currently checking a value that we have written
        // Skip the check
        return;
      }
      pathPtr = pathPtr.getNext();
    }

    report.coverPath(mValue.path);

    if (mValue.didThrow != fromThrow) {
      report.ignorePath(mValue.path);
      const pt = (t) => t ? "throwing" : "non-throwing";
      report.addTypeRegression(
          new TypeRegression(mValue.path, `Expected ${pt(mValue.didThrow)}`,
                             `Observed ${pt(fromThrow)}`));
      return;
    }

    // Check the type of the value
    this.checker.checkValue(mValue, runtimeValue, report, mValue.path);

    // If the value is null or undefined then we skip checks of its properties
    // since NoRegretsPlus will just crash when applying any actions on the value.
    if (runtimeValue == null) {
      return
    }

    // Recursively check the properties of the value
    mValue.getProperties().forEach((propertyPair) => {
      const prop = propertyPair[0];
      const propModel = propertyPair[1];
      // NON-EXIST properties should not be investigated further
      if (propModel.type.equals(NonExistingUnionType) ||
          propModel.type.equals(IgnoreUnionType)) {
        report.coverPath(propModel.path);
        return;
      }
      // APPLICATION
      if (prop instanceof ApplicationLabel) {
        this.addModelValueTest(new ModelValueTest(() => {
          if (this.options.skipHigherOrder === "true" &&
              propModel.isHigherOrderFunction()) {
            TestRunnerImpl.winston.debug(
                `Skipping higher-order function ${mValue.path.toString()}`);
            return;
          }
          const numArgs = prop.numArgs;

          const argLabelsSorted: ArgLabel[] =
              propModel.getArgumentLabelsSorted();
          assert.equal(
              argLabelsSorted.length, numArgs,
              "Assertion failed: expected number of arguments does not correspond to number of arguments found");

          // Array.from(Array(N).keys()) = [0,...,N-1]
          const expectedArgumentIdxSum =
              _.sum(Array.from(Array(numArgs).keys()));
          assert.equal(
              _.sumBy(argLabelsSorted, (l) => l.idx), expectedArgumentIdxSum,
              "Error: There must be exactly one argument label for each argument position");

          let synthArgs;
          try {
            synthArgs = _.map(argLabelsSorted, argLab => {
              const argMValue = propModel.getPropertyModel(argLab);
              return this.synthesize(argMValue, report);
            });
          } catch (e) {
            if (e instanceof UnSynthesizableException) {
              // We cannot call this function, so we skip any recursive checks
              report.ignorePath(propModel.path);
              return;
            }
            throw e;
          }

          let receiver: any = null;
          if (!prop.constructorCall) {
            const receiverMVal =
                propModel.getPropertyModelByEquality(ReceiverLab);
            receiver = this.synthesize(receiverMVal, report);
          }

          let retVal;
          try {
            retVal = prop.constructorCall
                         ? Reflect.construct(runtimeValue, synthArgs)
                         : Reflect.apply(runtimeValue, receiver, synthArgs);
          } catch (e) {
            if (e instanceof AssertionError) throw e;
            this.testModelValue(propModel, e, none, report, true);
            return;
          }
          this.testModelValue(propModel, retVal, none, report, false);
        }, prop.identifier));

      }
      // PROPERTY
      else if (prop instanceof PropertyLabel) {
        if (prop.equals(ReceiverLab)) {
          // Skip checking receivers.
          // They are only used in function calls
        } else {
          this.addModelValueTest(new ModelValueTest(() => {
            const property = prop.getProperty();
            let propValue: any;
            let fromThrow = false;
            try {
              if (property === Constants.PROTO_SYM) {
                propValue = Object.getPrototypeOf(runtimeValue);
              } else {
                propValue = runtimeValue[property];
              }
            } catch (e) {
              propValue = e;
              fromThrow = true;
            }
            this.testModelValue(propModel, propValue, option(runtimeValue),
                                report, fromThrow);
          }, propModel.id));
        }
      }
      // WRITE
      else if (prop instanceof WriteLabel) {
        // Note, this is not actually a check but instead a forced invocation of
        // a write We need to apply the write since futuer checks may depend on
        // it.
        this.addModelValueTest(new ModelValueTest(() => {
          const propName = prop.getProperty();

          // We create a new path for clientWrittenPaths since we don't want the
          // 'write' in the label.
          this.clientWrittenPaths.add(
              mValue.path.extendWithLabel(new PropertyLabel(propName))
                  .toString());
          let writeVal = this.synthesize(propModel, report);
          runtimeValue[propName] = writeVal;

          // We already add the VOP value here since we do not want to call
          // the type checker on a written value
          this.addVopValueIfNeeded(propModel.path, writeVal);
        }, propModel.id));
      }
      // ACCESS
      else if (prop instanceof AccessLabel) {
        this.addModelValueTest(new ModelValueTest(() => {
          let length = runtimeValue.length;
          assert(length > 0,
                 `trying to check access action value without a length field ${
                     runtimeValue}`);
          for (let i = 0; i < length; i++) {
            let propValue = runtimeValue[i];
            this.testModelValue(propModel, propValue, option(runtimeValue),
                                report, false);
            // this.addModelValueTest(new ModelValueTest(propModel, propValue,
            // option(runtimeValue), report));
          }
        }, propModel.id));
      } else {
        // NOOP
      }
    });
  }

  /**
   * Synthesized a runtime value based on a modelValue
   * Will throw an UnSynthesizableException if the modelValue transitively has
   * any properties with ignored pathStrs.
   * @param modelValue
   * @param report
   */
  public synthesize(modelValue: ModelValue, report: TestReport): any {
    const pQueue = this.pQueue;
    const vopMap = this.valueOriginMap;

    if (report.isIgnoredPath(modelValue.path)) {
      // This situation is most likely to arise if some VOP value doesn't type
      // check but is later required as an argument of a function
      throw new UnSynthesizableException(`ModelValue at path ${
          modelValue.path.toString()} is ignored and cannot be synthesized `);
    }
    if (modelValue.isKnownValue) {
      TestRunnerImpl.winston.warn(
          "Attempting to synthesize known value (is potential source of unsoundness)");

      // NOTE, this object should not be proxified since we do not proxy known
      // values in NoRegrets
      return ValueCreator.createObjectTypedValue(modelValue.type.get(0));
    }

    if (isPrimitive(modelValue.type) && modelValue.values.isEmpty) {
      throw new Error(
          "Primitively typed modelValues must have the value field set");
    }

    if (modelValue.values.isDefined) {
      // Some primitives may have a __proto__ prop since primitives are boxed
      // when calling getPrototypeOf on them
      const nonArgumentProperties = _.filter(
          Array.from(modelValue.getPropertyKeys()),
          lab => !(lab instanceof ArgLabel) && !(lab.equals(ProtoLab)) &&
                 !(lab.equals(ReceiverLab)));
      if (nonArgumentProperties.length !== 0) {
        // the existance of properties indicates that the value is an object or
        // a function
        throw Error(
            "Assertion failed: Cannot synthesize a ModelValue that has a defined value and properties");
      }
      const values = modelValue.values.get;
      assert(
          values.length === 1,
          `Unexpected ModelValue of primitive type ${
              modelValue.type.toTypeString()} with multiple values ${values}`);
      return ObservationAuxInfo.retrieveOriginalValue(values[0]);
    }

    // at this point we can safely assume that the modelValue represents an
    // object like value.
    let synthVal: any;

    // VALUE ORIGINATING FROM THE LIBRARY
    if (modelValue.valueOriginPath.isDefined) {
      const vop = modelValue.valueOriginPath.get;
      const originalValue = vopMap.get(vop.toString());
      assert(originalValue.isDefined, `Expected value origin path map to map ${
                                          vop.toString()} to some value`);
      // NOTE: we do not recursively add properties or put a proxy on this
      // argument since we have already checked that it has the right structure
      return originalValue.get;
    }
    // FUNCTION
    else if (modelValue.type.contains("function")) {
      const applicationLabels = modelValue.getLabelsOfType(ApplicationLabel);
      const that = this;
      synthVal = function() {
        // Everything below is code that is executed when the synthesized
        // callback is called.
        const calledAsConstructor = new.target !== undefined;
        const args = Array.from(arguments);
        const callMatchingLabels =
            _.filter(applicationLabels,
                     lab => lab.constructorCall == calledAsConstructor &&
                            args.length == lab.numArgs);

        if (callMatchingLabels.length === 0) {
          const errorPath = modelValue.path.extendWithLabel(
              new ApplicationLabel(args.length, calledAsConstructor, 0));
          report.addTypeRegression(
              new TypeRegression(errorPath, Constants.CIRC_TYPE, "undefined"));
          report.ignorePath(errorPath);
          TestRunnerImpl.winston.warn(`Returning 'undefined' from in-model-uncalled function ${modelValue.path}. This shows a divergence of the library semantics.`);

          // We return undefined since there is nothing else that can be done
          return;
        }
        const applicationMVal = TypeInfer.findMatchingModelValue(
            args, modelValue, callMatchingLabels, that.greatestCheckedId);

        const argLabs = applicationMVal.getArgumentLabelsSorted();
        assert.equal(
            args.length, argLabs.length,
            "The number of argument labels should match the number of actual arguments");

        // Type check the arguments
        for (let i = 0; i < args.length; i++) {
          const arg = args[i];
          const argLab = argLabs[i];
          const argMValue = applicationMVal.getPropertyModel(argLab);
          pQueue.enq(new ModelValueTest(
              () => that.testModelValue(argMValue, arg, none, report, false),
              argMValue.id));
        }

        // We also schedule the receiver model value for checking here
        // The receiver of a callback is only present if the callback is called
        // like `cb.call(recv)`. but in those cases it is crucial that we view
        // the receiver as a standard argument. Otherwise, missing side-effects
        // on the receiver may lead to some side-effects not being applied.
        const receiverModel =
            applicationMVal.getPropertyModelByEquality(ReceiverLab);
        pQueue.enq(new ModelValueTest(
            () => that.testModelValue(receiverModel, this, none, report, false),
            receiverModel.id));

        // Delay the return until any tasks with a smaller id have run.
        // This way, we ensure that all side-effects gets applied to the return
        // value and that the arguments of the callback are being checked before
        // the callback returns.
        while (!that.pQueue.isEmpty() &&
               that.pQueue.peek().id < applicationMVal.id) {
          const test = pQueue.deq();
          test.cont();
          that.updateGreatestCheckedId(test.id);
        }
        let synthReturnValue;
        try {
          synthReturnValue = that.synthesize(applicationMVal, report);
        } catch (e) {
          if (e instanceof UnSynthesizableException) {
            throw new StuckTestException(
                `Cannot return from callBack: Return value not synthesizable ${
                    e.toString()}`);
          }
        }
        that.updateGreatestCheckedId(applicationMVal.id);
        if (applicationMVal.didThrow) {
          throw synthReturnValue;
        } else {
          return that.synthesize(applicationMVal, report);
        }
      }
    }
    // ARRAY
    else if (modelValue.type.contains("Array")) {
      synthVal = [];
      if (GlobalState.useAccessActions) {
        const accessLabs = modelValue.getLabelsOfType(AccessLabel);
        if (accessLabs.length > 0) {
          // There can be at most 1 accessLabel
          assert(accessLabs.length <= 1,
                 `Unexpected multiple access labels for an array at ${
                     modelValue.path.toString()}`);

          const accessMVal = modelValue.getPropertyModel(accessLabs[0]);
          const arrValuesOption = accessMVal.values;

          // Do not synthesize primitive values since the synthesizer does not
          // like when there are multiple primitive values to choose from
          const arrValues = arrValuesOption.isDefined ? arrValuesOption.get : [
            this.synthesize(accessMVal, report)
          ];

          const lengthLab = _.find(modelValue.getLabelsOfType(PropertyLabel),
                                   (l) => l.getProperty() === 'length');
          const length =
              lengthLab ? this.synthesize(
                              modelValue.getPropertyModel(lengthLab), report)
                        : accessMVal.values.getOrElse([]).length;

          for (let i = 0; i < length; i++) {
            synthVal[i] = arrValues[i % arrValues.length];
          }
        } else {
          // This assertion is not always correct when we mix access actions
          // with precise types for some indices assert(!lengthLab, `Unexpected
          // length property for array without access labels
          // ${modelValue.path.toString()}`);
        }
      }
    }
    // OBJECT
    else {
      assert(modelValue.type.size() == 1,
             `How do we synthesize an object that is union typed? ${
                 modelValue.type.toTypeString()}`);
      synthVal = ValueCreator.createObjectTypedValue(modelValue.type.get(0));
    }

    return ArgumentProxyHandler.createArgumentProxy(modelValue, synthVal,
                                                    report, this);
  }

  private addVopValueIfNeeded(vopPath: AccessPath|string, value: any) {
    if (vopPath instanceof AccessPath) {
      vopPath = vopPath.toString();
    }

    const isVop = this.valueOriginMap.has(vopPath);
    if (isVop) {
      this.addVopValue(vopPath, value)
    }
  }

  public addVopValue(path: AccessPath|string, value: any): void {
    if (path instanceof AccessPath) {
      path = path.toString();
    }
    this.valueOriginMap.set(path, some(value));
  }

  isVop(path: AccessPath|string): boolean {
    if (path instanceof AccessPath) {
      path = path.toString();
    }
    return this.valueOriginMap.has(path);
  }

  deleteVop(path: AccessPath|string) {
    if (path instanceof AccessPath) {
      path = path.toString();
    }
    this.valueOriginMap.delete(path);
  }

  getGreatestCheckedId(): number { return this.greatestCheckedId; }
}

export class ModelValueTest {
  constructor(public cont: () => void, public id: number) {}

  public static compare(m1: ModelValueTest, m2: ModelValueTest) {
    return m2.id - m1.id;
  }
}

function getExceptionType(e: any): UnionType {
  const exceptionType = new UnionType();
  exceptionType.addType(getRuntimeType(new ThrownError(e)));
  return exceptionType;
}
