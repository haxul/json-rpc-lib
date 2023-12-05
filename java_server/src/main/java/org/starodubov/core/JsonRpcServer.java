package org.starodubov.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.starodubov.reqhandler.JsonRpcMethod;
import org.starodubov.util.Support;
import org.starodubov.validator.JsonRpc2VerValidator;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

import static org.starodubov.util.Support.assertThat;

public class JsonRpcServer {
    private final Logger log = LoggerFactory.getLogger(JsonRpcServer.class);
    private final int port;
    private final ObjectMapper mapper;
    private final List<JsonRpcMethod<?>> methods;
    private long connCounter = 0;
    private final Set<Socket> activeSocket = Collections.newSetFromMap(new WeakHashMap<>());

    public JsonRpcServer(final int port, final ObjectMapper mapper, final List<JsonRpcMethod<?>> methods) {
        assertThat(port > 0, "port must be > 0");
        this.port = port;
        this.mapper = mapper;
        this.methods = methods;
    }

    public static JsonRpcServerBuilder builder(int port) {
        return new JsonRpcServerBuilder(port);
    }

    public void startOnNewThread() {
        Thread.ofPlatform()
                .name("json-rpc-server-thread")
                .daemon(false)
                .inheritInheritableThreadLocals(false)
                .start(this::start);
    }

    public void start() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            activeSocket.forEach(Support::close);
            log.info("close all active socket gracefully");
        }));
        final var mMap = new HashMap<String, JsonRpcMethod<?>>();
        for (var m : methods) {
            if (mMap.containsKey(m.methodName())) {
                assertThat(!mMap.containsKey(m.methodName()),
                        "duplicate method handlers for: '%s'".formatted(m.methodName()));
            }
            mMap.put(m.methodName(), m);
        }
        final var ctx = new WorkerCtx(mMap, mapper, new JsonRpc2VerValidator());
        try (final var serverSock = new ServerSocket(port)) {
            log.info("json-rpc server started on port: {}", port);
            Socket sock;
            for (; ; ) {
                sock = serverSock.accept();
                activeSocket.add(sock);
                Thread.ofVirtual()
                        .name("json-rpc-sock-worker-thread-", connCounter++)
                        .inheritInheritableThreadLocals(false)
                        .uncaughtExceptionHandler((thread, ex) ->
                                log.error("uncaught ex on thread: '{}'", thread, ex))
                        .start(new SockWorker(sock, ctx));
            }
        } catch (Exception e) {
            log.error("unexpected err", e);
        }
    }

    public static class JsonRpcServerBuilder {
        private int port;
        private ObjectMapper mapper;
        private List<JsonRpcMethod<?>> methods;

        private JsonRpcServerBuilder(int port) {
            this.port = port;
        }

        public JsonRpcServerBuilder port(final int port) {
            this.port = port;
            return this;
        }

        public JsonRpcServerBuilder mapper(final ObjectMapper mapper) {
            this.mapper = mapper;
            return this;
        }

        public JsonRpcServerBuilder methods(final List<JsonRpcMethod<?>> methods) {
            this.methods = methods;
            return this;
        }

        public JsonRpcServer build() {
            if (mapper == null) mapper = new ObjectMapper();
            if (methods == null) methods = Collections.emptyList();

            return new JsonRpcServer(port, mapper, methods);
        }
    }
}
