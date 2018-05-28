//var exports = module.exports;

import winston = require("winston");
import _ = require('lodash');
import {isNullOrUndefined} from "util";
import * as assert from "assert";
import {Obligations, ObligationsOP} from "./type-obligations";
import {GlobalState} from "./global_state";
import {AccessPath, ArgLabel, Label, RequireLabel, ApplicationLabel, PropertyLabel} from "./paths";
import {KnownValuesNode} from "./known-values";
import {Constants} from "./constants";
import {KnownTypes} from "./known-types";
import {RuntimeType} from "./runtime-types";
import {ThrownError} from "./learn-handler";
winston.level = 'info';

let DISABLED = [false];
export function cannotObserve() {return DISABLED[DISABLED.length - 1]}
export function disableObservations(f) {
    DISABLED.push(true);
    try { return f(); }
    finally {DISABLED.pop();}
}
export function enableObservations(f) {
    DISABLED.push(false);
    try { return f(); }
    finally {DISABLED.pop();}
}

export class CustomProxyHandler implements ProxyHandler<{}> {
    constructor(
        public issuer: CustomProxyHandler | null,
        public originalValue: Object,
        public path: AccessPath
    ) {}


    get(target: {}, p: PropertyKey, receiver: any): any {
        if (p === Constants.PROXY_KEY) {
            return true;
        }
        else if (p === Constants.PROXY_TARGET) {
            return this.originalValue;
        } else if (p === Constants.GET_HANDLER) {
            return this;
        }
        if(cannotObserve()) return target[p];

        let self = this;
        return disableObservations(() => {
            assert(target === self.originalValue);

            let path = GlobalState.info.handler.makeAccessPath(self, p);

            let pLookupValue;
            try {
                pLookupValue = enableObservations(() => (target as any)[p]);
            }
            catch (e) {
                GlobalState.info.handler.handleRead(self, new ThrownError(e), path);
                throw e;
            }

            let descriptor = Object.getOwnPropertyDescriptor(target, p);
            if (!isNullOrUndefined(descriptor) && !descriptor.writable) {
                // the proxy invariant requires that the getter must return the exact target value (i.e., not a proxy)
                // if the property is non-writable.
                // Note, that we overload the Object.defineProperty and Object.defineProperties methods to always set a property to writable.
                // However, getters and setters cannot be set to writable so we still need this case to be handled
                return pLookupValue;
            }

            let retValue;
            if (!CustomProxyHandler.isProxifyAble(pLookupValue)) {
                retValue = pLookupValue;
                if (CustomProxyHandler.needUnproxification(pLookupValue)) {
                    retValue = CustomProxyHandler.unproxy(pLookupValue);
                }
            }
            else {
                let pResult = CustomProxyHandler.proxify(pLookupValue, self, path);
                retValue = pResult[0];
            }

            GlobalState.info.handler.handleRead(self, pLookupValue, path);

            return retValue;
        });
    }

    /**
     * Among the other things, this is the method called by instanceof
     */
    getPrototypeOf(target: {}): Object | any {
        if(cannotObserve()) return Reflect.getPrototypeOf(target);
        let self = this;
        return disableObservations(() => {
            assert(target === self.originalValue);
            let val = enableObservations(() => Reflect.getPrototypeOf(this.originalValue));
            let path = GlobalState.info.handler.makeAccessPath(self, Constants.GET_PROTOTYPE_OF);
            if (KnownTypes.knownTypes.knownType(target) === null) {
                if (!isNullOrUndefined(val)) {
                    let proxificationResult;
                    if (CustomProxyHandler.needUnproxification(val)) {
                        return CustomProxyHandler.unproxy(val);
                    }
                    else {
                        if (CustomProxyHandler.isProxifyAble(val)) {
                            proxificationResult = CustomProxyHandler.proxify(val, self, path);
                            GlobalState.info.handler.handleGetPrototype(self, val, proxificationResult[1].path);
                            if(GlobalState.withProtoProxy) {
                                return proxificationResult[0];
                            }
                        }
                        else {
                            return val;
                        }
                    }
                }
            }
            return val;
        });
    }

