@import distilling.server.test_generation.Types._;

@(testSpec;: TestSpecification;)


// Goto Preferences -> Code style -> “Formatter Control” and tick Enable Formatter markers in comments
// add another 'at' to the patterns to avoid conflicts with the templating

// @@formatter:off

//Register the coffee-script compiler, such that coffee-script modules can be loaded.
CoffeeScript = require('coffee-script');
CoffeeScript.register();

var chai = require("chai");
var colors = require("colors");
//var mocha = require('mocha');

const DEBUG = true;
const DEBUG_VERBOSE = false;
const OBJECT_PRINT_DEPTH = 5;

// With this heuristic enabled null and undefined are considered ok replacements for objects and the other way around.
// NOTE, that checking will be cut off if null or undefined is used instead of an object
const UNIFY_NULL_UNDEFINED_OBJECT_HEURISTIC = true;

function debug(s, verbose) {
  if (verbose == undefined) {
    verbose = false;
  }
  if (verbose) {
    if (DEBUG && DEBUG_VERBOSE) {
      console.log("DEBUG: "+s());
    }
  } else {
      if (DEBUG) {
        console.log("DEBUG: "+s());
      }
  }
}

function error(s) {
  console.log("ERROR: "+s());
}

function FailedCheck(msg) {
  this.msg = msg;
}

var Levels = {
  "OK": 0,
  "EXCEPTION": 2,
  "FAILED_CHECK": 4
};

var failureLevel = Levels["OK"];

/**
 * Map from descriptionIds to values (objects)
 */
var objectMap = new Map();
/**
 * Map typeIds to descIds
 * Note, it is an object used as a map, not an ES6 Map.
 */
var objDescMap = JSON.parse("@testSpec.renderTypeIdMap()");

var assert = chai.assert;
// var PriorityQueue = require('priorityqueue');

//TODO: Should be done in the wrapped module test
/*@for(initial <- testSpec.initials) {
    modulesRequirer["@initial.idFormatted"]();
}*/

/**
 * A check task to be performed
 *
* @@param typeId the identifier of the type
* @@param value the value that needs to be type-checked
* @@param runtimeType the runtime-type of the type
* @@param path a sequence of edges along with we discovered the type
* @@param checkFunction callback to do the type-check
* @@param onError callback called onError (serves as a continuation when checking union types)
* @@param isFunction whether the task involves a function or method call
* @@param objectPath sequence of types along with we discovered the current type
* @@param requiredIds list of object types that needs to be available before the task can be performed
* @@param requiredReceiver optional id of receiver in case the function is a method
 */
function Task(typeId, value, runtimeType, path, checkFunction, onError, isFunction, objectPath, requiredIds, requiredReceiver, isInitialObject) {
  this.typeId = typeId;
  this.value = value;
  this.runtimeType = runtimeType;
  this.path = path;
  this.checkFunction = checkFunction;
  this.onError = onError;
  this.isFunction = isFunction;
  this.objectPath = objectPath;
  this.requiredIds = requiredIds;
  this.requiredReceiver = requiredReceiver;
  this.isInitialObject = typeof isInitialObject === 'boolean' && isInitialObject === true;
}

// [Task]
var worklist = [];

function rootErrMsg(e, msg) {
  if(e instanceof FailedCheck) {
    failureLevel = failureLevel | Levels["FAILED_CHECK"];
      error(() = > msg.yellow;
  )
      error(() = > e.msg.yellow;
  )
  }
  else {
    failureLevel = failureLevel | Levels["EXCEPTION"];
      error(() = > msg.red;
  )
      if (e.stack !== undefined) {
        error(() = > e.stack.red;
    )
    } else {
        error(() = > e.stack.red;
    )
    }
  }
}

