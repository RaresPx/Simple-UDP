package com.project.my_udp;

import java.io.RandomAccessFile;

class FakeUartSource {

    private final RandomAccessFile file;

    public FakeUartSource(String filename) throws Exception {
        file = new RandomAccessFile(filename, "r");
    }

    /**
     * Reads up to 16 bytes (128 bits) to simulate hardware chunking.
     * Returns:
     *  - 16 normally
     *  - <16 only at EOF
     *  - -1 at true EOF
     */
    public int read(byte[] buffer) throws Exception {
        int toRead = Math.min(buffer.length, 16);

        int bytesRead = file.read(buffer, 0, toRead);

        if (bytesRead == -1) {
            return -1; // EOF
        }

        // simulate UART interrupts
        Thread.sleep(1);

        return bytesRead;
    }

    public void close() throws Exception {
        file.close();
    }
}