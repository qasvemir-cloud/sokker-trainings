package org.velja.app;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Objects;


public class ChargingDashboard extends JFrame {
    private boolean isConnected = false;
    private boolean isCharging = false;
    private boolean is5GConnected = true;
    private boolean windGustWarning = false;

    private JLabel connectedLabel;
    private JLabel chargingLabel;
    private JLabel warningLabel;
    private JLabel fiveGStatusLabel;
    private JLabel cctvLabel;
    private JButton connectButton;
    private JButton chargeButton;
    private JButton windGustButton;
    private JButton fiveGButton;
    private JButton saveCCTVButton;

    public ChargingDashboard() {
        setTitle("Charging Dashboard");
        setSize(800, 600);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JPanel alertPanel = createAlertPanel();
        JPanel leftPanel = createLeftPanel();
        DrawingPanel centerPanel = new DrawingPanel();
        JPanel rightPanel = createRightPanel();

        add(alertPanel, BorderLayout.NORTH);
        add(leftPanel, BorderLayout.WEST);
        add(centerPanel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);
    }

    private JPanel createAlertPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel labelPanel = new JPanel(new GridLayout(1, 3, 10, 10));
        warningLabel = new JLabel("", SwingConstants.CENTER);
        warningLabel.setForeground(Color.RED);
        warningLabel.setPreferredSize(new Dimension(250, 30));
        warningLabel.setOpaque(true);

        fiveGStatusLabel = new JLabel("5G Connected", SwingConstants.CENTER);
        fiveGStatusLabel.setForeground(Color.GREEN);
        fiveGStatusLabel.setPreferredSize(new Dimension(250, 30));
        fiveGStatusLabel.setOpaque(true);

        cctvLabel = new JLabel("", SwingConstants.CENTER);
        cctvLabel.setPreferredSize(new Dimension(250, 30));
        cctvLabel.setOpaque(true);

        labelPanel.add(warningLabel);
        labelPanel.add(fiveGStatusLabel);
        labelPanel.add(cctvLabel);

        JPanel buttonPanel = new JPanel(new GridLayout(1, 3, 10, 10));
        windGustButton = new JButton("Simulate Wind Gust");
        windGustButton.addActionListener(e -> {
            windGustWarning = !windGustWarning;
            warningLabel.setText(windGustWarning ? "Warning, wind gust above expected value!" : "");
            windGustButton.setText(windGustWarning ? "Clear warning":"Simulate Wind Gust" );

        });

        fiveGButton = new JButton("Disconnect 5G");
        fiveGButton.addActionListener(e -> toggle5G());

        saveCCTVButton = new JButton("Save CCTV");
        saveCCTVButton.addActionListener(e -> {
            if (cctvLabel.getText().isEmpty()) {
                cctvLabel.setText("CCTV footage is saved to Cloud");
                saveCCTVButton.setText("Clear CCTV message");
            } else {
                cctvLabel.setText("");
                saveCCTVButton.setText("Save CCTV");
            }
        });

        buttonPanel.add(windGustButton);
        buttonPanel.add(fiveGButton);
        buttonPanel.add(saveCCTVButton);

        panel.add(labelPanel, BorderLayout.NORTH);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createLeftPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 1, 10, 10));
        connectedLabel = new JLabel("Connected: OFF", SwingConstants.CENTER);
        connectedLabel.setForeground(Color.RED);

        connectButton = new JButton("Connect");
        connectButton.addActionListener(e -> toggleConnection());

        chargeButton = new JButton("Start Charging");
        chargeButton.setEnabled(false);
        chargeButton.addActionListener(e -> toggleCharging());
        chargingLabel = new JLabel("Connected: OFF", SwingConstants.CENTER);
        chargingLabel.setForeground(Color.RED);

        panel.add(connectedLabel);
        panel.add(connectButton);
        panel.add(chargingLabel);
        panel.add(chargeButton);


        return panel;
    }

    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new GridLayout(4, 1, 10, 10));
        panel.add(new JLabel("Tension: 3.47 t"));
        panel.add(new JLabel("Cable paid out: 109.17m"));
        panel.add(new JLabel("Distance to charger: 94.99 m"));
        panel.add(new JLabel("Catenary: 50.99 m"));
        return panel;
    }

    private void toggle5G() {
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

    private void toggleConnection() {
        isConnected = !isConnected;
        connectedLabel.setText("Connected: " + (isConnected ? "ON" : "OFF"));
        connectedLabel.setForeground(isConnected ? Color.GREEN : Color.RED);
        connectButton.setText(isConnected ? "Disconnect" : "Connect");
        chargeButton.setEnabled(isConnected);
    }

    private void toggleCharging() {
        if (!isConnected) return;
        isCharging = !isCharging;
        chargingLabel.setText("Charging: " + (isCharging ? "ON" : "OFF"));
        chargingLabel.setForeground(isCharging ? Color.GREEN : Color.RED);
        chargeButton.setText(isCharging ? "Stop Charging" : "Start Charging");
        connectButton.setEnabled(!isCharging);
    }

    private class DrawingPanel extends JPanel {
        private BufferedImage shipImage;
       public DrawingPanel()
        {
            try {
                shipImage = ImageIO.read(getClass().getResource("/ship.png"));
                if (shipImage == null) {
                    throw new IOException("Ship image not found!");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
/*            g2d.setColor(Color.GREEN);
            g2d.fillRect(350, 200, 100, 50); // Ship*/
            if (shipImage != null) {
                g2d.drawImage(shipImage, 350, 200, 100, 200, this); // Prikaz slike broda
            } else {
                g2d.setColor(Color.RED);
                g2d.drawString("Ship image not found!", 350, 220);
            }
            g2d.setColor(Color.GRAY);
            g2d.fillRect(200, 220, 50, 30); // Charging platform
            g2d.setColor(Color.BLUE);
            g2d.drawLine(250, 235, 350, 225); // Cable
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChargingDashboard().setVisible(true));
    }
}
