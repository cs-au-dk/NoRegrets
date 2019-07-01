"use strict";
var __extends = (this && this.__extends) || (function () {
    var extendStatics = Object.setPrototypeOf ||
        ({ __proto__: [] } instanceof Array && function (d, b) { d.__proto__ = b; }) ||
        function (d, b) { for (var p in b) if (b.hasOwnProperty(p)) d[p] = b[p]; };
    return function (d, b) {
        extendStatics(d, b);
        function __() { this.constructor = d; }
        d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
var paths_1 = require("../../../../api-inference/API-tracer/src/paths");
var KnowledgeDiff = (function () {
    function KnowledgeDiff(paths) {
        this.paths = paths;
    }
    KnowledgeDiff.fromJson = function (arr) {
        var paths = [];
        for (var _i = 0, _a = arr.pathStrs; _i < _a.length; _i++) {
            var pair = _a[_i];
            paths.push([paths_1.AccessPath.fromJson(pair["_1"]), TypeDiff.fromJson(pair["_2"])]);
        }
        return new KnowledgeDiff(paths);
    };
    return KnowledgeDiff;
}());
exports.KnowledgeDiff = KnowledgeDiff;
var TypeDiff = (function () {
    function TypeDiff() {
    }
    TypeDiff.fromJson = function (i) {
        if (i.jsonClass === "NoObservationInPost") {
            return NoObservationInPost.fromJson(i);
        }
        else if (i.jsonClass === "NoObservationInBase") {
            return NoObservationInBase.fromJson(i);
        }
        else if (i.jsonClass === "DifferentObservations") {
            return DifferentObservations.fromJson(i);
        }
        throw new Error("Unexpected " + i.jsonClass + " from ");
    };
    return TypeDiff;
}());
exports.TypeDiff = TypeDiff;
var NoObservationInPost = (function (_super) {
    __extends(NoObservationInPost, _super);
    function NoObservationInPost(obs) {
        var _this = _super.call(this) || this;
        _this.obs = obs;
        return _this;
    }
    NoObservationInPost.fromJson = function (i) {
        return new NoObservationInPost(i.obs);
    };
    NoObservationInPost.prototype.toString = function () {
        return "no-observation-in-post";
    };
    return NoObservationInPost;
}(TypeDiff));
exports.NoObservationInPost = NoObservationInPost;
var NoObservationInBase = (function (_super) {
    __extends(NoObservationInBase, _super);
    function NoObservationInBase(obs) {
        var _this = _super.call(this) || this;
        _this.obs = obs;
        return _this;
    }
    NoObservationInBase.fromJson = function (i) {
        return new NoObservationInBase(i.obs);
    };
    NoObservationInBase.prototype.toString = function () {
        return "no-observation-in-base";
    };
    return NoObservationInBase;
}(TypeDiff));
exports.NoObservationInBase = NoObservationInBase;
var DifferentObservations = (function (_super) {
    __extends(DifferentObservations, _super);
    function DifferentObservations(types) {
        var _this = _super.call(this) || this;
        _this.types = types;
        return _this;
    }
    DifferentObservations.fromJson = function (i) {
        var o = {};
        for (var type in i.types) {
            o[type] = TypeDiff.fromJson(i.types[type]);
        }
        return new DifferentObservations(o);
    };
    DifferentObservations.prototype.toString = function () {
        var s = "";
        for (var p in this.types) {
            s += p.toString() + ": " + this.types[p].toString() + ",";
        }
        return s;
    };
    return DifferentObservations;
}(TypeDiff));
exports.DifferentObservations = DifferentObservations;
