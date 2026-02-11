package org.velja.app.api;

import lombok.SneakyThrows;
import org.velja.app.model.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class ApiClient {
    private static final String BASE_URL = "https://sokker.org/api";
    public static String phpSessionId = null;  // Čuvamo PHPSESSID nakon logina

    // Metod za logovanje i čuvanje PHPSESSID
    public static String login(String username, String password) {
        String loginUrl = "https://sokker.org/api/auth/login";
        String requestBody = String.format("{\"login\":\"%s\",\"password\":\"%s\",\"remember\":true}", username, password);
        HttpClient httpClient = HttpClient.newBuilder().build();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(loginUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // Tražimo PHPSESSID u Set-Cookie headeru
                Optional<String> sessionCookie = response.headers().allValues("set-cookie").stream()
                        .filter(cookie -> cookie.startsWith("PHPSESSID="))
                        .findFirst();

                if (sessionCookie.isPresent()) {
                    // Parsiramo PHPSESSID i vracamo kao rezultat
                    return sessionCookie.get().split(";")[0].split("=")[1];
                } else {
                    System.out.println("Nema PHPSESSID u odgovorima!");
                    return null;
                }
            } else {
                System.out.println("Login failed! Status code: " + response.statusCode());
                return null;
            }
        } catch (IOException | InterruptedException e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    // Metod za preuzimanje trenutnog tima
    @SneakyThrows
    public static Team fetchCurrentTeam(String phpSessionId) {
        if (phpSessionId == null) {
            System.out.println("Greška: Nema PHPSESSID, potrebno se prvo ulogovati!");
            return null;
        }

        try {
            URL url = new URI(BASE_URL + "/current").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Cookie", "PHPSESSID=" + phpSessionId);

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                String responseBody = readResponse(conn);
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
            } else {
                System.out.println("Greška pri preuzimanju tima, status: " + responseCode);
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    // Metod za preuzimanje liste igrača
    @SneakyThrows
    public static List<PlayerElement> fetchPlayers(int teamId) {
        if (phpSessionId == null) {
            System.out.println("Greška: Nema PHPSESSID, potrebno se prvo ulogovati!");
            return new ArrayList<>();
        }

        try {
            URL url = new URI(BASE_URL + "/team/" + teamId + "/player").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Cookie", "PHPSESSID=" + phpSessionId);

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                String responseBody = readResponse(conn);
                JSONObject json = new JSONObject(responseBody);
                JSONArray playersArray = json.getJSONArray("players");
                List<PlayerElement> players = new ArrayList<>();
                for (int i = 0; i < playersArray.length(); i++) {
                    JSONObject playerJson = playersArray.getJSONObject(i);
                    JSONObject infoObject = playerJson.getJSONObject("info");
                    int id = playerJson.getInt("id");
                    Name name = new Name();
                    JSONObject nameObject = infoObject.getJSONObject("name");
                    name.setName(nameObject.getString("name"));name.setSurname(nameObject.getString("surname"));name.setFull(nameObject.getString("full"));
                    JSONObject skillsObject = infoObject.getJSONObject("skills");
                    Skills skills = new Skills(skillsObject.getInt("form"),
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
                    Characteristics characteristics = new Characteristics(characteristicsObject.getInt("age"),
                            characteristicsObject.getInt("height"),characteristicsObject.getDouble("bmi"),
                            characteristicsObject.getDouble("weight"));
                    Info info = new Info(name,skills, characteristics);
                    PlayerElement playerElement = new PlayerElement(id,info);
                    players.add(playerElement);
                }
                return players;
            } else {
                System.out.println("Greška pri preuzimanju igrača, status: " + responseCode);
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        return new ArrayList<>();
    }

    @SneakyThrows
    public static List<TrainingReport.TrainingEntry> getTrainingReports(PlayerElement player) throws IOException {
        String urlString = BASE_URL + "/training/" + player.getId() + "/report";
        URL url = new URI(urlString).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Cookie", "PHPSESSID=" + phpSessionId);

        String responseBody = readResponse(conn);

        JSONObject json = new JSONObject(responseBody);
        JSONArray reportsArray = json.getJSONArray("reports");
        TrainingReport.TrainingEntry trainingEntry;
        List<TrainingReport.TrainingEntry> reports = new ArrayList<>();
        for (int i = 0; i < reportsArray.length(); i++) {

            JSONObject trainingEntryObject= reportsArray.getJSONObject(i);

           JSONObject skillsObject = trainingEntryObject.getJSONObject("skills");
           JSONObject skillsChangeObject = trainingEntryObject.getJSONObject("skillsChange");
           JSONObject trainingDayObject = trainingEntryObject.getJSONObject("day");
           JSONObject trainingTypeObject = trainingEntryObject.getJSONObject("type");

            TrainingReport.TrainingType trainingType = new TrainingReport.TrainingType(trainingTypeObject.getInt("code"),trainingTypeObject.getString("name"));

            TrainingReport.TrainingDay trainingDay = new TrainingReport.TrainingDay(trainingDayObject.getInt("season"),
                    trainingDayObject.getInt("week"),trainingDayObject.getInt("seasonWeek"),
                    trainingDayObject.getInt("day"));

            Skills actualSkills = new Skills(skillsObject.getInt("form"), skillsObject.getInt("tacticalDiscipline"),
                   skillsObject.getInt("experience"),
                   skillsObject.getInt("teamwork"),skillsObject.getInt("stamina"),
                    skillsObject.getInt("pace"),skillsObject.getInt("striker"),
                    skillsObject.getInt("defending"), skillsObject.getInt("technique"),
                    skillsObject.getInt("passing"),skillsObject.getInt("playmaking"),
                   skillsObject.getInt("keeper")
            )
                   ;
            Skills skillsChange = new Skills(skillsChangeObject.getInt("form"), skillsChangeObject.getInt("tacticalDiscipline"),
                    skillsChangeObject.getInt("experience"),
                    skillsChangeObject.getInt("teamwork"),skillsChangeObject.getInt("stamina"),
                    skillsChangeObject.getInt("pace"),skillsChangeObject.getInt("striker"),
                    skillsChangeObject.getInt("defending"), skillsChangeObject.getInt("technique"),
                    skillsChangeObject.getInt("passing"),skillsChangeObject.getInt("playmaking"),
                    skillsChangeObject.getInt("keeper"));

             trainingEntry = new TrainingReport.TrainingEntry(trainingDay,actualSkills,skillsChange, trainingType);
             reports.add(trainingEntry);
        }
        return reports;
    }

    @SneakyThrows
    public static List<TrainingReport.TrainingEntry> getTrainingReportsNonAdmin(PlayerElement player) throws IOException {
        String urlString = BASE_URL + "/training";
        URL url = new URI(urlString).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Cookie", "PHPSESSID=" + phpSessionId);

        String responseBody = readResponse(conn);
        JSONObject json = new JSONObject(responseBody);
        JSONArray reportsArray = json.getJSONArray("players");
        TrainingReport.TrainingEntry trainingEntry ;
        List<TrainingReport.TrainingEntry> reports = new ArrayList<>();
        for (int i = 0; i < reportsArray.length(); i++) {

            JSONObject trainingEntryObject2= reportsArray.getJSONObject(i);
        if(trainingEntryObject2.getInt("id")==player.getId())
        {
            JSONObject trainingEntryObject = trainingEntryObject2.getJSONObject("report");
            JSONObject skillsObject = trainingEntryObject.getJSONObject("skills");
            JSONObject skillsChangeObject = trainingEntryObject.getJSONObject("skillsChange");
            JSONObject trainingDayObject = trainingEntryObject.getJSONObject("day");
            TrainingReport.TrainingDay trainingDay = new TrainingReport.TrainingDay(trainingDayObject.getInt("season"),
                trainingDayObject.getInt("week"), trainingDayObject.getInt("seasonWeek"),
                trainingDayObject.getInt("day"));

            JSONObject trainingTypeObject = trainingEntryObject.getJSONObject("type");
            TrainingReport.TrainingType trainingType = new TrainingReport.TrainingType(trainingTypeObject.getInt("code"),trainingTypeObject.getString("name"));
            Skills actualSkills = new Skills(skillsObject.getInt("form"), skillsObject.getInt("tacticalDiscipline"),
                skillsObject.getInt("experience"),
                skillsObject.getInt("teamwork"), skillsObject.getInt("stamina"),
                skillsObject.getInt("pace"), skillsObject.getInt("striker"),
                skillsObject.getInt("defending"), skillsObject.getInt("technique"),
                skillsObject.getInt("passing"), skillsObject.getInt("playmaking"),
                skillsObject.getInt("keeper")
            );

            Skills skillsChange = new Skills(skillsChangeObject.getInt("form"), skillsChangeObject.getInt("tacticalDiscipline"),
                skillsChangeObject.getInt("experience"),
                skillsChangeObject.getInt("teamwork"), skillsChangeObject.getInt("stamina"),
                skillsChangeObject.getInt("pace"), skillsChangeObject.getInt("striker"),
                skillsChangeObject.getInt("defending"), skillsChangeObject.getInt("technique"),
                skillsChangeObject.getInt("passing"), skillsChangeObject.getInt("playmaking"),
                skillsChangeObject.getInt("keeper"));

            trainingEntry = new TrainingReport.TrainingEntry(trainingDay, actualSkills, skillsChange, trainingType);
            reports.add(trainingEntry);
        }
        }
        return reports;
    }

    // Pomocni metod za citanje odgovora API-ja
    public static String readResponse(HttpURLConnection conn) throws IOException {
        Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8);
        String response = scanner.useDelimiter("\\A").next();
        scanner.close();
        return response;
    }
}