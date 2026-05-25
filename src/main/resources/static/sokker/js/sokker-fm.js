const api = {
    login: '/sokker/api/login',
    logout: '/sokker/api/logout',
    me: '/sokker/api/me',
    players: '/sokker/api/players',
    training: '/sokker/api/training/current',
    trainingPlayers: '/sokker/api/training/players',
    playerTraining: (playerId) => `/sokker/api/players/${playerId}/training`
};

const fallbackTeamLogo = '/sokker/images/omladinacLogo.png';

const skillLabels = [
    ['form', 'Form'],
    ['stamina', 'Sta'],
    ['pace', 'Pace'],
    ['keeper', 'GK'],
    ['defending', 'Def'],
    ['technique', 'Tech'],
    ['playmaking', 'PM'],
    ['passing', 'Pass'],
    ['striker', 'Str']
];

const predictorSkills = ['pace', 'defending', 'technique', 'passing', 'playmaking', 'striker'];
const skillNames = Object.fromEntries(skillLabels);
const directFormation = { keeper: 'GK', defending: 'DEF', playmaking: 'MID', striker: 'ATT' };
const levelCredits = {
    1: 1.6, 2: 1.75, 3: 1.87, 4: 2.2, 5: 2.26, 6: 2.27, 7: 2.4, 8: 2.49,
    9: 2.67, 10: 3.04, 11: 4.09, 12: 4.57, 13: 5.43, 14: 6.03, 15: 7.19, 16: 9.23
};
const skillFactor = {
    pace: 1.18,
    defending: 0.96,
    technique: 1,
    passing: 1.02,
    playmaking: 0.98,
    striker: 1
};
const gtRatio = 6;

const state = {
    current: null,
    players: [],
    trainingRows: [],
    trainingSetup: null,
    playerReports: new Map(),
    focusedPredictorPlayerId: null,
    activeView: 'players',
    playerSort: 'name'
};

const loginScreen = document.querySelector('#login-screen');
const appScreen = document.querySelector('#app-screen');
const loginForm = document.querySelector('#login-form');
const loginError = document.querySelector('#login-error');
const playersView = document.querySelector('#players-view');
const trainingView = document.querySelector('#training-view');
const predictorView = document.querySelector('#predictor-view');
const playerDetailView = document.querySelector('#player-detail-view');

loginForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    loginError.textContent = '';
    const button = loginForm.querySelector('button');
    button.disabled = true;
    button.textContent = 'Logging in...';

    try {
        const form = new FormData(loginForm);
        state.current = await postJson(api.login, {
            login: form.get('login'),
            password: form.get('password')
        });
        await bootstrap();
    } catch (error) {
        loginError.textContent = error.message;
    } finally {
        button.disabled = false;
        button.textContent = 'Login';
    }
});

document.querySelector('#logout-button').addEventListener('click', async () => {
    await fetch(api.logout, { method: 'POST' });
    state.current = null;
    state.players = [];
    state.trainingRows = [];
    appScreen.classList.add('hidden');
    loginScreen.classList.remove('hidden');
});

document.querySelectorAll('[data-view]').forEach((item) => {
    item.addEventListener('click', () => showView(item.dataset.view));
});

async function bootstrap() {
    loginScreen.classList.add('hidden');
    appScreen.classList.remove('hidden');
    await loadData();
    renderShell();
    showView('players');
}

async function loadData() {
    const [playersResponse, trainingResponse] = await Promise.all([
        getJson(api.players),
        getJson(api.training)
    ]);
    state.players = playersResponse.players || [];
    state.trainingRows = trainingResponse.players || [];
}

