package org.velja.app.sokker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.velja.app.sokker.SokkerApiService;
import org.velja.app.sokker.SokkerWebController;

@RestController
@RequestMapping("/sokker/api/report")
public class ReportApiController {

    private final SokkerApiService sokkerApiService;
    private final ObjectMapper objectMapper;

    public ReportApiController(SokkerApiService sokkerApiService, ObjectMapper objectMapper) {
        this.sokkerApiService = sokkerApiService;
        this.objectMapper = objectMapper;
    }

    private String sessionCookie(HttpSession session) {
        Object phpSessionId = session.getAttribute(SokkerWebController.SESSION_COOKIE);
        if (!(phpSessionId instanceof String value) || value.isBlank()) {
            throw new IllegalStateException("You are not logged in.");
        }
        return value;
    }

    private int teamId(HttpSession session) {
        Object teamId = session.getAttribute(SokkerWebController.TEAM_ID);
        if (teamId instanceof Integer value) {
            return value;
        }
        throw new IllegalStateException("Team not loaded. Log in again.");
    }

    @GetMapping("/current")
    public JsonNode current(HttpSession session) {
        return sokkerApiService.current(sessionCookie(session));
    }

    @GetMapping("/seasons")
    public JsonNode seasons(HttpSession session) {
        return sokkerApiService.getSafe("/seasons", sessionCookie(session)).orElse(null);
    }

    @GetMapping("/team-matches")
    public JsonNode teamMatches(@RequestParam int teamId, @RequestParam int season, @RequestParam(defaultValue="0") int fromWeek, @RequestParam(defaultValue="0") int toWeek, HttpSession session) {
        String path = String.format("/team/%d/match?filter[season]=%d&filter[week]=%d-%d", teamId, season, fromWeek, toWeek);
        return sokkerApiService.get(path, sessionCookie(session));
    }

    @GetMapping("/report")
    public JsonNode report(@RequestParam int teamId, @RequestParam int fromWeek, @RequestParam int toWeek, HttpSession session) {
        String path = String.format("/report?week=%d-%d&teamId=%d", fromWeek, toWeek, teamId);
        return sokkerApiService.get(path, sessionCookie(session));
    }
}
