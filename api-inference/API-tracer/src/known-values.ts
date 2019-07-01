import {isFunction, isNullOrUndefined, isObject} from "util";

import {GlobalState} from "./global_state";
import {KnownTypes} from "./known-types";

const server = require('_http_server');
class KnownValues {
  private knownFunctions = new Set();
  private knownObjects = new Set();

  // Objects are known if they have been constructed with a knownConstructor
  private knownConstructors = new Set();

  /**
   * Call this at the very last moment.
   * This is needed to ensure that all the runtime-hacks have been put in place
   * before we start reading values.
   */
  init() {
    this.knownConstructors.add(server.Server);
    this.knownConstructors.add(server.ServerResponse);

    let knownContainer: any[] = [
      global, Function.prototype, String.prototype, Boolean.prototype,
      Number.prototype,
      // Date.prototype,
      Object, Function,
      // Date,
      String, Boolean, Number
    ];

    if (GlobalState.withObjectPrototypeAsKnownValue) {
      knownContainer.push(Object.prototype);
    }

    let knownTypes = KnownTypes.knownTypes;
    for (let kt of Object.getOwnPropertyNames(knownTypes)) {
      if (!isNullOrUndefined(knownTypes[kt])) {
        knownContainer.push(knownTypes[kt].prototype);
        knownContainer.push(knownTypes[kt]);
      }
    }

    for (let prototype of knownContainer) {
      for (let prop of Object.getOwnPropertyNames(prototype)) {
        if (prop !== "caller" && prop !== "arguments") {
          try {
            let desc = Object.getOwnPropertyDescriptor(prototype, prop);
            if (!isNullOrUndefined(desc.value)) {
              if (isFunction(desc.value)) {
                this.knownFunctions.add(desc.value);
              } else if (isObject(desc.value)) {
                this.knownObjects.add(desc.value);
              }
            }
            if (!isNullOrUndefined(desc.set)) {
              this.knownFunctions.add(desc.set);
            }
            if (!isNullOrUndefined(desc.get)) {
              this.knownFunctions.add(desc.get);
            }
          } catch (e) {
          }
        }
      }
    }
  }

  isKnown(o): boolean {
    if (isFunction(o) && this.knownFunctions.has(o)) {
      return true;
    }

    if (isObject(o) && this.knownObjects.has(o)) {
      return true;
    }

    return ((isObject(o) || isFunction(o)) &&
            this.knownConstructors.has(o.constructor));

    // if(isFunction(o)) {
    //    return this.knownFunctions.has(o);
    //}
    // else if(isObject(o)) {
    //    return this.knownObjects.has(o);
    //}
    // return false;
  }
}

export let KnownValuesNode = new KnownValues();