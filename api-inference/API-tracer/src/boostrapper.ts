import {prototype} from "module";

require('debug-fd-deprecated');
import fs = require('fs');
import winston = require("winston");
import {isNullOrUndefined} from "util";
import {GlobalState} from "./global_state";
import {Config} from "./config";
import {
  cloneGlobalObjects,
  replaceDangerousNativeFunctions,
  replaceMochaFunctions,
  replaceRequire
} from "./runtime-hacks";
import {KnownValuesNode} from "./known-values";
import CRJSON = require('circular-json');
require('source-map-support').install();
import * as path from "path";

winston.level = 'info';

let loaded = false;

// Note: The output will only be written to a file if no callback is provided
export function initializeTracing(config: Config) {
  if (loaded) return;
  loaded = true;

  // longjohn will collect more detailed stack traces.
  // For example, it will include the stack of whoever asynchronously scheduled
  // an event.
  if (config.collectStackTraces && config.detailedStackTraces) {
    let longjohn = require('longjohn');
    longjohn.async_trace_limit = 15;
  }

  winston.info("Running extractor with options:");
  console.dir(config);

  replaceDangerousNativeFunctions();
  GlobalState.init(config);
  replaceRequire(config);
  replaceMochaFunctions();
  // Delay known values initialization after all the known functions have been
  // hacked
  KnownValuesNode.init();

  let exitHandler;
  let done = false;

  exitHandler = function exitHandler(options: any, err: any) {
    winston.info("Exit handler called, there are " +
                 GlobalState.tracerObservationState.observations.size +
                 " observations");
    if (!isNullOrUndefined(err) && err != 0) {
      winston.error("program terminated with error " + err);
      process.exitCode = 25;
      winston.error(err.stack);
    }
    if (!done) {
      if (config.output !== undefined) {
        // dump the tracingResult
        winston.info(`dumping trace ${config.output}`);
        winston.info(`sigint: ${options.sigint}, uncaught: ${
            options.uncaught}, exit: ${options.exit}`);
        let realErr;
        if (options.exit) {
          if (err != 0) {
            realErr = "Tests failed, process is exiting with exit code: " + err;
          }
        } else if (options.uncaught) {
          realErr = "Tests failed, process is exiting with error: " + err;
        } else if (options.sigint) {
          realErr = "Tests failed, process is exiting with error: " + err;
        }
        if (!isNullOrUndefined(realErr)) {
          winston.error("Error: " + realErr);
        }
        GlobalState.tracerObservationState.finalize();
        fs.writeFileSync(
            `${config.output}`,
            JSON.stringify(GlobalState.tracerObservationState.toJson(
                realErr, config.coverage, path.dirname(config.output),
                config.libraryVersion)));
        // fs.writeFileSync(`${config.output}`,
        // CRJSON.stringify(GlobalState.tracerObservationState.toJson(realErr)));
        winston.info(`trace dumped in ${config.output}`);
      }
      done = true;
    }
  };

  winston.info("Registering exit handlers");
  // Registering to be able to dump as soon as mocha has finished executing
  process.stdin.resume();  // so the program will not close instantly

  // do something when app is closing
  process.on('exit', exitHandler.bind(null, {exit : true}));

  // catches ctrl+c event
  process.on('SIGINT', exitHandler.bind(null, {sigint : true}));

  // catches uncaught exceptions
  process.on('uncaughtException', exitHandler.bind(null, {uncaught : true}));

  winston.info("Exit handler registered");
}
