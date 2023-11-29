package org.starodubov.core;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.starodubov.reqhandler.JsonRpcMethod;
import org.starodubov.util.Result;
import org.starodubov.util.JsonRpcCode;
import org.starodubov.validator.ValidateResult;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;

import static org.starodubov.util.Constant.COMMA;
import static org.starodubov.util.Constant.EOF;
import static org.starodubov.util.JsonRpcCode.*;
import static org.starodubov.util.Result.FAIL;
import static org.starodubov.util.Result.OK;
import static org.starodubov.util.Support.close;

public class SockWorker implements Runnable {

    private final static Logger log = LoggerFactory.getLogger(SockWorker.class);
    private final Socket socket;
    private final BufferedInputStream in;
    private final BufferedOutputStream out;
    private final ExecCtx ctx;

    private record DoHandleReqDto(Result res, String errMsg, JsonRpcCode code, JsonNode methodResponse) {
        public DoHandleReqDto(String errMsg, JsonRpcCode code) {
            this(FAIL, errMsg, code, null);
        }

        public DoHandleReqDto(JsonNode methodResponse) {
            this(OK, null, SUCCESS, methodResponse);
        }
    }

    public SockWorker(final Socket socket, final ExecCtx ctx) {
        setKeepAlive(socket);
        this.socket = socket;
        this.in = getIn(socket);
        this.out = getOut(socket);
        this.ctx = ctx;
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
        JsonNode json;
        for (; ; ) {
            try {
                final int len = in.read(buff);
                if (len == EOF) {
                    close(socket);
                    return;
                }
                json = parse(buff, len);
                if (json == null) {
                    log.debug("parse err: '{}'", json);
                    continue;
                }

                log.debug("json-rpc request: '{}'", json);

                final DoHandleReqDto handleReq = doHandleReq(json);

                if (handleReq.res() == OK && handleReq.methodResponse != null) {
                    final byte[] respArr = ctx.jsonMapper().writeValueAsBytes(handleReq.methodResponse());
                    out.write("%d:".formatted(respArr.length).getBytes());
                    out.write(respArr);
                    out.write(COMMA);
                } else {
                    doHandleErr(handleReq);
                }
            } catch (SocketException e) {
                log.error("socket '{}' is disconnected", socket);
                close(socket);
                return;
            } catch (Exception e) {
                log.error("internal err: {}", socket, e);
            } finally {
                if (socket != null && socket.isConnected()) {
                    try {
                        out.flush();
                    } catch (IOException e) {
                        log.error("out.flush() err", e);
                    }
                } else {
                    close(socket);
                }
            }
        }
    }

    private DoHandleReqDto doHandleReq(final JsonNode node) {
        try {

            final ValidateResult validate = ctx.validator().validate(node);
            if (validate.res().isErr()) {
                log.error("validation err: '{}'", validate.errMsg());
                return new DoHandleReqDto(validate.errMsg(), INVALID_REQ);
            }

            final String reqMethod = node.get("method").asText();

            final JsonRpcMethod jsonRpcMethod = ctx.methodMap().get(reqMethod);
            if (jsonRpcMethod == null) {
                log.error("method '{}' is not found", reqMethod);
                return new DoHandleReqDto("method is not found", METHOD_NOT_FOUND);
            }

            if (node.get("params") == null) {
                return new DoHandleReqDto(jsonRpcMethod.doHandle());
            }

            return new DoHandleReqDto(jsonRpcMethod.doHandle(node));
        } catch (final Exception e) {
            log.error("internal err", e);
            return new DoHandleReqDto("unexpected err", INTERNAL_ERR);
        }
    }

    private void doHandleErr(DoHandleReqDto handleReq) {
        //TODD
//        out.println("doHandleReqDto fail");
    }

    private void doHandleErr(final JsonRpcCode res) {
        //TODO
//        out.println("err: " + res);
    }

    private void doHandleErr(final String msg) {
        //TODO
//        out.println(msg);
    }

    private JsonNode parse(final byte[] buff, final int len) {
        try {
            return ctx.jsonMapper().readTree(buff, 0, len);
        } catch (Exception e) {
            log.error("parse err", e);
            return null;
        }
    }

    private JsonNode parse(final String s) {
        try {
            return ctx.jsonMapper().readTree(s);
        } catch (Exception e) {
            log.error("json parse err: '{}'", s, e);
            return null;
        }
    }
}
