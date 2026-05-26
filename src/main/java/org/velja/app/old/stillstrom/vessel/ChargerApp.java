package org.velja.app.old.stillstrom.vessel;

import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.swing.*;
import java.awt.*;


public class ChargerApp extends JFrame {
    private static final Logger logger = LoggerFactory.getLogger(ChargerApp.class);
    private static JLabel chargerStatusLabel;
    private static JLabel batteryLevelLabel;
    private static JProgressBar batteryProgressBar;
    private static JLabel connectedLabel;
    private static JLabel chargingLabel;
    private static JLabel warningLabel;
    private static JLabel fiveGStatusLabel;
    private static JLabel cctvLabel;
    private static JButton connectButton;
    private static JButton chargeButton;
    private static JButton windGustButton;
    private static JButton fiveGButton;
    private static JButton saveCCTVButton;
    private static JTextArea emulatorOutput;
    private static JTextArea serverOutput;
    private static VesselBE vesselBE;

    private static boolean isConnected = false;
    public static boolean isCharging = false;
    private static boolean is5GConnected = true;
    private static boolean windGustWarning = false;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SplashScreen().setVisible(true));
    }


    public static void createAndShowGUI() {
        SwingUtilities.invokeLater(() -> {
            vesselBE = new VesselBE();
            vesselBE.startServer();

        });
        JFrame mainFrame = new JFrame("ChargerApp");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(600, 400);
        mainFrame.setLayout(new BorderLayout());

        JPanel alertPanel = createAlertPanel();
        JPanel leftPanel = createLeftPanel();
        JPanel centerPanel = createCenterPanel();

        mainFrame.add(alertPanel, BorderLayout.NORTH);
        mainFrame.add(leftPanel, BorderLayout.WEST);
        mainFrame.add(centerPanel, BorderLayout.CENTER);

        mainFrame.setLocation(100, 100);
        mainFrame.setVisible(true);

        createLogWindows(mainFrame);
    }

    private static JPanel createAlertPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel labelPanel = new JPanel(new GridLayout(1, 3, 10, 10));
        warningLabel = new JLabel("", SwingConstants.CENTER);
        warningLabel.setForeground(Color.RED);
        warningLabel.setOpaque(true);

        fiveGStatusLabel = new JLabel("5G Connected", SwingConstants.CENTER);
        fiveGStatusLabel.setForeground(Color.GREEN);
        fiveGStatusLabel.setOpaque(true);

        cctvLabel = new JLabel("", SwingConstants.CENTER);
        cctvLabel.setOpaque(true);

        labelPanel.add(warningLabel);
        labelPanel.add(fiveGStatusLabel);
        labelPanel.add(cctvLabel);

        JPanel buttonPanel = new JPanel(new GridLayout(1, 3, 10, 10));
        windGustButton = new JButton("Simulate Wind Gust");
        windGustButton.addActionListener(e -> toggleWindGustWarning());

        fiveGButton = new JButton("Disconnect 5G");
        fiveGButton.addActionListener(e -> toggle5G());

        saveCCTVButton = new JButton("Save CCTV");
        saveCCTVButton.addActionListener(e -> toggleCCTV());

        buttonPanel.add(windGustButton);
        buttonPanel.add(fiveGButton);
        buttonPanel.add(saveCCTVButton);

        panel.add(labelPanel, BorderLayout.NORTH);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private static JPanel createLeftPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 1, 10, 10));
        connectedLabel = new JLabel("Connected: OFF", SwingConstants.CENTER);
        connectedLabel.setForeground(Color.RED);

        connectButton = new JButton("Connect");
        connectButton.addActionListener(e -> toggleConnection());

        chargeButton = new JButton("Start Charging");
        chargeButton.setEnabled(false);
        chargeButton.addActionListener(e -> toggleCharging());

        chargingLabel = new JLabel("Charging: OFF", SwingConstants.CENTER);
        chargingLabel.setForeground(Color.RED);

        panel.add(connectedLabel);
        panel.add(connectButton);
        panel.add(chargingLabel);
        panel.add(chargeButton);

        return panel;
    }

    private static JPanel createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        batteryLevelLabel = new JLabel("Battery Level: Unknown", SwingConstants.CENTER);
        batteryProgressBar = new JProgressBar(0, 100);
        batteryProgressBar.setStringPainted(true);
        chargerStatusLabel = new JLabel("Charger Available: Unknown", SwingConstants.CENTER);

        panel.add(chargerStatusLabel, BorderLayout.NORTH);
        panel.add(batteryLevelLabel, BorderLayout.CENTER);
        panel.add(batteryProgressBar, BorderLayout.SOUTH);

        return panel;
    }

    private static void toggleWindGustWarning() {
        windGustWarning = !windGustWarning;
        warningLabel.setText(windGustWarning ? "Warning, wind gust above expected value!" : "");
        windGustButton.setText(windGustWarning ? "Clear warning" : "Simulate Wind Gust");
    }

    private static void toggle5G() {
        is5GConnected = !is5GConnected;
        fiveGStatusLabel.setText(is5GConnected ? "5G Connected" : "5G Disconnected");
        fiveGStatusLabel.setForeground(is5GConnected ? Color.GREEN : Color.RED);
        fiveGButton.setText(is5GConnected ? "Disconnect 5G" : "Connect 5G");

        if (!is5GConnected) {
            warningLabel.setText("5G disconnected, charging stopped and disconnected");
            if (isCharging) toggleCharging();
            if (isConnected) toggleConnection();
            connectButton.setEnabled(false);
            chargeButton.setEnabled(false);
        } else {
            warningLabel.setText("");
            connectButton.setEnabled(true);
        }
    }

    private static void toggleCCTV() {
        if (cctvLabel.getText().isEmpty()) {
            cctvLabel.setText("CCTV footage is saved to Cloud");
            saveCCTVButton.setText("Clear CCTV message");
        } else {
            cctvLabel.setText("");
            saveCCTVButton.setText("Save CCTV");
        }
    }

    private static void toggleConnection() {
        isConnected = !isConnected;
        connectedLabel.setText("Connected: " + (isConnected ? "ON" : "OFF"));
        connectedLabel.setForeground(isConnected ? Color.GREEN : Color.RED);
        connectButton.setText(isConnected ? "Disconnect" : "Connect");
        chargeButton.setEnabled(isConnected);
    }

    private static void toggleCharging() {
        if (!isConnected) return;
        isCharging = !isCharging;
        chargingLabel.setText("Charging: " + (isCharging ? "ON" : "OFF"));
        chargingLabel.setForeground(isCharging ? Color.GREEN : Color.RED);
        chargeButton.setText(isCharging ? "Stop Charging" : "Start Charging");
        connectButton.setEnabled(!isCharging);
        vesselBE.startFetching(isCharging);
    }

    private static void createLogWindows(JFrame mainFrame) {
        JFrame emulatorFrame = new JFrame("Emulator Output");
        emulatorFrame.setSize(400, 200);
        emulatorFrame.setLocation(mainFrame.getX() + mainFrame.getWidth() + 10, mainFrame.getY());
        emulatorOutput = new JTextArea();
        emulatorFrame.add(new JScrollPane(emulatorOutput));
        emulatorFrame.setVisible(true);

        JFrame serverFrame = new JFrame("Server Output");
        serverFrame.setSize(400, 200);
        serverFrame.setLocation(mainFrame.getX() + mainFrame.getWidth() + 10, mainFrame.getY() + 220);
        serverOutput = new JTextArea();
        serverFrame.add(new JScrollPane(serverOutput));
        serverFrame.setVisible(true);
    }

    @SneakyThrows
    public static void updateUI(boolean chargerAvailable, double batteryLevel) {
        SwingUtilities.invokeLater(() -> {
            chargerStatusLabel.setText("Charger Available: " + chargerAvailable);
            batteryLevelLabel.setText("Battery Level: " + batteryLevel + "%");
            batteryProgressBar.setValue((int) batteryLevel);
        });
    }

    @SneakyThrows
    public static void logEmulator(String message) {
        SwingUtilities.invokeLater(() -> emulatorOutput.append(message + "\n"));
    }

    public static void logServer(String message) {
        SwingUtilities.invokeLater(() -> serverOutput.append(message + "\n"));
    }


}