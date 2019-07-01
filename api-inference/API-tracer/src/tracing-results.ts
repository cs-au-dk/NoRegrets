import {CoverageObject} from "../../test-runner/src/CoverageObject";

import {Observation, ReadObservation} from "./observations";

export abstract class TracingResult {
  jsonClass: "LearningFailure"|"APIModel";
  observations: Observation[];
  testTimeMillis: number;
  unifications: any;

  constructor(klass) { this.jsonClass = klass; }

  static fromJson(i): TracingResult {
    if (i.jsonClass === "APIModel") {
      return APIModel.fromJson(i)
    } else if (i.jsonClass === "LearningFailure") {
      return LearningFailure.fromJson(i)
    }
    throw new Error("Unexpected " + i.jsonClass + " from ")
  }

  abstract toString(): string;
}

export class APIModel extends TracingResult {
  unifications: any;
  coverageObject: CoverageObject;
  private coverageFiles: string[] = [];

  constructor(testTimeMillis: number, public observations: Observation[]) {
    super("APIModel");
    this.testTimeMillis = testTimeMillis;
    this.coverageObject = new CoverageObject();
  }

  static fromJson(i): APIModel {
    const obsArr = [];
    for (let obs of i.observations) {
      obsArr.push(Observation.fromJson(obs));
    }

    return new APIModel(i.testTimeMillis, obsArr);
  }

  prettyString(): string {
    return this.observations.map(obs => obs.toString()).join("\n");
  }

  public addCoverage(name: string, covObj: any) {
    this.coverageObject.addStatementCoverage(name, covObj.statements);
    this.coverageObject.addLineCoverage(name, covObj.lines);
    this.coverageObject.addlinesTotal(covObj.lines.covered);
  }

  addCoverageFile(path: string) { this.coverageFiles.push(path); }

  toString(): string { return "APIModel"; }
}

export class LearningFailure extends TracingResult {
  unifications: any;

  constructor(msg: string, testTimeMillis: number,
              public observations: Observation[]) {
    super("LearningFailure");
    this.testTimeMillis = testTimeMillis;
    this.msg = msg;
  }

  static fromJson(i): LearningFailure {
    const obsArr = [];
    for (let obs of i.observations) {
      obsArr.push(Observation.fromJson(obs));
    }

    return new LearningFailure(i.msg, i.testTimeMillis, obsArr);
  }

  toString(): string { return "LearningFailure"; }

  msg: string
}
