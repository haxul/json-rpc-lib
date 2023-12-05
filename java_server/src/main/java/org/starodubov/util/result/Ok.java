package org.starodubov.util.result;

public record Ok() implements Result {
    public static Result asResult() {
        return inst;
    }

    public static <T> BoxedOk<T> box(T value) {
        return new BoxedOk<>(value);
    }

    private static final Ok inst = new Ok();
}
