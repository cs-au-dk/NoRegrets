"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var BenchmarksStatus = (function () {
    function BenchmarksStatus(observations, diffKeys) {
        this.observations = observations;
        this.diffKeys = diffKeys;
    }
    BenchmarksStatus.fromJson = function (o) {
        return new BenchmarksStatus(o.observations, o.diffKeys);
    };
    return BenchmarksStatus;
}());
exports.BenchmarksStatus = BenchmarksStatus;
