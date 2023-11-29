package org.starodubov.validator;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.starodubov.util.JsonRpcCode.INVALID_REQ;
import static org.starodubov.validator.ValidateResult.VALID;

public class JsonRpc2VerValidator implements JsonRpcValidator {

    private final Logger log = LoggerFactory.getLogger(JsonRpc2VerValidator.class);

    public ValidateResult validate(final JsonNode jsonNode) {
        if (jsonNode == null) {
            return new ValidateResult(INVALID_REQ, "json is null");
        }
        if (jsonNode.get("id") == null) {
            return new ValidateResult(INVALID_REQ, "id field is not found");
        }

        final JsonNode jsonrpc = jsonNode.get("jsonrpc");
        if (jsonrpc == null) {
            return new ValidateResult(INVALID_REQ, "jsonrpc field is null");
        }

        if (!jsonrpc.asText().equals("2.0")) {
            return new ValidateResult(INVALID_REQ, "unsupported json rpc version");
        }

        final JsonNode method = jsonNode.get("method");
        if (method == null) {
            return new ValidateResult(INVALID_REQ, "method field is not found");
        }

        if (method.asText().isBlank()) {
            return new ValidateResult(INVALID_REQ, "method field is blank");
        }

        return VALID;
    }
}
