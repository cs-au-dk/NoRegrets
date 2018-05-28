import {CustomProxyHandler} from "./regression-runtime-checker";
import {IDictionary} from "./common-utils";
import {APIType, BottomType, FunctionType, ObjectType, PrimitiveType, RegressionType, TypeVariable} from "./types";
import * as winston from "winston";
import {isNull, isNullOrUndefined} from "util";
import * as assert from "assert";
import {AccessPath} from "./paths";
import {Observation} from "./observations";
import {Learned, LearningFailure} from "./tracing-results"
import {ProxyEventHandler} from "./proxy-handler";
import Collections = require("typescript-collections");

export class Infos {

    /**
     *  Paths that should be unified. For example, due to writes.
     */
    unifications = new Collections.PriorityQueue<[AccessPath, AccessPath]>(
        function (unfA, unfB) {
            let lengthA = Math.max(unfA[0].length(), unfA[1].length());
            let lengthB = Math.max(unfB[0].length(), unfB[1].length());
            return lengthB - lengthA;
        }
    );

    /**
     * All observations being made so far.
     */
    observationSet = new Set();

    /**
     * Observations, the map associates a fully descriptive id to the actual observation object.
     */
    observations: Observation[];

    /**
     * Specification of the public API.
     */
    inferedModuleType: APIType;

    /**
     * Maps a path into its type variable.
     */
    infos: IDictionary<TypeVariable> = {};

    /**
     * Maps a value into its type variable.
     */
    types = new WeakMap<any, TypeVariable>();

    /**
     * Current event handler.
     */
    handler: ProxyEventHandler;

    /**
     * Known functions are stored here with their path, so that we can name constructors.
     */
    discoveredConstructorsPrototype = new WeakMap<Object, AccessPath>();

    /**
     * Functions that are executed at the end of the observation phase.
     */
    missingWork: Function[] = [];

    finalize() {
        for(let f of this.missingWork) {
            try {f();}
            catch(e) {winston.error("Finalization error: " + e);}
        }
    }

    toJson(err): any {
        winston.info("Dumping path information");
        let unifications = [];
        this.unifications.forEach((uni) => {
            unifications.push([uni[0].toString(), uni[1].toString()])
        });

        let dumpObj : Learned = isNullOrUndefined(err) ? new Learned() : new LearningFailure(err);
        dumpObj.observations = this.observations.map(obs => obs.toJson());
        dumpObj.unifications = unifications;

        return dumpObj;
    }

    typeVariableForHandler(h: CustomProxyHandler): TypeVariable {
        return this.typeVariableForPathAndValue(h.path, h.originalValue);
    }

    typeVariableForPathAndValue(path: AccessPath, baseValue: any): TypeVariable {
        assert(!isNullOrUndefined(path));

        // First we check whether we have a type variable for the value itself
        if(typeof baseValue == "object" || typeof baseValue == "function") {
            let old = this.types.get(baseValue);
            if (!isNullOrUndefined(old)) {
                return old;
            }
        }

        // then we check whether we have a variable for the path,
        // this may also be the case for primitive base values
        let pre = this.infos[path.toString()];
        if(!isNullOrUndefined(pre)) {
            return pre;
        }

        let newOne = new TypeVariable(this.makeType(baseValue));

        if(!isNull(baseValue) && (typeof baseValue == "object" || typeof baseValue == "function")) {
            this.types[baseValue] = newOne;
        }
        this.infos[path.toString()] = newOne;

        winston.info(`New type registered for path: ${path} and value`);
        return newOne;
    }

    _typeForValue(v: any): RegressionType {
        let old = this.types.get(v);
        if(!isNullOrUndefined(old)) {
            return old;
        }
        let ret = this.makeType(v);
        if(!isNullOrUndefined(ret)) {
            if (typeof v === "object" || typeof v === "function") {
                ret = new TypeVariable(ret);
                this.types.set(v, ret as TypeVariable);
            }
            return ret;
        }
    }

    makeType(v: any): RegressionType {
        let vtype = typeof v;
        let ret;
        if(vtype == "function") {
            ret = new FunctionType({}, new BottomType(), new BottomType(), {});
        }
        else if(vtype == "object") {
            ret = new ObjectType({});
        }
        else if(vtype === "symbol" || vtype === "number" || vtype === "boolean" || vtype === "string" || vtype === "undefined") {
            ret = new PrimitiveType(vtype);
        }

        if(!isNullOrUndefined(ret)) {
            return ret;
        }
        throw new Error(`Implement me for ${vtype}`);
    }
}

export class Info {
    constructor(public type: TypeVariable) {}
}