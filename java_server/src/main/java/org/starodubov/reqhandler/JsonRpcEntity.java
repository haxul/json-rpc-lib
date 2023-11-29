package org.starodubov.reqhandler;

import org.starodubov.util.JsonRpcCode;

public record JsonRpcEntity<A>(JsonRpcCode code, A result, String err) {
    public JsonRpcEntity(JsonRpcCode code, A result) {
        this(code, result, null);
    }

    public JsonRpcEntity(JsonRpcCode code, String err) {
        this(code, null, err);
    }

    public boolean hasErr() {
        return this.err != null;
    }
}
