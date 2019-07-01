// var exports = module.exports;

import winston = require("winston");
import {isNullOrUndefined} from "util";
import * as assert from "assert";
import {GlobalState} from "./global_state";
import {AccessPath, RequireLabel} from "./paths";
import {KnownValuesNode} from "./known-values";
import {Constants} from "./constants";
import {KnownTypes} from "./known-types";
import {ThrownError} from "./throwError";
import {option, none, some, Option} from "ts-option";
import {isGlobalObject, isWhiteListedGlobalPropertyRead} from "./runtime-hacks";
import {hasInChain} from "./common-utils";
import {ProxyOperations} from "./ProxyOperations";
import {getNumberOfProtos, getProtoWithProp} from "./JSAux";
import {IGNORE, NON_EXIST} from "./runtime-types";
winston.level = 'info';

let DISABLED = [ false ];
export function cannotObserve() { return DISABLED[DISABLED.length - 1] }
export function disableObservations(f) {
  DISABLED.push(true);
  try {
    return f();
  } finally {
    DISABLED.pop();
  }
}
export function enableObservations(f) {
  DISABLED.push(false);
  try {
    return f();
  } finally {
    DISABLED.pop();
  }
}

// Note, only used if the blacklistSideEffects is enabled
// Maps objects to a list of properties for which no observations should be
// recorded Probably since the properties were written by the opposite side of
// the client/library barrier.
export const blackLists: WeakMap<object, string[]> = new WeakMap();
const hasOwnProperty: (target: {}, p: PropertyKey) => boolean =
    Object.getPrototypeOf(Object).hasOwnProperty;

export class NoRegretsProxyHandler implements ProxyHandler<{}> {
  private proxyRef: any;
  constructor(public issuer: NoRegretsProxyHandler|null,
              public originalValue: Object, public path: AccessPath) {}

  // Must invariantly be called right after creation of the proxy
  public setProxyReference(ref: any) { this.proxyRef = ref; }

