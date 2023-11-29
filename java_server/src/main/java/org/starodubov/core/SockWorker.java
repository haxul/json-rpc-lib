package org.starodubov.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.starodubov.reqhandler.JsonRpcEntity;
import org.starodubov.reqhandler.JsonRpcMethod;
import org.starodubov.util.Result;
import org.starodubov.util.JsonRpcCode;
import org.starodubov.validator.ValidateResult;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;

import static org.starodubov.util.Constant.EOF;
import static org.starodubov.util.JsonRpcCode.*;
import static org.starodubov.util.Result.FAIL;
import static org.starodubov.util.Result.OK;
import static org.starodubov.util.Support.*;

public class SockWorker implements Runnable {

    private final static Logger log = LoggerFactory.getLogger(SockWorker.class);
    private final Socket socket;
    private final BufferedInputStream in;
    private final BufferedOutputStream out;
    private final WorkerCtx ctx;
    private final ObjectMapper mapper;

    private record DoHandleReqDto(Result res, String errMsg, JsonRpcCode code,
                                  JsonRpcEntity<?> methodRespBody) {
        public DoHandleReqDto(String errMsg, JsonRpcCode code) {
            this(FAIL, errMsg, code, null);
        }

        public DoHandleReqDto(JsonRpcEntity<?> methodResponse) {
            this(OK, null, SUCCESS, methodResponse);
        }
    }

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
                req = jsonTokenerParse(ctx.jsonMapper(), buff, len);
                if (req == null) {
                    log.debug("parse err: '{}'", req);
                    final var entity = new JsonRpcEntity<>(PARSE_ERR, "Parse error");
                    writeAsNetstring(out, mapper.writeValueAsBytes(buildFailResponse(mapper, null, entity)));
                    continue;
                }

                log.debug("json-rpc request: '{}'", req);

                final DoHandleReqDto handleReq = doHandleReq(req);

                if (handleReq.res() == OK && handleReq.methodRespBody != null) {
                    //SUCCESS:  метод с таким названием существует, формат соответствует json-rpc 2.0
                    if (handleReq.methodRespBody().hasErr()) {
                        // Пользователь вернул успешный ответ
                        final byte[] respBytes = ctx.jsonMapper()
                                .writeValueAsBytes(buildFailResponse(mapper, req, handleReq.methodRespBody()));
                        writeAsNetstring(out, respBytes);
                    } else {
                        // Пользователь вернул ошибку
                        final JsonRpcEntity<?> jsonRpcEntity = handleReq.methodRespBody();
                        final byte[] respBytes = ctx.jsonMapper()
                                .writeValueAsBytes(buildSuccessResponse(mapper, req, jsonRpcEntity.result()));
                        writeAsNetstring(out, respBytes);
                    }
                } else {
                    // ERROR: что то не так с форматом или методом
                    final JsonNode failResp =
                            buildFailResponse(mapper, req, new JsonRpcEntity<>(handleReq.code(), handleReq.errMsg()));
                    writeAsNetstring(out, mapper.writeValueAsBytes(failResp));
                }
            } catch (SocketException e) {
                close(socket);
                return;
            } catch (Exception e) {
                log.error("internal err",  e);
            }
        }
    }

    private DoHandleReqDto doHandleReq(final JsonNode req) {
        try {
            final ValidateResult validate = ctx.validator().validate(req);
            if (validate.res().isErr()) {
                log.error("validation err: '{}'", validate.errMsg());
                return new DoHandleReqDto(validate.errMsg(), INVALID_REQ);
            }

            final String reqMethod = req.get("method").asText();

            final JsonRpcMethod<?> jsonRpcMethod = ctx.methodMap().get(reqMethod);
            if (jsonRpcMethod == null) {
                log.error("method '{}' is not found", reqMethod);
                return new DoHandleReqDto("method is not found", METHOD_NOT_FOUND);
            }

            if (req.get("params") == null) {
                return new DoHandleReqDto(jsonRpcMethod.doMethod());
            }

            return new DoHandleReqDto(jsonRpcMethod.doMethod(req.get("params")));
        } catch (final Exception e) {
            log.error("internal err", e);
            return new DoHandleReqDto("unexpected err", INTERNAL_ERR);
        }
    }
}
