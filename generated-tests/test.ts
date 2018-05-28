import * as fs from "fs";
import * as util from "util";
import {error, isNullOrUndefined} from "util";
import {unescape} from "querystring";

///////////////////////////////////////////////////
///////////////////////////////////////////////////
interface Members {
    m: MemberObj[];
}

interface NeededObj {
    obj: TypeValue;
    edge: Edge;
}

interface TypeValue {
    runtimeType: string;
    repr: string;
    value: any;
    initial: string;
    fields: Members;
}

interface Edge {
    label: string;
    argsRepr: string[];
}

interface MemberObj {
    edge: Edge;
    destinationRepr: string[];
}
///////////////////////////////////////////////////
///////////////////////////////////////////////////

const DEBUG_GET = false;
const DEBUG_CHECK = false;
const DEBUG_IDS = true;
const DEBUG_ERROR_INSTANTLY = false;
const DEBUG_PROXIES = false;
const NO_VALUE = Symbol("NoValue");

///////////////// HEURISTICS //////////////////////

const ALLOW_NULL_AND_UNDEFINED_OBJECTS = false;

///////////////////////////////////////////////////

let proxies: Map<string, Set<NeededObj>> = fromPairsSet(JSON.parse(fs.readFileSync('test/proxies.json','utf8')));
let details: Map<string, TypeValue> = fromPairs(JSON.parse(fs.readFileSync('test/details.json','utf8')));
let initials: Set<string> = new Set<string>(JSON.parse(fs.readFileSync('test/initials.json','utf8')));

let typeCheckTasks: Map<string, CheckTask>  = new Map();
let getTasks: Map<string, TypeRetrivalTask> = new Map();
let synthCache: Map<string, any> = new Map();

abstract class TestError {
    task: Task | null;
    abstract toString() : string;
}

class TypeCheckError extends TestError {
    msg: string;
    e: Error | null;

    constructor(msg: string, task: Task | null = null, e: Error | null = null) {
        super();
        this.msg = msg;
        this.task = task;
        this.e = e;
    }
    toString() {
        let errMsg = "TypeCheckError(\n" +
                `    message: ${this.msg}\n` +
                `    task: ${this.task}\n`+
                `    error: ${this.e}`;
        if(this.e === null) {
            return errMsg + ")";
        }
        else {
            return errMsg + "\n" + "stack: " + this.e.stack + ")";
        }
    }
}

class RecursiveGetError extends TestError {
    constructor(task: Task | null = null) {
        super();
        this.task = task;
    }

    toString() {
        return "RecursiveGetError(\n" +
            `   task ${this.task})`
    }
}

/**
 * This exception should be thrown whenever there is an issue with the technique rather than the module under test.
 * Therefore, it should only be caught in the top-level throw catch.
 */
class InternalTypeCheckError {
    msg: string;
    task: Task | null;
    e: Error | null;
    constructor(msg: string, task: Task | null = null, e: Error | null = null) {
        this.msg = msg;
        this.task = task;
        this.e = e;
    }
    toString() {
        let errMsg =  "InternalTypeCheckError(\n" +
            `    message: ${this.msg}\n` +
            `    task: ${this.task}\n`+
            `    error: ${this.e}`;
        if(this.e === null) {
            return errMsg + ")";
        }
        else {
            return errMsg + "\n" + "stack: " + this.e.stack + ")";
        }
    }
}

abstract class Task {
    issuer: Task | null;
    path: string;
    errors: Set<TestError> = new Set();

    constructor(path: string, issuer: Task | null) {
        this.issuer = issuer;
        this.path = path;
    }

    abstract getPath(): string

    getRestPath(): string {
        return (this.issuer != null) ?
            `${this.issuer.getPath()}\n   .`
            :
            "";
    }

    /**
     * Store the error err for the attempted type value value
     */
    error(err: TestError) {
        this.errors.add(err);
    }

    copyErrorsFromTask(tsk: Task) {
        for(let e of tsk.errors) {
            this.errors.add(e);
        }
    }

