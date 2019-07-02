## NoRegrets
This repository contains the NoRegrets (Node.js Type Regression Tester) and NoRegrets+ tools
for detecting breaking changes in Node.js library updates, as described in the
2018 ECOOP [_Type Regression Testing to Detect Breaking Changes in Node.js Libraries_](https://cs.au.dk/~amoeller/papers/noregrets/paper.pdf) and
2019 FSE [_Model-Based Testing of Breaking Changes in Node.js Libraries_](https://cs.au.dk/~amoeller/papers/noregretsplus/paper.pdf).
See [web-UI](web-UI/README.md) for a detailed description of NoRegrets.

## Installation
Dependencies
 - Linux or Mac
 - Java 8 or newer (openJDK will not work)
 - Scala Build Tool (SBT) v1.0.3 or newer
 - Node.js (Tested with Node v8.10.0)
 - npm (tested with npm 5.5.1)

Run `install.sh` to install the remaining dependencies.

## License
Copyright 2019 casa.au.dk

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