  /**
   *
   * @param target: Is always originalValue or some prototype of originvalValue
   * such that it is
   *                - The actual object that contains the property p, or
   *                - The first proxy in the prototype chain between
   * originalValue and the actual object that contains the property p
   * @param p
   * @param receiver: The object on which the property lookup is performed
   */
  get(target: {}, p: PropertyKey, receiver: any): any {
    if (p === Constants.IS_PROXY_KEY) {
      // If the receiver is not equal to `this.proxyRef`,
      // then IS_PROXY_KEY was looked up on some object that inherits from the
      // proxy Therefore, the object is not a proxy, and so we return false
      if (receiver === this.proxyRef) {
        return true;
      }
      return false;
    } else if (p === Constants.GET_PROXY_TARGET) {
      return this.originalValue;
    } else if (p === Constants.GET_HANDLER) {
      return this;
    }
    if (cannotObserve()) return target[p];

    let self = this;
    return disableObservations(() => {
      assert(target === self.originalValue);

      const realTargetDesc = getProtoWithProp(target, p);
      let realTarget = target;
      let protoCount: number;
      let desc: PropertyDescriptor|undefined;
      if (realTargetDesc.isDefined) {
        realTarget = realTargetDesc.get[0];
        protoCount = realTargetDesc.get[1];
        desc = Reflect.getOwnPropertyDescriptor(realTarget, p);
      } else {
        // If realTargetDesc is none, then the property does not exist on any
        // object in the chain. We therefore report the property as non-existing
        // on the last object in the prototype chain. The reason for doing it
        // that way, is that NoRegretsPlus dispatches the property read all the way
        // through the prototype chain, when the property is non-existing. So
        // it's most natural have the last object in th chain report the
        // property as non-existing to be most consistent with how NoRegrets
        // works for existing properties
        protoCount = getNumberOfProtos(realTarget);
      }

      let path = GlobalState.tracerObservationState.handler.makeAccessPath(
          self, p, protoCount);

      let pLookupValue;
      try {
        // The property actually exists on realTarget and it's a getter
        if (desc !== undefined && desc.get !== undefined) {
          // Note, it's only important that we use the `receiver` when the
          // property is a getter
          // since the `this` variable must refer to the correcty receiver
          // value.
          pLookupValue = enableObservations(() => desc.get.apply(receiver));
        } else {
          pLookupValue = enableObservations(() => (target as any)[p]);
        }
      } catch (e) {
        GlobalState.tracerObservationState.handler.handleRead(
            self.path, e, path, none, false, true);
        throw e;
      }

      if (NoRegretsProxyHandler.isBlackListed(target, p)) {
        return pLookupValue;
      }

      let descriptor = Object.getOwnPropertyDescriptor(target, p);
      if (!isNullOrUndefined(descriptor) && !descriptor.writable) {
        // the proxy invariant requires that the getter must return the exact
        // target value (i.e., not a proxy) if the property is non-writable.
        // Note, that we overload the Object.defineProperty and
        // Object.defineProperties methods to always set a property to writable.
        // However, getters and setters cannot be set to writable so we still
        // need this case to be handled
        GlobalState.tracerObservationState.handler.handleRead(
            self.path, IGNORE, path, none, false, false);
        return pLookupValue;
      }

      let retValue;
      if (!NoRegretsProxyHandler.isProxifyAble(pLookupValue)) {
        retValue = pLookupValue;
        if (ProxyOperations.isProxy(pLookupValue)) {
          retValue = ProxyOperations.getTarget(pLookupValue);
        }
      } else {
        let pResult = NoRegretsProxyHandler.proxify(pLookupValue, self, path);
        retValue = pResult[0];
      }

      // If undefined and non-existing then record as non existing
      const valueToRecordInModel =
          pLookupValue === undefined && !hasInChain(target, p) ? NON_EXIST
                                                               : pLookupValue;
      const vop: Option<AccessPath> = ProxyOperations.getPath(pLookupValue);
      GlobalState.tracerObservationState.handler.handleRead(
          self.path, valueToRecordInModel, path, vop,
          KnownValuesNode.isKnown(pLookupValue), false);

      return retValue;
    });
  }

  /**
   * Among the other things, this is the method called by instanceof
   */
  getPrototypeOf(target: {}): Object|any {
    if (cannotObserve()) return Reflect.getPrototypeOf(target);
    let self = this;
    return disableObservations(() => {
      assert(target === self.originalValue);
      let val =
          enableObservations(() => Reflect.getPrototypeOf(this.originalValue));
      let path = GlobalState.tracerObservationState.handler.makeAccessPath(
          self, Constants.PROTO_SYM, 0);
      if (KnownTypes.knownTypes.knownType(target) === null) {
        if (!isNullOrUndefined(val)) {
          let proxificationResult;
          if (ProxyOperations.isProxy(val)) {
            return ProxyOperations.getTarget(val);
          } else {
            if (NoRegretsProxyHandler.isProxifyAble(val)) {
              proxificationResult =
                  NoRegretsProxyHandler.proxify(val, self, path);
              GlobalState.tracerObservationState.handler.handleGetPrototype(
                  self.path, val, proxificationResult[1].path,
                  KnownValuesNode.isKnown(val));
              if (GlobalState.withProtoProxy) {
                return proxificationResult[0];
              }
            } else {
              return val;
            }
          }
        }
      }
      return val;
    });
  }

  getOwnPropertyDescriptor(target: {}, p: PropertyKey): PropertyDescriptor {
    if (p === Constants.IS_PROXY_KEY) {
      return {
        configurable : true,
        enumerable : false,
        value : true,
        writable : false
      };
    }

    if (cannotObserve()) return Object.getOwnPropertyDescriptor(target, p);

    let self = this;
    return disableObservations(() => {
      assert(target === self.originalValue);
      if (!GlobalState.BE_GENTLE) throw new Error("Implement me");
      return enableObservations(() =>
                                    Object.getOwnPropertyDescriptor(target, p));
    });
  }

