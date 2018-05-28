import Module = require('module')
import winston = require("winston");
import {CustomProxyHandler} from "./regression-runtime-checker";
import {Config} from "./config";
import regressionTypeChecker = require("./regression-runtime-checker");
import originalRequire = Module.prototype.require;
import {ThrownError} from "./learn-handler";
import {isNullOrUndefined} from "util";

const SPECIAL_ALWAYS_PROXIFY  = "./library.js";
const DISABLED = false;

export function replaceRequire(config: Config) {
    Module.prototype.require = function (name: string) {
        let originalModule;
        try {
            originalModule = originalRequire.apply(this, arguments);
        }
        catch(e) {
            originalModule = new ThrownError(e);
        }
        let isMochaDep = this.filename.includes("mocha");
        let isTransitiveDependency =  this.filename.includes("node_modules");
        if (!DISABLED &&
            (name == SPECIAL_ALWAYS_PROXIFY
                || name.startsWith(config.libraryModuleName + '/') || name === config.libraryModuleName) &&
            !isMochaDep &&
            !isTransitiveDependency) {
            winston.debug("Proxifying " + name);
            return regressionTypeChecker.makeModuleProxy(name, originalModule);
        } else {
            if(originalModule instanceof ThrownError) throw originalModule.e;
            return originalModule;
        }
    };
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
    //     /**
    //      *  http://casadev.cs.au.dk/DISTILLEDTESTS/TestDistillerRoot/issues/65
    //      *  Please do investigate
    //      *  let Runner = require("mocha/lib/runner");
    //      *  Object.defineProperty(Runner.prototype, "runTest", {writable: false, configurable: false, value: runTest});
    //      *  Object.defineProperty(Runner.prototype, "foo", {writable: false, configurable: false, value: runTest});
    //      */
    //     //let Runner = require("mocha/lib/runner");
    // } catch (e) {
    //     winston.error("Unsuccessful replacement of mocha runTest: " + e);
    // }

}

export function replaceDangerousNativeFunctions() {
    let nativeDefineProperty = Object.defineProperty;
    let nativeDefineProperties = Object.defineProperties;
    let nativeGetPrototype = Object.getPrototypeOf;

    Object.defineProperty = function(a, b, c) {
        try {
            winston.debug("Defining property "
                + (typeof a === 'symbol' ? a.toString() : a) + "."
                + (typeof b === 'symbol' ? b.toString() : b));
        } catch (e) {
            winston.debug("Unable to print defineProperty parameters. Continuing without debug msg");
        }

        if(typeof c.get === 'undefined' && typeof c.set === 'undefined') {
            c.writable = true;
        }

        return nativeDefineProperty(a, b, c);
    };

    Object.defineProperties = function(a, b) {
        try {
            winston.debug("target object :" + a.toString());
            winston.debug("enumerable property object: " + b.toString());
        } catch(e) {
            winston.debug("Unable to print target and enumerable property object. Continuing without debug msg");
        }
        for(let p in b) {
            if(b[p] == "object" && typeof b[p].get === 'undefined' && typeof b[p].set === 'undefined') {
                b[p].writable = true;
            }
        }
        return nativeDefineProperties(a, b);
    };


    /**
     * Native functions doesn't always like proxies,
     * we replace the problematic ones to ensure that they work correctly
     */

    let toReplace = [
        {obj: Function.prototype, prop: "toString"},
        {obj: String.prototype, prop: "toString"},
        {obj: Array.prototype, prop: "toString"},
        {obj: Number.prototype, prop: "toString"},
        {obj: Boolean.prototype, prop: "toString"},
        {obj: Object.prototype, prop: "toString"},
        {obj: Buffer.prototype, prop: "toString"},
        {obj: RegExp.prototype, prop: "exec"}
    ];

    // Replace all.
    let replaceAll = [Date.prototype];
    for (var obj of replaceAll)  {
        var props = Object.getOwnPropertyNames(obj);
        for (var property of props) {
            if (obj.hasOwnProperty(property) && typeof obj[property] === 'function') {
                toReplace.push({obj: obj, prop: property});
            }
        }
    }


    for(let fun of toReplace) {
        let original = fun.obj[fun.prop];
        let newFun = fun.obj[fun.prop] = function() {
            let unproxifiedArgs = [];
            for (let m of arguments) {
                unproxifiedArgs.push(CustomProxyHandler.unproxy(m));
            }
            return Reflect.apply(original, CustomProxyHandler.unproxy(this), unproxifiedArgs);
        };
        //Copy over function properties
        var props = Object.getOwnPropertyNames(original);
        for (var prop of props) {
            var propDesc = Object.getOwnPropertyDescriptor(newFun, prop);
            if (isNullOrUndefined(propDesc) || propDesc.writable) {
                newFun[prop] = original[prop];
            }
            else if (!propDesc.writable) {
                delete newFun[prop];
                Object.defineProperty(newFun, prop, {
                    writable: propDesc.writable,
                    configurable: propDesc.configurable,
                    value: original[prop]});
            }
        }

        // Preserving the toString to allow correct native detection by ugly libraries
        newFun.toString = function() {return original.toString();}

        //new Proxy(original, new DeproxifierHandler(fun, original));

    }
}

export class DeproxifierHandler implements ProxyHandler<Function> {

    constructor(private replacement, private original) {}

    apply(target: Function, thisArg: any, argArray?: any): any {
        let unproxifiedArgs = [];
        for (let m of argArray) {
            unproxifiedArgs.push(CustomProxyHandler.unproxy(m));
        }
        return Reflect.apply(this.original, CustomProxyHandler.unproxy(thisArg), unproxifiedArgs);
    }

    construct(target: Function, argArray: any, newTarget?: any): Object {
        let unproxifiedArgs = [];
        for (let m of argArray) {
            unproxifiedArgs.push(CustomProxyHandler.unproxy(m));
        }
        return Reflect.construct(this.original, unproxifiedArgs);
    }
}