package org.starodubov;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class LatencyTest {
    public static void main(String[] args) {
        try {
            final var sock = new Socket();
            sock.connect(new InetSocketAddress(3030));
            final var in = new BufferedInputStream(sock.getInputStream());
            final var out = new BufferedOutputStream(sock.getOutputStream());
            final var json = """
                    {"jsonrpc": "2.0", "method": "subtract", "params": [1142, 23], "id": 2}""".trim();
            final var bytes = json.getBytes();
            final var buff = new byte[512];
            out.write(bytes);
            out.flush();
            long start = System.nanoTime();
            int read = in.read(buff);
            System.out.println(new String(buff, 0, read));
            System.out.printf("%d мс%n", (System.nanoTime() - start) / 1000);
            in.close();
            out.close();
            sock.close();
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }
}
