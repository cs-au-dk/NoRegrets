"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
Object.defineProperty(exports, "__esModule", { value: true });
var core_1 = require("@angular/core");
var diffs_1 = require("../datastructures/diffs");
var DiffService = (function () {
    function DiffService(http) {
        this.http = http;
        this.headers = new Headers({ 'Content-Type': 'application/json' });
        this.baseUrl = 'caching/';
    }
    DiffService.prototype.getDiff = function (key) {
        return this.http.get(this.baseUrl + key + ".json").map(function (response) {
            var json = response.json();
            for (var _i = 0, _a = Object.getOwnPropertyNames(json); _i < _a.length; _i++) {
                var k = _a[_i];
                json[k] = diffs_1.KnowledgeDiff.fromJson(json[k]);
            }
            return json;
        });
    };
    DiffService = __decorate([
        core_1.Injectable()
    ], DiffService);
    return DiffService;
}());
exports.DiffService = DiffService;
