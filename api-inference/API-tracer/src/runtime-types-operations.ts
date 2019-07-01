/**
 * NOTE,
 * These functions are in a separate file since they require KnownTypes which depends on Node.JS
 * Therefore, they cannot exist in the runtime-types file since the warnings-ui depends on that file.
 */

import {KnownTypes} from "./known-types";
import {isNullOrUndefined} from "util";
import {ThrownError} from "./throwError";
import {IGNORE, IntersectionType, NON_EXIST, RuntimeType, UnionType} from "./runtime-types";

export function getRuntimeType (value: any): RuntimeType {
    let knownt = KnownTypes.knownTypes.knownType(value);
    if(!isNullOrUndefined(knownt)) {
      return knownt;
    }
    if(!isNullOrUndefined(value) && value instanceof ThrownError) return "throws";
    if(value === NON_EXIST) {
      return NON_EXIST;
    }
    if(value === IGNORE) {
      return IGNORE;
    }
    return typeof value;
  }



//@Deprecated
export function getIntersectionType (value: any): IntersectionType {
    let t: IntersectionType = new IntersectionType();
    t.addType(getRuntimeType(value));
    while ((typeof value === 'function' || typeof value === 'object' )
    && !isNullOrUndefined(value)) {
      value = Object.getPrototypeOf(value);
      if (value != null)  {
        t.addType(getRuntimeType(value));
      }
    }
    return t;
  }


export const BoolUnionType = new UnionType();
BoolUnionType.addType(getRuntimeType(true));
export const ObjUnionType = new UnionType();
ObjUnionType.addType(getRuntimeType({}));
export const StrUnionType = new UnionType();
StrUnionType.addType(getRuntimeType(""));
export const UndefinedUnionType = new UnionType();
UndefinedUnionType.addType(getRuntimeType(undefined));
export const NumUnionType = new UnionType();
NumUnionType.addType(getRuntimeType(0));
export const SymbolUnionType = new UnionType();
SymbolUnionType.addType(getRuntimeType(Symbol('s')));
export const ExceptionUnionType = new UnionType();
ExceptionUnionType.addType(getRuntimeType(new ThrownError({})));
export const CircleTypeStr = "◦";
export const NonExistingUnionType = new UnionType();
NonExistingUnionType.addType(NON_EXIST);
export const IgnoreUnionType = new UnionType();
IgnoreUnionType.addType(IGNORE);

export function isPrimitive(t: UnionType | RuntimeType) {
  if (t instanceof UnionType) {
    return BoolUnionType.equals(t) ||
      StrUnionType.equals(t) ||
      UndefinedUnionType.equals(t) ||
      NumUnionType.equals(t) ||
      SymbolUnionType.equals(t)
  } else {
    return t === "boolean"
      || t === "string"
      || t === "undefined"
      || t === "number"
      || t === "symbol";
  }
}

