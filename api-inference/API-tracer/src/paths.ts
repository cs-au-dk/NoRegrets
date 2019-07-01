import {Constants} from "./constants";
import {assert, isNullOrUndefined} from "./JSAux";

export class RequireLabel implements Label {
  equals(l: Label): boolean {
    return JSON.stringify(this.toJson()) === JSON.stringify(l.toJson());
  }

  constructor(public name: string) {}

  toString(): string { return `require(${this.name})`; }

  static contains(lab: any): boolean {
    return lab.jsonClass === "RequireLabel";
  }

  static fromJson(lab: any): RequireLabel {
    assert(RequireLabel.contains(lab));
    return new RequireLabel(lab.name);
  }

  toJson(): any {
    return { jsonClass: "RequireLabel", name: this.name }
  }

  toFSEPaperRepString(): string {
    return this.toString();
  }

  toECOOPPaperRepString(): string {
    return this.toString();
  }
}

export class ArgLabel implements Label {
  equals(l: Label): boolean {
    return JSON.stringify(this.toJson()) === JSON.stringify(l.toJson());
  }

  constructor(public idx: number) {}

  toString(): string { return "arg" + this.idx; }

  toFSEPaperRepString(kappa?: number): string {
    return `→${kappa.toString().sup()}ARG${this.idx.toString().sub()}`
  }

  static contains(lab: any): boolean { return lab.jsonClass === "ArgLabel"; }

  static fromJson(lab: any): ArgLabel {
    assert(ArgLabel.contains(lab));
    return new ArgLabel(lab.idx);
  }

  toJson(): any {
    return { jsonClass: "ArgLabel", idx: this.idx }
  }

  toECOOPPaperRepString(): string {
    return this.toString();
  }
}

export class AccessLabel implements Label {
  equals(l: Label): boolean {
    return JSON.stringify(this.toJson()) === JSON.stringify(l.toJson());
  }

  constructor() {}

  toString(): string { return "AccessLabel"; }

  static contains(lab: any): boolean { return lab.jsonClass === "AccessLabel"; }

  static fromJson(lab: any): AccessLabel {
    assert(AccessLabel.contains(lab));
    return new AccessLabel();
  }

  toJson(): any {
    return { jsonClass: "AccessLabel" }
  }

  toFSEPaperRepString(): string {
    return this.toString();
  }

  toECOOPPaperRepString(): string {
    return this.toString();
  }
}

export class ApplicationLabel implements Label {
  equals(l: Label): boolean {
    return JSON.stringify(this.toJson()) === JSON.stringify(l.toJson());
  }

  constructor(public numArgs: number, public constructorCall: boolean,
              public identifier: number) {}

  toString(): string {
    return `${this.constructorCall ? "new " : ""}(${this.numArgs})[id: ${
        this.identifier}]`;
  }

  toFSEPaperRepString(): string {
    return `${this.constructorCall ? "new " : ""}()`;
  }

  static contains(lab: any): boolean {
    return lab.jsonClass === "ApplicationLabel";
  }

  static fromJson(lab: any): ApplicationLabel {
    assert(ApplicationLabel.contains(lab));
    return new ApplicationLabel(lab.numArgs, lab.constructorCall,
        lab.identifier);
  }

  toJson(): any {
    return {
      jsonClass: "ApplicationLabel", numArgs: this.numArgs,
      constructorCall: this.constructorCall, identifier: this.identifier
    }
  }

  toECOOPPaperRepString(): string {
    return `${this.constructorCall ? "new " : ""}(${this.numArgs})`;
  }
}

export class PropertyLabel implements Label {
  equals(l: Label): boolean {
    return JSON.stringify(this.toJson()) === JSON.stringify(l.toJson());
  }

  constructor(private name: PropertyKey) {}

  toString(): string { return this.name.toString(); }

  /**
   * Returns a representation of the property, i.e., a best effort to restore
   * any Symbol references.
   */
  public getProperty(): symbol|string {
    return Labels.restoreSymbolsRefs(this);
  }

  public getName(): PropertyKey { return this.name; }

  static contains(lab: any): boolean {
    return lab.jsonClass === "PropertyLabel";
  }

  static fromJson(lab: any): PropertyLabel {
    assert(PropertyLabel.contains(lab));
    return new PropertyLabel(lab.name);
  }

  toJson(): any {
    return { jsonClass: "PropertyLabel", name: this.name.toString() }
  }

  toFSEPaperRepString(): string {
    return this.toString();
  }

  toECOOPPaperRepString(): string {
    return this.toString();
  }
}

export const ReceiverLab = new PropertyLabel("this");
export const ProtoLab = new PropertyLabel(Constants.PROTO_SYM);

export class WriteLabel implements Label {
  equals(l: Label): boolean {
    return JSON.stringify(this.toJson()) === JSON.stringify(l.toJson());
  }

