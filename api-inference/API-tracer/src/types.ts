import * as assert from "assert";
import {isNullOrUndefined} from "util";

import {IDictionary, mapObjectValues} from "./common-utils";
import * as C from "./constants";
import {AccessPath, Label} from "./paths";

import winston = require("winston");
import _ = require('lodash');
import {RuntimeType} from "./runtime-types";

winston.level = 'info';

/**
 * A representation of a structural type (primitive or object)
 */

export class APIType {
  getRuntimeTypes(): Set<RuntimeType> { return this.runtimeTypes; }

  getProperties(): Map<Label, APIType> { return this.properties; }

  prettyRuntimeTypes(): string {
    return `{${Array.from(this.getRuntimeTypes()).join(', ')}}`;
  }

  addType(propertyPath: AccessPath, propagatedType: RuntimeType) {
    if (!propertyPath.hasNext()) {
      this.runtimeTypes.add(propagatedType);
    } else {
      this.addRest(propertyPath.getNext(), propagatedType);
    }
  }

  private addRest(path: AccessPath, propagatedType: RuntimeType) {
    let name = path.getName();

    let propertyType: APIType;
    if (this.properties.has(name)) {
      propertyType = this.properties.get(name);
    } else {
      propertyType = new APIType();
      this.properties.set(name, propertyType)
    }

    if (!path.hasNext()) {
      propertyType.runtimeTypes.add(propagatedType);
    } else {
      propertyType.addRest(path.getNext(), propagatedType);
    }
  }

  followPath(path: AccessPath): APIType|undefined {
    return this.followPath_(path);
  }

  private followPath_(path: AccessPath|undefined): APIType|undefined {
    console.log(this.properties);
    let name = path.getName();
    if (path.hasNext()) {
      if (this.properties.has(name)) {
        return this.properties.get(name).followPath(path.getNext());
      } else {
        return undefined;
      }
    } else {
      return this.properties.has(name) ? this.properties.get(name) : undefined;
    }
  }

  hasType(type: RuntimeType): boolean { return this.runtimeTypes.has(type); }

  /**
   * @param {APIType} other
   * Inserts the contents of other into this.
   */
  insert(other: APIType) {
    let pathTypePairs = other.collectPathTypePairs();
    _.forEach(pathTypePairs, (pair) => this.addType(pair[0], pair[1]));
  }

  static merge(t1: APIType, t2: APIType) {
    t1.insert(t2);
    t2.insert(t1);
  }

  collectPathTypePairs(): [ AccessPath, RuntimeType ][] {
    return  // DISABLED BECAUSE NOT UNDERSTOOD this.collectSubTypes_(new
            // AccessPath(C.ABS_IDENTIFIER, undefined));
  }

  private collectSubTypes_(subPath: AccessPath): [ AccessPath, RuntimeType ][] {
    let currentTypes = _.map<RuntimeType, [ AccessPath, RuntimeType ]>(
        Array.from(this.runtimeTypes.values()), rt => [subPath, rt]);

    let collectedSubTypes: [ AccessPath, RuntimeType ][] =
        _.flatMap(Array.from(this.properties.keys()), (prop) => {
          let newSubPath = subPath.extendWithLabel(prop);
          let propType = this.properties.get(prop);
          return propType.collectSubTypes_(newSubPath);
        });

    return _.concat(currentTypes, collectedSubTypes)
  }

  constructor(runtimeType?: RuntimeType) {
    this.properties = new Map();
    this.runtimeTypes = new Set();
    if (runtimeType !== undefined) {
      this.runtimeTypes.add(runtimeType);
    }
  }

  private runtimeTypes: Set<RuntimeType>;
  private properties: Map<Label, APIType>
}

export abstract class RegressionType extends Object {
  /**
   * This performs an unification when invoken on type variables!
   * If this or t2 is a variable a variable is returned
   */
  abstract lub(t2: RegressionType): RegressionType

      abstract toJSON(): any

