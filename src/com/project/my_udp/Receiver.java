package com.project.my_udp;

import com.project.my_udp.control_hub.ControlHub;
import com.project.my_udp.fake_uart.FakeUartSink;

import java.net.*;
import java.util.*;

/*
Simple receiver, handles all sender test cases
Has block buffer so it can handle sliding window sender
Now:
- works on raw bytes (no String corruption)
- forwards data directly to UART
- no END_OF_MESSAGE needed
Can implement user custom logic in deliver() (e.g. UART, file, etc.)
*/
public class Receiver {

    static final int PORT = Config.PORT;
    static int MAX_PACKET_SIZE = Config.MAX_PACKET_SIZE;
    static int PAYLOAD_SIZE = Config.PAYLOAD_SIZE;
    static int TIMEOUT_MS = Config.TIMEOUT_MS;
    static int WINDOW_SIZE = Config.WINDOW_SIZE;
    static int SEQ_MOD = Short.MAX_VALUE + 1;

    public static void main(String[] args) throws Exception {

        DatagramSocket socket = new DatagramSocket(PORT);

        int expectedSeq = 0;
        Set<Integer> received = new HashSet<>();
        Map<Integer, byte[]> buffer = new HashMap<>();

        FakeUartSink uart = new FakeUartSink();

        log("EVENT=START msg=Receiver started");

        while (true) {

            long loopStart = System.currentTimeMillis();

            if(Config.DEBUG_CONTROL) {
                if(ControlHub.hasUpdated()) {
                    ControlHub.TransmissionConfig tc = ControlHub.getConfig();
                    PAYLOAD_SIZE = tc.payloadSize;
                    TIMEOUT_MS = tc.timeoutMs;
                    WINDOW_SIZE = tc.windowSize;
                    MAX_PACKET_SIZE = tc.maxpacketSize;

                    ControlHub.log("RX", "EVENT=CONFIG_UPDATE");
                    log("EVENT=CONFIG_UPDATE payload=" + PAYLOAD_SIZE +
                            " timeout=" + TIMEOUT_MS +
                            " window=" + WINDOW_SIZE);
                }
            }

            // delete older packets
            if(received.size() > 2 * WINDOW_SIZE){
                log("EVENT=HISTORY_CLEAN size=" + received.size());
                received.clear();
            }

            byte[] buf = new byte[MAX_PACKET_SIZE];
            DatagramPacket dp = new DatagramPacket(buf, buf.length);
            socket.receive(dp);

            Packet p = Packet.fromBytes(
                    Arrays.copyOf(dp.getData(), dp.getLength())
            );

            if (p == null) {
                log("EVENT=CORRUPTED_PACKET");
                continue;
            }

            int seq = p.seq;

            log("EVENT=PKT_RX seq=" + seq +
                    " len=" + p.length +
                    " expected=" + expectedSeq +
                    " loopTime=" + (System.currentTimeMillis() - loopStart));

            if (received.contains(seq)) {

                log("EVENT=PKT_DUP seq=" + seq);

            } else {

                received.add(seq);

                byte[] validData = Arrays.copyOf(p.payload, p.length);

                if (seq == expectedSeq) {

                    log("EVENT=PKT_IN_ORDER seq=" + seq);

                    deliver(seq, validData, uart);

                    expectedSeq = (expectedSeq + 1) % SEQ_MOD;

                    // release buffered
                    while (buffer.containsKey(expectedSeq)) {

                        byte[] bufferedData = buffer.remove(expectedSeq);

                        log("EVENT=BUFFER_RELEASE seq=" + expectedSeq);

                        deliver(expectedSeq, bufferedData, uart);

                        expectedSeq = (expectedSeq + 1) % SEQ_MOD;
                    }

                    log("EVENT=EXPECTED_ADVANCE new_expected=" + expectedSeq);

                } else {

                    log("EVENT=PKT_OOO seq=" + seq +
                            " expected=" + expectedSeq);

                    if ((seq - expectedSeq + SEQ_MOD) % SEQ_MOD < WINDOW_SIZE) {
                        buffer.putIfAbsent(seq, validData);
                        log("EVENT=BUFFER_ADD seq=" + seq +
                                " bufferSize=" + buffer.size());
                    }
                }
            }

            sendAck(socket, dp.getAddress(), dp.getPort(), seq);
        }
    }

    static void deliver(int seq, byte[] data, FakeUartSink uart) {
        log("EVENT=DELIVER seq=" + seq +
                " bytes=" + data.length);

        uart.write(data, data.length);
    }

    static void sendAck(DatagramSocket socket, InetAddress addr, int port, int seq) throws Exception {

        Packet ack = new Packet();
        ack.seq = (short) seq;
        ack.flags = 1;
        ack.length = 0;

        byte[] bytes = ack.toBytes();
        DatagramPacket dp = new DatagramPacket(bytes, bytes.length, addr, port);
        socket.send(dp);

        log("EVENT=ACK_SENT seq=" + seq);
    }

    static void log(String s) {
        ControlHub.log("RX", s);
        if(Config.DEBUG) {
            System.out.println("[RECEIVER] " + s);
            System.out.flush();
        }
    }
}