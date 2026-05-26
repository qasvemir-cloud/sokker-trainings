package org.velja.app.old;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import javax.swing.*;
import java.awt.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Scanner;

public class WeatherApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(WeatherApp::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("Weather Forecast");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 600);
        frame.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel latLabel = new JLabel("Lat:");
        JTextField latField = new JTextField("55.6761", 10);
        JLabel lonLabel = new JLabel("Long:");
        JTextField lonField = new JTextField("12.5683", 10);

        JButton getDataButton = new JButton("Get forecast data");
        SkillLabelHelper.styleButton(getDataButton);
        JTextArea locationInfo = new JTextArea(6, 40);
        locationInfo.setEditable(false);
        JTextArea forecastData = new JTextArea(6, 40);
        forecastData.setEditable(false);

        JLabel windLimitLabel = new JLabel("Enter wind speed limit:");
        JTextField windLimitField = new JTextField("15.00");
        JLabel warningLabel = new JLabel(" ");
        warningLabel.setForeground(Color.RED);
        warningLabel.setPreferredSize(new Dimension(400, 40));

        gbc.gridx = 0;
        gbc.gridy = 1;
        frame.add(latLabel, gbc);
        gbc.gridx = 1;
        frame.add(latField, gbc);
        gbc.gridx = 2;
        frame.add(lonLabel, gbc);
        gbc.gridx = 3;
        frame.add(lonField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 4;
        frame.add(getDataButton, gbc);

        gbc.gridy = 3;
        frame.add(new JLabel("Location Info:"), gbc);
        gbc.gridy = 4;
        frame.add(new JScrollPane(locationInfo), gbc);

        gbc.gridy = 5;
        frame.add(new JLabel("Forecast Data:"), gbc);
        gbc.gridy = 6;
        frame.add(new JScrollPane(forecastData), gbc);

        gbc.gridy = 7;
        frame.add(windLimitLabel, gbc);
        gbc.gridy = 8;
        frame.add(windLimitField, gbc);

        gbc.gridy = 0;
        frame.add(warningLabel, gbc);

        getDataButton.addActionListener(e -> {
            String lat = latField.getText();
            String lon = lonField.getText();
         //   String apiUrl = "http://api.weatherstack.com/current?access_key=d77ea51283d28e6f14b7ba169f65e6a7&query=" + lat + "," + lon;
            String apiUrl = "https://api.weatherapi.com/v1/forecast.json?q=" + lat + "," + lon+"&days=1&key=1008a6a889f64337ab1143949251002";

            try {
                String response = getApiResponse(apiUrl);
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(response);

                JsonNode location = jsonNode.get("location");
                JsonNode current = jsonNode.get("current");

                if (location != null && current != null) {
                    locationInfo.setText("Location: " + location.get("name").asText() + "\n" +
                            "Country: " + location.get("country").asText() + "\n" +
                            "Region: " + location.get("region").asText() + "\n" +
                            "Local Time: " + location.get("localtime").asText());

                   // int windSpeed = current.get("wind_speed").asInt();
                    int windSpeed = current.get("wind_kph").asInt();
/*                    forecastData.setText("Temperature: " + current.get("temperature").asText() + "°C\n" +
                            "Description: " + current.get("weather_descriptions").get(0).asText() + "\n" +
                            "Wind Speed: " + windSpeed + " km/h\n" +
                            "Pressure: " + current.get("pressure").asText() + " hPa");*/
                    forecastData.setText("Temperature: " + current.get("temp_c").asText() + "°C\n" +
                            "Gust: " + current.get("gust_kph").asText() + "\n" +
                            "Wind Speed: " + windSpeed + " km/h\n" +
                            "Pressure: " + current.get("pressure_mb").asText() + " hPa");

                    String windLimitText = windLimitField.getText();
                    if (!windLimitText.isEmpty()) {
                        try {
                            double windLimit = Double.parseDouble(windLimitText);
                            if (windSpeed > windLimit) {
                                warningLabel.setForeground(Color.RED);
                                warningLabel.setText("<html>Warning: Wind speed higher than expected!<br>Check if conditions are good for charging.<br/></html>");
                            } else {
                                warningLabel.setForeground(Color.BLUE);
                                warningLabel.setText("Conditions looks OK");
                            }
                        } catch (NumberFormatException ex) {
                            warningLabel.setText("Invalid wind speed limit!");
                        }
                    }
                }
            } catch (Exception ex) {
                locationInfo.setText("Error fetching data");
                forecastData.setText("");
                warningLabel.setText("");
            }
        });

        frame.setVisible(true);
    }

    @SneakyThrows
    private static String getApiResponse(String apiUrl) {
        URL url = new URI(apiUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        Scanner scanner = new Scanner(conn.getInputStream());
        StringBuilder response = new StringBuilder();
        while (scanner.hasNext()) {
            response.append(scanner.nextLine());
        }
        scanner.close();
        return response.toString();
    }
}