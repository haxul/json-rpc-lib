package org.starodubov;

import org.starodubov.core.JsonRpcServer;

public class Main {
    public static void main(String[] args) {
        var jsonRpcServer = new JsonRpcServer(3030);
        jsonRpcServer.startOnNewThread();
    }
}