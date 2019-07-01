import {APIModel} from "../../API-tracer/src/tracing-results";
import {AccessPath, ApplicationLabel, ArgLabel, Label, PropertyLabel, RequireLabel} from "../../API-tracer/src/paths";
import {ArgumentProxyHandler} from "../src/argumentProxyHandler";
import {TestReport, TypeRegression} from "../src/testResult";
import {Constants} from "../../API-tracer/src/constants";
import {
  ArrayType,
  UnionType
} from "../../API-tracer/src/runtime-types";
import {KnownValuesNode} from "../../API-tracer/src/known-values";
import {ReadObservation} from "../../API-tracer/src/observations";
import {none} from "ts-option";
import {lookupPropAux, TestRunnerStub} from "./mocks";
import {getRuntimeType, UndefinedUnionType} from "../../API-tracer/src/runtime-types-operations";
import {ModelValue} from "../src/modelValue";

const modelFile = `${__dirname}/../res/models/ClientWithOkLibModel.json`;
const assert = require('chai').assert;
const fs = require("fs");

describe('APIModel', function() {
  it ("should have the expected structure", function() {
    const model = APIModel.fromJson(JSON.parse(fs.readFileSync(modelFile)));
    /**
     * The model is of the following form
     * require(lib) -> object
     * require(lib).f -> object ∧ function
     * require(lib).g -> object ∧ function
     * require(lib).h -> object ∧ function
     * require(lib).a -> number
     * require(lib).f(1) -> boolean
     * require(lib).f(1).arg1 -> object ∧ function
     * require(lib).g(1) -> boolean
     * require(lib).g(1).arg1 -> object
     * require(lib).g(1).arg1.f -> object ∧ function
     * require(lib).h(1) -> boolean
     * require(lib).h(1).arg1 -> number
     */
    assert.isOk(model.observations.find(o => o.path.toString() === "require(lib)"
      && (o as ReadObservation).type === "object"));
    assert.isOk(model.observations.find(o => o.path.toString() === "require(lib).f"
      && (o as ReadObservation).type.includes("function")));
     assert.isOk(model.observations.find(o => o.path.toString() === "require(lib).f.Symbol(__proto__)"
      && (o as ReadObservation).type.includes("function")));
    assert.isOk(model.observations.find(o => o.path.toString() === "require(lib).f.Symbol(__proto__).Symbol(__proto__)"
      && (o as ReadObservation).type.includes("object")));
    assert.isOk(model.observations.find(o => o.path.toString() === "require(lib).g"
      && (o as ReadObservation).type.includes("function")));
    assert.isOk(model.observations.find(o => o.path.toString() === "require(lib).g.Symbol(__proto__)"
      && (o as ReadObservation).type.includes("function")));
    assert.isOk(model.observations.find(o => o.path.toString() === "require(lib).g.Symbol(__proto__).Symbol(__proto__)"
      && (o as ReadObservation).type.includes("object")));
    assert.isOk(model.observations.find(o => o.path.toString() === "require(lib).h"
      && (o as ReadObservation).type.includes("function")));
    assert.isOk(model.observations.find(o => o.path.toString() === "require(lib).h.Symbol(__proto__)"
      && (o as ReadObservation).type.includes("function")));
    assert.isOk(model.observations.find(o => o.path.toString() === "require(lib).h.Symbol(__proto__).Symbol(__proto__)"
      && (o as ReadObservation).type.includes("object")));
    assert.isOk(model.observations.find(o => o.path.toString() === "require(lib).a"
      && (o as ReadObservation).type.includes("number")));
    assert.isOk(model.observations.find(o => o.path.toString() === "require(lib).f.(1)[id: 6]"
      && (o as ReadObservation).type === "boolean"));
    assert.isOk(model.observations.find(o => o.path.toString() === "require(lib).g.(1)[id: 15]"
      && (o as ReadObservation).type === "boolean"));
    assert.isOk(model.observations.find(o => o.path.toString() === "require(lib).h.(1)[id: 26]"
      && (o as ReadObservation).type === "boolean"));
    assert.isOk(model.observations.find(o => o.path.toString() === "require(lib).f.(1)[id: 6].arg0"
      && (o as ReadObservation).type.includes("function")));
    assert.isOk(model.observations.find(o => o.path.toString() === "require(lib).f.(1)[id: 6].arg0.Symbol(__proto__)"
      && (o as ReadObservation).type.includes("function")));
    assert.isOk(model.observations.find(o => o.path.toString() === "require(lib).f.(1)[id: 6].arg0.Symbol(__proto__).Symbol(__proto__)"
      && (o as ReadObservation).type.includes("object")));
    assert.isOk(model.observations.find(o => o.path.toString() === "require(lib).g.(1)[id: 15].arg0"
      && (o as ReadObservation).type === "object"));
    assert.isOk(model.observations.find(o => o.path.toString() === "require(lib).g.(1)[id: 15].arg0.f"
      && (o as ReadObservation).type.includes("function")));
    assert.isOk(model.observations.find(o => o.path.toString() === "require(lib).g.(1)[id: 15].arg0.f.Symbol(__proto__)"
      && (o as ReadObservation).type.includes("function")));
    assert.isOk(model.observations.find(o => o.path.toString() === "require(lib).g.(1)[id: 15].arg0.f.Symbol(__proto__).Symbol(__proto__)"
      && (o as ReadObservation).type.includes("object")));
    assert.isOk(model.observations.find(o => o.path.toString() === "require(lib).h.(1)[id: 26].arg0"
      && (o as ReadObservation).type.includes("number")));
  });
});

