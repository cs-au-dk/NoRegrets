The following page describes the architecture of the NoRegrets+ tool presented in the [Model-based Testing of Breaking Changes in Node.js Libraries - FSE 2019](papers/ESECFSE19.pdf) paper. 

NoRegrets+ is a tool for automatically identifying breaking changes in Node.js library updates. 
It utilizes the test suites of client applications that depend upon the library to construct a model of the library's public API.
Upon an update of the library, NoRegrets+ can check that the updated library still adheres to the API specification of the API model.

In [Section 1](#architecture), we describe NoRegrets+'s architecture comprised of 4 phases, with pointers to the relevant parts of the source code.
The artifact includes a web-UI, which is documented in [Section 2](#experimental-results-web-UI), for displaying the results of NoRegrets+ and NoRegrets benchmarks.
This web-UI comes preloaded with the results used in the evaluation of NoRegrets+. In [Section 3](#interpret-results), we show how to relate the data from the web-UI to the evaluation of the paper.
Eventually, in [Section 4](#running-benchmarks), we show how to reproduce the results from the paper and how to extend NoRegrets+ with additional benchmarks.


<a name="architecture"></a>
# 1.  Architecture
NoRegrets+ is divided into 4 phases, the client retrieval phase, the model generation phase, the model processing phase, and the type regression testing phase as illustrated by the figure below.

NoRegrets+ pipeline:<a name="pipeline"></a>![NoRegrets+ pipeline](http://i65.tinypic.com/15xo5s8.png)

#### Client Retrieval
Before NoRegrets+ can generate API models for a library *l*, it must retrieve a suitable set of clients. 
When NoRegrets+ is first run, it will download and save a copy of the dependency information of all packages in the npm registry (see out/registry &#x2248; 2.8GB) (NoRegrets+ can be forced to download the newest version of the dependency information by deleting out/registry).
This dependency information is queried for a set of packages that has *l* as a dependency.
If NoRegrets+ finds more than 2000 suitable clients, then it picks the 2000 packages with the greatest number of stars on npm.
NoRegrets+ will clone each package and run their test suites filtering any packages that either do not have a test suite, or whose test suites terminate with a non-zero exit code.
The final set of clients is cached such that this phase does not have to be repeated upon later runs of NoRegrets+.
The relevant parts of the implementation are found in [backend/src/main/scala-2.11/backend/commands/Successfuls.scala](backend/src/main/scala-2.11/backend/commands/Successfuls.scala) and [backend/src/main/scala-2.11/backend/commands/Dependents.scala](backend/src/main/scala-2.11/backend/commands/Dependents.scala)

#### Model Generation
To test for breaking changes in library updates, NoRegrets+ needs to construct a model of the pre-update version of the library's public API.
A model is generated for each of the clients from the client retrieval phase.
The exact details of how the model generation works are outlined in Section 4 of the [paper](papers/ESECFSE19.pdf).
The relevant parts of the implementation are found in [backend/src/main/scala-2.11/backend/commands/RegressionTypeLearner.scala](backend/src/main/scala-2.11/backend/commands/RegressionTypeLearner.scala) 

#### Model Processing
NoRegrets+ post-processes the generated models to remove duplicate information, such that the type regression testing phase runs faster.
The exact details of how this phase works are described in Section 4 of the [paper](papers/ESECFSE19.pdf).
The implementation is found in [backend/src/main/scala-2.11/backend/regression_typechecking/TreeAPIModel.scala](backend/src/main/scala-2.11/backend/regression_typechecking/TreeAPIModel.scala). Especially, the `compress` method is relevant.

#### Type regression testing
The final phase of NoRegrets+ uses the models from the model processing phase to check an updated version of the library for type regressions.
Type regressions represent type-related changes in the public API of the library, and do often indicate breaking changes that may affect client applications.
This phase is meant to be run as part of the typical modify-test-adjust development loop, giving the developer almost instant feedback about potential breaking changes introduced by the modifications.
The relevant parts of the implementation are found in [backend/src/main/scala-2.11/backend/commands/RegressionTypeLearner.scala](backend/src/main/scala-2.11/backend/commands/RegressionTypeLearner.scala) and [api-inference/test-runner/src/testRunner.ts](api-inference/test-runner/src/testRunner.ts). 


<!-- digraph NoRegrets+ {  -->
<!--   "Client Retrieval" [ shape=box];  -->
<!--   "Model Generation" [ shape=box];  -->
<!--   "Model Processing" [ shape = box];-->
<!--   "Model Testing" [ shape=box label="Type regression testing"];  -->
<!--   "input client" [ shape=plaintext, label="Library + begin version"];  -->
<!--   "output" [ style=invis];  -->
<!--   "cachedClients" [ label="cache", shape=folder ];  -->
<!--   "cachedModels" [ label="cache", shape=folder ];-->
<!--   "inputModelTesting" [ label="Library test version", shape=plaintext];-->
<!--   -->
<!--   { rank=same "Client Retrieval" "Model Generation" "Model Testing" "input client" "output" "Model Processing"}  -->
<!--   { rank=same "cachedClients" "cachedModels" }  -->
<!--   -->
<!--   -->
<!--   "input client" -> "Client Retrieval";  -->
<!--   "Client Retrieval" -> "Model Generation" [label = "Client set"];  -->
<!--   "Model Generation" -> "Model Processing" [label = "Library models"];  -->
<!--   "Model Processing" -> "Model Testing" [label = "Processed models"];  -->
<!--   "Model Testing" -> "output" [ label = "Type Regressions, coverage info, etc.."];  -->
<!--   -->
<!--   "Client Retrieval" -> "cachedClients" [ style = dashed ];-->
<!--   "cachedClients" -> "Model Generation" [ label = "Cached clients", style = dashed];  -->
<!--   "Model Processing" -> "cachedModels" [ style = dashed ];-->
<!--   "cachedModels" -> "Model Testing" [ label = "Cached models", style = dashed];-->
<!--   "inputModelTesting" -> "Model Testing";  -->
<!-- }  -->

<a name="experimental-results-web-UI"></a>
# 2.  Experimental results web-UI 
NoRegrets+ includes a web tool for displaying the results of the experiments.
If you have opened this README.md file manually, then you can start the web-UI by navigating to the `web-UI` folder and run `npm start`.
Otherwise, you are already in the Web-UI, and you may head to the front page by clicking the _Results_ button in the navigation bar. 

<a name="results-overview"></a>
#### Results overview
The results overview (main page of the web-UI) lists results from runs of NoRegrets+ and NoRegrets.
It comes preloaded with the results necessary to fill the table from the [paper](papers/ESECFSE19.pdf).

Each entry in the list, marked as *lib@ver*, represents a benchmark of the library *lib* starting at version *ver*.
Clicking on an entry will bring forward a table with a row for every tool configuration for which the benchmark was run.
The columns of the table are as follows:

* __Tool__: Either NoRegrets+ or NoRegrets
* __Major updates__: Total number of major updates of the benchmark
* __Minor/patch updates__: Total number of minor and patch updates of the benchmark
* __With coverage__: Coverage information has been recorded when running the tool
* __With unconstrained clients__: NoRegrets+'s relaxed client option (not available for NoRegrets)
* __Total number of type regressions__: The total number of type regressions recorded across all the updates of the benchmark. _Notice, this number is not necessarily correlated with the number breaking changes (some breaking changes are exposed by many type regressions)._ 
* __Total clients__: Total number of clients used in the benchmark

There may be multiple entries for each tool.
In particular, for the benchmarks where NoRegrets finds clients, we include a run of NoRegrets+ where NoRegrets+ uses the same clients as NoRegrets, i.e., __with unconstrained clients__ set to false, such that we can compare the running time of NoRegrets+ and NoRegrets using the exact same set of clients. 


#### Benchmark results view
Clicking on the tool name in one of the rows will take you to the benchmark results view, which lists the results for each version of the library.

At the top of the page there is a configuration area, where prototype actions can be enabled/disabled, and coverage details for each client can be enabled/disabled.
Both options are disabled by default since it improves readability.

The *library@version* entries for which type regressions were detected are colored red.
Clicking on a *library@version* pair will bring forward a table of client-related details, followed by some coverage information if available, followed by the actual type regressions if any, for that specific version of the library.

###### Client details table
The client details table has a row for each client used in the benchmark.
The columns of the table are as follows:

* __Client__: Name and version of the client
* __Tool execution time__: The time it takes for NoRegrets+ to run the type regression testing phase (last phase of [NoRegrets+ pipeline](#pipeline)), or, for NoRegrets benchmarks, the time it takes to generate the API models.
* __Model size (paths)__: The model size is the number of paths in π (See the [paper](papers/ESECFSE19.pdf) for a description of π)
* __Compressed model size__: The size of the model after the compression (The percentage shrunk is in parentheses)
* __Statement coverage__: The Statement coverage for each library file loaded during the testing (only if __Hide the coverage for individual clients__ is unchecked.)
* __Model size/Client size__: Shows either the size of the model in bytes for NoRegrets+ clients, or the size of the actual clients code for NoRegrets.

###### Coverage information
The coverage information section contains the aggregated coverage across all the clients.
First, the coverage for every file is listed, followed by the total coverage across all files, which is listed in boldface.
The aggregated coverage is calculated as the union of all the statements covered by all the clients.
For example, if client A has covered statement 1 and 2, and client B statement 3 and 4 of a library consisting of 4 statements, then the total statement coverage is 100%. Whereas if client B instead covered statements 2 and 3, then the total coverage would be 75%.

###### Type Regressions
At last, any type regressions found in the update are listed in a table.
The first column contains the access path on which the regression was observed, the second column contains the violation of type relation, and the third column lists all of the involved clients.
For NoRegrets the involved clients are the clients for which the model changed in the update in such a way that the type regression was exposed. For NoRegrets+ the involved clients are the clients whose models exposed the type regression when NoRegrets+ ran the type regression testing phase.

NoRegrets+ and NoRegrets have slightly different models, so the paths look different even when they refer to type regressions exposing the same breaking change. We refer to the [paper](papers/ESECFSE19.pdf) for a more detailed description of the models. 

#### Compare running time
<a name="compare"></a>
From the [results overview](#results-overview), it is possible to select two benchmarks for comparison.
Pick a library, then select two benchmarks by clicking on the rows, and then click the compare button.
Notice, that it only makes sense to compare benchmarks with the same number of clients, with coverage disabled (measuring coverage is going to slow down both NoRegrets+ and NoRegrets considerably), and with different tools (NoRegrets+ in one configuration and NoRegrets in the other).
This will open a view where every client that is in both benchmarks, and which does not crash in either of the benchmarks, is listed.
For every client, the running time of the model generation phase (NoRegrets) or the type regression testing phase (NoRegrets+) is listed, followed by the ratio (fastest/slowest).
The average for all the clients is listed in the bottom (this is the number that appears in Table 1 of the [paper](papers/ESECFSE19.pdf)). 

<a name="interpret-results"></a>
# 3. Interpreting results
The artifact comes preloaded with the result files from the benchmarks used in the [paper](papers/ESECFSE19.pdf).
Rerunning all of the benchmarks is going to take several weeks even on a fast computer since NoRegrets+ and NoRegrets have to run on thousands of updates sometimes using hundreds of models or clients.
In the [Running benchmarks](#running-benchmarks) section, we show how to reproduce all of the results, but we also provide a guide on how to reproduce a smaller subset of the results such that the reader can confirm that NoRegrets+ works as expected within a reasonable amount of time.
_Notice, if you have already run NoRegrets+ as described in [Section 4](#running-benchmarks), then you may have overwritten some of the preloaded results_

A couple of bug fixes and some adjustments to the Web-UI have made it necessary to recompute the results since the submission of the paper in February 2019. 
Therefore there may be some discrepancies between the runtime difference of NoRegrets+ and NoRegrets reported in the paper and the runtime difference shown in the UI. 
For the benchmarks with many clients, the speed-up ratio remains more or less the same, but for some of the benchmarks with few clients, the ratio has changed substantially.
For example, _aws-sdk_ went from 6.57x to 13.92x, _mime_ went from 27.19x to 3.85x, and _mysql_ from 11.89x to 166.84x.
We will change the paper to reflect these changes.

The web-UI provides all the necessary data to reconstruct Table 1 from the [paper](papers/ESECFSE19.pdf).
Most of the columns have a one-to-one correspondence with a column in the web-UI, and therefore does not require further explanation.
The columns that are non-trivial are coverage, BC, and speed-up. 

#### Coverage
The coverage is computed on the first post-update version of a given benchmark, e.g., the _lodash_ benchmark starts at version _3.0.0_ and the next version is _3.0.1_, so the coverage is computed on _3.0.1_.
Computing the coverage of each benchmark is not completely trivial.
For example, some libraries contain both a single file with the full library, and the library partitioned into a set of files.   
The benefit of this approach is that clients that only require parts of the library do not need to load the full library, which improves performance for those clients.   
For example, _lodash_ includes both the `index.js` file containing the full library and every public function as a separate file in the subfolders `array`, `function`, etc.
For libraries with this property, we take the conservative approach of only counting the coverage of the full library file.

#### BC
We find the breaking changes by manual inspection of the type regressions reported in each minor and patch update.
Notice, that multiple type regressions may point to the same breaking change, and that the same type regression may be reported by multiple clients, e.g., _debug<span>@<span>2.4.0_.  
It is a laborious task to understand the root cause of all the type regressions, especially, if you are unfamiliar with the implementation details of the affected library.
Using a [single client-library test](#single-test), one may gain a deeper understanding of the type regression by attaching a debugger to NoRegrets+, and then investigating the library state at the point where the type regression is reported.
NoRegrets+ also contains a [version-diff tool](#version-diff) that may help debug the cause of type-regressions. 
We encourage interested readers to use these tools to investigate and understand the breaking changes mentioned in the Motivating example and Case studies of the [paper](papers/ESECFSE19.pdf). 

#### Speed-up
We compute the speed-up ratio by running NoRegrets and NoRegrets+ using the same set of clients, and then compare the running times of NoRegrets+'s type regression testing phase with NoRegrets' model generation phase.
We use only the clients where both NoRegrets' model generation phase and NoRegrets+'s type regression testing phase terminate successfully without any errors (notice, errors here do not include type-regressions).
The web-UI tool has a feature for automatically computing this ratio (see [Compare running time](#compare)).

<a name="running-benchmarks"></a>
# 4. Reproducing paper results
The evaluation in the [paper](papers/ESECFSE19.pdf) is based on runs of 25 benchmarks, where a benchmark represents a run of NoRegrets+ on a sequence of library updates using models based on the first version in this sequence.
For some of the 25 benchmarks, multiple runs with different configurations are necessary to reproduce all of the results.
For example, for the benchmarks where we compare with the NoRegrets tool, we do the speed comparison with the coverage collection disabled since collecting coverage slows down both tools.

There are two steps required to run a benchmark.
Say, for example, that the library is _big-integer_, it starts at version _1.0.0_, and we want to test every version between _1.0.0_ and _1.6.4_ (like in the benchmarks of the [paper](papers/ESECFSE19.pdf)).
First, the client retrieval phase must be run to extract the set of clients that NoRegrets+ will use to generate models.
Second, the remaining phases of the [NoRegrets+ pipeline](#pipeline) are initiated by a single command, which also automatically feeds the type regression testing phase with all versions of _big-integer_ between _1.0.0_ and _1.6.4_.

The benchmarks are specified in the file [backend/src/test/scala-2.11/TestEntries.scala](backend/src/test/scala-2.11/TestEntries.scala).
Notice, that this file already contains an entry for each of the benchmarks from the paper.
For example, the entry below defines a benchmark named top1000-big-integer<span>@<span>1.0.0 that starts with _big-integer_ version _1.0.0_ and contains every version up to and including _1.6.4_.

```
"top1000-big-integer@1.0.0" should "run" in {
  perform("big-integer@1.0.0", Some("1.6.40"))
}
```
To initiate the client retrieval for this benchmark run:

```
sbt -batch "project backend" "test:testOnly FindSuccessfulRegenerateNoRegretsPlusWithDependentsRegen -- -z top1000-big-integer@1.0.0"
```
This command and all the following commands should be run from the root of the NoRegrets+ project.
Running this command is going to fetch a set of clients and save them in the caching folder.
Computing the client set is going to take a long time, and requires around 50GB of memory to load the npm registry metadata.
However, we have already included the set of clients in the cache folder, so this part can be skipped for users lacking sufficient memory. 

Afterward, the remaining phases of NoRegrets+ are started on the benchmark by running

```
sbt -batch "project backend" "test:testOnly FullCycleRegenerateNoRegretsPlusUnconstrained -- -z top1000-big-integer@1.0.0"
```
Or alternatively, add `WithCoverage` to the end of the command to also record coverage.

```
sbt -batch "project backend" "test:testOnly FullCycleRegenerateNoRegretsPlusUnconstrainedWithCoverage -- -z top1000-big-integer@1.0.0"
```
These commands should take approximately 15 minutes to run.
We also recommend running this command on machines with at least 12GB of memory.

Afterward, the results will automatically appear in the [Web-UI](#experimental-results-web-UI).

To run the benchmarks using only the used with NoRegrets, remove the `Unconstrained` at the end of the commands.

To reproduce the results from the paper, run the benchmarks _debug_, _lodash_, _async_, _express_, _moment_, _mime_, _aws-sdk, _mysql_, _joi_, _minimatch_, and _autoprefixer_ both with `coverage` and `unconstrained` and without `coverage` and `unconstrained`.
The latter case is necessary to do the speed comparison with NoRegrets.
For the remaining benchmarks, no comparison with NoRegrets is necessary, so they should just be run with coverage and unconstrained.
The results from the client retrieval phase have already been computed for all the included benchmarks, so there is no need to run the `FindSuccessful` command.
Notice, that some benchmarks may require more than 12GB of memory. 
In the evaluation of the paper, we used a machine with 80GB of memory.


<a name="single-test"></a>
### Single client/library pair test.
It is also possible to run NoRegrets+ with only a single client on a single update.
This feature is often useful for debugging the root cause of a type regressions after having run a full benchmark.

Single client NoRegrets+ runs are specified in the file [backend/src/test/scala-2.11/Debugging.scala](backend/src/test/scala-2.11/Debugging.scala).
For example, to reproduce the type regression from the motivating example in the [paper](papers/ESECFSE19.pdf), add the following entry to the Debugging class:

```
"big-integer-against-deposit-iban" should "run" in {
  runEvolution("big-integer@1.4.6", List("1.4.7"), "deposit-iban@1.1.0", { diff =>
    true
  }, NoRegretsPlusMode = true, withCoverage = false)
}
```
This creates a test _big-integer-against-deposit-iban_, which runs on the update of _big-integer<span>@<span>1.4.6_ to version _1.4.7_ using the client _deposit-iban<span>@<span>1.1.0_.

The following command runs the test

```
 sbt -batch "project backend" "test:testOnly Debugging -- -z big-integer-against-deposit-iban"
```

If you look at the terminal output, you will see that NoRegrets+ at some point outputs the following line

```
node ./build/test-runner/src/index.js -m /home/NoRegretsPlus/NoRegretsPlus/out-type-regression/debugging/big-integer@1.4.6_1.4.7_deposit-iban@1.1.0/_auxClient-deposit-iban@1.1.0/model.json -n big-integer --libraryVersion 1.4.6 --coverage false -f /home/NoRegrets+/NoRegrets+/out-type-regression/debugging/big-integer@1.4.6_1.4.7_deposit-iban@1.1.0/_auxClient-deposit-iban@1.1.0 --exactValueChecking false
```
This is the command that initiates the model processing.
Sometimes it is useful to attach a debugger to this phase to better understand the cause of a type regression.
Move into the test-runner folder.

```
cd api-inference/test-runner
```
Then repeat the command with the `--inspect-brk` option passed to node
```
node --inspect-brk ./build/test-runner/src/index.js -m /home/NoRegretsPlus/NoRegretsPlus/out-type-regression/debugging/big-integer@1.4.6_1.4.7_deposit-iban@1.1.0/_auxClient-deposit-iban@1.1.0/model.json -n big-integer --libraryVersion 1.4.6 --coverage false -f /home/NoRegrets+/NoRegrets+/out-type-regression/debugging/big-integer@1.4.6_1.4.7_deposit-iban@1.1.0/_auxClient-deposit-iban@1.1.0 --exactValueChecking false
```
Open chrome and enter `chrome://inspect` in the navigation bar.
Click the inspect link below Target (v8.10.0), which will open the debugger window.
It may be helpful to put a breakpoint at the `addTypeRegression` function in the `testResult.ts` file since this will stop NoRegrets+ every time a type regression is encountered.



<a name="version-diff"></a>
#### Version diff
NoRegrets+ includes a version diff tool that is sometimes useful for debugging the root causes of type regressions.
For example, to compare _big-integer_ version _1.4.6_ with version _1.4.7_ write:

```
./npmcli source-diff big-integer 1.4.6 1.4.7
```
This will open a browser window with a diff-view between version _1.4.6_ and _1.4.7_ of _big-integer_.

