package org.velja.app.sokker.sokkerreport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SokkerApiClient {

    private static final Logger log = LoggerFactory.getLogger(SokkerApiClient.class);
    private static final String BASE = "https://sokker.org";

    private final RestClient http = RestClient.builder()
            .baseUrl(BASE)
            .build();

    private final Map<String, String> cookies = new HashMap<>();

    public synchronized String login(String username, String password) {
        log.info("SOKKER API CALL -> POST /api/auth/login user={}", username);
        String body = "{\"login\":" + quote(username)
                + ",\"password\":" + quote(password) + "}";

        var response = http.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, res) -> {
                    captureCookies(res.getHeaders());
                    String respBody = new String(res.getBody().readAllBytes());
                    log.info("SOKKER API RESPONSE <- POST /api/auth/login status={} bodyLen={}", res.getStatusCode(), respBody.length());
                    return respBody;
                });

        String result = response == null ? "{}" : response;
        log.info("SOKKER API RESULT login response={}", result);
        return result;
    }

    public synchronized String logout() {
        log.info("SOKKER API CALL -> POST /api/auth/logout");
        String result = http.post()
                .uri("/api/auth/logout")
                .headers(this::addCookies)
                .retrieve()
                .body(String.class);
        log.info("SOKKER API RESPONSE <- POST /api/auth/logout body={}", result);
        cookies.clear();
        return result;
    }

    /**
     * Loads Sokker seasons.
     *
     * Example:
     * [
     *   {
     *     "season": 79,
     *     "start": {
     *       "week": 1210
     *     },
     *     "end": {
     *       "week": 1222
     *     }
     *   }
     * ]
     */
    public synchronized String seasons() {
        log.info("SOKKER API CALL -> GET /api/seasons");
        String result = http.get()
                .uri("/api/seasons")
                .headers(this::addCookies)
                .retrieve()
                .body(String.class);
        log.info("SOKKER API RESPONSE <- GET /api/seasons bodyLen={} body={}", result != null ? result.length() : 0, result != null && result.length() < 2000 ? result : result != null ? result.substring(0,2000) : "");
        return result;
    }

    public synchronized String current() {
        log.info("SOKKER API CALL -> GET /api/current");
        String result = http.get()
                .uri("/api/current")
                .headers(this::addCookies)
                .retrieve()
                .body(String.class);
        log.info("SOKKER API RESPONSE <- GET /api/current bodyLen={} body={}", result != null ? result.length() : 0, result);
        return result;
    }

    public synchronized String arena(int teamId) {
        log.info("SOKKER API CALL -> GET /api/team/{}/arena", teamId);
        String result = http.get()
                .uri("/api/team/" + teamId + "/arena")
                .headers(this::addCookies)
                .retrieve()
                .body(String.class);
        log.info("SOKKER API RESPONSE <- GET /api/team/{}/arena bodyLen={} body={}", teamId, result != null ? result.length() : 0, result);
        return result;
    }

    /**
     * Loads team matches from Sokker for given week range.
     */
    public synchronized String matches(int teamId, int season, int fromWeek, int toWeek) {
        log.info("SOKKER API CALL -> GET /api/team/{}/match season={} week={}-{}", teamId, season, fromWeek, toWeek);
        String result = http.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/team/" + teamId + "/match")
                        .queryParam("filter[past]", true)
                        .queryParam("filter[live]", false)
                        .queryParam("filter[future]", false)
                        .queryParam("filter[teamId]", teamId)
                        .queryParam("filter[simple]", false)
                        .queryParam("filter[season]", season)
                        .queryParam("filter[week]", fromWeek + "-" + toWeek)
                        .build())
                .headers(headers -> {
                    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                    addCookies(headers);
                })
                .retrieve()
                .body(String.class);
        log.info("SOKKER API RESPONSE <- GET /api/team/{}/match bodyLen={} body={}", teamId, result != null ? result.length() : 0, result != null && result.length() < 2000 ? result : result != null ? result.substring(0,2000) : "");
        return result;
    }

    /**
     * Loads one report range from Sokker.
     *
     * The caller is responsible for splitting large ranges.
     */
    public synchronized String report(
            int teamId,
            int fromWeek,
            int toWeek) {
        log.info("SOKKER API CALL -> GET /api/report teamId={} week={}-{}", teamId, fromWeek, toWeek);
        String result = http.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/report")
                        .queryParam("currentWeek", "no")
                        .queryParam("week", fromWeek + "-" + toWeek)
                        .queryParam("teamId", teamId)
                        .build())
                .headers(headers -> {
                    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                    addCookies(headers);
                })
                .retrieve()
                .body(String.class);
        log.info("SOKKER API RESPONSE <- GET /api/report bodyLen={} body={}", result != null ? result.length() : 0, result != null && result.length() < 2000 ? result : result != null ? result.substring(0,2000) : "");
        return result;
    }

    /**
     * Kept for compatibility if something else still calls report(teamId).
     */
    public synchronized String report(int teamId) {

        return report(teamId, 1, Integer.MAX_VALUE);
    }

    public synchronized boolean isLoggedIn() {
        return !cookies.isEmpty();
    }

    private void addCookies(HttpHeaders headers) {

        if (cookies.isEmpty()) {
            return;
        }

        String cookieHeader = cookies.entrySet()
                .stream()
                .map(entry ->
                        entry.getKey() + "=" + entry.getValue()
                )
                .reduce((a, b) -> a + "; " + b)
                .orElse("");

        headers.add(
                HttpHeaders.COOKIE,
                cookieHeader
        );
    }

    private void captureCookies(HttpHeaders headers) {

        for (String value :
                headers.getOrDefault(
                        HttpHeaders.SET_COOKIE,
                        List.of()
                )) {

            String first = value.split(";", 2)[0];

            int separator = first.indexOf('=');

            if (separator > 0) {

                String name =
                        first.substring(0, separator);

                String cookie =
                        first.substring(separator + 1);

                cookies.put(name, cookie);
            }
        }
    }

    private static String quote(String value) {

        return "\""
                + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                + "\"";
    }
}