function renderShell() {
    const team = state.current?.team || {};
    const teamLogo = resolveSokkerAsset(team.emblem);
    document.querySelector('#team-name').textContent = team.name || 'Sokker Club';
    document.querySelector('#team-meta').textContent = [
        team.country?.name,
        team.rank ? `Rank ${number(team.rank)}` : null,
        team.rankPosition ? `Position ${team.rankPosition}` : null
    ].filter(Boolean).join(' | ');
    setTeamLogo(document.querySelector('#team-emblem'), teamLogo);
    setTeamLogo(document.querySelector('#nav-team-emblem'), teamLogo);

    const weeklyTotals = sumWeeklyChanges();
    document.querySelector('#stat-players').textContent = state.players.length;
    document.querySelector('#stat-form').innerHTML = statPair(weeklyTotals.form.up, weeklyTotals.form.down);
    document.querySelector('#stat-stamina').innerHTML = statPair(weeklyTotals.stamina.up, weeklyTotals.stamina.down);
    document.querySelector('#stat-skills').innerHTML = statPair(weeklyTotals.skills.up, weeklyTotals.skills.down);

    renderPlayers();
    renderTraining();
    predictorView.innerHTML = '';
}

function setTeamLogo(image, url) {
    image.onerror = () => {
        image.onerror = null;
        image.src = fallbackTeamLogo;
    };
    image.src = url || fallbackTeamLogo;
}

function resolveSokkerAsset(url) {
    if (!url) {
        return fallbackTeamLogo;
    }
    if (url.startsWith('http://') || url.startsWith('https://')) {
        return url;
    }
    if (url.startsWith('/')) {
        return `https://sokker.org${url}`;
    }
    return url;
}

function showView(view) {
    state.activeView = view;
    document.querySelectorAll('.view').forEach((element) => element.classList.add('hidden'));
    document.querySelectorAll('[data-view]').forEach((element) => {
        element.classList.toggle('active', element.dataset.view === view);
    });

    if (view === 'training') {
        document.querySelector('#page-title').textContent = 'Training Reports';
        trainingView.classList.remove('hidden');
    } else if (view === 'predictor') {
        document.querySelector('#page-title').textContent = 'Training Predictor';
        predictorView.classList.remove('hidden');
        renderPredictor();
    } else {
        document.querySelector('#page-title').textContent = 'First Team';
        playersView.classList.remove('hidden');
    }
}

function renderPlayers() {
    const sortedPlayers = [...state.players].sort((a, b) => {
        if (state.playerSort === 'age') {
            return Number(age(a)) - Number(age(b));
        }
        return fullName(a).localeCompare(fullName(b));
    });
    playersView.innerHTML = `
        <div class="view-panel">
            <div class="toolbar">
                <h2>First Team</h2>
                <div class="toolbar-actions">
                    <button class="action-button secondary" data-sort="name">Name</button>
                    <button class="action-button secondary" data-sort="age">Age</button>
                </div>
            </div>
            <div class="player-grid">
                ${sortedPlayers.map(playerCard).join('')}
            </div>
        </div>
    `;

    playersView.querySelector('[data-sort="age"]').addEventListener('click', () => {
        state.playerSort = 'age';
        renderPlayers();
    });
    playersView.querySelector('[data-sort="name"]').addEventListener('click', () => {
        state.playerSort = 'name';
        renderPlayers();
    });
    playersView.querySelectorAll('[data-player-id]').forEach((card) => {
        card.addEventListener('click', () => openPlayerDetail(Number(card.dataset.playerId)));
    });
}

function renderTraining() {
    const rows = state.trainingRows.map((row) => {
        const player = row.player ? { id: row.id, info: row.player } : findPlayer(row.id);
        return { id: row.id, player, report: row.report };
    });

    trainingView.innerHTML = `
        <div class="view-panel">
            <div class="toolbar">
                <h2>Current Week Training</h2>
                <button id="refresh-training" class="action-button">Refresh</button>
            </div>
            <div class="table-scroll">
                <table class="data-table">
                    <thead>
                    <tr>
                        <th>Igrac</th>
                        <th>Godine</th>
                        <th>Tip</th>
                        <th>Intenzitet</th>
                        <th>Skokovi / padovi ove nedelje</th>
                    </tr>
                    </thead>
                    <tbody>
                    ${rows.length ? rows.map(trainingRow).join('') : `<tr><td colspan="5" class="empty-state">Nema trening izvestaja za ovu nedelju.</td></tr>`}
                    </tbody>
                </table>
            </div>
        </div>
    `;

    trainingView.querySelector('#refresh-training').addEventListener('click', async () => {
        await loadData();
        renderShell();
        showView('training');
    });
    trainingView.querySelectorAll('[data-player-id]').forEach((row) => {
        row.addEventListener('click', () => openPlayerDetail(Number(row.dataset.playerId)));
    });
}

