package org.velja.app.old;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

@SpringBootApplication
public class TrainingApp implements CommandLineRunner {
    public static void main(String[] args) {
        SpringApplication.run(TrainingApp.class, args);
    }

    @Override
    public void run(String... args) {
        try {
            String url = "http://localhost:8080/api/login";
            openBrowser(url);
        } catch (Exception e) {
            System.err.println("Ručno otvaranje potrebno: http://localhost:8080/api/login");
        }
    }

    private void openBrowser(String url) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        Runtime rt = Runtime.getRuntime();

        if (os.contains("win")) {
            rt.exec("rundll32 url.dll,FileProtocolHandler " + url);
        } else if (os.contains("mac")) {
            rt.exec("open " + url);
        } else if (os.contains("nix") || os.contains("nux")) {
            String[] browsers = { "xdg-open", "google-chrome", "firefox" };
            String browser = Arrays.stream(browsers)
                    .filter(cmd -> new File("/usr/bin/" + cmd).exists())
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Pretraživač nije pronađen"));
            rt.exec(new String[]{browser, url});
        } else {
            throw new UnsupportedOperationException("Nepodržan operativni sistem: " + os);
        }
    }
}