export class Constants {
    static DEBUG = true;

    static PROXY_ID_KEY = '___getProxyID';
    static FUNCTION_CALL = '___[invoke]';
    static METHOD_CALL = '___[method-call]';
    static CONSTRUCTOR_CALL = '___[new]';
    static RECEIVER: string = '___[receiver]';
    static PROPERTY_BLACKLIST: string = '___[blacklist]';
    static ANY = '___[any]';
    static GLOBAL = '___[global]';
    static ARRAY_ACCESS = '___[access]';
    static PROTOTYPE_ACCESS = '___[prototype]';
    static MODULE_IDENTIFIER = 'module';
    static GET_HANDLER = '___[getHandler]';
    static GET_IS_NATIVE = '___[getIsNative]';
    static PATH_DELIMITER = '.';

    static IS_PROXY_KEY = Symbol("proxy-key");
    static GET_PROXY_TARGET = "proxy-target";
    static PROTO_SYM = Symbol("__proto__");
    static GET_OWN_PROPERTY_DESCRIPTOR = Symbol("get-own-property-descriptor");
    static HAS = Symbol("has");
    static CONSTRUCT = Symbol("construct");

    static CIRC_TYPE = "◦";
}

if(Constants.DEBUG) {
  (global as any).ProxyConstants = Constants;
}