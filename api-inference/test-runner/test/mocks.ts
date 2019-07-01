/**
 * Since properties are modeled in a Label -> ModelValue map, we need
 * a reference to the right label to lookup a property.
 * However, for convenience we have this function that
 * does the lookup based on the name of the property.
 * Notice, it's inefficient and should therefore only
 * be used for testing purposes.
 * @returns {ModelValue | undefined}
 */
import {none, Option} from "ts-option";

import {LearnHandler} from "../../API-tracer/src/learn-handler";
import {
  AccessPath,
  ArgLabel,
  ProtoLab,
  RequireLabel
} from "../../API-tracer/src/paths";
import {getBuiltInProtoCount} from "../../API-tracer/src/runtime-types";
import {ArgumentProxyHandler} from "../src/argumentProxyHandler";
import {TypeChecker} from "../src/checker";
import {ModelValue} from "../src/modelValue";
import {ModelValueTest, TestRunner} from "../src/testRunner";
import {TestReport} from "../src/testResult";

export function lookupPropAux(m: ModelValue, p: string): ModelValue|undefined {
  for (var labValPair of m.getProperties()) {
    if (labValPair[0].toString().includes(p)) {
      return labValPair[1] as ModelValue;
    }
  }
  return undefined;
}

export class TestRunnerStub implements TestRunner {
  addVopValue(path: AccessPath|string, value: any): void {}

  isVop(path: AccessPath|string): boolean { return false; }

  runTest(): TestReport { return undefined; }

  deleteVop(path: AccessPath): void {}

  synthesize(modelValue: ModelValue, report: TestReport): any {}

  addModelValueTest(modelValueTest: ModelValueTest): void {}

  testModelValue(mValue: ModelValue, runtimeValue: any, receiver: Option<any>,
                 report: TestReport, fromThrow: boolean) {}

  getGreatestCheckedId(): number {
    return 0;
  }
}

// Add the default prototypes to the models (Saves a lot of boilerplate code)
export function fillPrototypes(val: ModelValue) {
  const ap = new AccessPath(new RequireLabel("lib"), undefined);
  const protos = getBuiltInProtoCount(val.type.get(0)).getOrElse(0);
  let protoPoint = val;
  for (let i = 0; i < protos; i++) {
    const emptyProto = new ModelValue(TypeChecker.extractType({}), none,
                                      ap.extendWithLabel(ProtoLab), none, 42,
                                      false, false, false);
    protoPoint.addProperty(ProtoLab, emptyProto);
    protoPoint = emptyProto;
  }
  // Recurse
  Array.from(val.getPropertyKeys()).forEach(prop => {
    if (!prop.equals(ProtoLab)) fillPrototypes(val.getPropertyModel(prop))
  });
}

/**
 * Return an ArgumentProxyHandler on the path require(lib).arg0
 */
export function createAuxArgumentProxyHandler() {
  const ap = new AccessPath(new RequireLabel("lib"), undefined)
                 .extendWithLabel(new ArgLabel(0));
  const mv = new ModelValue("object", none, ap, none, 1, false, false, false);
  fillPrototypes(mv);

  return new ArgumentProxyHandler(mv, new TestReport([], 0), new TestRunnerStub());
}

export function createAuxPath(): AccessPath {
  return new AccessPath(new RequireLabel("lib"), undefined);
}
