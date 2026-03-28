package com.project.my_udp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

class Packet {

    //Packet: Start | Seq    | Flags            | Payload                           | CRC
    //          ^      ^       ^                   ^                                   ^
    //        0x7E    2 bytes  Data/Ack bit flag   16 bits from aes128 transmission    16-bit CRC checksum
    //Total packet size: 22
    static final byte START = 0x7E;//IDK why this is used
    static final int PAYLOAD_SIZE = 64; //AES128 outputs 16 bytes

    short seq;//Seq number 0 .... 65536
    byte flags; // 0 = data, 1 = ACK
    byte[] payload = new byte[PAYLOAD_SIZE];

    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(1 + 2 + 1 + PAYLOAD_SIZE + 2);
        buffer.order(ByteOrder.BIG_ENDIAN);

        buffer.put(START);
        buffer.putShort(seq);
        buffer.put(flags);
        buffer.put(payload);

        short crc = crc16(buffer.array(), 0, 1 + 2 + 1 + PAYLOAD_SIZE);
        buffer.putShort(crc);

        return buffer.array();
    }

    public static Packet fromBytes(byte[] data) {
        if (data.length < 22) return null;

        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.BIG_ENDIAN);

        if (buffer.get() != START) return null;

        Packet p = new Packet();
        p.seq = buffer.getShort();
        p.flags = buffer.get();
        buffer.get(p.payload);

        short receivedCrc = buffer.getShort();
        short computedCrc = crc16(data, 0, 1 + 2 + 1 + PAYLOAD_SIZE);

        if (receivedCrc != computedCrc) return null;

        return p;
    }

    //Standard 16-bit CRC algorithm using 0x1021 generator polynomial
    //This code is taken directly from the web, I have no idea how checksum math works
    static short crc16(byte[] data, int offset, int length) {
        int crc = 0xFFFF;
        for (int i = offset; i < length; i++) {
            crc ^= (data[i] << 8);
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x8000) != 0)
                    crc = (crc << 1) ^ 0x1021;
                else
                    crc <<= 1;
            }
        }
        return (short) (crc & 0xFFFF);
    }
}