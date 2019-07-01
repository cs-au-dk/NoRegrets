import {
  Observation,
  ObservationAuxInfo
} from "../../API-tracer/src/observations";
import {AccessPath} from "../../API-tracer/src/paths";
import {ProxyOperations} from "../../API-tracer/src/ProxyOperations";
import {
  IntersectionType,
  RuntimeType,
  UnionType
} from "../../API-tracer/src/runtime-types";
import {
  getRuntimeType,
  isPrimitive
} from "../../API-tracer/src/runtime-types-operations";

import {ModelValue} from "./modelValue";
import {TestReport, TypeRegression} from "./testResult";

export interface Checker {
  checkValue(modelValue: ModelValue, runtimeValue: any, report: TestReport,
             path: AccessPath);
}

export class TypeChecker implements Checker {
  public static extractType(val: any): RuntimeType {
    if (ProxyOperations.isProxy(val)) {
      return getRuntimeType(ProxyOperations.getTarget(val));
    }
    return getRuntimeType(val);
  }

  public checkValue(modelValue: ModelValue, runtimeValue: any,
                    report: TestReport, path: AccessPath) {
    const observedType = TypeChecker.extractType(runtimeValue);
    const modelType = modelValue.type;
    if (!modelType.subType(observedType)) {
      const observedUnionType = new UnionType();
      observedUnionType.addType(observedType);
      report.ignorePath(path);
      report.addTypeRegression(new TypeRegression(
          path, modelType.toTypeString(), observedUnionType.toTypeString()));
      return;
    }

    // We report it as a type regression if an object becomes null
    // Strictly, this is not a type change since `typeof null` is `object`
    // However, it is often breaking if a change like this occurs
    if (runtimeValue === null &&
        (modelValue.values.isEmpty ||
         !modelValue.values.get.find(
             p => ObservationAuxInfo.retrieveOriginalValue(p) === null))) {
      report.ignorePath(path);
      // We use the special type 'null' since we want to avoid getting
      // the strange looking "object !<: object" type regression,
      // which could happen if we just take `typeof null`.
      report.addTypeRegression(
          new TypeRegression(path, modelType.toTypeString(), "null"));
    }
  }
}

/**
 * Require exact equivalence for primitive values
 */
export class ValueChecker implements Checker {
  private typeChecker = new TypeChecker();

  checkValue(modelValue: ModelValue, runtimeValue: any, report: TestReport,
             path: AccessPath) {
    const modelTypeStr = modelValue.type.toTypeString();
    if (isPrimitive(TypeChecker.extractType(runtimeValue))) {
      if (modelValue.values.isEmpty) {
        report.addTypeRegression(
            new TypeRegression(path, modelTypeStr, `${runtimeValue}`));
      } else {
        const modelPrimitiveValue = modelValue.values.get.map(
            val => ObservationAuxInfo.retrieveOriginalValue(val));
        if (!modelPrimitiveValue.includes(runtimeValue)) {
          report.addTypeRegression(new TypeRegression(
              path, `{${modelPrimitiveValue.join(", ")}}`, `${runtimeValue}`));
        }
      }
    } else {
      this.typeChecker.checkValue(modelValue, runtimeValue, report, path);
    }
  }
}
