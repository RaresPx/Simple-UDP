package com.project.my_udp.fake_uart;

import com.project.my_udp.Config;

import java.io.FileOutputStream;
import java.io.PrintStream;

public class FakeUartSink {

    private final PrintStream out;
    private final boolean humanReadable = Config.UART_HUMAN_READABLE;

    public FakeUartSink() throws Exception {

        if (Config.UART_TO_FILE) {
            out = new PrintStream(new FileOutputStream(Config.UART_FILE, false)); // append
            //out.println("\n" + "=".repeat(10) + "\nNEW TRANSMISSION\n" + "=".repeat(10));
        } else {
            out = System.err; // separate from stdout logs
        }
    }

    public void write(byte[] data, int len) {

        if (humanReadable) {
            try {
                //write bytes
                out.write(data,0,len);
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