  constructor(private name: PropertyKey, private identifier: number) {}

  toString(): string {
    return `write[${this.identifier}](${this.name.toString()})`;
  }

  /**
   * Returns a representation of the property, i.e., a best effort to restore
   * any Symbol references.
   */
  public getProperty(): symbol|string {
    return Labels.restoreSymbolsRefs(this);
  }

  public getName(): PropertyKey { return this.name; }

  static contains(lab: any): boolean { return lab.jsonClass === "WriteLabel"; }

  static fromJson(lab: any): WriteLabel {
    assert(WriteLabel.contains(lab));
    return new WriteLabel(lab.name, lab.identifier);
  }

  toJson(): any {
    return {
      jsonClass: "WriteLabel", name: this.name.toString(),
      identifier: this.identifier
    }
  }

  public equalsModuloIdentifier(other: WriteLabel) {
    return this.name.toString() == other.name.toString();
  }

  public getIdentifier(): number { return this.identifier; }

  toFSEPaperRepString(): string {
    return `⋅${this.name.toString()}→`;
  }

  toECOOPPaperRepString(): string {
    return this.toString();
  }
}

export const ProtoWriteLab = new WriteLabel(Constants.PROTO_SYM, Infinity);

export interface Label {
  toJson(): any
  // Structural equivalence
  equals(l: Label): boolean

  toFSEPaperRepString(): string
  toECOOPPaperRepString(): string
}

export class Labels {
  static fromJson(lab: any): Label {
    if (RequireLabel.contains(lab)) {
      return RequireLabel.fromJson(lab);
    }
    if (ArgLabel.contains(lab)) {
      return ArgLabel.fromJson(lab);
    }
    if (ApplicationLabel.contains(lab)) {
      return ApplicationLabel.fromJson(lab);
    }
    if (PropertyLabel.contains(lab)) {
      return PropertyLabel.fromJson(lab);
    }
    if (AccessLabel.contains(lab)) {
      return AccessLabel.fromJson(lab);
    }
    if (WriteLabel.contains(lab)) {
      return WriteLabel.fromJson(lab);
    }
    throw new Error("Unexpected to be here");
  }

  /**
   * Restore the toString result of a symbol to the original Symbol
   * I.e., the string Symbol(Symbol.toStringTag) becomes the Symbol.toStringTag
   * symbol value
   * @param lab
   */
  static restoreSymbolsRefs(lab: WriteLabel|PropertyLabel): symbol|string {
    const symRe = /Symbol\((.*)\)/;
    let name: symbol|string = lab.getName().toString();
    const symNames = name.match(symRe);
    if (symNames != null && symNames.length === 2) {
      // 0'th element is the full string
      const symName = symNames[1];
      switch (symName) {
        case "Symbol.toPrimitive":
          name = Symbol.toPrimitive;
          break;
        case "Symbol.toStringTag":
          name = Symbol.toStringTag;
          break;
        case "Symbol.iterator":
          name = Symbol.iterator;
          break;
        case "Symbol.split":
          name = Symbol.split;
          break;
        case "Symbol.hasInstance":
          name = Symbol.hasInstance;
          break;
        case "Symbol.match":
          name = Symbol.match;
          break;
        case "Symbol.replace":
          name = Symbol.replace;
          break;
        case "Symbol.search":
          name = Symbol.search;
          break;
        case "Symbol.species":
          name = Symbol.species;
          break;
        case "Symbol.unscopables":
          name = Symbol.unscopables;
          break;
        case "__proto__":
          name = Constants.PROTO_SYM;
          break;
        default:
          name = Symbol(symName);  // Create new symbol for unknown symbols
      }
    }
    return name;
  }
}

/**
 * Utility class for navigating the generated API
 */

export class AccessPath {
  constructor(name: Label, rest: AccessPath|undefined) {
    this.name = name;
    this.next = rest;
  }

  length(): number {
    if (this.next == undefined) {
      return 1;
    } else {
      return this.next.length() + 1;
    }
  }

  hasNext(): boolean { return this.next !== undefined; }

  getNext(): AccessPath { return this.next; }

  getName(): Label { return this.name; }

  static woPrototypes(path: AccessPath): AccessPath {
    if (path.getName().equals(ProtoLab)) {
      return this.woPrototypes(path.getNext());
    } else {
        if (path.hasNext()) {
          return new AccessPath(path.getName(), this.woPrototypes(path.getNext()));
        } else {
          return new AccessPath(path.getName(), undefined);
        }
    }
  }

  toString(fsePaperRep?: boolean, ecoopPaperRep?: boolean, hidePrototypes?: boolean): string {
    let path:AccessPath = this;
    if (hidePrototypes) {
      path = AccessPath.woPrototypes(path);
    }
    if (fsePaperRep) {
      return path.toFSEPaperRepString()
    } else if (ecoopPaperRep) {
      return path.toString_(ecoopPaperRep)
    } else {
      return path.toString_(false)
    }
  }

