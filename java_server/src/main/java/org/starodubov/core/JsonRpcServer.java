package org.starodubov.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ServerSocket;

import static org.starodubov.util.Support.state;


public class JsonRpcServer {
    private final Logger log = LoggerFactory.getLogger(JsonRpcServer.class);
    private final int port;
    private long connCounter = 0;

    public JsonRpcServer(int port) {
        state(() -> port > 0, "port must be > 0");
        this.port = port;
    }

    public void startOnNewThread() {
        Thread.ofPlatform()
                .name("json-rpc-server-thread")
                .daemon(false)
                .inheritInheritableThreadLocals(false)
                .start(this::start);
    }

    public void start() {
        try (final var serverSock = new ServerSocket(port)) {
            log.info("json-rpc server started on port: {}", port);
            for (; ; ) {
                Thread.ofVirtual()
                        .name("json-rpc-sock-worker-thread-", connCounter++)
                        .inheritInheritableThreadLocals(false)
                        .uncaughtExceptionHandler((thread, ex) -> {
                            log.error("uncaught ex on thread: '{}'", thread, ex);
                        })
                        .start(new SockWorker(serverSock.accept()));
            }
        } catch (Exception e) {
            log.error("unexpected err", e);
        }
    }
}
