package org.velja.app.sokker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

@Service
public class SokkerApiService {
    private static final String BASE_URL = "https://sokker.org/api";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String login(String login, String password) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("login", login);
        body.put("password", password);
        body.put("remember", true);

        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/auth/login"))
                .timeout(Duration.ofSeconds(25))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = send(request);
        if (response.statusCode() != 200) {
            throw new SokkerApiException("Login nije uspeo. Sokker status: " + response.statusCode());
        }

        Optional<String> sessionCookie = response.headers().allValues(HttpHeaders.SET_COOKIE).stream()
                .filter(cookie -> cookie.startsWith("PHPSESSID="))
                .findFirst();

        return sessionCookie
                .map(cookie -> cookie.split(";", 2)[0].split("=", 2)[1])
                .orElseThrow(() -> new SokkerApiException("Sokker nije vratio PHPSESSID cookie."));
    }

    public JsonNode current(String phpSessionId) {
        return get("/current", phpSessionId);
    }

    public JsonNode players(int teamId, String phpSessionId) {
        return get("/team/" + teamId + "/player", phpSessionId);
    }

    public JsonNode currentTraining(String phpSessionId) {
        return get("/training", phpSessionId);
    }

    public JsonNode trainingPlayers(String phpSessionId) {
        return get("/training/players", phpSessionId);
    }

    public JsonNode trainingSummary(String phpSessionId) {
        return get("/training/summary", phpSessionId);
    }

    public JsonNode trainingReport(long playerId, String phpSessionId) {
        return get("/training/" + playerId + "/report", phpSessionId);
    }

    public JsonNode juniors(String phpSessionId) {
        return get("/junior", phpSessionId);
    }

    public JsonNode juniorReport(String phpSessionId) {
        return get("/junior/report", phpSessionId);
    }

    public JsonNode juniorGraph(long juniorId, String phpSessionId) {
        return get("/junior/" + juniorId + "/graph", phpSessionId);
    }

    public JsonNode teamTransfers(int teamId, String phpSessionId) {
        return get("/team/" + teamId + "/transfer", phpSessionId);
    }

    public JsonNode marketTransfers(String phpSessionId) {
        return get("/transfer", phpSessionId);
    }

    public JsonNode teamMatches(int teamId, String phpSessionId) {
        return get("/team/" + teamId + "/match", phpSessionId);
    }

    public JsonNode matchStats(long matchId, String phpSessionId) {
        return get("/match/" + matchId + "/stats", phpSessionId);
    }

    public JsonNode teamAlumni(int teamId, String phpSessionId) {
        return get("/team/" + teamId + "/alumni", phpSessionId);
    }

    private JsonNode get(String path, String phpSessionId) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(25))
                .header(HttpHeaders.ACCEPT, "application/json")
                .header(HttpHeaders.COOKIE, "PHPSESSID=" + phpSessionId)
                .GET()
                .build();

        HttpResponse<String> response = send(request);
        if (response.statusCode() == 401) {
            throw new SokkerApiException("Sokker sesija je istekla. Uloguj se ponovo.");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new SokkerApiException("Sokker API greska za " + path + ". Status: " + response.statusCode());
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new SokkerApiException("Ne mogu da procitam Sokker JSON odgovor.", e);
        }
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new SokkerApiException("Sokker API nije dostupan: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SokkerApiException("Sokker API poziv je prekinut.", e);
        }
    }
}
