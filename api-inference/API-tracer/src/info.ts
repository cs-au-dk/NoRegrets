import * as assert from "assert";
import {isNull, isNullOrUndefined} from "util";
import * as winston from "winston";
import istanbul = require('istanbul');

import {IDictionary} from "./common-utils";
import {Observation, ReadObservation} from "./observations";
import {AccessPath} from "./paths";
import {ProxyEventHandler} from "./proxy-handler";
import {NoRegretsProxyHandler} from "./regression-runtime-checker";
import {APIModel, LearningFailure, TracingResult} from "./tracing-results"
import {
  APIType,
  BottomType,
  FunctionType,
  ObjectType,
  PrimitiveType,
  RegressionType,
  TypeVariable
} from "./types";

import Collections = require("typescript-collections");
import * as path from "path";
export class TracerObservationState {
  /**
   *  Paths that should be unified. For example, due to writes.
   */
  unifications =
      new Collections.PriorityQueue<[ AccessPath, AccessPath ]>(function(unfA,
                                                                         unfB) {
        let lengthA = Math.max(unfA[0].length(), unfA[1].length());
        let lengthB = Math.max(unfB[0].length(), unfB[1].length());
        return lengthB - lengthA;
      });

  /**
   * Observations, the map associates a fully descriptive id to the actual
   * observation object.
   */
  observations: Map<string, Observation>;

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
   * Known functions are stored here with their path, so that we can name
   * constructors.
   */
  discoveredConstructorsPrototype = new WeakMap<Object, AccessPath>();

  /**
   * Functions that are executed at the end of the observation phase.
   */
  missingWork: Function[] = [];

  createTime: number;
  static DateRef = Date;

  finalize() {
    for (let f of this.missingWork) {
      try {
        f();
      } catch (e) {
        winston.error("Finalization error: " + e);
      }
    }
  }

  toJson(err, coverage: boolean, outFolder: string,
         libraryVersion: string): any {
    winston.info("Dumping path information");
    let unifications = [];
    this.unifications.forEach(
        (uni) => {unifications.push([ uni[0].toString(), uni[1].toString() ])});

    const observations =
        Array.from(this.observations.values()).map(obs => obs.toJson());

    const testTimeMillis =
        TracerObservationState.DateRef.now() - this.createTime;

    let dumpObj: TracingResult =
        isNullOrUndefined(err)
            ? new APIModel(testTimeMillis, observations)
            : new LearningFailure(err, testTimeMillis, observations);

    if (dumpObj instanceof APIModel && coverage) {
      //@ts-ignore
      //@ts-ignore
      for (var file in global.__coverage__) {
        const moduleName = file.substring(file.indexOf("node_modules/") +
                                          "node_modules/".length);
        const collector = new istanbul.Collector();
        //@ts-ignore
        collector.add({file : global.__coverage__[file]});
        //@ts-ignore
        const coverageReport = istanbul.Report.create('json');
        coverageReport.opts.dir = path.resolve(outFolder, "coverage");
        coverageReport.opts.file =
            `${moduleName}-${libraryVersion}-coverage.json`;
        coverageReport.writeReport(collector, true);
        dumpObj.addCoverage(
            moduleName,
            istanbul
                .utils
                //@ts-ignore
                .summarizeCoverage({moduleName : global.__coverage__[file]}));
        dumpObj.addCoverageFile(
            `${coverageReport.opts.dir}/${coverageReport.opts.file}`);
      }
      //@ts-ignore
      global.__coverage__ = undefined
    }
    // dumpObj.unifications = unifications;

    return dumpObj;
  }

  typeVariableForHandler(h: NoRegretsProxyHandler): TypeVariable {
    return this.typeVariableForPathAndValue(h.path, h.originalValue);
  }

  typeVariableForPathAndValue(path: AccessPath, baseValue: any): TypeVariable {
    assert(!isNullOrUndefined(path));

    // First we check whether we have a type variable for the value itself
    if (typeof baseValue == "object" || typeof baseValue == "function") {
      let old = this.types.get(baseValue);
      if (!isNullOrUndefined(old)) {
        return old;
      }
    }

    // then we check whether we have a variable for the path,
    // this may also be the case for primitive base values
    let pre = this.infos[path.toString()];
    if (!isNullOrUndefined(pre)) {
      return pre;
    }

    let newOne = new TypeVariable(this.makeType(baseValue));

    if (!isNull(baseValue) &&
        (typeof baseValue == "object" || typeof baseValue == "function")) {
      this.types[baseValue] = newOne;
    }
    this.infos[path.toString()] = newOne;

    winston.info(`New type registered for path: ${path} and value`);
    return newOne;
  }

  _typeForValue(v: any): RegressionType {
    let old = this.types.get(v);
    if (!isNullOrUndefined(old)) {
      return old;
    }
    let ret = this.makeType(v);
    if (!isNullOrUndefined(ret)) {
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
    if (vtype == "function") {
      ret = new FunctionType({}, new BottomType(), new BottomType(), {});
    } else if (vtype == "object") {
      ret = new ObjectType({});
    } else if (vtype === "symbol" || vtype === "number" ||
               vtype === "boolean" || vtype === "string" ||
               vtype === "undefined") {
      ret = new PrimitiveType(vtype);
    }

    if (!isNullOrUndefined(ret)) {
      return ret;
    }
    throw new Error(`Implement me for ${vtype}`);
  }
}

export class Info {
  constructor(public type: TypeVariable) {}
}