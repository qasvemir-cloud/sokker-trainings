const api = {
    login: '/sokker/api/login',
    logout: '/sokker/api/logout',
    me: '/sokker/api/me',
    players: '/sokker/api/players',
    training: '/sokker/api/training/current',
    trainingPlayers: '/sokker/api/training/players',
    trainingFormations: '/sokker/api/training/formations',
    trainingSummary: '/sokker/api/training/summary',
    juniors: '/sokker/api/juniors',
    juniorGraph: (juniorId) => `/sokker/api/juniors/${juniorId}/graph`,
    teamTransfers: '/sokker/api/market/team-transfers',
    marketTransfers: '/sokker/api/market/transfers',
    matches: '/sokker/api/matches',
    alumni: '/sokker/api/alumni',
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
    ['striker', 'Str'],
    ['tacticalDiscipline', 'Tact'],
    ['experience', 'Exp'],
    ['teamwork', 'TW']
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
    juniors: null,
    juniorGraphs: new Map(),
    trainingSummary: null,
    market: null,
    matches: null,
    alumni: null,
    playerReports: new Map(),
    formationSkills: null,
    focusedPredictorPlayerId: null,
    activeView: 'players',
    playerSort: 'age',
    predictorSort: 'prob',
    predictorShowAll: false
};

const loginScreen = document.querySelector('#login-screen');
const appScreen = document.querySelector('#app-screen');
const loginForm = document.querySelector('#login-form');
const loginError = document.querySelector('#login-error');
const playersView = document.querySelector('#players-view');
const trainingView = document.querySelector('#training-view');
const lastTrainingView = document.querySelector('#last-training-view');
const plannerView = document.querySelector('#planner-view');
const predictorView = document.querySelector('#predictor-view');
const juniorsView = document.querySelector('#juniors-view');
const summaryView = document.querySelector('#summary-view');
const marketView = document.querySelector('#market-view');
const matchesView = document.querySelector('#matches-view');
const alumniView = document.querySelector('#alumni-view');
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

document.querySelector('.menu-items')?.addEventListener('click', () => {
    const toggle = document.getElementById('menu-toggle');
    if (toggle) toggle.checked = false;
});