async function renderPredictor() {
    predictorView.innerHTML = `
        <div class="view-panel">
            <div class="toolbar">
                <h2>Advanced Training Predictor</h2>
                <button id="refresh-predictor" class="action-button">Refresh</button>
            </div>
            <div class="empty-state">Ucitavam istoriju advanced igraca...</div>
        </div>
    `;
    bindPredictorRefresh();

    if (!state.trainingSetup) {
        state.trainingSetup = await getJson(api.trainingPlayers);
    }
    const advanced = state.trainingSetup.advanced || [];
    await Promise.all(advanced.map((player) => loadPlayerReport(player.id)));

    predictorView.innerHTML = `
        <div class="view-panel">
            <div class="toolbar">
                <h2>${state.focusedPredictorPlayerId ? 'Player Predictor' : 'Advanced Training Predictor'}</h2>
                <div class="toolbar-actions">
                    ${state.focusedPredictorPlayerId ? '<button id="predictor-back" class="action-button secondary">Back</button>' : ''}
                    <button id="refresh-predictor" class="action-button">Refresh</button>
                </div>
            </div>
            ${state.focusedPredictorPlayerId ? renderFocusedPredictor(advanced) : renderPredictorList(advanced)}
        </div>
    `;
    bindPredictorRefresh();
    bindPredictorFocus();
    bindTraceButtons();
}

function renderPredictorList(advanced) {
    return `
        <div class="predictor-list">
            ${advanced.length ? advanced.map(predictorListItem).join('') : `<div class="empty-state">Nema igraca na advanced treningu.</div>`}
        </div>
    `;
}

function renderFocusedPredictor(advanced) {
    const player = advanced.find((item) => Number(item.id) === Number(state.focusedPredictorPlayerId));
    return player ? `<div class="predictor-single">${predictorCard(player)}</div>` : `<div class="empty-state">Igrac nije pronadjen na advanced treningu.</div>`;
}

function predictorListItem(trainingPlayer) {
    const row = state.trainingRows.find((item) => Number(item.id) === Number(trainingPlayer.id));
    const currentReport = row?.report || {};
    const trainedSkill = currentReport.type?.name || 'pace';
    const player = findPlayer(trainingPlayer.id) || { id: trainingPlayer.id, info: trainingPlayer.info };
    const reports = state.playerReports.get(trainingPlayer.id) || [];
    const mainPrediction = predictSkill(player, reports, trainedSkill, 'DT');

    return `
        <button class="predictor-list-item" data-focus-player="${player.id}">
            <span>
                <strong>${escapeHtml(fullName(player))}</strong>
                <small>Age ${age(player)} | ${escapeHtml(currentReport.formation?.name || trainingPlayer.formation?.name || '-')} | ${currentReport.intensity ?? trainingPlayer.intensity ?? '-'}%</small>
            </span>
            <span class="training-badge">DT ${escapeHtml(skillNames[trainedSkill] || trainedSkill)}</span>
            <span class="predictor-list-prob">${mainPrediction.nextProbability}%</span>
        </button>
    `;
}

function bindPredictorRefresh() {
    predictorView.querySelector('#refresh-predictor')?.addEventListener('click', async () => {
        state.trainingSetup = null;
        state.playerReports.clear();
        await loadData();
        renderShell();
        renderPredictor();
    });
}

function bindPredictorFocus() {
    predictorView.querySelector('#predictor-back')?.addEventListener('click', () => {
        state.focusedPredictorPlayerId = null;
        renderPredictor();
    });
    predictorView.querySelectorAll('[data-focus-player]').forEach((button) => {
        button.addEventListener('click', () => {
            state.focusedPredictorPlayerId = Number(button.dataset.focusPlayer);
            renderPredictor();
        });
    });
}

