// var exports = module.exports;
import winston = require("winston");
import {IDictionary, isNaturalNumber} from "./common-utils";
import {NoRegretsProxyHandler} from "./regression-runtime-checker";
import {TracerObservationState} from "./info";
import {
  AccessLabel,
  AccessPath,
  ApplicationLabel,
  ArgLabel,
  PropertyLabel,
  ProtoLab,
  ReceiverLab,
  WriteLabel
} from "./paths";
import {GlobalState} from "./global_state";
import {isNullOrUndefined} from "util";
import {Constants} from "./constants";
import {
  ReadObservation,
  ObservationAuxInfo,
  Observation,
} from "./observations";
import {KnownTypes} from "./known-types";
import {RuntimeType} from "./runtime-types";
import {ProxyEventHandler} from "./proxy-handler";
import * as assert from "assert";
import {KnownValuesNode} from "./known-values";
import {Option, none} from "ts-option";
import * as _ from "lodash";
import {ProxyOperations} from "./ProxyOperations";
import {getRuntimeType} from "./runtime-types-operations";

export class LearnHandler implements ProxyEventHandler {
  private obsAuxInfo = new ObservationAuxInfo(
      GlobalState.collectPrimitiveValues, GlobalState.orderedObservations);
  constructor(private infos: TracerObservationState) {}

  handleRead(targetPath: AccessPath, originalLookedUpValue: any,
             readPath: AccessPath, valueOriginPath: Option<AccessPath>,
             isKnown: boolean, didThrow: boolean) {
    let property = readPath.getName();

    let known = KnownTypes.knownTypes.knownType(originalLookedUpValue);
    winston.debug(`Reading ${property} on ${targetPath} (${known})\n`);
    this.addObservation(new ReadObservation(
        getRuntimeType(originalLookedUpValue), readPath,
        LearnHandler.grabProperties(originalLookedUpValue),
        this.obsAuxInfo.boxValue(originalLookedUpValue),
        this.obsAuxInfo.getObsId(), valueOriginPath, isKnown,
        LearnHandler.isNative(originalLookedUpValue), didThrow));

    // If the value is a function that can also be a constructor, we record the
    // path to its prototype, so that we can later bind objects whose __proto__
    // is this function prototype to their constructor
    if (typeof originalLookedUpValue === "function") {
      LearnHandler.discoverPrototypes(originalLookedUpValue, readPath);
    }
  }

  private static discoverPrototypes(fun: Function, funPath: AccessPath) {
    if (!isNullOrUndefined(fun.prototype) &&
        !GlobalState.tracerObservationState.discoveredConstructorsPrototype.has(
            ProxyOperations.getTarget(fun.prototype))) {
      GlobalState.tracerObservationState.discoveredConstructorsPrototype.set(
          ProxyOperations.getTarget(fun.prototype),
          funPath.extendWithLabel(new PropertyLabel("prototype")));
    }
  }

  handleArgumentRead(funcPath: AccessPath, argument: any,
                     argumentOriginPath: Option<AccessPath>,
                     argPath: AccessPath, isKnown: boolean) {
    let property = argPath.getName();

    let known = KnownTypes.knownTypes.knownType(argument);
    winston.debug(`Reading ${property} on ${funcPath} (${known})\n`);
    this.addObservation(new ReadObservation(
        getRuntimeType(argument), argPath,
        LearnHandler.grabProperties(argument),
        this.obsAuxInfo.boxValue(argument), this.obsAuxInfo.getObsId(),
        // argumentOriginPath.isDefined ? some(new
        // ValueOriginInfo(argumentOriginPath.get, sideEffects)): none,
        argumentOriginPath, isKnown, LearnHandler.isNative(argument), false));
    // If the value is a function that can also be a constructor, we record the
    // path to its prototype, so that we can later bind objects whose __proto__
    // is this function prototype to their constructor
    if (typeof argument === "function") {
      LearnHandler.discoverPrototypes(argument, argPath);
    }
  }

  handleHas(proxyHandler: NoRegretsProxyHandler, property: PropertyKey,
            has: boolean, valueOriginPath: Option<AccessPath>) {
    let value = proxyHandler.originalValue[property];

    let accessPath = null;

    if (LearnHandler.shouldUseAccessAction(proxyHandler.originalValue,
                                           property)) {
      accessPath = proxyHandler.path.extendWithLabel(new AccessLabel());
    } else {
      accessPath = this.makeAccessPath(proxyHandler, property, 0);
    }

    // We do not proxify has lookups, so we need to manually trigger the
    // prototype reads
    this.forceReadPrototypes(accessPath, value);

    this.addObservation(new ReadObservation(
        getRuntimeType(value), accessPath, LearnHandler.grabProperties(value),
        this.obsAuxInfo.boxValue(value), this.obsAuxInfo.getObsId(),
        valueOriginPath, false, LearnHandler.isNative(value), false));
  }

