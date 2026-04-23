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
    static  int PAYLOAD_SIZE = Config.PAYLOAD_SIZE;
    static  int TIMEOUT_MS = Config.TIMEOUT_MS;
    static  int WINDOW_SIZE = Config.WINDOW_SIZE;

    public static void main(String[] args) throws Exception {

        DatagramSocket socket = new DatagramSocket(PORT);

        int expectedSeq = 0;
        Set<Integer> received = new HashSet<>();
        Map<Integer, byte[]> buffer = new HashMap<>();

        FakeUartSink uart = new FakeUartSink();

        //System.out.println("Receiver started...\n");

        while (true) {
            if(Config.DEBUG_CONTROL) {
                if(ControlHub.hasUpdated()) {
                    ControlHub.TransmissionConfig tc = ControlHub.getConfig();
                    PAYLOAD_SIZE = tc.payloadSize;
                    TIMEOUT_MS = tc.timeoutMs;
                    WINDOW_SIZE = tc.windowSize;
                    MAX_PACKET_SIZE = tc.maxpacketSize;
                    ControlHub.log("RX", "Modified Transmission Config: ");
                }
            }
            byte[] buf = new byte[MAX_PACKET_SIZE];
            DatagramPacket dp = new DatagramPacket(buf, buf.length);
            socket.receive(dp);

            Packet p = Packet.fromBytes(
                    Arrays.copyOf(dp.getData(), dp.getLength())
            );

            if (p == null) {
                log("CORRUPTED PACKET");
                continue;
            }

            int seq = p.seq;

            log("Received seq=" + seq + " len=" + p.length);

            if(seq == 0 && expectedSeq >= Short.MAX_VALUE - WINDOW_SIZE){
                received.clear();
                buffer.clear();
                expectedSeq = 0;
                //System.err.println("MAX SEQ REACHED, RESTARTING FROM 0");
                ControlHub.log("WARNING","MAX SEQ REACHED, RESTARTING FROM 0");
            }
            if (received.contains(seq)) {
                log("DUPLICATE seq=" + seq);
            } else {
                received.add(seq);

                byte[] validData = Arrays.copyOf(p.payload, p.length);

                if (seq == expectedSeq) {
                    deliver(seq, validData, uart);
                    expectedSeq++;

                    // release buffered
                    while (buffer.containsKey(expectedSeq)) {
                        byte[] bufferedData = buffer.remove(expectedSeq);
                        deliver(expectedSeq, bufferedData, uart);
                        expectedSeq++;
                    }

                } else {
                    log("OUT OF ORDER seq=" + seq + " (expected " + expectedSeq + ")");
                    buffer.put(seq, validData);
                }
            }

            sendAck(socket, dp.getAddress(), dp.getPort(), seq);
        }
    }

    static void deliver(int seq, byte[] data, FakeUartSink uart) {
        log("DELIVER seq=" + seq + " bytes=" + data.length);

        uart.write(data, data.length);
    }

    static void sendAck(DatagramSocket socket, InetAddress addr, int port, int seq) throws Exception {
        Packet ack = new Packet();
        ack.seq = (short) seq;
        ack.flags = 1;
        ack.length = 0; // no payload

        byte[] bytes = ack.toBytes();
        DatagramPacket dp = new DatagramPacket(bytes, bytes.length, addr, port);
        socket.send(dp);

        log("ACK SENT seq=" + seq);
    }

    static void log(String s) {
        ControlHub.log("RX",s);
        if(Config.DEBUG) {
            System.out.println("[RECEIVER] " + s);
            System.out.flush();
        }
    }
}