function bindTraceButtons() {
    predictorView.querySelectorAll('[data-trace-player]').forEach((button) => {
        button.addEventListener('click', () => {
            const playerId = Number(button.dataset.tracePlayer);
            const skill = button.dataset.traceSkill;
            renderSkillTrace(playerId, skill);
        });
    });
}

function renderSkillTrace(playerId, skill) {
    const player = findPlayer(playerId) || { id: playerId, info: {} };
    const reports = state.playerReports.get(playerId) || [];
    const trace = buildSkillTrace(reports, skill);
    const target = predictorView.querySelector(`#trace-${playerId}`);
    if (!target) {
        return;
    }
    target.innerHTML = `
        <div class="trace-head">
            <strong>${escapeHtml(fullName(player))}: ${escapeHtml(skillNames[skill] || skill)}</strong>
            <span>${trace.current.level} -> ${trace.current.level + 1}</span>
        </div>
        <div class="trace-section">
            <div class="small-muted">Prethodni skokovi</div>
            ${trace.intervals.length ? trace.intervals.map(traceInterval).join('') : '<div class="small-muted">Nema kompletnih intervala; koristi se globalni fallback.</div>'}
        </div>
        <div class="trace-section">
            <div class="small-muted">Trenutno od poslednjeg skoka</div>
            <div class="trace-line">
                <span>Credit ${trace.current.credit.toFixed(2)} | DT ${trace.current.dtWeeks} | GT ${trace.current.gtWeeks} | skip ${trace.current.skipWeeks}</span>
                <strong>${trace.current.weeks} ned.</strong>
            </div>
            ${trace.current.weeksList.map(traceWeek).join('')}
        </div>
    `;
}

function traceInterval(interval) {
    return `
        <div class="trace-line">
            <span>${interval.from} -> ${interval.to}: ${interval.credits.toFixed(2)} credit = ${interval.dtWeeks} DT + ${interval.gtWeeks} GT + ${interval.skipWeeks} skip</span>
            <strong>S${interval.season}/${interval.seasonWeek}</strong>
        </div>
    `;
}

function traceWeek(week) {
    return `
        <div class="trace-week">
            <span>S${week.season}/${week.seasonWeek} ${escapeHtml(week.type)} ${escapeHtml(week.formation)} ${week.intensity}% ${week.source === 'sktables' ? 'SkTables' : 'Sokker'}</span>
            <strong>${week.mode} +${week.credit.toFixed(2)}</strong>
        </div>
    `;
}

async function loadPlayerReport(playerId) {
    if (state.playerReports.has(playerId)) {
        return state.playerReports.get(playerId);
    }
    const response = await getJson(api.playerTraining(playerId));
    const reports = response.reports || [];
    state.playerReports.set(playerId, reports);
    return reports;
}

function predictorCard(trainingPlayer) {
    const row = state.trainingRows.find((item) => Number(item.id) === Number(trainingPlayer.id));
    const currentReport = row?.report || {};
    const trainedSkill = currentReport.type?.name || 'pace';
    const player = findPlayer(trainingPlayer.id) || { id: trainingPlayer.id, info: trainingPlayer.info };
    const reports = state.playerReports.get(trainingPlayer.id) || [];
    const mainPrediction = predictSkill(player, reports, trainedSkill, 'DT');
    const gtPredictions = predictorSkills
        .filter((skill) => skill !== trainedSkill)
        .map((skill) => predictSkill(player, reports, skill, 'GT'))
        .sort((a, b) => b.nextProbability - a.nextProbability);

    return `
        <article class="predictor-card">
            <div class="predictor-head">
                <div>
                    <button class="predictor-player-button" data-focus-player="${player.id}">
                        <h3>${escapeHtml(fullName(player))}</h3>
                    </button>
                    <div class="small-muted">Age ${age(player)} | ${escapeHtml(currentReport.formation?.name || trainingPlayer.formation?.name || '-')} | ${currentReport.intensity ?? trainingPlayer.intensity ?? '-'}%</div>
                </div>
                <div class="training-badge">DT ${escapeHtml(skillNames[trainedSkill] || trainedSkill)}</div>
            </div>
            ${predictionMain(mainPrediction, player.id)}
            <div class="gt-list">
                ${gtPredictions.map((prediction) => gtItem(prediction, player.id)).join('')}
            </div>
            <div class="skill-trace" id="trace-${player.id}"></div>
        </article>
    `;
}