    copyErrorsFromTasks(tasks : Array<Task>) {
        tasks.forEach(t => this.copyErrorsFromTask(t));
    }

    hasErrors(): boolean {
        return this.errors.size > 0;
    }

    removeErrors() {
        this.errors.clear()
    }
}

class TypeRetrivalTask extends Task {
    objId: string;
    foundValues: Set<any> = new Set();
    candidateValue: Set<any> = new Set();
    strategy: string = "";

    constructor(objId: string,
                path: string,
                issuer: Task | null) {
        super(path, issuer);

        this.objId = objId;
    }

    /**
     * The value will be attempted
     */
    attempt(value: any) {
        this.candidateValue.add(value);
    }

    /**
     * The value is now verified of the type this.objId
     */
    found(value: any) {
        this.foundValues.add(value);
        this.candidateValue.delete(value);
    }

    candidate(value: any) {
        this.candidateValue.add(value);
    }

    clear() {
        this.candidateValue.clear();
    }

    foundSet(value: Set<any>) {
        for(let el of value) {
            this.foundValues.add(el);
            this.candidateValue.delete(el);
        }
    }

    withStrategy(s: string) {
        this.strategy = s;
    }

    getPath(): string {
        if(DEBUG_IDS) {
            return `${this.getRestPath()}Get(${this.path}, ${this.objId}, ${this.strategy})`;
        }
        else {
            return `${this.getRestPath()}Get(${this.path}, ${this.strategy})`;
        }
    }

    toString(): string {
        return this.getPath();
    }
}

class CheckTask extends Task {
    objId: string;
    valueToCheck: any;

    constructor(value: any,
                objId: string,
                path: string,
                issuer: Task | null) {
        super(path, issuer);
        this.valueToCheck = value;
        this.objId = objId;
    }

    getPath(): string {
        if(DEBUG_IDS) {
            return `${this.getRestPath()}Check(${this.path}, ${this.objId})`;
        }
        else {
            return `${this.getRestPath()}Check(${this.path})`;
        }
    }

    toString(): string {
        return this.getPath();
    }
}



function fromPairs(o: any): Map<string, any> {
    let m = new Map();
    for(let k in o) {
        m.set(k, o[k]);
    }
    return m;
}

function fromPairsSet(o: any): Map<string, Set<any>> {
    let m = new Map();
    for(let k in o) {
        m.set(k, new Set<string>(o[k]));
    }
    return m;
}

async function getTypeValue(ti: TypeRetrivalTask): Promise<TypeRetrivalTask> {


    /**
     * In the case of
     * CHECK(o)...GET(o)
     * as originated from
     *  () --- m ---> (42) --- invoke (42) ----> (Int)
     * we can take the value we are checking, assuming it correct.
     *
     * In the other cases, for example
     * GET(o)...CHECK(o)...GET(o)
     * or
     * GET(o)....GET(o)
     * we use the candidate value of the get if present, otherwise we throw an internal error
     *
     */
    let recursiveTask = isRecursiveGet(ti.objId, ti.issuer);
    if(recursiveTask != null) {
        ti.withStrategy("recursive");
        if(recursiveTask instanceof CheckTask) {
            ti.found(recursiveTask.valueToCheck);
        }
        else if(recursiveTask instanceof TypeRetrivalTask && recursiveTask.candidateValue.size > 0) {
            ti.foundSet(recursiveTask.candidateValue);
        }
        else {
            ti.error(new RecursiveGetError(ti));
            //throw new RecursiveGetError(ti);
            //throw new InternalTypeCheckError(`Unable to provide a value for recursive task`, ti);
        }

        /*let err = new TypeCheckError(`Recursive get of ${ti.objId}`, ti);
        if(DEBUG_ERROR_INSTANTLY) console.log(`Error: ${err.msg}`);
        ti.error(err);*/
        return ti;
    }

    getTasks.set(ti.objId, ti);

    // If it is primitive, then synthesize
    let objDetails = getObjectDetail(ti.objId);
    if (util.isNullOrUndefined(objDetails)) {
        let msg = `Object ${ti} not present in the details map`;
        if(DEBUG_ERROR_INSTANTLY) console.log(`Error: ${msg}`);
        throw new InternalTypeCheckError(msg, ti);
    }

    if(DEBUG_GET)
        console.log(`getTypeValue(${ti})`);

    switch (objDetails.runtimeType) {
        case "string":
            ti.found(objDetails.value);
            return ti;
        case "boolean":
            ti.found(objDetails.value);
            return ti;
        case "number":
            ti.found(objDetails.value);
            return ti;
        case "undefined":
            ti.found(undefined);
            return ti;
        case "null":
            ti.found(null);
            return ti;
        case "object":
        case "function":
        default:
            return await getObjOrFunction(ti, objDetails);
    }
}

