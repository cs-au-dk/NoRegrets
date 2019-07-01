import {ProxyOperations} from "../../API-tracer/src/ProxyOperations";
import {
  NoRegretsProxyHandler
} from "../../API-tracer/src/regression-runtime-checker";

import {createAuxArgumentProxyHandler, createAuxPath} from "./mocks";

const assert = require('chai').assert;
const fs = require("fs");

describe('ProxyOperations', function() {
  describe("#isProxy", function() {
    it("should return false for non-proxies", function() {
      var o = {};
      assert.isFalse(ProxyOperations.isProxy(o));
    });

    it("should return true for ArgumentProxyHandler proxies", function() {
      const o = {};
      var aph = createAuxArgumentProxyHandler();
      var p = new Proxy(o, aph);
      assert.isTrue(ProxyOperations.isProxy(p));
    });

    it("should return true for RegressionRuntimeChecker proxies", function() {
      const o = {};
      const handler = new NoRegretsProxyHandler(null, o, createAuxPath());
      const p = new Proxy(o, handler);
      assert.isTrue(ProxyOperations.isProxy(p));
    });

    it("should return false for objects with prototype proxies", function() {
      var aph = createAuxArgumentProxyHandler();
      var p = new Proxy({}, aph);
      var o = {};
      Object.setPrototypeOf(o, p);
      assert.isFalse(ProxyOperations.isProxy(o));
    });

    it("should return true for objects with prototype proxies if the object is non-extensible",
       function() {
         var aph = createAuxArgumentProxyHandler();
         var p = new Proxy({}, aph);
         var o = {};
         Object.setPrototypeOf(o, p);
         Object.freeze(o);
         assert.isTrue(ProxyOperations.isProxy(o));
       });
  });

  describe("#getTarget", function() {
    it("should return the target for ArgumentProxyHandler proxies", function() {
      const o = {};
      var aph = createAuxArgumentProxyHandler();
      var p = new Proxy(o, aph);
      assert.equal(ProxyOperations.getTarget(p), o);
    });

    it("should return the target for RegressionRuntimeChecker proxies",
       function() {
         const o = {};
         const handler = new NoRegretsProxyHandler(null, o, createAuxPath());
         const p = new Proxy(o, handler);
         assert.equal(ProxyOperations.getTarget(p), o);
       });
  })
});
