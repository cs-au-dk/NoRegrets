import {
AccessLabel,
AccessPath,
ApplicationLabel,
PropertyLabel,
ArgLabel,
RequireLabel
} from "../../API-tracer/src/paths";
import {TestReport, TypeRegression} from "../src/testResult";
import {Constants} from "../../API-tracer/src/constants";
import {
ArrayType,
FunctionType,
UnionType
} from "../../API-tracer/src/runtime-types";
import {none, Option, option, some} from "ts-option";
import {GlobalState} from "../../API-tracer/src/global_state";
import {TestRunnerImpl} from "../src/testRunner";
import {fillPrototypes} from "./mocks";
import {
BoolUnionType, CircleTypeStr, ExceptionUnionType, getIntersectionType, getRuntimeType,
NumUnionType, ObjUnionType,
StrUnionType,
UndefinedUnionType
} from "../../API-tracer/src/runtime-types-operations";
import {ModelValue} from "../src/modelValue";
const assert = require('chai').assert;

describe("TestRunnerImpl", function () {
it("Should throw if APIModel contains no observations", function () {
  assert.throw(() => new TestRunnerImpl({modelFile : "res/models/empty-model.json", libraryFolder: "foo", skipHigherOrder: "true", exactValueChecking: "false"}, 0))
});

describe("#testModelValue", function () {
  require('winston').level = 'error';
  it("Should not report any regressions when pased a library with the same API as the model", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: "res/models/ClientWithOkLibModel.json",
      skipHigherOrder: "true",
      libraryFolder: `../res/sampleClientWithOkLib`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();
    assert.isEmpty(report.typeRegressions, "Unexpected type regressions when checking non-broken library");
  });
  it("should report type regressions when checking a broken library", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/ClientWithOkLibModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleClientWithBrokenLib`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();
    const apRequire = new AccessPath(new RequireLabel("lib"), undefined);

    assert.equal(report.typeRegressions.length, 3, "Expected 3 type regressions when checking library with support for higher-order functions");
    assert.includeDeepMembers(report.typeRegressions, [
      new TypeRegression(apRequire.extendWithLabel(new PropertyLabel("f"))
          .extendWithLabel(new ApplicationLabel(1, false, 6))
          .extendWithLabel(new ArgLabel(0))
          .extendWithLabel(new PropertyLabel("k")),
        Constants.CIRC_TYPE,
        UndefinedUnionType.toTypeString()),
      new TypeRegression(apRequire.extendWithLabel(new PropertyLabel("a")),
        NumUnionType.toTypeString(),
        StrUnionType.toTypeString()),
      new TypeRegression(apRequire.extendWithLabel(new PropertyLabel("h"))
          .extendWithLabel(new ApplicationLabel(1, false, 26)),
        BoolUnionType.toTypeString(),
        ObjUnionType.toTypeString())
    ], `The regression tests should report the following two regressions when ignoring higher-order functions\n` +
      `\tReturn type of g changed from boolean to string\n` +
      `\tReturn type of h changed from boolean to object`);
  });

  /**
   *
   */

  it("valueOriginPaths should prevent the issue with checking values that originated from the library", function () {
    /**
     * Given the library in res/sampleValueOriginPathClient:
     *
     * exports.f = function () {
   *     return {a : 42};
   *  }
     *
     *  exports.g = function (libObj) {
   *    libObj.a;
   *    return true;
   *  }
     *
     * We should test the scenario where a client calls f and uses the return value of f as an argument to g.
     * Since the argument of g originated from the library, it should be the case that libObj.a read is not checked on the argument
     * Specifically, if the type of a changes, then it should not result in a type regression being reported.
     */
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/ValueOriginPathClientModel.json`,
      skipHigherOrder: "true",
      libraryFolder: `../res/sampleValueOriginPathClient`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();
    assert.isEmpty(report.typeRegressions, "Did not expect any type regressions since the argument of g originally came from the library");
  });

  it("should report a type regression if an array contains an element that is not a subtype of the array's union type", function () {
    /**
     * To test an array, we must check that all the elements are of the correct type
     * Alternatively, we could pick one of the elements and check that it's of the correct type.
     * We probably need some kind of heuristic that checks at most 1000 elements per path for performance reasons.
     */

    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/UnionTypeArrayClientModel.json`,
      skipHigherOrder: "true",
      libraryFolder: `../res/sampleUnionTypeArrayClient`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();
    assert.isNotEmpty(report.typeRegressions, "We expect one type regression since the model does not allow a boolean in the libA array");
    //TODO check that the regression has the correct structure
  });

  it("should test two models in separation if the required module is different in the require label", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/MultiModuleClientModel.json`,
      skipHigherOrder: "true",
      libraryFolder: `../res/sampleMultiModuleClient`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();
    assert.equal(report.typeRegressions.length, 2, "We expect one type regressions for each of the two modules");

    assert.includeDeepMembers(report.typeRegressions, [
      new TypeRegression((new AccessPath(new RequireLabel('lib'), undefined)).extendWithLabel(new PropertyLabel("a")),
        StrUnionType.toTypeString(),
        NumUnionType.toTypeString()),
      new TypeRegression((new AccessPath(new RequireLabel('otherLib'), undefined)).extendWithLabel(new PropertyLabel("a")),
        NumUnionType.toTypeString(),
        StrUnionType.toTypeString())
    ], `The regression tests should report the following two regressions when ignoring higher-order functions\n` +
      `\Property type of lib.a changed from string to number\n` +
      `\Property type of otherLib.a changed from number to string`);
  });

  it("should throw exception when loading non-existing library", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/exceptionModel.json`,
      skipHigherOrder: "true",
      libraryFolder: `../res/doesNotExists`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.equal(1, report.typeRegressions.length, "Expected a type regression when trying to load non-existing module");
    assert.includeDeepMembers(report.typeRegressions, [
      new TypeRegression((new AccessPath(new RequireLabel('doesNotExistLibrary'), undefined)),
        ObjUnionType.toTypeString(),
        ExceptionUnionType.toTypeString())
    ], "Thrown exception did not have the expected structure")
  });

  it("should throw exception when loading non-existing library", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/applicationExceptionModel.json`,
      skipHigherOrder: "true",
      libraryFolder: `../res/sampleClientThrowingException`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.equal(1, report.typeRegressions.length, "Expected a type regression when trying to load non-existing module");
    assert.includeDeepMembers(report.typeRegressions, [
      new TypeRegression((new AccessPath(new RequireLabel('lib'), undefined).extendWithLabel(new ApplicationLabel(0, false, 0))),
        "Expected non-throwing",
        "Observed throwing")
    ], "Thrown exception did not have the expected structure")
  });

  it("should not report any regressions when pathStrs are tested in the correct order", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/orderDependentClientOk.json`,
      skipHigherOrder: "true",
      libraryFolder: `../res/sampleOrderDependentClient`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect regressions since the model states that init is called before f");
  });

  it("should report a type regression when pathStrs are tested in the correct order", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/orderDependentClientNotOk.json`,
      skipHigherOrder: "true",
      libraryFolder: `../res/sampleOrderDependentClient`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    //FIXME: assert that the regression has the correct structure
    assert.equal(report.typeRegressions.length, 1, "Expected one regression since model states that f is called before init");
  });

  it("should not report a type regression when writes are recorded and replayed", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/SideEffectReplayIssueModel.json`,
      skipHigherOrder: "true",
      libraryFolder: `../res/sampleSideEffectReplayIssue`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect any type regressions when writes are recorded and replayed");
  });

  //Note, this used to be an issue when we used black listing
  it("should not report an issue when side effects are applied to an argument, which is later used as a return value", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/SideEffectReplayIssue2Model.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleSideEffectReplayIssue2`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect any type regressions when side effects are applied to an argument, which is later used as a return value");
  });

  it("should not report a type regression when a callback is called with an argument of the correct type", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/CallbackArgCheckClientOkModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleCallbackArgCheckClientOk`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Unexpected type regression when calling call back with argument of the correct type");
  });

  it("should report a type regression when a callback is called with an argument of the incorrect type", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/CallbackArgCheckClientNotOkModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleCallbackArgCheckClientOk`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.equal(report.typeRegressions.length, 1, "Expected type regression when calling callback with an argument of an incorrect type");
    assert.includeDeepMembers(report.typeRegressions, [
      new TypeRegression((new AccessPath(new RequireLabel('lib'), undefined)
          .extendWithLabel(new ApplicationLabel(1, false, 4))
          .extendWithLabel(new ArgLabel(0))
          .extendWithLabel(new ApplicationLabel(1, false, 9))
          .extendWithLabel(new ArgLabel(0))),
        NumUnionType.toTypeString(),
        BoolUnionType.toTypeString())
    ], "Callback call with incorrect arguments regression did not have the correct structure");

  });

  it("should not report a type regression when using NonExisting to model non-existing properties", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/UndefinedVSNonExistingClientOkModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleUndefinedVSNonExistingClientOk`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect type regression when using NonExisting to model the non-existing property (length) on the argument a");
  });

  //FIXME:
  //This issue is no longer present after we started to lazily synthesize arguments
  //Since the "undefined" property is never synthesized
  xit("should report a type regression when using undefined to model non-existing properties", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/UndefinedVSNonExistingClientNotOkModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleUndefinedVSNonExistingClientOk`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.equal(report.typeRegressions.length, 1, "Expected type regression when using undefined to model the non-existing property (length) on the argument a");
    assert.includeDeepMembers(report.typeRegressions, [
      new TypeRegression((new AccessPath(new RequireLabel('lib'), undefined)
          .extendWithLabel(new ApplicationLabel(2, false, -1))
          .extendWithLabel(new ArgLabel(1))
          .extendWithLabel(new PropertyLabel("length"))),
        CircleTypeStr,
        UndefinedUnionType.toTypeString())
    ], "Undefined as model of non-existing property regression did not have the correct structure");

  });

  it("should not report a type regression when using IGNORE to model non-writable properties", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/IgnorePropertyClientOkModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleIgnorePropertyClientOk`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect type regression when using IGNORE to model the non-writabel property (length) on the argument cb");
  });

  //Same model as IgnorePropertyClientOk, but with length set to ◦
  //FIXME: No longer an issue after never reporting type-regressions on the read of the length property of a function
  //Whether, this is the 'correct' thing to do is arguable, and depends a bit on how the results are presented in the paper.
  //Is TRT a way to find all API changes, or just the ones that may cause problems for actual clients?
  xit("should report a type regression when not using IGNORE to model non-writable properties", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/IgnorePropertyClientNotOkModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleIgnorePropertyClientOk`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();


    //assert.equal(report.typeRegressions.length, 1, "Expected a type regression when not using IGNORE to model the non-writabel property (length) on the argument cb");
    //assert.includeDeepMembers(report.typeRegressions, [
    //  new TypeRegression((new AccessPath(new RequireLabel('lib'), undefined)
    //      .extendWithLabel(new ApplicationLabel(1, false, 4))
    //      .extendWithLabel(new ArgLabel(0))
    //      .extendWithLabel(new PropertyLabel("length"))),
    //    CircleTypeStr,
    //    NumUnionType.toTypeString(),
    //    false)
    //], "Regression of (length) property read on cb did not have the correcty structure");
  });

  it("should not report a type regression when calling toString with a proxy as receiver", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/UnproxyOnToStringClientModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleUnproxyOnToStringClient`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect type regression when calling toString with a proxy receiver since toString has been overwritten to remove proxies");
  });

  it("should not report a type regression when an array argument affects the return type and an heuristic is used to not always use the access action", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/ArrayArgAffectingReturnTypeClientOkModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleArrayArgAffectingReturnTypeClientOk`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect a type regressions when an array argument affects the return type and the access action is not used");
  });

  //Same model as ArrayArgAffectingReturnTypeClientOkModel but with the index property lookups replaced with AccessLabels
  it("should report a type regression when an array argument affects the return type and the access action is always used", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/ArrayArgAffectingReturnTypeClientNotOkModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleArrayArgAffectingReturnTypeClientOk`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isNotEmpty(report.typeRegressions, "Expected type regressions when an array argument affects the return type and the access action is always used");
  });

  it("should not check the a value written by the client using a write action", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/BlacklistingClientWrittenValuesModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleBlacklistingClientWrittenValues`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect a type regressions when the value with a type error is written by the client");
  });

  it("should check that the correct return value is picked when a callback is called multiple times with arguments of different types", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/ChooseCorrectReturnModelValueModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleChooseCorrectReturnModelValue`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect a type regressions when calling callback multiple times with arguments of different types");
  });

  it("should check that the correct return value is picked when a callback is called multiple times with arguments of different types when some properties are NON-EXISTING", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/ChooseCorrectReturnModelValue2Model.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleChooseCorrectReturnModelValue2`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect a type regressions when calling callback multiple times with arguments of different types");
  });

  it("should ...", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/ChooseCorrectReturnModelValue3Model.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleChooseCorrectReturnModelValue3`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect a type regressions when ...");
  });

  //Don't think this is relevant after we started to not proxify all known values
  xit("should check that the correct string is produced by Function.prototype.toString for objects marked as native", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/NativeToStringClientModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleNativeToStringClient`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect a type regressions when producing the correct \"native\" string for objects marked as native");
  });

  it("should synthesize callbacks that throws correctly", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/SynthesizeCallbackThatThrowsModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleSynthesizeCallbackThatThrows`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect a type regressions when synthesizing callbacks that throws correctly");
  });

  it("should use the VOP value when reading a property that was already a proxy in the NoRegrets setting", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/VOPOnPropertyReadModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleVOPOnPropertyRead`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect a type regression when reading a property that was already a proxy in the NoRegrets setting");
  });

  it("should use the VOP value when writing a property that was already a proxy in the NoRegrets setting", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/VOPOnPropertyWriteModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleVOPOnPropertyWrite`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect a type regression when writing a proxified function to a property that was already a proxy in the NoRegrets setting");
  });

  it("should correctly synthesize the intersection typed object", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/SynthesizeIntersectionTypedObjectModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleSynthesizeIntersectionTypedObject`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect a type regression when synthesizing an intersection typed object");
  });

  /**
   * FIXME: This test is not done.
   * It should test that argument writes are captured in the VOP map.
   * Alternatively, use lodash@3.2.0 -> 3.3.0 with strong-params@0.7.0
   */
  it("should correctly ...", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/ArgumentPropertyWriteModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleArgumentPropertyWrite`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect a type regression when ...");
  });

  it("should put the property in the correct object in the prototype chain", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/PropertyShouldResideOnPrototypeModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/samplePropertyShouldResideOnPrototype`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect a type regression when putting a property on the correct object in the prototype chain");
  });

  it("should use the correct objects in the prototype chain", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/SynthesizePrototypeChainModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleSynthesizePrototypeChain/`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect a type regression when putting a property on the correct object in the prototype chain");
  });

  it("should correctly restore the symbols saved in the model", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/SymbolResolutionModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleSymbolResolution`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect a type regression when correctly restoring the symbols saved in the model",);
  });

  it("should correctly type check proxies", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/TypeCheckProxiesModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleTypeCheckProxies`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect a type regression when correctly type checking ignore",);
  });

  it("should correctly type check ignored properties", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/TypecheckIgnoreModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleTypecheckIgnore`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect a type regression when correctly type checking ignore",);
  });

  it("should correctly type check exceptions", function () {
    //NOTE, the following test is mostly a test of the model generation by NoRegrets
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/ThrowAndErrorCheckModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleThrowAndErrorCheck`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect a type regression when correctly type checking exceptions",);
  });

  it("should lazily synthesize arguments such that the VOP map is as updated as possible", function () {
    //NOTE, the following test is mostly a test of the model generation by NoRegrets
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/LazyArgumentSynthesisModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleLazyArgumentSynthesis`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect a type regression when lazily synthesizing arguments",);
  });

  it("should report yet to be synthesized properties as existing when using Object.keys()", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/OwnKeysShouldReportNonSynthesizedPropertiesModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleOwnKeysShouldReportNonSynthesizedProperties`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect a type regression when reporting yet to be synthesized properties as existing");
  });

  it("should not try to use the vop map of a yet to be written property", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/NonExistProtoVOPCombiModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleNonExistProtoVOPCombi`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect a type regression when reporting yet to be synthesized properties as existing");
  });

  it("should not try to use the vop map of a yet to be written property 2", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/VOPCombiModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleVOPCombi`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect a type regression when having mulitple vop writes on the same property");
  });

  it("should not produce any type regressions when applying functions in the right order", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/ApplicationOrderClientModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleApplicationOrderClient`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect a type regression when reporting yet to be synthesized properties as existing");
  });

  it("should not produce any type regressions when applying functions with the right receiver", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/ReceiverOfCallOnProtoModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleReceiverOfCallOnProto`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect a type regression when applying functions with the right receiver");
  });

  it("should not produce any type regressions when applying bound functions with the right receiver", function () {
    //NOTE, the following test is mostly a test of the model generation by NoRegrets
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/ReceiverOfCallWithBindModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleReceiverOfCallWithBind`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect a type regression when applying bound functions with the right receiver");
  });

  it("should not produce any type regressions when converting date objects to numbers", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/ValueOfOnDateModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleValueOfOnDate`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect a type regression when converting date objects to numbers");
  });

  //Not yet supported
  xit("should not produce any type regressions when reading, then writing, and then reading the same property with different types", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/ReadWriteReadModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleReadWriteRead`,
      exactValueChecking: "false"
    },0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect a type regression when reading, then writing, and then reading the same property with different types");
  });

  //This cannot be handled unless we have a way to detect if ownKeys are called from a for-in loop
  //FIXME, This test is not correct. Create a test that actual identifies the issue.
  //NOTE, we are still missing a case showing that incorrect handling of for-in is actually an issue in practice.
  xit("should produce a type regressions when using a for-in loop to read properties on a prototype", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/KeysInObjectModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleKeysInObject`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isNotEmpty(report.typeRegressions, "Expected a type regressing when using a for-in loop to read properties on a prototype");
  });

  it("should not produce any type regressions when the library changes the prototype of a client generated object", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/RewritePrototypeTypeCheckModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleRewritePrototypeTypeCheck`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect a type regression when the library changes the prototype of a client generated object");
  });

  //FIXME: Insert description
  it("should not produce any type regressions when ...", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/NonExistWithProxyProtoModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleNonExistWithProxyProto`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();

    assert.isEmpty(report.typeRegressions, "Did not expect a type regression when ...");
  });

  it("should not have trouble synthesizing a return value when calling a callback with an array the callback iterated when the model was generated", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/FlattenIteratedArrayModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleFlattenIteratedArray`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();
    assert.isEmpty(report.typeRegressions, "Did not expect a type regression when ...");
  });

  it("Should produce a type regression when calling a callback that was not called in the model ", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/CallingUncalledFunctionModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleCallingUncalledFunction`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();
    assert.isNotEmpty(report.typeRegressions, "Expected a type regression when calling a callback that was not called in the model");
  });

  it("Should produce a type regression when reading length on a function", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/LengthOnFunctionReadModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleLengthOnFunctionRead`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();
    assert.isEmpty(report.typeRegressions, "Did not expect a type regression when reading length on a function");
  });

  it("Should produce a type regression when writing a value of a wrong type to an argument", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/CheckingArgumentWrittenValueModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleCheckingArgumentWrittenValue`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();
    assert.equal(report.typeRegressions.length, 1, "Expected a type regression when writing a value of a wrong type to an argument");
    });

    it("Should produce a type regression when writing a value of a wrong type to an argument 2", function () {
      const testRunner = new TestRunnerImpl({
        modelFile: `res/models/CheckingArgumentWrittenValue2Model.json`,
        skipHigherOrder: "false",
        libraryFolder: `../res/sampleCheckingArgumentWrittenValue2`,
      exactValueChecking: "false"
      }, 0);
      const report = testRunner.runTest();
      assert.equal(report.typeRegressions.length, 1, "Expected a type regression when writing a value of a wrong type to an argument");
    });

    it("Should not produce a type regression when reading a getter that throws", function () {
      const testRunner = new TestRunnerImpl({
        modelFile: `res/models/ClientThrowingOnPropertyReadModel.json`,
        skipHigherOrder: "false",
        libraryFolder: `../res/sampleClientThrowingOnPropertyRead`,
      exactValueChecking: "false"
      }, 0);
      const report = testRunner.runTest();
      assert.isEmpty(report.typeRegressions, "Did not expect a type regression when reading a getter that throws");
    });

  it("Should not crash when trying to read the prototype of a value changed to null or undefined", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/PrototypeOfUndefinedOrNullModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/samplePrototypeOfUndefinedOrNull`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();
    assert.isNotEmpty(report.typeRegressions, "Expected a type regression when an object changes from object to null or undefined");
  });

  it("Should be able to pick the correct return value even with the presence of nulls", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/ChooseCorrectReturnModelValue4Model.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleChooseCorrectReturnModelValue4`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();
    assert.isEmpty(report.typeRegressions, "Did not expect a type regression when synthesizing a return value in the presence of a null");
  });

  it("Should propagate side-effects on the receiver when it's used in callbacks", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/SideEffectsOnTheThisArgModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleSideEffectsOnTheThisArg`,
      exactValueChecking: "false"
    }, 0);
    const report = testRunner.runTest();
    assert.isEmpty(report.typeRegressions, "Did not expect a type regression when propagating side-effects on the receiver");
  });

  it("should not crash when an object turns undefined in an update", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/ContinueWhenObjectBecomesUndefinedModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleContinueWhenObjectBecomesUndefined`,
      exactValueChecking: "false"
    }, 0);
    let report: TestReport;
    assert.doesNotThrow(() => report = testRunner.runTest());
    assert.isNotEmpty(report.typeRegressions, "Expected a regression indicating that the value became undefined")
  });

  it("should not crash when an object turns null in an update", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/ContinueWhenObjectBecomesNullModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleContinueWhenObjectBecomesNull`,
      exactValueChecking: "false"
    }, 0);
    let report: TestReport;
    assert.doesNotThrow(() => report = testRunner.runTest());
    assert.isNotEmpty(report.typeRegressions, "Expected a regression indicating that the value became null");
  });

  /**
   * Notice, that the library writing to a property on an argument corresponds to returning a value.
   * Hence, like we distinguish two different calls to the same function using an id, we also need to distinguish two different writes using an id.
   * Otherwise, we will report a regression when a property of the first written values is missing on the second written value and so on.
   */
  it("should handle multiple different values written to the same property", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/CheckingArgumentPropertyWrittenTwiceModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleCheckingArgumentPropertyWrittenTwice`,
      exactValueChecking: "false"
    }, 0);
    let report: TestReport;
    assert.doesNotThrow(() => report = testRunner.runTest());
    assert.isEmpty(report.typeRegressions, "Did not expecting any type-regressions when distinguishing written value pathStrs");
  });

  it("should handle getters throwing correctly", function () {
    const testRunner = new TestRunnerImpl({
      modelFile: `res/models/ThrowingGetterModel.json`,
      skipHigherOrder: "false",
      libraryFolder: `../res/sampleThrowingGetter`,
      exactValueChecking: "false"
    }, 0);
    let report: TestReport;
    assert.doesNotThrow(() => report = testRunner.runTest());
    assert.isEmpty(report.typeRegressions, "Did not expecting any type-regressions when handling throwing getters correctly");
  });

  });

  describe("#synthesize", function () {
    const ap = new AccessPath(new RequireLabel("lib"), undefined);
    const testRunner = new TestRunnerImpl({modelFile: `res/models/aux-model.json`, skipHigherOrder: "true", libraryFolder: `../res/doNotLoad`, exactValueChecking: "false"}, 0);

    it("should synthesize strings correctly", function () {
      const auxReport = new TestReport([], 0);
      const valOrig = "foo";
      const mValue = new ModelValue(getRuntimeType(valOrig), some([valOrig]), ap, none, 0, false, false);
      assert.equal(testRunner.synthesize(mValue, auxReport), valOrig);
    });

    it("should synthesize objects as ArgumentProxies", function () {
      const auxReport = new TestReport([], 0);
      const valOrig = {a : 'foo'};
      const propLabel = new PropertyLabel("a");
      const propPath = ap.extendWithLabel(propLabel);
      const oModelValue = new ModelValue(getRuntimeType(valOrig), none, ap, none, 0, false, false);
      const aModelValue = new ModelValue(getRuntimeType(valOrig.a), some([valOrig.a]), propPath, none, 0, false, false);
      oModelValue.addProperty(propLabel, aModelValue);
      fillPrototypes(oModelValue);

      const synthVal = testRunner.synthesize(oModelValue, auxReport);
      assert.isTrue(synthVal[Constants.IS_PROXY_KEY], "expected synthVal to be a proxy");
      assert.equal(synthVal['a'], valOrig['a']);
    });

    it("should synthesize functions as ArgumentProxies", function () {
      const auxReport = new TestReport([], 0);
      const valOrig = function () {};
      valOrig['a'] = 'foo';
      const propLabel = new PropertyLabel("a");
      const propPath = ap.extendWithLabel(propLabel);
      const oModelValue = new ModelValue(getRuntimeType(valOrig), none, ap, none, 0, false, false);
      const aModelValue = new ModelValue(getRuntimeType(valOrig['a']), some([valOrig['a']]), propPath, none, 0, false, false);
      oModelValue.addProperty(propLabel, aModelValue);
      fillPrototypes(oModelValue);

      const synthVal = testRunner.synthesize(oModelValue, auxReport);
      assert.isTrue(getIntersectionType(synthVal).equals(FunctionType), "expected synthVal to be a function");
      assert.isTrue(synthVal[Constants.IS_PROXY_KEY], "expected synthVal to be a proxy");
      assert.equal(synthVal['a'], valOrig['a']);
    });

    it("should synthesize arrays as argumentProxies", function () {
      const auxReport = new TestReport([], 0);
      const arr = [];
      const arrModelValue = new ModelValue(getRuntimeType(arr), none, ap, none, 0, false, false);
      fillPrototypes(arrModelValue);

      const synthVal = testRunner.synthesize(arrModelValue, auxReport);
      assert.isTrue(synthVal[Constants.IS_PROXY_KEY], "expected synthVal to be a proxy");
      assert.isTrue(getIntersectionType(synthVal).equals(ArrayType), "expected synthVal to be an array");
    });

    it("should throw if trying to synthesize a primitively type ModelValue without a recorded value", function () {
      const auxReport = new TestReport([], 0);
      const strVal = "foo";
      const intVal = 42;
      const boolVal = true;
      const undefVal = undefined;
      const mValueStr = new ModelValue(getRuntimeType(strVal), none, ap, none, 0, false, false);
      const mValueInt = new ModelValue(getRuntimeType(intVal), none, ap, none, 0, false, false);
      const mValueBool = new ModelValue(getRuntimeType(boolVal), none, ap, none, 0, false, false);
      const mValueUndef = new ModelValue(getRuntimeType(undefVal), none, ap, none, 0, false, false);

      assert.throw(() => testRunner.synthesize(mValueStr, auxReport));
      assert.throw(() => testRunner.synthesize(mValueInt, auxReport));
      assert.throw(() => testRunner.synthesize(mValueBool, auxReport));
      assert.throw(() => testRunner.synthesize(mValueUndef, auxReport));
    });

    it("should synthesize union typed arrays such that elements of the correct type are correctly distributed amongst the elements", function () {
      const auxReport = new TestReport([], 0);
      const useAccessActionOld = GlobalState.useAccessActions;
      GlobalState.useAccessActions = true;
      const valOrig = [42, 'foo', 41, 'bar', true];
      const mVal = new ModelValue(getRuntimeType(valOrig), none, ap, none, 0, false, false);
      const uType = new UnionType();
      uType.addType(getRuntimeType(42));
      uType.addType(getRuntimeType('foo'));
      uType.addType(getRuntimeType(true));
      mVal.addProperty(new AccessLabel(), new ModelValue(uType, option([42, 'foo', true]), ap.extendWithLabel(new AccessLabel()), none, 0, false, false));
      const length = valOrig.length;
      const lengthLab = new PropertyLabel('length');
      mVal.addProperty(lengthLab, new ModelValue(getRuntimeType(length), option([length]), ap.extendWithLabel(lengthLab), none, 0, false, false));
      fillPrototypes(mVal);

      const synthVal = testRunner.synthesize(mVal, auxReport);
      assert.isTrue(synthVal[Constants.IS_PROXY_KEY], "expected synthVal to be a proxy");
      assert.isTrue(getIntersectionType(synthVal).equals(ArrayType), "expected synthVal to be an array");
      assert.isTrue(synthVal.length === 5, "expected synthesized array to have same length as original array");
      assert.includeMembers(synthVal[Constants.GET_PROXY_TARGET], [42, 'foo', true]);
      GlobalState.useAccessActions = useAccessActionOld;
    });

    it("should synthesize union typed arrays without length field with one element for each of the `union'ed` terms", function () {
      const auxReport = new TestReport([], 0);
      const useAccessActionOld = GlobalState.useAccessActions;
      GlobalState.useAccessActions = true;
      const mVal = new ModelValue(getRuntimeType([]), none, ap, none, 0, false, false);
      const uType = new UnionType();
      uType.addType(getRuntimeType(42));
      uType.addType(getRuntimeType('foo'));
      uType.addType(getRuntimeType(true));
      mVal.addProperty(new AccessLabel(), new ModelValue(uType, option([42, 'foo', true]), ap.extendWithLabel(new AccessLabel()), none, 0, false, false));
      fillPrototypes(mVal);

      const synthVal = testRunner.synthesize(mVal, auxReport);
      assert.isTrue(synthVal[Constants.IS_PROXY_KEY], "expected synthVal to be a proxy");
      assert.isTrue(getIntersectionType(synthVal).equals(ArrayType), "expected synthVal to be an array");
      assert.isTrue(synthVal.length === 3, "expected synthesized array to have length equal to the number subterms in the union type");
      assert.includeMembers(synthVal[Constants.GET_PROXY_TARGET], [42, 'foo', true]);
      GlobalState.useAccessActions = useAccessActionOld;
    });

    // When an array is modelled using both access actions and precise values/types for specific indices,
    // then we want to use the specific types/values when possible and fallback to the access abstraction
    // based synthesis for the remanining indices.
    it("should synthesize arrays with both precise element modelling and access actions correctly", function() {
      const auxReport = new TestReport([], 0);
      const useAccessActionOld = GlobalState.useAccessActions;
      GlobalState.useAccessActions = true;
      const origArray = ["foo", "bar", 42, true];
      const mVal = new ModelValue(getRuntimeType([]), none, ap, none, 0, false, false);
      const uType = new UnionType();
      uType.addType(getRuntimeType('foo'));
      uType.addType(getRuntimeType(42));
      uType.addType(getRuntimeType(true));
      mVal.addProperty(new AccessLabel(), new ModelValue(uType, option(['foo', 42, true]), ap.extendWithLabel(new AccessLabel()), none, 0, false, false));
      mVal.addProperty(new PropertyLabel(1), new ModelValue(getRuntimeType("bar"), option(['bar']), ap.extendWithLabel(new PropertyLabel(1)), none, 1, false, false));
      const lengthLab = new PropertyLabel('length');
      mVal.addProperty(lengthLab, new ModelValue(getRuntimeType(4), option([4]), ap.extendWithLabel(lengthLab), none, 0, false, false));
      fillPrototypes(mVal);

      const synthVal = testRunner.synthesize(mVal, auxReport);
      assert.equal(synthVal.length, 4, "expected synthesized array to have length equal to the number subterms in the union type");
      assert.equal(synthVal[1], "bar", "The model puts `bar` at the first position, so we expect to find it there");

      GlobalState.useAccessActions = useAccessActionOld;
    });

    //Test inserted to debug a bug found in a benchmark
    it("should synthesize arrays correctly", function () {
      const auxReport = new TestReport([], 0);
      const useAccessActionOld = GlobalState.useAccessActions;
      GlobalState.useAccessActions = true;
      const strVal = 'Section 1';
      const origArray = [strVal, strVal];
      const mVal = new ModelValue(getRuntimeType(origArray), none, ap, none, 0, false, false);
      const length = origArray.length;
      const lengthLab = new PropertyLabel('length');

      mVal.addProperty(lengthLab, new ModelValue(getRuntimeType(length), option([length]), ap.extendWithLabel(lengthLab), none, 0, false, false));
      mVal.addProperty(new AccessLabel(), new ModelValue(getRuntimeType(strVal), option([strVal]), ap.extendWithLabel(new AccessLabel()), none, 0, false, false));
      fillPrototypes(mVal);

      const synthVal = testRunner.synthesize(mVal, auxReport);
      assert.isTrue(synthVal[Constants.IS_PROXY_KEY], "expected synthVal to be a proxy");
      assert.isTrue(getIntersectionType(synthVal).equals(ArrayType), "expected synthVal to be an array");
      assert.isTrue(synthVal.length === length, "expected synthesized array to have same length as original array");
      assert.deepEqual(synthVal, origArray, "Synthesized array must equal the original array");
      GlobalState.useAccessActions = useAccessActionOld;
    });

    //`constructor` is a knownValue so it shouldn't be synthesized
    it ("should ignore the constructor property when synthesizing functions", function() {
      const auxReport = new TestReport([], 0);
      const origFun = function () {};
      const mVal = new ModelValue("function", none, ap, none, 0, false, false);
      fillPrototypes(mVal);
      const constructorLab = new PropertyLabel('constructor');
      mVal.addProperty(constructorLab, new ModelValue("function", none, ap.extendWithLabel(constructorLab), none, 0, true, false));
      const synthVal = testRunner.synthesize(mVal, auxReport);
      assert.equal(origFun.constructor, synthVal.constructor, "The constructor should not be changed even though a read of it appears in the model");
    });

    it("should use the values in the vopMap if they exist", function() {
      const auxReport = new TestReport([], 0);
      const origVal = {};
      const mVal = new ModelValue(getRuntimeType(origVal), none, ap, some(ap), 0, false, false);
      const vopMap = new Map<string, Option<any>>();
      testRunner.addVopValue(ap, origVal);
      const vopVal = testRunner.synthesize(mVal, auxReport);
      assert.equal(vopVal, origVal, "Expected synthesizer to use value in vopMap");
      testRunner.deleteVop(ap);
    });
  });
});