var modulesRequirer = {;
@for(initial <- testSpec.initials) {
    "@{initial.idFormatted}"; : function () {
        debug(() = > "Requiring @{initial.requirePath.get} with id: @initial.idFormatted";
    )
        var required = require('@{initial.requirePath.get}');
        debug(() = > "   returned " + typeof required;
    )
        objectMap.set(objDescMap["@initial.idFormatted"], required);
      return required;
     },
}
}

@for(init <- testSpec.initials) {
  addToWorklist(
      new Task(
        "@init.idFormatted",
        modulesRequirer["@init.idFormatted"],
        "@init.runtimeType", "module",
        check_;@init.idFormatted,
        rootErrMsg,
        false,
        "@init.idFormatted",
        [],
        false,
        true;
))
}


function getObjectsForIds(task) {
  var argIds = task.requiredIds;
  var receiverId = task.requiredReceiver;
  var objects = [];

  if (receiverId !== undefined){
    var descReceiverId = objDescMap[receiverId];
    objects.push(objectMap.get(descReceiverId));
  }

  for (var i = 0; i < argIds.length; i++) {
    var descId = objDescMap[argIds[i]];
    objects.push(objectMap.get(descId));
  }
  return objects;
}

function hasRequiredIds(task) {
  var argIds = task.requiredIds;
  var receiverId = task.requiredReceiver;

  if (receiverId !== undefined && !objectMap.has(objDescMap[receiverId])) {
    return false;
  }

  for (var i = 0; i < argIds.length; i++) {
    var descId = objDescMap[argIds[i]];
    if (!objectMap.has(descId)) {
       return false;
    }
  }
  return true;
}

function synthesizeObjects(task) {
  var argIds = task.requiredIds;
  var receiverId = task.requiredReceiver;

  var objects = [];

  if (receiverId !== undefined) {
    var descReceiverId = objDescMap[receiverId];
    assert.isTrue(objectMap.has(descReceiverId), "AssertionError: objectMap does not contain receiver, which we cannot synthesize");
    objects.push(objectMap.get(descReceiverId));
  }


  for (var i = 0; i < argIds.length; i++) {
    var descId = objDescMap[argIds[i]];
    if (objectMap.has(descId)) {
      objects.push(objectMap.get(descId));
    } else {
      var synthesizedObject = synthesizeObject(descId);
        debug(() = > "synthesized desc " + descId + " for typeId " + argIds[i] + "\n" + objectToString(synthesizedObject, OBJECT_PRINT_DEPTH), true;
    )
        objects.push(synthesizeObject(descId));
    }
  }
  return objects;
}


function synthesizeObject (id) {
  var objectRepresentation = eval("getObjectRepresentation_" + id + "()");
  var ttype = objectRepresentation.ttype;
  var fields = objectRepresentation.fields;
  var object;

  function getConcreteValue (value) {
    if (typeof value === "string") {
      var objectExtractRegExp = /___obj\[(\d*)\]/;
      var matches = value.match(objectExtractRegExp);
      if (matches !== null) {
         //If matches is non-null, then 'fieldValueRepresentation' refers to an object
        var descId = matches[1];
        if (objectMap.has(descId)) {
          return objectMap.get(descId);
        } else {
          return synthesizeObject(descId);
        }
      } else {
        //value is a non-object string
        return value;
        }
    } else {
      //value is a non-string primitive
      return value;
    }
  }

  if (ttype === "function") {
    var result = objectRepresentation.invokeResult;
    object = function () {
      //Notice: We have to compute this result value at call time, otherwise we risk diverging for functions returning a value containing itself
      return getConcreteValue(result);
    }
  } else { //ttype == "object"
    object = {};
  }
  objectMap.set(id, object);

  for (var i = 0; i < fields.length; i++) {
    var field = fields[i];

    //Notice, that `field` invariantly only have one property
    //It is a slightly odd representation of a field but it is more idiomatic in the scala code
    var fieldName = Object.getOwnPropertyNames(field)[0];

    var fieldValueRepresentation = field[fieldName];
    object[fieldName] = getConcreteValue(fieldValueRepresentation);
  }
  return object;
}

