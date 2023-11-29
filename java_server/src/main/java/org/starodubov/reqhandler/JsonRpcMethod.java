package org.starodubov.reqhandler;

import com.fasterxml.jackson.databind.JsonNode;

public interface JsonRpcMethod {

    JsonNode doHandle(final JsonNode json);

    JsonNode doHandle();

    String getMethodName();
}
