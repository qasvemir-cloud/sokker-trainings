package org.velja.app.old.stillstrom.vessel;

import lombok.SneakyThrows;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class SplashScreen extends JFrame {

    @SneakyThrows
    public SplashScreen() {
        setTitle("Splash Screen");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);

        // Kreiramo JLayeredPane da bismo imali više slojeva
        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setPreferredSize(new Dimension(800, 600));

        // Postavite SplashPanel sa pozadinom na najniži sloj (pozadina)
        SplashPanel splashPanel = new SplashPanel();
        splashPanel.setBounds(0, 0, 800, 600);
        layeredPane.add(splashPanel, JLayeredPane.DEFAULT_LAYER);  // Donji sloj

        // Kreirajte brod i platforme
        BrodPanel brodPanel = new BrodPanel();
        brodPanel.setBounds(250, 520, 50, 70); // Postavite brod

        PlatformaPanel platforma1 = new PlatformaPanel("Open Platform1 ", 500, 400);
        platforma1.setBounds(500, 400, 300, 50);
        platforma1.disableButton();

        PlatformaPanel platforma2 = new PlatformaPanel("Open Platform2 ", 50, 30);
        platforma2.setBounds(50, 160, 300, 50);
        platforma2.disableButton();

        // Dodajte brod i platforme na viši sloj
        layeredPane.add(brodPanel, JLayeredPane.PALETTE_LAYER);  // Viši sloj za brod
        layeredPane.add(platforma1, JLayeredPane.MODAL_LAYER);  // Viši sloj za platforme
        layeredPane.add(platforma2, JLayeredPane.MODAL_LAYER);  // Viši sloj za platforme

        add(layeredPane);
        setLocationRelativeTo(null); // Centrirajte ekran
        setVisible(true);

        // Pokreni animaciju pomeranja broda
        startAnimation(brodPanel, platforma1, platforma2, this);
    }

    private static void startAnimation(BrodPanel brod, PlatformaPanel platform1, PlatformaPanel platform2, JFrame splashFrame) {
        Timer timer = new Timer(150, e -> {
            brod.updatePosition(); // Pomeri brod
            platform1.updateDistance(brod.getY()); // Ažuriraj udaljenost od prve platforme
            platform2.updateDistance(brod.getY()); // Ažuriraj udaljenost od druge platforme

            if (platform1.getDistance() <= 300) {
                platform1.enableButton();
            } else {
                platform1.disableButton();
            }

            if (platform2.getDistance() <= 300) {
                platform2.enableButton();
            } else {
                platform2.disableButton();
            }

            brod.repaint();
            splashFrame.repaint();
        });
        timer.start();
    }

    private static class SplashPanel extends JPanel {
        private Image splashImage;

        public SplashPanel() {
            setSize(800, 600);
            try {
                splashImage = ImageIO.read(getClass().getResource("/windpark.png"));
                if (splashImage == null) {
                    throw new IOException("Splash image not found!");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            // Nacrtajte sliku splash ekrana kao pozadinu
            g.drawImage(splashImage, 0, 0, getWidth(), getHeight(), this);
        }
    }
}