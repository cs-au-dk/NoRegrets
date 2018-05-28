
import {isNullOrUndefined} from "util";
import {KnownTypes} from "./known-types";

/**
 * Runtime types that we can recognise.
 * Note, there are other runtime-types containing paths in the case we care that a proto is a constructor prototype.
 */
export class UnionType {
    private types: Set<RuntimeType> = new Set();

    public addType(t: RuntimeType) {
        this.types.add(t);
    }

    public toString(): string {
       return `[${Array.from(this.types).join(', ')}]`;
    }

}

export type RuntimeType = "object"
    | "function"
    | "number"
    | "boolean"
    | "symbol"
    | "string"
    | "undefined"
    | "throws"
    | "Array"
    | "ArrayBuffer"
    | "Buffer"
    | "DataView"
    | "Date"
    | "Error"
    | "EvalError"
    | "Float32Array"
    | "Float64Array"
    | "Int16Array"
    | "Int32Array"
    | "Int8Array"
    | "Map"
    | "Promise"
    | "RangeError"
    | "ReferenceError"
    | "RegExp"
    | "Set"
    | "SyntaxError"
    | "TypeError"
    | "URIError"
    | "Uint16Array"
    | "Uint32Array"
    | "Uint8Array"
    | "Uint8ClampedArray"
    | "WeakMap"
    | "WeakSet"
    | "EventEmitter"
    | "Stream";
