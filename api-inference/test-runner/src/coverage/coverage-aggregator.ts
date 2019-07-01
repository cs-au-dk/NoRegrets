import fs = require('fs');
import istanbul = require("istanbul");
import * as path from "path";
// import path = require("path");

/****
 * The following file computes the aggregated coverage for runs across multiple
 * clients. The aggregated coverage is computed such that if client A covered
 * 30% and client B another 30% where there is a 20% overlap between A and B,
 * then the total coverage will be 40%.
 * Notice, that if module A requires module B, then the coverage will be
 * computed on both A and B. It is thus not only the coverage of the module
 * itself, but the coverage of all modules required recursively by the module.
 */

const ArgumentParser = require('argparse').ArgumentParser;

const parser = new ArgumentParser(
    {version : '0.0.1', addHelp : true, description : 'coverageAggregator'});

parser.addArgument(
    [ '-c', '--coverageFile' ],
    {help : 'A file containing the list of coverage files', required : true});

parser.addArgument(
    [ '-o', '--output' ],
    {help : 'The place to write the aggregated output', required : true});

interface Options {
  coverageFile: string
  output: string
}

const opts: Options = parser.parseArgs();

const files: string[] = JSON.parse(fs.readFileSync(opts.coverageFile, "utf-8"));
const collector = new istanbul.Collector();

let coverage = {};
files.forEach(function(f) {
  const next = JSON.parse(fs.readFileSync(f, 'utf8'));
  //@ts-ignore
  for (var subModuleFile in next) {
    const subModule = pathToModule(subModuleFile);
    const nextCoverageInfo = next[subModuleFile];
    if (coverage.hasOwnProperty(subModule)) {
      //@ts-ignore
      coverage[subModule] = istanbul.utils.mergeFileCoverage(
          coverage[subModule], nextCoverageInfo);
    } else {
      coverage[subModule] = nextCoverageInfo;
    }
  }
});

function pathToModule(modulePath: string) {
  // const moduleMatch = /(.*)-\d\.\d\.\d(.*)-coverage\.json/;
  return path.basename(modulePath, ".js");

  // The first capturing group contains the module name.
  // return moduleMatch.exec(modulePath)[1];
}

// collector.add({lodash: coverage});

//@ts-ignore
const finalCoverageInfo = istanbul.utils.summarizeCoverage(coverage);
//@ts-ignore
fs.writeFileSync(opts.output, JSON.stringify(finalCoverageInfo), "utf-8");
// const report = istanbul.Report.create('json');
// report.opts.file = path.basename(opts.output);
// report.opts.dir = path.dirname(opts.output);
// report.writeReport(collector, true);
