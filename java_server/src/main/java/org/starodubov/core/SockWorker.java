package org.starodubov.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;

public class SockWorker implements Runnable {

    private final Logger log = LoggerFactory.getLogger(SockWorker.class);
    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;

    public SockWorker(final Socket socket) {
        setKeepAlive(socket);
        this.socket = socket;
        this.in = getIn(socket);
        this.out = getOut(socket);
    }

    private BufferedReader getIn(final Socket socket) {
        try {
            return new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            log.error("sock input stream err", e);
            throw new RuntimeException(e);
        }
    }

    private PrintWriter getOut(final Socket socket) {
        try {
            return new PrintWriter(socket.getOutputStream(), false);
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
        String line;
        for (; ; ) {
            try {
                line = in.readLine();
                if (line == /*EOF*/null) {
                    log.info("client '{}' disconnected", socket);
                    return;
                }
                log.info("get msg: '{}'", line);
                out.println("ACK");
                out.flush();
            } catch (IOException e) {
                log.error("cannot read line from socket: {}", socket, e);
                throw new RuntimeException(e);
            }
        }
    }
}