describe('ModelValue', function() {
  const model = APIModel.fromJson(JSON.parse(fs.readFileSync(modelFile)));
  const root = ModelValue.buildModel(model);

  describe('structure of API Model', function() {
    assert.isOk(root);
    const f = lookupPropAux(root, "f");
    const g = lookupPropAux(root, "g");
    const h = lookupPropAux(root, "h");
    const a = lookupPropAux(root, "a");
    assert.isOk(f, "fail1");
    assert.isOk(g, "fail2");
    assert.isOk(h, "fail3");
    assert.isOk(a, "fail4");
    const fApp = lookupPropAux(f, "(1)[id: 6]");
    const gApp = lookupPropAux(g, "(1)[id: 15]");
    const hApp = lookupPropAux(h, "(1)[id: 26]");
    assert.isOk(fApp, "fail5");
    assert.isOk(gApp, "fail6");
    assert.isOk(hApp, "fail7");
    const fAppArg = lookupPropAux(fApp, "arg0");
    const gAppArg = lookupPropAux(gApp, "arg0");
    const hAppArg = lookupPropAux(hApp, "arg0");
    assert.isOk(fAppArg, "fail8");
    assert.isOk(gAppArg, "fail9");
    assert.isOk(hAppArg, "fail10");
    const gAppArgF = lookupPropAux(gAppArg, "f");
    assert.isOk(gAppArgF, "fail11");
  });

  describe('#isHigherOrderFunction', function() {
    it('should be higher-order if a parameter is a callback', function() {
      const funcApp = lookupPropAux(lookupPropAux(root, "f"), "(1)[id: 6]");
      assert(funcApp.isHigherOrderFunction(), "f takes a callback");
    });
    it('should be higher-order if a parameter has a method', function() {
      const funcApp = lookupPropAux(lookupPropAux(root, "g"), "(1)[id: 15]");
      assert(funcApp.isHigherOrderFunction(), "g takes an argument with a method");
    });
    it('should not be a higher-order function if the argument is a number', function() {
      const funcApp = lookupPropAux(lookupPropAux(root, "h"), "(1)[id: 26]");
      assert.isNotOk(funcApp.isHigherOrderFunction(), "h is not higher-order");
    });
  });
});


describe("argumentProxy", function () {
  describe("#get", function () {
    const o = {foo: 'str'};
    const ap = new AccessPath(new RequireLabel("foo"), undefined)
      .extendWithLabel(new ApplicationLabel(1, false, 0))
      .extendWithLabel(new ArgLabel(0));
    const report = new TestReport([], 0);
    const propLabels = [new PropertyLabel('foo')];
    const mVal = new ModelValue(getRuntimeType(o), none, ap, none, 42, false, false);
    mVal.addProperty(new PropertyLabel("foo"), new ModelValue(getRuntimeType(42), none, ap, none, 4, false, false));
    const argProxy = new Proxy(o, new ArgumentProxyHandler(mVal, report, new TestRunnerStub()));

    it("should not report a type if reading a legal property", function () {
      argProxy['foo'];
      assert.isEmpty(report.typeRegressions, "Unexpected type regression for legal read of argument property");
    });

    it ("should not report a regressions when reading a white listed property on an argument", function () {
      argProxy['toString'];
      argProxy['Symbol(toString)'];
      argProxy['Symbol(Symbol.toStringTag)'];
      argProxy['exec'];
      argProxy['Symbol(exec)'];
      argProxy['Symbol(Symbol.exec)'];
      assert.isEmpty(report.typeRegressions, "Unexpected type regression for white listed property read");
    });

    it("should not report a regression when reading a known value", function () {
      KnownValuesNode.init();
      argProxy['constructor'];
      assert.isEmpty(report.typeRegressions, "Unexpected type regression when reading known value");
    });

    it("should report a regression if reading an illegal property", function () {
      argProxy['bar'];
      assert.isNotEmpty(report.typeRegressions, "Missing type regression for illegal read of argument property");
      assert.deepEqual(report.typeRegressions[0],
        new TypeRegression(ap.extendWithLabel(
          new PropertyLabel('bar')),
          Constants.CIRC_TYPE,
          UndefinedUnionType.toTypeString()), "TypeRegression for illegal read of argument property did not have the expected structure");
    });
  });
});



