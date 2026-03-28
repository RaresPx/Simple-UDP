package com.project.my_udp;

import java.net.*;
import java.util.*;

/*
Sender with testcases,
        NORMAL,
        DROP,
        CORRUPT,
        DROP_ACK,
        OUT_OF_ORDER
 Testcases repeat every 5 packets.
 Out of order realistically never happens since this is not a sliding window sender,
 but receiver handles it with a buffer.
 Out of order also leads to 2 duplicate packets on the receiver side since there are 2 extra SEND commands
 executed here
 */
public class Sender {

    static final boolean DEBUG_TOGGLE_TESTS = false;
    static final int PAYLOAD_SIZE = 64;
    static final int PORT = 5000;
    static final int TIMEOUT_MS = 150;

    //region MESSAGE
    static final String MESSAGE =
            "THIS_IS_A_LONG_TEST_MESSAGE_FOR_PROTOCOL_WITH_MULTIPLE_FAILURE_SCENARIOS_"
                    + "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_ABCDEFGHIJKLMNOPQRSTUVWXYZ_"
                    + "abcdefghijklmnopqrstuvwxyz_1234567890_"
                    + "\n Servus toc ce mai faceti\n Va pup de pe UDP\n"
                    + "Some padding"
                    + "String meme =\n" +
                    "\"................................................................................\\n\" +\n" +
                    "\".................##########..............##########.............................\\n\" +\n" +
                    "\"...............##############..........##############...........................\\n\" +\n" +
                    "\"..............################........################..........................\\n\" +\n" +
                    "\".............##################......##################.........................\\n\" +\n" +
                    "\"............####################....####################........................\\n\" +\n" +
                    "\"............######......######......######......######..........................\\n\" +\n" +
                    "\"............#####........#####......#####........#####..........................\\n\" +\n" +
                    "\"............#####..##....#####......#####....##..#####..........................\\n\" +\n" +
                    "\"............#####..##....#####......#####....##..#####..........................\\n\" +\n" +
                    "\"............#####........#####......#####........#####..........................\\n\" +\n" +
                    "\"............######......######......######......######..........................\\n\" +\n" +
                    "\"............####################....####################........................\\n\" +\n" +
                    "\".............##################......##################.........................\\n\" +\n" +
                    "\"..............################........################..........................\\n\" +\n" +
                    "\"...............##############..........##############...........................\\n\" +\n" +
                    "\".................##########..............##########.............................\\n\" +\n" +
                    "\"................................................................................\\n\" +\n" +
                    "\".....................###########....###########.................................\\n\" +\n" +
                    "\"....................#############..#############................................\\n\" +\n" +
                    "\"...................#############################................................\\n\" +\n" +
                    "\"...................#############################................................\\n\" +\n" +
                    "\"....................###########################.................................\\n\" +\n" +
                    "\".....................#########################..................................\\n\" +\n" +
                    "\".......................#####################....................................\\n\" +\n" +
                    "\".........................#################......................................\\n\" +\n" +
                    "\"...........................#############........................................\\n\" +\n" +
                    "\".............................#########..........................................\\n\" +\n" +
                    "\"...............................#####............................................\\n\" +\n" +
                    "\"................................###.............................................\\n\" +\n" +
                    "\".................................#..............................................\\n\" +\n" +
                    "\"................................................................................\\n\" +\n"
                    + "[END_OF_MESSAGE]";//So that Receiver can reconstruct the transmission
    //endregion MESSAGE


