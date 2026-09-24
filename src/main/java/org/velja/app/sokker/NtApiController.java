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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sokker/api/nt")
public class NtApiController {

    private static final String SESSION_COOKIE = "sokkerPhpSessionId";
    private static final String USERNAME = "sokkerUsername";
    private static final String COUNTRY_CODE = "sokkerCountryCode";

    private final SokkerApiService sokkerApiService;
    private final NtPlayersService ntPlayersService;
    private final ObjectMapper objectMapper;

    @Value("${sokker.nt-manager-usernames:dzungla,veljizao}")
    private List<String> managerUsernames;

    @Value("${sokker.nt-team-id:39}")
    private int ntTeamId;

    @Value("${sokker.nt-country-code:39}")
    private int ntCountryCode;

    public NtApiController(SokkerApiService sokkerApiService, NtPlayersService ntPlayersService, ObjectMapper objectMapper) {
        this.sokkerApiService = sokkerApiService;
        this.ntPlayersService = ntPlayersService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/status")
    public JsonNode status(HttpSession session) {
        String username = sessionUsername(session);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("username", username == null ? "" : username);
        payload.put("countryCode", sessionCountryCode(session));
        payload.put("access", hasAccess(session));
        payload.put("canUpdate", isManager(session));
        return payload;
    }

    @GetMapping("/players")
    public JsonNode players(HttpSession session) {
        ObjectNode payload = objectMapper.createObjectNode();
        if (!hasAccess(session)) {
            payload.put("access", false);
            payload.put("canUpdate", false);
            payload.set("players", objectMapper.createArrayNode());
            return payload;
        }
        boolean showAllSkills = isManager(session);
        ArrayNode players = ntPlayersService.load(showAllSkills);
        payload.put("access", true);
        payload.put("canUpdate", showAllSkills);
        payload.set("players", players);
        return payload;
    }

    @PostMapping("/update")
    public JsonNode update(HttpSession session) {
        if (!hasAccess(session)) {
            throw new ForbiddenRequest("NT access not allowed for this team country.");
        }
        if (!isManager(session)) {
            throw new ForbiddenRequest("Only the NT manager may update players.");
        }
        JsonNode payload = sokkerApiService.countryPlayers(ntTeamId, sessionCookie(session));
        int count = ntPlayersService.replaceAll(payload.path("players"));
        ObjectNode result = objectMapper.createObjectNode();
        result.put("updated", count);
        result.put("access", true);
        result.put("canUpdate", true);
        result.set("players", ntPlayersService.load(true));
        return result;
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(ForbiddenRequest.class)
    public ResponseEntity<Map<String, String>> forbidden(ForbiddenRequest exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", exception.getMessage()));
    }

    private boolean hasAccess(HttpSession session) {
        return sessionCountryCode(session) == ntCountryCode;
    }

    private boolean isManager(HttpSession session) {
        String username = sessionUsername(session);
        if (username == null) {
            return false;
        }
        return managerUsernames.stream().anyMatch(name -> name != null && name.equalsIgnoreCase(username.trim()));
    }

    private String sessionUsername(HttpSession session) {
        Object value = session.getAttribute(USERNAME);
        return value instanceof String s ? s : null;
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