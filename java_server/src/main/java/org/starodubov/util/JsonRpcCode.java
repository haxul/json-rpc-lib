package org.starodubov.util;

/*
-32700 	Parse error 	Invalid JSON was received by the server. An error occurred on the server while parsing the JSON text.
-32600 	Invalid Request 	The JSON sent is not a valid Request object.
-32601 	Method not found 	The method does not exist / is not available.
-32602 	Invalid params 	Invalid method parameter(s).
-32603 	Internal error 	Internal JSON-RPC error.
-32000 to -32099 	Server error 	Reserved for implementation-defined server-errors.
 */
public enum JsonRpcCode {
    SUCCESS(0, "Parse error"),
    INTERNAL_ERR(-32603, "Internal Error"),
    METHOD_NOT_FOUND(-32601, "Method not found"),
    INVALID_PARAMS(-32602, "Invalid Params"),
    INVALID_REQ(-32600, "Invalid Request"),
    PARSE_ERR(-32700, "Parse error");

    private final int intVal;
    private final String title;

    public int intVal() {
        return intVal;
    }

    public String title() {
        return title;
    }

    public boolean isErr() {
        return this != SUCCESS;
    }

    JsonRpcCode(int intVal, String title) {
        this.intVal = intVal;
        this.title = title;
    }
}
