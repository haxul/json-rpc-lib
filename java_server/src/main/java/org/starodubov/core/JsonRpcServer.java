package org.starodubov.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.starodubov.reqhandler.JsonRpcMethod;
import org.starodubov.validator.JsonRpc2VerValidator;

import java.net.ServerSocket;
import java.util.HashMap;
import java.util.List;

import static org.starodubov.util.Support.state;


public class JsonRpcServer {
    private final Logger log = LoggerFactory.getLogger(JsonRpcServer.class);
    private final int port;
    private final ObjectMapper mapper;
    private final List<JsonRpcMethod> methods;
    private long connCounter = 0;

    public JsonRpcServer(final int port, final ObjectMapper mapper, final List<JsonRpcMethod> methods) {
        state(() -> port > 0, "port must be > 0");
        this.port = port;
        this.mapper = mapper;
        this.methods = methods;
    }

    public void startOnNewThread() {
        Thread.ofPlatform()
                .name("json-rpc-server-thread")
                .daemon(false)
                .inheritInheritableThreadLocals(false)
                .start(this::start);
    }

    public void start() {
        final var mMap = new HashMap<String, JsonRpcMethod>();
        for (var m : methods) {
            if (mMap.containsKey(m.getMethodName())) {
                state(() -> !mMap.containsKey(m.getMethodName()),
                        "duplicate method handlers for: '%s'".formatted(m.getMethodName()));
            }
            mMap.put(m.getMethodName(), m);
        }
        final var ctx = new ExecCtx(mMap, mapper, new JsonRpc2VerValidator());
        try (final var serverSock = new ServerSocket(port)) {
            log.info("json-rpc server started on port: {}", port);
            for (; ; ) {
                Thread.ofVirtual()
                        .name("json-rpc-sock-worker-thread-", connCounter++)
                        .inheritInheritableThreadLocals(false)
                        .uncaughtExceptionHandler((thread, ex) -> {
                            log.error("uncaught ex on thread: '{}'", thread, ex);
                        })
                        .start(new SockWorker(serverSock.accept(), ctx));
            }
        } catch (Exception e) {
            log.error("unexpected err", e);
        }
    }
}