function addToWorklist(task) {
  var typeId = task.typeId;
  var objectPath = task.objectPath;

  //Is it an object?
  if (typeId !== undefined && !typeId.endsWith("_0")) {
    var indexOfLastSeen = objectPath.lastIndexOf(".");
    if (indexOfLastSeen !== -1) {
      var indexOf2LastSeen = objectPath.slice(0, indexOfLastSeen).lastIndexOf(".");
      if (indexOf2LastSeen !== -1) {
        var lastTwoObjects = objectPath.slice(indexOf2LastSeen);
        var objPathWithoutLastTwoObjects = objectPath.slice(0, indexOf2LastSeen);
        if(objPathWithoutLastTwoObjects.includes(lastTwoObjects)) {
            debug(() = > "skipping task " + typeId + " due to cyclic checks";
        )
            return;
        }
      }
    }
  }

  if (typeId !== undefined) {
     var value = task.value;
    var descId = objDescMap[task.typeId];
    if (!objectMap.has(descId)) {
      objectMap.set(descId, value);
    }
      debug(() = > "adding task " + task.typeId + " to worklist";
  )
  } else {
      debug(() = > "adding invocation task with path " + task.path + " to worklist";
  )
  }

  worklist.push(task);
}
function wrap(lambda, msg, onError) {
  try {
    lambda()
  }
  catch(e) {
    if (onError !== undefined) {
      onError(e, msg);
    }
    //else if(e instanceof FailedCheck) {
    //  failureLevel = failureLevel | Levels["FAILED_CHECK"];
    //  error(() => msg.yellow);
    //  error(() => e.msg.yellow);
    //}
    //else {
    //  failureLevel = failureLevel | Levels["EXCEPTION"];
    //  error(() => msg.red);
    //  if (e.stack !== undefined) {
    //    error(() => e.stack.red);
    //  } else {
    //    error(() => e.stack.red);
    //  }
    //}
  }
}

function getErrMsg (task) {
  return "failed checking " + task.typeId + " on path " + task.path + " with objectPath " + task.objectPath;
}

function resumeWorklist() {
  if (worklist.length > 0) {
    var length = worklist.length;
    var count = 0;
    var done = false;
    while (count < length && !done) {
      //Pop the first element
      var task = worklist[0];
      worklist.shift();
      if (task.isFunction) {
        if (hasRequiredIds(task)) {
            debug(() = > "enqueueing " + task.typeId + "()";
        )
            setImmediate(wrap.bind(null, (task.checkFunction.bind(task, getObjectsForIds(task))), getErrMsg(task), task.onError));
          done = true;
        } else {
          worklist.push(task);
          count++;
        }
      } else {
          debug(() = > "enqueuing " + task.typeId;
      )
          setImmediate(wrap.bind(null, task.checkFunction.bind(task, task), getErrMsg(task), task.onError));
        done = true;
      }
    }
    if (!done){
        debug(() = > "Only tasks which require object synthesizing left. Picking one..";
    )
        var task = worklist.pop();
      setImmediate(wrap.bind(null, task.checkFunction.bind(task, synthesizeObjects(task)), getErrMsg(task), task.onError));
    }

    // regain control after checks or synthesizing are done
    setImmediate(function() {resumeWorklist();});
  } else {
    console.log("Done type checking, exit code: " + failureLevel);
    process.exit(failureLevel);
  }
}
function getValueForPrimitiveType(jsType) {
  var value = jsType.value;
  if (value !== undefined) {
    return value
  } else {
    switch (jsType.runTimeType) {
      case "String" : return "foo";
      case "Int" : return 0;
      case "Undefined" : return undefined;
    }
  }
}
/**
 * For checking primitive values, i.e., values with id 0
 */
