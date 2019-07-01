import {EventEmitter} from 'events';

import {subType, UnionType} from '../../API-tracer/src/runtime-types';
import {getRuntimeType} from '../../API-tracer/src/runtime-types-operations';

const assert = require('chai').assert;

describe('TypeChecking', function() {
  describe('#getRuntimeType', function() {
    it('should behave as expected for primitives', function() {
      assert.equal(getRuntimeType(42), 'number');
      assert.equal(getRuntimeType(true), 'boolean');
      assert.equal(getRuntimeType('foo'), 'string');
      assert.equal(getRuntimeType(undefined), 'undefined');
      assert.equal(getRuntimeType(Symbol('foo')), 'symbol');
    });

    it('should behave as expected for known types', function() {
      assert.equal(getRuntimeType({}), 'object');
      assert.equal(getRuntimeType(new EventEmitter()), 'EventEmitter');
      assert.equal(getRuntimeType(function() {}), 'function');
    });

    it('should only report the type of the first object in the prototype chain',
       function() {
         var o: any = {};
         o.__proto__ = new EventEmitter();
         assert.equal(getRuntimeType(o), 'object');
       })
  });

  describe('#subType', function() {
    it('should return true when rt1 is a subtype of rt2', function() {
      assert.isTrue(subType('Map', 'object'), 'Map <: object');
      assert.isTrue(subType('function', 'object'), 'function <: object');
      assert.isTrue(subType('function', 'function'), 'function <: function');
      assert.isFalse(subType('object', 'function'), 'function !<: object');
    })
  });

  describe('#UnionType.subType', function() {
    it('should return true when rt1 is a subtype of rt2', function() {
      const u1 = new UnionType();
      u1.addType('Set');
      u1.addType('Map');
      assert.isTrue(u1.subType('Map'), 'Map <: Map');
      assert.isTrue(u1.subType('Set'), 'Set <: Set');
      assert.isFalse(u1.subType('EventEmitter'),
                     'EventEmitter !<: Set v EventEmitter');

      const u2 = new UnionType();
      u2.addType('object');
      assert.isTrue(u1.subType('Map'), 'Map <: object');
    })
  });
});
