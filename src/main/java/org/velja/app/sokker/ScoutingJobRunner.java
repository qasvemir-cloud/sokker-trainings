package org.velja.app.sokker;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ScoutingJobRunner {

    private static final Logger log = LoggerFactory.getLogger(ScoutingJobRunner.class);

    private static final int TEAM_PAGE_LIMIT = 200;
    private static final int STALE_TEAM_DAYS = 30;
    private static final int RESUME_BATCH = 5000;
    private static final int ALL_CLUB_ESTIMATE = 151326;
    private static final int UPSERT_BATCH = 50;

    private final SokkerApiService sokkerApiService;
    private final ScoutingPlayersService scoutingPlayersService;
    private final ExecutorService jobExecutor;
    private final ExecutorService pool;

    private final Map<String, JobState> jobs = new ConcurrentHashMap<>();
    private final AtomicLong jobIds = new AtomicLong();
    private volatile String activeJobId;

    private final int countryCode;
    private final int maxAge;
    private final int threads;

    public ScoutingJobRunner(SokkerApiService sokkerApiService,
                             ScoutingPlayersService scoutingPlayersService,
                             @Value("${sokker.scouting-country-code:39}") int countryCode,
                             @Value("${sokker.scouting-max-age:21}") int maxAge,
                             @Value("${sokker.scouting-threads:5}") int threads) {
        this.sokkerApiService = sokkerApiService;
        this.scoutingPlayersService = scoutingPlayersService;
        this.countryCode = countryCode;
        this.maxAge = maxAge;
        this.threads = Math.max(1, threads);
        this.jobExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "scouting-job");
            thread.setDaemon(true);
            return thread;
        });
        this.pool = Executors.newFixedThreadPool(this.threads, runnable -> {
            Thread thread = new Thread(runnable, "scouting-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    public synchronized String start(String type, String phpSessionId) {
        String previous = activeJobId;
        if (previous != null) {
            JobState running = jobs.get(previous);
            if (running != null && running.running.get()) {
                throw new IllegalStateException("Scouting job already running: " + running.type);
            }
        }
        String jobId = "job-" + jobIds.incrementAndGet();
        JobState state = new JobState(jobId, type);
        jobs.put(jobId, state);
        activeJobId = jobId;
        jobExecutor.submit(() -> run(state, phpSessionId));
        return jobId;
    }

    public JobState status(String jobId) {
        return jobId == null ? null : jobs.get(jobId);
    }

    public void cancel(String jobId) {
        JobState state = jobs.get(jobId);
        if (state != null) {
            state.cancelled.set(true);
        }
    }

    private void run(JobState state, String phpSessionId) {
        long started = System.currentTimeMillis();
        try {
            switch (state.type) {
                case "serbian-clubs" -> scanSerbianClubs(state, phpSessionId);
                case "all-clubs" -> scanAllClubs(state, phpSessionId);
                case "update-skills" -> updateSkills(state, phpSessionId);
                default -> throw new IllegalStateException("Unknown job type: " + state.type);
            }
            state.error = null;
        } catch (Exception exception) {
            log.warn("[Scouting] job {} failed", state.jobId, exception);
            state.error = exception.getMessage();
        } finally {
            state.finishedAt = System.currentTimeMillis();
            state.running.set(false);
            log.info("[Scouting] job {} ({}) done in {} ms teams={} players={} failures={} cancelled={} error={}",
                    state.jobId, state.type, state.finishedAt - started,
                    state.teams.get(), state.players.get(), state.failures.get(),
                    state.cancelled.get(), state.error);
        }
    }

    private void scanSerbianClubs(JobState state, String phpSessionId) {
        List<JsonNode> teams = new ArrayList<>();
        int offset = 0;
        int total = Integer.MAX_VALUE;
        while (offset < total) {
            JsonNode page = sokkerApiService.countryTeamsPage(countryCode, TEAM_PAGE_LIMIT, offset, phpSessionId);
            total = page.path("total").asInt(0);
            JsonNode items = page.path("items");
            if (!items.isArray() || items.isEmpty()) {
                break;
            }
            items.forEach(teams::add);
            offset += TEAM_PAGE_LIMIT;
        }
        state.totalTeams = teams.size();
        state.total = teams.size();
        scanTeamBatch(state, phpSessionId, teams, List.of());
        finishScan(state);
    }

    private void scanAllClubs(JobState state, String phpSessionId) {
        List<Long> recentTeams = scoutingPlayersService.staleTeamIds(STALE_TEAM_DAYS, RESUME_BATCH);
        state.totalTeams = ALL_CLUB_ESTIMATE;
        state.total = ALL_CLUB_ESTIMATE;
        List<JsonNode> batch = new ArrayList<>();
        for (int offset = 0; offset < ALL_CLUB_ESTIMATE; offset += TEAM_PAGE_LIMIT) {
            if (state.cancelled.get()) {
                break;
            }
            JsonNode page = sokkerApiService.allTeamsPage(TEAM_PAGE_LIMIT, offset, phpSessionId);
            JsonNode items = page.path("items");
            if (!items.isArray() || items.isEmpty()) {
                break;
            }
            items.forEach(batch::add);
            if (batch.size() >= 1000) {
                scanTeamBatch(state, phpSessionId, batch, recentTeams);
                batch.clear();
            }
        }
        if (!batch.isEmpty() && !state.cancelled.get()) {
            scanTeamBatch(state, phpSessionId, batch, recentTeams);
        }
        finishScan(state);
    }

    private void finishScan(JobState state) {
        if (!state.cancelled.get()) {
            state.skippedPlayers = scoutingPlayersService.deleteNotMatchingCriteria(maxAge);
        }
    }

    private void scanTeamBatch(JobState state, String phpSessionId, List<JsonNode> teams, List<Long> skipTeamIds) {
        List<JsonNode> found = Collections.synchronizedList(new ArrayList<>());
        List<Callable<Void>> tasks = new ArrayList<>(teams.size());
        for (JsonNode team : teams) {
            long teamId = team.path("id").asLong();
            if (skipTeamIds.contains(teamId)) {
                state.skippedTeams.incrementAndGet();
                continue;
            }
            tasks.add(() -> {
                if (state.cancelled.get()) {
                    return null;
                }
                try {
                    JsonNode roster = sokkerApiService.teamPlayers((int) teamId, phpSessionId);
                    JsonNode players = roster.path("players");
                    if (players.isArray()) {
                        found.addAll(scoutingPlayersService.filterScouted(players, countryCode, maxAge));
                    }
                    scoutingPlayersService.markTeamScanned(teamId);
                } catch (Exception exception) {
                    state.failures.incrementAndGet();
                } finally {
                    state.teams.incrementAndGet();
                    state.detail = "teams " + state.teams.get() + "/" + state.total;
                }
                return null;
            });
        }
        runTasks(tasks, state);
        int pending = found.size();
        while (pending > 0 && !state.cancelled.get()) {
            int size = Math.min(UPSERT_BATCH, pending);
            state.players.addAndGet(scoutingPlayersService.upsertAll(new ArrayList<>(found.subList(0, size))));
            found.subList(0, size).clear();
            pending -= size;
        }
    }

    private void updateSkills(JobState state, String phpSessionId) {
        List<Long> playerIds = scoutingPlayersService.storedPlayerIds();
        state.total = playerIds.size();
        state.totalTeams = playerIds.size();
        List<JsonNode> found = Collections.synchronizedList(new ArrayList<>());
        List<Callable<Void>> tasks = new ArrayList<>(playerIds.size());
        for (long playerId : playerIds) {
            tasks.add(() -> {
                if (state.cancelled.get()) {
                    return null;
                }
                try {
                    JsonNode player = sokkerApiService.playerById(playerId, phpSessionId);
                    if (player != null && player.has("id")) {
                        found.add(player);
                    }
                } catch (Exception exception) {
                    state.failures.incrementAndGet();
                } finally {
                    state.teams.incrementAndGet();
                    state.detail = "players " + state.teams.get() + "/" + state.total;
                }
                return null;
            });
        }
        runTasks(tasks, state);
        int pending = found.size();
        while (pending > 0 && !state.cancelled.get()) {
            int size = Math.min(UPSERT_BATCH, pending);
            state.players.addAndGet(scoutingPlayersService.upsertAll(new ArrayList<>(found.subList(0, size)), "detail"));
            found.subList(0, size).clear();
            pending -= size;
        }
    }

    private void runTasks(List<Callable<Void>> tasks, JobState state) {
        if (tasks.isEmpty()) {
            return;
        }
        try {
            List<Future<Void>> futures = pool.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            state.cancelled.set(true);
        } catch (Exception exception) {
            log.warn("[Scouting] batch finished with error", exception);
        }
    }

    @PreDestroy
    public void shutdown() {
        jobExecutor.shutdownNow();
        pool.shutdownNow();
    }

    public static class JobState {
        public final String jobId;
        public final String type;
        public final long createdAt = System.currentTimeMillis();
        public final AtomicInteger teams = new AtomicInteger();
        public final AtomicInteger players = new AtomicInteger();
        public final AtomicInteger failures = new AtomicInteger();
        public final AtomicInteger skippedTeams = new AtomicInteger();
        public final AtomicBoolean running = new AtomicBoolean(true);
        public final AtomicBoolean cancelled = new AtomicBoolean();
        public volatile int total;
        public volatile int totalTeams;
        public volatile int skippedPlayers;
        public volatile long finishedAt;
        public volatile String detail = "";
        public volatile String error;

        JobState(String jobId, String type) {
            this.jobId = jobId;
            this.type = type;
        }
    }
}
