import {
  AccessLabel,
  AccessPath,
  Label,
  PropertyLabel,
  ProtoLab,
  ProtoWriteLab,
  ReceiverLab,
  WriteLabel
} from "../../API-tracer/src/paths";
import _ = require('lodash');
import {TestReport, TypeRegression} from "./testResult";
import {Constants} from "../../API-tracer/src/constants";
import {
  getBuiltInProtoCount,
  RuntimeType,
  UnionType
} from "../../API-tracer/src/runtime-types";
import {KnownValuesNode} from "../../API-tracer/src/known-values";
import {LearnHandler} from "../../API-tracer/src/learn-handler";
import {ModelValueTest, TestRunner} from "./testRunner";
import {GlobalState} from "../../API-tracer/src/global_state";
import {hasInChain} from "../../API-tracer/src/common-utils";
import * as assert from "assert";
import {none, Option, some} from "ts-option";
import {getProtoWithProp} from "../../API-tracer/src/JSAux";
import {
  IgnoreUnionType,
  NonExistingUnionType
} from "../../API-tracer/src/runtime-types-operations";
import {ProxyOperations} from "../../API-tracer/src/ProxyOperations";
import {ModelValue} from "./modelValue";
import {TypeChecker} from "./checker";
import {AssertionError} from "assert";
import {TypeInfer} from "./typeInfer";

/**
 * Stack mechanism used to avoid activating proxy traps within other proxy
 * traps.
 */
let DISABLED = [ false ];
function cannotObserve() { return DISABLED[DISABLED.length - 1] }
function disableObservations(f) {
  DISABLED.push(true);
  try {
    return f();
  } finally {
    DISABLED.pop();
  }
}
export function enableObservations(f) {
  DISABLED.push(false);
  try {
    return f();
  } finally {
    DISABLED.pop();
  }
}

/**
 * The proxy handler used to check for type-regressions on library method
 * arguments. For example, detect if a library function suddenly expects more on
 * one of its arguments.
 *
 * An import feature of the ArgumentProxyHandler is that it lazily synthesizes
 * the properties of the target object. The reason for this is demonstrated in
 * the lazyArgumentSynthesis test. In short, if the synthesis of the `p`
 * property on argument `a`, may depend on some `a.q = ...` write that the
 * library method performs in its body. So, if we try to eagerly synthesize all
 * properties when the proxy is created, then we will get stuck.
 */
export class ArgumentProxyHandler implements ProxyHandler<{}> {
  protected static winston = require("winston");
  public path: AccessPath;
  private hasSynthesized: Set<string|symbol> = new Set();
  private hasWritten: Set<string|symbol> = new Set();
  private inheritsPrototypeChecking = false;
  constructor(public modelValue: ModelValue, private report: TestReport,
              private testRunner: TestRunner) {
    this.path = this.modelValue.path;
  }

  // For a given property, this map points to the identifier value of the latest
  // writeLab used in the write of this property.
  private latestWriteMap: Map<string, number> = new Map();

  public static createArgumentProxy(modelValue: ModelValue, argument: any,
                                    report: TestReport,
                                    testRunner: TestRunner) {
    const argProxyHandler =
        new ArgumentProxyHandler(modelValue, report, testRunner);
    const argProxy = new Proxy(argument, argProxyHandler);

    // Avoid initializing the prototypes for exceptions
    if (!modelValue.type.getTypes().includes("throws")) {
      argProxyHandler.initProtoChain(argument, none);
    }
    return argProxy;
  }