async function getObjOrFunction(ti: TypeRetrivalTask, detail: TypeValue): Promise<TypeRetrivalTask> {
    const ProxyAndSynthesize = "proxy-and-then-synthesize";

    var target;
    if(!isNullOrUndefined(detail.initial)) {
        ti.withStrategy("require");
        let target;
        try {
            target = require(detail.initial);
        }
        catch (e) {
            ti.error(new TypeCheckError(`Unable to require ${detail.initial}`, ti, e));
            ti.clear();
            return ti;
        }
        ti.candidate(target);
        let response = await check(
            new CheckTask(
                target,
                ti.objId,
                ti.path,
                ti
            ));
        if (!response.hasErrors()) {
            //Note! the presence  comment is asserted in successful runs. DO NOT DELETE
            console.log(`successfully checked module ${detail.initial}`);
            ti.found(target);
        }
        else {
            for (let e of response.errors) ti.error(e);
        }

    }

    else {
        let objProxies = Array.from(proxies.get(ti.objId) as Set<NeededObj>);

        if (objProxies.length != 0) {

            ti.withStrategy(ProxyAndSynthesize);
            if(DEBUG_PROXIES)
                console.log(`Retrieving ${ti.objId} through proxies ${util.inspect(objProxies)}`);

            // First we retrieve the proxies, if possible
            let attempts: Promise<[NeededObj, TypeRetrivalTask]>[]  = objProxies.map(async proxy => {
                let task:TypeRetrivalTask = await getTypeValue(
                    new TypeRetrivalTask(
                        proxy.obj.repr,
                        `<proxy-for-label-${proxy.edge.label}>`,
                        ti));
                let ret: [NeededObj, TypeRetrivalTask] = [proxy, task];
                return ret;
            });

            let targets = await Promise.all(attempts);

            let retrievedSuccessfully = targets.filter(t => !t[1].hasErrors());
            let failedToRetrieve = targets.filter(t => t[1].hasErrors());

            if(retrievedSuccessfully.length > 0) {

                // Perform the access
                let accessed = await Promise.all(retrievedSuccessfully.map(async target => {
                    let fakeTask = new TypeRetrivalTask(ti.objId, `<proxy-access-${target[0].edge.label}>`, ti);
                    let ret = await accessEdge(Array.from(target[1].foundValues)[0], target[0].edge, fakeTask);
                    return [ret, fakeTask];

                    // Then we typecheck ret
                    /*let checked = await check(
                        new CheckTask(
                            ret,
                            ti.objId,
                            `<proxy-access-${target[0].edge.label}`,
                            fakeTask
                        )
                    );*/

                }));

                let accessedSuccessfully = accessed.filter(t => !t[1].hasErrors());
                let failedToAccess = accessed.filter(t => t[1].hasErrors());

                if(accessedSuccessfully.length > 0) {
                    ti.found(Array.from(accessedSuccessfully)[0][0]);
                }
                else {
                    if (DEBUG_ERROR_INSTANTLY)
                        console.log("Failed check, task: " + failedToAccess + "\n" +
                            "errors: " + failedToAccess.map(e => Array.from(e[1].errors).join(", ")).join(" || "));
                    ti.copyErrorsFromTasks(failedToAccess.map(objectTaskPair => objectTaskPair[1]))
                }
            }
            else {
                ti.copyErrorsFromTasks(failedToRetrieve.map(objectTaskPair => objectTaskPair[1]));
            }
        }

        let onlyRecursionErrors = Array.from(ti.errors).every(e => e instanceof RecursiveGetError);
        // If there are no proxies or only recursion errors then it is safe to try to synthesize the required object.
        if (objProxies.length == 0 || (ti.errors.size > 0 && onlyRecursionErrors && ti.strategy == ProxyAndSynthesize)) {
            ti.removeErrors();
            let r = synthesize(ti.objId, ti);
            ti.found(r);
            return ti;
        }
    }
    return ti;
}

