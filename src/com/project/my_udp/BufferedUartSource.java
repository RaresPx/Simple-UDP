package com.project.my_udp;

class BufferedUartSource {

    private final FakeUartSource source;
    private final ByteRingBuffer buffer;
    private final Thread readerThread;

    public BufferedUartSource(String filename, int bufferSize) throws Exception {
        this.source = new FakeUartSource(filename);
        this.buffer = new ByteRingBuffer(bufferSize);

        this.readerThread = new Thread(this::readerLoop);
        this.readerThread.setDaemon(true);
        this.readerThread.start();
    }

    private void readerLoop() {
        byte[] chunk = new byte[16]; // 128-bit UART chunk

        try {
            while (true) {
                int read = source.read(chunk);

                if (read == -1) {
                    Thread.sleep(20);
                    continue;
                }

                if (read > 0) {
                    buffer.put(chunk, read);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Drop-in replacement for uart.read(buffer)
     */
    public int read(byte[] dest) throws Exception {
        int totalRead = 0;

        while (totalRead < dest.length) {
            int read = buffer.take(
                    dest,
                    totalRead,
                    dest.length - totalRead
            );

            if (read == 0) {
                // EOF
                return totalRead == 0 ? -1 : totalRead;
            }

            totalRead += read;
        }

        return totalRead;
    }
}