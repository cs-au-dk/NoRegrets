import {none, option, Option, some} from "ts-option";

import {IDictionary} from "./common-utils";
import {
  AccessPath,
} from "./paths";
import {IGNORE, NON_EXIST, RuntimeType} from "./runtime-types";

import _ = require("lodash");
import CRJSON = require('circular-json');
import {isNullOrUndefined} from "./JSAux";

/**
 * We need NULL_STRING and UNDEF_STRING since there is no undefined in JSON
 * FUNC_STRING is used to indicate function writes in the model
 */
const NULL_STRING = "__NULL__";
const UNDEF_STRING = "__UNDEFINED__";
const FUNC_STRING = "__FUNCTION__";

/**
 * Type of primitive values
 * void represents absence of a value
 */
export type primitive = number|boolean|symbol|string;

/**
 * IMPORTANT:
 * It seems odd that we have a class that expects parameters which are also
 * accessable on the GlobalState object However, importing the global state will
 * make ReadObservation dependent on Node.js, which means that ReadObservation
 * cannot be used by Angular. It is therefore important that this file doesn't
 * important anything that requires Node
 */
export class ObservationAuxInfo {
  private observationCount: number = 0;
  constructor(public collectPrimitiveValues: boolean,
              public orderedObservations: boolean) {}

  boxValue(val: any): Option<primitive> {
    if (this.collectPrimitiveValues) {
      let valueType = typeof val;
      switch (valueType) {
        case "number":
        case "boolean":
        case "symbol":
        case "string":
          if (val === NON_EXIST) {
            return some(NON_EXIST);
          }
          if (val === IGNORE) {
            return some(IGNORE);
          }
          return some(val);
        case "undefined":
          return some(UNDEF_STRING);
        default: {
          if (val === null) {
            return some(NULL_STRING);
          }
          return none;
        }
      }
    } else {
      return none;
    }
  }

  static retrieveOriginalValue(val: primitive) {
    switch (val) {
      case NULL_STRING:
        return null;
      case UNDEF_STRING:
        return undefined;
      default:
        return val;
    }
  }

  getObsId(): Option<number> {
    return this.orderedObservations ? some(++this.observationCount) : none;
  }
}

/**
 * The number is a (starting from 0) number providing an ordering of when
 * observations were added.
 */
export abstract class Observation {
  jsonClass: "ReadObservation"|"WriteObservation";
  stack: string;
  protected constructor(
      klass,
      public id: Option<number>,
      public path: AccessPath,
      public valueOriginPath: Option<AccessPath>,
      // True if observation was created by the handleHas function in the
      // LearnHandler We prioritize observations that are not created by the
      // hasHandler since they may contain important VOP info
  ) {
    this.jsonClass = klass;
  }

  addStack(s: string) { this.stack = s; }

  abstract toJson(): any;

  abstract uniquenessIdentifier(): string;

  static fromJson(i): Observation {
    if (i.jsonClass === "ReadObservation") {
      return ReadObservation.fromJson(i);
    } else if (i.jsonClass === "WriteObservation") {
      return WriteObservation.fromJson(i);
    }
    throw new TypeError(
        `Trying to parse Observation json ${i} with unknown jsonClass value`);
  }
}
/**
 *  @deprecated("We use write labels, vops, proxies and the standard labels to
 * model written values")
 */
export class WriteObservation extends Observation {
  constructor(id: Option<number>, public path: AccessPath,
              public writtenValue: any, valueOriginPath: Option<AccessPath>) {
    super("WriteObservation", id, path, valueOriginPath);
  }

  static fromJson(i): WriteObservation {
    const writtenValue = (function(value) {
      switch (value.type) {
        case "undefined":
          return "undefined";
        case "function":
          return function() { return "WRITTEN FUNCTION" };
        case "object":
          if (value.value == NULL_STRING) {
            return null
          } else {
            return CRJSON.parse(value.value, function(key, value: any) {
              switch (value) {
                case UNDEF_STRING:
                  return undefined;
                case NULL_STRING:
                  return null;
                case FUNC_STRING:
                  return function() { return "WRITTEN FUNCTION" };
              }
            });
          }
        default:
          return value.value;
      }
    })(i.writtenValue);
    const vop = i.valueOriginPath !== undefined
                    ? some(AccessPath.fromJson(i.valueOriginPath))
                    : none;
    return new WriteObservation(option(i.id), AccessPath.fromJson(i.path),
                                writtenValue, vop);
  }

  toJson(): any {
    let oRes: any = {};
    if (this.id.isDefined) {
      oRes.id = this.id.get;
    }
    oRes.path = this.path.toJson();
    oRes.writtenValue = {
      type : typeof this.writtenValue,
      value : (function(writtenValue) {
        switch (typeof writtenValue) {
          case "function":
            return FUNC_STRING;
          case "undefined":
            return UNDEF_STRING;
          case "object":
            if (writtenValue == null) {
              return null;
            } else {
              return CRJSON.stringify(writtenValue, function(key, val) {
                switch (typeof val) {
                  case "undefined":
                    return UNDEF_STRING;
                  case "function":
                    return FUNC_STRING;
                  case "object":
                    if (val == null) return NULL_STRING;
                  default:
                    return val;
                }
              });
            }
          default:
            return writtenValue;
        }
      })(this.writtenValue)
    };
    if (this.valueOriginPath.isDefined) {
      oRes.valueOriginPath = this.valueOriginPath.get.toJson();
    } else {
      oRes.valueOriginPath = undefined;
    }
    oRes.jsonClass = this.jsonClass;
    return oRes;
  }

