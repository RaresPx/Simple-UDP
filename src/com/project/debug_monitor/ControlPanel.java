package com.project.debug_monitor;

import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Lightweight UDP Protocol Task Manager
 * - RX / TX / SYS consoles
 * - live metrics (packet/window timing)
 * - inline login (no popup)
 * - structured SET controls
 */
public class ControlPanel {

    // =========================
    // CONFIG
    // =========================
    private static final String ESP_IP = "127.0.0.1";
    private static final int ESP_PORT = 8000;

    private final java.util.concurrent.BlockingQueue<String> uiQueue =
            new java.util.concurrent.LinkedBlockingQueue<>(5000);

    // =========================
    // NETWORK
    // =========================
    private DatagramSocket socket;

    // =========================
    // LOGIN
    // =========================
    private volatile boolean loggedIn = false;
    private volatile boolean loginPending = false;
    private long loginStartTime = 0;

    // =========================
    // METRICS
    // =========================
    private int txPackets = 0;
    private int rxPackets = 0;
    private int acks = 0;

    private long totalPacketBytes = 0;

    private long windowStartTime = System.currentTimeMillis();
    private long totalWindowTime = 0;
    private int windowCount = 0;

    private long lastPacketTime = System.currentTimeMillis();
    private long totalPacketTime = 0;

    private int warnings = 0;

    // =========================
    // UI
    // =========================
    private JTextArea rxConsole;
    private JTextArea txConsole;
    private JTextArea sysConsole;

    private JLabel metricsLabel;
    private JLabel loginLabel;

    private JTextField userField;
    private JPasswordField passField;

    private JTextField cmdKey;
    private JTextField cmdValue;

    private final Deque<String> cmdHistory = new ArrayDeque<>();
    private JTextArea commandInfo;

    // =========================
    // MAIN
    // =========================
    public static void main(String[] args) {
        new ControlPanel().start();
    }

