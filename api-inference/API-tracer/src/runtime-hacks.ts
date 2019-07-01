import Module = require('module');
import winston = require("winston");
import {Config} from "./config";
import regressionTypeChecker = require("./regression-runtime-checker");
// @ts-ignore
import originalRequire = Module.prototype.require;
import {isNullOrUndefined} from "util";
import {ThrownError} from "./throwError";
import {GlobalState} from "./global_state";
import * as assert from "assert";
import {Constants} from "./constants";
const SPECIAL_ALWAYS_PROXIFY = "./library.js";
const DISABLED = false;
import * as _ from 'lodash';
import {ProxyOperations} from "./ProxyOperations";
import istanbul = require('istanbul');

export function replaceRequire(config: Config) {
  // Applies the instrumentation when the following predicate is true.

  if (config.coverage) {
    const instrumenter = new istanbul.Instrumenter();

    const doInstrumentPred = (filePath: string) =>
        filePath.substring(filePath.indexOf("node_modules"))
            .includes(config.libraryModuleName);

    const instrumenterFunc = (code: string, file: string) => {
      try {
        return instrumenter.instrumentSync(code, file);
      } catch (e) {
        console.log("error instrumenting for coverage");
      }
    };

    //@ts-ignore
    istanbul.hook.hookRequire(doInstrumentPred, instrumenterFunc);
  }

  Module.prototype.require = function(name: string) {
    let preInitGlobalClones;
    let isMochaDep = this.filename.includes("mocha");
    let isTransitiveDependency = this.filename.includes("node_modules");
    const isLibraryOfInterest =
        (!DISABLED &&
         (name == SPECIAL_ALWAYS_PROXIFY ||
          name.startsWith(config.libraryModuleName + '/') ||
          name === config.libraryModuleName) &&
         !isMochaDep && !isTransitiveDependency);

    if (isLibraryOfInterest) {
      preInitGlobalClones = cloneGlobalObjects();
      let originalModule = loadLib(this, arguments);
      let postInitGlobalClones = cloneGlobalObjects();
      updateGlobalObjectWhitelist(preInitGlobalClones, postInitGlobalClones);
      winston.debug("Proxifying " + name);
      return regressionTypeChecker.makeModuleProxy(name, originalModule);
    } else {
      let originalModule = loadLib(this, arguments);
      if (originalModule instanceof ThrownError) throw originalModule.e;
      return originalModule;
    }
  };

  function loadLib(recv, args) {
    try {
      return originalRequire.apply(recv, args);
    } catch (e) {
      return new ThrownError(e);
    }
  }
}

export function replaceMochaFunctions() {
  // try {
  //     /*
  //     for(let fun in Runnable.prototype) {
  //         let f = Runnable.prototype[fun];
  //         if(typeof f === 'function') {
  //             console.log(f._timeout);
  //             console.log(Runnable._timeout);
  //             Runnable.prototype[fun] = function () {
  //                 this._enableTimeouts = false;
  //                 console.log(this._timeout);
  //                 console.log(f._timeout);
  //                 return Reflect.apply(f, this, arguments);
  //             }
  //         }
  //     }*/
  // }
  // catch(e) {
  //     winston.error("Unsuccessful replacement of mocha timeout: " + e);
  // }
  //
  // try {
  //     let runTest = function (fn) {
  //         var self = this;
  //         var test = this.test;
  //         GlobalState.currentTest = test;
  //
  //         if (!test) {
  //             return;
  //         }
  //         if (this.asyncOnly) {
  //             test.asyncOnly = true;
  //         }
  //         test.on('error', function (err) {
  //             self.fail(test, err);
  //         });
  //         if (this.allowUncaught) {
  //             test.allowUncaught = true;
  //             return test.run(fn);
  //         }
  //         try {
  //             test.run(fn);
  //         } catch (err) {
  //             fn(err);
  //         }
  //     };
  //
  //     //let Runner = require("mocha/lib/runner");
  // } catch (e) {
  //     winston.error("Unsuccessful replacement of mocha runTest: " + e);
  // }
}

const globals = [
  Object, Object.prototype, Function, Function.prototype, Array, Array.prototype
];
// These properties are non-writable, so there is no need to whitelist them.
const ignoredProperties = [ 'caller', 'callee', 'arguments' ];

/**
 * The whitelisted global object properties, i.e., the properties for which we
 * want observations are: The initial global objects properties + the ones added
 * to the global objects by the library. Computed as initGlobal +
 * (diff(preInitGlobalClones, postInitGlobalClones)
 *
 * @return The returned map M, maps a global object to a white listed version of
 * that same global object where: Some property o.p is white listed if and only
 * if o.p === M(o).p
 */
