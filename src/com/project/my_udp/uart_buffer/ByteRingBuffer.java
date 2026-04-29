package com.project.my_udp.uart_buffer;

import com.project.my_udp.Config;
import com.project.my_udp.control_hub.ControlHub;

class ByteRingBuffer {

    private final byte[] buffer;
    private int head = 0, tail = 0;
    public int size = 0;
    private final int capacity;

    public ByteRingBuffer(int capacity) {
        this.buffer = new byte[capacity];
        this.capacity = capacity;

        ControlHub.log("RINGBUF", "EVENT=INIT cap=" + capacity);
    }

    public synchronized void put(byte[] src, int length) throws InterruptedException {

        int offset = 0;

        if (size > (int) (capacity * Config.WARN_AT_BUFFER_PERCENTAGE)) {
            ControlHub.log("RINGBUF", "EVENT=WARN_FULLNESS size=" + size);
        }

        while (offset < length) {

            while (size == capacity) {
                ControlHub.log("RINGBUF", "EVENT=FULL_BLOCK");
                wait();
            }

            int space = capacity - size;
            int chunk = Math.min(space, length - offset);

            for (int i = 0; i < chunk; i++) {
                buffer[head] = src[offset + i];
                head = (head + 1) % capacity;
            }

            size += chunk;
            offset += chunk;

            ControlHub.log("RINGBUF",
                    "EVENT=PUT chunk=" + chunk +
                            " size=" + size +
                            " head=" + head +
                            " tail=" + tail);

            notifyAll();
        }
    }

    public synchronized int take(byte[] dest, int offset, int length, long timeoutNs)
            throws InterruptedException {

        long deadline = System.nanoTime() + timeoutNs;

        while (size < length) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                ControlHub.log("RINGBUF", "EVENT=TAKE_TIMEOUT requested=" + length + " available=" + size);
                break;
            }

            wait(remaining / 1_000_000, (int)(remaining % 1_000_000));
        }

        int count = Math.min(length, size);

        for (int i = 0; i < count; i++) {
            dest[offset + i] = buffer[tail];
            tail = (tail + 1) % capacity;
        }

        size -= count;

        ControlHub.log("RINGBUF",
                "EVENT=TAKE count=" + count +
                        " size=" + size +
                        " head=" + head +
                        " tail=" + tail);

        notifyAll();
        return count;
    }
}