function check_0_0(task) {
  var value = task.value;
  var runtimeType = task.runtimeType;
  var path = task.path;
  var valueType = typeof value;
  if (UNIFY_NULL_UNDEFINED_OBJECT_HEURISTIC) {
    var unifyableTypes = ["object", "undefined"];
    if (unifyableTypes.indexOf(runtimeType) !== -1) {
      if(unifyableTypes.indexOf(valueType) === -1) {
        throw new FailedCheck("Expected: {object,undefined} but found " + valueType + " at " + path);
      } else {
        return;
      }
    }
  }
  if (valueType !== runtimeType) {
    throw new FailedCheck("Expected: " + runtimeType + " but found " + valueType + " at " + path);
  }
}

// FIXME: this is not correct.
// it accept objects where the property is on the prototype but was supposed to be on the object itself
function checkHasProperty (object, property, path) {
  var objectInChain = object;
  var found = false;
  while (objectInChain !== undefined && objectInChain !== null) {
    //Note, do not use objectInChain.getOwnProperty, since this method is not defined for all objects.
    var propDesc = Object.getOwnPropertyDescriptor(objectInChain, property);
    if (propDesc !== undefined) {
      found = true;
      break;
    }
    objectInChain = Object.getPrototypeOf(objectInChain);
  }
  if (!found) {
    throw new FailedCheck("Missing property: " + property + " at " + path);
  }
}

function mergeArgs(primitives, objects) {
  var objectsRev = objects.reverse();
  var primitivesRev = primitives.reverse();
  var mergedArgs = [];
  var i = 0;
  while (primitivesRev.length > 0 && objectsRev.length > 0) {
    if (primitivesRev[primitivesRev.length-1][0] == i) {
      mergedArgs.push(primitivesRev.pop()[1]);
      i++;
    } else {
      mergedArgs.push(objectsRev.pop());
      i++;
    }
  }
  mergedArgs = mergedArgs.concat(objectsRev.reverse());
    mergedArgs = mergedArgs.concat(primitivesRev.reverse().map(o = > o[1]);
)
    return mergedArgs;
}

function extractLength(value) {
  var length = value.length;
  if (length !== undefined && isInt(length)) {
    return length;
  } else {
    throw new FailedCheck("Missing property length on object with ___[access] fields. (Object was expected to be of an Array like type)");
  }
}

function isInt(value) {
  return !isNaN(value) &&
         parseInt(Number(value)) == value &&
         !isNaN(parseInt(value, 10));
}

//display block for generating standard onError callback
@displayOnError(i; : Int;) = {
  var calledOnError = false;
  var newOnError = function(e, msg) {
    if (calledOnError)  {
      return;
    }
    @if(i == 0) {
      calledOnError = true;
      onError(e, msg);
    } else {
      calledOnError = true;
      checkPi;@{i - 1}();
    }
  };
}

