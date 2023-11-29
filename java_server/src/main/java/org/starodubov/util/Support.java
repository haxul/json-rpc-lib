package org.starodubov.util;

import java.util.function.Supplier;

public class Support {

    public static void state(final Supplier<Boolean> fn, final String msg) {
        if (!fn.get()) throw new IllegalStateException(msg);
    }
}
