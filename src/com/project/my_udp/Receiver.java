package com.project.my_udp;

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

    public static void main(String[] args) throws Exception {

        DatagramSocket socket = new DatagramSocket(PORT);

        int expectedSeq = 0;
        Set<Integer> received = new HashSet<>();
        Map<Integer, byte[]> buffer = new HashMap<>();

        FakeUartSink uart = new FakeUartSink();

        System.out.println("Receiver started...\n");

        while (true) {

            byte[] buf = new byte[Config.MAX_PACKET_SIZE];
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

        // 🔌 forward to UART (16-bit chunk simulation inside)
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
        System.out.println("[RECEIVER] " + s);
        System.out.flush();
    }
}