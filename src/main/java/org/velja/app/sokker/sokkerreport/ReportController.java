package org.velja.app.sokker.sokkerreport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/local")
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);
    private final SokkerApiClient client;
    private final ObjectMapper objectMapper;

    public ReportController(
            SokkerApiClient client,
            ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/login")
    public String login(
            @RequestBody LoginRequest request) {

        String resp = client.login(
                request.username(),
                request.password()
        );
        log.info("CONTROLLER /api/local/login response={}", resp);
        return resp;
    }

    @PostMapping("/logout")
    public String logout() {
        String resp = client.logout();
        log.info("CONTROLLER /api/local/logout response={}", resp);
        return resp;
    }

    @GetMapping("/seasons")
    public String seasons() {
        return client.seasons();
    }

    @GetMapping("/current")
    public ResponseEntity<String> current() {
        try {
            String body = client.current();
            log.info("CONTROLLER /api/local/current response body={}", body);
            try {
                JsonNode node = objectMapper.readTree(body);
                JsonNode teamNode = node.path("team");
                String teamId = teamNode.has("id") ? String.valueOf(teamNode.get("id").asLong()) : "unknown";
                String teamName = teamNode.has("name") ? teamNode.get("name").asText() : "unknown";
                log.info("CONTROLLER /api/local/current parsed teamId={} teamName={}", teamId, teamName);
            } catch (Exception e) {
                log.warn("CONTROLLER /api/local/current parse error {}", e.getMessage());
            }
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.warn("CONTROLLER /api/local/current error, returning empty: {}", e.getMessage());
            return ResponseEntity.ok("{\"team\":{}}");
        }
    }

    @GetMapping("/arena")
    public String arena(@RequestParam("teamId") int teamId) {
        String body = client.arena(teamId);
        log.info("CONTROLLER /api/local/arena teamId={} response body={}", teamId, body);
        try {
            JsonNode node = objectMapper.readTree(body);
            String name = node.has("name") ? node.get("name").asText() : "unknown";
            int seats = node.has("seats") ? node.get("seats").asInt() : 0;
            log.info("CONTROLLER /api/local/arena parsed name={} seats={}", name, seats);
        } catch (Exception e) {
            log.warn("CONTROLLER /api/local/arena parse error {}", e.getMessage());
        }
        return body;
    }

    @GetMapping("/matches")
    public ResponseEntity<String> matches(
            @RequestParam(name = "teamId", defaultValue = "73599")
            int teamId,
            @RequestParam(name = "season")
            int season) {
        try {
            // Find season weeks
            String seasonsJson = client.seasons();
            JsonNode seasonsNode = objectMapper.readTree(seasonsJson);
            int fromWeek = -1;
            int toWeek = -1;
            if (seasonsNode.isArray()) {
                for (JsonNode s : seasonsNode) {
                    if (s.has("season") && s.get("season").asInt() == season) {
                        JsonNode start = s.get("start");
                        JsonNode end = s.get("end");
                        if (start != null && start.has("week")) fromWeek = start.get("week").asInt();
                        if (end != null && end.has("week")) toWeek = end.get("week").asInt();
                        break;
                    }
                }
            }
            if (fromWeek < 0 || toWeek < 0) {
                return ResponseEntity.badRequest()
                        .body("{\"error\":\"Season weeks not found\"}");
            }

            List<JsonNode> allMatches = new ArrayList<>();
            int currentWeek = fromWeek;
            while (currentWeek <= toWeek) {
                int week = currentWeek;
                String response = client.matches(teamId, season, week, week);
                JsonNode root = objectMapper.readTree(response);
                if (root != null && root.has("matches") && root.get("matches").isArray()) {
                    for (JsonNode m : root.get("matches")) {
                        allMatches.add(m);
                    }
                }
                currentWeek = currentWeek + 1;
            }

            // Optional sort by game day week desc
            allMatches.sort(Comparator.comparingInt((JsonNode m) -> {
                JsonNode time = m.path("time");
                JsonNode gameDay = time.path("gameDay");
                JsonNode weekNode = gameDay.path("week");
                return weekNode.isInt() ? weekNode.asInt() : Integer.MIN_VALUE;
            }).reversed());

            ObjectNode result = objectMapper.createObjectNode();
            ArrayNode arr = objectMapper.createArrayNode();
            for (JsonNode m : allMatches) arr.add(m);
            result.set("matches", arr);
            result.put("total", allMatches.size());
            result.put("season", season);
            result.put("teamId", teamId);
            return ResponseEntity.ok(objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    @GetMapping("/report")
    public ResponseEntity<String> report(
            @RequestParam(name = "teamId", defaultValue = "73599")
            int teamId,
            @RequestParam(name = "fromWeek")
            int fromWeek,
            @RequestParam(name = "toWeek")
            int toWeek) {

        if (fromWeek > toWeek) {
            return ResponseEntity.badRequest()
                    .body("{\"error\":\"fromWeek cannot be greater than toWeek\"}");
        }

        try {
            List<JsonNode> allEvents = new ArrayList<>();
            List<String> requestedRanges = new ArrayList<>();

            int currentWeek = fromWeek;

            while (currentWeek <= toWeek) {

                int week = currentWeek;

                requestedRanges.add(
                        week + "-" + week
                );

                String response =
                        client.report(
                                teamId,
                                week,
                                week
                        );

                JsonNode root =
                        objectMapper.readTree(response);

                if (root != null && root.isArray()) {

                    for (JsonNode event : root) {
                        allEvents.add(event);
                    }

                } else if (
                        root != null &&
                                root.has("events") &&
                                root.get("events").isArray()) {

                    for (JsonNode event : root.get("events")) {
                        allEvents.add(event);
                    }
                }

                currentWeek = currentWeek + 1;
            }

            // Sortiraj SVE spojene reportove po week.
            // Najnovija nedelja ide prva.
            allEvents.sort(
                    Comparator.comparingInt(
                            this::getWeek
                    ).reversed()
            );

            ArrayNode combinedEvents =
                    objectMapper.createArrayNode();

            for (JsonNode event : allEvents) {
                combinedEvents.add(event);
            }

            ObjectNode result =
                    objectMapper.createObjectNode();

            result.set(
                    "events",
                    combinedEvents
            );

            result.put(
                    "fromWeek",
                    fromWeek
            );

            result.put(
                    "toWeek",
                    toWeek
            );

            result.put(
                    "teamId",
                    teamId
            );

            ArrayNode ranges =
                    result.putArray("requestedRanges");

            for (String range : requestedRanges) {
                ranges.add(range);
            }

            return ResponseEntity.ok(
                    objectMapper.writeValueAsString(result)
            );

        } catch (Exception e) {

            return ResponseEntity.internalServerError()
                    .body(
                            "{\"error\":\""
                                    + escapeJson(e.getMessage())
                                    + "\"}"
                    );
        }
    }

    private int getWeek(JsonNode event) {

        JsonNode week = event.get("week");

        if (week == null || week.isNull()) {
            return Integer.MIN_VALUE;
        }

        if (week.isInt() || week.isLong()) {
            return week.asInt();
        }

        try {
            return Integer.parseInt(
                    week.asText()
            );
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }

    private String escapeJson(String value) {

        if (value == null) {
            return "Unknown error";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    public record LoginRequest(
            String username,
            String password
    ) {
    }
}