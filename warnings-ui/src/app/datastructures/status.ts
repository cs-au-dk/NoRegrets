import {KnowledgeDiff} from "./diffs";

export interface IDictionary<X> {
  [index: string]: X
}

export interface DiffInfo {
  key: String,
  diffObeservationCount: number,
  diffClientCount: number,
  totalClientCount: number,
  totalObservationCount: number
};

export class BenchmarksStatus {
  constructor(public observations: IDictionary<IDictionary<Status>>,
              public diffKeys: IDictionary<DiffInfo>) {}

  static fromJson(o): BenchmarksStatus {
    return new BenchmarksStatus(o.observations, o.diffKeys)
  }
}

export interface Status {
  short: string,
  cacheFileKey: string[],
  observations: number
}
