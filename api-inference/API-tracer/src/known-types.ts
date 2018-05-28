
import {isNullOrUndefined} from "util";
import {Stream} from "stream";
import {EventEmitter} from "events";
import {RuntimeType} from "./runtime-types";

export class KnownTypes {
    static knownTypes = new KnownTypes();

    private constructor() {}

    [index: string]: any
    Array = typeof Array ? Array : undefined;
    ArrayBuffer = typeof ArrayBuffer ? ArrayBuffer : undefined;
    Buffer = typeof Buffer ? Buffer : undefined;
    DataView = typeof DataView ? DataView : undefined;
    Date = typeof Date ? Date : undefined;
    Error = typeof Error ? Error : undefined;
    EvalError = typeof EvalError ? EvalError : undefined;
    Float32Array = typeof Float32Array ? Float32Array : undefined;
    Float64Array = typeof Float64Array ? Float64Array : undefined;
    Int16Array = typeof Int16Array ? Int16Array : undefined;
    Int32Array = typeof Int32Array ? Int32Array : undefined;
    Int8Array = typeof Int8Array ? Int8Array : undefined;
    Map = typeof Map ? Map : undefined;
    Promise = typeof Promise ? Promise : undefined;
    RangeError = typeof RangeError ? RangeError : undefined;
    ReferenceError = typeof ReferenceError ? ReferenceError : undefined;
    RegExp = typeof RegExp ? RegExp : undefined;
    Set = typeof Set ? Set : undefined;
    Symbol = typeof Symbol ? Symbol : undefined;
    SyntaxError = typeof SyntaxError ? SyntaxError : undefined;
    TypeError = typeof TypeError ? TypeError : undefined;
    URIError = typeof URIError ? URIError : undefined;
    Uint16Array = typeof Uint16Array ? Uint16Array : undefined;
    Uint32Array = typeof Uint32Array ? Uint32Array : undefined;
    Uint8Array = typeof Uint8Array ? Uint8Array : undefined;
    Uint8ClampedArray = typeof Uint8ClampedArray ? Uint8ClampedArray : undefined;
    WeakMap = typeof WeakMap ? WeakMap : undefined;
    WeakSet = typeof WeakSet ? WeakSet : undefined;
    //Node API;
    EventEmitter = typeof EventEmitter ? EventEmitter : undefined;
    Stream = typeof Stream ? Stream : undefined;

    knownType(target: {}): RuntimeType {
        let known = KnownTypes.knownTypes;
        for(let k of Object.getOwnPropertyNames(known)) {
            if(!isNullOrUndefined(known[k]) &&
                target instanceof known[k]) {
                return k as RuntimeType;
            }
        }
        return null;
    }
}