  toString_(ecoopPaperRep?: boolean): string {
    const nameStr = ecoopPaperRep ?
        this.name.toECOOPPaperRepString() :
        this.name.toString();

    if (isNullOrUndefined(this.next)) {
      return nameStr;
    } else {
      return this.next.toString_(ecoopPaperRep) + Constants.PATH_DELIMITER +
          nameStr;
    }
  }

  toFSEPaperRepString(): string {
    if (isNullOrUndefined(this.next)) {
      return this.name.toFSEPaperRepString();
    } else {
      let next = this.getNext();
      let name;
      if (this.name instanceof ArgLabel) {
        assert(next.name instanceof ApplicationLabel);
        const app = next.name as ApplicationLabel;
        const kappa = app.identifier;
        next = next.getNext();
        name = this.name.toFSEPaperRepString(kappa);
      } else {
        name = this.name.toFSEPaperRepString();
      }
      return next.toFSEPaperRepString() + Constants.PATH_DELIMITER + name;
    }
  }

  private name: Label;
  private next: AccessPath|undefined;

  extendWithLabel(s?: Label) { return new AccessPath(s, this); }

  pop(): AccessPath { return this.next; }

  toJson() {
    let elms = [];
    let cur: AccessPath|undefined = this;
    while (!isNullOrUndefined(cur)) {
      if (elms.length > 0) elms.unshift(1);
      elms[0] = cur.name.toJson();
      cur = cur.next;
    }
    return elms;
  }

  getLast(): RequireLabel {
    let iter: AccessPath = this;
    while (iter.hasNext()) {
      iter = iter.getNext();
    }
    assert(iter.getName() instanceof RequireLabel);
    return (iter.getName() as RequireLabel);
  }

  /**
   * return true if path was created by a client side read
   */
  isCovariant(): boolean {
    if (this.hasNext()) {
      return this.name instanceof ArgLabel ? !this.getNext().isCovariant()
          : this.getNext().isCovariant();
    }
    return !(this.name instanceof ArgLabel);
  }

  diff(other: AccessPath): number {
    if (this.length() >= other.length()) {
      return this._diff(other)
    } else {
      return other._diff(this);
    }
  }

  /**
   * must be called on the path which is longest
   * Returns the difference in the number of labels between this and other
   * @param other
   * @private
   */
  private _diff(other: AccessPath): number {
    const thisLabs = this.getLabels().reverse();
    const otherLabs = other.getLabels().reverse();

    for (let i = 0; i < otherLabs.length; i++) {
      if (!otherLabs[i].equals(thisLabs[i])) {
        return -1
      }
    }
    return thisLabs.length - otherLabs.length;
    // if (other.getName().equals(this.getName()))  {
    //  if (other.hasNext()) {
    //    return this.getNext()._diff(other.getNext());
    //  } else {
    //    return this.length()-1;
    //  }
    //} else {
    //  return -1;
    //}
  }

  /**
   *
   */
  getLabels(): Label[] {
    if (this.hasNext()) {
      return [ this.name ].concat(this.getNext().getLabels());
    } else {
      return [ this.name ];
    }
  }

  /**
   * Should be included in path coverage
   * We include pathStrs that are covariant since these are the pathStrs where
   * NoRegretsPlus is responsible for the covearge For contravariant pathStrs, it's
   * the library that is responsible for covering the path, which the library is
   * not required to do. For example, if a library stops reading a property on
   * an argument. Similarly, when a path in covariant, but has 2 or more
   * argument labels, then part of the coverage comes from the library calling a
   * client callback, which again, the library is allowed not to do so we cannot
   * count these pathStrs in the coverage.
   *
   * We also discount receiver pathStrs (pathStrs with this) since they are only used
   * to synthesize/retrieve receivers Write pathStrs are also not included at the
   * moment since it's not currently counted as a type regression not to perform
   * a write.
   */
  shouldIncludeInPathCoverarge(): boolean {
    return this.isCovariant() &&
        this.getLabels().filter((lab) => lab instanceof ArgLabel).length <
        2 &&
        !this.isWritePath() && !this.isReceiverPath();
  }

  isReceiverPath(): boolean {
    return this.getLabels().some(lab => lab.equals(ReceiverLab));
  }

  static fromJson(l: any[]): AccessPath {
    let root = new AccessPath(Labels.fromJson(l[l.length - 1]), undefined);

    let cur = root;
    for (let k = l.length - 2; k >= 0; k--) {
      cur.next = new AccessPath(Labels.fromJson(l[k]), undefined);
      cur = cur.next;
    }
    return root;
  }

  isWritePath(): boolean {
    return this.getLabels().some(lab => lab instanceof WriteLabel);
  }
}
