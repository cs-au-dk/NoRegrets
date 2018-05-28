
import {IDictionary} from "./datastructures/status";

export function _getEntries<T>(c: IDictionary<T>, sorter?: (a:any,b:any) => number | undefined) {
  let entries = [];
  for(let k in c) {
    entries.push({key: k,  value: c[k]});
  }
  if (sorter !== undefined) {
    return entries.sort(sorter);
  }
  return entries;
}

export function verToNum(s) {
  let v = s.split("@")[1];
  let revs = v.split(".");
  return parseInt(revs[0])*1000000 + parseInt(revs[1])*1000 + parseInt(revs[2]);
}