      abstract _toString(visited: Set<RegressionType>): string

      abstract toIdString(): string

  isSame(t2): boolean { return this.toIdString() === t2.toIdString(); }
}

export class TypeVariable extends RegressionType {
  static count = 0;

  t: RegressionType;
  idx: number = TypeVariable.count++;

  constructor(t: RegressionType) {
    super();
    assert(!(t instanceof TypeVariable));
    assert(!isNullOrUndefined(t));
    this.t = t
  }

  lub(t2: RegressionType): RegressionType {
    if (t2 === this) return this;
    if (t2 instanceof TypeVariable) {
      throw new Error("Not accepted");
    } else {
      this.t = t2.lub(this.t);
      return this;
    }
  }

  public toString(): string { return this._toString(new Set()); }

  public toIdString(): string { return `V${this.idx}[...]`; }

  public _toString(visited: Set<RegressionType>): string {
    if (visited.has(this)) {
      return `V${this.idx}[...]`;
    } else {
      visited.add(this);
      return `V${this.idx}[${this.t._toString(visited)}]`;
    }
  }

  toJSON(): any { return this.t.toJSON(); }
}

export class BottomType extends RegressionType {
  lub(t2: RegressionType): RegressionType {
    if (t2 instanceof BottomType) return this;
    return t2;
  }

  toJSON(): any { return {"kind" : "BottomType"}; }

  public toString(): string { return this._toString(null); }

  public toIdString(): string { return this.toString(); }

  public _toString(visited: Set<RegressionType>): string { return "Bottom"; }
}

export class HasType extends RegressionType {
  lub(t2: RegressionType): RegressionType {
    if (t2 instanceof BottomType) return this;
    return t2;
  }

  toJSON(): any { return {"kind" : "HasType"}; }

  public toString(): string { return this._toString(null); }

  public toIdString(): string { return this.toString(); }

  public _toString(visited: Set<RegressionType>): string { return "Has"; }
}

export class PrimitiveType extends RegressionType {
  typeName: String;
  constructor(typeName: string) {
    super();
    this.typeName = typeName;
  }

  lub(t2: RegressionType): RegressionType {
    if (t2 instanceof BottomType || t2 instanceof HasType) return this;

    if (t2 instanceof UnionType) {
      return t2.lub(this);
    }

    if (t2 instanceof PrimitiveType && t2.typeName == this.typeName)
      return this;

    return new UnionType([ this, t2 ]);
  }

  public toString(): string { return this._toString(null); }

  public toIdString(): string { return this.toString(); }

  public _toString(visited: Set<RegressionType>): string {
    return `Primitive(${this.typeName})`;
  }

  toJSON(): any {
    return {"kind" : "PrimitiveType", "typeName" : this.typeName};
  }
}

export class KnownType extends RegressionType {
  lub(t2: RegressionType): RegressionType {
    if (t2 instanceof BottomType || t2 instanceof HasType) return this;

    if (t2 instanceof UnionType) {
      return t2.lub(this);
    }
    return new UnionType([ this, t2 ]);
  }

  public toString(): string { return this._toString(null); }

  public toIdString(): string { return this.toString(); }

  public _toString(visited: Set<RegressionType>): string {
    return `Known(boh)`;
  }

  toJSON(): any { return {"kind" : "KnownType", "name" : "boh"}; }
}

export class ObjectType extends RegressionType {
  constructor(public properties: IDictionary<RegressionType>) {
    super();
    assert(!isNullOrUndefined(properties));
  }

