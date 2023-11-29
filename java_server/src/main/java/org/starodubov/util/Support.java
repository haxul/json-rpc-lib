package org.starodubov.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Socket;
import java.util.function.Supplier;

public class Support {

    private static final Logger log = LoggerFactory.getLogger(Support.class);

    public static void state(final Supplier<Boolean> fn, final String msg) {
        if (!fn.get()) throw new IllegalStateException(msg);
    }

    public static void close(final Socket socket) {
        if (socket == null) return;
        try {
            socket.close();
        } catch (Exception e) {
            log.error("close token err", e);
        }
    }
}
