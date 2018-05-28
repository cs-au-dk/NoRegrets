
export class Constants {
    static DEBUG = true;

    static IS_PROXY_KEY = '___isProxy';
    static PROXY_ID_KEY = '___getProxyID';
    static GET_TARGET_KEY = '___getTarget';
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
    static PATH_DELIMITER = '.';

    static PROXY_KEY = Symbol("proxy-key");
    static PROXY_TARGET = Symbol("proxy-target");
    static GET_PROTOTYPE_OF = Symbol("get-prototype-of");
    static GET_OWN_PROPERTY_DESCRIPTOR = Symbol("get-own-property-descriptor");
    static HAS = Symbol("has");
    static CONSTRUCT = Symbol("construct");
}

if(Constants.DEBUG)
    (global as any).ProxyConstants = Constants;