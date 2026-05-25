package org.velja.app.sokker;

import com.fasterxml.jackson.databind.JsonNode;
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

    private final SokkerApiService sokkerApiService;
    private final SktablesService sktablesService;

    public SokkerWebController(SokkerApiService sokkerApiService, SktablesService sktablesService) {
        this.sokkerApiService = sokkerApiService;
        this.sktablesService = sktablesService;
    }

    @PostMapping("/login")
    public JsonNode login(@RequestBody LoginRequest request, HttpSession session) {
        String phpSessionId = sokkerApiService.login(request.login(), request.password());
        JsonNode current = sokkerApiService.current(phpSessionId);
        JsonNode teamId = current.path("team").path("id");
        if (!teamId.isInt()) {
            throw new SokkerApiException("Sokker nije vratio tim za trenutnog korisnika.");
        }
        session.setAttribute(SESSION_COOKIE, phpSessionId);
        session.setAttribute(TEAM_ID, teamId.asInt());
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

    @GetMapping("/players/{playerId}/training")
    public JsonNode playerTraining(@PathVariable long playerId, HttpSession session) {
        JsonNode report = sokkerApiService.trainingReport(playerId, sessionCookie(session));
        Object sktablesCookie = session.getAttribute(SKTABLES_COOKIE);
        if (sktablesCookie instanceof String cookie && !cookie.isBlank()) {
            return sktablesService.mergeTrainingReport(report, playerId, cookie);
        }
        return report;
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
            throw new IllegalStateException("Niste ulogovani.");
        }
        return value;
    }

    private int teamId(HttpSession session) {
        Object teamId = session.getAttribute(TEAM_ID);
        if (teamId instanceof Integer value) {
            return value;
        }
        throw new IllegalStateException("Tim nije ucitan. Uloguj se ponovo.");
    }

    public record LoginRequest(String login, String password) {
    }
}
