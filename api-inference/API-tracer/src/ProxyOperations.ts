import * as assert from "assert";
import {none, Option, some} from "ts-option";

import {ArgumentProxyHandler} from "../../test-runner/src/argumentProxyHandler";

import {Constants} from "./constants";
import {AccessPath} from "./paths";
import {NoRegretsProxyHandler} from "./regression-runtime-checker";

export class ProxyOperations {
  public static isProxy(value: any): boolean {
    let type = typeof value;
    return (type == 'object' || type == 'function') && value !== null &&
           !!(Reflect.isExtensible(value)
                  ? Reflect.getOwnPropertyDescriptor(value,
                                                     Constants.IS_PROXY_KEY)
                  : Reflect.get(value, Constants.IS_PROXY_KEY));
  }

  /**
   * Returns the proxy target, i.e., the object that the proxy wraps
   * The function is the identity function if v is not a proxy
   * @param v
   */
  public static getTarget(v: any): any {
    if (this.isProxy(v)) {
      return v[Constants.GET_PROXY_TARGET];
    }
    return v;
  }

  public static getPath(val: any): Option<AccessPath> {
    let vop = none;
    if (this.isProxy(val)) {
      vop = some((val[Constants.GET_HANDLER] as
                  (NoRegretsProxyHandler | ArgumentProxyHandler))
                     .path);
    }
    return vop;
  }

  public static getHandler<T extends ArgumentProxyHandler|
                           NoRegretsProxyHandler>(proxy: any): T {
    assert(this.isProxy(proxy), "Called getHandler on non-proxy value");
    return proxy[Constants.GET_HANDLER] as T;
  }
}