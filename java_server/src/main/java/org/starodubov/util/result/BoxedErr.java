package org.starodubov.util.result;

public record BoxedErr<E>(E value) implements Result {
}