  has(target: {}, p: PropertyKey): boolean {
    if (cannotObserve())
      return NoRegretsProxyHandler.safeHasOwnProperty(target, p);
    let self = this;
    return disableObservations(() => {
      assert(target === self.originalValue);

      if (NoRegretsProxyHandler.isBlackListed(target, p)) {
        return NoRegretsProxyHandler.safeHasOwnProperty(target, p);
      }

      let has = enableObservations(
          () => NoRegretsProxyHandler.safeHasOwnProperty(target, p));
      if (has) {
        const val = target[p];
        const vop = ProxyOperations.getPath(val);
        GlobalState.tracerObservationState.handler.handleHas(self, p, has, vop);
      }
      return has;
    });
  }

  setPrototypeOf(target: {}, prototype: any): boolean {
    return this.set(target, Constants.PROTO_SYM, prototype, undefined);
  }

  set(target: {}, p: PropertyKey, value: any, receiver: any): boolean {
    if (cannotObserve()) {
      Reflect.set(target, p, value, receiver);
      //(target as any)[p] = value;
      return true;
    }
    let self = this;
    return disableObservations(() => {
      assert(target === self.originalValue);

      let writeToPath =
          GlobalState.tracerObservationState.handler.makeWritePath(self, p, 0);
      let retVal = value;
      if (NoRegretsProxyHandler.isProxifyAble(value)) {
        retVal = NoRegretsProxyHandler.proxify(value, self, writeToPath)[0];
      } else {
        if (ProxyOperations.isProxy(value)) {
          retVal = ProxyOperations.getTarget(value);
        }
      }
      const vop = ProxyOperations.getPath(value);

      p = p === Constants.PROTO_SYM ? "__proto__" : p;
      try {
        // enableObservations(() => Reflect.set(target, p, retVal, receiver));
        // enableObservations(() => target[p] = retVal);

        // Perform write without re-enabling observations.
        // If the prototype is also a proxy, then the set trap on the prototype
        // is also triggered, which results in a spurious observation being
        // created (the value will be written twice to the inheriting object)
        // Note, doing in this way, we may potentially miss some observations if
        // the p is a setter.

        target[p] = retVal;
      } catch (e) {
        GlobalState.tracerObservationState.handler.handleWrite(
            new ThrownError(e), writeToPath, vop, false);
        throw e;
      }
        if (retVal === Function.prototype.toString) {
        // The setter has most likely been called from
        // replaceToStringOnFunctionProxy
        return;
      }

      GlobalState.tracerObservationState.handler.handleWrite(
          ProxyOperations.getTarget(retVal), writeToPath, vop,
          KnownValuesNode.isKnown(ProxyOperations.getTarget(retVal)));

      if (p === "__proto__") {
        GlobalState.tracerObservationState.handler.forceReadPrototypes(
            this.path, this.originalValue)
      }
      return true;
    });
  }

  defineProperty(target: {}, p: PropertyKey,
                 attributes: PropertyDescriptor): boolean {
    assert(target === this.originalValue);

    winston.error(
        "Missing implementation of defineProperty-trap in NoRegretsProxyHandler");
    return Object.defineProperty(target, p, attributes);
  }

  ownKeys(target: {}): PropertyKey[] {
    if (cannotObserve()) return Reflect.ownKeys(target);
    let self = this;
    return disableObservations(() => {
      assert(target === self.originalValue);
      let all = Reflect.ownKeys(target);
      if (GlobalState.observeHasWhenOwnKeys) {
        for (let k of all) {
          if (!NoRegretsProxyHandler.isBlackListed(target, k)) {
            const vop = ProxyOperations.getPath(target[k]);
            GlobalState.tracerObservationState.handler.handleHas(self, k, true,
                                                                 vop);
          }
        }
      }
      return all;
    });
  }