  lub(t2: RegressionType): RegressionType {
    if (t2 instanceof BottomType || t2 instanceof HasType) return this;

    if (t2 instanceof UnionType) {
      return t2.lub(this);
    }

    if (t2 instanceof ObjectType) {
      let changed = false;
      let newProps: IDictionary<RegressionType> = {};
      let allProps = new Set(Object.keys(t2.properties));
      for (let thisK of Object.keys(this.properties)) {
        allProps.add(thisK);
      }
      for (let k of allProps) {
        let newProperty = t2.properties[k];
        let oldProp = this.properties[k];
        if (!isNullOrUndefined(oldProp) && !isNullOrUndefined(newProperty)) {
          let lubbed = oldProp.lub(newProperty);
          if (!lubbed.isSame(oldProp)) changed = true;
          newProps[k] = lubbed;
        } else if (!isNullOrUndefined(oldProp)) {
          newProps[k] = oldProp;
          changed = true;
        } else if (!isNullOrUndefined(newProperty)) {
          newProps[k] = newProperty;
          changed = true;
        }
      }
      if (changed) return new ObjectType(newProps);
      return this;
    }

    return new UnionType([ this, t2 ]);
  }

  public toString(): string { return this._toString(new Set()); }

  public toIdString(): string { return this.toString(); }

  public _toString(visited: Set<RegressionType>): string {
    let props = Object.keys(this.properties)
                    .sort((a, b) => a.toString().localeCompare(b.toString()));
    let s = "Object(";
    for (let p of props) {
      s = s + `  ${p} -> ${this.properties[p]._toString(visited)}, `;
    }
    return s + ")"
  }

  toJSON(): any {
    return {
      "kind": "ObjectType",
          "properties": mapObjectValues(this.properties, (k, v) => v.toJSON())
    }
  }
}

export class FunctionType extends RegressionType {
  constructor(public properties: IDictionary<RegressionType>,
              public receiver: RegressionType,
              public returnType: RegressionType,
              public args: IDictionary<RegressionType[]>) {
    super();
  }

  lub(t2: RegressionType): RegressionType {
    if (t2 instanceof BottomType || t2 instanceof HasType) return this;

    if (t2 instanceof UnionType) {
      return t2.lub(this);
    }

    if (t2 instanceof FunctionType) {
      throw new Error("Implement me");
    }

    return new UnionType([ this, t2 ]);
  }

  toJSON(): any {
    return {
      "kind": "FunctionType",
          "properties": mapObjectValues(this.properties, (k, v) => v.toJSON()),
          "receiver": this.receiver.toJSON(),
          "returnType": this.returnType.toJSON(),
          "args": mapObjectValues(this.args, (k, v) => v.map(t => t.toJSON()))
    }
  }

  public toString(): string { return this._toString(new Set()); }

  public toIdString(): string { return this.toString(); }

  public _toString(visited: Set<RegressionType>): string { return "Function" }
}

export class UnionType extends RegressionType {
  constructor(public types: RegressionType[]) {
    super();
    assert(types.length);
    assert(types.every((item) => !(item instanceof UnionType)));
  }

  lub(t2: RegressionType): RegressionType {
    let newTypes = UnionType.lubOrAdd(this.types, t2);
    if (newTypes != this.types) {
      return new UnionType(newTypes);
    } else
      this;
  }

  private static lubOrAdd(types: RegressionType[],
                          tt: RegressionType): RegressionType[] {
    let ns: RegressionType[] = [];
    let haslubbed = false;
    let changed = false;
    for (let t of types) {
      let l = t.lub(tt);
      if (!(l instanceof UnionType)) {
        haslubbed = true;
        if (!l.isSame(t)) changed = true;
        ns.push(l);
      } else {
        ns.push(t);
      }
    }
    if (!haslubbed) ns.push(tt);
    if (changed || !haslubbed) {
      if (ns.length == 0) throw new Error("Unexpected empty union type");
      return ns;
    }

    return types;
  }

  public toString(): string { return this._toString(new Set()); }

  public toIdString(): string {
    this.types.sort((a, b) => a.toIdString().localeCompare(b.toIdString()));
    return this.toString();
  }

  _toString(visited: Set<RegressionType>) {
    return `Union(${this.types.map(x => x._toString(visited)).join(",")})`;
  }

  toJSON(): any {
    return { "kind": "UnionType", "types": this.types.map((v) => v.toJSON()) }
  }
}
