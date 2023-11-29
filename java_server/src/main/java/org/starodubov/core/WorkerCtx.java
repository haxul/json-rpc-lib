package org.starodubov.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.starodubov.reqhandler.JsonRpcMethod;
import org.starodubov.validator.JsonRpcValidator;

import java.util.Map;

public record WorkerCtx(
        Map<String, JsonRpcMethod<?>> methodMap,
        ObjectMapper jsonMapper,
        JsonRpcValidator validator
) {
}
