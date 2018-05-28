import {CustomProxyHandler} from "./regression-runtime-checker";
import * as assert from "assert";
import * as winston from "winston";
import {KnownValuesNode} from "./known-values";
import {isNullOrUndefined} from "util";
import {Constants} from "./constants";

/**
 * Used as a proxy on native functions and objects
 * Native functions and objects sometimes require that all arguments including the receiver are in a non-proxy form.
 * For example.
 *
 * var regExp = getRegExpFromModule()
 * regExp.exec(
 */
export class NativeProxyHandler {
    constructor(
        public originalValue: Object,
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

        let lookupValue = target[p];

        let descriptor = Object.getOwnPropertyDescriptor(target, p);
        if (!isNullOrUndefined(descriptor) && !descriptor.writable) {
            // the proxy invariant requires that the getter must return the exact target value (i.e., not a proxy)
            // if the property is non-writable.
            // Note, that we overload the Object.defineProperty and Object.defineProperties methods to always set a property to writable.
            // However, getters and setters cannot be set to writable so we still need this case to be handled
            return lookupValue;
        }

        if (KnownValuesNode.isKnown(lookupValue)) {
            return NativeProxyHandler.proxify(lookupValue);
        }
        return lookupValue;

    }

    apply(target: Function, thisArg: any, argArray?: any): any {
        let functionResult;
        let unproxifiedThis = CustomProxyHandler.unproxy(thisArg);
        functionResult = Reflect.apply(target, unproxifiedThis, argArray);
        return functionResult;
    }

    static proxify(target: {}): any {
        assert(CustomProxyHandler.isProxifyAble(target));
        assert(!CustomProxyHandler.isProxy(target), "Reproxification is not allowed");
        let handler = new NativeProxyHandler(target);
        let proxy = new Proxy(target, handler);
        return new Proxy(target, handler);
    }
}