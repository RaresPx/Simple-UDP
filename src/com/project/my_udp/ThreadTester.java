package com.project.my_udp;

import com.project.my_udp.control_hub.ControlHub;

public class ThreadTester {
    public static void main(String[] args) {

        if(Config.DEBUG_CONTROL) {
            ControlHub.start();
            ControlHub.log("SYSTEM", "EVENT=CONTROL_HUB_STARTED");
        }

        Thread receiverThread = new Thread(() -> {
            try {
                ControlHub.log("SYSTEM", "EVENT=RECEIVER_THREAD_START");
                new Receiver().main(args);
            } catch (Exception e) {
                ControlHub.log("SYSTEM", "EVENT=RECEIVER_THREAD_ERROR msg=" + e.getMessage());
                e.printStackTrace();
            }
        });

        Thread senderThread = new Thread(() -> {
            try {
                ControlHub.log("SYSTEM", "EVENT=SENDER_THREAD_START");
                new Sender().main(args);
            } catch (Exception e) {
                ControlHub.log("SYSTEM", "EVENT=SENDER_THREAD_ERROR msg=" + e.getMessage());
                e.printStackTrace();
            }
        });

        receiverThread.start();
        senderThread.start();

        ControlHub.log("SYSTEM", "EVENT=THREADS_STARTED");
    }
}