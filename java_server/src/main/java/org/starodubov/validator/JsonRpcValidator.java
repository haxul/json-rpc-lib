package org.starodubov.validator;

import com.fasterxml.jackson.databind.JsonNode;

public interface JsonRpcValidator {
    ValidateResult validate(final JsonNode node);
}
