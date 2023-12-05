package org.starodubov.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.starodubov.reqhandler.JsonRpcEntity;
import org.starodubov.util.result.Err;
import org.starodubov.util.result.Ok;
import org.starodubov.util.result.Result;

import java.io.OutputStream;
import java.net.Socket;
import java.util.Map;

import static org.starodubov.util.Constant.COMMA;

public class Support {

    private static final Logger log = LoggerFactory.getLogger(Support.class);

    public static void assertThat(final boolean exp, final String msg) {
        if (!exp) throw new IllegalStateException(msg);
    }

    public static void close(final Socket socket) {
        if (socket == null) return;
        try {
            socket.close();
            log.info("socket is closed: '{}'", socket);
        } catch (Exception e) {
            log.error("close socket err", e);
        }
    }

    public static JsonNode jsonTokenerParse(final ObjectMapper mapper, final byte[] buff, final int offset, final int len) {
        assertThat(mapper != null, "mapper is  null");
        assertThat(buff != null, "buff is  null");
        if (offset < 0) return null;
        if (len <= 0) return null;

        try {
            return mapper.readTree(buff, offset, len);
        } catch (Exception e) {
            log.error("parse err: '{}'", e.getMessage());
            return null;
        }
    }

    public static JsonNode buildFailResponse(final ObjectMapper mapper, final JsonNode req, final JsonRpcEntity<?> entity) {
        final JsonNode errRes = mapper.createObjectNode()
                .setAll(Map.of(
                        "code", new IntNode(entity.code().intVal()),
                        "message", new TextNode(entity.err())
                ));
        return mapper
                .createObjectNode()
                .setAll(Map.of(
                        "jsonrpc", new TextNode("2.0"),
                        "error", errRes,
                        "id", req == null ? NullNode.getInstance() : req.get("id")
                ));
    }

    public static JsonNode buildSuccessResponse(final ObjectMapper mapper, final JsonNode req, final Object bodyResp) {
        final JsonNode bodyTree = mapper.valueToTree(bodyResp);
        return mapper.createObjectNode()
                .setAll(Map.of(
                        "jsonrpc", new TextNode("2.0"),
                        "result", bodyTree,
                        "id", req.get("id")
                ));
    }

    public static Result writeAsNetstring(final OutputStream out, final byte[] bytes) {
        try {
            out.write("%d:".formatted(bytes.length).getBytes());
            out.write(bytes);
            out.write(COMMA);
            out.flush();
            return Ok.asResult();
        } catch (Exception e) {
            log.error("output write err", e);
            return Err.asResult();
        }
    }
}
