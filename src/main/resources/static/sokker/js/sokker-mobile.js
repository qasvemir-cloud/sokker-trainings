const api = {
    login: '/sokker/api/login',
    logout: '/sokker/api/logout',
    players: '/sokker/api/players',
    training: '/sokker/api/training/current',
    trainingPlayers: '/sokker/api/training/players',
    trainingFormations: '/sokker/api/training/formations',
    playerTraining: (playerId) => `/sokker/api/players/${playerId}/training`
};

const skillLabels = [
    ['form', 'Form'], ['stamina', 'Sta'], ['pace', 'Pace'], ['keeper', 'GK'],
    ['defending', 'Def'], ['technique', 'Tech'], ['playmaking', 'PM'],
    ['passing', 'Pass'], ['striker', 'Str'], ['tacticalDiscipline', 'Tact'],
    ['experience', 'Exp'], ['teamwork', 'TW']
];
const skillNames = Object.fromEntries(skillLabels);

// Two-column layout: left = stamina, pace, technique, passing; right = GK, defending, playmaking, striker
const leftSkills = ['stamina', 'pace', 'technique', 'passing'];
const rightSkills = ['keeper', 'defending', 'playmaking', 'striker'];

const gtRatio = 6;

const state = {
    current: null,
    players: [],
    activeTab: 'history'
};

const loginScreen = document.querySelector('#login-screen');
const appScreen = document.querySelector('#app-screen');
const loginForm = document.querySelector('#login-form');
const loginError = document.querySelector('#login-error');

const fallbackTeamLogo = '/sokker/images/omladinacLogo.png';

async function loadLogin() {
    loginForm.addEventListener('submit', async (event) => {
        event.preventDefault();
        loginError.textContent = '';
        const button = loginForm.querySelector('button');
        button.disabled = true;
        button.textContent = 'Logging in...';
        try {
            const form = new FormData(loginForm);
            const response = await postJson(api.login, {
                login: form.get('login'),
                password: form.get('password')
            });
            state.current = response;
            await bootstrap();
        } catch (error) {
            loginError.textContent = error.message;
        } finally {
            button.disabled = false;
            button.textContent = 'Login';
        }
    });

    document.querySelector('#logout-button').addEventListener('click', async () => {
        await fetch(api.logout, { method: 'POST' }).catch(() => {});
        state.current = null;
        appScreen.classList.add('hidden');
        loginScreen.classList.remove('hidden');
    });

    document.querySelector('#back-button').addEventListener('click', () => showPlayers());

    document.querySelectorAll('.tab').forEach((tab) => {
        tab.addEventListener('click', () => switchTab(tab.dataset.tab));
    });
}

async function bootstrap() {
    loginScreen.classList.add('hidden');
    appScreen.classList.remove('hidden');
    await loadData();
    renderTeam();
    renderPlayers();
    showPlayers();
}

async function loadData() {
    const playersResponse = await getJson(api.players);
    state.players = playersResponse.players || [];
}

// ---------- Team header ----------
function renderTeam() {
    const team = state.current?.team || {};
    const badge = document.querySelector('#team-emblem');
    badge.onerror = () => { badge.onerror = null; badge.src = fallbackTeamLogo; };
    badge.src = resolveSokkerAsset(team.emblem) || fallbackTeamLogo;
    document.querySelector('#team-name').textContent = team.name || 'Club';
    document.querySelector('#team-country').textContent = team.country?.name || '-';
    document.querySelector('#team-rank').textContent = team.rank ? number(team.rank) : '-';
    document.querySelector('#team-position').textContent = team.rankPosition ?? '-';
}

// ---------- Player list ----------
function showPlayers() {
    document.querySelector('#players-view').classList.remove('hidden');
    document.querySelector('#detail-view').classList.add('hidden');
}

function renderPlayers() {
    const list = document.querySelector('#player-list');
    if (!state.players.length) {
        list.innerHTML = '<div class="empty">No players found.</div>';
        return;
    }
    const sorted = [...state.players].sort((a, b) => {
        const n1 = fullName(a), n2 = fullName(b);
        return (n1 || '').localeCompare(n2 || '');
    });
    list.innerHTML = sorted.map(playerCard).join('');
    list.querySelectorAll('.player-card').forEach((card) => {
        card.addEventListener('click', () => openPlayerDetail(Number(card.dataset.playerId)));
    });
}

