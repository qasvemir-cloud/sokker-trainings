package org.velja.app.sokker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/sokker/api")
public class SokkerWebController {
    private static final String SESSION_COOKIE = "sokkerPhpSessionId";
    private static final String SKTABLES_COOKIE = "sktablesCookie";
    private static final String TEAM_ID = "sokkerTeamId";
    private static final String USERNAME = "sokkerUsername";
    private static final String COUNTRY_CODE = "sokkerCountryCode";


    private final SokkerApiService sokkerApiService;
    private final SktablesService sktablesService;
    private final ObjectMapper objectMapper;

    public SokkerWebController(SokkerApiService sokkerApiService, SktablesService sktablesService, ObjectMapper objectMapper) {
        this.sokkerApiService = sokkerApiService;
        this.sktablesService = sktablesService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/login")
    public JsonNode login(@RequestBody LoginRequest request, HttpSession session) {
        String phpSessionId = sokkerApiService.login(request.login(), request.password());
        JsonNode current = sokkerApiService.current(phpSessionId);
        JsonNode teamId = current.path("team").path("id");
        if (!teamId.isInt()) {
            throw new SokkerApiException("Sokker did not return team for current user.");
        }
        session.setAttribute(SESSION_COOKIE, phpSessionId);
        session.setAttribute(TEAM_ID, teamId.asInt());
        session.setAttribute(USERNAME, request.login());
        JsonNode countryCode = current.path("team").path("country").path("code");
        session.setAttribute(COUNTRY_CODE, countryCode.isInt() ? countryCode.asInt() : -1);
        sktablesService.login(request.login(), request.password())
                .ifPresent(cookie -> session.setAttribute(SKTABLES_COOKIE, cookie));
        return current;
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(HttpSession session) {
        session.invalidate();
        return Map.of("ok", true);
    }

    @GetMapping("/me")
    public JsonNode me(HttpSession session) {
        return sokkerApiService.current(sessionCookie(session));
    }

    @GetMapping("/players")
    public JsonNode players(HttpSession session) {
        return sokkerApiService.players(teamId(session), sessionCookie(session));
    }

    @GetMapping("/training/current")
    public JsonNode currentTraining(HttpSession session) {
        return sokkerApiService.currentTraining(sessionCookie(session));
    }

    @GetMapping("/training/players")
    public JsonNode trainingPlayers(HttpSession session) {
        return sokkerApiService.trainingPlayers(sessionCookie(session));
    }

    @GetMapping("/training/summary")
    public JsonNode trainingSummary(HttpSession session) {
        return sokkerApiService.trainingSummary(sessionCookie(session));
    }

    @GetMapping("/training/formations")
    public JsonNode trainingFormations(HttpSession session) {
        return sokkerApiService.trainingFormations(sessionCookie(session));
    }

    @GetMapping("/players/{playerId}/training")
    public JsonNode playerTraining(@PathVariable long playerId, HttpSession session) {
        JsonNode report = sokkerApiService.trainingReportWithFallback(playerId, sessionCookie(session));
        Object sktablesCookie = session.getAttribute(SKTABLES_COOKIE);
        if (sktablesCookie instanceof String cookie && !cookie.isBlank()) {
            return sktablesService.mergeTrainingReport(report, playerId, cookie);
        }
        return report;
    }

    @GetMapping("/juniors")
    public JsonNode juniors(HttpSession session) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("sokker", sokkerApiService.juniors(sessionCookie(session)));
        payload.set("report", sokkerApiService.juniorReport(sessionCookie(session)));
        Object sktablesCookie = session.getAttribute(SKTABLES_COOKIE);
        if (sktablesCookie instanceof String cookie && !cookie.isBlank()) {
            payload.set("sktables", sktablesService.academy(cookie));
        } else {
            ObjectNode sktables = objectMapper.createObjectNode();
            sktables.set("juniors", objectMapper.createArrayNode());
            payload.set("sktables", sktables);
        }
        return payload;
    }

    @GetMapping("/juniors/{juniorId}/graph")
    public JsonNode juniorGraph(@PathVariable long juniorId, HttpSession session) {
        return sokkerApiService.juniorGraph(juniorId, sessionCookie(session));
    }

    @GetMapping("/market/team-transfers")
    public JsonNode teamTransfers(HttpSession session) {
        return sokkerApiService.teamTransfers(teamId(session), sessionCookie(session));
    }

    @GetMapping("/market/transfers")
    public JsonNode marketTransfers(HttpSession session) {
        return sokkerApiService.marketTransfers(sessionCookie(session));
    }

    @GetMapping("/matches")
    public JsonNode teamMatches(HttpSession session) {
        return sokkerApiService.teamMatches(teamId(session), sessionCookie(session));
    }

    @GetMapping("/matches/{matchId}/stats")
    public JsonNode matchStats(@PathVariable long matchId, HttpSession session) {
        return sokkerApiService.matchStats(matchId, sessionCookie(session));
    }

    @GetMapping("/alumni")
    public JsonNode alumni(HttpSession session) {
        return sokkerApiService.teamAlumni(teamId(session), sessionCookie(session));
    }

    @ExceptionHandler(SokkerApiException.class)
    public ResponseEntity<Map<String, String>> apiError(SokkerApiException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> authError(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", exception.getMessage()));
    }

    private String sessionCookie(HttpSession session) {
        Object phpSessionId = session.getAttribute(SESSION_COOKIE);
        if (!(phpSessionId instanceof String value) || value.isBlank()) {
            throw new IllegalStateException("You are not logged in.");
        }
        return value;
    }

    private int teamId(HttpSession session) {
        Object teamId = session.getAttribute(TEAM_ID);
        if (teamId instanceof Integer value) {
            return value;
        }
        throw new IllegalStateException("Team not loaded. Log in again.");
    }

    public record LoginRequest(String login, String password) {
    }
}
