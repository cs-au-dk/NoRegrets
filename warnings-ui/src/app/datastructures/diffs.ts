import {AccessPath} from "../../../../api-inference/API-tracer/src/paths";
import {IDictionary} from "./status";

export class KnowledgeDiff {
  constructor(public paths: [AccessPath, TypeDiff][]) {
  }

  static fromJson(arr) {
    let paths = [];
    for (let pair of arr.paths) {
      paths.push([AccessPath.fromJson(pair["_1"]), TypeDiff.fromJson(pair["_2"])]);
    }
    return new KnowledgeDiff(paths);
  }
}

export abstract class TypeDiff {
  static fromJson(i) {
    if(i.jsonClass === "NoObservationInPost") {
      return NoObservationInPost.fromJson(i)
    }
    else if(i.jsonClass === "NoObservationInBase") {
      return NoObservationInBase.fromJson(i)
    }
    else if(i.jsonClass === "DifferentObservations") {
      return DifferentObservations.fromJson(i)
    }
    else if(i.jsonClass === "RelationFailure") {
      return RelationFailure.fromJson(i)
    }
    throw new Error("Unexpected " + i.jsonClass + " from ")
  }
}

export class NoObservationInPost extends TypeDiff {
  constructor(public obs: Observer[], public types: string[]) {super();}

  static fromJson(i) {
    return new NoObservationInPost(i.obs, i.types);
  }

  toString(): string {
    return `no-observation-in-post, types in base: ${this.types}`
  }
}

export class NoObservationInBase extends TypeDiff {
  constructor(public obs: Observer[], public types: string[]) {super();}

  static fromJson(i) {
    return new NoObservationInBase(i.obs, i.types);
  }

  toString(): string {
    return `no-observation-in-base, types in post: ${this.types}`
  }
}

export class DifferentObservations extends TypeDiff {
  constructor(public types: IDictionary<TypeDiff>) {
    super();
  }

  static fromJson(i: IDictionary<any>) {
    let o = {};
    for(let type in i.types) {
      o[type] = TypeDiff.fromJson(i.types[type]);
    }
    return new DifferentObservations(o);
  }

  toString(): string {
    let s = "";
    for(let p in this.types) {
      s += p.toString() + ": " + this.types[p].toString() + ","
    }
    return s;
  }

}
export class RelationFailure extends TypeDiff {
  constructor(public obs: IDictionary<Observer[]>,
              public relationFailure: string) {super();}

  static fromJson(i) {
    return new RelationFailure(i.obs, i.relationFailure);
  }

  toString(): string {
    return `Relation failure: ${this.relationFailure}`;
  }
}

interface Observer {
  pv
  stack: String
}