  handleWrite(writtenValue: any, writeToPath: AccessPath,
              valueOriginPath: Option<AccessPath>, isKnown: boolean) {
    this.addObservation(new ReadObservation(
        getRuntimeType(writtenValue), writeToPath,
        LearnHandler.grabProperties(writtenValue),
        this.obsAuxInfo.boxValue(writtenValue), this.obsAuxInfo.getObsId(),
        valueOriginPath, isKnown, LearnHandler.isNative(writtenValue), false));
  }

  handleCall(argArray: any[], functionResult: any, didThrow: boolean,
             callPath: AccessPath, valueOriginPath: Option<AccessPath>,
             isKnown: boolean) {
    this.addObservation(new ReadObservation(
        getRuntimeType(functionResult), callPath,
        LearnHandler.grabProperties(functionResult),
        this.obsAuxInfo.boxValue(functionResult), this.obsAuxInfo.getObsId(),
        valueOriginPath, isKnown, LearnHandler.isNative(functionResult),
        didThrow));

    if (ProxyOperations.isProxy(functionResult)) {
      // Unify the original path of what is returned with the return path;
      if (GlobalState.config.withUnifications) {
        this.infos.unifications.enqueue([
          callPath,
          (functionResult[Constants.GET_HANDLER] as NoRegretsProxyHandler).path
        ]);
      }
    }

    winston.debug(`Invoking ${callPath} returned ${typeof functionResult}\n`)
  }

  handleGetPrototype(targetPath: AccessPath, originalLookedUpValue: any,
                     readPath: AccessPath, isKnown: boolean) {
    // The client is accessing proto, hence we assume he cares about the
    // identity of the __proto__ Unfortunately we might not have seen the
    // constructor and its prototype yet, for example in x.y instanceof k.l y is
    // read first, and l after. Hence here we save some work to perform when the
    // execution has finished.

    let known = KnownTypes.knownTypes.knownType(originalLookedUpValue);
    if (known == null &&
        GlobalState
            .useKnownObjectInstances) {



      let runtimeType = getRuntimeType(targetPath);
      this.infos.missingWork.push(() => {
        if (GlobalState.tracerObservationState.discoveredConstructorsPrototype
                .has(originalLookedUpValue)) {
          this.addObservation(new ReadObservation(
              runtimeType + "(" +
                      GlobalState.tracerObservationState
                          .discoveredConstructorsPrototype.get(
                              originalLookedUpValue) +
                      ")" as any,
              readPath, undefined,
              this.obsAuxInfo.boxValue(originalLookedUpValue),
              this.obsAuxInfo.getObsId(), none, isKnown,
              LearnHandler.isNative(originalLookedUpValue), false));
        }
      });
    }
  }

  makeAccessPath(issuer: NoRegretsProxyHandler, p: PropertyKey,
                 protoCount: number): AccessPath {
    assert(!isNullOrUndefined(p));

    if (issuer == null) {
      return new AccessPath(new PropertyLabel(p), undefined);
    } else {
      let propertyLabel =
          LearnHandler.shouldUseAccessAction(issuer.originalValue, p)
              ? new AccessLabel()
              : new PropertyLabel(p);
      return LearnHandler.extendWithProto(issuer.path, protoCount)
          .extendWithLabel(propertyLabel);
    }
  }

  makeArgPath(path: AccessPath, count: number): AccessPath {
    return path.extendWithLabel(new ArgLabel(count));
  }

  makeCallPath(issuer: NoRegretsProxyHandler, argNum: number): AccessPath {
    // It's a slight hack to use the observationCount as the unique id, but it's
    // unique for each constructed path so it serves to purpose
    return issuer.path.extendWithLabel(new ApplicationLabel(
        argNum, false, this.obsAuxInfo.getObsId().getOrElse(-1)));
  }

  makeConstructorPath(issuer: NoRegretsProxyHandler,
                      argNum: number): AccessPath {
    // It's a slight hack to use the observationCount as the unique id, but it's
    // unique for each constructed path so it serves to purpose
    return issuer.path.extendWithLabel(new ApplicationLabel(
        argNum, true, this.obsAuxInfo.getObsId().getOrElse(-1)));
  }

  makeReceiverPath(appPath: AccessPath): AccessPath {
    return appPath.extendWithLabel(ReceiverLab);
  }

  makeWritePath(issuer: NoRegretsProxyHandler, p: PropertyKey,
                protoCount: number): AccessPath {
    assert(!isNullOrUndefined(p));
    if (issuer == null) {
      return new AccessPath(new WriteLabel(p, this.obsAuxInfo.getObsId().getOrElse(-1)), undefined);
    } else {
      return LearnHandler.extendWithProto(issuer.path, protoCount)
          .extendWithLabel(new WriteLabel(p, this.obsAuxInfo.getObsId().getOrElse(-1)));
    }
  }

