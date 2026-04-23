package com.project.my_udp;

import com.project.my_udp.control_hub.ControlHub;

public class ThreadTester {
    public static void main(String[] args) {

        if(Config.DEBUG_CONTROL)
            ControlHub.start();

        Thread receiverThread = new Thread(() -> {
            try {
                new Receiver().main(args);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread senderThread = new Thread(() -> {
            try {
                new Sender().main(args);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        receiverThread.start();
        senderThread.start();
    }
}