    public static void main(String[] args) throws Exception {

        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(TIMEOUT_MS);

        InetAddress addr = InetAddress.getByName("127.0.0.1");

        List<byte[]> blocks = splitMessage(MESSAGE);

        short seq = 0;

        System.out.println("Sender started...\n");

        for (byte[] block : blocks) {

            Packet p = new Packet();
            p.seq = seq;
            p.flags = 0;
            p.payload = block;

            boolean acked = false;
            int attempt = 0;

            while (!acked) {

                attempt++;

                TestAction action = getAction(seq, attempt);

                byte[] bytes = p.toBytes();

                // Apply corruption if needed
                if (action == TestAction.CORRUPT) {
                    bytes[5] ^= 0xFF;
                    log("CORRUPTING packet seq=" + seq);
                }

                if (action == TestAction.OUT_OF_ORDER) {
                    log("FORCING OUT-OF-ORDER seq=" + seq);

                    // Only do if next packet exists
                    if (seq + 1 < blocks.size()) {
                        // Send "next" packet first
                        Packet nextPacket = new Packet();
                        nextPacket.seq = (short) (seq + 1);
                        nextPacket.flags = 0;
                        nextPacket.payload = blocks.get(seq + 1); // payload of original seq+1

                        byte[] nextBytes = nextPacket.toBytes();
                        DatagramPacket dpNext = new DatagramPacket(nextBytes, nextBytes.length, addr, PORT);
                        socket.send(dpNext);
                        log("SEND OUT-OF-ORDER seq=" + nextPacket.seq + " [" + fromFixed16(nextPacket.payload) + "]");
                    }

                    // Set action to normal for the current packet
                    action = TestAction.NORMAL;
                }

                if (action == TestAction.DROP) {
                    log("DROPPING packet seq=" + seq);
                } else {
                    DatagramPacket dp = new DatagramPacket(bytes, bytes.length, addr, PORT);
                    socket.send(dp);
                    log("SEND seq=" + seq + " [" + fromFixed16(block) + "]");
                }

                try {
                    Packet ack = receive(socket);

                    if (ack != null && ack.flags == 1 && ack.seq == seq) {

                        if (action == TestAction.DROP_ACK) {
                            log("IGNORING ACK seq=" + seq);
                            continue; // pretend we didn't get it
                        }

                        log("ACK OK seq=" + seq);
                        acked = true;
                    }

                } catch (SocketTimeoutException e) {
                    log("TIMEOUT seq=" + seq);
                }
            }

            seq++;
            Thread.sleep(100);
        }

        System.out.println("\nTransmission finished.");
    }

    enum TestAction {
        NORMAL,
        DROP,
        CORRUPT,
        DROP_ACK,
        OUT_OF_ORDER
    }

    static TestAction getAction(int seq, int attempt) {
        if(!DEBUG_TOGGLE_TESTS) return TestAction.NORMAL;
        if (attempt > 1) return TestAction.NORMAL;

        switch (seq % 5) {
            case 0: return TestAction.DROP;       // drop first attempt
            case 1: return TestAction.CORRUPT;    // corrupt first attempt
            case 2: return TestAction.DROP_ACK;   // drop ack first attempt
            case 3: return TestAction.OUT_OF_ORDER; // force out-of-order
            default: return TestAction.NORMAL;
        }
    }

    static Packet receive(DatagramSocket socket) throws Exception {
        byte[] buf = new byte[1024];
        DatagramPacket dp = new DatagramPacket(buf, buf.length);
        socket.receive(dp);

        return Packet.fromBytes(
                Arrays.copyOf(dp.getData(), dp.getLength())
        );
    }

    static List<byte[]> splitMessage(String msg) {
        List<byte[]> blocks = new ArrayList<>();

        for (int i = 0; i < msg.length(); i += PAYLOAD_SIZE) {
            String part = msg.substring(i, Math.min(i + PAYLOAD_SIZE, msg.length()));
            blocks.add(toFixed16(part));
        }

        return blocks;
    }

    static byte[] toFixed16(String s) {
        byte[] result = new byte[PAYLOAD_SIZE];
        byte[] src = s.getBytes();

        System.arraycopy(src, 0, result, 0, Math.min(PAYLOAD_SIZE, src.length));
        return result;
    }

    static String fromFixed16(byte[] data) {
        return new String(data).trim();
    }

    static void log(String s) {
        System.out.println("[SENDER] " + s);
    }
}