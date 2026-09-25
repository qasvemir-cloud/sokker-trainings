package org.velja.app.sokker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/sokker/api/scouting")
public class ScoutingApiController {

    private static final String SESSION_COOKIE = "sokkerPhpSessionId";
    private static final String USERNAME = "sokkerUsername";

    private final SokkerApiService sokkerApiService;
    private final ScoutingPlayersService scoutingPlayersService;
    private final ScoutingJobRunner jobRunner;
    private final ObjectMapper objectMapper;

    private final Set<String> allowedUsernames;
    private final Set<String> scanUsernames;
    private final int countryCode;

    public ScoutingApiController(SokkerApiService sokkerApiService,
                                 ScoutingPlayersService scoutingPlayersService,
                                 ScoutingJobRunner jobRunner,
                                 ObjectMapper objectMapper,
                                 @Value("${sokker.scouting-usernames:}") List<String> allowedUsernames,
                                 @Value("${sokker.scouting-scan-usernames:}") List<String> scanUsernames,
                                 @Value("${sokker.scouting-country-code:39}") int countryCode) {
        this.sokkerApiService = sokkerApiService;
        this.scoutingPlayersService = scoutingPlayersService;
        this.jobRunner = jobRunner;
        this.objectMapper = objectMapper;
        this.allowedUsernames = lower(allowedUsernames);
        this.scanUsernames = lower(scanUsernames);
        this.countryCode = countryCode;
    }

    @GetMapping("/status")
    public JsonNode status(HttpSession session) {
        ObjectNode payload = objectMapper.createObjectNode();
        boolean access = hasAccess(session);
        payload.put("access", access);
        payload.put("canScan", access && isScanner(session));
        payload.put("count", access ? scoutingPlayersService.count() : 0);
        payload.put("maxAge", 21);
        payload.put("countryCode", countryCode);
        if (access) {
            ArrayNode ages = payload.putArray("ages");
            for (int age : scoutingPlayersService.ages()) {
                ages.add(age);
            }
        }
        return payload;
    }

    @GetMapping("/players")
    public JsonNode players(HttpSession session) {
        requireAccess(session);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("access", true);
        payload.put("canScan", isScanner(session));
        payload.put("count", scoutingPlayersService.count());
        payload.set("players", scoutingPlayersService.load());
        return payload;
    }

    @PostMapping("/scan/serbian-clubs")
    public JsonNode scanSerbianClubs(HttpSession session) {
        requireAccess(session);
        requireScanner(session);
        return startJob("serbian-clubs", session);
    }

    @PostMapping("/scan/all-clubs")
    public JsonNode scanAllClubs(HttpSession session) {
        requireAccess(session);
        requireScanner(session);
        return startJob("all-clubs", session);
    }

    @PostMapping("/update-skills")
    public JsonNode updateSkills(HttpSession session) {
        requireAccess(session);
        return startJob("update-skills", session);
    }

    @PostMapping("/job/cancel")
    public JsonNode cancelJob(HttpSession session, @org.springframework.web.bind.annotation.RequestParam String jobId) {
        requireAccess(session);
        jobRunner.cancel(jobId);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("jobId", jobId);
        payload.put("cancelled", true);
        return payload;
    }

    @GetMapping("/job")
    public JsonNode jobStatus(HttpSession session, @org.springframework.web.bind.annotation.RequestParam String jobId) {
        requireAccess(session);
        ScoutingJobRunner.JobState state = jobRunner.status(jobId);
        if (state == null) {
            throw new IllegalArgumentException("Unknown job: " + jobId);
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("jobId", state.jobId);
        payload.put("type", state.type);
        payload.put("running", state.running.get());
        payload.put("cancelled", state.cancelled.get());
        payload.put("teams", state.teams.get());
        payload.put("total", state.total);
        payload.put("players", state.players.get());
        payload.put("failures", state.failures.get());
        payload.put("skippedTeams", state.skippedTeams.get());
        payload.put("skippedPlayers", state.skippedPlayers);
        payload.put("detail", state.detail);
        payload.put("count", scoutingPlayersService.count());
        if (state.error != null) {
            payload.put("error", state.error);
        }
        if (state.finishedAt > 0) {
            payload.put("durationMs", state.finishedAt - state.createdAt);
        }
        return payload;
    }

    private JsonNode startJob(String type, HttpSession session) {
        String phpSessionId = sessionCookie(session);
        String jobId = jobRunner.start(type, phpSessionId);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("jobId", jobId);
        payload.put("type", type);
        payload.put("running", true);
        return payload;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> illegalState(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> illegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", exception.getMessage()));
    }

    private boolean hasAccess(HttpSession session) {
        String username = sessionUsername(session);
        return username != null && allowedUsernames.contains(username);
    }

    private boolean isScanner(HttpSession session) {
        String username = sessionUsername(session);
        return username != null && scanUsernames.contains(username);
    }

    private void requireAccess(HttpSession session) {
        if (!hasAccess(session)) {
            throw new ScoutingAccessDenied("U21 scouting is available only to vacke and veljizao.");
        }
    }

    private void requireScanner(HttpSession session) {
        if (!isScanner(session)) {
            throw new ScoutingAccessDenied("Only veljizao can run scouting scans.");
        }
    }

    @ExceptionHandler(ScoutingAccessDenied.class)
    public ResponseEntity<Map<String, String>> forbidden(ScoutingAccessDenied exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", exception.getMessage()));
    }

    private String sessionUsername(HttpSession session) {
        Object value = session.getAttribute(USERNAME);
        return value instanceof String s ? s.toLowerCase().trim() : null;
    }

    private String sessionCookie(HttpSession session) {
        Object phpSessionId = session.getAttribute(SESSION_COOKIE);
        if (!(phpSessionId instanceof String value) || value.isBlank()) {
            throw new IllegalStateException("You are not logged in.");
        }
        return value;
    }

    private Set<String> lower(List<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toLowerCase().trim())
                .collect(Collectors.toUnmodifiableSet());
    }

    public static class ScoutingAccessDenied extends RuntimeException {
        public ScoutingAccessDenied(String message) {
            super(message);
        }
    }
}
