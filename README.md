# NoRegrets Breaking Change detecting tool for NPM.
This repository contains the implementation of the NoRegrets tool as described in the 2018 ECOOP paper *Type Regression Testing to Detect Breaking Changes in Node.js Libraries* (Link will follow later)

Documentation-of how to use the tool can be found [here](guide/index.html). Note, this documentation assumes that a registry is already hosted and that NoRegrets is configured to use this registry. For more information, see [Setting up the registry](#setting-up-the-registry). 

## Dependencies
The following dependencies must be installed.
- sbt 1.0.3
- yarn 
- npm
- node v8.9.4

Moreover, the following script must be run

```
$ ./install.sh
```

## Setting up the registry
NoRegerts relies on CouchDB database containing the NPM registry.
The NPM registry contains metadata for each package on the NPM system, roughly corresponding to the information available in the [package.json](https://docs.npmjs.com/files/package.json) files.
This information is needed for NoRegrets to compute, for example, a package's dependents.

Use [this guide](https://github.com/npm/npm-registry-couchapp) to install CouchDB and download the NPM registry.
Once the installation is done, edit the registry-servers entry in [server/src/main/resources/application.conf](server/src/main/resources/application.conf) to match the CouchDB installation .


