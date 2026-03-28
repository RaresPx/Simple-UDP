package com.project.my_udp;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

class FakeUartSink {

    private PrintStream out;
    private boolean humanReadable;
    private CharsetDecoder decoder;

    public FakeUartSink() throws Exception {
        this.humanReadable = Config.UART_HUMAN_READABLE;

        if (Config.UART_TO_FILE) {
            out = new PrintStream(new FileOutputStream(Config.UART_FILE, true)); // append
            out.println("\nNEW TRANSMISSION\n");
        } else {
            out = System.err; // separate from stdout logs
        }

        // UTF-8 decoder that replaces malformed bytes with �
        decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
    }

    public void write(byte[] data, int len) {

        if (humanReadable) {
            try {
                // decode only the first 'len' bytes
                String text = decoder.decode(java.nio.ByteBuffer.wrap(data, 0, len)).toString();
                out.print(text);
            } catch (Exception e) {
                out.println("[Invalid UTF-8 bytes]");
            }

        } else {
            // simulate 16-bit chunks in hex
            for (int i = 0; i < len; i += 2) {
                int hi = data[i] & 0xFF;
                int lo = (i + 1 < len) ? data[i + 1] & 0xFF : 0;
                out.printf("[%02X %02X] ", hi, lo);
            }
            out.println();
        }
    }
}