/**
 * Check that the target is a value of one of the typeValuesRepr types.
 * If errors are detected they are attached to the getTasks,
 *
 */
async function check(checkTi: CheckTask): Promise<CheckTask> {
    if(DEBUG_CHECK)
        console.log(`check(${checkTi})`);

    if(isRecursiveCheck(checkTi.objId, checkTi.issuer)) {
        return checkTi;
    }

    typeCheckTasks.set(checkTi.objId, checkTi);

    let detail = getObjectDetail(checkTi.objId);

    const observedRuntimeType = detail.runtimeType;
    const realRuntimeType = getRealRuntimeType(observedRuntimeType);
    if (ALLOW_NULL_AND_UNDEFINED_OBJECTS
        && (realRuntimeType === "undefined" || realRuntimeType === "object") ) {
        let toCheckType : string = typeof checkTi.valueToCheck;
        if (toCheckType !== 'undefined' && toCheckType !== 'object') {
            checkTi.error(new TypeCheckError(`Wrong runtime type, expected ${realRuntimeType}(${observedRuntimeType}) but had ${toCheckType}`, checkTi));
            return checkTi;
        }
    }
    //null need special case since typeof null === 'object'
    else if (observedRuntimeType === "null" && checkTi.valueToCheck != null) {
        checkTi.error(new TypeCheckError(`Wrong runtime type, expected null but had ${typeof checkTi.valueToCheck}`, checkTi));
        return checkTi;
    }
    else if (getRealRuntimeType(observedRuntimeType) != typeof checkTi.valueToCheck) {
        checkTi.error(new TypeCheckError(`Wrong runtime type expected ${realRuntimeType}(${observedRuntimeType}) but had ${typeof checkTi.valueToCheck}`, checkTi));
        return checkTi;
    }


    let fieldsChecks = detail.fields.m.map(async memb => {

        let ret = await accessEdge(checkTi.valueToCheck, memb.edge, checkTi);

        if(ret === NO_VALUE) return;

        let memberChecks = await Promise.all(memb.destinationRepr.map(async repr =>
            await check(
                new CheckTask(
                    ret,
                    repr,
                    `${memb.edge.label}`,
                    checkTi))
        ));

        let res = memberChecks.find(m => m.hasErrors());
        if(!isNullOrUndefined(res)) {
            for(let err of res.errors) {
                checkTi.error(err);
            }
        }
    });
    await Promise.all(fieldsChecks);
    return checkTi;
}

async function accessEdge(target: any, edge: Edge, issuer: Task) {
    let label = edge.label;
    var count = 0;
    let neededArgs: Promise<TypeRetrivalTask>[] = edge.argsRepr.map(arg =>
        getTypeValue(
            new TypeRetrivalTask(
                arg,
                `<arg ${count++}>`,
                issuer))
    );
    let args: TypeRetrivalTask[] = await Promise.all(neededArgs);

    let failureRetrievingArgs = args.some(arg => arg.errors.size > 0);

    if(failureRetrievingArgs) {
        if(DEBUG_ERROR_INSTANTLY) {
            console.log(`Failure retrieving args while checking member ${label} of ${issuer}`);
        }
        for(let arg of args) {
            issuer.copyErrorsFromTask(arg);
        }
        return;
    }

    try {
        return access(target, label, args);
    }
    catch(e) {
        let error = new TypeCheckError(`Failed access of ${label} with repr of args ${edge.argsRepr}`, issuer, e);
        if(DEBUG_ERROR_INSTANTLY) console.log(error.msg);
        issuer.error(error);
        return NO_VALUE;
    }
}

