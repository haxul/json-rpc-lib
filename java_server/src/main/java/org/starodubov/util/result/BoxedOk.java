package org.starodubov.util.result;

public record BoxedOk<T>(T value) implements Result {
}
