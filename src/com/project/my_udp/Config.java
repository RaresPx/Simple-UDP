package com.project.my_udp;

/*
==================== FUTURE HARDWARE / LONG-RUNNING STREAMING NOTES ====================

1. Packet CRC:
   - Currently CRC is calculated over the full payload buffer (PAYLOAD_SIZE),
     not just the valid bytes (length). On hardware, leftover uninitialized bytes
     could cause false corruption detections.
   - Consider zeroing payload or calculating CRC only over `length` bytes.

2. Sequence Number Wraparound:
   - seq is currently a `short` (0..32767 then -32768..0). On long-running streams,
     modulo logic for test cases (seq % 5) may behave incorrectly.
   - Use `int` for seq internally, cast to short only in packets if needed.

3. Sender Busy-Loop:
   - When the UART source file has no new data, FakeUartSource sleeps 20ms,
     but sender keeps checking in a tight loop.
   - Consider longer sleep or Thread.yield() to reduce CPU usage.

4. Logging Suppression:
   - Currently logs are suppressed when END_OF_TRANSMISSION is true.
     May hide retransmit events. Consider logging retransmits even when idle.

5. Received Set Growth (Receiver):
   - The `received` Set grows indefinitely, never prunes old sequence numbers.
   - For long-running streams, memory could grow. Consider trimming old entries
     beyond a window of e.g., expectedSeq - WINDOW_SIZE*2.

6. UTF-8 Decoding (Receiver / FakeUartSink):
   - Partial multi-byte UTF-8 sequences split across packets may be replaced with '�'.
   - For perfect decoding, buffer incomplete sequences across packet boundaries.

7. FakeUartSource / File Handling:
   - File is never closed until JVM exit.
   - On repeated restarts or long-running operation, consider proper close / reopen logic.

8. Timeout Settings:
   - TIMEOUT_MS is low (150ms). On slow UART or OS scheduling delays,
     may trigger unnecessary retransmits. Adjust for real hardware latency.

========================================================================================
*/

public class Config {

    public static final String INET_ADDR = "127.0.0.1";
    public static final int PORT = 5000;

    public static final int BUFFER_SIZE = 1024*1024; //1M
    public static final double WARN_AT_BUFFER_PERCENTAGE = 0.5;

    public static int PAYLOAD_SIZE = 1024;
    public static int WINDOW_SIZE = 10;

    public static final int TIMEOUT_MS = 150;
    public static final int SOCKET_TIMEOUT_MS = 50;

    public static int MAX_PACKET_SIZE = 2048;

    public static final boolean DEBUG = true;
    public static final boolean DEBUG_TESTS = false;
    public static final boolean DEBUG_CONTROL = true;
    public static final int DEBUG_CONTROL_PORT = 8000;
    public static final int DEBUG_PACKET_SIZE = 512;
    // UART config
    public static final boolean UART_TO_FILE = true;
    public static final String UART_FILE = "uart_output.txt";
    public static final String UART_INPUT_FILE = "uart_in.txt";
    public static final boolean UART_HUMAN_READABLE = true;
}