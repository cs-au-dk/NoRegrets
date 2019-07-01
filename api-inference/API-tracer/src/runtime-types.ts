import {none, Option, some} from "ts-option";
import {isNullOrUndefined} from "./JSAux";

let _ = require('lodash');

/**
 * Runtime types that we can recognise.
 * Note, there are other runtime-types containing pathStrs in the case we care that
 * a proto is a constructor prototype.
 */
export class IntersectionType {
  private types: Set<RuntimeType> = new Set();

  constructor(types?: string) {
    if (!isNullOrUndefined(types)) {
      // Assume that types is on the form [t1, t2, ..., tn]
      let splitTypes =
          types.substring(1, types.length - 1).replace(/ /g, '').split(",");
      for (let t of splitTypes) {
        this.types.add(t as RuntimeType);
      }
    }
  }

  public addType(t: RuntimeType) { this.types.add(t); }

  public toString(): string { return `[${Array.from(this.types).join(', ')}]`; }

  public isObjectLike(): boolean { return this.contains("object"); }

  public contains(t: RuntimeType): boolean { return this.types.has(t); }

  public equals(other: IntersectionType): boolean {
    return _.isEqual(this.types, other.types);
  }

  public size(): number { return this.types.size; }

  // Weird implementation since the types are contained in a set
  // However, it is only called with i = 0 for now.
  public get(i: number): RuntimeType {
    return Array.from(this.types.values())[i];
  }

  public toTypeString(): string { return Array.from(this.types).join(' ∧ '); }
}

export class UnionType {
  private types: Set<RuntimeType> = new Set();

  public addType(t: RuntimeType) { this.types.add(t); }

  public toTypeString(): string {
    return Array.from(this.types.values()).map(t => `(${t})`).join(" v ");
  }

  public contains(t: RuntimeType) {
    // Lodash doesn't support ES6 sets, so we must convert this.types to an
    // array
    return _.some(Array.from(this.types), rt => t === rt);
  }

  public equals(t: UnionType) { return _.isEqual(this, t); }

  public size(): number { return this.types.size; }

  public getTypes(): Array<RuntimeType> {
    return Array.from(this.types.values());
  }

  // Weird implementation since the types are contained in a set
  // However, it is only called with i = 0 for now.
  public get(i: number): RuntimeType {
    return Array.from(this.types.values())[i];
  }

  /**
   * returts true if subt is a subtype of this.
   * @param subt
   */
  public subType(subt: RuntimeType) {
    return Array.from(this.types.values()).some(rt => subType(subt, rt));
  }
}

const ft = new IntersectionType();
ft.addType('object');
ft.addType('function');
export const FunctionType = ft;

const at = new IntersectionType();
at.addType('object');
at.addType('Array');
export const ArrayType = at;

export const NON_EXIST = '__NON-EXISTING__';
export const IGNORE = '__IGNORE__';

export function getBuiltInProtoCount(t: RuntimeType): Option<number> {
  switch (t) {
    case 'object':
      return some(1);
    case 'function':
      return some(2);
    case 'number':
      return none;
    case 'boolean':
      return none;
    case 'symbol':
      return some(2);
    case 'string':
      return none;
    case 'undefined':
      return none;
    case 'throws':
      return none;
    case 'Array':
      return some(2);
    case 'ArrayBuffer':
      return some(2);
    case 'Buffer':
      return some(4);
    case 'DataView':
      return none;
    case 'Date':
      return some(2);
    case 'Error':
      return some(2);
    case 'EvalError':
      return none;
    case 'Float32Array':
      return some(3);
    case 'Float64Array':
      return some(3);
    case 'Int16Array':
      return some(3);
    case 'Int32Array':
      return some(3);
    case 'Int8Array':
      return some(3);
    case 'Map':
      return some(3);
    case 'Promise':
      return some(2);
    case 'RangeError':
      return some(3);
    case 'ReferenceError':
      return some(3);
    case 'RegExp':
      return some(2);
    case 'Set':
      return some(2);
    case 'SyntaxError':
      return some(3);
    case 'TypeError':
      return some(3);
    case 'URIError':
      return some(3);
    case 'Uint16Array':
      return some(3);
    case 'Uint32Array':
      return some(3);
    case 'Uint8Array':
      return some(3);
    case 'Uint8ClampedArray':
      return some(3);
    case 'WeakMap':
      return some(2);
    case 'WeakSet':
      return some(2);
    case 'EventEmitter':
      return some(2);
    case 'Stream':
      return some(3);  // 1+ EventEmitter
    case 'ServerResponse':
      return some(5);
    case 'IncomingMessage':
      return some(5);
    case '__NON-EXISTING__':
      return none;
    case '__IGNORE__':
      return none;
  }
}

export type RuntimeType = 'object'|'function'|'number'|'boolean'|'symbol'|
    'string'|'undefined'| 'bigint' |'throws'|'Array'|'ArrayBuffer'|'Buffer'|'DataView'|
    'Date'|'Error'|'EvalError'|'Float32Array'|'Float64Array'|'Int16Array'|
    'Int32Array'|'Int8Array'|'Map'|'Promise'|'RangeError'|'ReferenceError'|
    'RegExp'|'Set'|'SyntaxError'|'TypeError'|'URIError'|'Uint16Array'|
    'Uint32Array'|'Uint8Array'|'Uint8ClampedArray'|'WeakMap'|'WeakSet'|
    'EventEmitter'|'Stream'|'ServerResponse'|'IncomingMessage'|
    '__NON-EXISTING__'  // For some reason the compiler complains if we refer to
                        // the const instead :/
    |'__IGNORE__';

/**
 * returns rt1 <: rt2
 * @param rt1
 * @param rt2
 */
export function subType(rt1: RuntimeType, rt2: RuntimeType): boolean {
  if (rt1 === rt2) {
    return true;
  }
  if (rt2 === 'object') {
                return ['function', 'Array', 'ArrayBuffer', 'Buffer', 'Int32Array', 'Int8Array', 'Map', 'Promise', 'Set',
                  'Uint16Array', 'Uint32Array', 'Uint8Array', 'Uint8ClampedArray', 'Weakmap', 'WeakSet',
    'EventEmitter', 'Stream', 'ServerResponse', 'IncomingMessage'].includes(rt1);
  }
  return false;
}