  apply(target: Function, thisArg: any, argArray?: any): any {
    if (cannotObserve()) return Reflect.apply(target, thisArg, argArray);
    let self = this;
    return disableObservations(() => {
      assert(target === self.originalValue);

      let applicationPath =
          GlobalState.tracerObservationState.handler.makeCallPath(
              self, argArray.length);

      const receiverPath =
          GlobalState.tracerObservationState.handler.makeReceiverPath(
              applicationPath);
      let receiver = thisArg;

      if (NoRegretsProxyHandler.isProxifyAble(receiver)) {
        receiver =
            NoRegretsProxyHandler.proxify(receiver, self, receiverPath)[0];
      } else {
        if (ProxyOperations.isProxy(receiver)) {
          receiver = ProxyOperations.getTarget(receiver);
        }
      }
      GlobalState.tracerObservationState.handler.handleRead(
          this.path, receiver, receiverPath, ProxyOperations.getPath(thisArg),
          KnownValuesNode.isKnown(receiver), false);

      let proxifiedArguments = NoRegretsProxyHandler.proxifyArguments(
          argArray, self, applicationPath);
      let valueOriginPath = none;

      let functionResult;
      let didThrow = false;
      try {
        functionResult = enableObservations(
            () => Reflect.apply(target, receiver, proxifiedArguments));
      } catch (e) {
        functionResult = e;
        didThrow = true;
      }

      // proxy/unproxy of function result
      let result: any;
      if (!NoRegretsProxyHandler.isProxifyAble(functionResult)) {
        result = functionResult;
        if (ProxyOperations.isProxy(functionResult)) {
          valueOriginPath = option(functionResult[Constants.GET_HANDLER].path);
          result = ProxyOperations.getTarget(result);
        }
      } else {
        let proxy = NoRegretsProxyHandler.proxify(functionResult, self,
                                                  applicationPath);
        result = proxy[0];
      }

      GlobalState.tracerObservationState.handler.handleCall(
          argArray, functionResult, didThrow, applicationPath, valueOriginPath,
          KnownValuesNode.isKnown(functionResult));
      if (didThrow) {
        throw result;
      } else {
        return result;
      }
    });
  }

  construct(target: Function, argArray: any, newTarget?: any): Object {
    if (cannotObserve()) Reflect.construct(target, argArray, newTarget);
    let self = this;
    return disableObservations(() => {
      assert(target === self.originalValue);

      let constructorPath =
          GlobalState.tracerObservationState.handler.makeConstructorPath(
              self, argArray.length);
      let proxifiedArguments = NoRegretsProxyHandler.proxifyArguments(
          argArray, self, constructorPath);

      let ctrResult;
      let didThrow = false;
      try {
        ctrResult = enableObservations(
            () => Reflect.construct(self.originalValue as Function,
                                    proxifiedArguments));
      } catch (e) {
        didThrow = true;
        ctrResult = e;
      }

      const valueOriginPath = ProxyOperations.isProxy(ctrResult)
                                  ? ProxyOperations.getPath(ctrResult)
                                  : none;
      let result;
      if (NoRegretsProxyHandler.isProxifyAble(ctrResult)) {
        result =
            NoRegretsProxyHandler.proxify(ctrResult, self, constructorPath)[0];
      } else {
        result = ctrResult;
      }
      GlobalState.tracerObservationState.handler.handleCall(
          argArray, ctrResult, didThrow, constructorPath, valueOriginPath,
          KnownValuesNode.isKnown(ctrResult));
      if (didThrow) {
        throw result
      } else {
        return result;
      }
    });
  }

  static proxify(target: {}, issuer: NoRegretsProxyHandler,
                 path: AccessPath): [ any, NoRegretsProxyHandler ] {
    assert(NoRegretsProxyHandler.isProxifyAble(target));
    assert(!ProxyOperations.isProxy(target), "Reproxification is not allowed");
    let handler = new NoRegretsProxyHandler(issuer, target, path);
    let proxy = new Proxy(target, handler);
    handler.setProxyReference(proxy);
    GlobalState.tracerObservationState.handler.forceReadPrototypes(handler.path,
                                                                   target);

    return [ proxy, handler ];
  }