function access(target: any, label: string, argsTasks: TypeRetrivalTask[]) {

    let args = argsTasks.map(trt => Array.from(trt.foundValues)[0]);
    switch(label) {
        case "___[invoke]":
            try {
                return Function.prototype.apply.call(target, null, args);
            }
            catch(e) {
                if(DEBUG_ERROR_INSTANTLY)
                    console.log("Failed invocation of:" + util.inspect(target) + ":\n" + e + "\n" + e.stackTrace);
                throw e;
            }
        case "___[method-call]":
            try {
                return Function.prototype.apply.call(target, args[0], args.slice(1));
            }
            catch(e) {
                if(DEBUG_ERROR_INSTANTLY)
                    console.log("Failed invocation of:" + util.inspect(target) + ":\n" + e + "\n" + e.stackTrace);
                throw e;
            }
        case "___[new]":
            try {
                let ret = Reflect.construct(target, args);
                return ret;
            }
            catch(e) {
                if(DEBUG_ERROR_INSTANTLY) {
                    console.log("Failed invocation of:" + util.inspect(target) + ":\n" + e + "\n" + e.stackTrace);
                }
                throw e;
            }
        case "___[access]":
            return target[0];
        case "___[prototype]": {
            let proto = Object.getPrototypeOf(target);
            return proto;
        }
        default:
            return target[label];
    }
}

function getObjectDetail(objId: string): TypeValue {
    let tv = details.get(objId);
    if(isNullOrUndefined(tv)) throw new InternalTypeCheckError(`Undefined details for object id ${objId}`, null, new Error());
    return tv;
}

/**
 * Is recursive get whenever there is a get or a check in the chain
**/
function isRecursiveGet(objId: string, leaf: Task | null): Task | null {
    if(isNullOrUndefined(leaf))
        return null;
    else if(
        (leaf instanceof TypeRetrivalTask && leaf.objId == objId)
        ||
        (leaf instanceof CheckTask && leaf.objId == objId))
        return leaf;
    else return isRecursiveGet(objId, leaf.issuer);
}

/**
 * It is recursive when there is another check in the chain
 */
function isRecursiveCheck(objId: string, leaf: Task | null): boolean {
    if(isNullOrUndefined(leaf))
        return false;
    else if(leaf instanceof CheckTask && leaf.objId == objId)
        return true;
    else return isRecursiveCheck(objId, leaf.issuer);
}

function getRealRuntimeType(t: string) {
    switch(t) {
        case "function": return "function";
        case "symbol": return "symbol";
        case "string": return "string";
        case "undefined": return "undefined";
        case "null": return "object";
        case "number": return "number";
        case "boolean": return "boolean";
    }
    return "object";
}

