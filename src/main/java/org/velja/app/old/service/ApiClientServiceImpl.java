package org.velja.app.old.service;

import lombok.SneakyThrows;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.velja.app.old.model.*;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

@Service
public class ApiClientServiceImpl implements ApiClientService {
    private static final String BASE_URL = "https://sokker.org/api";

    @Override
    public String login(String username, String password) {
        String loginUrl = BASE_URL + "/auth/login";
        String requestBody = String.format("{\"login\":\"%s\",\"password\":\"%s\",\"remember\":true}", username, password);
        HttpClient httpClient = HttpClient.newBuilder().build();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(loginUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Login response status: " + response.statusCode()); // Dodato logovanje

            if (response.statusCode() == 200) {
                Optional<String> sessionCookie = response.headers().allValues("set-cookie").stream()
                        .filter(cookie -> cookie.startsWith("PHPSESSID="))
                        .findFirst();

                if (sessionCookie.isPresent()) {
                    String phpSessionId = sessionCookie.get().split(";")[0].split("=")[1];
                    System.out.println("PHPSESSID: " + phpSessionId); // Dodato logovanje
                    return phpSessionId;
                } else {
                    System.err.println("PHPSESSID not found in response headers");
                    throw new RuntimeException("PHPSESSID not found in response headers");
                }
            } else {
                System.err.println("Login failed with status code: " + response.statusCode());
                throw new RuntimeException("Login failed with status code: " + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Error during login: " + e.getMessage());
            throw new RuntimeException("Error during login: " + e.getMessage(), e);
        }
    }

    @Override
    @SneakyThrows
    public Team fetchCurrentTeam(String phpSessionId) {
        if (phpSessionId == null) {
            System.err.println("PHPSESSID is null");
            throw new IllegalArgumentException("PHPSESSID cannot be null");
        }

        URL url = new URI(BASE_URL + "/current").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Cookie", "PHPSESSID=" + phpSessionId);

        int responseCode = conn.getResponseCode();
        System.out.println("Fetch team response status: " + responseCode); // Dodato logovanje
        if (responseCode == 200) {
            String responseBody = readResponse(conn);
            System.out.println("Fetch team response body: " + responseBody); // Dodato logovanje
            JSONObject json = new JSONObject(responseBody);
            JSONObject teamJson = json.getJSONObject("team");

            return new Team(
                    teamJson.getInt("id"),
                    teamJson.getString("name"),
                    teamJson.getDouble("rank"),
                    teamJson.getInt("rankPosition"),
                    teamJson.getString("emblem"),
                    teamJson.getJSONObject("country").getString("name")
            );
        }
        System.err.println("Failed to fetch team, status: " + responseCode);
        throw new RuntimeException("Failed to fetch team, status: " + responseCode);
    }

    @Override
    @SneakyThrows
    public List<PlayerElement> fetchPlayers(int teamId, String phpSessionId) {
        if (phpSessionId == null) {
            System.err.println("PHPSESSID is null for fetchPlayers");
            throw new IllegalArgumentException("PHPSESSID cannot be null");
        }

        URL url = new URI(BASE_URL + "/team/" + teamId + "/player").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Cookie", "PHPSESSID=" + phpSessionId);

        int responseCode = conn.getResponseCode();
        System.out.println("Fetch players response status for teamId " + teamId + ": " + responseCode); // Dodato logovanje
        if (responseCode == 200) {
            String responseBody = readResponse(conn);
            System.out.println("Fetch players response body: " + responseBody); // Dodato logovanje
            try {
                JSONObject json = new JSONObject(responseBody);
                JSONArray playersArray = json.getJSONArray("players");
                List<PlayerElement> players = new ArrayList<>();
                for (int i = 0; i < playersArray.length(); i++) {
                    JSONObject playerJson = playersArray.getJSONObject(i);
                    JSONObject infoObject = playerJson.getJSONObject("info");
                    int id = playerJson.getInt("id");
                    Name name = new Name();
                    JSONObject nameObject = infoObject.getJSONObject("name");
                    name.setName(nameObject.getString("name"));
                    name.setSurname(nameObject.getString("surname"));
                    name.setFull(nameObject.getString("full"));
                    JSONObject skillsObject = infoObject.getJSONObject("skills");
                    Skills skills = new Skills(
                            skillsObject.getInt("form"),
                            skillsObject.getInt("tacticalDiscipline"),
                            skillsObject.getInt("experience"),
                            skillsObject.getInt("teamwork"),
                            skillsObject.getInt("stamina"),
                            skillsObject.getInt("pace"),
                            skillsObject.getInt("striker"),
                            skillsObject.getInt("defending"),
                            skillsObject.getInt("technique"),
                            skillsObject.getInt("passing"),
                            skillsObject.getInt("playmaking"),
                            skillsObject.getInt("keeper")
                    );
                    JSONObject characteristicsObject = infoObject.getJSONObject("characteristics");
                    Characteristics characteristics = new Characteristics(
                            characteristicsObject.getInt("age"),
                            characteristicsObject.getInt("height"),
                            characteristicsObject.getDouble("bmi"),
                            characteristicsObject.getDouble("weight")
                    );
                    Info info = new Info(name, skills, characteristics);
                    PlayerElement playerElement = new PlayerElement(id, info);
                    players.add(playerElement);
                }
                System.out.println("Fetched " + players.size() + " players"); // Dodato logovanje
                return players;
            } catch (Exception e) {
                System.err.println("Error parsing players JSON: " + e.getMessage());
                throw new RuntimeException("Error parsing players JSON", e);
            }
        }
        System.err.println("Failed to fetch players, status: " + responseCode);
        throw new RuntimeException("Failed to fetch players, status: " + responseCode);
    }

    @Override
    @SneakyThrows
    public List<TrainingReport.TrainingEntry> getTrainingReports(PlayerElement player, String phpSessionId) {
        if (phpSessionId == null) {
            System.err.println("PHPSESSID is null for getTrainingReports");
            throw new IllegalArgumentException("PHPSESSID cannot be null");
        }

        String urlString = BASE_URL + "/training/" + player.getId() + "/report";
        URL url = new URI(urlString).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Cookie", "PHPSESSID=" + phpSessionId);

        int responseCode = conn.getResponseCode();
        System.out.println("Fetch training reports response status for playerId " + player.getId() + ": " + responseCode); // Dodato logovanje
        if (responseCode == 200) {
            String responseBody = readResponse(conn);
            System.out.println("Fetch training reports response body: " + responseBody); // Dodato logovanje
            JSONObject json = new JSONObject(responseBody);
            JSONArray reportsArray = json.getJSONArray("reports");
            List<TrainingReport.TrainingEntry> reports = new ArrayList<>();
            for (int i = 0; i < reportsArray.length(); i++) {
                JSONObject trainingEntryObject = reportsArray.getJSONObject(i);
                JSONObject skillsObject = trainingEntryObject.getJSONObject("skills");
                JSONObject skillsChangeObject = trainingEntryObject.getJSONObject("skillsChange");
                JSONObject trainingDayObject = trainingEntryObject.getJSONObject("day");
                JSONObject trainingTypeObject = trainingEntryObject.getJSONObject("type");

                TrainingReport.TrainingType trainingType = new TrainingReport.TrainingType(
                        trainingTypeObject.getInt("code"),
                        trainingTypeObject.getString("name")
                );
                TrainingReport.TrainingDay trainingDay = new TrainingReport.TrainingDay(
                        trainingDayObject.getInt("season"),
                        trainingDayObject.getInt("week"),
                        trainingDayObject.getInt("seasonWeek"),
                        trainingDayObject.getInt("day")
                );
                Skills actualSkills = new Skills(
                        skillsObject.getInt("form"),
                        skillsObject.getInt("tacticalDiscipline"),
                        skillsObject.getInt("experience"),
                        skillsObject.getInt("teamwork"),
                        skillsObject.getInt("stamina"),
                        skillsObject.getInt("pace"),
                        skillsObject.getInt("striker"),
                        skillsObject.getInt("defending"),
                        skillsObject.getInt("technique"),
                        skillsObject.getInt("passing"),
                        skillsObject.getInt("playmaking"),
                        skillsObject.getInt("keeper")
                );
                Skills skillsChange = new Skills(
                        skillsChangeObject.getInt("form"),
                        skillsChangeObject.getInt("tacticalDiscipline"),
                        skillsChangeObject.getInt("experience"),
                        skillsChangeObject.getInt("teamwork"),
                        skillsChangeObject.getInt("stamina"),
                        skillsChangeObject.getInt("pace"),
                        skillsChangeObject.getInt("striker"),
                        skillsChangeObject.getInt("defending"),
                        skillsChangeObject.getInt("technique"),
                        skillsChangeObject.getInt("passing"),
                        skillsChangeObject.getInt("playmaking"),
                        skillsChangeObject.getInt("keeper")
                );
                TrainingReport.TrainingEntry trainingEntry = new TrainingReport.TrainingEntry(
                        trainingDay, actualSkills, skillsChange, trainingType
                );
                reports.add(trainingEntry);
            }
            System.out.println("Fetched " + reports.size() + " training reports"); // Dodato logovanje
            return reports;
        }
        System.err.println("Failed to fetch training reports, status: " + responseCode);
        throw new RuntimeException("Failed to fetch training reports, status: " + responseCode);
    }

    @Override
    @SneakyThrows
    public List<TrainingReport.TrainingEntry> getTrainingReportsNonAdmin(PlayerElement player, String phpSessionId) {
        if (phpSessionId == null) {
            System.err.println("PHPSESSID is null for getTrainingReportsNonAdmin");
            throw new IllegalArgumentException("PHPSESSID cannot be null");
        }

        String urlString = BASE_URL + "/training";
        URL url = new URI(urlString).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Cookie", "PHPSESSID=" + phpSessionId);

        int responseCode = conn.getResponseCode();
        System.out.println("Fetch non-admin training reports response status for playerId " + player.getId() + ": " + responseCode); // Dodato logovanje
        if (responseCode == 200) {
            String responseBody = readResponse(conn);
            System.out.println("Fetch non-admin training reports response body: " + responseBody); // Dodato logovanje
            JSONObject json = new JSONObject(responseBody);
            JSONArray reportsArray = json.getJSONArray("players");
            List<TrainingReport.TrainingEntry> reports = new ArrayList<>();
            for (int i = 0; i < reportsArray.length(); i++) {
                JSONObject trainingEntryObject2 = reportsArray.getJSONObject(i);
                if (trainingEntryObject2.getInt("id") == player.getId()) {
                    JSONObject trainingEntryObject = trainingEntryObject2.getJSONObject("report");
                    JSONObject skillsObject = trainingEntryObject.getJSONObject("skills");
                    JSONObject skillsChangeObject = trainingEntryObject.getJSONObject("skillsChange");
                    JSONObject trainingDayObject = trainingEntryObject.getJSONObject("day");
                    JSONObject trainingTypeObject = trainingEntryObject.getJSONObject("type");

                    TrainingReport.TrainingDay trainingDay = new TrainingReport.TrainingDay(
                            trainingDayObject.getInt("season"),
                            trainingDayObject.getInt("week"),
                            trainingDayObject.getInt("seasonWeek"),
                            trainingDayObject.getInt("day")
                    );
                    TrainingReport.TrainingType trainingType = new TrainingReport.TrainingType(
                            trainingTypeObject.getInt("code"),
                            trainingTypeObject.getString("name")
                    );
                    Skills actualSkills = new Skills(
                            skillsObject.getInt("form"),
                            skillsObject.getInt("tacticalDiscipline"),
                            skillsObject.getInt("experience"),
                            skillsObject.getInt("teamwork"),
                            skillsObject.getInt("stamina"),
                            skillsObject.getInt("pace"),
                            skillsObject.getInt("striker"),
                            skillsObject.getInt("defending"),
                            skillsObject.getInt("technique"),
                            skillsObject.getInt("passing"),
                            skillsObject.getInt("playmaking"),
                            skillsObject.getInt("keeper")
                    );
                    Skills skillsChange = new Skills(
                            skillsChangeObject.getInt("form"),
                            skillsChangeObject.getInt("tacticalDiscipline"),
                            skillsChangeObject.getInt("experience"),
                            skillsChangeObject.getInt("teamwork"),
                            skillsChangeObject.getInt("stamina"),
                            skillsChangeObject.getInt("pace"),
                            skillsChangeObject.getInt("striker"),
                            skillsChangeObject.getInt("defending"),
                            skillsChangeObject.getInt("technique"),
                            skillsChangeObject.getInt("passing"),
                            skillsChangeObject.getInt("playmaking"),
                            skillsChangeObject.getInt("keeper")
                    );
                    TrainingReport.TrainingEntry trainingEntry = new TrainingReport.TrainingEntry(
                            trainingDay, actualSkills, skillsChange, trainingType
                    );
                    reports.add(trainingEntry);
                }
            }
            System.out.println("Fetched " + reports.size() + " non-admin training reports"); // Dodato logovanje
            return reports;
        }
        System.err.println("Failed to fetch non-admin training reports, status: " + responseCode);
        throw new RuntimeException("Failed to fetch non-admin training reports, status: " + responseCode);
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8);
        String response = scanner.useDelimiter("\\A").next();
        scanner.close();
        return response;
    }
}