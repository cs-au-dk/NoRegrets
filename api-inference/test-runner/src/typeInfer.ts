import {allInheritedKeys} from "../../API-tracer/src/JSAux";
import {
  AccessPath,
  ApplicationLabel,
  Label,
  PropertyLabel,
  ProtoLab,
  WriteLabel
} from "../../API-tracer/src/paths";
import {ProxyOperations} from "../../API-tracer/src/ProxyOperations";
import {RuntimeType} from "../../API-tracer/src/runtime-types";
import {
  getRuntimeType,
  IgnoreUnionType,
  isPrimitive,
  NonExistingUnionType
} from "../../API-tracer/src/runtime-types-operations";

import {ArgumentProxyHandler} from "./argumentProxyHandler";
import {ModelValue} from "./modelValue";
import _ = require("lodash");
import {ObservationAuxInfo} from "../../API-tracer/src/observations";
import {Constants} from "../../API-tracer/src/constants";
import * as assert from "assert";

type StrucType = RuntimeType|Map<string|symbol, RuntimeType|any>|AccessPath|
    "Circular";  // We use type 'any' since infinite recursive types aren't
                 // supported by TypeScript

export class TypeInfer {
  /**
   * Finds the matching return model value for a given set of args.
   *
   * For example, say the callback function 'f' has been called with the
   * argument {a : 42} represented by model value M1 in the model and with
   * argument {a : 'foo'} represented by model value M2. We need to synthesize
   * the correct return value when 'f' is called in the testRunner, so we must
   * determine if the actual argument corresponds to M1 or M2. This is done by
   * creating a StrucType for the actual argument and then matching it with M1
   * and M2, in the hope that it will exclude one of them.
   *
   * Notice, we do something similar with writes to client supplied arguments
   * since the written value can be viewed as an argument from the library to
   * the function.
   *
   * If there are multiple matching modelValues then we pick the one that has an
   * id greater than and closets to the greatestCheckedId.
   *
   * @param args The args can either represent actual args from an application
   * or just a singleton-list with the value written to a property.
   * @param modelValue
   * @param toCheckLabels
   * @param greatestCheckedId
   */
  public static findMatchingModelValue(args: any[], modelValue: ModelValue,
                                       toCheckLabels: ApplicationLabel[]|
                                       WriteLabel[],
                                       greatestCheckedId: number): ModelValue {
    let filteredCalledLabels;
    if (toCheckLabels.length > 1) {
      const argStructTypes =
          args.map((arg) => TypeInfer.computeStrucType(arg, new WeakSet()));

      filteredCalledLabels =
          _.filter(toCheckLabels, function(label: ApplicationLabel) {
            const applicationMVal = modelValue.getPropertyModel(label);
            const argLabs = applicationMVal.getArgumentLabelsSorted();
            for (var i = 0; i < argLabs.length; i++) {
              const argMVal = applicationMVal.getPropertyModel(argLabs[i]);

              function cmpMValWithStrucType(mVal: ModelValue,
                                            t: StrucType): boolean {
                const mValType = mVal.type;

                // CIRCULAR
                if (t === "Circular") {
                  return true;
                }
                // PRIMITIVE
                if (isPrimitive(mValType)) {
                  return !(t instanceof Map) &&
                         mValType.contains(t as RuntimeType);
                }
                // OBJECT
                else {
                  if (mVal.valueOriginPath.isDefined) {
                    return t instanceof AccessPath &&
                           mVal.valueOriginPath.get.toString() ==
                               (t as AccessPath).toString();
                  }

                  // Handle the special case where `t` is of type 'object',
                  // which indicates that the argument is null. Otherwise,
                  // `t` will be a map.
                  if (t === "object") {
                    return mVal.values.isDefined &&
                           mVal.values.get.some(
                               v => ObservationAuxInfo.retrieveOriginalValue(
                                        v) === null);
                  }

                  let propLabs = mVal.getLabelsOfType(
                      PropertyLabel);  //.filter(prop => prop.getProperty()
                                       //!== Constants.PROTO_SYM);

                  // See the definition of isIteratedArray for an
                  // explanation of the why iterated arrays need to be
                  // handled as special cases. In short, when an array is
                  // iterated, the content of the array will be modelled as
                  // calls to the iter function instead of direct writes.
                  // Therefore, it's not trivial to compare the modelValue
                  // one to one with the argStructType. The hope is that we
                  // can just ignore the contents of the array in these
                  // cases. We do that by ignoring the prototype since the
                  // iterator symbol seems to always be one of the
                  // prototypes. This is slightly overkill, but we will use
                  // it as a heuristic to begin with.
                  if (mVal.isIteratedArray()) {
                    propLabs = propLabs.filter(lab => !lab.equals(ProtoLab));
                  }
                  return t instanceof Map &&
                         _.every(propLabs, function(l: PropertyLabel) {
                           const property = l.getProperty();
                           const propMVal = mVal.getPropertyModel(l);
                           if (propMVal.type.equals(NonExistingUnionType) ||
                               propMVal.type.equals(IgnoreUnionType)) {
                             return true;
                           }

                           // The StrucType does not model prototypes so if
                           // we see a prototype property label then we just
                           // recursively check the prototype model value
                           // with same t value.
                           if (property === Constants.PROTO_SYM) {
                             return cmpMValWithStrucType(propMVal, t);
                           }

                           if (t.has(property)) {
                             return cmpMValWithStrucType(propMVal,
                                                         t.get(property));
                           } else {
                             return false;
                           }
                         });
                }
              }

              if (!cmpMValWithStrucType(argMVal, argStructTypes[i])) {
                return false;
              }
            }
            return true;
          });
    } else {
      filteredCalledLabels = toCheckLabels;
    }

    assert(filteredCalledLabels.length > 0,
           "Expected at least one call label to match the argument types");

    // If there are still multiple potential ret values
    // This can happen if the arguments are functions
    // or if some of the arguments are vop value.
    // We just pick the return value that is greater than and closets
    // to the most recently checked value
    if (filteredCalledLabels.length > 1) {
      const applicationMVals: ModelValue[] =
          filteredCalledLabels.map(lab => modelValue.getPropertyModel(lab));
      const geLast: ModelValue[] =
          _.filter(applicationMVals, appVal => appVal.id > greatestCheckedId);
      return geLast.length == 0
                 ? _.maxBy(applicationMVals, (val) => val.id)
                 : _.minBy(geLast, val => val.id - greatestCheckedId);
    } else {
      return modelValue.getPropertyModel(filteredCalledLabels[0])
    }
  }

  /**
   * Computes a 'structural type' of o
   * The structural type is o where values has been replaced with their types.
   * The struct types uses vops for properties that are proxies and circular for
   * properties that are circular.
   * @param o
   * @param hasSeen
   */
  protected static computeStrucType(o: any,
                                    hasSeen: WeakSet<object>): StrucType {
    const type = getRuntimeType(o);
    if (isPrimitive(type) || o === null) {
      return type;
    } else {
      if (hasSeen.has(o)) {
        return "Circular";
      }
      hasSeen.add(o);
      if (ProxyOperations.isProxy(o)) {
        return ProxyOperations.getHandler<ArgumentProxyHandler>(o).path;
      }
      const types = new Map<string, RuntimeType|any>();
      const props = allInheritedKeys(o).filter(
          p => p !== 'arguments' && p !== 'caller' && p !== 'callee');
      for (const prop of props) {
        // Read the property value without triggering any type
        // regressions on ArgumentProxyHandlers
        const propVal = ProxyOperations.isProxy(o)
                            ? ProxyOperations.getTarget(o)[prop]
                            : o[prop];

        types.set(prop, TypeInfer.computeStrucType(propVal, hasSeen));
      }
      return types;
    }
  }
}