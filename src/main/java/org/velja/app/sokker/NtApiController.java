package org.velja.app.sokker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/sokker/api/nt")
public class NtApiController {

    private static final String SESSION_COOKIE = "sokkerPhpSessionId";
    private static final String COUNTRY_CODE = "sokkerCountryCode";

    private static final String NT_TABLE = "nt_players";
    private static final String NT21_TABLE = "nt_players_u21";

    private final SokkerApiService sokkerApiService;
    private final NtPlayersService ntPlayersService;
    private final ObjectMapper objectMapper;

    @Value("${sokker.nt-team-id:39}")
    private int ntTeamId;

    @Value("${sokker.nt21-team-id:439}")
    private int nt21TeamId;

    @Value("${sokker.nt-country-code:39}")
    private int ntCountryCode;

    public NtApiController(SokkerApiService sokkerApiService, NtPlayersService ntPlayersService, ObjectMapper objectMapper) {
        this.sokkerApiService = sokkerApiService;
        this.ntPlayersService = ntPlayersService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/status")
    public JsonNode ntStatus(HttpSession session) {
        return status(session);
    }

    @GetMapping("/players")
    public JsonNode ntPlayers(HttpSession session) {
        return players(session, NT_TABLE);
    }

    @PostMapping("/update")
    public JsonNode ntUpdate(HttpSession session) {
        return update(session, ntTeamId, NT_TABLE);
    }

    @GetMapping("/nt21/status")
    public JsonNode nt21Status(HttpSession session) {
        return status(session);
    }

    @GetMapping("/nt21/players")
    public JsonNode nt21Players(HttpSession session) {
        return players(session, NT21_TABLE);
    }

    @PostMapping("/nt21/update")
    public JsonNode nt21Update(HttpSession session) {
        return update(session, nt21TeamId, NT21_TABLE);
    }

    private JsonNode status(HttpSession session) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("countryCode", sessionCountryCode(session));
        payload.put("access", hasAccess(session));
        payload.put("canUpdate", hasAccess(session));
        return payload;
    }

    private JsonNode players(HttpSession session, String table) {
        ObjectNode payload = objectMapper.createObjectNode();
        if (!hasAccess(session)) {
            payload.put("access", false);
            payload.put("canUpdate", false);
            payload.set("players", objectMapper.createArrayNode());
            return payload;
        }
        payload.put("access", true);
        payload.put("canUpdate", true);
        payload.set("players", ntPlayersService.load(table));
        return payload;
    }

    private JsonNode update(HttpSession session, int teamId, String table) {
        if (!hasAccess(session)) {
            throw new ForbiddenRequest("NT access not allowed for this team country.");
        }
        JsonNode payload = sokkerApiService.countryPlayers(teamId, sessionCookie(session));
        int count = ntPlayersService.replaceAll(table, payload.path("players"));
        ObjectNode result = objectMapper.createObjectNode();
        result.put("updated", count);
        result.put("access", true);
        result.put("canUpdate", true);
        result.set("players", ntPlayersService.load(table));
        return result;
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(ForbiddenRequest.class)
    public ResponseEntity<Map<String, String>> forbidden(ForbiddenRequest exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", exception.getMessage()));
    }

    private boolean hasAccess(HttpSession session) {
        return sessionCountryCode(session) == ntCountryCode;
    }

    private int sessionCountryCode(HttpSession session) {
        Object value = session.getAttribute(COUNTRY_CODE);
        return value instanceof Integer i ? i : -1;
    }

    private String sessionCookie(HttpSession session) {
        Object phpSessionId = session.getAttribute(SESSION_COOKIE);
        if (!(phpSessionId instanceof String value) || value.isBlank()) {
            throw new IllegalStateException("You are not logged in.");
        }
        return value;
    }

    public static class ForbiddenRequest extends RuntimeException {
        public ForbiddenRequest(String message) {
            super(message);
        }
    }
}