  static proxifyArguments(argArray: any[], issuer: NoRegretsProxyHandler,
                          applicationPath: AccessPath): any {
    let count = 0;
    let resultArgs = [];

    for (let arg of argArray) {
      let newArg: any;
      let path: AccessPath =
          GlobalState.tracerObservationState.handler.makeArgPath(
              applicationPath, count);
      let argumentOriginPath = none;

      if (NoRegretsProxyHandler.isProxifyAble(arg)) {
        let proxies = NoRegretsProxyHandler.proxify(arg, issuer, path);
        newArg = proxies[0];
      } else {
        newArg = arg;
        if (ProxyOperations.isProxy(newArg)) {
          // Retrieve path
          argumentOriginPath = option(newArg[Constants.GET_HANDLER].path);
          newArg = ProxyOperations.getTarget(newArg);
        }
      }

      const argUnproxied = ProxyOperations.getTarget(newArg);
      GlobalState.tracerObservationState.handler.handleArgumentRead(
          issuer.path, argUnproxied, argumentOriginPath, path,
          KnownValuesNode.isKnown(argUnproxied));
      resultArgs.push(newArg);
      ++count;
    }
    return resultArgs;
  }

  static isProxifyAble(value: any): boolean {
    let type = typeof value;
    return (
        !KnownValuesNode.isKnown(value) &&
        (type == 'object' || type == 'function') && value !== null &&
        // Note, do not use ProxyOperations.isProxy here.
        // It's important that we do not attempt to use the
        // getOwnPropertyDescriptor, which isProxy sometimes does as this may
        // report objects as being proxies if they inherit from a proxy object.
        !value[Constants.IS_PROXY_KEY]);
  }

  /**
   * returns true if reading a blacklisted global property
   * (A property which is not written by the library or is present by default)
   * @param target
   * @param p
   */
  private static isBlackListed(target: {}, p: PropertyKey): Boolean {
    if (p in target) {
      let protoChainCurrent = target;
      while (protoChainCurrent !== null) {
        if (protoChainCurrent.hasOwnProperty === undefined &&
            protoChainCurrent[p] !== undefined)
          break;
        if (Object.prototype.hasOwnProperty.call(protoChainCurrent, p)) break;
        //if (protoChainCurrent.hasOwnProperty(p)) break;
        //if (Object.prototype.hasOwnProperty.call(protoChainCurrent, p)) break;
        protoChainCurrent = Object.getPrototypeOf(protoChainCurrent);
      }
      assert(protoChainCurrent !== null);
      return isGlobalObject(protoChainCurrent) &&
             !isWhiteListedGlobalPropertyRead(protoChainCurrent, p);
    }
  }

  // Consider replacing with Reflect.has
  private static safeHasOwnProperty = function(o, p): boolean {
    if ((o.hasOwnProperty == null) || o.hasOwnProperty == undefined) {
      return Object.getOwnPropertyNames(o).includes(p.toString());
    } else {
      return o.hasOwnProperty(p);
    }
  };
}

export function makeModuleProxy(path: string, value: any): any {
  let reqPath = new AccessPath(new RequireLabel(path), undefined);
  GlobalState.tracerObservationState.handler.handleRead(reqPath, value, reqPath,
                                                        none, false, false);
  if (value instanceof ThrownError) {
    throw value.e;
  } else {
    const handler = new NoRegretsProxyHandler(null, value, reqPath);
    const proxy = new Proxy(value, handler);
    handler.setProxyReference(proxy);
    GlobalState.tracerObservationState.handler.forceReadPrototypes(handler.path,
                                                                   value);
    return proxy;
  }
}
