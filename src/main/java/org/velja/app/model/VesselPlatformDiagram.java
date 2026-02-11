package org.velja.app.model;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;

public class VesselPlatformDiagram extends JPanel {
    private boolean is5GOn = true;
    private final JButton toggle5GButton;

    public VesselPlatformDiagram() {
        setPreferredSize(new Dimension(1000, 800));
        setLayout(null);

        toggle5GButton = new JButton("5G ON");
        toggle5GButton.setBounds(380, 210, 80, 30);
        toggle5GButton.addActionListener(e -> {
            is5GOn = !is5GOn;
            toggle5GButton.setText(is5GOn ? "5G ON" : "5G OFF");
            repaint();
        });
        add(toggle5GButton);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // Left Side - Vessel
        g2d.setColor(Color.BLUE);
        g2d.setStroke(new BasicStroke(3)); // Podesi debljinu ivice
        drawRectangle(g2d, 100, 200, 160, 50, "      Vessel Server");
        g2d.setColor(Color.MAGENTA);
        drawRectangle(g2d, 130, 400, 100, 50, " IAS SCADA");
        drawRectangle(g2d, 400, 400, 160, 50, "              PLC");
        g2d.setStroke(new BasicStroke(1)); // Podesi debljinu ivice


        drawDoubleBorderRectangle(g2d, 50, 50, 60, 60, "App 1");
        drawDoubleBorderRectangle(g2d, 130, 50, 60, 60, "App 2");
        drawDoubleBorderRectangle(g2d, 210, 50, 60, 60, "App 3");

        g2d.drawLine(40, 320, 320, 320); // horizontal line
        drawRectangle(g2d, 50, 340, 70, 50, "...");
        drawRectangle(g2d, 240, 340, 70, 50, "DP");
        drawRectangle(g2d, 50, 400, 70, 50, "GPS");
        drawRectangle(g2d, 50, 460, 70, 50, "SENSORS");
        drawRectangle(g2d, 240, 460, 70, 50, "PMS");
        drawRectangle(g2d, 240, 400, 70, 50, "BMS");
        g2d.drawLine(40, 520, 320, 520); // horizontal line


        g2d.drawLine(340, 320, 620, 320); // horizontal top left line
        drawRectangle(g2d, 350, 340, 80, 50, "CCTV");
        drawRectangle(g2d, 530, 340, 80, 50, "FIRE");
        drawRectangle(g2d, 350, 460, 80, 50, "11KV");
        drawRectangle(g2d, 530, 460, 80, 50, "CMCS");
        g2d.drawLine(340, 520, 620, 520); // horizontal bottom left line
        g2d.setColor(Color.BLACK);
        g2d.drawLine(640, 30, 640, 620); // vertical line


        // Right Side - Platform
        g2d.setColor(Color.ORANGE);
        g2d.drawLine(690, 150, 860, 150); // horizontal top right line
        g2d.setStroke(new BasicStroke(3)); // Podesi debljinu ivice
        drawRectangle(g2d, 700, 200, 160, 50, "      Platform Server");
        drawRectangle(g2d, 700, 400, 160, 50, "        SICAM SCADA");
        g2d.setStroke(new BasicStroke(1)); // Podesi debljinu ivice

        g2d.drawLine(690, 520, 860, 520); // horizontal bottom right line

        g2d.setColor(Color.PINK);
        g2d.setStroke(new BasicStroke(3)); // Podesi debljinu ivice
        drawCloud(g2d, 720, 30, "AZURE");
        g2d.setStroke(new BasicStroke(1)); // Podesi debljinu ivice

        // Connections
        g2d.setColor(Color.green);
        g2d.drawLine(262, 225, 378, 225); // Vessel Server to 5G Button
        g2d.drawLine(462, 225, 698, 225); // 5G Button to Platform Server
        g2d.drawLine(262, 222, 720, 65); //Vessel Server to Azure (Green)

        g2d.setColor(Color.BLUE);
        // Koordinate linije
        int x1 = 230, y1 = 252, x2 = 520, y2 = 398;
        g2d.drawLine(x1, y1, x2, y2); //Vessel Server to PLC

        // Računanje sredine linije
        int midX = (x1 + x2) / 2;
        int midY = (y1 + y2) / 2;

        // Računanje ugla linije
        double angle = Math.atan2(y2 - y1, x2 - x1);

        // Postavi font
        g2d.setFont(new Font("Arial", Font.BOLD, 14));

        // Crtanje rotiranog teksta
        Graphics2D g2dRotated = (Graphics2D) g2d.create();
        g2dRotated.translate(midX, midY);
        g2dRotated.rotate(angle);
        g2dRotated.drawString("OPC UA", -20, -5); // Podešavanje položaja teksta
        g2dRotated.dispose();

        // Koordinate linije
        int xx1 = 200, yy1 = 252, xx2 = 350, yy2 = 365;
        g2d.drawLine(xx1, yy1, xx2, yy2); //Vessel Server to CCTV

        // Računanje sredine linije
        int midXX = (xx1 + xx2) / 2;
        int midYY= (yy1 + yy2) / 2;

        // Računanje ugla linije
        double angle2 = Math.atan2(yy2 - yy1, xx2 - xx1);

        // Postavi font
        g2d.setFont(new Font("Arial", Font.BOLD, 14));

        // Crtanje rotiranog teksta
        Graphics2D g2dRotated2 = (Graphics2D) g2d.create();
        g2dRotated2.translate(midXX, midYY);
        g2dRotated2.rotate(angle2);
        g2dRotated2.drawString("SAMBA|FTP", -20, -5); // Podešavanje položaja teksta
        g2dRotated2.dispose();

        g2d.drawLine(180, 252, 180, 398); //Vessel Server to IAS SCADA
        g2d.drawLine(170, 198, 80, 112); //Vessel Server to sq1
        g2d.drawLine(180, 198, 160, 112); //Vessel Server to sq1
        g2d.drawLine(190, 198, 240, 112); //Vessel Server to sq1
        g2d.drawLine(560, 422, 698, 422); // PLC to SICAM SCADA
        // Crtanje strelica na oba kraja
        drawArrowHead(g2d, 560, 422, 698, 422);
        drawArrowHead(g2d, 698, 422, 560, 422);
        g2d.setColor(Color.orange);
        // Koordinate linije
        x1 = 770;y1 = 252; x2 = 770; y2 = 398;
        g2d.drawLine(x1, y1, x2, y2); // Platform Server to SICAM SCADA

        // Računanje sredine linije
         midX = (x1 + x2) / 2;
         midY = (y1 + y2) / 2;

        // Računanje ugla linije
         angle = Math.atan2(y2 - y1, x2 - x1);

        // Postavi font
        g2d.setFont(new Font("Arial", Font.BOLD, 14));

        // Crtanje rotiranog teksta
        g2dRotated = (Graphics2D) g2d.create();
        g2dRotated.translate(midX, midY);
        g2dRotated.rotate(angle);
        g2dRotated.drawString("OPC UA", -20, -5); // Podešavanje položaja teksta
        g2dRotated.dispose();

        g2d.drawLine(770, 198, 770, 80); // Platform Server to Azure

        // Postavljanje fonta i boje za tekst
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        g2d.setColor(Color.BLACK);

// Računanje sredine i ispis teksta ispod linija
        FontMetrics metrics = g2d.getFontMetrics();
        int vesselTextWidth = metrics.stringWidth("VESSEL");
        int platformTextWidth = metrics.stringWidth("PLATFORM");

// X koordinate za centriranje teksta ispod linija
        int vesselX = (40 + 620) / 2 - vesselTextWidth / 2;
        int platformX = (690 + 860) / 2 - platformTextWidth / 2;

// Y koordinata ispod linije (520 + 15 piksela razmaka)
        int textY = 540 + metrics.getHeight();

        g2d.drawString("VESSEL", vesselX, textY);
        g2d.drawString("PLATFORM", platformX, textY);

        g2d.setColor(is5GOn ? Color.GREEN : Color.RED);
        g2d.drawLine(262, 225, 378, 225); // Green when 5G ON, Gray when OFF
        g2d.drawLine(462, 225, 698, 225); // Green when 5G ON, Gray when OFF


    }

