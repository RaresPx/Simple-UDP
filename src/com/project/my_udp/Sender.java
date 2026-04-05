package com.project.my_udp;

import java.net.*;
import java.util.*;

/*
Sender with testcases,
        NORMAL,
        DROP,
        CORRUPT,
        DROP_ACK,
        DUPLICATE
 Testcases repeat every 5 packets.
 Out of order realistically never happens since this is not a sliding window sender,
 but receiver handles it with a buffer.
 Out of order also leads to 2 duplicate packets on the receiver side since there are 2 extra SEND commands
 executed here
 */
public class Sender {

    static final int PAYLOAD_SIZE = Config.PAYLOAD_SIZE;
    static final int PORT = Config.PORT;
    static final int TIMEOUT_MS = Config.TIMEOUT_MS;
    static final int WINDOW_SIZE = Config.WINDOW_SIZE;
    static final boolean DEBUG_TESTS = Config.DEBUG_TESTS;
    static boolean END_OF_TRANSMISSION = false;

    enum TestAction {
        NORMAL,
        DROP,
        CORRUPT,
        DROP_ACK,
        DUPLICATE
    }

    public static void main(String[] args) throws Exception {

        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(Config.SOCKET_TIMEOUT_MS);
        InetAddress addr = InetAddress.getByName(Config.INET_ADDR);

        // 🔹 Replace static message with UART source
        BufferedUartSource uart = new BufferedUartSource(Config.UART_INPUT_FILE,Config.BUFFER_SIZE);

        byte[] buffer = new byte[PAYLOAD_SIZE];
        int base = 0, nextSeq = 0;
        Map<Integer, Packet> window = new HashMap<>();
        Map<Integer, Long> timers = new HashMap<>();
        Map<Integer, Integer> attempts = new HashMap<>();

        System.out.println("Sender started...\n");

        while (true) {

            // Fill window
            while (nextSeq < base + WINDOW_SIZE) {
                int read = uart.read(buffer);
                if (read <= 0) {
                    if(!END_OF_TRANSMISSION) System.out.println("End of UART reached");
                    END_OF_TRANSMISSION = true;
                    break;
                }else{
                    END_OF_TRANSMISSION = false;
                }

                Packet p = new Packet();
                p.seq = (short) nextSeq;
                p.flags = 0;
                p.length = (short) read;
                System.arraycopy(buffer, 0, p.payload, 0, read);

                attempts.put(nextSeq, 1);
                sendWithTests(socket, addr, p, nextSeq, 1);

                window.put(nextSeq, p);
                timers.put(nextSeq, System.currentTimeMillis());

                nextSeq++;
            }

            // Receive ACKs
            try {
                Packet ack = receive(socket);
                if (ack != null && ack.flags == 1) {
                    int ackSeq = ack.seq;
                    int attempt = attempts.getOrDefault(ackSeq, 1);
                    TestAction action = getAction(ackSeq, attempt);

                    if (action != TestAction.DROP_ACK) {
                        log("ACK OK seq=" + ackSeq);
                        window.remove(ackSeq);
                        timers.remove(ackSeq);
                        attempts.remove(ackSeq);

                        if (ackSeq == base) {
                            while (!window.containsKey(base) && base < nextSeq) base++;
                        }
                    } else {
                        log("IGNORING ACK seq=" + ackSeq);
                        attempts.put(ackSeq, attempt + 1); // increment so next time it passes
                    }
                }
            } catch (SocketTimeoutException e) {
                log("ACK TIMEOUT (no packet received)");
            } catch (SocketException e) {
                log("SOCKET EXCEPTION: " + e.getMessage());
            } catch (Exception e) {
                log("RECEIVE ERROR: " + e.getMessage());
            }

            // Check retransmit
            long now = System.currentTimeMillis();
            for (int seq : new ArrayList<>(window.keySet())) {
                if (now - timers.get(seq) > TIMEOUT_MS) {
                    log("TIMEOUT seq=" + seq);
                    Packet p = window.get(seq);
                    int attempt = attempts.getOrDefault(seq, 1);
                    sendWithTests(socket, addr, p, seq, attempt + 1);
                    timers.put(seq, now);
                    attempts.put(seq, attempt + 1);
                }
            }
        }
    }

    static TestAction getAction(int seq, int attempt) {
        if (!DEBUG_TESTS) return TestAction.NORMAL;
        if (attempt > 1) return TestAction.NORMAL;
        return switch (seq % 5) {
            case 0 -> TestAction.DROP;
            case 1 -> TestAction.CORRUPT;
            case 2 -> TestAction.DROP_ACK;
            case 3 -> TestAction.DUPLICATE;
            default -> TestAction.NORMAL;
        };
    }

    static void sendWithTests(DatagramSocket socket, InetAddress addr,
                              Packet p, int seq, int attempt) throws Exception {
        TestAction action = getAction(seq, attempt);
        byte[] bytes = p.toBytes();

        if (action == TestAction.CORRUPT) {
            bytes[5] ^= 0xFF;
            log("CORRUPTING packet seq=" + seq);
        }

        if (action == TestAction.DROP) {
            log("DROPPING packet seq=" + seq);
            return;
        }

        DatagramPacket dp = new DatagramPacket(bytes, bytes.length, addr, PORT);
        socket.send(dp);
        log("SEND seq=" + seq + " len=" + p.length);

        if (action == TestAction.DUPLICATE) {
            socket.send(dp);
            log("DUPLICATE SEND seq=" + seq);
        }
    }

    static Packet receive(DatagramSocket socket) throws Exception {
        byte[] buf = new byte[Config.MAX_PACKET_SIZE];
        DatagramPacket dp = new DatagramPacket(buf, buf.length);
        socket.receive(dp);
        return Packet.fromBytes(Arrays.copyOf(dp.getData(), dp.getLength()));
    }

    static void log(String s) {
        if (Config.DEBUG && !END_OF_TRANSMISSION) System.out.println("[SENDER] " + s);
    }
}