@displayStandardTypeTest(stdType;: StandardType;) = {
  //Typechecking @{stdType.toString()}
  function check_;@{stdType.idFormatted}(task); {
    if(task.isInitialObject) {
        debug(() = > "Now requiring object with id @{stdType.idFormatted}, because isInitialObject: " + task.isInitialObject;
    )
        task.value = task.value();
      task.isInitialObject = false;
    }
    var value = task.value;
    var path = task.path;
    var onError = task.onError;

    if (UNIFY_NULL_UNDEFINED_OBJECT_HEURISTIC) {
        if (value === null || value === undefined) {
            debug(() = > "Value null or undefined: skipping check of " + task.typeId + " ";
        )
            return;
        }
    }

    @defining(testSpec.types(stdType)); {typeInfo =;>
      @for(propertyName <- typeInfo.properties.keys) {
        @* Check; that; the; property; exists; if all edges; are; standardtypes. Otherwise, the; edge; might in some; cases; refeer; to; an; invocation *;@
        @if(typeInfo.properties(propertyName).forall(ttype => ttype.isInstanceOf[StandardType])) {
          @if(propertyName.toString == "prototype") {
            assert.isTrue(Object.getPrototypeOf(value) !== undefined);
          } else if (propertyName.toString == "___[access]") {

          } else {
            checkHasProperty(value, "@propertyName", path);
          }
        }
      }
      @for(propertyName <- typeInfo.properties.keys) {
        @if(propertyName.toString == "___[access]") {
          @defining(typeInfo.properties(propertyName).filter(_.isInstanceOf[StandardType]).zipWithIndex.reverse); { ttypes =;>
            {
              @for(ttype <- ttypes) {
                var checkPi;@{ttype._2} = function (i) {
                  @displayOnError(ttype._2);
                  @defining(ttype._1.asInstanceOf[StandardType]); { propType =;>
                    @if(propType.runtimeType != "___[any]") {
                      var objectPath = task.objectPath + ".@{propType.idFormatted}";
                      var newTask = new Task("@{propType.idFormatted}", value[i], "@{propType.runtimeType}",  path + "." + "@{propertyName}[" + i + "]",
                          check_;@{
                                propType.idFormatted
                            }
                        ,
                            newOnError, false, objectPath, [];
                        )
                            addToWorklist(newTask);
                    }
                  }
                };
              }
              var length = extractLength(value);
              for (var i = 0; i < length; i++) {
                checkPi;@{ttypes.length - 1}(i)
              }
            }
          }
        } else {
          @defining(typeInfo.properties(propertyName)); { ttypes =;>
            @if(!ttypes.forall(ttype => ttype.isInstanceOf[AnyType.type])) {
              {
                @for(ttype <- ttypes.filterNot(_.isInstanceOf[AnyType.type]).zipWithIndex.reverse) {
                  var checkPi;@{ttype._2} = function () {
                    @displayOnError(ttype._2);
                    @if(ttype._1.isInstanceOf[StandardType]) {
                      @defining(ttype._1.asInstanceOf[StandardType]); { propType =;>
                        @if(propType.runtimeType != "___[any]") {
                          var objectPath = task.objectPath + ".@{propType.idFormatted}";
                          var newTask = new Task("@{propType.idFormatted}", value["@propertyName"], "@{propType.runtimeType}",  path + "." + "@propertyName",
                              check_;@{
                                      propType.idFormatted
                                  }
                              ,
                                  newOnError, false, objectPath, [];
                              )
                                  addToWorklist(newTask);
                        }
                      }
                    } else if (ttype._1.isInstanceOf[Invokable])  {
                      @defining(ttype._1.asInstanceOf[Invokable]); { invoke =;>
                        @if(ttype._1.isInstanceOf[FunctionCall]) {
                          @defining(ttype._1.asInstanceOf[FunctionCall]); { call =;>
                            var checkFunction = function (args) {
                              var primitiveArgsWithIndex = JSON.parse("@invoke.primitiveArgsWithIndexToJson");
                              var realArgs = mergeArgs(primitiveArgsWithIndex, args);
                              var result = value.apply(null, realArgs);
                              var objectPath = task.objectPath + ".@{call.result.idFormatted}";
                              var newTask = new Task("@{call.result.idFormatted}", result, "@call.result.asInstanceOf[StandardType].runtimeType",
                                  path + "()->", check_;@{
                                    call.result.idFormatted
                                }
                            ,
                                newOnError, false, objectPath, [];
                            )
                                addToWorklist(newTask);
                            };
                            var callRep = JSON.parse("@{call.toJson}");
                            var objectPath = task.objectPath + ".";
                            var newTask = new Task(undefined, undefined, undefined, path + "()", checkFunction, newOnError,
                                          true, objectPath, JSON.parse("@invoke.objectArgsToJson"));
                            addToWorklist(newTask);
                          }
                        } else if (ttype._1.isInstanceOf[MethodCall]) {
                          @defining(ttype._1.asInstanceOf[MethodCall]); { call =;>
                            var checkMethod = function (args) {
                              var receiver = args[0];
                              var primitiveArgsWithIndex = JSON.parse("@invoke.primitiveArgsWithIndexToJson");
                              var realArgs = mergeArgs(primitiveArgsWithIndex, args.slice(1, args.length));
                              var result = value.apply(receiver, realArgs);
                              var objectPath = task.objectPath + ".@{call.result.idFormatted}";
                              var newTask = new Task("@{call.result.idFormatted}", result, "@call.result.asInstanceOf[StandardType].runtimeType",
                                  path + "()->", check_;@{
                                    call.result.idFormatted
                                }
                            ,
                                newOnError, false, objectPath, [];
                            )
                                addToWorklist(newTask);
                            };
                            var callRep = JSON.parse("@{call.toJson}");
                            var objectPath = task.objectPath + ".";
                            addToWorklist(new Task(undefined, undefined, undefined, path + "()", checkMethod, newOnError,
                                          true, objectPath, JSON.parse("@invoke.objectArgsToJson"), callRep.receiver));
                          }
                        } else if (ttype._1.isInstanceOf[ConstructorCall]) {
                          @defining(ttype._1.asInstanceOf[ConstructorCall]); { call =;>
                            var checkConstruct = function (args) {
                              var primitiveArgsWithIndex = JSON.parse("@invoke.primitiveArgsWithIndexToJson");
                              var realArgs = mergeArgs(primitiveArgsWithIndex, args);
                              var resultingObject = Reflect.construct(value, realArgs);
                              var objectPath = task.objectPath + ".@{call.result.idFormatted}";
                              var newTask = new Task("@{call.result.idFormatted}", resultingObject, "@call.result.asInstanceOf[StandardType].runtimeType",
                                  path + "()->", check_;@{
                                    call.result.idFormatted
                                }
                            ,
                                newOnError, false, objectPath, [];
                            )
                                addToWorklist(newTask);
                            };
                            var callRep = JSON.parse("@{call.toJson}");
                            var objectPath = task.objectPath + ".";
                            var newTask = new Task(undefined, undefined, undefined, path + "()", checkConstruct, newOnError,
                                          true, objectPath, JSON.parse("@invoke.objectArgsToJson"));
                            addToWorklist(newTask);
                          }
                        }
                      }
                    }
                  };
                }
                checkPi;@{ttypes.length-1}();
              }
            }
          }
        }
      }
    }
}
}

