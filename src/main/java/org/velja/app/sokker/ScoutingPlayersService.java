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
public class ScoutingPlayersService {

    private static final Logger log = LoggerFactory.getLogger(ScoutingPlayersService.class);

    private static final String TABLE = "u21scouting";

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

    private static final String TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS %s (
                player_id            BIGINT PRIMARY KEY,
                name                 VARCHAR(255),
                age                  INT,
                club_id              BIGINT,
                club_name            VARCHAR(255),
                club_country_code    INT,
                wage                 BIGINT,
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
                skills_source        VARCHAR(16),
                skills_updated_at    TIMESTAMP,
                scanned_at           TIMESTAMP
            )
            """;

    private static final String TEAM_SCAN_SQL = """
            CREATE TABLE IF NOT EXISTS scouting_team_scan (
                team_id     BIGINT PRIMARY KEY,
                scanned_at  TIMESTAMP
            )
            """;

    private static final String INSERT_SQL = """
            INSERT INTO %s (player_id, name, age, club_id, club_name, club_country_code, wage,
                form, stamina, pace, keeper, defending, technique, playmaking, passing, striker,
                tactical_discipline, experience, teamwork, skills_source, skills_updated_at, scanned_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_SQL = """
            UPDATE %s SET name=?, age=?, club_id=?, club_name=?, club_country_code=?, wage=?,
                form=COALESCE(?, form), stamina=COALESCE(?, stamina), pace=COALESCE(?, pace),
                keeper=COALESCE(?, keeper), defending=COALESCE(?, defending),
                technique=COALESCE(?, technique), playmaking=COALESCE(?, playmaking),
                passing=COALESCE(?, passing), striker=COALESCE(?, striker),
                tactical_discipline=COALESCE(?, tactical_discipline),
                experience=COALESCE(?, experience), teamwork=COALESCE(?, teamwork),
                skills_source=?, skills_updated_at=?, scanned_at=?
                WHERE player_id=?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ScoutingPlayersService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void ensureTables() {
        jdbcTemplate.execute(TABLE_SQL.formatted(TABLE));
        jdbcTemplate.execute(TEAM_SCAN_SQL);
    }

    public int count() {
        ensureTables();
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + TABLE, Integer.class);
        return count == null ? 0 : count;
    }

    public int[] ages() {
        ensureTables();
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT age FROM " + TABLE + " WHERE age IS NOT NULL ORDER BY age", Integer.class)
                .stream().mapToInt(Integer::intValue).toArray();
    }

    public ArrayNode load() {
        ensureTables();
        List<ObjectNode> rows = jdbcTemplate.query(
                "SELECT player_id, name, age, club_id, club_name, club_country_code, wage, form, stamina, "
                        + "pace, keeper, defending, technique, playmaking, passing, striker, "
                        + "tactical_discipline, experience, teamwork, skills_source, skills_updated_at, "
                        + "scanned_at FROM " + TABLE,
                (rs, rowNum) -> toPlayer(rs));
        ArrayNode players = objectMapper.createArrayNode();
        rows.forEach(players::add);
        return players;
    }

    public List<Long> storedPlayerIds() {
        ensureTables();
        return jdbcTemplate.queryForList("SELECT player_id FROM " + TABLE + " ORDER BY player_id", Long.class);
    }

    public int upsertAll(List<JsonNode> players) {
        return upsertAll(players, "scan");
    }

    public int upsertAll(List<JsonNode> players, String skillsSource) {
        ensureTables();
        int stored = 0;
        for (JsonNode player : players) {
            Object[] row = toRow(player, skillsSource);
            if (row == null) {
                continue;
            }
            long playerId = (Long) row[0];
            Object[] updateArgs = new Object[row.length - 1 + 1];
            System.arraycopy(row, 1, updateArgs, 0, row.length - 1);
            updateArgs[row.length - 1] = playerId;
            if (jdbcTemplate.update(UPDATE_SQL.formatted(TABLE), updateArgs) == 0) {
                jdbcTemplate.update(INSERT_SQL.formatted(TABLE), row);
            }
            stored++;
        }
        return stored;
    }

    public int deleteNotMatchingCriteria(int maxAge) {
        ensureTables();
        return jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE age IS NULL OR age > ?", maxAge);
    }

    public void markTeamScanned(long teamId) {
        ensureTables();
        int updated = jdbcTemplate.update("UPDATE scouting_team_scan SET scanned_at=? WHERE team_id=?",
                Timestamp.from(Instant.now()), teamId);
        if (updated == 0) {
            jdbcTemplate.update("INSERT INTO scouting_team_scan (team_id, scanned_at) VALUES (?, ?)",
                    teamId, Timestamp.from(Instant.now()));
        }
    }

    public List<Long> staleTeamIds(int maxAge, int batchSize) {
        ensureTables();
        return jdbcTemplate.queryForList(
                "SELECT team_id FROM scouting_team_scan WHERE scanned_at < ? ORDER BY scanned_at LIMIT ?",
                Long.class, Timestamp.from(Instant.now().minusSeconds(maxAge * 86400L)), batchSize);
    }

    public int scannedTeamCount() {
        ensureTables();
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scouting_team_scan", Integer.class);
        return count == null ? 0 : count;
    }

    public void markSkillsSource(long playerId, String source) {
        ensureTables();
        jdbcTemplate.update("UPDATE " + TABLE + " SET skills_source=?, skills_updated_at=? WHERE player_id=?",
                source, Timestamp.from(Instant.now()), playerId);
    }

    private Object[] toRow(JsonNode player, String skillsSource) {
        JsonNode info = player.path("info");
        String name = info.path("name").path("full").asText();
        if (name.isEmpty()) {
            name = (info.path("name").path("name").asText() + " "
                    + info.path("name").path("surname").asText()).trim();
        }
        if (name.isEmpty()) {
            return null;
        }
        JsonNode team = info.path("team");
        Integer age = intOrNull(info.path("characteristics").path("age"));
        Object[] row = new Object[22];
        row[0] = player.path("id").asLong();
        row[1] = name;
        row[2] = age;
        row[3] = team.isMissingNode() || team.isNull() ? null : team.path("id").asLong();
        row[4] = team.isMissingNode() || team.isNull() ? null : team.path("name").asText(null);
        row[5] = team.isMissingNode() || team.isNull() ? null : intOrNull(team.path("country").path("code"));
        row[6] = money(info.path("wage").path("value"));
        int i = 7;
        JsonNode skills = info.path("skills");
        for (String column : SKILL_COLUMNS.values()) {
            row[i++] = intOrNull(skills.get(apiKeyFor(column)));
        }
        row[19] = skillsSource;
        row[20] = Timestamp.from(Instant.now());
        row[21] = Timestamp.from(Instant.now());
        return row;
    }

    private Long money(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return null;
        }
        return node.asLong();
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

    private ObjectNode toPlayer(ResultSet rs) throws SQLException {
        ObjectNode player = objectMapper.createObjectNode();
        player.put("id", rs.getLong("player_id"));
        ObjectNode info = player.putObject("info");
        ObjectNode name = info.putObject("name");
        name.put("full", rs.getString("name"));
        ObjectNode characteristics = info.putObject("characteristics");
        characteristics.put("age", rs.getObject("age") != null ? rs.getInt("age") : -1);
        ObjectNode team = info.putObject("team");
        team.put("id", rs.getLong("club_id"));
        team.put("name", rs.getString("club_name"));
        ObjectNode country = team.putObject("country");
        country.put("code", rs.getObject("club_country_code") != null ? rs.getInt("club_country_code") : -1);
        ObjectNode money = info.putObject("value");
        money.put("wage", rs.getObject("wage") != null ? rs.getLong("wage") : 0);
        ObjectNode skills = info.putObject("skills");
        for (Map.Entry<String, String> entry : SKILL_COLUMNS.entrySet()) {
            Integer value = rs.getObject(entry.getValue()) != null ? rs.getInt(entry.getValue()) : null;
            if (value != null) {
                skills.put(entry.getKey(), value);
            }
        }
        player.put("skillsSource", rs.getString("skills_source"));
        player.put("skillsUpdatedAt", rs.getTimestamp("skills_updated_at") == null
                ? "" : rs.getTimestamp("skills_updated_at").toInstant().toString());
        player.put("scannedAt", rs.getTimestamp("scanned_at") == null
                ? "" : rs.getTimestamp("scanned_at").toInstant().toString());
        return player;
    }

    public List<JsonNode> filterScouted(JsonNode players, int countryCode, int maxAge) {
        List<JsonNode> result = new ArrayList<>();
        if (!players.isArray()) {
            return result;
        }
        for (JsonNode player : players) {
            JsonNode info = player.path("info");
            Integer age = intOrNull(info.path("characteristics").path("age"));
            Integer nationality = intOrNull(info.path("country").path("code"));
            if (age != null && age <= maxAge && nationality != null && nationality == countryCode) {
                result.add(player);
            }
        }
        return result;
    }
}
