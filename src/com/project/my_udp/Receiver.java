package com.project.my_udp;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.HashSet;
import java.util.Set;

import java.net.*;
import java.util.*;
/*
Simple receiver, handles all sender test cases and outputs a final message when the message constructed
from the payloads contains and [END_OF_MESSAGE] block,
Has block buffer so it could handle sliding window sender
It can continue indefinitely, resets the seq after the message has been delivered(enf of message detected)
Can implement user custom logic in the delivered method and the final while block
 */
public class Receiver {

    static final int PORT = 5000;

    public static void main(String[] args) throws Exception {

        DatagramSocket socket = new DatagramSocket(PORT);

        int expectedSeq = 0;
        Set<Integer> received = new HashSet<>();
        Map<Integer, String> buffer = new HashMap<>();

        StringBuilder message = new StringBuilder();

        System.out.println("Receiver started...\n");

        while (true) {

            byte[] buf = new byte[1024];
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
            String text = fromFixed16(p.payload);

            log("Received seq=" + seq + " [" + text + "]");

            if (received.contains(seq)) {
                log("DUPLICATE seq=" + seq);
            } else {
                received.add(seq);

                if (seq == expectedSeq) {
                    deliver(seq, text, message);
                    expectedSeq++;

                    // release buffered
                    while (buffer.containsKey(expectedSeq)) {
                        String bufferedText = buffer.remove(expectedSeq);
                        deliver(expectedSeq, bufferedText, message);
                        expectedSeq++;
                    }

                } else {
                    log("OUT OF ORDER seq=" + seq + " (expected " + expectedSeq + ")");
                    buffer.put(seq, text);
                }
            }

            sendAck(socket, dp.getAddress(), dp.getPort(), seq);

            if (message.toString().contains("[END_OF_MESSAGE]")) {
                System.out.println("\n===== FINAL MESSAGE =====");
                System.out.println(message.toString());
                System.out.println("=========================\n");
                message.setLength(0);
                expectedSeq = 0;
                received.clear();
                buffer.clear();
                //break;
            }
        }
    }

    static void deliver(int seq, String text, StringBuilder message) {
        log("DELIVER seq=" + seq + " [" + text + "]");
        message.append(text);
    }

    static void sendAck(DatagramSocket socket, InetAddress addr, int port, int seq) throws Exception {
        Packet ack = new Packet();
        ack.seq = (short) seq;
        ack.flags = 1;

        byte[] bytes = ack.toBytes();
        DatagramPacket dp = new DatagramPacket(bytes, bytes.length, addr, port);
        socket.send(dp);

        log("ACK SENT seq=" + seq);
    }

    static String fromFixed16(byte[] data) {
        int len = data.length;

        // find first zero byte (padding) at the end
        while (len > 0 && data[len - 1] == 0) {
            len--;
        }

        return new String(data, 0, len);
    }

    static void log(String s) {
        System.out.println("[RECEIVER] " + s);
        System.out.flush();
    }
}