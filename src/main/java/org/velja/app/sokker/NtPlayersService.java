package org.velja.app.sokker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NtPlayersService {

    private static final Logger log = LoggerFactory.getLogger(NtPlayersService.class);

    private static final Map<String, String> SKILL_COLUMNS = new LinkedHashMap<>();

    static {
        SKILL_COLUMNS.put("form", "form");
        SKILL_COLUMNS.put("stamina", "stamina");
        SKILL_COLUMNS.put("pace", "pace");
        SKILL_COLUMNS.put("keeper", "keeper");
        SKILL_COLUMNS.put("defending", "defending");
        SKILL_COLUMNS.put("technique", "technique");
        SKILL_COLUMNS.put("playmaking", "playmaking");
        SKILL_COLUMNS.put("passing", "passing");
        SKILL_COLUMNS.put("striker", "striker");
        SKILL_COLUMNS.put("tacticalDiscipline", "tactical_discipline");
        SKILL_COLUMNS.put("experience", "experience");
        SKILL_COLUMNS.put("teamwork", "teamwork");
    }

    private static final List<String> PUBLIC_SKILLS = List.of(
            "form", "tacticalDiscipline", "experience", "teamwork"
    );

    private static final String TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS nt_players (
                player_id            BIGINT PRIMARY KEY,
                name                 VARCHAR(255),
                age                  INT,
                form                 INT,
                stamina              INT,
                pace                 INT,
                keeper               INT,
                defending            INT,
                technique            INT,
                playmaking           INT,
                passing              INT,
                striker              INT,
                tactical_discipline  INT,
                experience           INT,
                teamwork             INT,
                updated_at           TIMESTAMP
            )
            """;

    private static final String INSERT_SQL = """
            INSERT INTO nt_players (player_id, name, age, form, stamina, pace, keeper,
                defending, technique, playmaking, passing, striker, tactical_discipline,
                experience, teamwork, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public NtPlayersService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public ArrayNode load(boolean showAllSkills) {
        ensureTable();
        List<ObjectNode> rows = jdbcTemplate.query("""
                        SELECT player_id, name, age, form, stamina, pace, keeper, defending,
                               technique, playmaking, passing, striker, tactical_discipline,
                               experience, teamwork
                        FROM nt_players
                        ORDER BY name
                        """,
                (rs, rowNum) -> toPlayer(rs, showAllSkills));
        ArrayNode players = objectMapper.createArrayNode();
        rows.forEach(players::add);
        return players;
    }

    public int replaceAll(JsonNode players) {
        ensureTable();
        List<Object[]> batch = new ArrayList<>();
        for (JsonNode player : players) {
            Object[] row = toRow(player);
            if (row != null) {
                batch.add(row);
            }
        }
        jdbcTemplate.update("DELETE FROM nt_players");
        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(INSERT_SQL, batch);
        }
        log.info("[NT] Updated {} players in nt_players table.", batch.size());
        return batch.size();
    }

    private Object[] toRow(JsonNode player) {
        JsonNode info = player.path("info");
        String name = info.path("name").path("full").asText();
        if (name.isEmpty()) {
            name = (info.path("name").path("name").asText() + " "
                    + info.path("name").path("surname").asText()).trim();
        }
        if (name.isEmpty()) {
            return null;
        }
        int age = info.path("characteristics").path("age").asInt(-1);
        JsonNode skills = info.path("skills");
        Object[] row = new Object[16];
        row[0] = player.path("id").asLong();
        row[1] = name;
        row[2] = age == -1 ? null : age;
        int i = 3;
        for (String column : SKILL_COLUMNS.values()) {
            row[i++] = intOrNull(skills.get(apiKeyFor(column)));
        }
        row[15] = Timestamp.from(Instant.now());
        return row;
    }

    private void ensureTable() {
        jdbcTemplate.execute(TABLE_SQL);
    }

    private String apiKeyFor(String column) {
        for (Map.Entry<String, String> entry : SKILL_COLUMNS.entrySet()) {
            if (entry.getValue().equals(column)) {
                return entry.getKey();
            }
        }
        return column;
    }

    private Integer intOrNull(JsonNode node) {
        return node != null && node.isNumber() ? node.asInt() : null;
    }

    private ObjectNode toPlayer(ResultSet rs, boolean showAllSkills) throws SQLException {
        ObjectNode player = objectMapper.createObjectNode();
        player.put("id", rs.getLong("player_id"));
        ObjectNode info = player.putObject("info");
        ObjectNode name = info.putObject("name");
        name.put("full", rs.getString("name"));
        ObjectNode characteristics = info.putObject("characteristics");
        characteristics.put("age", rs.getObject("age") != null ? rs.getInt("age") : -1);
        ObjectNode skills = info.putObject("skills");
        for (Map.Entry<String, String> entry : SKILL_COLUMNS.entrySet()) {
            if (!showAllSkills && !PUBLIC_SKILLS.contains(entry.getKey())) {
                continue;
            }
            Integer value = rs.getObject(entry.getValue()) != null ? rs.getInt(entry.getValue()) : null;
            if (value != null) {
                skills.put(entry.getKey(), value);
            }
        }
        return player;
    }
}