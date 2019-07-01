/**
 * Model of object and function types
 */
import * as assert from "assert";
import {none, Option, option, some} from "ts-option";
import {isNullOrUndefined} from "util";

import {
  Observation,
  primitive,
  ReadObservation,
  WriteObservation
} from "../../API-tracer/src/observations";
import {
  AccessPath,
  ApplicationLabel,
  ArgLabel,
  Label,
  PropertyLabel,
  ProtoLab,
  RequireLabel
} from "../../API-tracer/src/paths";
import {RuntimeType, UnionType} from "../../API-tracer/src/runtime-types";
import {APIModel} from "../../API-tracer/src/tracing-results";

import {MissingImplementationException} from "./exceptions";

import _ = require("lodash");

export class ModelValue {
  private properties: Map<Label, ModelValue>;
  public isNative: boolean;
  public didThrow: boolean;
  public type: UnionType;

  constructor(type: UnionType|RuntimeType, public values: Option<primitive[]>,
              public path: AccessPath,
              public valueOriginPath: Option<AccessPath>, public id: number,
              public isKnownValue: boolean, isNative: boolean|undefined,
              didThrow?: boolean|undefined) {
    this.properties = new Map<Label, ModelValue>();
    if (!(type instanceof UnionType)) {
      this.type = new UnionType();
      this.type.addType(type);
    } else {
      this.type = type;
    }

    if (isNative === undefined) {
      this.isNative = false
    } else {
      this.isNative = isNative;
    }
    this.didThrow = didThrow === undefined ? false : didThrow
  }

  private cloneShallow(): ModelValue {
    const clone = new ModelValue(
        this.type, this.values, this.path, this.valueOriginPath, this.id,
        this.isKnownValue, this.isNative, this.didThrow);
    clone.properties = new Map(this.properties);
    return clone;
  }

  public static buildModel(apiModel: APIModel|Observation[]): ModelValue {
    const obs = apiModel instanceof APIModel ? apiModel.observations : apiModel;
    const obsSorted: Observation[] =
        obs.sort((obs1, obs2) => obs1.path.length() - obs2.path.length());

    const requireObs = obsSorted[0] as ReadObservation;
    const remainingObs = obsSorted.slice(1);
    const ut = new UnionType();
    ut.addType(requireObs.type);
    const model = new ModelValue(ut, requireObs.value.map(p => [p]),
                                 requireObs.path, requireObs.valueOriginPath,
                                 requireObs.id.get, requireObs.isKnownValue,
                                 requireObs.isNative, requireObs.didThrow);

    for (let o of remainingObs) {
      model.add(o);
    }
    return model;
  }

  public prettyString(detailed?: boolean, indent?: number): string {
    indent = isNullOrUndefined(indent) ? 0 : indent;
    detailed = (!(isNullOrUndefined(detailed) || !detailed));
    let indentStr = "  ".repeat(indent);
    let str = `${indentStr}type : ${this.type.toTypeString()}\n`;

    let indentStrExt = "  ".repeat(indent + 1);
    if (detailed && this.values.isDefined) {
      str += `${indentStr}values : {${this.values.get.join(', ')}}\n`;
    }

    if (detailed && this.valueOriginPath.isDefined) {
      str += `${indentStr}valueOriginPath : {${
          this.valueOriginPath.get.toString()}}\n`;
    }

    if (detailed) {
      str += `${indentStr}id : ${this.id}\n`;
    }

    if (!_.isEmpty(this.properties)) {
      str += `${indentStr}properties : {\n`;
      for (let prop of this.properties.keys()) {
        str += `${indentStrExt}${prop} : {\n`;
        str += this.properties.get(prop).prettyString(detailed, indent + 2);
        str += `${indentStrExt}}\n`;
      }
    }

    return str;
  }

  /**
   * Augments the model with the information from o
   * Notice, observations must be sorted according to path length
   * @param {ReadObservation} o
   */
  private add(o: Observation): void {
    let reversePath = (p: AccessPath): Label[] => {
      let base = p.getName();
      if (p.hasNext()) {
        let rest = reversePath(p.getNext());
        rest.push(base);
        return rest;
      } else {
        return [ base ];
      }
    };

    let pathRev = reversePath(o.path);
    if (!(pathRev[0] instanceof RequireLabel)) {
      throw Error("All pathStrs must begin with a require label");
    }
    // Call with slice to remove the require label
    this.add_(pathRev.slice(1), o);
  }

  // Aux method used by add
  private add_(revPath: Label[], o: Observation): void {
    let propLab = revPath[0];
    let isHead = revPath.length === 1;

    // This seems slightly odd, but it's more convenient than converting labels
    // to (and from) strings when they are used as keys
    let propIfHas: Label|undefined = _.find(Array.from(this.properties.keys()),
                                            (l: Label) => l.equals(propLab));

    if (isHead) {
      if (propIfHas) {
        assert(!(propIfHas instanceof ApplicationLabel),
               "Union types should have been removed from functions");
        (this.properties.get(propIfHas) as ModelValue).merge(o);
        return;
      }
      let mVal;
      if (o instanceof ReadObservation) {
        const oCast = o as ReadObservation;
        mVal =
            new ModelValue(oCast.type, oCast.value.map(p => [p]), oCast.path,
                           oCast.valueOriginPath, oCast.id.get,
                           oCast.isKnownValue, oCast.isNative, oCast.didThrow);
      } else {
        const oCast = o as WriteObservation;
      }

      this.properties.set(propLab, mVal);
    } else {
      if (propIfHas) {
        (this.getPropertyModel(propIfHas)).add_(revPath.slice(1), o);
      } else {
        throw new Error(
            "Observations must be inserted in sorted order according to the path length")
      }
    }
  }