  // Currently, we keey the most recent write to any path.
  // We may need to add the id to the uniquenessIdentifier if we have to keep
  // mulitple writes on the same path Note, however, that this could result in
  // overly large models
  uniquenessIdentifier(): string { return this.path.toString(); }
}

/**
 * An read observation represents what is in the ECOOP18 paper described as a
 * Path -> Type. In this version of type regression it has been extended with
 * additional information
 *
 * The value is the concrete runtime value if the type is primitive
 * For non-primitive types (eg., function/object), it's either an explicit
 * undefined or not present propertyTypes are not currently used, but we keep
 * them for backwards compatibility reasons.
 *
 * valueOriginPath is the path originally assigned to a value that is being
 * unproxied and the side effects that has occured on the proxied side since
 * it's passing the library-client barrier a second time. This value is needed
 * to track the origin of the value if we later need to use it as an argument.
 *
 */
export class ReadObservation extends Observation {
  constructor(
      public type: RuntimeType, path: AccessPath,
      public propertyTypes: IDictionary<RuntimeType>|undefined,
      public value: Option<primitive>, id: Option<number>,
      valueOriginPath: Option<AccessPath>, public isKnownValue: boolean,
      public isNative: boolean|
      undefined,  // undefined to be backwards compatible with old observations
      public didThrow: boolean|undefined) {
    super("ReadObservation", id, path, valueOriginPath);
  }

  toString(): string { return `${this.path} -> ${this.type}`; }

  static fromJson(i): ReadObservation {
    let path = AccessPath.fromJson(i.path);
    const vop = i.valueOriginPath !== undefined
                    ? some(AccessPath.fromJson(i.valueOriginPath))
                    : none;

    // We do not serialize/deserialize the formHas field since it's only
    // important in the model generation phase.
    let obs = new ReadObservation(i.type, path, i.propertyTypes,
                                  option(i.value), some(i.id), vop,
                                  i.isKnownValue, i.isNative, i.didThrow);
    obs.stack = i.stack;
    if (!isNullOrUndefined(obs.propertyTypes)) {
      for (let p of Object.keys(obs.propertyTypes)) {
        obs.propertyTypes[p] = obs.propertyTypes[p];
      }
    }
    return obs;
  }

  toJson(): any {
    let pt = {};

    if (!isNullOrUndefined(this.propertyTypes)) {
      for (let k of Object.keys(this.propertyTypes)) {
        pt[k] = this.propertyTypes[k].toString();
      }
    }

    const oRes: any = {};
    oRes.type = this.type.toString();
    oRes.path = this.path.toJson();
    oRes.stack = this.stack;
    oRes.propertyTypes = pt;
    oRes.isKnownValue = this.isKnownValue;
    oRes.isNative = this.isNative;
    oRes.didThrow = this.didThrow;

    if (this.value.isDefined) {
      oRes.value = this.value.get;
    }

    if (this.id.isDefined) {
      oRes.id = this.id.get;
    } else {
      oRes.id = undefined;
    }

    if (this.valueOriginPath.isDefined) {
      oRes.valueOriginPath = this.valueOriginPath.get.toJson();
    } else {
      oRes.valueOriginPath = undefined;
    }
    oRes.jsonClass = this.jsonClass;

    return oRes;
  }

  uniquenessIdentifier(): string {
    return this.path.toString() + ":" + this.type.toString();
  }
}

// Note, we need this key to avoid the se[__proto__] = ...
// which will change the type of the SideEffect object and
// thereby remove the toJson method from object.
export const PROTO_SE_KEY = "___proto__";
export class SideEffects {
  [key: string]: any

      static fromJson(i): SideEffects {
    const se = new SideEffects();
    const unparsedSE = CRJSON.parse(i);
    for (const p in unparsedSE) {
      se[p] = unparsedSE[p];
    }
    return se;
  }

  toJson(): any {
    let oRes: any = {};
    for (const p in this) {
      oRes[p] = this[p];
    }
    return CRJSON.stringify(oRes);
    // return oRes;
  }
}

export class ValueOriginInfo {
  constructor(public valueOriginPath: AccessPath,
              public sideEffects: SideEffects) {}

  static fromJson(i): ValueOriginInfo {
    const se = SideEffects.fromJson(i.sideEffects);
    const vop = AccessPath.fromJson(i.valueOriginPath);
    return new ValueOriginInfo(vop, se);
  }

  toJson(): any {
    return {
      valueOriginPath : this.valueOriginPath.toJson(),
      sideEffects : this.sideEffects.toJson()
    };
  }

  toString(): string {
    return `path: ${this.valueOriginPath.toString()}\tsideEffects: ${
        _.join(Object.getOwnPropertyNames(this.sideEffects), ", ")}`
  }
}