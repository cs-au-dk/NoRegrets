//var exports = module.exports;

import winston = require("winston");
import {convertToString, IDictionary, isNaturalNumberString} from "./common-utils";
import {CustomProxyHandler} from "./regression-runtime-checker";
import {Infos} from "./info";
import {AccessLabel, AccessPath, ApplicationLabel, ArgLabel, Label, PropertyLabel} from "./paths";
import {GlobalState} from "./global_state";
import {isNullOrUndefined, isString} from "util";
import {Constants} from "./constants";
import {Observation} from "./observations";
import {KnownTypes} from "./known-types";
import {RuntimeType, UnionType} from "./runtime-types";
import {getFullTestName} from "./mochaTest";
import {ProxyEventHandler} from "./proxy-handler";
import * as assert from "assert";
import {unescape} from "querystring";
import {KnownValuesNode} from "./known-values";

export class ThrownError {
    constructor(public e: any) {}
}

export class LearnHandler implements ProxyEventHandler {

    constructor(private infos: Infos){}

    handleRead(proxyHandler: CustomProxyHandler, originalLookedUpValue: any, readPath: AccessPath) {
        let property = readPath.getName();

        let known = KnownTypes.knownTypes.knownType(proxyHandler.originalValue);
        winston.debug(`Reading ${property} on ${proxyHandler.path} (${known})\n`);
        if (LearnHandler.isBinaryCollection(known)) {
            // no observation recorded
        }
        else {
            this.addObservation(
                new Observation(
                    LearnHandler.getUnionType(originalLookedUpValue),
                    readPath,
                    LearnHandler.grabProperties(originalLookedUpValue))
            );
        }

        // If the value is a function that can also be a constructor, we record the path to its prototype, so that we can later
        // bind objects whose __proto__ is this function prototype to their constructor
        if(typeof originalLookedUpValue === "function") {
            if(!isNullOrUndefined(originalLookedUpValue.prototype) &&
                !GlobalState.info.discoveredConstructorsPrototype.has(CustomProxyHandler.unproxy(originalLookedUpValue.prototype))) {

                GlobalState.info.discoveredConstructorsPrototype.set(
                    CustomProxyHandler.unproxy(originalLookedUpValue.prototype),
                    readPath.extendWithLabel(new PropertyLabel("prototype"))
                )
            }
        }

    }

    handleHas(proxyHandler: CustomProxyHandler, property: PropertyKey, has: boolean) {
        let value = proxyHandler.originalValue[property];
        let known = KnownTypes.knownTypes.knownType(proxyHandler.originalValue);

        let observe = false;
        let accessPath = null;

        if(LearnHandler.isPolymorphicCollection(known) && isNaturalNumberString(property.toString())) {
            observe = true;
            accessPath = proxyHandler.path.extendWithLabel(new AccessLabel());
        }
        else {
            observe = true;
            accessPath = this.makeAccessPath(proxyHandler, property);
        }

        if(observe) {
            this.addObservation(
                new Observation(
                    LearnHandler.getUnionType(value),
                    accessPath,
                    LearnHandler.grabProperties(value)
                )
            );
        }
    }

    handleWrite(proxyHandler: CustomProxyHandler, writtenValue: any, writeToPath: AccessPath) {
        if (CustomProxyHandler.isProxy(writtenValue)) {
            let writeTo = writeToPath;
            let writeFrom = (writtenValue[Constants.GET_HANDLER] as CustomProxyHandler).path;
            if(GlobalState.config.withUnifications) {
                this.infos.unifications.enqueue([writeTo, writeFrom]);
            }
        }
    }

    handleCall(proxyHandler: CustomProxyHandler, argArray: any[], functionResult: any, callPath: AccessPath) {
        this.addObservation(
            new Observation(
                LearnHandler.getUnionType(functionResult),
                callPath,
                LearnHandler.grabProperties(proxyHandler.originalValue)
            )
        );

        if (CustomProxyHandler.isProxy(functionResult)) {
            // Unify the original path of what is returned with the return path;
            if(GlobalState.config.withUnifications) {
                this.infos.unifications.enqueue([callPath, (functionResult[Constants.GET_HANDLER] as CustomProxyHandler).path]);
            }
        }

        winston.debug(`Invoking ${proxyHandler.path} returned ${typeof functionResult}\n`)
    }

