import * as assert from "assert";
import {isNullOrUndefined} from "util";
import {Constants} from "./constants";

export class RequireLabel implements Label {
    constructor(public name: string) {
    }

    toString(): string {
        return `require(${this.name})`;
    }

    static contains(lab: any): boolean {
        return lab.jsonClass === "RequireLabel";
    }

    static fromJson(lab: any): RequireLabel {
        assert(RequireLabel.contains(lab));
        return new RequireLabel(lab.name);
    }

    toJson(): any {
        return {jsonClass: "RequireLabel", name: this.name}
    }

}

export class ArgLabel implements Label {
    constructor(public idx: number) {
    }

    toString(): string {
        return "arg" + this.idx;
    }


    static contains(lab: any): boolean {
        return lab.jsonClass === "ArgLabel";
    }

    static fromJson(lab: any): ArgLabel {
        assert(ArgLabel.contains(lab));
        return new ArgLabel(lab.idx);
    }

    toJson(): any {
        return {jsonClass: "ArgLabel", idx: this.idx}
    }
}

export class AccessLabel implements Label {
    constructor() {
    }

    toString(): string {
        return "AccessLabel";
    }


    static contains(lab: any): boolean {
        return lab.jsonClass === "AccessLabel";
    }

    static fromJson(lab: any): AccessLabel {
        assert(AccessLabel.contains(lab));
        return new AccessLabel();
    }

    toJson(): any {
        return {jsonClass: "AccessLabel"}
    }
}

export class ApplicationLabel implements Label {
    constructor(public numArgs: number, public constructorCall: boolean) {}

    toString(): string {
        return (this.constructorCall ? "new" : "") + "(" + this.numArgs + ")";
    }

    static contains(lab: any): boolean {
        return lab.jsonClass === "ApplicationLabel";
    }

    static fromJson(lab: any): ApplicationLabel {
        assert(ApplicationLabel.contains(lab));
        return new ApplicationLabel(lab.numArgs, lab.constructorCall);
    }

    toJson(): any {
        return {
            jsonClass: "ApplicationLabel",
            numArgs: this.numArgs,
            constructorCall: this.constructorCall
        }
    }
}


export class PropertyLabel implements Label {
    constructor(public name: PropertyKey) {}

    toString(): string {
        return this.name.toString();
    }

    static contains(lab: any): boolean {
        return lab.jsonClass === "PropertyLabel";
    }

    static fromJson(lab: any): PropertyLabel {
        assert(PropertyLabel.contains(lab));
        return new PropertyLabel(lab.name);
    }

    toJson(): any {
        return {jsonClass: "PropertyLabel", name: this.name.toString()}
    }
}

export interface Label {
    toJson(): any
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
        throw new Error("Unexpected to be here");
    }
}

/**
 * Utility class for navigating the generated API
 */

export class AccessPath {

    constructor (name: Label, rest: AccessPath | undefined) {
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

    hasNext(): boolean {
        return this.next !== undefined;
    }

    getNext(): AccessPath {
        return this.next;
    }

    getName(): Label {
        return this.name;
    }

    toString(): string {

        let base = "";
        if(!isNullOrUndefined(this.next)) {
            base = this.next.toString();
        }
        return base + Constants.PATH_DELIMITER + this.name.toString();
    }

    private name: Label;
    private next: AccessPath | undefined;

    extendWithLabel(s?: Label) {
        return new AccessPath(s, this);
    }

    pop(): AccessPath {
        return this.next;
    }

    toJson() {
        let elms = [];
        let cur: AccessPath | undefined = this;
        while (!isNullOrUndefined(cur)) {
            if(elms.length > 0) elms.unshift(1);
            elms[0] = cur.name.toJson();
            cur = cur.next
        }
        return elms;
    }

    static fromJson(l: any[]): AccessPath {
        let root = new AccessPath(Labels.fromJson(l[l.length - 1]), undefined);

        let cur = root;
        for(let k = l.length - 2; k >= 0; k--) {
            cur.next = new AccessPath(Labels.fromJson(l[k]), undefined);
            cur = cur.next;
        }
        return root;
    }

}