function updateGlobalObjectWhitelist(
    preInitGlobalClones: Map<object, object>,
    postInitGlobalClones: Map<object, object>) {
  // GlobalState.globalObjectsWhiteList =
  // _.cloneDeep(GlobalState.initialGlobalObjects);

  for (let global of globals) {
    const preGlobal = preInitGlobalClones.get(global);
    const postGlobal = postInitGlobalClones.get(global);
    const prePostDiffNames =
        _.filter(_.union(getAllNames(preGlobal), getAllNames(postGlobal)),
                 function(propName) {
                   return !_.isEqual(preInitGlobalClones[propName],
                                     postInitGlobalClones[propName]);
                 });
    const whiteListedObj = GlobalState.globalObjectsWhiteList.get(global);
    _.forEach(prePostDiffNames,
              (propName) => whiteListedObj[propName] = postGlobal[propName]);
  }
}

export function isGlobalObject(o: object) { return globals.includes(o); }

export function cloneGlobalObjects(): Map<object, object> {
  const resMap: Map<object, object> = new Map();
  for (let glob of globals) {
    const propNames = getAllNames(glob);

    var globClone = {};
    for (let propName of propNames) {
      if (ignoredProperties.includes(propName.toString())) {
        continue;
      }
      try {
        globClone[propName] = glob[propName];
      } catch (e) {
        winston.error(
            `Skipping clone of ${propName.toString()} on ${glob} due to ${e}`);
      }
    }
    Object.setPrototypeOf(globClone, Object.getPrototypeOf(glob));
    resMap.set(glob, globClone);
  }
  return resMap;
}

/**
 * Check that o[p] matches the white listed property.
 * If not, then the property may either have changed since the library was
 * loaded, or the property was overwritten by the client before the library was
 * loaded.
 * @param {object} o
 * @param {PropertyKey} p
 * @returns {boolean}
 */
export function isWhiteListedGlobalPropertyRead(o: object,
                                                p: PropertyKey): boolean {
  const whiteListObj = GlobalState.globalObjectsWhiteList.get(o);
  assert(whiteListObj !== undefined);
  return o[p] === whiteListObj[p] || ignoredProperties.includes(p.toString());
}

/**
 * @param {object} o
 * @returns {(string | symbol)[]} all property names/symbols including the ones
 * that are non-enumerable
 */
function getAllNames(o: object): (string|symbol)[] {
  return (Object.getOwnPropertyNames(o) as ((string | symbol)[]))
      .concat(Object.getOwnPropertySymbols(o));
}

export function replaceDangerousNativeFunctions() {
  let nativeDefineProperty = Object.defineProperty;
  let nativeDefineProperties = Object.defineProperties;
  let nativeGetPrototype = Object.getPrototypeOf;

  Object.defineProperty = function(a, b, c) {
    try {
      winston.debug("Defining property " +
                    (typeof a === 'symbol' ? a.toString() : a) + "." +
                    (typeof b === 'symbol' ? b.toString() : b));
    } catch (e) {
      winston.debug(
          "Unable to print defineProperty parameters. Continuing without debug msg");
    }

    if (typeof c.get === 'undefined' && typeof c.set === 'undefined') {
      c.writable = true;
    }

    return nativeDefineProperty(a, b, c);
  };

  Object.defineProperties = function(a, b) {
    try {
      winston.debug("target object :" + a.toString());
      winston.debug("enumerable property object: " + b.toString());
    } catch (e) {
      winston.debug(
          "Unable to print target and enumerable property object. Continuing without debug msg");
    }
    for (let p in b) {
      if (b[p] == "object" && typeof b[p].get === 'undefined' &&
          typeof b[p].set === 'undefined') {
        b[p].writable = true;
      }
    }
    return nativeDefineProperties(a, b);
  };
  unproxyOnProblematicNativeCalls();
  // recordToString();
}

