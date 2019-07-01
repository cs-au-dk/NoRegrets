import {AccessPath, Label} from "./paths";
import {NoRegretsProxyHandler} from "./regression-runtime-checker";
import {option, Option, some, none} from "ts-option";
import {SideEffects} from "./observations";

export interface ProxyEventHandler {

    handleHas(
        proxyHandler: NoRegretsProxyHandler,
        property: PropertyKey,
        has: boolean,
        valueOriginPath: Option<AccessPath>
    )

    handleWrite(
        writtenValue: any,
        writeToPath: AccessPath,
        valueOriginPath: Option<AccessPath>,
        isKnown: boolean
    )

    handleRead(
        targetPath: AccessPath,
        originalLookedUpValue: any,
        readPath: AccessPath,
        valueOriginPath: Option<AccessPath>,
        isKnown: boolean,
        didThrow: boolean
    )

    handleArgumentRead(
      funcPath: AccessPath,
      argument: any,
      argumentOriginPath: Option<AccessPath>,
      argPath: AccessPath,
      isKnown: boolean
    )


    handleCall(
        argArray: any[],
        functionResult: any,
        didThrow: boolean,
        callPath: AccessPath,
        valueOriginPath: Option<AccessPath>,
        isKnown: boolean
    )

    handleGetPrototype(targetPath: AccessPath,
                       originalLookedUpValue: any,
                       readLookedUpPath: AccessPath,
                       isKnown: boolean
    )

    forceReadPrototypes(initPath: AccessPath, target: {})

    makeAccessPath(issuer: NoRegretsProxyHandler, p: PropertyKey, protoCount: number): AccessPath

    makeWritePath(issuer: NoRegretsProxyHandler, p: PropertyKey, protoCount: number): AccessPath

    makeArgPath(path: AccessPath, count: number): AccessPath

    makeCallPath(issuer: NoRegretsProxyHandler, argNum: number): AccessPath

    makeConstructorPath(issuer: NoRegretsProxyHandler, argNum: number): AccessPath

    makeReceiverPath(appPath: AccessPath): AccessPath
}

