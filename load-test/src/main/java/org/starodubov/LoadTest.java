package org.starodubov;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class LoadTest {
    public final static AtomicBoolean flag = new AtomicBoolean(true);

    public static void main(String[] args) {
        final var sc = new Scanner(System.in);

        System.out.print("Введи количество потоков: ");
        final int thxCount = sc.nextInt();

        System.out.print("Введи общее время выполнения(сек): ");
        final int time = sc.nextInt();

        System.out.print("Введи количество реквестов в сек: ");
        final int rps = sc.nextInt();

        final int rpsForThread = rps / thxCount;
        final long intervalPerThread = 1000 / rpsForThread;
        System.out.println("Начинаем нагружать сервер...");
        Thread.ofVirtual().start(() -> {
            sleep(time * 1000L);
            flag.setOpaque(false);
        });

        try (final var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < thxCount; i++) {
                pool.execute(() -> loadTest(intervalPerThread));
            }
        }
        System.out.println("Тестрирование завершено");
    }

    public static void loadTest(final long intervalPerThread) {
        try (final var sock = new Socket()) {
            sock.connect(new InetSocketAddress(3030));
            final var in = new BufferedInputStream(sock.getInputStream());
            final var out = new BufferedOutputStream(sock.getOutputStream());

            final var buff = new byte[512];
            while (flag.getOpaque()) {
                final var json = """
                        {"jsonrpc": "2.0", "method": "subtract", "params": {"goal": %d, "str": "%s"}, "id": %d}""".formatted(
                        ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE),
                        genStr(),
                        ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE)
                ).trim();
                final var bytes = json.getBytes();
                out.write(bytes);
                out.flush();
                in.read(buff);
                sleepSmooth(intervalPerThread);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String genStr() {
        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        int targetStringLength = 10;

        return ThreadLocalRandom.current().ints(leftLimit, rightLimit + 1)
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    public static void sleepSmooth(final long t) {
        try {
            int n = ThreadLocalRandom.current().nextInt(0, 15);
            boolean b = ThreadLocalRandom.current().nextBoolean();
            long tt = b ? t + n : t - n;
            Thread.sleep(tt < 0 ? 0 : tt);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    public static void sleep(final long t) {
        try {
            Thread.sleep(t);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }
}