  /**
   * Synthesize the objects in the prototype chain
   * @param target
   * @param inheritingObject: The modelValue of the object below the object for
   * which we are currently synthesizing a prototype:
   * Chain: inheritingObject -> target -> currently synthesizing prototype
   */
  public initProtoChain(target: {}, inheritingObject: Option<ModelValue>) {
    const protoLab = Array.from(this.modelValue.getPropertyKeys())
                         .find(p => p.equals(ProtoLab));

    const protoTypeTypes = this.modelValue.type.getTypes();
    let t: RuntimeType;
    if (protoTypeTypes.length > 1) {
      // Multiple prototypes indicates that the prototype was
      // overwritten at some point in the client's tests.
      // We try to fetch the prototype that existed before the write since this
      // prototype is the one that is recorded in the model (We always record
      // the prototypes when an object is first proxified).

      assert(
          inheritingObject.isDefined,
          "Synthesize prototype for union-typed object requires access to the inheriting object");
      const protoWriteLab =
          inheritingObject.get.getLabelsOfType(WriteLabel)
              .find(p => p.equalsModuloIdentifier(ProtoWriteLab));
      assert(
          protoWriteLab,
          "Unexpectedly found union-typed protowrite without write on inheriting object");
      const protoWriteMVal =
          inheritingObject.get.getPropertyModel(protoWriteLab);
      const nonWrittenTypes = protoTypeTypes.filter(
          t => !protoWriteMVal.type.getTypes().includes(t));
      assert(
          nonWrittenTypes.length === 1,
          "Unexpectedly found multiple non-write types on union typed prototype");
      t = nonWrittenTypes[0];
    } else {
      t = protoTypeTypes[0];
    }

    let stdProtoCount: Option<number>|number = getBuiltInProtoCount(t);
    assert(stdProtoCount.isDefined,
           `Could not fetch standard proto count for type ${t}`);
    stdProtoCount = stdProtoCount.get;
    const numProtos = this.modelValue.numProtos();
    if (numProtos > stdProtoCount) {
      // There are more prototypes than what is standard for the type `t`,
      // so we continue the synthesizing.
      const protoMVal = this.modelValue.getPropertyModel(protoLab);
      const proxyProtoHandler =
          new ArgumentProxyHandler(protoMVal, this.report, this.testRunner);
      const synthProto = this.testRunner.synthesize(protoMVal, this.report);
      const proxyProto = new Proxy(synthProto, proxyProtoHandler);
      Object.setPrototypeOf(target, proxyProto);
      this.hasSynthesized.add(ProtoLab.getProperty());
      proxyProtoHandler.initProtoChain(synthProto, some(this.modelValue));
    } else {
      // The remaining prototypes are built-in/standard protos for the given
      // type. If it is a known value, then we haven't made any observations for
      // the prototypes
      assert(
          this.modelValue.isKnownValue ||
              (numProtos == stdProtoCount ||
               t === 'object'),  // We include the t equal to 'object' check
                                 // since we do not distinguish objects created
                                 // as literals, objects without prototypes and
                                 // the Object.prototype at the moment. A
                                 // potential improvement is to have a separate
                                 // type of some form for each of these.
          `ModelValue type has fewer prototypes than what is standard for its type`);
      this.inheritsPrototypeChecking = true;
    }
  }

