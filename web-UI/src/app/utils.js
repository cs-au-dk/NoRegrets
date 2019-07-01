"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
function _getEntries(c, sorter) {
    var entries = [];
    for (var k in c) {
        entries.push({ key: k, value: c[k] });
    }
    if (sorter !== undefined) {
        return entries.sort(sorter);
    }
    return entries;
}
exports._getEntries = _getEntries;
function verToNum(s) {
    var v = s.split("@")[1];
    var revs = v.split(".");
    return revs[0] * 100000 + revs[1] * 100 + revs[2];
}
exports.verToNum = verToNum;
