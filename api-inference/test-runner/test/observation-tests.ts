import {
  AccessPath,
  ApplicationLabel,
  ArgLabel,
  PropertyLabel,
  RequireLabel,
  WriteLabel
} from "../../API-tracer/src/paths";

const assert = require('chai').assert;
const fs = require("fs");

describe('AccessPath', function() {
  describe("#isCovariant", function() {
    it("should return true if the path does not contain any argument actions",
       function() {
         const ap = new AccessPath(
             new RequireLabel("lib"),
             new AccessPath(new PropertyLabel("bla"), undefined));
         assert.isTrue(ap.isCovariant());
       });
    it("should return false if the path contains an uneven number of argument actions",
       function() {
         const ap = new AccessPath(
             new RequireLabel("lib"),
             new AccessPath(new PropertyLabel("bla"),
                            new AccessPath(new ArgLabel(0), undefined)));
         assert.isFalse(ap.isCovariant());
       });
    it("should return true if the path contains an even number of argument actions",
       function() {
         const ap = new AccessPath(
             new RequireLabel("lib"),
             new AccessPath(
                 new PropertyLabel("bla"),
                 new AccessPath(new ArgLabel(0),
                                new AccessPath(new ArgLabel(1),
                                               new AccessPath(new ArgLabel(2),
                                                              undefined)))));
         assert.isFalse(ap.isCovariant());
       });
  });

  describe("#pathDiff", function() {
    it("should return the number of diff labels between two pathStrs", function() {
      const ap1 = new AccessPath(new RequireLabel("lib"), undefined);
      const ap2 = ap1.extendWithLabel(new PropertyLabel("bla"));

      assert.equal(ap1.diff(ap2), 1);
      assert.equal(ap2.diff(ap1), 1);
    });

    it("should return the number of diff labels between two complicated pathStrs",
       function() {
         // const ap1 = new AccessPath(new RequireLabel("lib"),
         //  new AccessPath(new PropertyLabel("bla"),
         //    new AccessPath(new ApplicationLabel(1, false, 0),
         //      new AccessPath(new ArgLabel(0),
         //        new AccessPath(new PropertyLabel("foo"), undefined)))));

         const ap1 = new AccessPath(new RequireLabel("lib"), undefined)
                         .extendWithLabel(new PropertyLabel("bla"))
                         .extendWithLabel(new ApplicationLabel(1, false, 0));

         const ap2 = ap1.extendWithLabel(new ArgLabel(0))
                         .extendWithLabel(new PropertyLabel("foo"));

         assert.equal(ap1.diff(ap2), 2);
         assert.equal(ap2.diff(ap1), 2);
       });

    it("should return 0 when a path is diffed agains itself", function() {
      const ap = new AccessPath(new RequireLabel("lib"), undefined)
                     .extendWithLabel(new PropertyLabel("foo"));

      assert.equal(ap.diff(ap), 0);
      assert.equal(ap.diff(ap), 0);
    });

    it("should return -1 if one path is not a prefix of the other", function() {
      const ap1 = new AccessPath(new RequireLabel("lib"), undefined)
                      .extendWithLabel(new PropertyLabel("bla"));
      const ap2 = new AccessPath(new RequireLabel("lib"), undefined)
                      .extendWithLabel(new PropertyLabel("foo"));

      assert.equal(ap1.diff(ap2), -1);
      assert.equal(ap2.diff(ap1), -1)
    });
  });

  describe("#isReceiverPath", function() {
    it("should return true for pathStrs ending with this", () => {
      const ap = new AccessPath(new RequireLabel("lib"), undefined)
                     .extendWithLabel(new PropertyLabel("this"));
      assert(ap.isReceiverPath());
    });

    it("should return true for pathStrs containing this", () => {
      const ap = new AccessPath(new RequireLabel("lib"), undefined)
                     .extendWithLabel(new PropertyLabel("this"))
                     .extendWithLabel(new PropertyLabel("foobar"));
      assert(ap.isReceiverPath);
    });

    it("should return false for pathStrs without this", () => {
      const ap = new AccessPath(new RequireLabel("lib"), undefined)
                     .extendWithLabel(new PropertyLabel("foobar"));
      assert(!ap.isReceiverPath());
    });
  });

  describe("#isWritePath", function() {
    it("should return true for pathStrs containing write labels", () => {
      const ap = new AccessPath(new RequireLabel("lib"), undefined)
                     .extendWithLabel(new WriteLabel("foobar", 42))
                     .extendWithLabel(new PropertyLabel("foobar"));
      assert(ap.isWritePath());
    });

    it("should return false for pathStrs not containing write labels", () => {
      const ap = new AccessPath(new RequireLabel("lib"), undefined)
                     .extendWithLabel(new PropertyLabel("foobar"));
      assert(!ap.isWritePath());
    });
  });
});

describe('label', function() {
  describe("#getProperty", function() {
    it("should restore built-in symbols", function() {
      const lab = new PropertyLabel(Symbol.toPrimitive);
      assert.strictEqual(lab.getProperty(), Symbol.toPrimitive);
    });

    it("should behave as the identity function for non-symbol properties",
       function() {
         const lab = new PropertyLabel("foobar");
         assert.strictEqual(lab.getProperty(), "foobar");
       });
  });
});