  get(target: {}, p: PropertyKey, receiver: any): any {
    if (p === Constants.IS_PROXY_KEY) {
      return true;
    }
    if (p === Constants.GET_PROXY_TARGET) {
      return target;
    }
    if (p === Constants.GET_IS_NATIVE) {
      return this.modelValue.isNative;
    }
    if (p === Constants.GET_HANDLER) {
      return this;
    }

    if (cannotObserve()) {
      return Reflect.get(target, p, receiver);
    }
    return disableObservations(() => {
      // If, we dispatch a read to a proxified prototype, then the target object
      // is first set to the proxy of that prototype. So to avoid having the get
      // handler run twice on the same object, we have to manually remove the
      // proxy if it exists. This prevents the duplication issue of type
      // regressions that we had in the beginning.
      let targetUnProxified = ProxyOperations.isProxy(target)
                                  ? ProxyOperations.getTarget(target)
                                  : target;

      let lab: PropertyLabel|AccessLabel = new PropertyLabel(p);
      if (LearnHandler.shouldUseAccessAction(targetUnProxified, p) &&
          !this.modelValue.hasLabel(lab)) {
        // Do only use the accessLabel if the property does not exist directly
        // as a numeric property
        lab = new AccessLabel();
      }

      const propPath = this.path.extendWithLabel(lab);
      const pathStr = propPath.toString();
      let mValWithProp: Option<ModelValue> = some(this.modelValue);

      if (lab instanceof PropertyLabel && this.modelValue.hasLabel(lab) &&
          !this.hasSynthesized.has(lab.getProperty())
          // The reason why we call isMissingWrite here is demonstrated by the
          // test case NonExistProtoVOPCombi. In short, the non-exist is likely
          // on the prototype if a write is missing, so if try to perform the
          // read on this object, then we will try to extract a VOP value not
          // yet available
          && !this.isMissingWrite(lab)) {
        this.synthesizeProperty(lab, targetUnProxified);
      }

      // If prototypes are non-proxies, i.e., standard prototypes, then inherit
      // their checking
      if (this.inheritsPrototypeChecking) {
        if (!Reflect.has(targetUnProxified, p)) {
          let protoWithPropCountPair: Option<[ ModelValue, number ]> =
              this.modelValue.getPropertyInChainOfPrototype(lab);
          if (protoWithPropCountPair.isDefined) {
            const propMVal = protoWithPropCountPair.get[0];
            if (propMVal.type.equals(NonExistingUnionType)) {
              // If is NON-EXISTING on non-proxified prototype
              return undefined;
            }
            this.synthesizeProperty(lab, targetUnProxified,
                                    protoWithPropCountPair.get[1]);
          } else {
            // No element in the prototype chain has the property in the model.
            // and the Reflect.has check failed.
            // So most likely a type regression will be reported after the read
          }
        } else {
          // The Reflect.has check succeeded, so we fetch the ModelValue
          // corresponding to the prototype that has the property, and then we
          // perform the type-check on that instead.
          const realTargetDesc = getProtoWithProp(targetUnProxified, p);
          assert(
              realTargetDesc.isDefined,
              "Shouldn't happen when the previous Reflect.has check succeeded");
          try {
            mValWithProp =
                some(this.modelValue.getNthPrototype(realTargetDesc.get[1]));
          } catch (e) {
            if (e instanceof AssertionError) {
              // Reaching this branch means that the model value doesn't have as
              // many prototypes as we need to follow to get to the model value
              // that has the property 'p'. The only sensible thing we can do at
              // this point is to report an error, indicating that the structure
              // of target doesn't match the structure of the model value of
              // target.
              mValWithProp = none;
              this.report.addTypeRegression(new TypeRegression(
                  this.modelValue.path,
                  `Expected ${p.toString()} on the ${
                      realTargetDesc[0].get} prototype`,
                  `Found only ${
                      this.modelValue.numProtos()} prototypes on value`))
            }
          }
        }
      }

      // For some reason the __proto__ lookup is dispatched to the prototype,
      // even though
      // the targetUnProxified has a prototype, so we must handle in manually.
      if (p.toString() == '__proto__') {
        return Object.getPrototypeOf(targetUnProxified);
      }

      const readVal = enableObservations(
          () => { return Reflect.get(targetUnProxified, p, receiver); });

      // If it has a proxy proto,
      // then let the proxy do the type checking
      if (mValWithProp.isDefined &&  // If mValWithProp is none then we cannot
                                     // perform the type check, but we have
                                     // already reported another regression.
          !this.hasProxyProto(
              targetUnProxified) &&  // We delegate the type checking to the
                                     // proto if it's also a proxy
          !mValWithProp.get.hasLabel(lab) &&
          !this.isWhiteListed(lab, readVal)) {
        const obsType = new UnionType();
        obsType.addType(TypeChecker.extractType(readVal));
        const errorPath = mValWithProp.get.path.extendWithLabel(lab);
        this.report.ignorePath(errorPath);
        this.report.addTypeRegression(new TypeRegression(
            errorPath, Constants.CIRC_TYPE, obsType.toTypeString()));
      } else {
        // We only add the value to the VOP map if no type errors are found.
        const isVop = this.testRunner.isVop(pathStr);
        if (isVop) {
          this.testRunner.addVopValue(pathStr, readVal);
        }
      }
      return readVal;
    });
  }

