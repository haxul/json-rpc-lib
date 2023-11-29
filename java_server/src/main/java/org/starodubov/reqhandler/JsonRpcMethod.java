package org.starodubov.reqhandler;

import com.fasterxml.jackson.databind.JsonNode;

public interface JsonRpcMethod<A> {

    default JsonRpcEntity<A> doMethod(final JsonNode json) {
        throw new UnsupportedOperationException("not implemented");
    }

    default JsonRpcEntity<A> doMethod() {
        throw new UnsupportedOperationException("not implemented");
    }

    String methodName();
}