    getOwnPropertyDescriptor(target: {}, p: PropertyKey): PropertyDescriptor {
        if(cannotObserve()) return Object.getOwnPropertyDescriptor(target, p);
        let self = this;
        return disableObservations(() => {
            assert(target === self.originalValue);

            if (!GlobalState.BE_GENTLE) throw new Error("Implement me");
            return enableObservations(() => Object.getOwnPropertyDescriptor(target, p));
        });
    }

    has(target: {}, p: PropertyKey): boolean {
        if(cannotObserve()) return target.hasOwnProperty(p);
        let self = this;
        return disableObservations(() => {
            assert(target === self.originalValue);

            let has = enableObservations(() => target.hasOwnProperty(p));
            if (has) {
                GlobalState.info.handler.handleHas(self, p, has);
            }
            return has;
        });
    }

    set(target: {}, p: PropertyKey, value: any, receiver: any): boolean {
        if(cannotObserve()) {
            (target as any)[p] = value;
            return true;
        }
        let self = this;
        return disableObservations(() => {
            assert(target === self.originalValue);

            let writeToPath = GlobalState.info.handler.makeAccessPath(self, p);

            try {
                enableObservations(() => (target as any)[p] = value);
            }
            catch (e) {
                GlobalState.info.handler.handleWrite(self, new ThrownError(e), writeToPath);
                throw e;
            }

            if (value === Function.prototype.toString) {
                //The setter has most likely been called from replaceToStringOnFunctionProxy
                return;
            }
            GlobalState.info.handler.handleWrite(self, CustomProxyHandler.unproxy(value), writeToPath);
            return true;
        });
    }

    defineProperty(target: {}, p: PropertyKey, attributes: PropertyDescriptor): boolean {
        assert(target === this.originalValue);

        winston.error("Missing implementation of defineProperty");
        return Object.defineProperty(target, p, attributes)
    }

    ownKeys(target: {}): PropertyKey[] {
        if(cannotObserve()) return Reflect.ownKeys(target);
        let self = this;
        return disableObservations(() => {
            assert(target === self.originalValue);
            let all = Reflect.ownKeys(target);
            if(GlobalState.observeHasWhenOwnKeys) {
                for (let k of all) {
                    GlobalState.info.handler.handleHas(self, k, true);
                }
            }
            return all;
        });
    }

    apply(target: Function, thisArg: any, argArray?: any): any {
        if(cannotObserve()) return Reflect.apply(target, thisArg, argArray);
        let self = this;
        return disableObservations(() => {
            assert(target === self.originalValue);

            let applicationPath = GlobalState.info.handler.makeCallPath(self, argArray.length);
            let proxifiedArguments = CustomProxyHandler.proxifyArguments(argArray, self, applicationPath);
            let unproxifiedThis = CustomProxyHandler.unproxy(thisArg);

            try {
                let functionResult = enableObservations(() => Reflect.apply(target, unproxifiedThis, proxifiedArguments));

                let result: any;
                if (!CustomProxyHandler.isProxifyAble(functionResult)) {
                    result = functionResult;
                    if (CustomProxyHandler.needUnproxification(functionResult)) {
                        result = CustomProxyHandler.unproxy(result);
                    }
                } else {
                    let proxy = CustomProxyHandler.proxify(functionResult, self, applicationPath);
                    result = proxy[0];
                }

                GlobalState.info.handler.handleCall(self, argArray, functionResult, applicationPath);
                return result;
            }
            catch(e) {
                GlobalState.info.handler.handleCall(self, argArray, new ThrownError(e), applicationPath);
                throw e;
            }
        });
    }

