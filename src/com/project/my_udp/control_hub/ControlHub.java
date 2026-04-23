package com.project.my_udp.control_hub;

import com.project.my_udp.Config;

import java.net.*;
import java.io.*;
import java.util.concurrent.*;

/**
 * ControlHub Singleton
 * - UDP login + command port
 * - UDP log streaming after login
 * - runtime config tuning
 * - TX/RX global static access
 */
public class ControlHub {

    // =========================================================
    // SINGLETON
    // =========================================================
    private static final ControlHub INSTANCE = new ControlHub();

    public static void start() { INSTANCE.startInternal(); }

    public static void log(String src, String msg) {
        if(!Config.DEBUG_CONTROL) return;
        INSTANCE.logInternal(src, msg);
    }

    public static TransmissionConfig getConfig() {
        return INSTANCE.getConfigInternal();
    }

    public static boolean hasUpdated(){
        return INSTANCE.modifiedConfig;
    }

    // =========================================================
    // TRANSMISSION CONFIG SNAPSHOT
    // =========================================================
    public static class TransmissionConfig {
        public final int maxpacketSize;
        public final int payloadSize;
        public final int windowSize;
        public final int timeoutMs;

        public TransmissionConfig(int p, int pl, int w, int t) {
            this.maxpacketSize = p;
            this.payloadSize = pl;
            this.windowSize = w;
            this.timeoutMs = t;
        }
    }

    // =========================================================
    // RUNTIME CONFIG (MODIFIABLE)
    // =========================================================
    private volatile int maxpacketSize = 2048;
    private volatile int payloadSize = 1024;
    private volatile int windowSize = 10;
    private volatile int timeoutMs = 150;

    private volatile boolean modifiedConfig = false;

    private volatile boolean loggedIn = false;

    // =========================================================
    // UDP STATE
    // =========================================================
    private DatagramSocket socket;
    private InetAddress clientAddress;
    private int clientPort;

    // =========================================================
    // QUEUES
    // =========================================================
    private final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>();

    // =========================================================
    // THREADS
    // =========================================================
    private Thread udpThread;
    private Thread logThread;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================
    private ControlHub() {}

    // =========================================================
    // START SYSTEM
    // =========================================================
    private void startInternal() {

        try {
            socket = new DatagramSocket(Config.DEBUG_CONTROL_PORT);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // -------------------------
        // UDP CONTROL THREAD
        // -------------------------
        udpThread = new Thread(() -> {

            byte[] buffer = new byte[Config.DEBUG_PACKET_SIZE];

            while (true) {

                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String msg = new String(packet.getData(), 0, packet.getLength());
                    //System.out.println("Received " + msg);
                    handleMessage(msg, packet.getAddress(), packet.getPort());

                } catch (Exception ignored) {}
            }
        });

        // -------------------------
        // LOG SENDER THREAD (UDP)
        // -------------------------
        logThread = new Thread(() -> {

            while (true) {

                try {
                    String log = logQueue.take();

                    if (loggedIn && clientAddress != null) {

                        byte[] data = log.getBytes();

                        DatagramPacket packet = new DatagramPacket(
                                data,
                                data.length,
                                clientAddress,
                                clientPort
                        );

                        socket.send(packet);
                    }

                } catch (Exception ignored) {}
            }
        });

        udpThread.start();
        logThread.start();
    }

    // =========================================================
    // MESSAGE HANDLER
    // =========================================================
    private void handleMessage(String msg, InetAddress addr, int port) {

        // LOGIN
        if (msg.startsWith("client=")) {

            if (msg.contains("client=admin") &&
                    msg.contains("password=pass")) {

                loggedIn = true;

                clientAddress = addr;
                clientPort = port;

                logInternal("SYS", "LOGIN OK");

            } else {
                logInternal("SYS", "LOGIN FAIL");
            }

            return;
        }

        if (!loggedIn) return;

        // COMMANDS
        if (msg.startsWith("SET")) {

            String[] p = msg.split(" ");
            if (p.length == 3) {
                applyCommand(p[1], p[2]);
            }
        }
    }

    // =========================================================
    // APPLY COMMAND
    // =========================================================
    private void applyCommand(String key, String val) {

        try {
            int v = Integer.parseInt(val);

            switch (key) {

                case "MAX_PACKET_SIZE": maxpacketSize = v; break;
                case "PAYLOAD_SIZE": payloadSize = v; break;
                case "WINDOW_SIZE": windowSize = v; break;
                case "TIMEOUT_MS": timeoutMs = v; break;
                default: break;
            }

            modifiedConfig = true;

            logInternal("SYS", "SET " + key + "=" + v);

        } catch (Exception ignored) {}
    }

    // =========================================================
    // LOG API (TX/RX CALL THIS)
    // =========================================================
    private void logInternal(String src, String msg) {

        if (!loggedIn) return;

        long ts = System.currentTimeMillis();

        String line =
                "[LOG][" + src + "]" +
                        "[" + msg + "]" +
                        "[TS=" + ts + "]" +
                "\n";

        logQueue.offer(line);
    }

    // =========================================================
    // CONFIG SNAPSHOT
    // =========================================================
    private TransmissionConfig getConfigInternal() {
        modifiedConfig = false;
        return new TransmissionConfig(
                maxpacketSize,
                payloadSize,
                windowSize,
                timeoutMs
        );
    }
}