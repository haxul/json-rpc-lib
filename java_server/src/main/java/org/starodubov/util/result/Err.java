package org.starodubov.util.result;

public record Err() implements Result {

    public static Result asResult() {
        return inst;
    }

    public static <E> BoxedErr<E> box(E value) {
        return new BoxedErr<>(value);
    }

    private static final Err inst = new Err();

}