    handleGetPrototype(target: CustomProxyHandler, originalLookedUpValue: any, readPath: AccessPath) {
        // The client is accessing proto, hence we assume he cares about the identity of the __proto__
        // Unfortunately we might not have seen the constructor and its prototype yet, for example in
        // x.y instanceof k.l
        // y is read first, and l after.
        // Hence here we save some work to perform when the execution has finished.

        let known = KnownTypes.knownTypes.knownType(target.originalValue);
        if(known == null && GlobalState.useKnownObjectInstances) {

            let runtimeType = LearnHandler.getRuntimeType(target);
                this.infos.missingWork.push(() => {
                if (GlobalState.info.discoveredConstructorsPrototype.has(originalLookedUpValue)) {
                    this.addObservation(new Observation(
                        runtimeType + "(" + GlobalState.info.discoveredConstructorsPrototype.get(originalLookedUpValue) + ")" as any,
                        readPath,
                        undefined
                    ));
                }
            });
        }
    }


    makeAccessPath(issuer: CustomProxyHandler, p: PropertyKey): AccessPath {
        assert(!isNullOrUndefined(p));

        if(issuer == null) {
            return new AccessPath(new PropertyLabel(p), undefined);
        } else {
            let objType = LearnHandler.getRuntimeType(issuer.originalValue);
            let propertyLabel = LearnHandler.isPolymorphicCollection(objType) && LearnHandler.isCollectionAccessKey(p)
                ? new AccessLabel()
                : new PropertyLabel(p);
            return issuer.path.extendWithLabel(propertyLabel);
        }
    }

    makeArgPath(path: AccessPath, count: number): AccessPath {
        return path.extendWithLabel(new ArgLabel(count));
    }

    makeCallPath(issuer: CustomProxyHandler, argNum: number): AccessPath {
        return issuer.path.extendWithLabel(new ApplicationLabel(argNum, false));
    }

    makeConstructorPath(issuer: CustomProxyHandler, argNum: number): AccessPath {
        return issuer.path.extendWithLabel(new ApplicationLabel(argNum, true));
    }

    addObservation(obs: Observation) {
        let id = obs.fullId();

        if(!this.infos.observationSet.has(id)) {
            this.infos.observationSet.add(id);
            this.infos.observations.push(obs);
            if (!isNullOrUndefined(GlobalState.config) && GlobalState.config.collectStackTraces)
                obs.addStack(new Error().stack);
        }
    }

    static isCollectionAccessKey(property): boolean {
        return !isNullOrUndefined(property) &&
            property instanceof PropertyLabel &&
            isString(property.name) &&
            isNaturalNumberString(property.name.toString())
    }

    static isPolymorphicCollection(known: RuntimeType): boolean {
        return !isNullOrUndefined(known) && (
            known === "Array" ||
            known === "Float32Array" ||
            known === "Float64Array" ||
            known === "Int16Array" ||
            known === "Int32Array" ||
            known === "Int8Array" ||
            known === "Uint16Array" ||
            known === "Uint32Array" ||
            known === "Uint8Array" ||
            known === "Uint8ClampedArray")
    }

    static isBinaryCollection(known): boolean {
        return !isNullOrUndefined(known) && (
            known === "Buffer" ||
            known === "ArrayBuffer")
    }

    static getUnionType (value: any): UnionType {
        let t: UnionType = new UnionType();
        t.addType(this.getRuntimeType(value));
        while ((typeof value === 'function' || typeof value === 'object' )
                    && !isNullOrUndefined(value)) {
            value = Object.getPrototypeOf(value);
            if (value != null)  {
                t.addType(this.getRuntimeType(value));
            }
        }
        return t;
    }

    static getRuntimeType (value: any): RuntimeType {
        let knownt = KnownTypes.knownTypes.knownType(value);
        if(!isNullOrUndefined(knownt)) {
            return knownt;
        }
        if(!isNullOrUndefined(value) && value instanceof ThrownError) return "throws";
        return typeof value;
    }

    static grabProperties(v: any): IDictionary<RuntimeType> | undefined {
        if(GlobalState.config.collectOwnProperties) {
            return LearnHandler.grabOwnProperties(v, !KnownValuesNode.isKnown(v.prototype))
        }
        else {
            return undefined;
        }
    }

    static grabOwnProperties(v: any, withPrototype: boolean): IDictionary<RuntimeType> | undefined {
        if(typeof v === 'function' || typeof v === 'object') {
            let psTypes = {};
            let ps = Object.keys(v);
            for(let curp of ps) {
                let desc = Object.getOwnPropertyDescriptor(v, curp);
                if(typeof desc.get === 'undefined') {
                    psTypes[curp] = LearnHandler.getRuntimeType(v[curp])
                }
            }
            if(withPrototype && !isNullOrUndefined(v.prototype)) {
                let psProt = Object.keys(v.prototype);
                for (let curp of psProt) {
                    let desc = Object.getOwnPropertyDescriptor(v.prototype, curp);
                    if (!psTypes.hasOwnProperty(curp) && typeof desc.get === 'undefined') {
                        psTypes[curp] = LearnHandler.getRuntimeType(v.prototype[curp])
                    }
                }
            }
            return psTypes;
        }
        return undefined;
    }


}