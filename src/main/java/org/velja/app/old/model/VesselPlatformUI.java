package org.velja.app.old.model;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class VesselPlatformUI extends JFrame {
    private boolean is5GOn = true;

    public VesselPlatformUI() {
        setTitle("Vessel and Platform Interaction");
        setSize(1600, 900); // Veliki prozor umesto fullscreen-a
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JPanel leftPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawVesselPanel((Graphics2D) g);
            }
        };

        JPanel rightPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawPlatformPanel((Graphics2D) g);
            }
        };

        leftPanel.setPreferredSize(new Dimension(800, 900));
        rightPanel.setPreferredSize(new Dimension(800, 900));

        add(leftPanel, BorderLayout.WEST);
        add(rightPanel, BorderLayout.EAST);

        JButton btn5G = new JButton("5G ON");
        btn5G.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                is5GOn = !is5GOn;
                btn5G.setText(is5GOn ? "5G ON" : "5G OFF");
                leftPanel.repaint();
                rightPanel.repaint();
            }
        });
        add(btn5G, BorderLayout.SOUTH);
    }

    private void drawVesselPanel(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(Color.BLACK);
        g.drawRect(200, 100, 200, 100);
        g.drawString("Vessel Server", 250, 150);

        g.drawRect(200, 250, 150, 75);
        g.drawString("PLC", 250, 290);

        g.drawRect(200, 400, 200, 100);
        g.drawString("IAS SCADA", 250, 450);

        g.setColor(is5GOn ? Color.GREEN : Color.GRAY);
        g.drawLine(400, 150, 600, 150);
    }

    private void drawPlatformPanel(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(Color.BLACK);
        g.drawRect(200, 100, 200, 100);
        g.drawString("Platform Server", 250, 150);

        g.drawRect(250, 250, 150, 75);
        g.drawString("SICAM SCADA", 270, 290);

        g.setColor(Color.BLUE);
        g.drawLine(400, 150, 600, 150);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            VesselPlatformUI frame = new VesselPlatformUI();
            frame.setVisible(true);
        });
    }
}