function playerCard(player) {
    const skills = player.info?.skills || {};
    const changes = player.info?.skillsChange || {};
    return `
        <button class="player-card" data-player-id="${player.id}">
            <div class="player-card-head">
                <h3>${escapeHtml(fullName(player))}</h3>
                <span class="player-age">${age(player)} y</span>
            </div>
            <div class="skill-grid">
                ${skillRows(skills, changes, leftSkills)}
                ${skillRows(skills, changes, rightSkills)}
            </div>
            <div class="delta-strip">${deltaPills(changes)}</div>
        </button>
    `;
}

function skillRows(skills, changes, keys) {
    return keys.map((key) => `
        <div class="skill-line">
            <span>${escapeHtml(skillNames[key] || key)}</span>
            <strong class="${skillChangeClass(changes?.[key])}">${skills[key] ?? '-'}</strong>
        </div>
    `).join('');
}

function skillGrid(skills) {
    return `
        <div class="skill-grid">
            ${skillRows(skills, {}, leftSkills)}
            ${skillRows(skills, {}, rightSkills)}
        </div>
    `;
}

function deltaPills(changes) {
    const pills = skillLabels
        .filter(([key]) => typeof changes?.[key] === 'number' && changes[key] !== 0)
        .map(([key, label]) => {
            const value = changes[key];
            const sign = value > 0 ? '+' : '-';
            const klass = value > 0 ? 'positive' : 'negative';
            return `<span class="delta-pill ${klass}">${label} ${sign}${Math.abs(value)}</span>`;
        });
    return pills.length ? pills.join('') : '<span class="no-change">No change</span>';
}

// ---------- Player detail ----------
async function openPlayerDetail(playerId) {
    const player = findPlayer(playerId) || { id: playerId, info: {} };
    document.querySelector('#detail-name').textContent = fullName(player);
    document.querySelector('#players-view').classList.add('hidden');
    document.querySelector('#detail-view').classList.remove('hidden');
    document.querySelector('#detail-content').innerHTML = '<div class="placeholder">Loading training history...</div>';
    switchTab('history');
    try {
        const response = await getJson(api.playerTraining(playerId));
        const reports = response.reports || [];
        renderDetail(reports, player);
    } catch (error) {
        document.querySelector('#detail-content').innerHTML = `<div class="empty">${escapeHtml(error.message)}</div>`;
    }
}

function switchTab(tab) {
    state.activeTab = tab;
    document.querySelectorAll('.tab').forEach((el) => {
        el.classList.toggle('active', el.dataset.tab === tab);
    });
    const content = document.querySelector('#detail-content');
    const reports = content._reports || [];
    const player = content._player || {};
    renderDetail(reports, player);
}

function renderDetail(reports, player) {
    const content = document.querySelector('#detail-content');
    content._reports = reports;
    content._player = player;
    const tab = state.activeTab;
    if (tab === 'player') {
        content.innerHTML = renderPlayerHistory(reports, player);
    } else {
        content.innerHTML = renderTrainingHistory(reports);
    }
}

// ---------- Player History tab ----------
function renderPlayerHistory(reports, player) {
    const current = player.info?.skills || {};
    const stats = trainingStats(reports, player);
    const r = stats.firstReport;
    const firstDate = r ? formatDate(r.day?.date?.value || r.date?.value) : '-';
    const firstWeek = r ? `S${r.day?.season || '?'}/${r.day?.seasonWeek || '?'}` : '-';
    return `
        <section class="card">
            <h3>Current Skills</h3>
            <p class="muted">Age ${age(player)} | ID ${player.id}</p>
            ${skillGrid(current)}
        </section>
        <section class="card">
            <h3>When He Arrived</h3>
            <p class="muted">First week ${firstWeek} | ${firstDate} | Age ${stats.ageAtFirst ?? '?'}</p>
            ${r ? skillGrid(r.skills || {}) : '<div class="empty">No training history.</div>'}
        </section>
        <section class="card">
            <h3>Totals</h3>
            ${totalsTable(stats)}
        </section>
    `;
}

