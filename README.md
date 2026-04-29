# UDP Reliable Transmission Simulator

## Overview

This project implements a custom UDP-based reliable data transfer system in Java.  
It simulates a simplified transport protocol with sliding windows, retransmissions, ACK handling, and controlled fault injection (drop, corruption, duplication).

It is designed as a testing sandbox for studying how UDP behaves under unreliable network conditions.

---

## Architecture

```
com.project.my_udp
├── Sender                  → Sliding-window UDP sender with retransmission + test injection
├── Receiver                → In-order receiver with buffering + ACK generation
├── Packet                  → Custom packet format (seq, flags, payload)
├── Config                  → Global configuration parameters
├── ThreadTester            → Runs Sender + Receiver together
│
├── uart_buffer             → Buffered UART-like input system (ring buffer)
├── fake_uart               → Simulated UART source and sink
├── control_hub             → Runtime logging + config tuning system (UDP-based)
│
├── debug_monitor
│   └── ControlPanel        → GUI for live monitoring, logging, and control commands
│
└── testers
    └── OutputIntegrityCheck → File-based validation tool for transmitted data
```

---

## Key Features

### Reliable UDP Simulation
- Sliding window sender
- Sequence-number based tracking
- ACK-based confirmation
- Automatic retransmission on timeout

---

### Fault Injection Testing
Built-in deterministic test cases:
- Packet drop simulation
- ACK drop simulation
- Packet corruption
- Packet duplication
- Repeating test patterns

---

### Out-of-Order Handling
- Receiver buffers out-of-order packets
- Reorders using expected sequence number
- Detects duplicates safely

---

### UART Simulation Layer
- Byte-level UART abstraction
- Buffered input using ring buffer
- Safe raw byte transfer (no string encoding issues)

---

### Runtime Control System
- Live config updates via ControlHub
- Adjustable parameters:
    - payload size
    - window size
    - timeout
    - max packet size
- UDP-based control commands

---

### Debug Monitoring GUI
ControlPanel provides:
- RX / TX / SYS live logs
- Login-protected control access
- Runtime tuning of protocol parameters
- Basic metrics:
    - packet rate
    - latency
    - window timing
    - warnings

---

### Integrity Testing
- Compares input vs output UART files
- Ensures loss-free transmission under test conditions

---

## How It Works

### Sender
- Reads bytes from UART source
- Splits into packets
- Sends using sliding window
- Retransmits on timeout or missing ACK

### Receiver
- Receives UDP packets
- Detects duplicates and corruption
- Buffers out-of-order packets
- Delivers in-order to UART sink
- Sends ACKs back to sender

### Control System
- Injects runtime configuration changes
- Streams structured logs (TX/RX/SYS)
- Enables real-time testing scenarios

---

## Design Goal

This is not a production networking stack.  
It is a controlled experimental environment for:

- studying UDP reliability
- simulating packet loss/corruption
- analyzing retransmission behavior
- observing buffering and queue dynamics

---

## Notes

- Sequence numbers are modular (`SEQ_MOD`)
- Timing is based on system timestamps (ms precision)
- Logging is centralized via ControlHub
- Designed for single sender / single receiver setup