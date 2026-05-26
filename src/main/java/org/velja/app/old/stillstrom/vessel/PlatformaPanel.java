package org.velja.app.old.stillstrom.vessel;

import lombok.Getter;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;

import static org.velja.app.old.stillstrom.vessel.ChargerApp.createAndShowGUI;

public class PlatformaPanel extends JPanel {
    private final JButton platformButton;
    private final JLabel infoTextArea;
    private final int initialDistance;
    @Getter
    private boolean clicked = false;

    public PlatformaPanel(String name, int x, int y) {
        this.initialDistance = y;
        this.setLayout(new BorderLayout());
        this.setOpaque(false);

        platformButton = new JButton(name);
        platformButton.setBorder(new LineBorder(Color.WHITE, 2));
        platformButton.setEnabled(false); // Početno je disabled
        platformButton.setBackground(Color.GRAY);
        platformButton.addActionListener(e -> openPlatformWindow());

        infoTextArea = new JLabel();
        infoTextArea.setText(" Distance to charger: 500");
        infoTextArea.setOpaque(false); // Omogućava transparentnost

        this.add(platformButton, BorderLayout.WEST);
        this.add(infoTextArea, BorderLayout.CENTER);
        this.setBounds(x, y, 300, 50); // Podesi veličinu panela za platformu
    }

    public void updateDistance(int brodX) {
        int distance = Math.abs(brodX - initialDistance);
        infoTextArea.setText(" Distance to charger: " + distance);

        if (distance <= 300) {
            infoTextArea.setForeground(Color.GREEN);
            platformButton.setForeground(Color.green);
            platformButton.setBackground(Color.GRAY);
            platformButton.setEnabled(true);
            platformButton.repaint();
            platformButton.revalidate();
        } else {
            infoTextArea.setForeground(Color.RED);
            platformButton.setForeground(Color.red);
            platformButton.setBackground(Color.darkGray);
            platformButton.setEnabled(false);
        }
    }

    public void enableButton() {
        platformButton.setEnabled(true);
    }
    public void disableButton() {
        platformButton.setEnabled(false);
    }

    private void openPlatformWindow() {
        if (!clicked) {
            clicked = true;

            // Zatvori trenutni prozor
            SwingUtilities.getWindowAncestor(this).dispose(); // Zatvori trenutni prozor (splashFrame)

            // Otvori novi prozor
            createAndShowGUI(); // Otvori sledeći prozor
        }
    }

    public int getDistance() {
        return Math.abs(initialDistance - this.getY());
    }
}