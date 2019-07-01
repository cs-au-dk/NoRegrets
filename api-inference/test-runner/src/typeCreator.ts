import {EventEmitter} from "events";

import {RuntimeType} from "../../API-tracer/src/runtime-types";

/**
 * This is not a perfect implementation of how object types should be
 * instantiated. We do not currently support intersection types with more than
 * three types but it could be added relatively easily when the need arises.
 * @param type
 */
export class ValueCreator {
  private static winston = require("winston");

  public static createObjectTypedValue(type: RuntimeType): any {
    if (type === "object") {
      return {};
    } else if (type === "EventEmitter") {
      return new EventEmitter();
    } else if (type === "Date") {
      return new Date();
    } else if (type === "IncomingMessage") {
      var httpInc = require("_http_incoming");
      return new httpInc.IncomingMessage();
    } else if (type === "ServerResponse") {
      var httpInc = require("_http_incoming");
      var http = require("http");
      return new http.ServerResponse(new httpInc.IncomingMessage());
    } else if (type === "Error") {
      return new Error("Homemade error");
    } else {
      ValueCreator.winston.warn(
          `Missing implementation of value creation for type in ${type}`);
      return {};
    }
  }
}