function totalsTable(stats) {
    const rows = ['stamina', 'pace', 'technique', 'passing', 'keeper', 'defending', 'playmaking', 'striker']
        .filter((key) => stats.perSkill[key])
        .map((key) => {
            const s = stats.perSkill[key];
            return `
                <div class="stat-row">
                    <span class="lbl">${escapeHtml(skillNames[key])}</span>
                    <span class="num dt">${s.dtWeeks}</span>
                    <span class="num gt">${s.gtWeeks}</span>
                    <span class="num inj">${s.injuryWeeks}</span>
                    <span class="num jmp">${s.jumps}</span>
                    <span class="num">${s.jumps > 0 ? (s.totalCredit / s.jumps).toFixed(2) : '-'}</span>
                </div>
            `;
        }).join('');
    return `
        <div class="stat-grid">
            <div class="stat-row head">
                <span class="lbl">Skill</span><span class="num">DT</span><span class="num">GT</span><span class="num">Inj</span><span class="num">Jmp</span><span class="num">Avg</span>
            </div>
            ${rows}
        </div>
    `;
}

function trainingStats(reports, player) {
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
            const injuredKey = skillKeys.find((k) => k === dtSkill);
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
    let ageAtFirst = null;
    if (firstReport && reports.length > 0) {
        const currentAge = Math.floor(age(player) || 0);
        const last = reports[0];
        const currentSeason = last.day?.season || 0;
        const firstSeason = firstReport.day?.season || 0;
        ageAtFirst = currentAge - (currentSeason - firstSeason);
    }
    return { perSkill, firstReport, ageAtFirst };
}

// ---------- Training History tab ----------
function renderTrainingHistory(reports) {
    if (!reports.length) {
        return '<div class="empty">No previous training for this player.</div>';
    }
    const html = `
        <div class="history-list">
            ${reports.map(historyItem).join('')}
        </div>
    `;
    setTimeout(() => {
        document.querySelectorAll('.history-item').forEach((item) => {
            const head = item.querySelector('.history-head');
            head.addEventListener('click', () => item.classList.toggle('open'));
        });
    }, 0);
    return html;
}

function historyItem(report, index) {
    const kind = report.kind?.name;
    const intensity = report.intensity ?? 100;
    const badge = intensity <= 0
        ? '<span class="training-badge badge-inj" title="Injured / no training">✖</span>'
        : kind === 'individual'
            ? '<span class="training-badge badge-adv" title="Advanced training">★</span>'
            : kind === 'formation'
                ? '<span class="training-badge badge-ft" title="Formation training">FT</span>'
                : '';
    const source = report.source === 'sktables'
        ? '<span class="training-badge badge-src">SkTables</span>'
        : '<span class="training-badge badge-sokker">Sokker</span>';
    const type = escapeHtml(report.type?.name || report.kind?.name || report.source || '-');
    return `
        <div class="history-item" data-report-index="${index}">
            <button class="history-head">
                <span class="history-week">S${report.day?.season ?? '-'}/${report.day?.seasonWeek ?? report.week ?? '-'}</span>
                <span class="history-date">${formatDate(report.day?.date?.value || report.date?.value)}</span>
                ${badge}
                <span class="history-type">${type}</span>
                <span class="history-int">${intensity ?? '-'}%${report.injury?.daysRemaining ? ` (+${report.injury.daysRemaining}d)` : ''}</span>
            </button>
            <div class="history-changes"><div class="delta-strip">${deltaPills(report.skillsChange || {})}</div></div>
            <div class="history-sub">${source}</div>
            <div class="history-detail">
                <div class="detail-title">Skills after this week</div>
                ${skillGrid(report.skills || {})}
            </div>
        </div>
    `;
}

// ---------- Helpers ----------
function findPlayer(playerId) {
    return state.players.find((player) => Number(player.id) === Number(playerId));
}

function fullName(player) {
    return player?.info?.name?.full || player?.name?.full || `Player ${player?.id ?? ''}`;
}

function age(player) {
    return player?.info?.characteristics?.age ?? player?.characteristics?.age ?? '-';
}

function formatDate(dateStr) {
    if (!dateStr) return '-';
    const parts = dateStr.split(/[-\s]/);
    if (parts.length >= 3) {
        return `${parts[2].padStart(2, '0')}-${parts[1].padStart(2, '0')}-${parts[0]}`;
    }
    return dateStr;
}

function skillChangeClass(value) {
    if (value > 0) return 'positive';
    if (value < 0) return 'negative';
    return '';
}

function resolveSokkerAsset(url) {
    if (!url) return null;
    if (url.startsWith('http://') || url.startsWith('https://')) return url;
    if (url.startsWith('/')) return `https://sokker.org${url}`;
    return url;
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

loadLogin();