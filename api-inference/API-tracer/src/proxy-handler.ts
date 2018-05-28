
import {AccessPath, Label} from "./paths";
import {CustomProxyHandler} from "./regression-runtime-checker";

export interface ProxyEventHandler {

    handleHas(
        proxyHandler: CustomProxyHandler,
        property: PropertyKey,
        has: boolean
    )

    handleWrite(
        target: CustomProxyHandler,
        writtenValue: any,
        writeToPath: AccessPath
    )

    handleRead(
        target: CustomProxyHandler,
        originalLookedUpValue: any,
        readLookedUpPath: AccessPath
    )

    handleCall(
        target: CustomProxyHandler,
        argArray: any[],
        functionResult: any,
        callPath: AccessPath
    )

    handleGetPrototype(target: CustomProxyHandler,
                       originalLookedUpValue: any,
                       readLookedUpPath: AccessPath
    )

    makeAccessPath(issuer: CustomProxyHandler, p: PropertyKey): AccessPath

    makeArgPath(path: AccessPath, count: number): AccessPath

    makeCallPath(issuer: CustomProxyHandler, argNum: number): AccessPath

    makeConstructorPath(issuer: CustomProxyHandler, argNum: number): AccessPath
}