    construct(target: Function, argArray: any, newTarget?: any): Object {
        if(cannotObserve()) Reflect.construct(target, argArray, newTarget);
        let self = this;
        return disableObservations(() => {
            assert(target === self.originalValue);

            let constructorPath = GlobalState.info.handler.makeConstructorPath(self, argArray.length);
            let proxifiedArguments = CustomProxyHandler.proxifyArguments(argArray, self, constructorPath);

            try {
                let result;
                let newObj = enableObservations(() => Reflect.construct(self.originalValue as Function, proxifiedArguments));
                if (CustomProxyHandler.isProxifyAble(newObj)) {
                    result = CustomProxyHandler.proxify(newObj, self, constructorPath)[0];
                }
                else {
                    result = newObj;
                }
                GlobalState.info.handler.handleCall(self, argArray, newObj, constructorPath);
                return result;
            } catch (e) {
                GlobalState.info.handler.handleCall(self, argArray, new ThrownError(e), constructorPath);
               throw e;
            }
        });
    }

    static proxify(target: {}, issuer: CustomProxyHandler, path: AccessPath): [any, CustomProxyHandler] {
        assert(CustomProxyHandler.isProxifyAble(target));
        assert(!CustomProxyHandler.isProxy(target), "Reproxification is not allowed");
        let handler = new CustomProxyHandler(issuer, target, path);
        let proxy = new Proxy(target, handler);

        return [proxy, handler];
    }

    static proxifyArguments(argArray: any[], issuer: CustomProxyHandler, applicationPath: AccessPath): any {
        let count = 0;
        let resultArgs = [];

        for (let arg of argArray) {
            let newArg: any;
            let path: AccessPath = GlobalState.info.handler.makeArgPath(applicationPath, count);
            if (CustomProxyHandler.isProxifyAble(arg)) {
                let proxies = CustomProxyHandler.proxify(arg, issuer, path);
                newArg = proxies[0];
            } else {
                newArg = arg;
                if(CustomProxyHandler.needUnproxification(newArg)) {
                    newArg = CustomProxyHandler.unproxy(newArg);
                }
            }
            GlobalState.info.handler.handleRead(issuer, CustomProxyHandler.unproxy(newArg), path);
            resultArgs.push(newArg);
            ++count;
        }
        return resultArgs;
    }

    static isProxifyAble(value: any): boolean {
        let type = typeof value;
        return (!KnownValuesNode.isKnown(value) && (type == 'object' || type == 'function')  && value !== null && isNullOrUndefined(value[Constants.PROXY_KEY]));
    }

    /**
     * In proxy-of-proxy situation we unproxify
     */
    static needUnproxification(value: any): boolean{
        let type = typeof value;
        return (type == 'object' || type == 'function')  && value !== null && !isNullOrUndefined(value[Constants.PROXY_KEY]);
    }

    static isProxy(value: any): any {
        let type = typeof value;
        return (type == 'object' || type == 'function') && value !== null && !isNullOrUndefined(value[Constants.PROXY_KEY]);
    }

    static unproxy(v: any):any {
        if(v != null && (typeof v == 'object' || typeof v == 'function')) {
            if(!isNullOrUndefined(v[Constants.PROXY_KEY])) {
                return v[Constants.PROXY_TARGET]
            }
            return v;
        }
        return v;
    }
}

export function makeModuleProxy(path: string, value: any): any {
    let reqPath = new AccessPath(new RequireLabel(path), undefined);
    GlobalState.info.handler.handleRead(new CustomProxyHandler(null, value, reqPath), value, reqPath);
    if(value instanceof ThrownError) {
        throw value.e;
    } else {
        return new Proxy(value, new CustomProxyHandler(null, value, reqPath));
    }
}

export class TypeSet {
    constructor(type? : RuntimeType) {
        this.parent = this;
        this.types = new Set();
        if (type !== undefined) {
            this.types.add(type);
        }
    }

    find(): TypeSet {
        return this.parent == this ? this : this.parent.find();
    }

    addTypes(types: Set<RuntimeType>) {
        for (let type of types) {
            this.types.add(type);
        }
    }

    static union(a: TypeSet, b: TypeSet) {
        let parentA = a.find();
        let parentB = b.find();
        if (parentA != parentB) {
            parentA.parent = parentB;
            parentB.addTypes(parentA.types);
        }
    }

    public getRuntimeTypes(): RuntimeType[] {
        return Array.from(this.types.values());
    }

    private parent: TypeSet;
    private types: Set<RuntimeType>;
}