  private merge(o: Observation): void {
    if (o instanceof WriteObservation) {
      throw new MissingImplementationException(
          "How do we merge a write observation?")
    }
    const oCast = o as ReadObservation;
    this.type.addType(oCast.type);
    if (oCast.value.isDefined) {
      let currentValues = this.values.getOrElse([]);
      currentValues.push(oCast.value.get);
      this.values = option(currentValues);
    }
    this.id = Math.min(oCast.id.get, this.id);
  }

  public getArgumentLabelsSorted(): ArgLabel[] {
    return _.sortBy(this.getLabelsOfType(ArgLabel), lab => lab.idx);
  }

  /**
   * The constructor arg is a slightly hackish way to make the instanceof check
   * work.
   * @returns {T[]}
   */
  public getLabelsOfType<T extends Label>(constructor:
                                              {new(...params: any[]): T}): T[] {
    return _.filter(Array.from(this.properties.keys()),
                    (l) => l instanceof constructor) as T[];
  }

  /**
   * @returns true if modelValue has any arguments that either directly are
   * functions or that have properties that are functions (methods)
   */
  public isHigherOrderFunction() {
    let argLabels = this.getArgumentLabelsSorted();
    return _.some(argLabels, (lab: Label) => {
      let argValue = this.properties.get(lab) as ModelValue;

      let containsFun = (value: ModelValue) => {
        return value.type.toTypeString().includes("function") ||
               _.some(Array.from(value.properties.keys()), (lab: Label) => {
                 return !(lab instanceof ArgLabel) &&
                        containsFun(value.properties.get(lab) as ModelValue);
               })
      };
      return containsFun(argValue);
    });
  }

  public getPropertyModel(lab: Label) { return this.properties.get(lab); }

  /**
   * Similar to getPropertyModel, but uses the equals method of Label instead of
   * referential equality of the labels Notice, it's considerably slower than
   * getPropertyModel, so use getPropertyModel whenever a reference to the
   * required Lab already exists.
   * @param lab
   */
  public getPropertyModelByEquality(lab: Label): ModelValue {
    const mValLab =
        Array.from(this.properties.keys()).find(ownLab => ownLab.equals(lab));
    assert(
        mValLab,
        "getPropertyModelByEquality called with lab not existing on ModelValue");
    return this.properties.get(mValLab) as ModelValue;
  }

  public hasLabel(lab: Label): boolean {
    return Array.from(this.properties.keys()).some(key => key.equals(lab));
  }

  public getPropertyKeys(): Label[] {
    return Array.from(this.properties.keys());
  }

  public getProperties(): [ Label, ModelValue ][] {
    return Array.from(this.properties.entries())
  }

  /**
   * Note. This is only meant to be used for testing purposes.
   * TODO use an interface to hide implementation
   * @param label
   * @param value
   */
  public addProperty(label: Label, value: ModelValue) {
    this.properties.set(label, value);
  }

  /**
   * Returns the number of prototypes above the modelValue
   */
  public numProtos(): number {
    if (this.hasLabel(ProtoLab)) {
      const protoMVal = this.getPropertyModelByEquality(ProtoLab);
      return 1 + protoMVal.numProtos();
    }
    return 0;
  }

  /**
   * Returns the n'th prototype model value
   * throws an exception if this does not have n prototypes
   * @param n
   */
  public getNthPrototype(n: number): ModelValue {
    if (n > 0) {
      return this.getPropertyModelByEquality(ProtoLab).getNthPrototype(n - 1);
    } else {
      return this;
    }
  }

  /**
   * Returns true if the Symbol.iterator was read from the array at some point.
   * This indicates that the array was iterated by a for-loop at some point in
   * the execution. When that happens the content of the array is not modelled
   * with the pathStrs array.idx, but instead
   * array.Symbol(iterator).next()[callid].value, where the order of the callids
   * determine the order of the elements. In an ideal setting, we would restore
   * the original array by looking at all the calls. However, doing so, is quite
   * difficult since there could be a varying number of prototypes at different
   * places in the accesspath. For example, the iterator symbol is typically on
   * the prototype or the prototype of the prototype of the original array.
   */
  public isIteratedArray(): boolean {
    if (!this.type.contains("Array")) {
      return false;
    }
    const iteratorLabel = new PropertyLabel("Symbol(Symbol.iterator)");
    try {
      const firstProto = this.getNthPrototype(1);
      if (firstProto.hasLabel(iteratorLabel)) {
        return true;
      }
      const secondProto = this.getNthPrototype(2);
      return secondProto.hasLabel(iteratorLabel);
    } catch (e) {
      return false;
    }
  }

  /**
   * Returns the modelValue-protoCount pair where modelValue represents that
   * prototype with the lab` and protocount represents the number of prototypes
   * between `this` and the returned modelValue. Note, that protoCount is
   * strictly larger than 0, i.e., the method does not check for the presence of
   * lab on `this`.
   * @param lab
   */
  public getPropertyInChainOfPrototype(lab: Label):
      Option<[ ModelValue, number ]> {
    let protoMVal: ModelValue = this;
    let protoCount = 0;

    while (protoMVal.hasLabel(ProtoLab)) {
      protoMVal = protoMVal.getPropertyModelByEquality(ProtoLab);
      protoCount++;
      if (protoMVal.hasLabel(lab)) {
        return some<[ ModelValue, number ]>(
            [ protoMVal.getPropertyModelByEquality(lab), protoCount ]);
      }
    }
    return none;
  }
}