  set(target: {}, p: PropertyKey, value: any, receiver: any): boolean {
    if (cannotObserve()) {
      return Reflect.set(target, p, value, receiver);
    }
    return disableObservations(() => {
      // Do not enableObservations here since it will trigger calls of
      // getOwnPropertyDescriptor, which will result in spurious synthesises.
      // Notice, this may result in loss of coverage of setters.
      target[p] = value;

      let nextWriteLab: WriteLabel;
      const pWrites = this.modelValue.getLabelsOfType(WriteLabel)
                          .filter((wLab) => wLab.getProperty() === p);

      if (pWrites.length === 0) {
        // Writing to a previous unseen property results in a type-regression.
        // Whether this should result in type regression or not is still a
        // little unclear. Technically, it's equivalent to strengthening a
        // return value, which is an ok thing to do. However, the write may
        // overwrite existing properties, or a name conflict may arise later due
        // to this write.
        const custWritePath =
            this.modelValue.path.extendWithLabel(new WriteLabel(p, -1));
        this.report.addTypeRegression(
            new TypeRegression(custWritePath, Constants.CIRC_TYPE,
                               TypeChecker.extractType(value)));
        this.report.ignorePath(custWritePath);
      } else {
        // What we do at the moment is to just pick the write model values
        // in-order, e.g, if p was written three times by the library when the
        // model was generated and the three writes were assigned ids 1,2 and 3,
        // then the first time the library writes p in the NoRegretsPlus phase we
        // pick the modelValue with id 1, the next time the one with id 2 etc.
        // Notice, this strategy only works as long as we don't try to
        // compress/merge writes. Also, it's a bit uncleary what should be done
        // if the property is written more times than we have model values.

        const lastWrite = this.latestWriteMap.get(p.toString());
        if (lastWrite) {
          nextWriteLab =
              _.minBy(pWrites, (wLab) => wLab.getIdentifier() <= lastWrite
                                             ? Infinity
                                             : wLab.getIdentifier());
          if (!nextWriteLab) {
            // If the library writes to the property more times than we
            // expected, then we just pick the model of the last write. This is
            // maybe a bit unsound. Instead we should report it as a loss of
            // soundness, and then try to infer the best model based on the type
            // of the written value.
            nextWriteLab = _.maxBy(pWrites, (wLab) => wLab.getIdentifier());
          }
        } else {
          nextWriteLab = _.minBy(pWrites, (wLab) => wLab.getIdentifier());
        }
        this.latestWriteMap.set(p.toString(), nextWriteLab.getIdentifier());
        const model = this.modelValue.getPropertyModel(nextWriteLab);

        this.hasWritten.add(nextWriteLab.getProperty());
        this.testRunner.testModelValue(model, value, none, this.report, false)
      }

      return true;
    });
  }

  has(target: {}, p: PropertyKey): boolean {
    if (cannotObserve()) {
      return Reflect.has(target, p);
    }
    return disableObservations(() => {
      const lab = LearnHandler.shouldUseAccessAction(target, p)
                      ? new AccessLabel()
                      : new PropertyLabel(p);
      return this.doPropExistsOnObject(lab, target);
    });
  }

