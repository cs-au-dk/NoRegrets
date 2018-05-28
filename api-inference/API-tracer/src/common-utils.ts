import {isNullOrUndefined} from "util";

export interface IDictionary<TValue> {
    [id: string]: TValue;
}

export function mapObjectValues<T, R>(m: IDictionary<T>, mapper: (k: string, v: T) => R) {

    let newOne: IDictionary<R> = {};

    for(let k of Object.keys(m)) {
        newOne[k] = mapper(k, m[k]);
    }

    return newOne
}

export function convertToString(value: any): string {
    return typeof value == 'symbol' ? value.toString() : `${value}`;
}


/**
 * returns true if str is a string containing only a natural number.
 */
export function isNaturalNumberString(str: string) {
    let match = str.match("\\+?[0-9]+");
    return match!=null && match.length == 1 && match[0] == str;
}
