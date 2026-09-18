package org.velja.app.sokker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SktablesService {
    private static final String BASE_URL = "https://sktables.org";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36";
    private static final Pattern BODY_PATTERN = Pattern.compile("<tbody class=\"list\">(.*?)</tbody>", Pattern.DOTALL);
    private static final Pattern ROW_PATTERN = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.DOTALL);
    private static final Pattern CELL_PATTERN = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL);
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?\\d+");
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final Pattern PERCENT_PATTERN = Pattern.compile("(\\d+)\\s*%");
    private static final Pattern ACADEMY_ROW_ID_PATTERN = Pattern.compile("/academy/talent/ID/(\\d+)");

    private static final List<String> SKILLS = List.of(
            "stamina", "keeper", "pace", "defending", "technique", "playmaking", "passing", "striker"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public Optional<String> login(String login, String password) {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient loginClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        String body = "login=" + encode(login) + "&password=" + encode(password);
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/login"))
                .timeout(Duration.ofSeconds(30))
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9")
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .header(HttpHeaders.REFERER, BASE_URL + "/login")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = loginClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 400) {
                return Optional.empty();
            }
            String cookieHeader = cookieManager.getCookieStore().getCookies().stream()
                    .map(cookie -> cookie.getName() + "=" + cookie.getValue())
                    .reduce((left, right) -> left + "; " + right)
                    .orElse("");
            if (cookieHeader.isBlank()) {
                return Optional.empty();
            }
            return myTeamAccessible(cookieHeader) ? Optional.of(cookieHeader) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    public JsonNode mergeTrainingReport(JsonNode sokkerReport, long playerId, String cookieHeader) {
        ArrayNode sktablesReports = trainingReports(playerId, cookieHeader);
        if (sktablesReports.isEmpty()) {
            return sokkerReport;
        }
        OptionalInt seasonOffset = seasonOffset(sokkerReport, playerId);
        if (seasonOffset.isEmpty()) {
            return sokkerReport;
        }
        assignSeasons(sktablesReports, seasonOffset.getAsInt());

        ObjectNode merged = sokkerReport.deepCopy();
        ArrayNode sokkerReports = sokkerReport.withArray("reports");
        Set<String> sokkerWeeks = new HashSet<>();
        Map<String, JsonNode> sktablesByWeek = new HashMap<>();
        sktablesReports.forEach(report -> sktablesByWeek.put(weekKey(report), report));

        List<JsonNode> rows = new ArrayList<>();
        sokkerReports.forEach(report -> {
            String key = weekKey(report);
            sokkerWeeks.add(key);
            String typeName = report.path("type").path("name").asText("");
            if ("general".equals(typeName) && sktablesByWeek.containsKey(key)) {
                rows.add(sktablesByWeek.get(key));
            } else {
                rows.add(report);
            }
        });
        sktablesByWeek.forEach((key, report) -> {
            if (!sokkerWeeks.contains(key)) {
                rows.add(report);
            }
        });

        rows.sort(Comparator.comparingInt(SktablesService::sortableWeek).reversed());
        ArrayNode reports = objectMapper.createArrayNode();
        rows.forEach(reports::add);
        fillMissingDates(reports);
        merged.set("reports", reports);
        return merged;
    }

    private static OptionalInt seasonOffset(JsonNode sokkerReport, long playerId) {
        for (JsonNode report : sokkerReport.path("reports")) {
            int season = report.path("day").path("season").asInt(0);
            int age = report.path("age").asInt(0);
            if (season > 0 && age > 0) {
                return OptionalInt.of(season - age);
            }
        }
        return OptionalInt.empty();
    }

    private void assignSeasons(ArrayNode reports, int offset) {
        for (JsonNode node : reports) {
            ObjectNode report = (ObjectNode) node;
            ObjectNode day = (ObjectNode) report.path("day");
            int age = day.path("age").asInt(0);
            int seasonWeek = day.path("seasonWeek").asInt(0);
            if (age <= 0 || seasonWeek <= 0) {
                continue;
            }
            int season = age + offset;
            day.put("season", season);
            day.put("week", season * 100 + seasonWeek);
            day.put("seasonWeek", seasonWeek);
            report.put("week", season * 100 + seasonWeek);
        }
    }

    private void fillMissingDates(ArrayNode reports) {
        Set<Integer> missingSeasons = new HashSet<>();
        for (JsonNode report : reports) {
            JsonNode date = report.path("day").path("date");
            String dateValue = date.isObject() ? date.path("value").asText("") : "";
            if (dateValue.isEmpty()) {
                int season = report.path("day").path("season").asInt(0);
                if (season > 0) missingSeasons.add(season);
            }
        }
        if (missingSeasons.isEmpty()) return;

        Map<Integer, LocalDate> seasonStarts = new HashMap<>();
        for (int season : missingSeasons) {
            try {
                JsonNode seasonInfo = fetchSeasonInfo(season);
                String startStr = seasonInfo.path("start").path("date").path("value").asText("");
                if (!startStr.isEmpty()) seasonStarts.put(season, LocalDate.parse(startStr));
            } catch (Exception e) {
                // season info unavailable — skip
            }
        }
        if (seasonStarts.isEmpty()) return;

        for (JsonNode report : reports) {
            JsonNode day = report.path("day");
            JsonNode date = day.path("date");
            String dateValue = date.isObject() ? date.path("value").asText("") : "";
            if (!dateValue.isEmpty()) continue;
            int season = day.path("season").asInt(0);
            int seasonWeek = day.path("seasonWeek").asInt(0);
            LocalDate start = seasonStarts.get(season);
            if (start != null && seasonWeek > 0 && day instanceof ObjectNode dayObj) {
                dayObj.putObject("date").put("value", start.plusDays((seasonWeek - 1) * 7L + 5).toString());
            }
        }
    }

    private JsonNode fetchSeasonInfo(int season) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://sokker.org/api/season/" + season))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Season API returned " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    public JsonNode academy(String cookieHeader) {
        try {
            HttpRequest request = baseRequest(BASE_URL + "/academy", cookieHeader).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return objectMapper.createObjectNode().set("juniors", objectMapper.createArrayNode());
            }
            ObjectNode payload = objectMapper.createObjectNode();
            payload.set("juniors", parseAcademyTable(response.body()));
            payload.put("source", "sktables");
            return payload;
        } catch (IOException e) {
            return objectMapper.createObjectNode().set("juniors", objectMapper.createArrayNode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return objectMapper.createObjectNode().set("juniors", objectMapper.createArrayNode());
        }
    }

    private boolean myTeamAccessible(String cookieHeader) throws IOException, InterruptedException {
        HttpRequest request = baseRequest(BASE_URL + "/myteam", cookieHeader).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 200 && response.body().contains("My Team");
    }

    private ArrayNode trainingReports(long playerId, String cookieHeader) {
        try {
            HttpRequest request = baseRequest(BASE_URL + "/myteam/player/" + playerId, cookieHeader).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return objectMapper.createArrayNode();
            }
            return parseTrainingTable(response.body());
        } catch (IOException e) {
            return objectMapper.createArrayNode();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return objectMapper.createArrayNode();
        }
    }

    private ArrayNode parseTrainingTable(String html) {
        Matcher bodyMatcher = BODY_PATTERN.matcher(html);
        if (!bodyMatcher.find()) {
            return objectMapper.createArrayNode();
        }

        List<ObjectNode> latestFirst = new ArrayList<>();
        Matcher rowMatcher = ROW_PATTERN.matcher(bodyMatcher.group(1));
        while (rowMatcher.find()) {
            List<String> cells = cells(rowMatcher.group(1));
            if (cells.size() < 13) {
                continue;
            }
            ObjectNode report = reportFromCells(cells);
            if (report != null) {
                latestFirst.add(report);
            }
        }

        ObjectNode previous = null;
        for (int i = latestFirst.size() - 1; i >= 0; i--) {
            ObjectNode current = latestFirst.get(i);
            ObjectNode change = objectMapper.createObjectNode();
            int up = 0;
            int down = 0;
            for (String skill : SKILLS) {
                int value = current.path("skills").path(skill).asInt(0);
                int delta = previous == null ? 0 : value - previous.path("skills").path(skill).asInt(value);
                change.put(skill, delta);
                if (delta > 0) {
                    up += delta;
                } else if (delta < 0) {
                    down += Math.abs(delta);
                }
            }
            change.put("form", 0);
            change.put("tacticalDiscipline", 0);
            change.put("teamwork", 0);
            change.put("experience", 0);
            change.put("up", up);
            change.put("down", down);
            current.set("skillsChange", change);
            previous = current;
        }

        ArrayNode reports = objectMapper.createArrayNode();
        latestFirst.forEach(reports::add);
        return reports;
    }

    private ArrayNode parseAcademyTable(String html) {
        Matcher bodyMatcher = Pattern.compile("<tbody[^>]*class=\"[^\"]*links-not-underlined[^\"]*\"[^>]*>(.*?)</tbody>", Pattern.DOTALL)
                .matcher(html);
        if (!bodyMatcher.find()) {
            bodyMatcher = Pattern.compile("<tbody[^>]*>(.*?)</tbody>", Pattern.DOTALL).matcher(html);
            if (!bodyMatcher.find()) {
                return objectMapper.createArrayNode();
            }
        }

        ArrayNode juniors = objectMapper.createArrayNode();
        Matcher rowMatcher = ROW_PATTERN.matcher(bodyMatcher.group(1));
        while (rowMatcher.find()) {
            String rowHtml = rowMatcher.group(1);
            List<String> rowCells = cells(rowHtml);
            if (rowCells.size() < 8) {
                continue;
            }
            Matcher idMatcher = ACADEMY_ROW_ID_PATTERN.matcher(rowHtml);
            if (!idMatcher.find()) {
                continue;
            }

            ObjectNode junior = objectMapper.createObjectNode();
            junior.put("id", Long.parseLong(idMatcher.group(1)));
            junior.put("name", text(rowCells.get(0)));
            junior.put("age", firstInt(text(rowCells.get(1)), 0));
            junior.put("skill", firstInt(text(rowCells.get(2)), 0));
            junior.put("change", academySkillChange(rowCells.get(2)));
            String talentText = text(rowCells.get(3));
            junior.put("talent", academyCellNumber(rowCells.get(3), null, 0));
            junior.put("talentUncertain", talentText.contains("?"));
            junior.put("weeksLeft", firstInt(text(rowCells.get(4)), 0));
            junior.put("ageOut", academyCellNumber(rowCells.get(5), null, 0));
            junior.put("finalLevel", firstInt(text(rowCells.get(6)), 0));
            junior.put("potential", text(rowCells.get(7)));
            junior.put("score", academyCellNumber(rowCells.get(7), "data-sort=\"([^\"]+)\"", 0));
            juniors.add(junior);
        }
        return juniors;
    }

    private ObjectNode reportFromCells(List<String> cells) {
        int age = firstInt(text(cells.get(0)), -1);
        int seasonWeek = firstInt(text(cells.get(1)), -1);
        if (age < 0 || seasonWeek < 0) {
            return null;
        }

        String formation = text(cells.get(2)).trim();
        int intensity = firstPercent(cells.get(12), 0);
        String trainedSkill = trainedSkill(cells);
        boolean missing = intensity <= 0 || hasClass(cells, "bg-gray");

        ObjectNode report = objectMapper.createObjectNode();
        report.put("week", 0);
        report.put("source", "sktables");

        ObjectNode day = report.putObject("day");
        day.put("age", age);
        day.put("seasonWeek", seasonWeek);
        day.put("day", 5);

        ObjectNode skills = report.putObject("skills");
        skills.put("form", 0);
        skills.put("tacticalDiscipline", 0);
        skills.put("teamwork", 0);
        skills.put("experience", 0);
        for (int i = 0; i < SKILLS.size(); i++) {
            skills.put(SKILLS.get(i), firstInt(text(cells.get(i + 4)), 0));
        }

        ObjectNode type = report.putObject("type");
        type.put("code", skillCode(trainedSkill));
        type.put("name", trainedSkill == null ? "missing" : trainedSkill);

        ObjectNode kind = report.putObject("kind");
        kind.put("code", missing ? 0 : 1);
        kind.put("name", missing ? "missing" : "individual");

        report.put("intensity", intensity);
        ObjectNode formationNode = report.putObject("formation");
        formationNode.put("code", formationCode(formation));
        formationNode.put("name", formation.isBlank() ? "-" : formation);
        ObjectNode injury = report.putObject("injury");
        injury.put("daysRemaining", firstInjuryDays(cells.get(12)));
        injury.put("severe", false);
        return report;
    }

    private List<String> cells(String rowHtml) {
        List<String> cells = new ArrayList<>();
        Matcher matcher = CELL_PATTERN.matcher(rowHtml);
        while (matcher.find()) {
            cells.add(matcher.group(1));
        }
        return cells;
    }

    private String trainedSkill(List<String> cells) {
        for (int i = 0; i < SKILLS.size(); i++) {
            String cell = cells.get(i + 4);
            if (cell.contains("bg-purple") || cell.contains("bg-blue")) {
                return SKILLS.get(i);
            }
        }
        return null;
    }

    private boolean hasClass(List<String> cells, String className) {
        return cells.stream().anyMatch(cell -> cell.contains(className));
    }

    private boolean hasSkillClass(List<String> cells, String skill, String className) {
        int index = SKILLS.indexOf(skill);
        return index >= 0 && cells.get(index + 4).contains(className);
    }

    private static int sortableWeek(JsonNode report) {
        int season = report.path("day").path("season").asInt(0);
        int seasonWeek = report.path("day").path("seasonWeek").asInt(0);
        if (season > 0 && seasonWeek > 0) {
            return season * 100 + seasonWeek;
        }
        return report.path("week").asInt(0);
    }

    private static String weekKey(JsonNode report) {
        int season = report.path("day").path("season").asInt(0);
        int seasonWeek = report.path("day").path("seasonWeek").asInt(0);
        if (season > 0 && seasonWeek > 0) {
            return season + ":" + seasonWeek;
        }
        return "week:" + report.path("week").asInt(0);
    }

    private static int skillCode(String skill) {
        if (skill == null) {
            return 0;
        }
        return switch (skill) {
            case "keeper" -> 1;
            case "defending" -> 2;
            case "playmaking" -> 3;
            case "passing" -> 4;
            case "technique" -> 5;
            case "striker" -> 6;
            case "stamina" -> 7;
            case "pace" -> 8;
            default -> 0;
        };
    }

    private static int formationCode(String formation) {
        return switch (formation) {
            case "GK" -> 0;
            case "DEF" -> 1;
            case "MID" -> 2;
            case "ATT" -> 3;
            default -> -1;
        };
    }

    private static String text(String html) {
        return TAG_PATTERN.matcher(html)
                .replaceAll(" ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static int firstInt(String text, int fallback) {
        Matcher matcher = INTEGER_PATTERN.matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group()) : fallback;
    }

    private static int academySkillChange(String html) {
        if (html.contains("green")) {
            return 1;
        }
        if (html.contains("red")) {
            return -1;
        }
        return 0;
    }

    private static double academyCellNumber(String html, String sourcePattern, double fallback) {
        String source = html;
        if (sourcePattern != null) {
            Matcher sourceMatcher = Pattern.compile(sourcePattern).matcher(html);
            if (sourceMatcher.find()) {
                source = sourceMatcher.group(1);
            }
        } else {
            source = text(html);
        }
        Matcher matcher = DECIMAL_PATTERN.matcher(source);
        return matcher.find() ? Double.parseDouble(matcher.group()) : fallback;
    }

    private static int firstPercent(String html, int fallback) {
        Matcher matcher = PERCENT_PATTERN.matcher(text(html));
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }

    private static int firstInjuryDays(String html) {
        Matcher matcher = Pattern.compile("<sup[^>]*class=\"[^\"]*red[^\"]*\"[^>]*>\\s*(\\d+)\\s*</sup>", Pattern.DOTALL)
                .matcher(html);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static HttpRequest.Builder baseRequest(String url, String cookieHeader) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9")
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .header(HttpHeaders.COOKIE, cookieHeader);
    }
}
