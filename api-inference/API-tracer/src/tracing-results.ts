import {Observation} from "./observations";

export abstract class TracingResult {
    jsonClass: "LearningFailure" | "Learned";

    constructor(klass) {
        this.jsonClass = klass;
    }

    static fromJson(i): TracingResult {
        if(i.jsonClass === "Learned") {
            return Learned.fromJson(i)
        }
        else if(i.jsonClass === "LearningFailure") {
            return LearningFailure.fromJson(i)
        }
        throw new Error("Unexpected " + i.jsonClass + " from ")
    }

    abstract toString(): string;
}

export class Learned extends TracingResult {

    observations: Observation[];
    unifications: any;

    constructor () {
        super("Learned");
        this.observations = [];
    }

    static fromJson(i): Learned {
        let l = new Learned();
        for (let obs of i.observations) {
            l.observations.push(Observation.fromJson(obs));
        }
        return l;
    }

    toString(): string {
        return "Learned";
    }
}

export class LearningFailure extends TracingResult {

    observations: Observation[];
    unifications: any;

    constructor (msg: string) {
        super("LearningFailure");
        this.msg = msg;
        this.observations = [];
    }

    static fromJson(i): LearningFailure {
        let l = new LearningFailure(i.msg);
        for (let obs of i.observations) {
            l.observations.push(Observation.fromJson(obs));
        }
        return l;
    }

    toString(): string {
        return "LearningFailure";
    }

    msg: string
}