  getOwnPropertyDescriptor(target: {}, prop: PropertyKey): PropertyDescriptor {
    if (prop === Constants.IS_PROXY_KEY) {
      return {
        configurable : true,
        enumerable : false,
        value : true,
        writable : false
      };
    }

    if (cannotObserve()) {
      return Reflect.getOwnPropertyDescriptor(target, prop);
    }
    return disableObservations(() => {
      const lab = new PropertyLabel(prop);
      if (this.modelValue.hasLabel(lab) &&
          !this.hasSynthesized.has(lab.getProperty()) &&
          !this.isMissingWrite(lab) && !Reflect.has(target, prop)) {
        this.hasSynthesized.add(lab.getProperty());
        return {
          configurable: true, enumerable: true,
              value: this.synthesizeProperty(lab, target), writable: true
        }
      }
      // If lab is a missing write, then we use Reflect.getOwnPropertyDescriptor
      // since synthesizing a descriptor is wrong in that case
      return Reflect.getOwnPropertyDescriptor(target, prop);
    })
  }

  ownKeys(target: {}): PropertyKey[] {
    if (cannotObserve()) {
      return Reflect.ownKeys(target);
    }
    return disableObservations(() => {
      const targetKeys = Reflect.ownKeys(target);
      const lazySynthKeys =
          this.modelValue.getLabelsOfType(PropertyLabel)
              .filter(lab => !targetKeys.includes(lab.getProperty()) &&
                             !this.isMissingWrite(lab) &&
                             !lab.equals(ProtoLab) &&
                             !lab.equals(ReceiverLab) &&
                             !this.modelValue.getPropertyModelByEquality(lab)
                                  .type.equals(NonExistingUnionType))
              .map(lab => lab.getProperty());
      const keys = targetKeys.concat(lazySynthKeys);
      const labs = Array.from(this.modelValue.getPropertyKeys());

      // It important that we preserve the order of the keys in the array. Some
      // clients will produce type regressions if the order is invalidated.
      keys.sort((k1, k2) => {
        let idK1 = -1;
        let idK2 = -2;
        const labk1 = LearnHandler.makeLabel(target, k1);
        const labk2 = LearnHandler.makeLabel(target, k2);

        const labLookup =
            (l: Label) => { return labs.find(mValLab => mValLab.equals(l)) };

        const mValLab1 = labLookup(labk1);
        const mValLab2 = labLookup(labk2);

        if (mValLab1 != null) {
          idK1 = this.modelValue.getPropertyModel(mValLab1).id;
        }
        if (mValLab2 != null) {
          idK2 = this.modelValue.getPropertyModel(mValLab2).id;
        }
        return idK1 - idK2;
      });
      return keys;
    });
  }

  /**
   * Synthesizes the property corresponding to
   * this.modelValue.walkProtoChain(protoCount).lab on the
   * target.walkProtoChain(protoCount).lab object.
   * @param lab
   * @param target
   * @param protoCount
   */
  private synthesizeProperty(lab: PropertyLabel|AccessLabel, target: any,
                             protoCount?: number) {
    let mVal = this.modelValue;
    if (protoCount) {
      mVal = mVal.getNthPrototype(protoCount);
    }

    if (lab instanceof PropertyLabel) {
      const propMVal = mVal.getPropertyModelByEquality(lab);
      this.hasSynthesized.add(lab.getProperty());
      if (lab.getProperty() === 'length' && GlobalState.useAccessActions &&
          this.modelValue.type.contains("Array")) {
        // Ignore since it's handled when the type is synthesized
        return;
      }
      if (propMVal.isKnownValue) {
        if (hasInChain(target, lab.getProperty())) {
          return;
          //    throw new UnSynthesizableException("Unexpectedly needs to
          //    synthesize known value: WHAT DO WE DO HERE?");
        }
        return;
      }
      if (propMVal.type.equals(NonExistingUnionType)) {
        // Return immediately (we shouldn't create this property)
        return;
      }
      if (propMVal.type.equals(IgnoreUnionType)) {
        // Nothing we can do here (the property is likely a length property of a
        // function,
        // which is non-writable and therefore cannot be assigned).
        // See the IGNORE test-runner-tests.
        return;
      }

      let synthTarget = target;
      while (protoCount > 0) {
        synthTarget = Object.getPrototypeOf(synthTarget);
        assert(synthTarget, `Expected ${protoCount} prototypes on target`);
        protoCount--;
      }

      const prop = lab.getProperty();
      const desc = Object.getOwnPropertyDescriptor(synthTarget, prop);

      // Check if writable
      if (desc == null || (desc != null && desc.writable)) {
        if (propMVal.didThrow) {
          Object.defineProperty(synthTarget, prop, {
            get : () => {
              throw this.testRunner.synthesize(propMVal, this.report)
            }
          });
        } else {
          synthTarget[prop] = this.testRunner.synthesize(propMVal, this.report);
        }
      } else {
        ArgumentProxyHandler.winston.warn(
            `Skipping write of non-writable property ${
                prop.toString()} on synthesis of ${this.modelValue.path}`);
      }
    }
  }