  private static extendWithProto(path: AccessPath, count: number): AccessPath {
    for (let i = 0; i < count; i++) {
      path = path.extendWithLabel(ProtoLab);
    }
    return path;
  }

  addObservation(obs: Observation) {
    let uniq = obs.uniquenessIdentifier();
    if (!this.infos.observations.has(uniq)) {
      this.infos.observations.set(uniq, obs);
    }
  }

  static isArrayIndexKey(property: PropertyKey): boolean {
    if (property === null || property === undefined) return false;
    return isNaturalNumber(property);
  }

  static isArraylikeCollection(known: RuntimeType): boolean {
    return !isNullOrUndefined(known) &&
           (known === "Array" || known === "Float32Array" ||
            known === "Float64Array" || known === "Int16Array" ||
            known === "Int32Array" || known === "Int8Array" ||
            known === "Uint16Array" || known === "Uint32Array" ||
            known === "Uint8Array" || known === "Uint8ClampedArray")
  }

  static isBinaryCollection(known): boolean {
    return !isNullOrUndefined(known) &&
           (known === "Buffer" || known === "ArrayBuffer");
  }

  static grabProperties(v: any): IDictionary<RuntimeType>|undefined {
    if (GlobalState.config.collectOwnProperties) {
      return LearnHandler.grabOwnProperties(
          v, !KnownValuesNode.isKnown(v.prototype));
    } else {
      return undefined;
    }
  }

  static grabOwnProperties(v: any, withPrototype: boolean):
      IDictionary<RuntimeType>|undefined {
    if (typeof v === 'function' || typeof v === 'object') {
      let psTypes = {};
      let ps = Object.keys(v);
      for (let curp of ps) {
        let desc = Object.getOwnPropertyDescriptor(v, curp);
        if (typeof desc.get === 'undefined') {
          psTypes[curp] = getRuntimeType(v[curp]);
        }
      }
      if (withPrototype && !isNullOrUndefined(v.prototype)) {
        let psProt = Object.keys(v.prototype);
        for (let curp of psProt) {
          let desc = Object.getOwnPropertyDescriptor(v.prototype, curp);
          if (!psTypes.hasOwnProperty(curp) &&
              typeof desc.get === 'undefined') {
            psTypes[curp] = getRuntimeType(v.prototype[curp]);
          }
        }
      }
      return psTypes;
    }
    return undefined;
  }

  static MAX_ELEM_COUNT_FOR_PRECISE_ARRAY_MODELLING = 25;

  /**
   * This method uses a heuristic to decide if the access action abstraction
   * should be used The targetObj is the target object, and p is the property
   * that is being looked-up
   * @param p
   */
  public static shouldUseAccessAction(targetObj: object,
                                      p: PropertyKey): Boolean {
    if (!GlobalState.useAccessActions ||
        !LearnHandler.isArraylikeCollection(getRuntimeType(targetObj)) ||
        !LearnHandler.isArrayIndexKey(p)) {
      return false;
    }

    // We can assume that targetObj has the length property since the
    // isArrayLikeCollection check passed
    const length = targetObj['length'];
    if (length > LearnHandler.MAX_ELEM_COUNT_FOR_PRECISE_ARRAY_MODELLING) {
      return true;
    }

    if (getRuntimeType(targetObj[p]) === 'string') {
      return false;
    }

    return true;
  }

  /**
   * Create an aux observation for each prototype in the chain of target
   * @param initPath
   * @param target
   */
  public forceReadPrototypes(initPath: AccessPath, target: {}): void {
    if (target == null) {
      return;
    }
    let proto = Object.getPrototypeOf(target);
    let path = initPath;
    while (proto != null) {
      path = path.extendWithLabel(ProtoLab);

      let vop = none;
      if (ProxyOperations.isProxy(proto)) {
        vop = ProxyOperations.getPath(proto);
        proto = ProxyOperations.getTarget(proto)
      }

      this.addObservation(new ReadObservation(
          getRuntimeType(proto), path, LearnHandler.grabProperties(proto),
          this.obsAuxInfo.boxValue(proto), this.obsAuxInfo.getObsId(), vop,
          KnownValuesNode.isKnown(proto), LearnHandler.isNative(proto), false));

      proto = Object.getPrototypeOf(proto);
    }
  }

  public static makeLabel(target: any, p: PropertyKey) {
    return LearnHandler.shouldUseAccessAction(target, p) ? new AccessLabel()
                                                         : new PropertyLabel(p);
  }

  static isNative(value: any): boolean { return _.isNative(value); }
}