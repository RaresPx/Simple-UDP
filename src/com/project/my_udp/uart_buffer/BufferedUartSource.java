package com.project.my_udp.uart_buffer;

import com.project.my_udp.fake_uart.FakeUartSource;
import com.project.my_udp.control_hub.ControlHub;

public class BufferedUartSource {

    private final FakeUartSource source;
    private final ByteRingBuffer buffer;
    private final Thread readerThread;

    public BufferedUartSource(String filename, int bufferSize) throws Exception {
        this.source = new FakeUartSource(filename);
        this.buffer = new ByteRingBuffer(bufferSize);

        ControlHub.log("UART_SRC", "EVENT=INIT file=" + filename + " buffer=" + bufferSize);

        this.readerThread = new Thread(this::readerLoop);
        this.readerThread.setDaemon(true);
        this.readerThread.start();
    }

    private void readerLoop() {
        byte[] chunk = new byte[16];

        try {
            while (true) {
                int read = source.read(chunk);

                if (read == -1) {
                    ControlHub.log("UART_SRC", "EVENT=EOF_WAIT");
                    Thread.sleep(20);
                    continue;
                }

                if (read > 0) {
                    ControlHub.log("UART_SRC", "EVENT=READ_CHUNK size=" + read);
                    buffer.put(chunk, read);
                }
            }
        } catch (Exception e) {
            ControlHub.log("UART_SRC", "EVENT=ERROR msg=" + e.getMessage());
            e.printStackTrace();
        }
    }

    public int read(byte[] dest) throws Exception {
        ControlHub.log("UART_SRC", "EVENT=READ_REQUEST size=" + dest.length);

        int totalRead = 0;

        while (totalRead < dest.length) {
            int read = buffer.take(
                    dest,
                    totalRead,
                    dest.length - totalRead,
                    1_000_000
            );

            if (read == 0) {
                ControlHub.log("UART_SRC", "EVENT=EOF_RETURN partial=" + totalRead);
                return totalRead == 0 ? -1 : totalRead;
            }

            totalRead += read;
        }

        ControlHub.log("UART_SRC", "EVENT=READ_DONE size=" + totalRead);
        return totalRead;
    }
}