export interface BenchmarkStatus {
  key: string,
  name: string,
  pathCountInDiff: number,
  clientCountInDiff: number,
  totalClientCount: number,
  totalPathCount: number,
  totalPathsCovered: number,
  minorPatchUpdates: number,
  majorUpdates: number,
  tool: string,
  withCoverage: boolean,
  withIgnoreTags: boolean
};

//export class BenchmarksStatus {
//  constructor(public observations: IDictionary<IDictionary<Status>>,
//              public diffKeys: IDictionary<BenchmarkStatus>) {}
//
//  static fromJson(o): BenchmarksStatus {
//    return new BenchmarksStatus(o.observations, o.diffKeys)
//  }
//}

export interface Status {
  short: string,
  cacheFileKey: string[],
  observations: number
}