async function bootstrap() {
    loginScreen.classList.add('hidden');
    appScreen.classList.remove('hidden');
    await loadData();
    renderShell();
    showView('matches-report');
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

async function showView(view) {
    state.activeView = view;
    document.querySelectorAll('.view').forEach((element) => element.classList.add('hidden'));
    document.querySelectorAll('[data-view]').forEach((element) => {
        element.classList.toggle('active', element.dataset.view === view);
    });

    if (view === 'training') {
        document.querySelector('#page-title').textContent = 'Training Reports';
        trainingView.classList.remove('hidden');
    } else if (view === 'last-training') {
        document.querySelector('#page-title').textContent = 'Last Training';
        lastTrainingView.classList.remove('hidden');
        renderLastTraining();
    } else if (view === 'planner') {
        document.querySelector('#page-title').textContent = 'Training Planner';
        plannerView.classList.remove('hidden');
        await renderPlanner();
    } else if (view === 'predictor') {
        document.querySelector('#page-title').textContent = 'Training Predictor';
        predictorView.classList.remove('hidden');
        renderPredictor();
    } else if (view === 'juniors') {
        document.querySelector('#page-title').textContent = 'Junior Academy';
        juniorsView.classList.remove('hidden');
        renderJuniors();
    } else if (view === 'summary') {
        document.querySelector('#page-title').textContent = 'Training Summary';
        summaryView.classList.remove('hidden');
        renderTrainingSummary();
    } else if (view === 'market') {
        document.querySelector('#page-title').textContent = 'Market';
        marketView.classList.remove('hidden');
        renderMarket();
    } else if (view === 'matches') {
        document.querySelector('#page-title').textContent = 'Matches';
        matchesView.classList.remove('hidden');
        renderMatches();
    } else if (view === 'alumni') {
        document.querySelector('#page-title').textContent = 'Alumni';
        alumniView.classList.remove('hidden');
        renderAlumni();
    } else if (view === 'matches-report') {
        document.querySelector('#page-title').textContent = 'Matches Report';
        document.querySelector('#matches-report-view').classList.remove('hidden');
        loadReportView('matches');
    } else if (view === 'events-report') {
        document.querySelector('#page-title').textContent = 'Events Report';
        document.querySelector('#events-report-view').classList.remove('hidden');
        loadReportView('events');
    } else {
        document.querySelector('#page-title').textContent = state.current?.team?.name || 'Sokker Club';
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
                <h2>${state.current?.team?.name || 'Sokker Club'}</h2>
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
                        <th title="Advanced training">AT</th>
                        <th>Player</th>
                        <th>Age</th>
                        <th class="mobile-hide">Type</th>
                        <th class="mobile-hide">Intensity</th>
                        <th>Weekly changes</th>
                    </tr>
                    </thead>
                    <tbody>
                    ${rows.length ? rows.map(trainingRow).join('') : `<tr><td colspan="6" class="empty-state">No training reports for this week.</td></tr>`}
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

function renderLastTraining() {
    const orderNames = { GK: 'GK', DEF: 'DEF', MID: 'MID', ATT: 'ATT' };
    const orderSkills = { GK: 'Pace', DEF: 'Defending', MID: 'Playmaking', ATT: 'Striker' };
    const skillOrder = ['stamina', 'keeper', 'pace', 'defending', 'technique', 'playmaking', 'passing', 'striker'];
    const skillHeaders = ['Sta', 'Gk', 'Pac', 'Def', 'Tec', 'Pla', 'Pas', 'Str'];
    const nonStaminaSkills = ['keeper', 'pace', 'defending', 'technique', 'playmaking', 'passing', 'striker'];
    let rows = state.trainingRows.map((row) => {
        const player = row.player ? { id: row.id, info: row.player } : findPlayer(row.id);
        const report = row.report || {};
        const skills = report.skills || player?.info?.skills || {};
        const changes = report.skillsChange || player?.info?.skillsChange || {};
        const hasChange = nonStaminaSkills.some(key => (changes[key] || 0) !== 0);
        return {
            id: row.id, player, report, skills, changes, hasChange,
            isAdv: report.kind?.name === 'individual',
            ord: report.formation?.name || player?.info?.formation?.name || '-',
            trPct: report.intensity ?? '-',
            pAge: report.age ?? age(player)
        };
    });
    rows.sort((a, b) => {
        if (a.hasChange !== b.hasChange) return a.hasChange ? -1 : 1;
        return Number(a.pAge) - Number(b.pAge);
    });
    const orderLegend = Object.entries(orderSkills).map(([ord, skill]) => `<span class="order-legend-item"><span class="training-badge">${ord}</span> → ${skill}</span>`).join('');
    lastTrainingView.innerHTML = `
        <div class="view-panel">
            <div class="toolbar">
                <h2>Last Training</h2>
                <button id="refresh-last-training" class="action-button">Refresh</button>
            </div>
            <div class="order-legend">${orderLegend}</div>
            <div class="table-scroll">
                <table class="data-table training-summary-table">
                    <thead>
                    <tr>
                        <th title="Advanced training">AT</th>
                        <th data-sort>Name</th>
                        <th data-sort>Age</th>
                        <th data-sort>Ord</th>
                        ${skillHeaders.map(h => `<th data-sort>${h}</th>`).join('')}
                        <th data-sort>Tr%</th>
                    </tr>
                    </thead>
                    <tbody>
                    ${rows.length ? rows.map(r => {
                        const ord = r.ord;
                        const trPct = r.trPct;
                        const pAge = r.pAge;
                        const skills = r.skills;
                        const changes = r.changes;
                        return `<tr data-player-id="${r.id}">
                            <td>${r.isAdv ? '<span class="planner-adv-marker" title="Advanced training">&#x2605;</span>' : ''}</td>
                            <td><strong>${escapeHtml(fullName(r.player))}</strong><div class="small-muted">ID ${r.id}</div></td>
                            <td>${pAge}</td>
                            <td><span class="training-badge">${escapeHtml(orderNames[ord] || ord)}</span></td>
                            ${skillOrder.map(key => {
                                const val = skills[key];
                                const change = changes[key];
                                if (val == null) return '<td class="small-muted">-</td>';
                                const klass = change > 0 ? 'positive' : change < 0 ? 'negative' : '';
                                return `<td class="${klass}">${val}${change ? (change > 0 ? `<span class="delta-up">+${change}</span>` : `<span class="delta-down">${change}</span>`) : ''}</td>`;
                            }).join('')}
                            <td>${trPct !== '-' ? `<span class="training-pct">${trPct}%</span>` : '-'}</td>
                        </tr>`;
                    }).join('') : '<tr><td colspan="12" class="empty-state">No training data available.</td></tr>'}
                    </tbody>
                </table>
            </div>
        </div>
    `;
    makeSortable(lastTrainingView.querySelector('.training-summary-table'));
    lastTrainingView.querySelector('#refresh-last-training')?.addEventListener('click', async () => {
        await loadData();
        renderShell();
        showView('last-training');
    });
    lastTrainingView.querySelectorAll('[data-player-id]').forEach((row) => {
        row.addEventListener('click', () => openPlayerDetail(Number(row.dataset.playerId)));
    });
}

async function renderTrainingSummary() {
    summaryView.innerHTML = loadingPanel('Training Summary', 'Loading weekly training summary...');
    try {
        if (!state.trainingSummary) {
            state.trainingSummary = await getJson(api.trainingSummary);
        }
        const weeks = state.trainingSummary.weeks || [];
        summaryView.innerHTML = `
            <div class="view-panel">
                <div class="toolbar">
                    <h2>Training Summary</h2>
                    <button id="refresh-summary" class="action-button">Refresh</button>
                </div>
                <div class="summary-grid">
                    ${weeks.slice(0, 12).map(summaryCard).join('')}
                </div>
            </div>
        `;
        summaryView.querySelector('#refresh-summary').addEventListener('click', () => {
            state.trainingSummary = null;
            renderTrainingSummary();
        });
    } catch (error) {
        summaryView.innerHTML = errorPanel('Training Summary', error.message);
    }
}

async function renderMarket() {
    marketView.innerHTML = loadingPanel('Market', 'Loading transfers and market list...');
    try {
        if (!state.market) {
            const [teamTransfers, marketTransfers] = await Promise.all([
                getJson(api.teamTransfers),
                getJson(api.marketTransfers).catch(() => ({ transfers: [] }))
            ]);
            state.market = { teamTransfers, marketTransfers };
        }
        const teamTransfers = state.market.teamTransfers?.transfers || [];
        const marketTransfers = state.market.marketTransfers?.transfers || [];
        marketView.innerHTML = `
            <div class="view-panel">
                <div class="toolbar">
                    <h2>Market</h2>
                    <button id="refresh-market" class="action-button">Refresh</button>
                </div>
                <div class="detail-layout">
                    <section class="detail-card">
                        <h3>Team transfers</h3>
                        <div class="table-scroll">
                            <table class="data-table">
                                <thead><tr><th>Player</th><th>Age</th><th>Price</th><th>Value</th><th>Date</th><th>Deal</th></tr></thead>
                                <tbody>${teamTransfers.slice(0, 20).map(teamTransferRow).join('') || `<tr><td colspan="6" class="empty-state">No transfers.</td></tr>`}</tbody>
                            </table>
                        </div>
                    </section>
                    <section class="detail-card">
                        <h3>Transfer list</h3>
                        <div class="table-scroll">
                            <table class="data-table">
                                <thead><tr><th>Player</th><th>Age</th><th>Price</th><th>Deadline</th><th>Team</th></tr></thead>
                                <tbody>${marketTransfers.slice(0, 20).map(marketTransferRow).join('') || `<tr><td colspan="5" class="empty-state">Market list not available.</td></tr>`}</tbody>
                            </table>
                        </div>
                    </section>
                </div>
            </div>
        `;
        marketView.querySelectorAll('.data-table').forEach(makeSortable);
        marketView.querySelector('#refresh-market').addEventListener('click', () => {
            state.market = null;
            renderMarket();
        });
    } catch (error) {
        marketView.innerHTML = errorPanel('Market', error.message);
    }
}

async function renderMatches() {
    matchesView.innerHTML = loadingPanel('Matches', 'Loading matches and training minutes...');
    try {
        if (!state.matches) {
            state.matches = await getJson(api.matches);
        }
        const matches = state.matches.matches || [];
        const minuteRows = state.trainingRows
            .map((row) => ({ row, player: row.player ? { id: row.id, info: row.player } : findPlayer(row.id) }))
            .sort((a, b) => (b.row.report?.intensity ?? 0) - (a.row.report?.intensity ?? 0));
        matchesView.innerHTML = `
            <div class="view-panel">
                <div class="toolbar">
                    <h2>Matches & Minutes</h2>
                    <button id="refresh-matches" class="action-button">Refresh</button>
                </div>
                <div class="detail-layout">
                    <section class="detail-card">
                        <h3>Training minutes audit</h3>
                        <div class="table-scroll">
                            <table class="data-table">
                                <thead><tr><th>Player</th><th>Intensity</th><th>Official</th><th>Friendly</th><th>National</th><th>Risk</th></tr></thead>
                                <tbody>${minuteRows.map(minutesRow).join('') || `<tr><td colspan="6" class="empty-state">No minutes data.</td></tr>`}</tbody>
                            </table>
                        </div>
                    </section>
                    <section class="detail-card">
                        <h3>Recent matches</h3>
                        <div class="table-scroll">
                            <table class="data-table">
                                <thead><tr><th>Week</th><th>Match</th><th>Type</th><th>Score</th></tr></thead>
                                <tbody>${matches.slice(0, 15).map(matchRow).join('') || `<tr><td colspan="4" class="empty-state">Nema meceva.</td></tr>`}</tbody>
                            </table>
                        </div>
                    </section>
                </div>
            </div>
        `;
        matchesView.querySelectorAll('.data-table').forEach(makeSortable);
        matchesView.querySelector('#refresh-matches').addEventListener('click', () => {
            state.matches = null;
            renderMatches();
        });
    } catch (error) {
        matchesView.innerHTML = errorPanel('Matches', error.message);
    }
}

async function renderAlumni() {
    alumniView.innerHTML = loadingPanel('Alumni', 'Loading former players...');
    try {
        if (!state.alumni) {
            state.alumni = await getJson(api.alumni);
        }
        const players = state.alumni.players || [];
        const totFirst = players.reduce((s, p) => s + (p.firstPrice?.value || 0), 0);
        const totTax = players.reduce((s, p) => s + (p.taxIncome?.value || 0), 0);
        const currency = players.find((p) => p.firstPrice?.currency)?.firstPrice?.currency || '';
        alumniView.innerHTML = `
            <div class="view-panel">
                <div class="toolbar">
                    <h2>Alumni</h2>
                    <button id="refresh-alumni" class="action-button">Refresh</button>
                </div>
                <div class="alumni-summary">
                    <div><strong>First price</strong><span class="value positive">${number(totFirst)} ${escapeHtml(currency)}</span></div>
                    <div><strong>Tax income</strong><span class="value positive">${number(totTax)} ${escapeHtml(currency)}</span></div>
                    <div></div>
                    <div class="alumni-total-line"><strong>Total</strong><span class="value positive">${number(totFirst + totTax)} ${escapeHtml(currency)}</span></div>
                    <div class="small-muted">(${players.length} players)</div>
                </div>
                <div class="table-scroll">
                    <table class="data-table">
                        <thead><tr><th>Player</th><th>Age</th><th>Current team</th><th>Sold</th><th>First price</th><th>Tax income</th><th>Total</th></tr></thead>
                        <tbody>${players.slice(0, 40).map(alumniRow).join('') || `<tr><td colspan="7" class="empty-state">No alumni players.</td></tr>`}</tbody>
                    </table>
                </div>
            </div>
        `;
        const alumniTable = alumniView.querySelector('.data-table');
        if (alumniTable) makeSortable(alumniTable);
        alumniView.querySelector('#refresh-alumni').addEventListener('click', () => {
            state.alumni = null;
            renderAlumni();
        });
    } catch (error) {
        alumniView.innerHTML = errorPanel('Alumni', error.message);
    }
}

async function renderPredictor() {
    predictorView.innerHTML = `
        <div class="view-panel">
            <div class="toolbar">
                <h2>Advanced Training Predictor</h2>
                <button id="refresh-predictor" class="action-button">Refresh</button>
            </div>
            <div class="empty-state">Loading advanced player history...</div>
        </div>
    `;
    bindPredictorRefresh();

    if (!state.trainingSetup) {
        state.trainingSetup = await getJson(api.trainingPlayers);
    }
    if (!state.formationSkills) {
        const formationsRes = await getJson(api.trainingFormations);
        const formations = formationsRes.formations || [];
        state.formationSkills = {};
        for (const f of formations) {
            state.formationSkills[f.formation.name] = f.type.name;
        }
    }
    let advanced = state.trainingSetup.advanced || [];
    let allPlayers = advanced.slice();
    const advIds = advancedIds();

    if (state.predictorShowAll) {
        const general = state.trainingSetup.general || [];
        for (const r of general) {
            if (!allPlayers.some(p => Number(p.id) === Number(r.id))) {
                allPlayers.push(r);
            }
        }
        const trainingRowsExtra = state.trainingRows.filter(r => !allPlayers.some(p => Number(p.id) === Number(r.id)));
        for (const r of trainingRowsExtra) {
            if (!allPlayers.some(p => Number(p.id) === Number(r.id))) {
                allPlayers.push({
                    id: r.id,
                    info: r.player,
                    formation: r.report?.formation || r.player?.formation,
                    intensity: r.report?.intensity
                });
            }
        }
    }

    await Promise.all(allPlayers.map((player) => loadPlayerReport(player.id).catch(() => {})));

    const sortField = state.predictorSort;
    allPlayers = allPlayers.slice().sort((a, b) => {
        if (sortField === 'prob') {
            const pa = findPlayer(a.id) || { id: a.id, info: a.info };
            const pb = findPlayer(b.id) || { id: b.id, info: b.info };
            const rowA = state.trainingRows.find((r) => Number(r.id) === Number(a.id));
            const rowB = state.trainingRows.find((r) => Number(r.id) === Number(b.id));
            const fa = playerFormation(a) || rowA?.report?.formation?.name;
            const fb = playerFormation(b) || rowB?.report?.formation?.name;
            const skillA = (state.formationSkills && fa && state.formationSkills[fa]) || rowA?.report?.type?.name || 'pace';
            const skillB = (state.formationSkills && fb && state.formationSkills[fb]) || rowB?.report?.type?.name || 'pace';
            const repA = state.playerReports.get(Number(a.id)) || [];
            const repB = state.playerReports.get(Number(b.id)) || [];
            const isAdvA = advIds.has(Number(a.id));
            const isAdvB = advIds.has(Number(b.id));
            const predA = predictSkill(pa, repA, skillA, 'DT', isAdvA);
            const predB = predictSkill(pb, repB, skillB, 'DT', isAdvB);
            return (predB.nextProbability || 0) - (predA.nextProbability || 0);
        }
        if (sortField === 'name') {
            return fullName(a).toLowerCase().localeCompare(fullName(b).toLowerCase());
        }
        const sa = a?.characteristics?.age ?? a?.info?.characteristics?.age ?? 0;
        const sb = b?.characteristics?.age ?? b?.info?.characteristics?.age ?? 0;
        return sa - sb;
    });

    const sortLabels = { prob: 'Probability', name: 'Name', age: 'Age' };
    const nextSort = { prob: 'name', name: 'age', age: 'prob' };
    const toggleLabel = state.predictorShowAll ? 'Advanced only' : 'Show all';
    predictorView.innerHTML = `
        <div class="view-panel">
            <div class="toolbar">
                <h2>${state.focusedPredictorPlayerId ? 'Player Predictor' : 'Training Predictor'}</h2>
                <div class="toolbar-actions">
                    ${state.focusedPredictorPlayerId ? '<button id="predictor-back" class="action-button secondary">Back</button>' : ''}
                    ${!state.focusedPredictorPlayerId ? '<button id="predictor-toggle-all" class="action-button secondary">' + toggleLabel + '</button>' : ''}
                    <button id="predictor-sort" class="action-button secondary">Sort: ${sortLabels[state.predictorSort] || 'Name'}</button>
                    <button id="refresh-predictor" class="action-button">Refresh</button>
                </div>
            </div>
            ${state.focusedPredictorPlayerId ? renderFocusedPredictor(allPlayers) : renderPredictorList(allPlayers)}
            ${!state.focusedPredictorPlayerId && !state.predictorShowAll ? '<div class="small-muted" style="margin-top:8px;text-align:center">Showing players on Advanced training. <a href="#" id="predictor-show-all-link">Show all players</a></div>' : ''}
        </div>
    `;
    bindPredictorRefresh();
    bindPredictorSort();
    bindPredictorFocus();
    bindPredictorToggle();
    bindTraceButtons();

    if (state.focusedPredictorPlayerId) {
        const focusedPlayer = allPlayers.find((p) => Number(p.id) === Number(state.focusedPredictorPlayerId));
        if (focusedPlayer) {
            const formationName = setupPlayerFormation(focusedPlayer.id) || playerFormation(focusedPlayer);
            const dtSkill = (state.formationSkills && formationName && state.formationSkills[formationName]) || 'pace';
            setTimeout(() => renderSkillTrace(focusedPlayer.id, dtSkill, false), 50);
        }
    }
}

function renderPredictorList(advanced) {
    return `
        <div class="predictor-list">
            ${advanced.length ? advanced.map(predictorListItem).join('') : `<div class="empty-state">No players on advanced training.</div>`}
        </div>
    `;
}

function renderFocusedPredictor(players) {
    const player = players.find((item) => Number(item.id) === Number(state.focusedPredictorPlayerId));
    return player ? `<div class="predictor-single">${predictorCard(player)}</div>` : `<div class="empty-state">Player not found.</div>`;
}

async function renderJuniors() {
    juniorsView.innerHTML = loadingPanel('Junior Academy', 'Loading juniors and talent assessments...');
    try {
        if (!state.juniors) {
            state.juniors = await getJson(api.juniors);
        }
        const juniors = state.juniors.sokker?.juniors || [];
        await Promise.all(juniors.map((junior) => loadJuniorGraph(junior.id).catch(() => {})));
        const reportById = new Map((state.juniors.report?.juniors || []).map((junior) => [Number(junior.id), junior]));
        const sktablesById = new Map((state.juniors.sktables?.juniors || []).map((junior) => [Number(junior.id), junior]));
        const rows = juniors
            .map((junior) => ({ ...junior, report: reportById.get(Number(junior.id)), sktables: sktablesById.get(Number(junior.id)) }))
            .sort((a, b) => (a.weeksLeft ?? 99) - (b.weeksLeft ?? 99));

        const isMobile = window.innerWidth < 768;
        const content = isMobile && rows.length
            ? `<div class="juniors-cards">${rows.map(juniorCard).join('')}</div>`
            : `<div class="table-scroll"><table class="data-table"><thead><tr><th>Junior</th><th>Age</th><th>Lvl</th><th>Talent</th><th>Weeks</th><th>Projection</th><th>Potential</th><th>Graph</th></tr></thead><tbody>${rows.length ? rows.map(juniorRow).join('') : `<tr><td colspan="8" class="empty-state">No juniors.</td></tr>`}</tbody></table></div>`;

        juniorsView.innerHTML = `
            <div class="view-panel">
                <div class="toolbar">
                    <h2>Junior Academy</h2>
                    <button id="refresh-juniors" class="action-button">Refresh</button>
                </div>
                ${content}
            </div>
        `;
        const juniorsTable = juniorsView.querySelector('.data-table');
        if (juniorsTable) makeSortable(juniorsTable);
        juniorsView.querySelector('#refresh-juniors').addEventListener('click', () => {
            state.juniors = null;
            state.juniorGraphs.clear();
            renderJuniors();
        });
    } catch (error) {
        juniorsView.innerHTML = errorPanel('Junior Academy', error.message);
    }
}

async function loadJuniorGraph(juniorId) {
    if (state.juniorGraphs.has(juniorId)) {
        return state.juniorGraphs.get(juniorId);
    }
    const graph = await getJson(api.juniorGraph(juniorId)).catch(() => ({ values: [] }));
    state.juniorGraphs.set(juniorId, graph);
    return graph;
}

function juniorRow(junior) {
    const graph = state.juniorGraphs.get(junior.id)?.values || [];
    const sktables = junior.sktables || {};
    const change = junior.report?.change ?? sktables.change ?? 0;
    const tVal = sktables.talent;
    const talentHtml = tVal
        ? `<strong>${Number(tVal).toFixed(1)}${sktables.talentUncertain ? '?' : ''}</strong>`
        : '<span class="small-muted">-</span>';
    return `
        <tr>
            <td><strong>${escapeHtml(junior.fullName?.full || junior.name)}</strong><div class="small-muted">ID ${junior.id}</div></td>
            <td>${junior.age}</td>
            <td><strong class="${skillChangeClass(change)}">${junior.skill}</strong> ${change ? deltaInline(change) : ''}</td>
            <td>${talentHtml}</td>
            <td>${junior.weeksLeft ?? sktables.weeksLeft ?? '-'}</td>
            <td>${sktables.finalLevel ? `${Number(sktables.ageOut).toFixed(1)}y | lvl ${sktables.finalLevel}` : '<span class="small-muted">Sokker only</span>'}</td>
            <td>${sktables.potential ? potentialTag(sktables.potential) : '<span class="small-muted">-</span>'}</td>
            <td>${miniGraph(graph)}</td>
        </tr>
    `;
}

function juniorCard(junior) {
    const graph = state.juniorGraphs.get(junior.id)?.values || [];
    const sktables = junior.sktables || {};
    const change = junior.report?.change ?? sktables.change ?? 0;
    const tVal = sktables.talent;
    const talentHtml = tVal ? `${Number(tVal).toFixed(1)}${sktables.talentUncertain ? '?' : ''}` : '-';
    return `
        <div class="junior-card">
            <div>
                <span class="junior-name">${escapeHtml(junior.fullName?.full || junior.name)}</span>
                <span class="minor-info">ID ${junior.id}</span>
            </div>
            <div>
                <span>Age:</span>
                <strong>${junior.age}</strong>
            </div>
            <div>
                <span>Level:</span>
                <strong class="${skillChangeClass(change)}">${junior.skill}</strong> ${change ? deltaInline(change) : ''}
            </div>
            <div>
                <span>Talent:</span>
                <strong>${talentHtml}</strong>
            </div>
            <div>
                <span>Weeks left:</span>
                <strong>${junior.weeksLeft ?? sktables.weeksLeft ?? '-'}</strong>
            </div>
            <div>
                <span>Projection:</span>
                <strong>${sktables.finalLevel ? `${Number(sktables.ageOut).toFixed(1)}y | lvl ${sktables.finalLevel}` : 'Sokker only'}</strong>
            </div>
            <div>
                <span>Potential:</span>
                ${sktables.potential ? potentialTag(sktables.potential) : '<span class="small-muted">-</span>'}
            </div>
            <div>
                <span>Graph:</span>
                ${miniGraph(graph)}
            </div>
        </div>
    `;
}

function summaryCard(week) {
    return `
        <article class="summary-card">
            <div>
                <strong>S${week.gameDay?.season ?? '-'}/${week.gameDay?.seasonWeek ?? '-'}</strong>
                <span class="small-muted">${week.gameDay?.date?.value ?? ''}</span>
            </div>
            <div class="summary-metrics">
                <span>Advanced <strong>${week.stats?.advanced ?? 0}</strong></span>
                <span>General <strong>${week.stats?.general ?? 0}</strong></span>
                <span>Skill ups <strong class="positive">${week.stats?.skillsUp ?? 0}</strong></span>
                <span>Junior ups <strong class="positive">${week.juniors?.skillsUp ?? 0}</strong></span>
            </div>
        </article>
    `;
}

function teamTransferRow(transfer) {
    const isBuy = Number(transfer.buyer?.id) === Number(state.current?.team?.id);
    return `
        <tr>
            <td><strong>${escapeHtml(transfer.playerName?.full || transfer.player?.name?.full || '-')}</strong></td>
            <td>${transfer.age ?? '-'}</td>
            <td>${money(transfer.price)}</td>
            <td>${money(transfer.value)}</td>
            <td>${transfer.date?.value ?? '-'}</td>
            <td><span class="training-badge ${isBuy ? '' : 'source-badge'}">${isBuy ? 'Buy' : 'Sell'}</span></td>
        </tr>
    `;
}

function marketTransferRow(transfer) {
    return `
        <tr>
            <td><strong>${escapeHtml(transfer.playerName?.full || transfer.name?.full || transfer.info?.name?.full || '-')}</strong></td>
            <td>${transfer.age ?? transfer.info?.characteristics?.age ?? '-'}</td>
            <td>${money(transfer.price || transfer.bid || transfer.currentBid)}</td>
            <td>${transfer.deadline?.value || transfer.dateEnd?.value || transfer.end?.value || '-'}</td>
            <td>${escapeHtml(transfer.seller?.name || transfer.team?.name || '-')}</td>
        </tr>
    `;
}

function minutesRow(item) {
    const report = item.row.report || {};
    const games = report.games || {};
    const intensity = report.intensity ?? 0;
    const risk = intensity <= 0 ? 'No training' : intensity < 85 ? 'Low' : 'OK';
    return `
        <tr>
            <td><strong>${escapeHtml(fullName(item.player))}</strong></td>
            <td><strong class="${intensity < 85 ? 'negative' : 'positive'}">${intensity}%</strong></td>
            <td>${games.minutesOfficial ?? 0}</td>
            <td>${games.minutesFriendly ?? 0}</td>
            <td>${games.minutesNational ?? 0}</td>
            <td>${riskTag(risk)}</td>
        </tr>
    `;
}

function matchRow(match) {
    return `
        <tr>
            <td>S${match.day?.season ?? '-'}/${match.day?.seasonWeek ?? '-'}</td>
            <td><strong>${escapeHtml(match.home?.name || '-')}</strong> - <strong>${escapeHtml(match.away?.name || '-')}</strong></td>
            <td>${escapeHtml(match.league?.type?.name || match.league?.name || '-')}</td>
            <td>${match.score ? `${match.score.home ?? '-'}:${match.score.away ?? '-'}` : '-'}</td>
        </tr>
    `;
}

function alumniRow(player) {
    const fp = player.firstPrice?.value || 0;
    const ti = player.taxIncome?.value || 0;
    const cur = player.firstPrice?.currency || '';
    return `
        <tr>
            <td><strong>${escapeHtml(player.name?.full || '-')}</strong></td>
            <td>${player.age ?? '-'}</td>
            <td>${escapeHtml(player.team?.name || '-')}</td>
            <td>${player.sellDate?.value ?? '-'}</td>
            <td>${money(player.firstPrice)}</td>
            <td>${money(player.taxIncome)}</td>
            <td>${number(fp + ti)} ${escapeHtml(cur)}</td>
        </tr>
    `;
}

function predictorListItem(trainingPlayer) {
    const advIdSet = advancedIds();
    const tid = Number(trainingPlayer.id);
    const isAdv = advIdSet.has(tid);
    const formationName = setupPlayerFormation(tid) || playerFormation(trainingPlayer);
    const trainedSkill = (state.formationSkills && formationName && state.formationSkills[formationName]) || 'pace';
    const setupPlayer = (state.trainingSetup?.advanced || []).find(p => Number(p.id) === tid)
        || (state.trainingSetup?.general || []).find(p => Number(p.id) === tid);
    const intensity = setupPlayer?.intensity;
    const player = findPlayer(trainingPlayer.id) || { id: tid, info: trainingPlayer.info };
    const reports = state.playerReports.get(tid) || [];
    const mainPrediction = predictSkill(player, reports, trainedSkill, 'DT', isAdv);

    const probLabel = mainPrediction.maxed ? 'MAX' : `${mainPrediction.nextProbability}%`;
    const advLabel = isAdv ? '<span class="training-badge advanced-badge">★</span>' : '<span class="training-badge formation-badge">FT</span>';
    return `
        <button class="predictor-list-item ${isAdv ? '' : 'predictor-nonadv'}" data-focus-player="${player.id}">
            <span>
                <strong>${escapeHtml(fullName(player))}</strong>
                <small>${advLabel} Age ${age(player)} | ${escapeHtml(formationName || '-')} | ${intensity ?? '-'}%</small>
            </span>
            <span class="training-badge">DT ${escapeHtml(skillNames[trainedSkill] || trainedSkill)}</span>
            <span class="predictor-list-prob">${probLabel}</span>
        </button>
    `;
}

function bindPredictorRefresh() {
    predictorView.querySelector('#refresh-predictor')?.addEventListener('click', async () => {
        state.trainingSetup = null;
        state.formationSkills = null;
        state.playerReports.clear();
        await loadData();
        renderShell();
        renderPredictor();
    });
}

function bindPredictorSort() {
    predictorView.querySelector('#predictor-sort')?.addEventListener('click', () => {
        const nextSort = { prob: 'name', name: 'age', age: 'prob' };
        state.predictorSort = nextSort[state.predictorSort] || 'prob';
        renderPredictor();
    });
}

function bindPredictorToggle() {
    const toggle = (showAll) => {
        state.predictorShowAll = showAll;
        renderPredictor();
    };
    predictorView.querySelector('#predictor-toggle-all')?.addEventListener('click', () => {
        toggle(!state.predictorShowAll);
    });
    predictorView.querySelector('#predictor-show-all-link')?.addEventListener('click', (e) => {
        e.preventDefault();
        toggle(true);
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
            renderSkillTrace(playerId, skill, true);
        });
    });
}

function renderSkillTrace(playerId, skill, shouldScroll = false) {
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
            <div class="small-muted">Previous jumps</div>
            ${trace.intervals.length ? trace.intervals.map(traceInterval).join('') : trace.jumps.length ? trace.jumps.map(traceJump).join('') : '<div class="small-muted">No complete intervals; using global fallback.</div>'}
        </div>
        <div class="trace-section">
            <div class="small-muted">Current since last jump</div>
            <div class="trace-line">
                <span>Credit ${trace.current.credit.toFixed(2)} | DT ${trace.current.dtWeeks} | GT ${trace.current.gtWeeks} | skip ${trace.current.skipWeeks}</span>
                <strong>${trace.current.weeks} w.</strong>
            </div>
            ${trace.current.weeksList.map(traceWeek).join('')}
        </div>
    `;
    if (shouldScroll) {
        target.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
}

function traceInterval(interval) {
    return `
        <div class="trace-line">
            <span>${interval.from} -> ${interval.to}: ${interval.credits.toFixed(2)} credit = ${interval.dtWeeks} DT + ${interval.gtWeeks} GT + ${interval.skipWeeks} skip</span>
            <strong>S${interval.season}/${interval.seasonWeek}</strong>
        </div>
    `;
}

function traceJump(jump) {
    return `
        <div class="trace-line">
            <span>${jump.from} -> ${jump.to}</span>
            <strong>S${jump.season}/${jump.seasonWeek}</strong>
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
    const tid = Number(trainingPlayer.id);
    const advIdSet = advancedIds();
    const isAdv = advIdSet.has(tid);
    const formationName = setupPlayerFormation(tid) || playerFormation(trainingPlayer);
    const defaultDtSkill = (state.formationSkills && formationName && state.formationSkills[formationName]) || 'pace';
    const setupPlayer = (state.trainingSetup?.advanced || []).find(p => Number(p.id) === tid)
        || (state.trainingSetup?.general || []).find(p => Number(p.id) === tid);
    const intensity = setupPlayer?.intensity;
    const player = findPlayer(trainingPlayer.id) || { id: tid, info: trainingPlayer.info };
    const reports = state.playerReports.get(trainingPlayer.id) || [];

    const skillsHtml = predictorSkills.map((skill) => {
        const dtPred = predictSkill(player, reports, skill, 'DT', isAdv);
        const gtPred = predictSkill(player, reports, skill, 'GT', isAdv);
        const isDefault = skill === defaultDtSkill;
        return `
                <div class="predictor-skill-row ${isDefault ? 'dt-active' : ''}" role="button" tabindex="0" data-trace-player="${player.id}" data-trace-skill="${skill}" title="View training history for ${skill}">
                    <div class="predictor-skill-head">
                        <span class="predictor-skill-name">${escapeHtml(skillNames[skill] || skill)} ${dtPred.level} -> ${dtPred.level + 1}</span>
                        <div>
                            ${isDefault ? '<span class="training-badge small">DT</span>' : ''}
                            <span class="trace-hint">&#x1F550; History</span>
                        </div>
                    </div>
                <div class="predictor-modes">
                    <div class="predictor-mode">
                        <span class="mode-label">DT</span>
                        ${dtPred.maxed ? '<strong>MAX</strong>' : `
                            <span class="mode-prob">${dtPred.nextProbability}%</span>
                            ${dtPred.decreaseRisk ? `<span class="mode-dec-badge" title="Risk of skill decrease due to age">&#x26A0; ${Math.round(dtPred.decreaseRisk * 100)}% dec</span>` : ''}
                            <div class="probability-bar mini"><div class="probability-fill" style="--probability:${dtPred.nextProbability}%"></div></div>
                            <span class="mode-detail">${dtPred.accumulated.toFixed(2)}/${dtPred.target.toFixed(2)} ~${dtPred.remainingTrainings} DT</span>
                        `}
                    </div>
                    <div class="predictor-mode">
                        <span class="mode-label">GT</span>
                        ${gtPred.maxed ? '<strong>MAX</strong>' : `
                            <span class="mode-prob">${gtPred.nextProbability}%</span>
                            ${gtPred.decreaseRisk ? `<span class="mode-dec-badge" title="Risk of skill decrease due to age">&#x26A0; ${Math.round(gtPred.decreaseRisk * 100)}% dec</span>` : ''}
                            <div class="probability-bar mini"><div class="probability-fill" style="--probability:${gtPred.nextProbability}%"></div></div>
                            <span class="mode-detail">${gtPred.accumulated.toFixed(2)}/${gtPred.target.toFixed(2)} ~${gtPred.remainingTrainings} GT</span>
                        `}
                    </div>
                </div>
            </div>
        `;
    }).join('');

    return `
        <article class="predictor-card">
            <div class="predictor-head">
                <div>
                    <button class="predictor-player-button" data-focus-player="${player.id}">
                        <h3>${escapeHtml(fullName(player))}</h3>
                    </button>
                    <div class="small-muted">Age ${age(player)} | ${escapeHtml(formationName || '-')} | ${intensity ?? '-'}%</div>
                </div>
                <div class="training-badge">DT ${escapeHtml(skillNames[defaultDtSkill] || defaultDtSkill)}</div>
            </div>
            <div class="predictor-skills">
                ${skillsHtml}
            </div>
            <div class="skill-trace" id="trace-${player.id}"></div>
        </article>
    `;
}

function predictSkill(player, reports, skill, mode, isAdv) {
    const level = Number(player.info?.skills?.[skill] ?? 0);
    if (level >= 18) {
        return {
            skill, mode, level,
            accumulated: 0, target: 0,
            source: 'max-level',
            nextProbability: 0,
            remainingTrainings: Infinity,
            maxed: true,
            hasHistory: true
        };
    }
    const accumulated = accumulatedCredit(reports, skill);
    const intervals = completedIntervals(reports, skill);
    const age = player.info?.characteristics?.age;
    const target = targetCredit(skill, level, intervals, age);
    const nextCredit = (mode === 'DT' ? 1 : 1 / gtRatio) / (isAdv ? 1 : 3.5);
    const currentRatio = accumulated / target;
    const hasHistory = intervals.length > 0;
    const decreaseRisk = age >= 29 ? Math.min(0.45, (age - 28) * 0.07) : 0;
    const nextRatio = (accumulated + nextCredit) / target;
    const nextProbability = currentRatio >= 0.65
        ? currentRatio >= 2.00
            ? 95
            : Math.round(78 + (currentRatio - 0.65) / 1.35 * 17)
        : nextRatio >= 0.85
            ? Math.min(77, Math.max(55, Math.round(55 + (nextRatio - 0.65) * 30)))
            : Math.min(54, Math.max(3, Math.round(nextRatio / 0.85 * 54)));
    const adjProbability = decreaseRisk > 0
        ? Math.round(nextProbability * (1 - decreaseRisk))
        : nextProbability;
    const remaining = Math.max(0, target - accumulated);
    return {
        skill,
        mode,
        level,
        accumulated,
        target,
        source: hasHistory ? 'player-history' : 'global-fallback',
        hasHistory,
        nextProbability: adjProbability,
        decreaseRisk,
        remainingTrainings: Math.ceil(remaining / nextCredit)
    };
}

function accumulatedCredit(reports, skill) {
    const sorted = [...reports].sort((a, b) => reportWeek(a) - reportWeek(b));
    let seenFirstJump = false;
    let credit = 0;
    let result = 0;
    for (const report of sorted) {
        const isAtReport = report.kind?.name === 'individual';
        const perWeek = reportCredit(report, skill);
        const change = report.skillsChange?.[skill] || 0;
        if (seenFirstJump || !isAtReport) {
            credit += perWeek;
        }
        if (change > 0) {
            if (!seenFirstJump) {
                seenFirstJump = true;
            }
            result = credit;
            credit = 0;
        } else {
            result = credit;
        }
    }
    return result;
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
                    seasonWeek: report.day?.seasonWeek,
                    age: report.age
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
    const jumps = [];

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
            jumps.push({ from: before, to: before + change, season: report.day?.season, seasonWeek: report.day?.seasonWeek });
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
        jumps,
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

function targetCredit(skill, level, intervals, age) {
    if (intervals.length) {
        const recent = intervals[intervals.length - 1];
        const previous = intervals[intervals.length - 2];
        const base = previous ? recent.credits * 0.8 + previous.credits * 0.2 : recent.credits;
        const scale = clamp(globalLevelCredit(level) / globalLevelCredit(recent.from), 0.92, 1.22);
        const ageScale = age != null && recent.age != null
            ? Math.max(1, ageFactor(age) / ageFactor(recent.age))
            : 1;
        return base * scale * highSkillDrag(skill, level) * ageScale;
    }
    const af = age != null ? ageFactor(age) : 1;
    return globalLevelCredit(level) * (skillFactor[skill] || 1) * highSkillDrag(skill, level) * af;
}

function globalLevelCredit(level) {
    return levelCredits[level] || (levelCredits[16] + Math.max(0, level - 16) * 1.9);
}

function highSkillDrag(skill, level) {
    let drag = 1 + Math.max(0, level - 13) * 0.10;
    if (skill === 'pace') {
        if (level >= 15) drag += Math.max(0, level - 14) * 0.05;
        if (level >= 17) drag += Math.max(0, level - 16) * 0.08;
    }
    return drag;
}

function ageFactor(age) {
    if (age <= 17) return 0.70;
    if (age === 18) return 0.82;
    if (age === 19) return 0.92;
    if (age <= 25) return 1.0;
    if (age === 26) return 1.03;
    if (age === 27) return 1.07;
    if (age === 28) return 1.14;
    if (age <= 30) return 1.22;
    return 1.35;
}

function clamp(value, min, max) {
    return Math.min(max, Math.max(min, value));
}

function advancedIds() {
    return new Set((state.trainingSetup?.advanced || []).map(p => Number(p.id)));
}

function isAdvanced(playerId) {
    return advancedIds().has(Number(playerId));
}

function setupPlayerFormation(playerId) {
    if (!state.trainingSetup) return undefined;
    const all = [...(state.trainingSetup.advanced || []), ...(state.trainingSetup.general || [])];
    const match = all.find(r => Number(r.id) === Number(playerId));
    return match?.formation?.name;
}

function lookupFormation(data) {
    if (!data) return undefined;
    return data?.report?.formation?.name
        || data?.formation?.name
        || data?.player?.formation?.name
        || data?.player?.info?.formation?.name;
}

function playerFormation(data) {
    if (!data) return undefined;
    const id = data?.id || data?.player?.id;
    if (id && state.trainingSetup) {
        const setupRows = [...(state.trainingSetup.advanced || []), ...(state.trainingSetup.general || [])];
        const match = setupRows.find(r => Number(r.id) === Number(id));
        const fromSetup = lookupFormation(match);
        if (fromSetup) return fromSetup;
    }
    return lookupFormation(data);
}

function reportWeek(report) {
    if (report.day?.season && report.day?.seasonWeek) {
        return report.day.season * 100 + report.day.seasonWeek;
    }
    return report.week ?? report.day?.week ?? 0;
}

function formatDate(dateStr) {
    if (!dateStr) return '-';
    const parts = dateStr.split(/[-\s]/);
    if (parts.length >= 3) {
        return `${parts[2].padStart(2, '0')}-${parts[1].padStart(2, '0')}-${parts[0]}`;
    }
    return dateStr;
}

function playerTrainingStats(reports, player) {
    const skillKeys = ['stamina', 'pace', 'technique', 'passing', 'keeper', 'defending', 'playmaking', 'striker'];
    const perSkill = {};
    for (const key of skillKeys) {
        perSkill[key] = { dtWeeks: 0, gtWeeks: 0, injuryWeeks: 0, jumps: 0, totalCredit: 0 };
    }
    let firstReport = null;
    for (const report of reports.slice().reverse()) {
        const dtSkill = report.type?.name || '';
        const changes = report.skillsChange || {};
        const intensity = report.intensity ?? 100;
        if (intensity <= 0) {
            const injuredKey = skillKeys.find(k => k === dtSkill);
            if (injuredKey) perSkill[injuredKey].injuryWeeks++;
        } else {
            for (const key of skillKeys) {
                if (key === dtSkill) {
                    perSkill[key].dtWeeks++;
                    perSkill[key].totalCredit += 1;
                } else {
                    perSkill[key].gtWeeks++;
                    perSkill[key].totalCredit += 1 / gtRatio;
                }
                if ((changes[key] || 0) > 0) {
                    perSkill[key].jumps++;
                }
            }
        }
        if (!firstReport) firstReport = report;
    }

    // Age at first training = current age - seasons elapsed
    let ageAtFirst = null;
    if (firstReport && reports.length > 0) {
        const currentAge = Math.floor(player.info?.characteristics?.age || 0);
        const last = reports[0];
        const currentSeason = last.day?.season || 0;
        const firstSeason = firstReport.day?.season || 0;
        ageAtFirst = currentAge - (currentSeason - firstSeason);
    }
    return { perSkill, firstReport, ageAtFirst };
}

function renderTrainingStats(stats, player) {
    if (!stats.firstReport) return '';
    const r = stats.firstReport;
    const dateStr = formatDate(r.day?.date?.value || r.date?.value);
    const ageStr = stats.ageAtFirst != null ? stats.ageAtFirst : '?';
    const firstSkills = r.skills || {};
    const firstSkillsHtml = skillGrid(firstSkills);
    const skillRows = Object.entries(stats.perSkill).map(([key, s]) => `
        <div class="stat-line">
            <span class="stat-label">${escapeHtml(skillNames[key] || key)}</span>
            <span class="stat-digits">
                <span class="stat-dt" title="DT trainings">${s.dtWeeks}</span>
                <span class="stat-gt" title="GT trainings">${s.gtWeeks}</span>
                <span class="stat-injury" title="Injury (intensity 0)">${s.injuryWeeks}</span>
                <strong class="stat-jumps" title="Skill jumps">${s.jumps}</strong>
                <span class="stat-avg" title="Avg credit per jump">${s.jumps > 0 ? (s.totalCredit / s.jumps).toFixed(2) : '-'}</span>
            </span>
        </div>
    `).join('');
    const totalDt = Object.values(stats.perSkill).reduce((sum, s) => sum + s.dtWeeks, 0);
    const totalInjury = Object.values(stats.perSkill).reduce((sum, s) => sum + s.injuryWeeks, 0);
    const totalJumps = Object.values(stats.perSkill).reduce((sum, s) => sum + s.jumps, 0);
    return `
        <div class="training-stats-section">
            <div class="small-muted">First week: S${r.day?.season || '?'}/${r.day?.seasonWeek || '?'} ${dateStr} | Age ${ageStr}</div>
            <div class="skill-grid first-week-grid">${firstSkillsHtml}</div>
            <div class="stat-grid">
                <div class="stat-header">
                    <span class="stat-label">Skill</span>
                    <span class="stat-digits">
                        <span class="stat-dt">DT</span>
                        <span class="stat-gt">GT</span>
                        <span class="stat-injury">Inj</span>
                        <strong class="stat-jumps" title="Skill jumps">Jmp</strong>
                        <span class="stat-avg" title="Avg credit per jump">Avg</span>
                    </span>
                </div>
                ${skillRows}
                <div class="stat-line stat-total">
                    <span class="stat-label">Total</span>
                    <span class="stat-digits">
                        <span class="stat-dt">${totalDt}</span>
                        <span class="stat-gt"></span>
                        <span class="stat-injury">${totalInjury}</span>
                        <strong class="stat-jumps">${totalJumps}</strong>
                        <span class="stat-avg"></span>
                    </span>
                </div>
            </div>
        </div>
    `;
}

async function openPlayerDetail(playerId) {
    const player = findPlayer(playerId) || { id: playerId, info: {} };
    playerDetailView.innerHTML = `<div class="player-detail-shell"><div class="empty-state">Loading training history...</div></div>`;
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
                    ${renderTrainingStats(playerTrainingStats(reports, player), player)}
                </section>
                <section class="detail-card">
                    <h3>Training History</h3>
                    <div class="table-scroll">
                        <table class="data-table training-history">
                            <thead>
                            <tr>
                                <th data-sort>Season</th>
                                <th data-sort>Week</th>
                                <th data-sort>Date</th>
                                <th data-sort>Type</th>
                                <th data-sort class="mobile-hide">Intensity</th>
                                <th data-sort class="mobile-hide">Source</th>
                                <th>Changes</th>
                            </tr>
                            </thead>
                            <tbody id="training-history-body">
                            ${reports.length ? reports.map((report, idx) => historyRow(report, idx)).join('') : `<tr><td colspan="7" class="empty-state">No previous training for this player.</td></tr>`}
                            </tbody>
                        </table>
                    </div>
                </section>
            </div>
        </div>
    `;
    const historyTable = document.querySelector('.training-history');
    if (historyTable) makeSortable(historyTable);
    document.querySelector('#back-from-detail').addEventListener('click', () => showView(state.activeView));
    document.querySelectorAll('#training-history-body tr.clickable-row').forEach((row) => {
        row.addEventListener('click', () => {
            const idx = Number(row.dataset.reportIndex);
            const detail = document.querySelector(`tr.week-detail[data-report-index="${idx}"]`);
            if (detail) {
                detail.classList.toggle('hidden');
            } else {
                const report = reports[idx];
                const detailRow = document.createElement('tr');
                detailRow.className = 'week-detail';
                detailRow.dataset.reportIndex = idx;
                detailRow.innerHTML = `<td colspan="7"><div class="week-skill-detail"><div class="skill-grid">${weekSkillColumns(report.skills || {}, report.skillsChange || {})}</div></div></td>`;
                row.after(detailRow);
            }
        });
    });
}

function playerCard(player) {
    const skills = player.info?.skills || {};
    const changes = player.info?.skillsChange || {};
    return `
        <button class="player-card" data-player-id="${player.id}">
            <div class="player-card-head">
                <h3>${escapeHtml(fullName(player))}</h3>
                <span>Age: ${age(player)}</span>
            </div>
            <div class="skill-grid">
                <div class="skill-column-extra">${playerSkillRows(skills, changes, ['form', 'tacticalDiscipline', 'experience', 'teamwork'])}</div>
                <div class="skill-column-left">${playerSkillRows(skills, changes, ['stamina', 'pace', 'technique', 'passing'])}</div>
                <div class="skill-column-right">${playerSkillRows(skills, changes, ['keeper', 'defending', 'playmaking', 'striker'])}</div>
            </div>
            <div class="delta-strip">${deltaPills(changes)}</div>
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

function weekSkillColumns(skills, changes) {
    return `
        <div class="skill-column-extra">${playerSkillRows(skills, changes, ['form', 'tacticalDiscipline', 'experience', 'teamwork'])}</div>
        <div class="skill-column-left">${playerSkillRows(skills, changes, ['stamina', 'pace', 'technique', 'passing'])}</div>
        <div class="skill-column-right">${playerSkillRows(skills, changes, ['keeper', 'defending', 'playmaking', 'striker'])}</div>
    `;
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
    const isAdv = report.kind?.name === 'individual';
    return `
        <tr data-player-id="${row.id}">
            <td>${isAdv ? '<span class="planner-adv-marker" title="Advanced training">&#x2605;</span>' : ''}</td>
            <td><strong>${escapeHtml(fullName(row.player))}</strong><div class="small-muted">ID ${row.id}</div></td>
            <td>${age(row.player)}</td>
            <td class="mobile-hide">${escapeHtml(report.type?.name || report.kind?.name || 'missing')}</td>
            <td class="mobile-hide">${report.intensity ?? '-'}%</td>
            <td><div class="delta-strip">${deltaPills(report.skillsChange || {})}</div></td>
        </tr>
    `;
}

function historyRow(report, index) {
    const kind = report.kind?.name;
    const intensity = report.intensity ?? 100;
    const badge = intensity <= 0
        ? '<span class="training-badge injury-badge" title="Injured / no training">&#x2716;</span> '
        : kind === 'individual'
            ? '<span class="training-badge advanced-badge" title="Advanced training">★</span> '
            : kind === 'formation'
                ? '<span class="training-badge formation-badge" title="Formation training">FT</span> '
                : '';
    return `
        <tr class="clickable-row" data-report-index="${index}">
            <td>${report.day?.season ?? '-'}</td>
            <td>${report.day?.seasonWeek ?? report.week ?? '-'}</td>
            <td>${formatDate(report.day?.date?.value || report.date?.value)}</td>
            <td>${badge}${escapeHtml(report.type?.name || report.kind?.name || '-')}</td>
            <td class="mobile-hide">${report.intensity ?? '-'}%</td>
            <td class="mobile-hide">${report.source === 'sktables' ? '<span class="training-badge source-badge">SkTables</span>' : '<span class="small-muted">Sokker</span>'}</td>
            <td><div class="delta-strip">${deltaPills(report.skillsChange || {})}</div></td>
        </tr>
    `;
}

function skillGrid(skills) {
    return `
        <div class="skill-column-extra">${playerSkillRows(skills, {}, ['form', 'tacticalDiscipline', 'experience', 'teamwork'])}</div>
        <div class="skill-column-left">${playerSkillRows(skills, {}, ['stamina', 'pace', 'technique', 'passing'])}</div>
        <div class="skill-column-right">${playerSkillRows(skills, {}, ['keeper', 'defending', 'playmaking', 'striker'])}</div>
    `;
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
    return pills.length ? pills.join('') : '<span class="small-muted">No change</span>';
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

function deltaInline(value) {
    const sign = value > 0 ? '+' : '-';
    return `<span class="${skillChangeClass(value)} smaller-2 bold">${sign}${Math.abs(value)}</span>`;
}

function potentialTag(value) {
    const normalized = String(value).toLowerCase();
    const klass = normalized.includes('excellent') ? 'positive'
        : normalized.includes('tragic') || normalized.includes('weak') ? 'negative'
            : '';
    return `<span class="training-badge ${klass ? `badge-${klass}` : ''}">${escapeHtml(value)}</span>`;
}

function riskTag(value) {
    const klass = value === 'OK' ? 'positive' : 'negative';
    return `<span class="training-badge badge-${klass}">${escapeHtml(value)}</span>`;
}

function miniGraph(values) {
    if (!values.length) {
        return '<span class="small-muted">-</span>';
    }
    const numeric = values.map((item) => Number(item.y ?? 0));
    const min = Math.min(...numeric);
    const max = Math.max(...numeric);
    return `
        <div class="mini-graph" title="${numeric.join(' ')}">
            ${numeric.slice(-18).map((value) => {
                const height = max === min ? 45 : 18 + ((value - min) / (max - min)) * 62;
                return `<span style="--h:${height}%"></span>`;
            }).join('')}
        </div>
    `;
}

function loadingPanel(title, message) {
    return `
        <div class="view-panel">
            <div class="toolbar"><h2>${escapeHtml(title)}</h2></div>
            <div class="empty-state">${escapeHtml(message)}</div>
        </div>
    `;
}

function errorPanel(title, message) {
    return `
        <div class="view-panel">
            <div class="toolbar"><h2>${escapeHtml(title)}</h2></div>
            <div class="empty-state">${escapeHtml(message)}</div>
        </div>
    `;
}

function money(value) {
    if (!value || typeof value.value !== 'number') {
        return '-';
    }
    return `${number(value.value)} ${escapeHtml(value.currency || '')}`;
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
    return new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 }).format(value);
}

function escapeHtml(value) {
    return String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#039;');
}

function makeSortable(table) {
    const tbody = table.querySelector('tbody');
    if (!tbody) return;
    const thead = table.querySelector('thead');
    if (!thead) return;
    const rows = Array.from(tbody.querySelectorAll('tr'));
    if (!rows.length) return;
    const ths = thead.querySelectorAll('th');
    let sortCol = -1;
    let sortAsc = true;

    function getVal(row, col) {
        const cell = row.children[col];
        if (!cell) return '';
        const text = cell.textContent.trim();
        const num = parseFloat(text.replace(/[^0-9.\-]/g, ''));
        return isNaN(num) ? text.toLowerCase() : num;
    }

    function sort(col) {
        const isSame = col === sortCol;
        sortAsc = isSame ? !sortAsc : true;
        sortCol = col;
        const dir = sortAsc ? 1 : -1;
        const sorted = rows.slice().sort((a, b) => {
            const va = getVal(a, col);
            const vb = getVal(b, col);
            if (typeof va === 'number' && typeof vb === 'number') return (va - vb) * dir;
            return String(va).localeCompare(String(vb)) * dir;
        });
        ths.forEach((th, i) => {
            th.classList.toggle('sort-asc', i === col && sortAsc);
            th.classList.toggle('sort-desc', i === col && !sortAsc);
        });
        sorted.forEach((row) => tbody.appendChild(row));
    }

    ths.forEach((th, i) => {
        th.addEventListener('click', () => sort(i));
    });
}

async function renderPlanner() {
    const posSkills = { GK:'keeper', DEF:'defending', MID:'playmaking', ATT:'striker' };
    const formations = ['GK','DEF','MID','ATT'];
    const sLabel = { stamina:'Sta', keeper:'GK', pace:'Pac', defending:'Def', technique:'Tec', playmaking:'Pla', passing:'Pas', striker:'Str' };
    const allSkills = ['keeper','pace','defending','technique','playmaking','passing','striker'];
    const sColor = { stamina:'#8ab4f8', keeper:'#f0c040', pace:'#4fd36c', defending:'#ff6b6b', technique:'#c084fc', playmaking:'#f0c040', passing:'#4fd36c', striker:'#ff6b6b' };
    const trainableSkills = ['pace','defending','technique','playmaking','passing','striker'];

    plannerView.innerHTML = `
        <div class="view-panel">
            <div class="toolbar"><h2>Training Planner</h2></div>
            <div class="empty-state">Loading player history...</div>
        </div>
    `;

    if (!state.trainingSetup) {
        state.trainingSetup = await getJson(api.trainingPlayers);
    }
    const advIdSet = advancedIds();
    const setupById = {};
    for (const p of [...(state.trainingSetup?.advanced || []), ...(state.trainingSetup?.general || [])]) {
        setupById[Number(p.id)] = p;
    }
    await Promise.all(state.trainingRows.map(r => loadPlayerReport(r.id).catch(() => {})));

    let players = state.players.map(player => {
        const pid = Number(player.id);
        const setupRow = setupById[pid];
        const row = state.trainingRows.find(r => Number(r.id) === pid);
        const report = row?.report || {};
        const skills = player.info?.skills || report.skills || {};
        const fm = setupRow?.formation?.name || playerFormation(row) || 'MID';
        const isAdv = advIdSet.has(pid);
        const pAge = report.age ?? age(player);
        const reports = state.playerReports.get(pid) || [];
        const skillData = {};
        for (const sk of allSkills) {
            const cur = skills[sk] ?? 0;
            if (cur >= 18) { skillData[sk] = { cur, maxed: true, accumulated: 0, target: 0, remaining: 0 }; continue; }
            const intervals = completedIntervals(reports, sk);
            const target = targetCredit(sk, cur, intervals, pAge);
            const accumulated = accumulatedCredit(reports, sk);
            const remaining = Math.max(0, target - accumulated);
            skillData[sk] = { cur, maxed: false, accumulated, target, remaining };
        }
        let bestScore = -1, bestFm = fm, bestSkill = 'pace';
        const validFms = fm === 'GK' ? formations : formations.filter(f => f !== 'GK');
        for (const f of validFms) {
            const valid = [...trainableSkills, posSkills[f]].filter((v, i, a) => a.indexOf(v) === i);
            for (const sk of valid) {
                const nd = skillData[sk];
                if (!nd || nd.maxed) continue;
                if (nd.remaining > bestScore) { bestScore = nd.remaining; bestFm = f; bestSkill = sk; }
            }
        }
        return { id: pid, player, age: pAge, skillData, isAdv, bestFm, bestSkill, bestScore };
    });

    const advancedCount = players.filter(p => p.isAdv).length;

    players.sort((a, b) => {
        if (a.isAdv !== b.isAdv) return a.isAdv ? -1 : 1;
        return b.bestScore - a.bestScore;
    });

    const fmPlan = {};
    for (const fm of formations) {
        const assigned = players.filter(p => p.bestFm === fm);
        if (!assigned.length) { fmPlan[fm] = posSkills[fm]; continue; }
        const skillScores = {};
        for (const p of assigned) {
            const valid = [...trainableSkills, posSkills[fm]].filter((v, i, a) => a.indexOf(v) === i);
            for (const sk of valid) {
                const nd = p.skillData[sk];
                if (nd && !nd.maxed) skillScores[sk] = (skillScores[sk] || 0) + nd.remaining;
            }
        }
        fmPlan[fm] = Object.entries(skillScores).sort((a, b) => b[1] - a[1])[0]?.[0] || posSkills[fm];
    }

    const planLegend = formations.map(fm =>
        `<span class="order-legend-item"><span class="training-badge">${fm}</span> → ${sLabel[fmPlan[fm]] || fmPlan[fm]}</span>`
    ).join('');

    const desktopHtml = `
        <div class="planner-desktop">
            <div class="planner-note small-muted">Each cell shows the <strong>current skill level</strong>. Hover for details: next level, accumulated/target credits and remaining need. The bar fills by <strong>accumulated/target</strong> credits toward the next level.</div>
            <div class="table-scroll">
                <table class="data-table planner-table">
                    <thead>
                    <tr>
                        <th title="Advanced training (Sokker)">Adv</th>
                        <th>Name</th>
                        <th>Age</th>
                        <th>Pos</th>
                        ${allSkills.map(sk => `<th title="${sk}" style="color:${sColor[sk]}">${sLabel[sk]||sk}</th>`).join('')}
                    </tr>
                    </thead>
                    <tbody>
                    ${players.length ? players.map(p => {
                        const advClass = p.isAdv ? 'planner-adv-row' : '';
                        const advMarker = p.isAdv ? '<span class="planner-adv-marker" title="On advanced training in Sokker">&#x2605;</span>' : '';
                        return `<tr class="${advClass}" data-player-id="${p.id}">
                            <td>${advMarker}</td>
                            <td><strong>${escapeHtml(fullName(p.player))}</strong><div class="small-muted">ID ${p.id}</div></td>
                            <td>${p.age}</td>
                            <td><span class="training-badge">${setupPlayerFormation(p.id) || playerFormation(p) || 'MID'}</span></td>
                            ${allSkills.map(sk => {
                                const sd = p.skillData[sk];
                                if (!sd) return '<td class="small-muted">-</td>';
                                if (sd.maxed) return '<td style="color:#4fd36c"><strong>MAX</strong></td>';
                                const pct = sd.target > 0 ? Math.min(100, Math.round((sd.accumulated / sd.target) * 100)) : 0;
                                const barColor = pct >= 100 ? '#4fd36c' : pct >= 80 ? '#f0c040' : '#ff6b6b';
                                return `<td title="${sk} ${sd.cur} → ${sd.cur + 1}, ${sd.accumulated.toFixed(2)}/${sd.target.toFixed(2)} (${pct}%), need: ${sd.remaining.toFixed(2)}">
                                    <div class="deficit-bar-container">
                                        <div class="deficit-bar" style="width:${pct}%;background:${barColor}"></div>
                                        <span class="deficit-label">${sd.cur}</span>
                                    </div>
                                </td>`;
                            }).join('')}
                        </tr>`;
                    }).join('') : '<tr><td colspan="20" class="empty-state">No training data.</td></tr>'}
                    </tbody>
                </table>
            </div>
        </div>
    `;

    const mobileHtml = `
        <div class="planner-mobile">
            <div class="planner-note small-muted">Tap a player to see all skills. Each bar shows <strong>accumulated/target</strong> credits toward the next level.</div>
            ${players.length ? players.map(p => {
                const advClass = p.isAdv ? 'planner-adv-row' : '';
                const advMarker = p.isAdv ? '<span class="planner-adv-marker">★</span>' : '';
                const fm = setupPlayerFormation(p.id) || playerFormation(p) || 'MID';
                const bestEntry = Object.entries(p.skillData).filter(([_, sd]) => sd && !sd.maxed).sort((a, b) => b[1].remaining - a[1].remaining)[0];
                const bestKey = bestEntry ? bestEntry[0] : null;
                const bestData = bestEntry ? bestEntry[1] : null;
                const bestPct = bestData && bestData.target > 0 ? Math.min(100, Math.round((bestData.accumulated / bestData.target) * 100)) : 0;
                const bestColor = bestPct >= 100 ? '#4fd36c' : bestPct >= 80 ? '#f0c040' : '#ff6b6b';
                return `
                <div class="planner-mobile-card ${advClass}" data-mobile-pid="${p.id}">
                    <div class="pm-header" data-pm-toggle>
                        <span class="pm-header-left">
                            ${advMarker}
                            <strong>${escapeHtml(fullName(p.player))}</strong>
                            <span class="small-muted">Age ${p.age}</span>
                            <span class="training-badge">${fm}</span>
                        </span>
                        <span class="pm-arrow">&#x25B6;</span>
                    </div>
                    <div class="pm-summary">
                        ${bestData ? `
                            <span class="pm-label">${sLabel[bestKey]} ${bestData.cur}</span>
                            <div class="deficit-bar-container">
                                <div class="deficit-bar" style="width:${bestPct}%;background:${bestColor}"></div>
                                <span class="deficit-label">${bestPct}%</span>
                            </div>
                            <span class="pm-need">need ${bestData.remaining.toFixed(1)}</span>
                        ` : '<span class="small-muted">All MAX</span>'}
                    </div>
                    <div class="pm-detail">
                        ${allSkills.map(sk => {
                            const sd = p.skillData[sk];
                            if (!sd) return `<div class="pm-skill"><span class="pm-label">${sLabel[sk]}</span><span class="small-muted">-</span></div>`;
                            if (sd.maxed) return `<div class="pm-skill"><span class="pm-label" style="color:#4fd36c">${sLabel[sk]}</span><strong style="color:#4fd36c">MAX</strong></div>`;
                            const pct = sd.target > 0 ? Math.min(100, Math.round((sd.accumulated / sd.target) * 100)) : 0;
                            const barColor = pct >= 100 ? '#4fd36c' : pct >= 80 ? '#f0c040' : '#ff6b6b';
                            return `<div class="pm-skill">
                                <div class="pm-skill-head">
                                    <span class="pm-label">${sLabel[sk]} ${sd.cur}</span>
                                    <span class="pm-pct">${pct}%</span>
                                    <span class="pm-need">need ${sd.remaining.toFixed(1)}</span>
                                </div>
                                <div class="deficit-bar-container">
                                    <div class="deficit-bar" style="width:${pct}%;background:${barColor}"></div>
                                    <span class="deficit-label">${sd.cur}</span>
                                </div>
                                <div class="small-muted" style="font-size:10px;text-align:right">${sd.accumulated.toFixed(2)}/${sd.target.toFixed(2)}</div>
                            </div>`;
                        }).join('')}
                    </div>
                </div>`;
            }).join('') : '<div class="empty-state">No training data.</div>'}
        </div>
    `;

    plannerView.innerHTML = `
        <div class="view-panel">
            <div class="toolbar">
                <h2>Training Planner</h2>
                <button id="refresh-planner" class="action-button">Refresh</button>
            </div>
            <div class="planner-summary">
                <div class="planner-summary-item"><strong>Advanced:</strong> ${advancedCount} / 10</div>
                <div class="planner-summary-item"><strong>Suggested formation plan:</strong></div>
                <div class="order-legend">${planLegend}</div>
            </div>
            ${desktopHtml}
            ${mobileHtml}
        </div>
    `;
    makeSortable(plannerView.querySelector('.planner-table'));
    plannerView.querySelector('#refresh-planner')?.addEventListener('click', async () => {
        state.trainingSetup = null;
        state.formationSkills = null;
        await loadData();
        renderShell();
        showView('planner');
    });
    plannerView.querySelectorAll('.planner-table [data-player-id]').forEach(row => {
        row.addEventListener('click', () => openPlayerDetail(Number(row.dataset.playerId)));
    });
    plannerView.querySelectorAll('[data-pm-toggle]').forEach(el => {
        el.addEventListener('click', () => {
            const card = el.closest('.planner-mobile-card');
            if (!card) return;
            const detail = card.querySelector('.pm-detail');
            const arrow = card.querySelector('.pm-arrow');
            if (!detail) return;
            const expanded = detail.style.display !== 'none';
            detail.style.display = expanded ? 'none' : 'block';
            if (arrow) arrow.innerHTML = expanded ? '&#x25B6;' : '&#x25BC;';
        });
    });
}

// Report integration
const eventTypeMap = {
    IN_ARENA: 'STADIUM_INCOME', IN_PRIZES: 'PRIZES', IN_SPONSORS: 'SPONSORS_WEEKLY',
    IN_SPONSORS_YEAR: 'SPONSOR_YEARLY_BONUS', IN_SUPPORTERS: 'SUPPORTERS_INCOME_WEEKLY',
    IN_TRANSFER: 'TRANSFER_INCOME', IN_YOUTH: 'YOUTH_COMMISIONS', OUT_ARENA: 'ARENA_MAINTENANCE',
    OUT_TRANSFER: 'TRANSFER_PAID', OUT_TRLIST: 'TRANSFER_LIST_COST',
    OUT_WAGES_HUMAN: 'PLAYERS_WAGE', OUT_WAGES_TRAINER: 'TRAINERS_WAGE',
    OUT_YOUTH: 'ACADEMY_EXPENSES', PITCH_MAINTENANCE: 'PITCH_MAINTENANCE',
    PITCH_RESIZE: 'PITCH_RESIZE', RPT_OUT_WAGES_JUNIOR_TRAINER: 'JUNIOR_TRAINERS_WAGE',
    SUPPORTERS_JOIN: 'SUPPORTERS_JOIN', SUPPORTERS_YEAR: 'SUPPORTERS_YEAR',
    YOUTH_JOIN: 'YOUTH_JOIN', YOUTH_NEW: 'YOUTH_NEW', YOUTH_PLAYER: 'YOUTH_PLAYER_JOINED'
};
const reverseEventTypeMap = Object.entries(eventTypeMap).reduce((acc, [k, v]) => { acc[v] = k; return acc; }, {});

function parseEventAmount(event) {
    const text = event.text || '';
    const match = text.match(/([\d\s]+)\s*din/i);
    if (!match) return 0;
    const num = parseInt(match[1].replace(/\s/g, ''), 10);
    return isNaN(num) ? 0 : num;
}
const amountEventTypes = new Set(['PLAYERS_WAGE', 'STADIUM_INCOME', 'PRIZES', 'TRANSFER_INCOME', 'YOUTH_COMMISIONS', 'TRAINERS_WAGE', 'JUNIOR_TRAINERS_WAGE']);

let reportAllEvents = [];
let reportAllMatches = [];
let reportCurrentPage = 1;
let reportPageSize = 20;
let reportSeasons = [];
let matchesCurrentPage = 1;
let matchesPageSize = 20;

async function loadReportView(type) {
    console.log('[Report] loadReportView called with type:', type);
    const viewId = type === 'matches' ? 'matches-report-view' : 'events-report-view';
    const view = document.getElementById(viewId);
    const teamId = state.current?.team?.id || 0;
    
    if (type === 'matches') {
        view.innerHTML = `
            <div class="view-panel">
                <div class="toolbar">
                    <h2>Matches Report</h2>
                    <div class="toolbar-actions">
                        <div class="stat-card"><div class="stat-value">${teamId}</div><div class="stat-label">Team ID</div></div>
                        <select id="matches-report-season"></select>
                        <button id="matches-report-load" class="action-button">Load data</button>
                    </div>
                </div>
                <div id="matches-report-toolbar">
                    <label>League Name <select id="matchLeagueName"><option value="">All leagues</option></select></label>
                    <label style="margin-left:12px;">League Type <select id="matchLeagueType"><option value="">All types</option></select></label>
                    <label style="margin-left:12px;">Arena <select id="matchArena"><option value="">All arenas</option></select></label>
                    <label style="margin-left:12px;">Rows per page 
                        <select id="matches-page-size" onchange="changeMatchesPageSize(this.value)">
                            <option value="10" ${matchesPageSize === 10 ? 'selected' : ''}>10</option>
                            <option value="20" ${matchesPageSize === 20 ? 'selected' : ''}>20</option>
                            <option value="50" ${matchesPageSize === 50 ? 'selected' : ''}>50</option>
                            <option value="100" ${matchesPageSize === 100 ? 'selected' : ''}>100</option>
                        </select>
                    </label>
                </div>
                <div id="matches-report-table" class="table-scroll"></div>
            </div>
        `;
    } else {
        view.innerHTML = `
            <div class="view-panel">
                <div class="toolbar">
                    <h2>Events Report</h2>
                    <div class="toolbar-actions">
                        <div class="stat-card"><div class="stat-value">${teamId}</div><div class="stat-label">Team ID</div></div>
                        <select id="events-report-season"></select>
                        <button id="events-report-load" class="action-button">Load data</button>
                    </div>
                </div>
                <div id="events-report-toolbar">
                    <label>Key 
                        <div class="multiselect" id="events-report-key-multiselect">
                            <button class="multiselect-toggle" id="events-report-key-toggle">All keys ▾</button>
                            <div class="multiselect-dropdown" id="events-report-key-dropdown"></div>
                        </div>
                    </label>
                    <label style="margin-left:12px;">Date 
                        <div class="multiselect" id="events-report-date-multiselect">
                            <button class="multiselect-toggle" id="events-report-date-toggle">All dates ▾</button>
                            <div class="multiselect-dropdown" id="events-report-date-dropdown"></div>
                        </div>
                    </label>
                    <label style="margin-left:12px;">Rows per page 
                        <select id="events-report-page-size" onchange="changeEventsPageSize(this.value)">
                            <option value="10" ${reportPageSize === 10 ? 'selected' : ''}>10</option>
                            <option value="20" ${reportPageSize === 20 ? 'selected' : ''}>20</option>
                            <option value="50" ${reportPageSize === 50 ? 'selected' : ''}>50</option>
                            <option value="100" ${reportPageSize === 100 ? 'selected' : ''}>100</option>
                        </select>
                    </label>
                </div>
                <div id="events-report-stats" class="stats"></div>
                <div id="events-report-table" class="table-scroll"></div>
            </div>
        `;
    }
    
    const v$ = (id) => document.getElementById(id);
    const seasonSelId = type === 'matches' ? 'matches-report-season' : 'events-report-season';
    const loadBtnId = type === 'matches' ? 'matches-report-load' : 'events-report-load';
    
    // Load seasons
    try {
        console.log('[Report] Loading seasons...');
        const res = await fetch('/sokker/api/report/seasons');
        const seasons = await res.json();
        console.log('[Report] Seasons loaded:', seasons);
        reportSeasons = seasons || [];
        const sel = document.getElementById(type === 'matches' ? 'matches-report-season' : 'events-report-season');
        if (Array.isArray(seasons)) {
            seasons.forEach(s => {
                const o = document.createElement('option');
                o.value = s.season; o.textContent = `Season ${s.season} (${s.start.week}-${s.end.week})`;
                sel.appendChild(o);
            });
        }
        if (seasons.length > 0) sel.value = String(seasons[0].season);
    } catch (e) { console.error('Seasons load failed', e); }
    
    // Load team arena for capacity and name
    try {
        const arenaRes = await fetch(`/sokker/api/report/arena?teamId=${teamId}`);
        const arenaData = await arenaRes.json();
        if (arenaData) {
            window.teamArenaName = arenaData.name || '';
            window.teamArenaSeats = Number(arenaData.seats) || 0;
        }
    } catch (e) { console.error('Arena load failed', e); }
    
    // Set default arena in matches tab
    if (type === 'matches' && window.teamArenaName) {
        const arenaSel = document.getElementById('matchArena');
        if (arenaSel) arenaSel.value = window.teamArenaName;
    }
    
    document.getElementById(type === 'matches' ? 'matches-report-load' : 'events-report-load').onclick = () => {
        console.log('[Report] Load button clicked');
        const season = document.getElementById(type === 'matches' ? 'matches-report-season' : 'events-report-season').value;
        if (!season) return;
        if (type === 'matches') loadReportMatches(teamId, Number(season));
        else loadReportEvents(teamId, Number(season));
    };
    
    // Close dropdowns when clicking outside
    document.addEventListener('click', (e) => {
        if (!e.target.closest('.multiselect')) {
            document.querySelectorAll('.multiselect-dropdown.open').forEach(d => d.classList.remove('open'));
        }
    });
}

async function loadReportMatches(teamId, season) {
    try {
        const res = await fetch(`/sokker/api/report/team-matches?teamId=${teamId}&season=${season}`);
        const data = await res.json();
        reportAllMatches = data.matches || [];
        populateMatchFilters();
        renderReportMatches();
    } catch (e) { console.error('Matches load failed', e); }
}

function populateMatchFilters() {
    const leagueNameSelect = document.getElementById('matchLeagueName');
    const leagueTypeSelect = document.getElementById('matchLeagueType');
    const arenaSelect = document.getElementById('matchArena');
    if (!leagueNameSelect) return;
    const leagues = new Set();
    const types = new Set();
    const arenas = new Set();
    for (const m of reportAllMatches) {
        const league = m.league?.name;
        if (league) leagues.add(league);
        const type = m.league?.type?.name;
        if (type) types.add(type);
        const arena = m.arena?.name;
        if (arena) arenas.add(arena);
    }
    document.getElementById('matchLeagueName').innerHTML = '<option value="">All leagues</option>' + [...leagues].sort().map(v => `<option value="${escapeHtml(v)}">${escapeHtml(v)}</option>`).join('');
    document.getElementById('matchLeagueType').innerHTML = '<option value="">All types</option>' + [...types].sort().map(v => `<option value="${escapeHtml(v)}">${escapeHtml(v)}</option>`).join('');
    document.getElementById('matchArena').innerHTML = '<option value="">All arenas</option>' + [...arenas].sort().map(v => `<option value="${escapeHtml(v)}">${escapeHtml(v)}</option>`).join('');
    const typeArray = [...types];
    const found = typeArray.find(t => t.toLowerCase() === 'league');
    if (found) document.getElementById('matchLeagueType').value = found;
    if (window.teamArenaName && [...arenas].includes(window.teamArenaName)) document.getElementById('matchArena').value = window.teamArenaName;
    document.getElementById('matchLeagueName').onchange = renderReportMatches;
    document.getElementById('matchLeagueType').onchange = renderReportMatches;
    document.getElementById('matchArena').onchange = renderReportMatches;
}

function renderReportMatches() {
    const leagueNameFilter = document.getElementById('matchLeagueName')?.value || '';
    const leagueTypeFilter = document.getElementById('matchLeagueType')?.value || '';
    const arenaFilter = document.getElementById('matchArena')?.value || '';

    const arenaByDate = new Map();
    for (const ev of reportAllEvents) {
        if (ev.type?.key === 'IN_ARENA') {
            const d = ev.date?.value;
            if (d) arenaByDate.set(d, ev.text || '');
        }
    }
    
    const teamId = state.current?.team?.id || 0;
    const filtered = reportAllMatches.filter(m => {
        const leagueName = m.league?.name || '';
        const leagueType = m.league?.type?.name || '';
        const arenaName = m.arena?.name || '';
        if (leagueNameFilter && leagueName !== leagueNameFilter) return false;
        if (leagueTypeFilter && leagueType !== leagueTypeFilter) return false;
        if (arenaFilter && arenaName !== arenaFilter) return false;
        return true;
    });

    const parseIncome = (text) => {
        if (!text) return 0;
        const m = text.match(/Income from tickets was[^0-9]*([\d\s\u00A0]+)\s*din/i);
        if (m && m[1]) {
            const num = m[1].replace(/[\s\u00A0]/g, '');
            const val = parseInt(num, 10);
            return isNaN(val) ? 0 : val;
        }
        const m2 = text.match(/([\d\s\u00A0]{6,})/);
        if (m2) {
            const num = m2[1].replace(/[\s\u00A0]/g, '');
            const val = parseInt(num, 10);
            if (!isNaN(val) && val > 0) return val;
        }
        return 0;
    };

    let sumSupporters = 0;
    let sumIncome = 0;
    let sumCapacity = 0;
    
    // Pagination
    const totalPages = Math.ceil(filtered.length / matchesPageSize) || 1;
    if (matchesCurrentPage > totalPages) matchesCurrentPage = 1;
    const start = (matchesCurrentPage - 1) * matchesPageSize;
    const pageItems = filtered.slice(start, start + matchesPageSize);
    
    const pageRows = pageItems.map(m => {
        const gameDay = m.time?.gameDay || {};
        const date = gameDay.date?.value || '';
        const week = gameDay.week ?? '';
        const seasonWeek = gameDay.seasonWeek ?? '';
        const season = gameDay.season ?? '';
        const timeVal = m.time?.time?.value || '';
        const home = m.home || {};
        const away = m.away || {};
        const isHome = home.id === state.current?.team?.id;
        const opponent = isHome ? away : home;
        const opponentName = opponent?.name || '';
        const scoreHome = m.score?.home ?? '';
        const scoreAway = m.score?.away ?? '';
        const result = isHome ? `${scoreHome} - ${scoreAway}` : `${scoreAway} - ${scoreHome}`;
        const supporters = Number(m.supporters) || 0;
        const leagueName = m.league?.name || '';
        const leagueType = m.league?.type?.name || '';
        const arenaName = m.arena?.name || '';
        const arenaText = arenaByDate.get(date) || '';
        const incomeVal = parseIncome(arenaText);
        const capacityPct = window.teamArenaSeats > 0 ? (supporters / window.teamArenaSeats * 100) : 0;
        sumSupporters += supporters;
        sumIncome += parseIncome(arenaByDate.get(date) || '');
        sumCapacity += capacityPct;
        return `
            <tr>
                <td>${season}</td>
                <td>${week}</td>
                <td>${seasonWeek}</td>
                <td>${date}</td>
                <td>${timeVal || ''}</td>
                <td>${opponentName || ''}</td>
                <td>${result}</td>
                <td>${leagueName}</td>
                <td>${leagueType || ''}</td>
                <td>${arenaName || ''}</td>
                <td>${supporters}</td>
                <td>${capacityPct > 0 ? capacityPct.toFixed(1) + '%' : ''}</td>
                <td class="event-text">${incomeVal} din</td>
            </tr>
        `;
    }).join('');
    
    const count = filtered.length || 1;
    const avgSupporters = Math.round(sumSupporters / count);
    const avgIncome = Math.round(sumIncome / count);
    const avgCapacity = Math.round(sumCapacity / count);
    const fmt = n => n.toLocaleString('de-DE');

    const footer = filtered.length ? `
        <tr style="font-weight:700;background:#f0f4f8;color:#111;">
            <td colspan="10">Total</td>
            <td>${fmt(sumSupporters)}</td>
            <td>${avgCapacity.toFixed(1)}%</td>
            <td>${fmt(sumIncome)} din</td>
        </tr>
        <tr style="font-weight:700;background:#f7f9fb;color:#111;">
            <td colspan="10">Average</td>
            <td>${fmt(avgSupporters)}</td>
            <td>${avgCapacity.toFixed(1)}%</td>
            <td>${fmt(sumIncome)} din</td>
        </tr>
    ` : '';
    
    const html = `
        <div class="table-scroll">
            <table class="data-table">
                <thead>
                    <tr><th>Season</th><th>Week</th><th>Season Week</th><th>Date</th><th>Time</th><th>Opponent</th><th>Result</th><th>League</th><th>League Type</th><th>Arena</th><th>Supporters</th><th>Capacity</th><th>Arena Income</th></tr>
                </thead>
                <tbody>${pageRows + (footer || '')}</tbody>
            </table>
        </div>
    `;
    document.querySelector('#matches-report-table').innerHTML = html;
}

async function loadReportEvents(teamId, season) {
    const seasonObj = reportSeasons.find(s => s.season === season);
    const fromWeek = seasonObj?.start?.week || 1;
    const toWeek = seasonObj?.end?.week || 52;
    
    try {
        const res = await fetch(`/sokker/api/report/report?teamId=${teamId}&fromWeek=${fromWeek}&toWeek=${toWeek}`);
        const data = await res.json();
        reportAllEvents = data.events || [];
        populateReportFilters();
        applyReportFilters();
    } catch (e) { console.error('Events load failed', e); }
}

function populateReportFilters() {
    const keys = new Set();
    reportAllEvents.forEach(e => { if (e.type?.key) keys.add(e.type.key); });
    
    const dropdown = document.getElementById('events-report-key-dropdown');
    if (dropdown) {
        dropdown.innerHTML = '';
        [...keys].sort().forEach(key => {
            dropdown.insertAdjacentHTML('beforeend', `<label><input type="checkbox" data-key="${key}"> ${eventTypeMap[key] || key}</label>`);
        });
        document.getElementById('events-report-key-toggle').onclick = (e) => { e.stopPropagation(); dropdown.classList.toggle('open'); };
        dropdown.addEventListener('change', () => {
            const checked = dropdown.querySelectorAll('input[type=checkbox]:checked');
            document.getElementById('events-report-key-toggle').textContent = checked.length === 0 ? 'All keys ▾' : `${checked.length} selected ▾`;
            applyReportFilters();
        });
    }
    
    const dates = [...new Set(reportAllEvents.map(e => e.date?.value).filter(Boolean))].sort().reverse();
    const dateDropdown = document.getElementById('events-report-date-dropdown');
    if (dateDropdown) {
        dateDropdown.innerHTML = '';
        dates.forEach(d => {
            dateDropdown.insertAdjacentHTML('beforeend', `<label><input type="checkbox" data-date="${d}"> ${d}</label>`);
        });
        document.getElementById('events-report-date-toggle').onclick = (e) => { e.stopPropagation(); dateDropdown.classList.toggle('open'); };
        dateDropdown.addEventListener('change', () => {
            const checked = dateDropdown.querySelectorAll('input[type=checkbox]:checked');
            document.getElementById('events-report-date-toggle').textContent = checked.length === 0 ? 'All dates ▾' : `${checked.length} selected ▾`;
            applyReportFilters();
        });
    }
    
    // Close dropdowns on outside click
    document.addEventListener('click', () => {
        const keyDrop = document.getElementById('events-report-key-dropdown');
        const dateDrop = document.getElementById('events-report-date-dropdown');
        if (keyDrop) keyDrop.classList.remove('open');
        if (dateDrop) dateDrop.classList.remove('open');
    });
    document.getElementById('events-report-key-multiselect')?.addEventListener('click', e => e.stopPropagation());
    document.getElementById('events-report-date-multiselect')?.addEventListener('click', e => e.stopPropagation());
}

function applyReportFilters() {
    const keyDropdown = document.getElementById('events-report-key-dropdown');
    const checkedKeyBoxes = keyDropdown ? Array.from(keyDropdown.querySelectorAll('input[type=checkbox]:checked')) : [];
    const keyFilters = checkedKeyBoxes.map(cb => cb.dataset.key).filter(v => v);
    
    const dateDropdown = document.getElementById('events-report-date-dropdown');
    const checkedDateBoxes = dateDropdown ? Array.from(dateDropdown.querySelectorAll('input[type=checkbox]:checked')) : [];
    const dateFilters = checkedDateBoxes.map(cb => cb.dataset.date).filter(v => v);
    
    const filtered = reportAllEvents.filter(e => {
        const eventKey = e.type?.key || '';
        if (keyFilters.length > 0 && !keyFilters.includes(e.type?.key || '')) return false;
        if (dateFilters.length > 0 && !dateFilters.includes(e.date?.value)) return false;
        return true;
    });
    
    reportCurrentPage = 1;
    renderReportEvents(filtered);
}

function renderReportEvents(filtered) {
    const totalEvents = reportAllEvents.length;
    const totalAmount = filtered.reduce((sum, e) => sum + parseEventAmount(e), 0);
    const avgAmount = filtered.length > 0 ? Math.round(totalAmount / filtered.length) : 0;
    const fmt = n => n.toLocaleString('de-DE');
    
    const statsHtml = `
        <div class="stat-card"><div class="stat-value">${reportAllEvents.length}</div><div class="stat-label">Total events</div></div>
        <div class="stat-card"><div class="stat-value">${filtered.length}</div><div class="stat-label">Filtered</div></div>
        <div class="stat-card"><div class="stat-value">${reportCurrentPage}</div><div class="stat-label">Current page</div></div>
        <div class="stat-card"><div class="stat-value">${fmt(filtered.reduce((sum, e) => sum + parseEventAmount(e), 0))} din</div><div class="stat-label">Total amount</div></div>
        <div class="stat-card"><div class="stat-value">${filtered.length > 0 ? fmt(Math.round(filtered.reduce((sum, e) => sum + parseEventAmount(e), 0) / filtered.length)) : fmt(0)} din</div><div class="stat-label">Avg amount</div></div>
    `;
    document.getElementById('events-report-stats').innerHTML = statsHtml;
    
    // Pagination
    const totalPages = Math.ceil(filtered.length / reportPageSize) || 1;
    if (reportCurrentPage > totalPages) reportCurrentPage = 1;
    const start = (reportCurrentPage - 1) * reportPageSize;
    const pageItems = filtered.slice(start, start + reportPageSize);
    
    const pageNumbers = [];
    for (let i = 1; i <= totalPages; i++) {
        if (i === 1 || i === totalPages || (i >= reportCurrentPage - 1 && i <= reportCurrentPage + 1)) {
            pageNumbers.push(i);
        } else if (pageNumbers[pageNumbers.length - 1] !== '...') {
            pageNumbers.push('...');
        }
    }
    
    const paginationHtml = totalPages > 1 ? `
        <div class="pager">
            <button class="secondary" onclick="changeReportPage(-1)" ${reportCurrentPage === 1 ? 'disabled' : ''}>&#x2190; Previous</button>
            <div class="page-numbers">
                ${pageNumbers.map(p => p === '...' ? '<span>...</span>' : `<button class="${p === reportCurrentPage ? 'active' : ''}" onclick="goToReportPage(${p})">${p}</button>`).join('')}
            </div>
            <button class="secondary" onclick="changeReportPage(1)" ${reportCurrentPage === totalPages ? 'disabled' : ''}>Next &#x2192;</button>
            <span style="margin-left:12px;">Rows per page:
                <select id="events-report-page-size" onchange="changeEventsPageSize(this.value)">
                    <option value="10" ${reportPageSize === 10 ? 'selected' : ''}>10</option>
                    <option value="20" ${reportPageSize === 20 ? 'selected' : ''}>20</option>
                    <option value="50" ${reportPageSize === 50 ? 'selected' : ''}>50</option>
                    <option value="100" ${reportPageSize === 100 ? 'selected' : ''}>100</option>
                </select>
            </span>
        </div>
    ` : '';
    
    const html = `
        <div class="table-scroll">
            <table class="data-table">
                <thead>
                    <tr><th>#</th><th>Date</th><th>Week</th><th>Type</th><th>Key</th><th>Amount</th><th>Event</th></tr>
                </thead>
                <tbody>
                    ${pageItems.map((e, i) => `
                        <tr><td>${start + i + 1}</td><td>${e.date?.value||''}</td><td>${e.week||''}</td><td>${e.type?.value||''}</td><td>${e.type?.key||''}</td><td>${fmt(parseEventAmount(e))} din</td><td>${e.text||''}</td></tr>
                    `).join('')}
                </tbody>
            </table>
        </div>
        <div class="pager">
            <button class="secondary" onclick="changeReportPage(-1)" ${reportCurrentPage === 1 ? 'disabled' : ''}>&#x2190; Previous</button>
            <div class="page-numbers">
                ${pageNumbers.map(p => p === '...' ? '<span>...</span>' : `<button class="${p === reportCurrentPage ? 'active' : ''}" onclick="goToReportPage(${p})">${p}</button>`).join('')}
            </div>
            <button class="secondary" onclick="changeReportPage(1)" ${reportCurrentPage === totalPages ? 'disabled' : ''}>Next &#x2192;</button>
            <span style="margin-left:12px;">Rows per page:
                <select id="events-report-page-size" onchange="changeEventsPageSize(this.value)">
                    <option value="10" ${reportPageSize === 10 ? 'selected' : ''}>10</option>
                    <option value="20" ${reportPageSize === 20 ? 'selected' : ''}>20</option>
                    <option value="50" ${reportPageSize === 50 ? 'selected' : ''}>50</option>
                    <option value="100" ${reportPageSize === 100 ? 'selected' : ''}>100</option>
                </select>
            </span>
        </div>
    `;
    document.getElementById('events-report-table').innerHTML = html;
}

function checkedKeysText() {
    const dropdown = document.getElementById('events-report-key-dropdown');
    const checked = dropdown ? dropdown.querySelectorAll('input[type=checkbox]:checked') : [];
    return checked.length === 0 ? 'All keys ▾' : `${checked.length} selected ▾`;
}

function changeReportPage(delta) {
    reportCurrentPage += delta;
    if (reportCurrentPage < 1) reportCurrentPage = 1;
    applyReportFilters();
}

function goToReportPage(page) {
    reportCurrentPage = page;
    applyReportFilters();
}

function changeEventsPageSize(size) {
    reportPageSize = Number(size);
    reportCurrentPage = 1;
    applyReportFilters();
}

function changeMatchesPageSize(size) {
    matchesPageSize = Number(size);
    matchesCurrentPage = 1;
    renderReportMatches();
}

function goToMatchesPage(page) {
    matchesCurrentPage = page;
    renderReportMatches();
}



function sanitizeEventHtml(html) {
    const template = document.createElement("template");
    template.innerHTML = html || '';
    template.content.querySelectorAll("script, iframe, object, embed").forEach(el => el.remove());
    template.content.querySelectorAll("a").forEach(link => {
        const href = link.getAttribute("href");
        if (!href || !href.startsWith("/player.php?PID=")) {
            link.replaceWith(document.createTextNode(link.textContent));
            return;
        }
        link.target = "_blank";
        link.rel = "noopener noreferrer";
    });
    return template.innerHTML;
}