    private void drawArrowHead(Graphics2D g2d, int x1, int y1, int x2, int y2) {
        int arrowSize = 10; // Veličina strelice
        double angle = Math.atan2(y2 - y1, x2 - x1); // Ugao linije

        AffineTransform oldTransform = g2d.getTransform();
        g2d.translate(x2, y2);
        g2d.rotate(angle);

        // Crtanje strelice (oblik slova V)
        g2d.drawLine(0, 0, -arrowSize, -arrowSize / 2);
        g2d.drawLine(0, 0, -arrowSize, arrowSize / 2);

        g2d.setTransform(oldTransform); // Vraćanje originalne transformacije
    }

    private void drawRectangle(Graphics2D g2d, int x, int y, int width, int height, String text)
    {
        g2d.drawRect(x, y, width, height);
        g2d.drawString(text, x + 10, y + height / 2);
    }

    private void drawDoubleBorderRectangle(Graphics2D g2d, int x, int y, int width, int height, String text) {
        g2d.drawRect(x, y, width, height);
        g2d.drawRect(x + 5, y + 5, width - 10, height - 10);
        g2d.drawString(text, x + 10, y + height / 2);
    }

    private void drawCloud(Graphics2D g2d, int x, int y, String text) {
        g2d.drawOval(x, y, 100, 50);
        g2d.drawString(text, x + 30, y + 30);
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Vessel - Platform Data Flow Diagram");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new VesselPlatformDiagram());
        frame.pack();
        frame.setVisible(true);
    }
}
