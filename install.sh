#!/bin/bash

npm install -g diff2html-cli
npm install -g chai
npm install -g typescript@2.5.3

(cd api-inference/API-tracer && npm install)
(cd warnings-ui && npm install)
(cd ci/mocha && npm install)
