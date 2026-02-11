package org.velja.app.stillstrom.vessel;

import lombok.Getter;
import lombok.SneakyThrows;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;

public class BrodPanel extends JPanel {
    @Getter
    private int y = 520; // Početna pozicija broda
    private static final int BROD_WIDTH = 50;
    private static final int BROD_HEIGHT = 70;
    private Image shipImage;


    @SneakyThrows
    public BrodPanel() {
        this.setPreferredSize(new Dimension(50, 70)); // Dimenzije panela
        // Učitajte sliku broda
        shipImage = ImageIO.read(getClass().getResource("/ship2_converted.png"));
    }

    public void updatePosition() {
        y -= 1; // Usporeno kretanje broda (malo smanjeno pomeranje)
        repaint();

    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Nacrtajte sliku broda kao pozadinu
        g.drawImage(shipImage, 0, 0, getWidth(), getHeight(), this);
    }

}