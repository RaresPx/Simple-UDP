package com.project.my_udp;

import java.io.*;

class FakeUartSource {

    private final RandomAccessFile file;
    private long pos = 0;

    public FakeUartSource(String filename) throws Exception {
        file = new RandomAccessFile(filename, "r");
    }

    /**
     * Reads up to buffer.length bytes into buffer.
     * Returns number of bytes read, or 0 if no new data yet.
     */
    public int read(byte[] buffer) throws Exception {
        file.seek(pos);
        int bytesRead = file.read(buffer);
        if (bytesRead > 0) {
            pos += bytesRead;
            return bytesRead;
        } else {
            // no new data, sleep briefly to avoid busy loop
            Thread.sleep(20);
            return 0;
        }
    }

    public void close() throws Exception {
        file.close();
    }
}