function predictionMain(prediction, playerId) {
    return `
        <button class="prediction-main prediction-trigger" data-trace-player="${playerId}" data-trace-skill="${prediction.skill}">
            <div class="prediction-row">
                <span>${escapeHtml(skillNames[prediction.skill] || prediction.skill)} ${prediction.level} -> ${prediction.level + 1}</span>
                <strong>${prediction.nextProbability}%</strong>
            </div>
            <div class="probability-bar"><div class="probability-fill" style="--probability:${prediction.nextProbability}%"></div></div>
            <div class="prediction-row">
                <span>Credit ${prediction.accumulated.toFixed(2)} / ${prediction.target.toFixed(2)}</span>
                <span>fali ~${prediction.remainingTrainings} ${prediction.mode}</span>
            </div>
        </button>
    `;
}

function gtItem(prediction, playerId) {
    return `
        <button class="gt-item prediction-trigger" data-trace-player="${playerId}" data-trace-skill="${prediction.skill}">
            <span>GT ${escapeHtml(skillNames[prediction.skill] || prediction.skill)} ${prediction.level}->${prediction.level + 1}</span>
            <strong>${prediction.nextProbability}% | ~${prediction.remainingTrainings} GT</strong>
        </button>
    `;
}

function predictSkill(player, reports, skill, mode) {
    const level = Number(player.info?.skills?.[skill] ?? 0);
    const accumulated = accumulatedCredit(reports, skill);
    const intervals = completedIntervals(reports, skill);
    const target = targetCredit(skill, level, intervals);
    const nextCredit = mode === 'DT' ? 1 : 1 / gtRatio;
    const nextRatio = (accumulated + nextCredit) / target;
    const currentRatio = accumulated / target;
    const nextProbability = nextRatio >= 1
        ? Math.min(99, Math.max(92, Math.round(92 + Math.min(1, currentRatio) * 7)))
        : Math.min(91, Math.max(3, Math.round(nextRatio * 100)));
    const remaining = Math.max(0, target - accumulated);
    return {
        skill,
        mode,
        level,
        accumulated,
        target,
        source: intervals.length ? 'player-history' : 'global-fallback',
        nextProbability,
        remainingTrainings: Math.ceil(remaining / nextCredit)
    };
}

function accumulatedCredit(reports, skill) {
    const sorted = [...reports].sort((a, b) => reportWeek(a) - reportWeek(b));
    let seenFirstJump = false;
    let credit = 0;
    for (const report of sorted) {
        const change = report.skillsChange?.[skill] || 0;
        if (seenFirstJump) {
            credit += reportCredit(report, skill);
        }
        if (change > 0) {
            if (!seenFirstJump) {
                seenFirstJump = true;
            }
            credit = 0;
        }
    }
    return credit;
}

function completedIntervals(reports, skill) {
    const sorted = [...reports].sort((a, b) => reportWeek(a) - reportWeek(b));
    const intervals = [];
    let seenFirstJump = false;
    let credit = 0;
    let dtWeeks = 0;
    let gtWeeks = 0;
    let skipWeeks = 0;
    for (const report of sorted) {
        const change = report.skillsChange?.[skill] || 0;
        const before = (report.skills?.[skill] ?? 0) - change;
        const reportMode = reportTrainingMode(report, skill);
        if (seenFirstJump) {
            credit += reportMode.credit;
            if (reportMode.kind === 'DT') {
                dtWeeks += 1;
            } else if (reportMode.kind === 'GT') {
                gtWeeks += 1;
            } else {
                skipWeeks += 1;
            }
        }
        if (change > 0) {
            if (seenFirstJump && credit > 0) {
                intervals.push({
                    from: before,
                    to: before + change,
                    credits: credit,
                    dtWeeks,
                    gtWeeks,
                    skipWeeks,
                    season: report.day?.season,
                    seasonWeek: report.day?.seasonWeek
                });
            }
            seenFirstJump = true;
            credit = 0;
            dtWeeks = 0;
            gtWeeks = 0;
            skipWeeks = 0;
        }
    }
    return intervals;
}

