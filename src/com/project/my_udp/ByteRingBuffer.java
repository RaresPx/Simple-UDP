package com.project.my_udp;

class ByteRingBuffer {

    private final byte[] buffer;
    private int head = 0, tail = 0, size = 0;
    private final int capacity;

    public ByteRingBuffer(int capacity) {
        this.buffer = new byte[capacity];
        this.capacity = capacity;
    }

    public synchronized void put(byte[] src, int length) throws InterruptedException {
        int offset = 0;

        while (offset < length) {
            while (size == capacity) {
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

    public synchronized int take(byte[] dest, int offset, int length) throws InterruptedException {
        if (size == 0) {
            return 0;
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