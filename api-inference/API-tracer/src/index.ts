
// We start our job only if this is a mocha run
let shouldWe = false;
for (let i = 0; i < process.argv.length; i++) {
  // We accept _mocha at any place for the moment, the following are the
  // invocation observed so far istanbul cover node_modules/mocha/bin/_mocha --
  // -R spec node_modules/mocha/bin/_mocha ....
  if (process.argv[i].endsWith("_mocha")) {
    shouldWe = true;
  }
}
if (shouldWe) {
  let fs = require("fs");
  console.log("Entry point for tracing has been required, loading options");

  let file = null;
  try {
    file = fs.readFileSync("__options.json");
  } catch (e) {
    file = fs.readFileSync("../__options.json");
  }
  require("./boostrapper").initializeTracing(JSON.parse(file));
}