function buildSkillTrace(reports, skill) {
    const sorted = [...reports].sort((a, b) => reportWeek(a) - reportWeek(b));
    const intervals = [];
    let seenFirstJump = false;
    let credit = 0;
    let dtWeeks = 0;
    let gtWeeks = 0;
    let skipWeeks = 0;
    let weeks = 0;
    let weeksList = [];
    let currentLevel = 0;

    for (const report of sorted) {
        const change = report.skillsChange?.[skill] || 0;
        const before = (report.skills?.[skill] ?? 0) - change;
        currentLevel = report.skills?.[skill] ?? before;
        const mode = reportTrainingMode(report, skill);
        if (seenFirstJump) {
            credit += mode.credit;
            weeks += 1;
            if (mode.kind === 'DT') {
                dtWeeks += 1;
            } else if (mode.kind === 'GT') {
                gtWeeks += 1;
            } else {
                skipWeeks += 1;
            }
            weeksList.push({
                season: report.day?.season,
                seasonWeek: report.day?.seasonWeek,
                type: report.type?.name || '-',
                formation: report.formation?.name || '-',
                intensity: report.intensity ?? 0,
                mode: mode.kind,
                credit: mode.credit,
                source: report.source || 'sokker'
            });
        }
        if (change > 0) {
            if (seenFirstJump && credit > 0) {
                intervals.push({
                    from: before,
                    to: before + change,
                    credits: credit,
                    dtWeeks,
                    gtWeeks,
                    skipWeeks,
                    season: report.day?.season,
                    seasonWeek: report.day?.seasonWeek
                });
            }
            seenFirstJump = true;
            credit = 0;
            dtWeeks = 0;
            gtWeeks = 0;
            skipWeeks = 0;
            weeks = 0;
            weeksList = [];
        }
    }

    return {
        intervals,
        current: {
            level: currentLevel,
            credit,
            dtWeeks,
            gtWeeks,
            skipWeeks,
            weeks,
            weeksList
        }
    };
}

function reportCredit(report, skill) {
    return reportTrainingMode(report, skill).credit;
}

function reportTrainingMode(report, skill) {
    const kind = report.kind?.name;
    const intensity = (report.intensity ?? 0) / 100;
    if (kind === 'missing' || intensity <= 0) {
        return { credit: 0, kind: 'skip' };
    }
    if (isDirectTraining(report, skill)) {
        return { credit: intensity, kind: 'DT' };
    }
    return { credit: intensity / gtRatio, kind: 'GT' };
}

function isDirectTraining(report, skill) {
    if (report.kind?.name === 'formation') {
        return false;
    }
    if (report.type?.name !== skill) {
        return false;
    }
    const formation = report.formation?.name;
    if (directFormation[skill]) {
        return formation === directFormation[skill];
    }
    return ['GK', 'DEF', 'MID', 'ATT'].includes(formation);
}

function targetCredit(skill, level, intervals) {
    if (intervals.length) {
        const recent = intervals[intervals.length - 1];
        const previous = intervals[intervals.length - 2];
        const base = previous ? recent.credits * 0.8 + previous.credits * 0.2 : recent.credits;
        const scale = clamp(globalLevelCredit(level) / globalLevelCredit(recent.from), 0.92, 1.22);
        return base * scale * highSkillDrag(skill, level);
    }
    return globalLevelCredit(level) * (skillFactor[skill] || 1) * highSkillDrag(skill, level);
}

