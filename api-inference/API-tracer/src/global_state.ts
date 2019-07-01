import * as winston from "winston";

import {Config} from "./config";
import {TracerObservationState} from "./info";
import {LearnHandler} from "./learn-handler";
import {MochaTest} from "./mochaTest";
import {cloneGlobalObjects} from "./runtime-hacks";
import {TypeVariable} from "./types";

export class GlobalState {
  static tracerObservationState: TracerObservationState =
      new TracerObservationState();
  static config: Config|undefined;

  static checkMode: boolean = false;
  static isSleeping: boolean = false;
  static BE_GENTLE: boolean = true;
  static observeHasWhenOwnKeys: boolean = true;
  static initialGlobalObjects: Map<object, object> = new Map();
  static globalObjectsWhiteList: Map<Object, Object>;
  /**
   * Set to true to discover type regressions that might due to
   * objects with null prototypes.
   */
  static withObjectPrototypeAsKnownValue: boolean = false;
  static withProtoProxy: boolean = true;
  static useKnownObjectInstances: boolean = false;

  // Test Distillation mode options
  static collectPrimitiveValues: boolean = false;
  static orderedObservations: boolean = false;

  // Enable to use the access abstraction in the models
  static useAccessActions: boolean = false;

  static currentTest: MochaTest = undefined;

  //static sleep(): void {
  //  GlobalState.isSleeping = true;
  //  winston.info(`Info full dump:`);
  //  winston.info(GlobalState.tracerObservationState.toJson(undefined));
  //}

  static unsleep(): void { GlobalState.isSleeping = false; }

  static init(c: Config|undefined = undefined): void {
    GlobalState.config = c;
    GlobalState.initialGlobalObjects = cloneGlobalObjects();
    GlobalState.globalObjectsWhiteList = GlobalState.initialGlobalObjects;

    // Run in observation mode
    this.reset();
  }

  static reset(): void {
    if (this.config.testDistillationMode) {
      this.collectPrimitiveValues = true;
      this.orderedObservations = true;
    }

    GlobalState.checkMode = false;
    GlobalState.isSleeping = false;
    GlobalState.tracerObservationState.infos = {};
    GlobalState.tracerObservationState.createTime =
        TracerObservationState.DateRef.now();
    GlobalState.tracerObservationState.types = new WeakMap<any, TypeVariable>();
    GlobalState.tracerObservationState.handler =
        new LearnHandler(GlobalState.tracerObservationState);
    GlobalState.tracerObservationState.observations = new Map();
    GlobalState.tracerObservationState.unifications.clear();
  }
}