function synthesize(objId: string, task: Task, visited: Map<string, any> = new Map()) {

    if(visited.has(objId)) {

        if(visited.get(objId) === null) throw new InternalTypeCheckError(`Recursive synthesis: ${Array.from(visited.keys()).join(", ")}`, task);

        return visited.get(objId);
    }

    visited.set(objId, ret); //pre-assigning to null to break recursion

    let typeInfo = getObjectDetail(objId);

    if(synthCache.has(objId)) {
        return synthCache.get(objId);
    }

    function initObjectField(obj: any) {
        for(let m of typeInfo.fields.m) {
            if(m.edge.label === "___[invoke]"
                || m.edge.label === "___[method-call]"
                || (m.edge.label === "name" && typeof obj === "function") ) continue;
            if (m.edge.label === "___[prototype]") {
                let proto = synthesize(m.destinationRepr[0], task, new Map(visited));
                Object.setPrototypeOf(obj, proto);
                continue;
            }
            if (m.edge.label === "___[access]") {
                if (obj.length === undefined) {
                    let lengthField = typeInfo.fields.m.find((field) => field.edge.label === 'length');
                    if (isNullOrUndefined(lengthField)) {
                        throw new InternalTypeCheckError("tried to synthesize ___[access] label for object without field 'length'");
                    }
                    obj['length'] = synthesize(lengthField.destinationRepr[0], task, new Map(visited));
                }
                let length = obj.length;
                for (var i = 0; i < length; i++) {
                    obj[i] = synthesize(m.destinationRepr[0], task, visited)
                }
                continue;
            }
            if (m.edge.label === "length") {
                if (obj.length !== undefined) {
                    // length might already have been synthesized by an ___[access] label.
                    continue;
                }
            }
            if(m.destinationRepr.length != 1) {
                m.destinationRepr.sort((a, b) => a.localeCompare(b));
            }
            if(m.destinationRepr.length > 0) {
                /*
                let tv = await getTypeValue(new TypeRetrivalTask(m.destinationRepr[0], m.edge.label, task));
                if(tv.hasErrors()) {
                    task.copyErrorsFromTask(tv);
                }
                else {
                    obj[m.edge.label] = Array.from(tv.foundValues)[0];
                }
                */
                obj[m.edge.label] = synthesize(m.destinationRepr[0], task, new Map(visited));
            }
        }
    }

    var ret;
    switch(typeInfo.runtimeType) {
        case "Array":
            var arr : any = [];
            initObjectField(arr);
            ret = arr;
            break;
        case "ArrayBuffer":
            ret = new ArrayBuffer(100);
            break;
        case "Buffer":
            ret = new Buffer("Some content");
            break;
        case "DataView":
            throw new InternalTypeCheckError("Not yet supported DataView",task);
        case "Date":
            throw new InternalTypeCheckError("Not yet supported Date",task);
        case "Error":
            throw new InternalTypeCheckError("Not yet supported Error",task);
        case "EvalError":
            throw new InternalTypeCheckError("Not yet supported EvalError",task);
        case "Float32Array":
            throw new InternalTypeCheckError("Not yet supported Float32Array",task);
        case "Float64Array":
            throw new InternalTypeCheckError("Not yet supported Float64Array",task);
        case "Int16Array":
            throw new InternalTypeCheckError("Not yet supported Int16Array",task);
        case "Int32Array":
            throw new InternalTypeCheckError("Not yet supported Int32Array",task);
        case "Int8Array":
            throw new InternalTypeCheckError("Not yet supported Int8Array",task);
        case "Map":
            throw new InternalTypeCheckError("Not yet supported Map",task);
        case "Promise":
            throw new InternalTypeCheckError("Not yet supported Promise",task);
        case "RangeError":
            throw new InternalTypeCheckError("Not yet supported RangeError",task);
        case "ReferenceError":
            throw new InternalTypeCheckError("Not yet supported ReferenceError",task);
        case "RegExp":
            ret = new RegExp("");
            break;
        case "Set":
            ret = new Set();
            break;
        case "Symbol":
            ret = Symbol("What?");
            break;
        case "SyntaxError":
            throw new InternalTypeCheckError("Not yet supported SyntaxError",task);
        case "TypeError":
            throw new InternalTypeCheckError("Not yet supported TypeError",task);
        case "URIError":
            throw new InternalTypeCheckError("Not yet supported URIError",task);
        case "Uint16Array":
            throw new InternalTypeCheckError("Not yet supported Uint16Array",task);
        case "Uint32Array":
            throw new InternalTypeCheckError("Not yet supported Uint32Array",task);
        case "Uint8Array":
            throw new InternalTypeCheckError("Not yet supported Uint8Array",task);
        case "Uint8ClampedArray":
            throw new InternalTypeCheckError("Not yet supported Uint8ClampedArray",task);
        case "WeakMap":
            throw new InternalTypeCheckError("Not yet supported WeakMap",task);
        case "WeakSet":
            throw new InternalTypeCheckError("Not yet supported WeakSet",task);
        case "string":
            ret = typeInfo.value;
            break;
        case "number":
            ret = typeInfo.value;
            break;
        case "boolean":
            ret = typeInfo.value;
            break;
        case "null":
            ret = null;
            break;
        case "undefined":
            ret = undefined;
            break;
        case "function":

            let invocations = typeInfo.fields.m.filter(m => m.edge.label === "___[invoke]" || m.edge.label === "___[method-call]");
            let argsMap: Map<number, any> = new Map();
            for(let inv of invocations) {
                inv.destinationRepr.sort((a, b) => a.localeCompare(b));


                let retValue = synthesize(inv.destinationRepr[0], task);
                if(inv.edge.label === "___[method-call]") {
                    argsMap.set(inv.edge.argsRepr.length - 1, retValue);
                }
                else {
                    argsMap.set(inv.edge.argsRepr.length, retValue);
                }
            }

            let f = function fun():any {
                if (argsMap.size == 0) {
                    throw new InternalTypeCheckError(`Unexpected function invocation: The function with object id ${objId} has not been recorded as invoked in the trace`, task);
                }

                if(!argsMap.has(arguments.length)) {
                    throw new TypeCheckError(`Invoked synthesized function with wrong number of arguments, expected ${Array.from(argsMap.keys()).join(",")} but got ${arguments.length}: ${util.inspect(arguments)}`);
                }
                else {
                    return argsMap.get(arguments.length);
                }
            };
            // Useful for debugging a floating around function
            //(f as any)["___[argsMap]"] = argsMap;
            initObjectField(f);
            ret = f;
            break;
        case "object":
            let obj = {};
            initObjectField(obj);
            ret = obj;
            break;
        default:
            throw new InternalTypeCheckError(`Unexpected type ${typeInfo.runtimeType}`, task);
    }
    visited.set(objId, ret);
    synthCache.set(objId, ret);
    return ret;
}


