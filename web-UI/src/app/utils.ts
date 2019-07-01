import {Dictionary} from 'lodash';

import {
  AccessPath,
  WriteLabel
} from "../../../api-inference/API-tracer/src/paths";

import {
  AnalysisResults,
  RelationFailure,
  TypeDiff
} from "./datastructures/diffs";

export function dictionaryToKeyValueObjectList<T>(
    c: Dictionary<T>,
    sorter?: (a: any, b: any) => number | undefined): KeyValueObject<T>[] {
  let entries = [];
  for (let k in c) {
    entries.push({key : k, value : c[k]});
  }
  if (sorter !== undefined) {
    return entries.sort(sorter);
  }
  return entries;
}

export interface KeyValueObject<T> {
  key: PropertyKey;
  value: T
}

export function verToNum(s: PropertyKey): number {
  let v = s.toString().split("@")[1];
  let revs = v.split(".");
  return parseInt(revs[0]) * 1000000 + parseInt(revs[1]) * 1000 +
         parseInt(revs[2]);
}

export function compareVersions(a: KeyValueObject<AnalysisResults>,
                                b: KeyValueObject<AnalysisResults>) {
  return verToNum(a.key) - verToNum(b.key);
}

export function selectedRegressions(regressions: [ AccessPath, TypeDiff ][],
                                    toolName: string) {
  if (toolName === 'NoRegretsPlus') {
    return regressions;
  } else {
    return regressions.filter(
        ([ path, tr ]) =>
            !path.getLabels().some(l => l instanceof WriteLabel) &&
            !(tr instanceof RelationFailure &&
             tr.relationFailure.includes('Ignore')));
  }
}
