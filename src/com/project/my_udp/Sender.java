package com.project.my_udp;

import com.project.my_udp.control_hub.ControlHub;
import com.project.my_udp.uart_buffer.BufferedUartSource;

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

    static final int PORT = Config.PORT;
    static  int PAYLOAD_SIZE = Config.PAYLOAD_SIZE;
    static  int TIMEOUT_MS = Config.TIMEOUT_MS;
    static  int WINDOW_SIZE = Config.WINDOW_SIZE;
    static int MAX_PACKET_SIZE = Config.MAX_PACKET_SIZE;
    static final boolean DEBUG_TESTS = Config.DEBUG_TESTS;
    static boolean END_OF_TRANSMISSION = false;
    static int SEQ_MOD = Short.MAX_VALUE + 1;

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

        BufferedUartSource uart = new BufferedUartSource(Config.UART_INPUT_FILE,Config.BUFFER_SIZE);

        byte[] buffer = new byte[PAYLOAD_SIZE];
        int base = 0, nextSeq = 0;
        Map<Integer, Packet> window = new HashMap<>();
        Map<Integer, Long> timers = new HashMap<>();
        Map<Integer, Integer> attempts = new HashMap<>();

        log("EVENT=START msg=Sender started");

        long startTime = System.currentTimeMillis();

        while (true) {

            long loopTime = System.currentTimeMillis();

            if(Config.DEBUG_CONTROL) {
                if(ControlHub.hasUpdated()) {
                    ControlHub.TransmissionConfig tc = ControlHub.getConfig();
                    PAYLOAD_SIZE = tc.payloadSize;
                    TIMEOUT_MS = tc.timeoutMs;
                    WINDOW_SIZE = tc.windowSize;
                    MAX_PACKET_SIZE = tc.maxpacketSize;

                    ControlHub.log("TX", "EVENT=CONFIG_UPDATE");
                    log("EVENT=CONFIG_UPDATE payload=" + PAYLOAD_SIZE +
                            " timeout=" + TIMEOUT_MS +
                            " window=" + WINDOW_SIZE);
                }
            }

            //Remove old packets
            {
                int old = (base + SEQ_MOD/2) % SEQ_MOD;
                window.remove(old);
                timers.remove(old);
                attempts.remove(old);
                log("EVENT=WINDOW_CLEAN base=" + base + " removed=" + old);
            }

            // Fill window
            while ((int)((nextSeq - base + SEQ_MOD) % SEQ_MOD) < WINDOW_SIZE) {

                int read = uart.read(buffer);

                if (read <= 0) {
                    if(!END_OF_TRANSMISSION) {
                        log("EVENT=EOF msg=End of UART reached");
                        ControlHub.log("SYS","End of UART reached");
                    }
                    END_OF_TRANSMISSION = true;
                    break;
                } else {
                    END_OF_TRANSMISSION = false;
                }

                log("EVENT=NEW_PACKET seq=" + nextSeq +
                        " len=" + read +
                        " base=" + base +
                        " next=" + nextSeq +
                        " loopTime=" + (System.currentTimeMillis() - loopTime));

                Packet p = new Packet();
                p.seq = (short) nextSeq;
                p.flags = 0;
                p.length = (short) read;
                System.arraycopy(buffer, 0, p.payload, 0, read);

                attempts.put(nextSeq, 1);

                sendWithTests(socket, addr, p, nextSeq, 1);

                window.put(nextSeq, p);
                timers.put(nextSeq, System.currentTimeMillis());

                log("EVENT=BUFFER_ADD seq=" + nextSeq + " windowSize=" + window.size());

                nextSeq = (nextSeq+1)%SEQ_MOD;
            }

            // Receive ACKs
            try {
                Packet ack = receive(socket);

                if (ack != null && ack.flags == 1) {

                    int ackSeq = ack.seq;
                    int attempt = attempts.getOrDefault(ackSeq, 1);
                    TestAction action = getAction(ackSeq, attempt);

                    log("EVENT=ACK_RECEIVED seq=" + ackSeq +
                            " base=" + base +
                            " windowSize=" + window.size());

                    if(DEBUG_TESTS)
                        log("EVENT=TEST_ACTION seq=" + ackSeq + " type=" + action + " attempt=" + attempt);

                    if (action != TestAction.DROP_ACK) {

                        window.remove(ackSeq);
                        timers.remove(ackSeq);
                        attempts.remove(ackSeq);

                        log("EVENT=ACK_ACCEPTED seq=" + ackSeq);

                        if (ackSeq == base) {
                            while (!window.containsKey(base) && base != nextSeq) {
                                base = (base+1)%SEQ_MOD;
                            }
                            log("EVENT=WINDOW_SLIDE new_base=" + base);
                        }

                    } else {
                        log("EVENT=ACK_IGNORED seq=" + ackSeq);
                        attempts.put(ackSeq, attempt + 1);
                    }
                }

            } catch (SocketTimeoutException e) {
                log("EVENT=ACK_TIMEOUT msg=no packet received");
            } catch (SocketException e) {
                log("EVENT=SOCKET_ERROR msg=" + e.getMessage());
            } catch (Exception e) {
                log("EVENT=RECEIVE_ERROR msg=" + e.getMessage());
            }

            // Check retransmit
            long now = System.currentTimeMillis();

            for (int seq : new ArrayList<>(window.keySet())) {
                if (now - timers.get(seq) > TIMEOUT_MS) {

                    log("EVENT=TIMEOUT seq=" + seq +
                            " age=" + (now - timers.get(seq)));

                    Packet p = window.get(seq);
                    int attempt = attempts.getOrDefault(seq, 1);

                    sendWithTests(socket, addr, p, seq, attempt + 1);

                    timers.put(seq, now);
                    attempts.put(seq, attempt + 1);

                    log("EVENT=RETRANSMIT seq=" + seq + " attempt=" + (attempt + 1));
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

        log("EVENT=SEND_PREP seq=" + seq + " action=" + action + " attempt=" + attempt);

        if (action == TestAction.CORRUPT) {
            bytes[5] ^= 0xFF;
            log("EVENT=CORRUPT seq=" + seq);
        }

        if (action == TestAction.DROP) {
            log("EVENT=DROP seq=" + seq);
            return;
        }

        DatagramPacket dp = new DatagramPacket(bytes, bytes.length, addr, PORT);
        socket.send(dp);

        log("EVENT=SEND seq=" + seq +
                " len=" + p.length +
                " attempt=" + attempt);

        if (action == TestAction.DUPLICATE) {
            socket.send(dp);
            log("EVENT=DUPLICATE_SEND seq=" + seq);
        }
    }

    static Packet receive(DatagramSocket socket) throws Exception {
        byte[] buf = new byte[MAX_PACKET_SIZE];
        DatagramPacket dp = new DatagramPacket(buf, buf.length);
        socket.receive(dp);
        return Packet.fromBytes(Arrays.copyOf(dp.getData(), dp.getLength()));
    }

    static void log(String s) {
        ControlHub.log("TX", s);
        if (Config.DEBUG && !END_OF_TRANSMISSION)
            System.out.println("[SENDER] " + s);
    }
}