function globalLevelCredit(level) {
    return levelCredits[level] || (levelCredits[16] + Math.max(0, level - 16) * 1.9);
}

function highSkillDrag(skill, level) {
    let drag = 1 + Math.max(0, level - 14) * 0.08;
    if (skill === 'pace' && level >= 15) {
        drag += 0.12 + Math.max(0, level - 16) * 0.08;
    }
    return drag;
}

function clamp(value, min, max) {
    return Math.min(max, Math.max(min, value));
}

function reportWeek(report) {
    if (report.day?.season && report.day?.seasonWeek) {
        return report.day.season * 100 + report.day.seasonWeek;
    }
    return report.week ?? report.day?.week ?? 0;
}

async function openPlayerDetail(playerId) {
    const player = findPlayer(playerId) || { id: playerId, info: {} };
    playerDetailView.innerHTML = `<div class="player-detail-shell"><div class="empty-state">Ucitavam istoriju treninga...</div></div>`;
    document.querySelectorAll('.view').forEach((element) => element.classList.add('hidden'));
    playerDetailView.classList.remove('hidden');
    document.querySelector('#page-title').textContent = fullName(player);

    try {
        const response = await getJson(api.playerTraining(playerId));
        const reports = response.reports || [];
        renderPlayerDetail(player, reports);
    } catch (error) {
        playerDetailView.innerHTML = `<div class="player-detail-shell"><div class="empty-state">${escapeHtml(error.message)}</div></div>`;
    }
}

function renderPlayerDetail(player, reports) {
    playerDetailView.innerHTML = `
        <div class="toolbar">
            <button id="back-from-detail" class="action-button secondary">Back</button>
            <h2>${escapeHtml(fullName(player))}</h2>
        </div>
        <div class="player-detail-shell">
            <div class="detail-layout">
                <section class="detail-card">
                    <h2>${escapeHtml(fullName(player))}</h2>
                    <p class="small-muted">Age ${age(player)} | ID ${player.id}</p>
                    <div class="skill-grid">${skillGrid(player.info?.skills || {})}</div>
                </section>
                <section class="detail-card">
                    <h3>Prethodni treninzi</h3>
                    <div class="table-scroll">
                        <table class="data-table">
                            <thead>
                            <tr>
                                <th>Sezona</th>
                                <th>Nedelja</th>
                                <th>Tip</th>
                                <th>Intenzitet</th>
                                <th>Izvor</th>
                                <th>Promene</th>
                            </tr>
                            </thead>
                            <tbody>
                            ${reports.length ? reports.map(historyRow).join('') : `<tr><td colspan="6" class="empty-state">Nema prethodnih treninga za igraca.</td></tr>`}
                            </tbody>
                        </table>
                    </div>
                </section>
            </div>
        </div>
    `;
    document.querySelector('#back-from-detail').addEventListener('click', () => showView(state.activeView));
}

function playerCard(player) {
    const skills = player.info?.skills || {};
    return `
        <button class="player-card" data-player-id="${player.id}">
            <div class="player-card-head">
                <h3>${escapeHtml(fullName(player))}</h3>
                <span>Age: ${age(player)}</span>
            </div>
            <div class="player-form-row">Form: <strong>${skills.form ?? '-'}</strong></div>
            <div class="player-skill-columns">
                <div>${playerSkillRows(skills, player.info?.skillsChange || {}, ['stamina', 'pace', 'technique', 'passing'])}</div>
                <div>${playerSkillRows(skills, player.info?.skillsChange || {}, ['keeper', 'defending', 'playmaking', 'striker'])}</div>
            </div>
            <div class="delta-strip">${deltaPills(player.info?.skillsChange || {})}</div>
        </button>
    `;
}

function playerSkillRows(skills, changes, keys) {
    return keys.map((key) => `
        <div class="player-skill-line">
            <span>${escapeHtml(skillNames[key] || key)}</span>
            <strong class="${skillChangeClass(changes[key])}">${skills[key] ?? '-'}</strong>
        </div>
    `).join('');
}