@for(ttype <- testSpec.sortedTypes) {
  @if(ttype.id._2 != 0) {
    @displayStandardTypeTest(ttype)
  }
}

@for(idf <- testSpec.sortedObjectRepresentationKeys) {
  function getObjectRepresentation_@{idf}(); {
    return JSON.parse("@{testSpec.renderObjectDescription(idf)}");
  }
}


resumeWorklist();


/**
 * returns a string representation of the synthesized object downto depth `depth`
 * @@param o
 * @@param depth
 */
function objectToString(o, depth, space) {
  var str = "";
  if (depth < 0) {
    depth = 0;
  }
  if (space === undefined) {
    space = "";
  }
  var props = Object.getOwnPropertyNames(o);
  str += space + "{\n";
  for (var i = 0; i < props.length; i++) {
    var prop = props[i];
    var propVal = o[prop];
    if (typeof propVal === 'object' && propVal !== null) {
      if(depth !== 0) {
        str += space +  prop + " :\n" + objectToString(propVal, depth-1, space + "  ") + '\n';
      } else {
        str += space + prop + " : object\n";
      }
    } else {
      str += space + prop + " : " + propVal + '\n';
    }
  }
  str += space + "}\n";
  return str;
}

function isOfType_Number(obj) {
  return typeof obj === 'number';
}

function isOfType_Boolean(obj) {
  return typeof obj === 'boolean';
}

function isOfType_String(obj) {
  return typeof obj === 'string';
}

function isOfType_Object(obj) {
  return typeof obj === 'object';
}

function isOfType_Function(obj) {
  return typeof obj === 'function';
}

// @@formatter:on
