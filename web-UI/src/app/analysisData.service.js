"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
Object.defineProperty(exports, "__esModule", { value: true });
var core_1 = require("@angular/core");
var status_1 = require("./datastructures/status");
var AnalysisDataService = (function () {
    function BenchmarkStatusService(http) {
        this.http = http;
        this.headers = new Headers({ 'Content-Type': 'application/json' });
        this.statusUrl = '/caching/benchmark-status.json';
    }
    BenchmarkStatusService.prototype.getStatus = function () {
        return this.http.get(this.statusUrl).map(function (response) { return status_1.BenchmarksStatus.fromJson(response.json()); });
    };
    BenchmarkStatusService = __decorate([
        core_1.Injectable()
    ], BenchmarkStatusService);
    return BenchmarkStatusService;
}());
exports.BenchmarkStatusService = AnalysisDataService;
