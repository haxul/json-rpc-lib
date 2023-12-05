package org.starodubov.reqhandler;

import org.starodubov.util.JsonRpcCode;

import static org.starodubov.util.Support.assertThat;

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

    public static <A> JsonRpcEntity<A> ok(A body) {
        return new JsonRpcEntity<>(JsonRpcCode.SUCCESS, body);
    }

    public static JsonRpcEntity<String> fail(JsonRpcCode code) {
        assertThat(code != JsonRpcCode.SUCCESS, "code must be have error type");

        return new JsonRpcEntity<>(code, code.title());
    }
}
