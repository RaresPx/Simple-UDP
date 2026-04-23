package com.project.my_udp.uart_buffer;

import com.project.my_udp.Config;
import com.project.my_udp.control_hub.ControlHub;

class ByteRingBuffer {

    private final byte[] buffer;
    private int head = 0, tail = 0;
    public int size = 0; //access in buffer source
    private final int capacity;

    public ByteRingBuffer(int capacity) {
        this.buffer = new byte[capacity];
        this.capacity = capacity;
    }

    public synchronized void put(byte[] src, int length) throws InterruptedException {
        int offset = 0;

        if(size > (int) (capacity * Config.WARN_AT_BUFFER_PERCENTAGE)) {
           // System.err.println("Buffer is " + Config.WARN_AT_BUFFER_PERCENTAGE * 100 + "% Full! " + this.size + " b");
            ControlHub.log("WARNING", "Buffer is " + Config.WARN_AT_BUFFER_PERCENTAGE * 100 + "% Full! " + this.size + " b");
        }

        while (offset < length) {
            while (size == capacity) {
                //System.err.println("Buffer full!");
                ControlHub.log("WARNING","Buffer full");
                wait(); // buffer full
            }

            int space = capacity - size;
            int chunk = Math.min(space, length - offset);

            for (int i = 0; i < chunk; i++) {
                buffer[head] = src[offset + i];
                head = (head + 1) % capacity;
            }

            size += chunk;
            offset += chunk;

            notifyAll();
        }
    }

    public synchronized int take(byte[] dest, int offset, int length, long timeoutNs)
            throws InterruptedException {

        long deadline = System.nanoTime() + timeoutNs;

        while (size < length) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                break; // timeout reached
            }

            long millis = remaining / 1_000_000;
            int nanos = (int)(remaining % 1_000_000);

            wait(millis, nanos);
        }

        int count = Math.min(length, size);

        for (int i = 0; i < count; i++) {
            dest[offset + i] = buffer[tail];
            tail = (tail + 1) % capacity;
        }

        size -= count;

        notifyAll();
        return count;
    }

}