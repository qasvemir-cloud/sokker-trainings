package org.velja.app.sokker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.velja.app.sokker.SokkerApiService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/sokker/api/report")
public class ReportApiController {

    private static final Logger log = LoggerFactory.getLogger(ReportApiController.class);
    private static final String SESSION_COOKIE = "sokkerPhpSessionId";

    private final SokkerApiService sokkerApiService;
    private final ObjectMapper objectMapper;

    public ReportApiController(SokkerApiService sokkerApiService, ObjectMapper objectMapper) {
        this.sokkerApiService = sokkerApiService;
        this.objectMapper = objectMapper;
    }

    private String sessionCookie(HttpSession session) {
        Object phpSessionId = session.getAttribute(SESSION_COOKIE);
        if (!(phpSessionId instanceof String value) || value.isBlank()) {
            throw new IllegalStateException("You are not logged in.");
        }
        return value;
    }

    private void logRequest(HttpServletRequest request, String action) {
        String sessionId = "none";
        HttpSession session = request.getSession(false);
        if (session != null) {
            String id = session.getId();
            if (id != null && id.length() >= 8) {
                sessionId = id.substring(0, 8);
            }
        }
        log.info("[REPORT API] {} {} - Session: {}", request.getMethod(), request.getRequestURI(), sessionId);
    }

    private void logResponse(String action, Object response) {
        String body = response != null ? response.toString() : "null";
        if (body.length() > 500) body = body.substring(0, 500) + "...";
        log.info("[REPORT API] Response {}: {}", action, body);
    }

    @GetMapping("/current")
    public JsonNode current(HttpSession session, HttpServletRequest request) {
        logRequest(request, "current");
        JsonNode result = sokkerApiService.current(sessionCookie(session));
        logResponse("current", result);
        return result;
    }

    @GetMapping("/seasons")
    public JsonNode seasons(HttpSession session, HttpServletRequest request) {
        logRequest(request, "seasons");
        try {
            JsonNode result = sokkerApiService.getSafe("/seasons", sessionCookie(session)).orElse(objectMapper.createArrayNode());
            logResponse("seasons", result);
            return result;
        } catch (Exception e) {
            log.error("[REPORT API] Seasons error: {}", e.getMessage());
            return objectMapper.createArrayNode();
        }
    }

    @GetMapping("/team-matches")
    public JsonNode teamMatches(@RequestParam int teamId, @RequestParam int season,
                                HttpSession session, HttpServletRequest request) {
        logRequest(request, "team-matches teamId=" + teamId + " season=" + season);

        try {
            // Get season week range
            JsonNode seasonsNode = sokkerApiService.getSafe("/seasons", sessionCookie(session)).orElse(null);
            int fromWeek = -1;
            int toWeek = -1;
            if (seasonsNode != null && seasonsNode.isArray()) {
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
                ObjectNode error = objectMapper.createObjectNode();
                error.put("error", "Season weeks not found");
                return error;
            }

            // Fetch matches week by week
            List<JsonNode> allMatches = new ArrayList<>();
            int currentWeek = fromWeek;
            while (currentWeek <= toWeek) {
                int week = currentWeek;
                String path = String.format("/team/%d/match?filter[past]=true&filter[live]=false&filter[future]=false&filter[teamId]=%d&filter[simple]=false&filter[season]=%d&filter[week]=%d-%d",
                        teamId, teamId, season, week, week);
                log.info("[REPORT API] Calling Sokker API: {}", path);
                JsonNode result = sokkerApiService.get(path, sessionCookie(session));
                if (result != null && result.has("matches") && result.get("matches").isArray()) {
                    for (JsonNode m : result.get("matches")) {
                        allMatches.add(m);
                    }
                }
                currentWeek++;
            }

            // Sort by week descending (newest first)
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
            log.info("[REPORT API] team-matches full payload ({} matches): {}", allMatches.size(), result);
            logResponse("team-matches", result);
            return result;

        } catch (Exception e) {
            log.error("[REPORT API] team-matches error: {}", e.getMessage());
            ObjectNode error = objectMapper.createObjectNode();
            error.put("error", e.getMessage());
            return error;
        }
    }

    @GetMapping("/report")
    public JsonNode report(@RequestParam int teamId, @RequestParam int fromWeek,
                           @RequestParam int toWeek, HttpSession session, HttpServletRequest request) {
        logRequest(request, "report teamId=" + teamId + " weeks=" + fromWeek + "-" + toWeek);

        if (fromWeek > toWeek) {
            ObjectNode error = objectMapper.createObjectNode();
            error.put("error", "fromWeek cannot be greater than toWeek");
            return error;
        }

        try {
            List<JsonNode> allEvents = new ArrayList<>();
            List<String> requestedRanges = new ArrayList<>();

            int currentWeek = fromWeek;
            while (currentWeek <= toWeek) {
                int week = currentWeek;
                requestedRanges.add(week + "-" + week);

                String path = String.format("/report?currentWeek=no&week=%d-%d&teamId=%d", week, week, teamId);
                log.info("[REPORT API] Calling Sokker API: {}", path);
                JsonNode result = sokkerApiService.get(path, sessionCookie(session));

                if (result != null && result.isArray()) {
                    for (JsonNode event : result) {
                        allEvents.add(event);
                    }
                } else if (result != null && result.has("events") && result.get("events").isArray()) {
                    for (JsonNode event : result.get("events")) {
                        allEvents.add(event);
                    }
                }
                currentWeek++;
            }

            // Sort by week descending (newest first)
            allEvents.sort(Comparator.comparingInt((JsonNode e) -> {
                JsonNode weekNode = e.path("week");
                return weekNode.isInt() ? weekNode.asInt() : Integer.MIN_VALUE;
            }).reversed());

            ArrayNode combinedEvents = objectMapper.createArrayNode();
            for (JsonNode event : allEvents) {
                combinedEvents.add(event);
            }

            ObjectNode result = objectMapper.createObjectNode();
            result.set("events", combinedEvents);
            result.put("fromWeek", fromWeek);
            result.put("toWeek", toWeek);
            result.put("teamId", teamId);

            ArrayNode ranges = result.putArray("requestedRanges");
            for (String range : requestedRanges) {
                ranges.add(range);
            }

            logResponse("report", result);
            return result;

        } catch (Exception e) {
            log.error("[REPORT API] report error: {}", e.getMessage());
            ObjectNode error = objectMapper.createObjectNode();
            error.put("error", e.getMessage());
            return error;
        }
    }

    @GetMapping("/arena")
    public JsonNode arena(@RequestParam int teamId, HttpSession session, HttpServletRequest request) {
        logRequest(request, "arena teamId=" + teamId);
        String path = String.format("/team/%d/arena", teamId);
        JsonNode result = sokkerApiService.get(path, sessionCookie(session));
        logResponse("arena", result);
        return result;
    }
}