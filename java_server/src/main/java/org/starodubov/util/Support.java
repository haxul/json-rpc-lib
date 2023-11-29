package org.starodubov.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Socket;
import java.util.function.Supplier;

public class Support {

    private static final Logger log = LoggerFactory.getLogger(Support.class);

    public static void assertThat(final Supplier<Boolean> fn, final String msg) {
        if (!fn.get()) throw new IllegalStateException(msg);
    }

    public static void close(final Socket socket) {
        if (socket == null) return;

        try {
            socket.close();
            log.info("socket is closed: '{}'", socket);
        } catch (Exception e) {
            log.error("close token err", e);
        }
    }

    public static JsonNode jsonTokenerParse(final ObjectMapper mapper, final byte[] buff, final int len) {
        assertThat(() -> mapper != null, "mapper is  null");
        assertThat(() -> buff != null, "buff is  null");

        try {
            return mapper.readTree(buff, 0, len);
        } catch (Exception e) {
            log.error("parse err", e);
            return null;
        }
    }
}