async function init(): Promise<any> {
    console.log(`Init with ${initials.size} initial type values`);
    let initialGets = await Promise.all(Array.from(initials).map(init =>
        getTypeValue(new TypeRetrivalTask(
            init,
            `require(${getObjectDetail(init).initial})`,
            null))
    ));

    for(let get of initialGets) {
        if(get.hasErrors()) {
            process.exitCode = 1;
            console.log("Errors retrieving initial object:");
            console.log(Array.from(get.errors).join("\n   "))
        }
    }

    let tcList = Array.from(typeCheckTasks.values());
    let failed = tcList.filter(tcv => tcv.errors.size > 0);
    let succeeded = tcList.filter(tcv => tcv.errors.size == 0);

    console.log(`Performed ${typeCheckTasks.size} type-checks, failed: ${failed.length}, successful: ${succeeded.length}`);
    console.log(`Got a total of ${getTasks.size} retrival tasks`);

    var tcFailed = false;
    for(let tc of typeCheckTasks.values()) {
        if (initials.has(tc.objId)) {
            let valueStr = "" + tc.valueToCheck;
            let valueTrunc = valueStr.substring(0, Math.min(valueStr.length, 20));
            tcFailed = tcFailed || tc.hasErrors();

            console.log(`checked value: -${valueTrunc}-:${typeof tc.valueToCheck} against type: ${tc.objId}, with task ${tc}, errors:\n  ${Array.from(tc.errors).join("\n  ")}`);
            console.log("\n");
        }
    }
    if(tcFailed)
        process.exitCode = 1;
}

init()
    .catch(error => {
        process.exitCode = 2;
        console.log(`UNCAUGHT ERROR: ${error}\n${error.stack}`);
    });

/*setInterval(() => {
    console.log(`Type check tasks: ${typeCheckTasks.size}, get tasks: ${getTasks.size}`);
}, 1000);*/