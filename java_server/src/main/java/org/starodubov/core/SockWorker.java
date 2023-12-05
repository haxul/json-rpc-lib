package org.starodubov.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.starodubov.reqhandler.JsonRpcEntity;
import org.starodubov.reqhandler.JsonRpcMethod;
import org.starodubov.util.JsonRpcCode;
import org.starodubov.util.result.*;
import org.starodubov.validator.ValidateResult;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

import static org.starodubov.util.Constant.EOF;
import static org.starodubov.util.JsonRpcCode.*;
import static org.starodubov.util.Support.*;

public class SockWorker implements Runnable {

    private final static Logger log = LoggerFactory.getLogger(SockWorker.class);
    private final Socket socket;
    private final BufferedInputStream in;
    private final BufferedOutputStream out;
    private final WorkerCtx ctx;
    private final ObjectMapper mapper;
    private int errCount = 0;

    public SockWorker(final Socket socket, final WorkerCtx ctx) {
        setKeepAlive(socket);
        this.socket = socket;
        this.in = getIn(socket);
        this.out = getOut(socket);
        this.ctx = ctx;
        this.mapper = ctx.jsonMapper();
    }

    private BufferedInputStream getIn(final Socket socket) {
        try {
            return new BufferedInputStream(socket.getInputStream());
        } catch (IOException e) {
            log.error("sock input stream err", e);
            throw new RuntimeException(e);
        }
    }

    private BufferedOutputStream getOut(final Socket socket) {
        try {
            return new BufferedOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            log.error("sock output stream err", e);
            throw new RuntimeException(e);
        }
    }

    private void setKeepAlive(final Socket socket) {
        try {
            socket.setKeepAlive(true);
        } catch (SocketException e) {
            log.error("cannot set keep alive for socket '{}'", socket);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        log.debug("new json-rpc socket: {}", socket);
        final byte[] buff = new byte[8 * 1024];
        JsonNode req;
        for (; ; ) {
            try {
                final int len = in.read(buff);
                if (len == EOF) {
                    close(socket);
                    return;
                }
                final int commaIdx = netstringCommaIdx(buff);
                if (commaIdx == -1) {
                    writeFailResponse(PARSE_ERR);
                    continue;
                }

                final int netstringLen = Integer.parseInt(new String(buff, 0, commaIdx));
                req = jsonTokenerParse(ctx.jsonMapper(), buff, commaIdx + 1, netstringLen);
                if (req == null) {
                    writeFailResponse(PARSE_ERR);
                    continue;
                }

                log.debug("json-rpc request: '{}'", req);

                final Result handleResult = doHandleReq(req);
                switch (handleResult) {
                    case BoxedOk(JsonRpcEntity<?> entity) -> {
                        //SUCCESS:  метод с таким названием существует, формат соответствует json-rpc 2.0
                        final byte[] respBytes = entity.hasErr() ?
                                // Пользователь вернул ошибку
                                ctx.jsonMapper().writeValueAsBytes(buildFailResponse(mapper, req, entity))
                                // Пользователь вернул успешный ответ
                                : ctx.jsonMapper().writeValueAsBytes(buildSuccessResponse(mapper, req, entity.result()));
                        log.debug("response: {}", new String(respBytes));
                        writeAsNetstring(out, respBytes);
                    }
                    case BoxedErr(JsonRpcCode code) when code != SUCCESS -> {
                        final JsonNode failResp =
                                buildFailResponse(mapper, req, JsonRpcEntity.fail(code));
                        log.debug("fail response: {}", failResp);
                        writeAsNetstring(out, mapper.writeValueAsBytes(failResp));
                    }
                    default -> throw new IllegalStateException("Unexpected value: " + handleResult);
                }
            } catch (SocketException e) {
                close(socket);
                return;
            } catch (Exception e) {
                log.error("internal err", e);
                if (errCount++ > 3) {
                    log.error("too much unexpected errors. close socket: {}", socket);
                    close(socket);
                }
            }
        }
    }

    private void writeFailResponse(final JsonRpcCode code) {
        try {
            writeAsNetstring(out,
                    mapper.writeValueAsBytes(buildFailResponse(mapper, null, JsonRpcEntity.fail(code))));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private int netstringCommaIdx(final byte[] buff) {
        for (int i = 0; i < 9; i++) {
            if (buff[i] == /*char ':'*/58) {
                return i;
            }
            if (!Character.isDigit(buff[i])) {
                return -1;
            }
        }
        return -1;
    }

    private Result doHandleReq(final JsonNode req) {
        try {
            final ValidateResult validate = ctx.validator().validate(req);
            if (validate.res().isErr()) {
                log.error("validation err: '{}'", validate.errMsg());
                return Err.box(INVALID_REQ);
            }

            final String reqMethod = req.get("method").asText();

            final JsonRpcMethod<?> jsonRpcMethod = ctx.methodMap().get(reqMethod);
            if (jsonRpcMethod == null) {
                log.error("method '{}' is not found", reqMethod);
                return Err.box(METHOD_NOT_FOUND);
            }

            if (req.get("params") == null) {
                return Ok.box(jsonRpcMethod.doMethod());
            }

            return Ok.box(jsonRpcMethod.doMethod(req.get("params")));
        } catch (final Exception e) {
            log.error("internal err", e);
            return Err.box(INTERNAL_ERR);
        }
    }
}
