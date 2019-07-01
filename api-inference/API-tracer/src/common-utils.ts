import {isNullOrUndefined} from "util";

export interface IDictionary<TValue> {
  [id: string]: TValue;
}

export function mapObjectValues<T, R>(m: IDictionary<T>,
                                      mapper: (k: string, v: T) => R) {
  let newOne: IDictionary<R> = {};

  for (let k of Object.keys(m)) {
    newOne[k] = mapper(k, m[k]);
  }

  return newOne;
}

export function convertToString(value: any): string {
  return typeof value == 'symbol' ? value.toString() : `${value}`;
}

/**
 * returns true if str is a string containing only a natural number.
 */
export function isNaturalNumber(property: PropertyKey) {
  const str = Number.isInteger((property as number)) ? (property as number) + ''
                                                     : property.toString();
  const match = str.match("\\+?[0-9]+");
  return match != null && match.length == 1 && match[0] == str;
}

// Returns true if the property p is in the prototype chain of obj
export function hasInChain(obj: object, p: PropertyKey): boolean {
  return obj !== undefined && obj !== null &&
         ((obj.hasOwnProperty !== undefined
               ? obj.hasOwnProperty(p)
               : Object.getOwnPropertyNames(obj).includes('p')) ||
          hasInChain(Object.getPrototypeOf(obj), p));
}