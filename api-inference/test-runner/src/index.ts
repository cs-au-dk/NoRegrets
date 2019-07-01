// if (process.argv.length < 1) {
//  throw new Error(`Options file path required`);
//}
// let optionsFile = process.argv[0];
import {Exception} from "winston";

import {Options} from "./option";
import {TestReportError, TestResult} from "./testResult";
import {TestRunnerImpl} from "./testRunner";

{
  const LOG_CATEGORY = 'index';
  const fs = require("fs");
  const winston = require("winston");
  const path = require("path");
  winston.level = 'debug';

  winston.info(`${LOG_CATEGORY} - Starting test-runner`);

  var ArgumentParser = require('argparse').ArgumentParser;

  var parser = new ArgumentParser(
      {version : '0.0.1', addHelp : true, description : 'test-runner'});

  parser.addArgument([ '-m', '--modelFile' ],
                     {help : 'the file containing the model', required : true});

  parser.addArgument(
      [ '-n', '--libraryName' ],
      {help : 'the name of the tested library', required : true});

  parser.addArgument([ '-f', '--libraryFolder' ], {
    help :
        'the location of the library where require(libraryFolder) should return a module',
    required : true
  });

  parser.addArgument([ '--libraryVersion' ], {
    help : 'the library version being tested (used in coverage file name)',
    required : true,
    type : 'string'
  });

  /**
   * This has to be a string since the parser doesn't support booleans
   */
  parser.addArgument([ '--skipHigherOrder' ], {
    help : 'flag enabling synthetisation of higher-order functions',
    required : false,
    defaultValue : "false",
    type : 'string',
    choices : [ "true", "false" ]
  });

  /**
   * Check that values of pathStrs match the exact value in the model. Not just its
   * type.
   */
  parser.addArgument([ '--exactValueChecking' ], {
    help : 'flag enabling exact value checking',
    required : false,
    defaultValue : "false",
    type : 'string',
    choices : [ "true", "false" ]
  });

  parser.addArgument([ '--coverage' ], {
    help : 'compute library statement coverage',
    required : false,
    defaultValue : "false",
    type : 'string',
    choices : [ "true", "false" ]
  });

  const options: Options = parser.parseArgs();
  const initTime: number = Date.now();

  let report;
  try {
    report = new TestRunnerImpl(options, initTime).runTest();
  } catch (e) {
    report = new TestReportError(`${e.message}\n${e.stack}`, initTime);
  }
  const reportJson = JSON.stringify(report.toJson(), null, 2);
  winston.debug(reportJson);
  const testReportFile = path.resolve(path.dirname(options.modelFile),
                                      TestResult.TEST_RESULT_FILE_NAME);
  fs.writeFileSync(testReportFile, reportJson);
  winston.info(`Written test report to ${testReportFile}`);

  // Force kill the process (sometimes random (test library related) things stop
  // the process from exiting)
  process.exit(0);
}
