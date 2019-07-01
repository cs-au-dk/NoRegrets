import {AccessPath} from "../../API-tracer/src/paths";
import _ = require("lodash");
import {CoverageObject} from "./CoverageObject";

export abstract class TestResult {
  jsonClass: "TestReport"|"TestReportError";
  static TEST_RESULT_FILE_NAME = "test-result.json";
  protected initTestingTime: number;

  constructor(klass) {
    this.jsonClass = klass;
    this.initTestingTime = Date.now();
  }

  abstract toJson(): any
}

export class TestReport extends TestResult {
  private static winston = require("winston");
  public typeRegressions: TypeRegression[] = [];
  private pathCoverage: Map<string, boolean> = new Map<string, boolean>();

  // Sometimes we have to ignore pathStrs cause they are subpaths of pathStrs with
  // type errors Or they VOP values that are affected by type errors.
  private ignoredPaths: Set<string> = new Set();

  // We need coverageFiles even though we have the coverageObject since we
  // need computed an aggregated coverage of all the library's clients.
  private coverageFiles: string[] = [];

  // Map from module-name to % of statements covered
  private coverageObject: CoverageObject = new CoverageObject();

  constructor(paths: AccessPath[], public initTime: number) {
    super("TestReport");
    for (const p of paths) {
      this.pathCoverage.set(p.toString(), false);
    }
  }

  coverPath(path: AccessPath) {
    let pathStr = path.toString();
    if (this.pathCoverage.has(pathStr)) {
      this.pathCoverage.set(pathStr, true);
    }
  }

  pathsTotal = () => this.pathCoverage.size;

  pathsCovered = () => Array.from(this.pathCoverage.values())
                           .filter((covered) => covered)
                           .length;

  /**
   * We ignore path if we find a type-regression on it.
   * We need to track these pathStrs since it indicates that we cannot trust the
   * value of the path. So if the path is later needed in a synthesis, then we
   * cannot complete the synethesis.
   * @param path
   */
  ignorePath(path: AccessPath) { this.ignoredPaths.add(path.toString()); }

  isIgnoredPath(path: AccessPath|string) {
    if (path instanceof AccessPath) {
      return this.ignoredPaths.has(path.toString());
    } else {
      return this.ignoredPaths.has(path)
    }
  }

  toJson(): any {
    let oRes: any = {};
    oRes.jsonClass = this.jsonClass;
    oRes.typeRegressions = this.typeRegressions.map(reg => {
      let regCopy: any = _.cloneDeep(reg);
      regCopy.path = regCopy.path.toJson();
      return regCopy;
    });
    oRes.pathsTotal = this.pathsTotal();
    oRes.pathsCovered = this.pathsCovered();
    oRes.testTimeMillis = Date.now() - this.initTestingTime;
    oRes.executionTimeMillis = Date.now() - this.initTime;
    oRes.coverageObject = this.coverageObject;
    oRes.coverageFiles = this.coverageFiles;
    return oRes;
  }

  addTypeRegression(t: TypeRegression) {
    TestReport.winston.debug(`There was a type error on path ${t.path} \t\t ${
        t.observedType} not in ${t.modelType}`);
    this.typeRegressions.push(t);
  }

  addCoverage(name: string, covObj: any) {
    this.coverageObject.addStatementCoverage(name, covObj.statements);
    this.coverageObject.addLineCoverage(name, covObj.lines);
    this.coverageObject.addlinesTotal(covObj.lines.covered);
  }

  addCoverageFile(path: string) { this.coverageFiles.push(path); }
}

export class TestReportError extends TestResult {
  constructor(public msg: string, private initTime: number) {
    super("TestReportError");
  }

  toJson(): any {
    return {
      jsonClass : this.jsonClass,
      msg : this.msg,
      testTimeMillis : 0,
      executionTimeMillis : Date.now() - this.initTime
    };
  }
}

export class TypeRegression {
  jsonClass: "TypeRegression" = "TypeRegression";
  public covariant: boolean;

  constructor(public path: AccessPath, public modelType: String,
              public observedType: String) {
    this.covariant = this.path.isCovariant();
  }
}