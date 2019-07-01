import {Dictionary} from "lodash";

import {AccessPath} from "../../../../api-inference/API-tracer/src/paths";
import {
  CoverageObject
} from "../../../../api-inference/test-runner/src/CoverageObject";

export class RegressionInfo {
  constructor(public regressions: [ AccessPath, TypeDiff ][]) {}

  static fromJson(arr) {
    let paths = [];
    for (let pair of arr.regressions) {
      paths.push(
          [ AccessPath.fromJson(pair["_1"]), TypeDiff.fromJson(pair["_2"]) ]);
    }
    return new RegressionInfo(paths);
  }
}

export interface AggregateClientDetails {
  executionTime: number, testTime: number, modelSize: number,
      compressedModelSize: number, pathsTotal: number, pathsCovered: number,
      clientOrModelSizeBytes: number
}

export class ClientDetail {
  constructor(public packageAtVersion: string, public succeeded: boolean,
              public error: String, public showError: boolean,
              public testTimeMillis: number, public executionTimeMillis: number,
              public modelSize: number, public compressedModelSize: number,
              public coverageObject: CoverageObject, public pathsTotal: number,
              public pathsCovered: number,
              public clientOrModelSizeBytes: number) {}
  static fromJson(i): ClientDetail {
    return new ClientDetail(i.packageAtVersion, i.succeeded, i.error, false,
                            i.testTimeMillis, i.executionTimeMillis,
                            i.modelSize, i.compressedModelSize,
                            i.coverageObject, i.pathsTotal, i.pathsCovered,
                            i.clientOrModelSizeBytes);
  }

  public toggleShowError(): void { this.showError = !this.showError; }

  public htmlStatementCoverage(): string {
    let res = '';
    for (var module in this.coverageObject.statementCoverage) {
      const sc = this.coverageObject.statementCoverage[module];
      res += `(${module}:${sc.pct}% [${sc.covered}/${sc.total}])  `
    }
    return res;
  }

  public htmlLineCoverage(): string {
    let res = '';
    for (var module in this.coverageObject.lineCoverage) {
      const sc = this.coverageObject.lineCoverage[module];
      res += `(${module}:${sc.pct}% [${sc.covered}/${sc.total}])  `
    }
    return res;
  }
}

export class AnalysisResults {
  constructor(public regressionInfo: RegressionInfo,
              public clientDetails: ClientDetail[],
              public coverageObject: CoverageObject) {}

  static fromJson(i): AnalysisResults {
    const clientDetails = [];
    for (let dt of i.clientDetails) {
      clientDetails.push(ClientDetail.fromJson(dt));
    }
    return new AnalysisResults(RegressionInfo.fromJson(i.regressionInfo),
                               clientDetails,
                               CoverageObject.fromJson(i.coverageObject));
  }

  public htmlStatementCoverage(): string {
    let res = '';
    for (var module in this.coverageObject.statementCoverage) {
      const sc = this.coverageObject.statementCoverage[module];
      res += `<li>${module} : ${sc.pct}% [${sc.covered}/${sc.total}]</li>`
    }
    return res;
  }

  public htmlLineCoverage(): string {
    let res = '';
    for (var module in this.coverageObject.lineCoverage) {
      const sc = this.coverageObject.lineCoverage[module];
      res += `<li>${module} : ${sc.pct}% [${sc.covered}/${sc.total}]</li>`
    }
    return res;
  }

  public linesTotal(): string {
    let total = 0;
    let covered = 0;
    for (var module in this.coverageObject.lineCoverage) {
      const sc = this.coverageObject.lineCoverage[module];
      covered += sc.covered;
      total += sc.total;
    }
    return `${covered}/${total} = ${(covered / total * 100).toFixed(2)}%`
  }

  public statementsTotal(): string {
    let total = 0;
    let covered = 0;
    for (var module in this.coverageObject.statementCoverage) {
      const sc = this.coverageObject.statementCoverage[module];
      covered += sc.covered;
      total += sc.total;
    }
    return `${covered}/${total} = ${(covered / total * 100).toFixed(2)}%`
  }
}

export abstract class TypeDiff {
  static fromJson(i) {
    if (i.jsonClass === "NoObservationInPost") {
      return NoObservationInPost.fromJson(i)
    } else if (i.jsonClass === "NoObservationInBase") {
      return NoObservationInBase.fromJson(i)
    } else if (i.jsonClass === "DifferentObservations") {
      return DifferentObservations.fromJson(i)
    } else if (i.jsonClass === "RelationFailure") {
      return RelationFailure.fromJson(i)
    }
    throw new Error("Unexpected " + i.jsonClass + " from ")
  }
}

export class NoObservationInPost extends TypeDiff {
  constructor(public obs: Observer[], public types: string[]) { super(); }

  static fromJson(i) { return new NoObservationInPost(i.obs, i.types); }

  toString(): string {
    return `no-observation-in-post, types in base: ${this.types}`
  }
}

export class NoObservationInBase extends TypeDiff {
  constructor(public obs: Observer[], public types: string[]) { super(); }

  static fromJson(i) { return new NoObservationInBase(i.obs, i.types); }

  toString(): string {
    return `no-observation-in-base, types in post: ${this.types}`
  }
}

export class DifferentObservations extends TypeDiff {
  constructor(public types: Dictionary<TypeDiff>) { super(); }

  static fromJson(i: Dictionary<any>) {
    let o = {};
    for (let type in i.types) {
      o[type] = TypeDiff.fromJson(i.types[type]);
    }
    return new DifferentObservations(o);
  }

  toString(): string {
    let s = "";
    for (let p in this.types) {
      s += p.toString() + ": " + this.types[p].toString() + ","
    }
    return s;
  }
}
export class RelationFailure extends TypeDiff {
  constructor(public obs: Dictionary<Observer[]>,
              public relationFailure: string) {
    super();
  }

  static fromJson(i) { return new RelationFailure(i.obs, i.relationFailure); }

  toString(): string { return `Relation failure: ${this.relationFailure}`; }
}

interface Observer {
  pv: PackageAtVersion
  stack: String
}

interface PackageAtVersion {
  packageName: string
  packageVersion: string
}