  /**
   * Returns true if the property exists on the target object, or true if the
   * object will be synthesized at next read
   * @param lab
   * @param target
   */
  private doPropExistsOnObject(lab: Label, target: {}) {
    if (lab instanceof PropertyLabel) {
      if (Reflect.has(target, lab.getProperty())) {
        return true;
      }
      return this.modelValue.hasLabel(lab) && !this.isMissingWrite(lab) &&
             !this.modelValue.getPropertyModelByEquality(lab).type.equals(
                 NonExistingUnionType)
    }
    if (lab instanceof AccessLabel) {
      return this.modelValue.hasLabel(lab);
    }
    return false;
  }

  private static whitelistedProperties = [
    new PropertyLabel('toString'), new PropertyLabel('Symbol(toString)'),
    new PropertyLabel('Symbol(Symbol.toStringTag)'), new PropertyLabel('exec'),
    new PropertyLabel('Symbol(exec)'), new PropertyLabel('Symbol(Symbol.exec)')
  ];

  private static typeDependentWhitelist = {
    "Date" : [ new PropertyLabel("valueOf") ],
    "function": [ new PropertyLabel(
        "length") ]  // non-writable always existing property
  };

  private isWhiteListed(actionLabel: Label, readVal: any): boolean {
    // We disregard observations of some properties in the NoRegrets phase (see
    // runtime-hacks.ts). So we should also disregard reads of these properties
    // in NoRegretsPlus.
    return _.some(ArgumentProxyHandler.whitelistedProperties,
                  whiteLab => whiteLab.equals(actionLabel)) ||
           KnownValuesNode.isKnown(readVal) ||
           this.modelValue.type.getTypes().some(
               t => ArgumentProxyHandler.typeDependentWhitelist[t] !==
                        undefined &&
                    ArgumentProxyHandler.typeDependentWhitelist[t].some(
                        pWhite => actionLabel.equals(pWhite)));
  }

  /**
   * Returns true if the model indicates that the library should write `lab` at
   * some point in the future.
   *
   * This method is useful to, for example, avoid synthesizing properties that
   * were actually written to the argument by the library.
   * @param lab
   */
  private isMissingWrite(lab: PropertyLabel): boolean {
    const propMVal = this.modelValue.getPropertyModelByEquality(lab);
    const correspondingWriteLabs =
        this.modelValue.getLabelsOfType(WriteLabel)
            .filter(wLab => wLab.getProperty() === lab.getProperty());

    const firstWrite =
        _.minBy(correspondingWriteLabs, lab => lab.getIdentifier());
    if (firstWrite) {
      const writeMVal = this.modelValue.getPropertyModel(firstWrite);
      // If read happened later than the write and the write hasn't occurred yet
      // Note, the fact that the read happened later than the write means that
      // the actual read, which the check should be performed on, is meant to be
      // delegated to a proxy of target.
      return propMVal.id > writeMVal.id &&
             !this.hasWritten.has(firstWrite.getProperty());
    }
    return false;
  }

  private hasProxyProto(target: {}): boolean {
    const proto = Object.getPrototypeOf(target);
    return !!proto &&
           !!enableObservations(() => Reflect.getOwnPropertyDescriptor(
                                    proto, Constants.IS_PROXY_KEY));
  }
}