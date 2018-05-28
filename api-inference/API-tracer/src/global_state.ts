import {Infos} from "./info";
import * as winston from "winston";
import {TypeVariable} from "./types";
import {LearnHandler} from "./learn-handler";
import {MochaTest} from "./mochaTest";
import {Config} from "./config";

export class GlobalState {
    static info: Infos = new Infos();
    static config: Config | undefined;

    static checkMode: boolean = false;
    static isSleeping: boolean = false;
    static BE_GENTLE: boolean = true;
    static observeHasWhenOwnKeys: boolean = false;
    /**
     * Set to true to discover type regressions that might due to
     * objects with null prototypes.
     */
    static withObjectPrototypeAsKnownValue: boolean = false;
    static withProtoProxy: boolean = false;
    static useKnownObjectInstances: boolean = false;

    static currentTest: MochaTest = undefined;

    static sleep(): void {
        GlobalState.isSleeping = true;
        winston.info(`Info full dump:`);
        winston.info(GlobalState.info.toJson(undefined));
    }

    static unsleep(): void {
        GlobalState.isSleeping = false;
    }


    static init(c: Config | undefined = undefined): void {
        GlobalState.config = c;

        //Run in observation mode
        this.reset();
    }

    static reset(): void {
        GlobalState.checkMode = false;
        GlobalState.isSleeping = false;
        GlobalState.info.infos = {};
        GlobalState.info.types = new WeakMap<any, TypeVariable>();
        GlobalState.info.handler = new LearnHandler(GlobalState.info);
        GlobalState.info.observations = [];
        GlobalState.info.unifications.clear();
    }
}