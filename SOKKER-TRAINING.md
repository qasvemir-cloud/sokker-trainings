# Sokker Training App — Specification & Progress

## Goal

Accurate skill‑jump predictor and training planner that uses per‑week AT/FT history, age penalties from 26+, skill‑level drag from 13, per‑report credit rates, and live Sokker formation‑training API for the player's current DT skill.

---

## Requirements & Constraints

### Credit rates
- AT (individual training) → full DT credit
- FT (formation training) → credit ÷ 3.5
- Applied **per training report**, not globally

### `isDirectTraining(report, skill)`
- FT always returns `false` (it's GT — general training)
- AT returns `true` only when `report.type.name === skill` AND formation matches `formationSkills`

### High‑skill drag
- Starts at level **13**: `1 + max(0, level - 13) × 0.10`
- Pace gets extra drag:
  - Level 15+: +0.05 per level
  - Level 17+: +0.08 per level

### Age factor
| Age       | Factor |
|-----------|--------|
| ≤17       | 0.70   |
| 18        | 0.82   |
| 19        | 0.92   |
| 20‑25     | 1.00   |
| 26        | 1.03   |
| 27        | 1.07   |
| 28        | 1.14   |
| 29‑30     | 1.22   |
| 31+       | 1.35   |

### Decrease risk (age 29+)
- Formula: `min(0.45, (age - 28) × 0.07)`
- Multiplies jump probability by `(1 - risk)`
- Displayed as ⚠️ warning badge in predictor

### Interval‑based `targetCredit`
- Applies age‑scale: `Math.max(1, ageFactor(age) / ageFactor(recent.age))`
- **Never reduces** target for younger players — only increases for older ones

### Formation (DT) lookup
- Priority: `/training/players` (`state.trainingSetup`) → `/training/current` report
- `playerFormation()` helper checks `state.trainingSetup` by player ID first, then falls back to data object's formation
- `lookupFormation()` extracts `formation.name` from multiple object shapes

### Planner
- Loads `state.trainingSetup` if missing
- Refresh button resets both `trainingSetup` and `formationSkills`
- Stamina **removed** from `allSkills` columns
- Mobile: expandable cards instead of table (<560px)
- Desktop: sortable data table

### Training History
- ★ = AT (advanced/individual training)
- FT = formation training
- ✖ (red) = intensity ≤ 0 (injury / no training)

### UI
- Mobile: hamburger menu below 560px
- Tables use `makeSortable()` for sortable columns
- Tooltips show cur→next level, accumulated/target credit, percentage, remaining need

---

## API Endpoints Used

| Purpose | Endpoint |
|---------|----------|
| Player list | `/sokker/api/players` |
| Training setup (current formation assignments) | `/sokker/api/training/players` |
| Formation skills mapping | `/sokker/api/training/formations` |
| Training reports (history) | `/sokker/api/training/reports` |
| Player report (per‑player history) | `/sokker/api/training/reports/{id}` |
| Last training week | `/sokker/api/training/current` |
| Juniors | `/sokker/api/juniors` |
| Junior graph | `/sokker/api/juniors/{id}/graph` |
| Training summary | `/sokker/api/training/summary` |
| Market | `/sokker/api/market` |
| Matches | `/sokker/api/matches` |
| Alumni | `/sokker/api/alumni` |

---

## Architecture — Core Functions

### Prediction engine
| Function | Purpose |
|----------|---------|
| `accumulatedCredit(reports, skill)` | Sums AT/FT credits per week; AT=full, FT=credit/3.5 |
| `highSkillDrag(level)` | Returns skill-level penalty multiplier |
| `ageFactor(age)` | Returns age penalty multiplier |
| `predictSkill(player, skill, isAdv)` | Predicts next level probability using accumulated credits, drag, age, and decrease risk. Checks `isDirectTraining` for `nextCredit` |

### Formation
| Function | Purpose |
|----------|---------|
| `lookupFormation(data)` | Extracts formation name from various object shapes |
| `playerFormation(data)` | Priority lookup: `state.trainingSetup[id]` → data object |

### Views
| View | Function |
|------|----------|
| Players | `renderPlayers()` |
| Training | `renderTraining()` / `trainingRow()` / `historyRow()` |
| Last Training | `renderLastTraining()` |
| Planner | `renderPlanner()` |
| Predictor | `renderPredictor()` / `predictorCard()` / `predictorListItem()` |
| Juniors | `renderJuniors()` / `juniorRow()` / `juniorCard()` |
| Summary | `renderTrainingSummary()` / `summaryCard()` |
| Market | `renderMarket()` |
| Matches | `renderMatches()` |
| Alumni | `renderAlumni()` |

---

## What Is Done

- [x] Per‑report credit rates (AT=full, FT=÷3.5) in `accumulatedCredit()`
- [x] `predictSkill()` and planner call `accumulatedCredit()` without global divisor
- [x] `nextCredit` uses `isAdv` for future rate estimation
- [x] `highSkillDrag()`: starts at 13, 0.10 multiplier; pace extra drag at 15+ and 17+
- [x] `ageFactor()`: full table from 17 to 31+
- [x] `decreaseRisk`: applied to `nextProbability`, displayed as ⚠️ badge
- [x] Interval‑based `targetCredit` with age‑scale (never reduces target)
- [x] `completedIntervals` stores `age` in each interval object
- [x] `playerFormation()` helper with `state.trainingSetup` priority
- [x] `lookupFormation()` for nested object shape extraction
- [x] Planner loads `state.trainingSetup` if null; refresh resets it
- [x] Training history: injury icon (✖) for intensity ≤ 0
- [x] Planner: stamina removed from `allSkills`
- [x] Mobile planner: expandable cards, tap to reveal
- [x] Mobile hamburger menu <560px
- [x] Sortable tables via `makeSortable()`
- [x] Tooltips on predictor skill rows
- [x] CSS for all badges, mobile views, responsiveness
- [x] Juniors view with Sokker API data merging (`sokker.juniors` + `report.juniors` + `sktables.juniors`)

### Historical validation
- Tested on 52 intervals ≥ 12 across 9 players under age 27
- 21 intervals in ≥91% range (33% within 15 weeks)
- 31 intervals in 60‑90% range (87% within 15 weeks)
- 3 overconfident cases (young prodigies 16‑17 at very high levels)

---

## What Remains

### Short‑term
- [ ] Monitor real training weeks to validate predicted probabilities vs actual jumps
  - Key cases: Šumenko Dabić PM 14→15 (85%), Žika Veljković PM 15→16 (54%)
- [ ] Tune `highSkillDrag`, `ageFactor`, and decrease‑risk percentages based on live data

### Optional / Nice‑to‑have
- [ ] Visual indicator in predictor showing which formation source is used (live vs cached)
- [ ] Export/import of prediction data
- [ ] Dark/light theme toggle
- [ ] Skill jump notification alerts

---

## Key Decisions

1. **Per‑report rate**: Each historical week checked individually rather than a single divisor for all history
2. **High‑skill drag shift**: Start at level 13 (was 14) with multiplier 0.10 — reduces overconfident 99% predictions
3. **Decrease risk**: Probability multiplier displayed as warning badge
4. **Interval age‑scale**: `Math.max(1, ...)` — never reduces target for young prodigies
5. **Formation lookup priority**: `/training/players` (current setup) → `/training/current` report — fixes stale formation when player changed slots
6. **Planner stamina removal**: Stamina doesn't benefit from training in the same way, removed from views

---

## Relevant Files

| File | Purpose |
|------|---------|
| `src/main/resources/static/sokker/js/sokker-fm.js` | All JS logic — predictor, planner, training views, core formulas, helpers |
| `src/main/resources/static/sokker/css/sokker-fm.css` | All styling — badges, mobile views, tables, media queries |
| `src/main/resources/static/sokker/index.html` | Page shell — menu, sidebar, view containers |
| `src/main/resources/static/sokker/data/training‑23‑27/` | 9 players' training JSON, `_current-training*.json`, `_analysis-intervals.json` |

---

## Typical Predictions (age 27, AT)

| Player | Skill | Level → Next | Target | Accumulated | Ratio | Probability |
|--------|-------|-------------|--------|-------------|-------|-------------|
| Šumenko Dabić | Playmaking | 14→15 | 7.22 | 6.00 | 0.853 | **85%** |
| Šumenko Dabić | Pace | 17→18 | — | — | — | **86%** |
| Šumenko Dabić | Passing | 17→18 | — | — | — | **27%** |
| Šumenko Dabić | Technique | 17→18 | — | — | — | **6%** |
| Žika Veljković | Playmaking | 15→16 | 9.87 | 5.17 | 0.540 | **54%** |
| Žika Veljković | Defending | 12→13 | — | — | — | **52%** |
| Žika Veljković | Passing/Tech/Pace | 17→18 | — | — | — | ≤8% |