// function recordToString() {
//  let toReplace = [
//    {obj: Function.prototype, prop: "toString"},
//    {obj: String.prototype, prop: "toString"},
//    {obj: Array.prototype, prop: "toString"},
//    {obj: Number.prototype, prop: "toString"},
//    {obj: Boolean.prototype, prop: "toString"},
//    {obj: Object.prototype, prop: "toString"},
//    {obj: Buffer.prototype, prop: "toString"},
//  ];
//
//  for(let fun of toReplace) {
//    let original = fun.obj[fun.prop];
//    let newFun = fun.obj[fun.prop] = function () {
//      const res = Reflect.apply(original, this, arguments);
//      if (NoRegretsProxyHandler.isProxy(this)) {
//        const handler: NoRegretsProxyHandler = this[Constants.GET_HANDLER];
//        const readPath = handler.path.extendWithLabel(new
//        PropertyLabel("toString"));
//        GlobalState.tracerObservationState.handler.handleRead(handler, newFun,
//        readPath, KnownValuesNode.isKnown(handler.originalValue));
//        GlobalState.tracerObservationState.handler.handleCall(handler,
//        Array.from(arguments), res,
//          readPath.extendWithLabel(new ApplicationLabel(0, false, (
//            GlobalState.tracerObservationState.handler as
//            LearnHandler).obsAuxInfo.getObsId().getOrElse(-1))),
//          none, false)
//      }
//      return res;
//    };
//    copyOverProps(original, newFun);
//  }
//}

function copyOverProps(original, newFun) {
  var props = Object.getOwnPropertyNames(original);
  for (var prop of props) {
    var propDesc = Object.getOwnPropertyDescriptor(newFun, prop);
    if (isNullOrUndefined(propDesc) || propDesc.writable) {
      newFun[prop] = original[prop];
    } else if (!propDesc.writable) {
      delete newFun[prop];
      Object.defineProperty(newFun, prop, {
        writable : propDesc.writable,
        configurable : propDesc.configurable,
        value : original[prop]
      });
    }
  }

  // Preserving the toString to allow correct native detection by ugly libraries
  newFun.toString = function() { return original.toString(); }
}

/**
 * Native functions doesn't always like proxies,
 * we replace the problematic ones to ensure that they work correctly
 */

export function unproxyOnProblematicNativeCalls() {
  let toReplace = [
    {obj : Function.prototype, prop : "toString"},
    {obj : String.prototype, prop : "toString"},
    {obj : Array.prototype, prop : "toString"},
    {obj : Number.prototype, prop : "toString"},
    {obj : Boolean.prototype, prop : "toString"},
    {obj : Object.prototype, prop : "toString"},
    {obj : Buffer.prototype, prop : "toString"},
    {obj : RegExp.prototype, prop : "exec"},
    {obj : Date.prototype, prop : "toString"},
    {obj : Date.prototype, prop : "valueOf"},
    {obj : Date.prototype, prop : "getTime"}
  ];

  // Replace all.
  // Date disabled since toPrimitive must be recorded
  let replaceAll = [];  //[Date.prototype];
      for (var obj of replaceAll) {
    var props = Object.getOwnPropertyNames(obj);
    for (var property of props) {
      if (obj.hasOwnProperty(property) && typeof obj[property] === 'function') {
        toReplace.push({obj : obj, prop : property});
      }
    }
  }

  for (let fun of toReplace) {
    let original = fun.obj[fun.prop];
    let newFun = fun.obj[fun.prop] = function() {
      let unproxifiedArgs = [];
      for (let m of arguments) {
        unproxifiedArgs.push(ProxyOperations.getTarget(m));
      }
      // try {
      return Reflect.apply(original, ProxyOperations.getTarget(this),
                           unproxifiedArgs);
      //} catch (e) {
      //  console.log(e);
      //}
    };
    // Copy over function properties
    copyOverProps(original, newFun);
  }
}

/**
 * Some libraries detect the 'nativeness' of a function by checking
 * for the `[native code]` block in the result of toString
 * Therefore, we make sure to preserve this property
 */
export function restoreNativeToStringResults() {
  let original = Function.prototype.toString;
  let newFun = Function.prototype.toString = function() {
    if (ProxyOperations.isProxy(this)) {
      if (this[Constants.GET_IS_NATIVE]) {
        return "function XYZ() { [native code] }"
      }
    }
    return Reflect.apply(original, this, arguments);
  };
  copyOverProps(original, newFun);
}

export class DeproxifierHandler implements ProxyHandler<Function> {
  constructor(private replacement, private original) {}

  apply(target: Function, thisArg: any, argArray?: any): any {
    let unproxifiedArgs = [];
    for (let m of argArray) {
      unproxifiedArgs.push(ProxyOperations.getTarget(m));
    }
    return Reflect.apply(this.original, ProxyOperations.getTarget(thisArg),
                         unproxifiedArgs);
  }

  construct(target: Function, argArray: any, newTarget?: any): Object {
    let unproxifiedArgs = [];
    for (let m of argArray) {
      unproxifiedArgs.push(ProxyOperations.getTarget(m));
    }
    return Reflect.construct(this.original, unproxifiedArgs);
  }
}