function skillChangeClass(value) {
    if (value > 0) {
        return 'positive';
    }
    if (value < 0) {
        return 'negative';
    }
    return '';
}

function trainingRow(row) {
    const report = row.report || {};
    return `
        <tr data-player-id="${row.id}">
            <td><strong>${escapeHtml(fullName(row.player))}</strong><div class="small-muted">ID ${row.id}</div></td>
            <td>${age(row.player)}</td>
            <td>${escapeHtml(report.type?.name || report.kind?.name || 'missing')}</td>
            <td>${report.intensity ?? '-'}%</td>
            <td><div class="delta-strip">${deltaPills(report.skillsChange || {})}</div></td>
        </tr>
    `;
}

function historyRow(report) {
    return `
        <tr>
            <td>${report.day?.season ?? '-'}</td>
            <td>${report.day?.seasonWeek ?? report.week ?? '-'}</td>
            <td>${escapeHtml(report.type?.name || report.kind?.name || '-')}</td>
            <td>${report.intensity ?? '-'}%</td>
            <td>${report.source === 'sktables' ? '<span class="training-badge source-badge">SkTables</span>' : '<span class="small-muted">Sokker</span>'}</td>
            <td><div class="delta-strip">${deltaPills(report.skillsChange || {})}</div></td>
        </tr>
    `;
}

function skillGrid(skills) {
    return skillLabels.map(([key, label]) => `
        <div class="skill-row">
            <span>${label}</span>
            <strong>${skills[key] ?? '-'}</strong>
        </div>
    `).join('');
}

function topSkills(skills) {
    return skillLabels
        .filter(([key]) => typeof skills[key] === 'number')
        .sort((a, b) => skills[b[0]] - skills[a[0]])
        .slice(0, 4)
        .map(([key, label]) => `<span class="skill-pill">${label} ${skills[key]}</span>`)
        .join('');
}

function deltaPills(changes) {
    const pills = skillLabels
        .filter(([key]) => typeof changes[key] === 'number' && changes[key] !== 0)
        .map(([key, label]) => {
            const value = changes[key];
            const sign = value > 0 ? '+' : '-';
            const klass = value > 0 ? 'positive' : 'negative';
            return `<span class="delta-pill ${klass}">${label} ${sign}${Math.abs(value)}</span>`;
        });
    return pills.length ? pills.join('') : '<span class="small-muted">Bez promene</span>';
}

function sumWeeklyChanges() {
    return state.trainingRows.reduce((totals, row) => {
        const change = row.report?.skillsChange || {};
        addStatChange(totals.form, change.form);
        addStatChange(totals.stamina, change.stamina);
        predictorSkills.forEach((key) => addStatChange(totals.skills, change[key]));
        return totals;
    }, {
        form: { up: 0, down: 0 },
        stamina: { up: 0, down: 0 },
        skills: { up: 0, down: 0 }
    });
}

function addStatChange(bucket, value) {
    if (value > 0) {
        bucket.up += value;
    } else if (value < 0) {
        bucket.down += Math.abs(value);
    }
}

function statPair(up, down) {
    return `<span class="form-good">${up}</span> / <span class="form-bad">${down}</span>`;
}

function findPlayer(playerId) {
    return state.players.find((player) => Number(player.id) === Number(playerId));
}

function fullName(player) {
    return player?.info?.name?.full || player?.name?.full || `Player ${player?.id ?? ''}`;
}

function age(player) {
    return player?.info?.characteristics?.age ?? player?.characteristics?.age ?? '-';
}

async function getJson(url) {
    const response = await fetch(url);
    return parseJsonResponse(response);
}

async function postJson(url, body) {
    const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    });
    return parseJsonResponse(response);
}

async function parseJsonResponse(response) {
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
        throw new Error(payload.error || `Request failed: ${response.status}`);
    }
    return payload;
}

function number(value) {
    return new Intl.NumberFormat('sr-RS', { maximumFractionDigits: 0 }).format(value);
}

function escapeHtml(value) {
    return String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#039;');
}
