import {AccessPath} from "./paths";
import {RuntimeType, UnionType} from "./runtime-types";
import {IDictionary} from "./common-utils";
import {isNullOrUndefined} from "util";

export class Observation {

    constructor(
        public type: UnionType,
        public path: AccessPath,
        public propertyTypes: IDictionary<RuntimeType> | undefined) {

    }

    stack: string;

    toString(): string {
        return `${this.path} -> ${this.type}`;
    }

    static fromJson(i): Observation {
        let path = AccessPath.fromJson(i.path);
        let type = i.type;
        let obs = new Observation(type, path, i.propertyTypes);
        obs.stack = i.stack;
        if(!isNullOrUndefined(obs.propertyTypes)) {
            for(let p of Object.keys(obs.propertyTypes)) {
                obs.propertyTypes[p] = obs.propertyTypes[p];
            }
        }
        return obs;
    }

    toJson(): any {
        let pt = {};
        if(!isNullOrUndefined(this.propertyTypes)) {
            for (let k of Object.keys(this.propertyTypes)) {
                pt[k] = this.propertyTypes[k].toString();
            }
        }
        return {
            type : this.type.toString(),
            path : this.path.toJson(),
            stack: this.stack,
            propertyTypes: pt
        };
    }

    addStack(stack: string) {
        this.stack = stack;
    }

    fullId(): string {
        let propStr = "";
        if(!isNullOrUndefined(this.propertyTypes)) {
            let sortedProperties = Object.keys(this.propertyTypes).sort((a, b) => a.localeCompare(b));
            for(let prop of sortedProperties) {
                propStr += prop + ":" + this.propertyTypes[prop].toString();
            }
        }
        return this.path.toString() + ":" + this.type.toString() + propStr;
    }
}
