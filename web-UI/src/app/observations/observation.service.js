"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
exports.__esModule = true;
var core_1 = require("@angular/core");
var ObservationService = /** @class */ (function () {
    function ObservationService(http) {
        this.http = http;
        this.headers = new Headers({ 'Content-Type': 'application/json' });
        this.baseUrl = 'caching/';
    }
    ObservationService.prototype.getObservations = function (key) {
        return this.http.get(this.baseUrl + key + ".json").map(function (response) {
            return response.json();
        });
    };
    ObservationService = __decorate([
        core_1.Injectable()
    ], ObservationService);
    return ObservationService;
}());
exports.ObservationService = ObservationService;
//# sourceMappingURL=observation.service.js.map