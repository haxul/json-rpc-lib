package org.starodubov.validator;

import org.starodubov.util.JsonRpcCode;

import static org.starodubov.util.JsonRpcCode.SUCCESS;

public record ValidateResult(JsonRpcCode res, String errMsg) {
    public static final ValidateResult VALID = new ValidateResult(SUCCESS, null);
}
