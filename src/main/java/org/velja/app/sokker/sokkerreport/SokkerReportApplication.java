package org.velja.app.sokker.sokkerreport;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.awt.*;
import java.io.IOException;
import java.net.URI;

@SpringBootApplication
public class SokkerReportApplication {
    private static final String APP_URL = "http://localhost:7071/index.html";

    @Value("${sokker.open-browser:true}")
    private boolean openBrowser;
    public static void main(String[] args) {
        freePortIfInUse(7071);
        SpringApplication.run(SokkerReportApplication.class, args);
    }

    private static void freePortIfInUse(int port) {
        try {
            // Try to free the port by killing processes listening on it
            Process p = new ProcessBuilder("sh", "-c", "lsof -ti:" + port + " | xargs -r kill -9").start();
            p.waitFor();
            Thread.sleep(500);
        } catch (Exception ignored) {
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void openBrowser() {
        if (!openBrowser || System.getenv("PORT") != null) {
            return;
        }
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("mac")) {
                new ProcessBuilder("open", APP_URL).start();
                return;
            }
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", APP_URL).start();
                return;
            }
            if (os.contains("nix") || os.contains("nux")) {
                new ProcessBuilder("xdg-open", APP_URL).start();
                return;
            }
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(APP_URL));
            }
        } catch (IOException | RuntimeException ignored) {
            System.out.println("Otvori rucno: " + APP_URL);
        }
    }
}
