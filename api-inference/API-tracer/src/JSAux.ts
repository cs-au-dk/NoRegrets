import {none, Option, some} from "ts-option";
import _ = require("lodash");

export function getProtoWithProp(target: {},
                                 p: PropertyKey): Option<[ {}, number ]> {
  let realTarget = target;
  let desc: PropertyDescriptor|undefined;
  let protoCount = 0;
  while (realTarget !== null) {
    desc = Object.getOwnPropertyDescriptor(realTarget, p);
    if (desc != null) {
      break;
    }
    realTarget = Object.getPrototypeOf(realTarget);
    protoCount++;
  }
  if (desc != null) {
    return some<[ {}, number ]>([ realTarget, protoCount ]);
  }
  return none;
}

export function getNumberOfProtos(o: {}): number {
  let count = 0;
  while (true) {
    o = Object.getPrototypeOf(o);
    if (o == null) {
      break
    }
    count++;
  }
  return count;
}

// Returns all own and inherited enumerable keys of o
export function allEnumerableInheritedKeys(o: {}): string[] {
  return _.keysIn(o)
}

// Returns all own and inherited keys of o
export function allInheritedKeys(o: {}): string[] {
  const proto = Object.getPrototypeOf(o);
  return Object.getOwnPropertyNames(o).concat(proto ? allInheritedKeys(proto)
                                                    : []);
}

export const isNullOrUndefined = (x) => x == null;

export const assert = (c) => {
  if (!c) throw new Error("AssertionError");
};