    // =========================
    // START
    // =========================
    public void start() {

        try {
            socket = new DatagramSocket();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        buildUI();
        startListenerThread();
        startLoginWatcher();
        startUiDrainThread();
    }

    // =========================
    // UI
    // =========================
    private void buildUI() {

        JFrame frame = new JFrame("UDP Protocol Task Manager");
        frame.setSize(1100, 700);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // =========================
        // TOP STATUS BAR
        // =========================
        JPanel top = new JPanel(new GridLayout(2, 1));

        loginLabel = new JLabel("OFFLINE");
        loginLabel.setFont(new Font("Consolas", Font.BOLD, 14));

        metricsLabel = new JLabel("No data");
        metricsLabel.setFont(new Font("Consolas", Font.PLAIN, 12));

        top.add(loginLabel);
        top.add(metricsLabel);

        // =========================
        // CONSOLES
        // =========================
        JPanel consoles = new JPanel(new GridLayout(1, 3));

        rxConsole = createConsole("RX");
        txConsole = createConsole("TX");
        sysConsole = createConsole("SYS");

        style(rxConsole, Color.BLACK, new Color(80, 255, 120));
        style(txConsole, Color.BLACK, new Color(80, 180, 255));
        style(sysConsole, Color.DARK_GRAY, Color.WHITE);

        consoles.add(new JScrollPane(rxConsole));
        consoles.add(new JScrollPane(txConsole));
        consoles.add(new JScrollPane(sysConsole));

        // =========================
        // BOTTOM CONTROL PANEL
        // =========================
        JPanel bottom = new JPanel(new GridLayout(2, 1));

        bottom.add(buildLoginPanel());
        bottom.add(buildCommandPanel());

        frame.add(top, BorderLayout.NORTH);
        frame.add(consoles, BorderLayout.CENTER);
        frame.add(bottom, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    // =========================
    // LOGIN PANEL
    // =========================
    private JPanel buildLoginPanel() {

        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));

        userField = new JTextField(8);
        passField = new JPasswordField(8);

        JButton loginBtn = new JButton("LOGIN");

        loginBtn.addActionListener(e -> login());

        p.add(new JLabel("User"));
        p.add(userField);
        p.add(new JLabel("Pass"));
        p.add(passField);
        p.add(loginBtn);

        return p;
    }

    // =========================
    // COMMAND PANEL
    // =========================
    private JPanel buildCommandPanel() {

        JPanel p = new JPanel(new BorderLayout());

        JPanel input = new JPanel(new FlowLayout(FlowLayout.LEFT));

        cmdKey = new JTextField(10);
        cmdValue = new JTextField(10);

        JButton send = new JButton("SET");

        send.addActionListener(e -> sendCommand());

        input.add(new JLabel("SET"));
        input.add(cmdKey);
        input.add(cmdValue);
        input.add(send);

        commandInfo = new JTextArea();
        commandInfo.setEditable(false);

        commandInfo.setText(
                "CONTROL PARAMETERS (runtime):\n" +
                        "--------------------------------\n" +
                        "Packet Control:\n" +
                        " - MAX_PACKET_SIZE\n" +
                        " - PAYLOAD_SIZE\n\n" +
                        "Windowing:\n" +
                        " - WINDOW_SIZE\n" +
                        " - TIMEOUT_MS\n"
        );

        p.add(input, BorderLayout.NORTH);
        p.add(commandInfo, BorderLayout.CENTER);

        return p;
    }

    // =========================
    // LOGIN
    // =========================
    private void login() {

        String msg = "client=" + userField.getText()
                + " password=" + new String(passField.getPassword());

        sendUDP(msg);

        loginPending = true;
        loginStartTime = System.currentTimeMillis();

        logSys("LOGIN request sent...");
    }

    // =========================
    // COMMAND
    // =========================
    private void sendCommand() {

        if (!loggedIn) {
            logSys("BLOCKED: not logged in");
            return;
        }

        String cmd = "SET " + cmdKey.getText() + " " + cmdValue.getText();

        sendUDP(cmd);

        cmdHistory.addFirst(cmd);

        logSys("CMD → " + cmd);
    }

    // =========================
    // UDP SEND
    // =========================
    private void sendUDP(String msg) {

        try {
            byte[] data = msg.getBytes();

            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    InetAddress.getByName(ESP_IP),
                    ESP_PORT
            );

            socket.send(packet);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =========================
    // LISTENER
    // =========================
    private void startListenerThread() {

        Thread t = new Thread(() -> {

            byte[] buffer = new byte[4096];

            while (true) {
                try {

                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String msg = new String(packet.getData(), 0, packet.getLength());

                    uiQueue.offer(msg);

                } catch (Exception ignored) {}
            }
        });

        t.setDaemon(true);
        t.start();
    }

    // =========================
    // HANDLE
    // =========================
    private void handle(String msg) {

        long now = System.currentTimeMillis();

        // =========================
        // METRICS
        // =========================
        rxPackets++;

        totalPacketTime += (now - lastPacketTime);
        lastPacketTime = now;

        totalPacketBytes += msg.length();

        // window stats (very useful for your protocol)
        if (msg.contains("SEND seq=0")) {
            totalWindowTime += (now - windowStartTime);
            windowStartTime = now;
            windowCount++;
        }

        if (msg.contains("ACK SENT")) acks++;
        if (msg.contains("WARNING")) warnings++;

        // =========================
        // LOGIN HANDLING
        // =========================
        if (msg.contains("LOGIN OK")) {
            loggedIn = true;
            loginPending = false;
            loginLabel.setText("ONLINE");
            logSys("LOGIN SUCCESS");
            return;
        }

        if (msg.contains("LOGIN FAIL")) {
            loggedIn = false;
            loginPending = false;
            loginLabel.setText("LOGIN FAILED");
            return;
        }

        // =========================
        // ROUTING
        // =========================
        if (msg.contains("[RX]")) rxConsole.append(msg + "\n");
        else if (msg.contains("[TX]")) txConsole.append(msg + "\n");
        else sysConsole.append(msg + "\n");

        updateMetrics();
    }

    // =========================
    // METRICS UI
    // =========================
    private void updateMetrics() {

        double avgPacketSize = rxPackets == 0 ? 0 : (double) totalPacketBytes / rxPackets;
        double avgPacketTime = rxPackets == 0 ? 0 : (double) totalPacketTime / rxPackets;
        double avgWindowTime = windowCount == 0 ? 0 : (double) totalWindowTime / windowCount;

        metricsLabel.setText(
                "RX=" + rxPackets +
                        " TX=" + txPackets +
                        " ACK=" + acks +
                        " | AvgPkt=" + String.format("%.1f", avgPacketSize) +
                        "B AvgΔt=" + String.format("%.1f", avgPacketTime) +
                        "ms AvgWin=" + String.format("%.1f", avgWindowTime) +
                        "ms WARN=" + warnings
        );
    }

    // =========================
    // LOGIN WATCH
    // =========================
    private void startLoginWatcher() {

        Thread t = new Thread(() -> {

            while (true) {
                try {

                    if (loginPending &&
                            System.currentTimeMillis() - loginStartTime > 3000) {

                        loginPending = false;
                        loggedIn = false;
                        loginLabel.setText("TIMEOUT");
                        logSys("LOGIN TIMEOUT");
                    }

                    Thread.sleep(100);

                } catch (Exception ignored) {}
            }
        });

        t.setDaemon(true);
        t.start();
    }

    private void startUiDrainThread() {

        Timer timer = new Timer(20, e -> {

            int batch = 0;

            while (batch < 200) { // prevent UI starvation
                String msg = uiQueue.poll();
                if (msg == null) break;

                handle(msg);
                batch++;
            }
        });

        timer.start();
    }

    // =========================
    // HELPERS
    // =========================
    private JTextArea createConsole(String title) {
        JTextArea a = new JTextArea();
        a.setBorder(BorderFactory.createTitledBorder(title));
        a.setEditable(false);
        return a;
    }

    private void style(JTextArea a, Color bg, Color fg) {
        a.setBackground(bg);
        a.setForeground(fg);
    }

    private void logSys(String s) {
        sysConsole.append(s + "\n");
    }
}