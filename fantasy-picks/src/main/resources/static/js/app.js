/* ═══════════════════════════════════════════════════════════════
   FantasyPicks — Frontend Application Logic
   Handles source selection, data fetching, table rendering,
   filtering, sorting, and UI state management.
   ═══════════════════════════════════════════════════════════════ */

'use strict';

// ── CONFIG ────────────────────────────────────────────────────
const API_BASE = '';  // Same-origin — Spring Boot serves both the API and static files

// Available data sources (mirrors what the backend will support)
const DATA_SOURCES = [
    { id: 'espn',       name: 'ESPN',           desc: 'ESPN Fantasy Football rankings and projections.' },
    { id: 'sleeper',    name: 'Sleeper',        desc: 'Sleeper app consensus rankings.' },
    { id: 'yahoo',      name: 'Yahoo',          desc: 'Yahoo Fantasy expert rankings.' },
    { id: 'nfl',        name: 'NFL.com',        desc: 'Official NFL Fantasy player rankings.' },
    { id: 'fantasypros', name: 'FantasyPros',   desc: 'FantasyPros expert consensus rankings (ECR).' },
];

// NFL Teams for the team filter dropdown
const NFL_TEAMS = [
    'ARI','ATL','BAL','BUF','CAR','CHI','CIN','CLE',
    'DAL','DEN','DET','GB','HOU','IND','JAX','KC',
    'LAC','LAR','LV','MIA','MIN','NE','NO','NYG',
    'NYJ','PHI','PIT','SEA','SF','TB','TEN','WAS'
];

// ── STATE ─────────────────────────────────────────────────────
let state = {
    selectedSources: new Set(),
    selectedYear: new Date().getFullYear(),
    players: [],
    filteredPlayers: [],
    draftedPlayers: new Set(),
    sortColumn: 'overallRank',
    sortDir: 'asc',
    searchQuery: '',
    filterPosition: 'ALL',
    filterTeam: 'ALL',
    isLoading: false,
};

// ── DOM REFERENCES ────────────────────────────────────────────
const $  = (sel) => document.querySelector(sel);
const $$ = (sel) => document.querySelectorAll(sel);

const dom = {
    sourceCards:     $('#source-cards'),
    searchInput:     $('#input-search'),
    filterYear:      $('#filter-year'),
    filterPosition:  $('#filter-position'),
    filterTeam:      $('#filter-team'),
    tableHeaderRow:  $('#table-header-row'),
    tableBody:       $('#rankings-body'),
    tableWrapper:    $('.table-wrapper'),
    stateLoading:    $('#state-loading'),
    stateEmpty:      $('#state-empty'),
    stateError:      $('#state-error'),
    errorMessage:    $('#error-message'),
    btnRefresh:      $('#btn-refresh-data'),
    statPlayerCount: $('#stat-player-count'),
    statSourceCount: $('#stat-source-count'),
    statLastUpdated: $('#stat-last-updated'),
    statSeason:      $('#stat-season'),
    toastContainer:  $('#toast-container'),
};

// ═══════════════════════════════════════════════════════════════
// INITIALIZATION
// ═══════════════════════════════════════════════════════════════
document.addEventListener('DOMContentLoaded', () => {
    renderSourceCards();
    populateYearFilter();
    populateTeamFilter();
    bindEvents();
    updateUIState();
});

// ═══════════════════════════════════════════════════════════════
// SOURCE CARDS
// ═══════════════════════════════════════════════════════════════
function renderSourceCards() {
    dom.sourceCards.innerHTML = DATA_SOURCES.map(src => `
        <div class="source-card" data-source-id="${src.id}" id="source-card-${src.id}">
            <div class="source-card-check">
                <svg viewBox="0 0 12 12"><path d="M10 3L4.5 8.5 2 6"/></svg>
            </div>
            <div class="source-card-content">
                <div class="source-card-name">${src.name}</div>
                <div class="source-card-desc">${src.desc}</div>
            </div>
        </div>
    `).join('');
}

function toggleSource(sourceId) {
    if (state.selectedSources.has(sourceId)) {
        state.selectedSources.delete(sourceId);
    } else {
        state.selectedSources.add(sourceId);
    }
    // Update card visual
    const card = $(`#source-card-${sourceId}`);
    if (card) card.classList.toggle('selected', state.selectedSources.has(sourceId));

    // Update stats
    dom.statSourceCount.textContent = state.selectedSources.size;

    // Rebuild table headers to reflect active sources
    rebuildTableHeaders();
    renderTable();
}

// ═══════════════════════════════════════════════════════════════
// TEAM FILTER DROPDOWN
// ═══════════════════════════════════════════════════════════════
function populateTeamFilter() {
    const frag = document.createDocumentFragment();
    NFL_TEAMS.forEach(team => {
        const opt = document.createElement('option');
        opt.value = team;
        opt.textContent = team;
        frag.appendChild(opt);
    });
    dom.filterTeam.appendChild(frag);
}

/**
 * Populate the year/season dropdown with years from 2020 to the current year.
 * The current year is selected by default.
 */
function populateYearFilter() {
    const currentYear = new Date().getFullYear();
    const startYear = 2020;
    const frag = document.createDocumentFragment();

    for (let y = currentYear; y >= startYear; y--) {
        const opt = document.createElement('option');
        opt.value = y;
        opt.textContent = `${y} Season`;
        if (y === currentYear) opt.selected = true;
        frag.appendChild(opt);
    }
    dom.filterYear.appendChild(frag);
    state.selectedYear = currentYear;
}

// ═══════════════════════════════════════════════════════════════
// EVENTS
// ═══════════════════════════════════════════════════════════════
function bindEvents() {
    // Source card clicks
    dom.sourceCards.addEventListener('click', (e) => {
        const card = e.target.closest('.source-card');
        if (card) toggleSource(card.dataset.sourceId);
    });

    // Refresh button
    dom.btnRefresh.addEventListener('click', fetchPlayerData);

    // Search input (debounced)
    let searchTimer;
    dom.searchInput.addEventListener('input', () => {
        clearTimeout(searchTimer);
        searchTimer = setTimeout(() => {
            state.searchQuery = dom.searchInput.value.trim().toLowerCase();
            applyFilters();
            renderTable();
        }, 200);
    });

    // Position filter
    dom.filterPosition.addEventListener('change', () => {
        state.filterPosition = dom.filterPosition.value;
        applyFilters();
        renderTable();
    });

    // Team filter
    dom.filterTeam.addEventListener('change', () => {
        state.filterTeam = dom.filterTeam.value;
        applyFilters();
        renderTable();
    });

    // Year filter — changing the year triggers a new data fetch
    dom.filterYear.addEventListener('change', () => {
        state.selectedYear = parseInt(dom.filterYear.value, 10);
        // If we already have data loaded, auto-refresh with the new year
        if (state.selectedSources.size > 0 && state.players.length > 0) {
            fetchPlayerData();
        }
    });

    // Draft checkbox clicks
    dom.tableBody.addEventListener('change', (e) => {
        if (e.target.classList.contains('draft-checkbox')) {
            const playerName = e.target.dataset.playerName;
            if (e.target.checked) {
                state.draftedPlayers.add(playerName);
                e.target.closest('tr').classList.add('row-drafted');
            } else {
                state.draftedPlayers.delete(playerName);
                e.target.closest('tr').classList.remove('row-drafted');
            }
        }
    });

    // Table header sort clicks
    dom.tableHeaderRow.addEventListener('click', (e) => {
        const th = e.target.closest('th[data-sort]');
        if (!th) return;
        const col = th.dataset.sort;
        if (state.sortColumn === col) {
            state.sortDir = state.sortDir === 'asc' ? 'desc' : 'asc';
        } else {
            state.sortColumn = col;
            state.sortDir = 'asc';
        }
        applyFilters();
        renderTable();
        updateSortIndicators();
    });

    // Nav links — smooth scrolling to sections
    $$('.nav-link').forEach(link => {
        link.addEventListener('click', (e) => {
            e.preventDefault();
            $$('.nav-link').forEach(l => l.classList.remove('active'));
            link.classList.add('active');
            const sectionId = `section-${link.dataset.section}`;
            const section = document.getElementById(sectionId);
            if (section) section.scrollIntoView({ behavior: 'smooth', block: 'start' });
        });
    });
}

// ═══════════════════════════════════════════════════════════════
// DATA FETCHING
// ═══════════════════════════════════════════════════════════════
async function fetchPlayerData() {
    if (state.selectedSources.size === 0) {
        showToast('Please select at least one data source.', 'info');
        return;
    }

    setLoading(true);

    try {
        const sourcesParam = [...state.selectedSources].join(',');
        const yearParam = state.selectedYear || new Date().getFullYear();
        const response = await fetch(`${API_BASE}/api/players?sources=${encodeURIComponent(sourcesParam)}&year=${yearParam}`);

        if (!response.ok) {
            throw new Error(`Server responded with ${response.status}`);
        }

        const data = await response.json();

        /*
         * Expected response shape:
         * {
         *   players: [
         *     {
         *       name: "Patrick Mahomes",
         *       position: "QB",
         *       team: "KC",
         *       overallRank: 1,
         *       rankings: {
         *         espn: 2,
         *         sleeper: 1,
         *         ...
         *       }
         *     },
         *     ...
         *   ],
         *   lastUpdated: "2026-04-25T20:00:00Z"
         * }
         */

        state.players = data.players || [];
        applyFilters();
        renderTable();
        updateStats(data);

        showToast(`Loaded ${state.players.length} players from ${state.selectedSources.size} source(s) for ${state.selectedYear} season.`, 'success');
    } catch (err) {
        console.error('Failed to fetch player data:', err);
        showError(err.message || 'Could not load player data.');
        showToast('Failed to fetch player data. Is the backend running?', 'error');
    } finally {
        setLoading(false);
    }
}

// ═══════════════════════════════════════════════════════════════
// FILTERING & SORTING
// ═══════════════════════════════════════════════════════════════
function applyFilters() {
    let result = [...state.players];

    // Text search
    if (state.searchQuery) {
        result = result.filter(p =>
            p.name.toLowerCase().includes(state.searchQuery) ||
            p.team.toLowerCase().includes(state.searchQuery) ||
            p.position.toLowerCase().includes(state.searchQuery)
        );
    }

    // Position filter
    if (state.filterPosition !== 'ALL') {
        result = result.filter(p => p.position === state.filterPosition);
    }

    // Team filter
    if (state.filterTeam !== 'ALL') {
        result = result.filter(p => p.team === state.filterTeam);
    }

    // Sort
    result.sort((a, b) => {
        let valA, valB;

        if (state.sortColumn === 'name' || state.sortColumn === 'position' || state.sortColumn === 'team') {
            valA = (a[state.sortColumn] || '').toLowerCase();
            valB = (b[state.sortColumn] || '').toLowerCase();
            return state.sortDir === 'asc' ? valA.localeCompare(valB) : valB.localeCompare(valA);
        }

        // Numeric columns (overallRank, source-specific ranks)
        if (state.sortColumn.startsWith('source_')) {
            const srcId = state.sortColumn.replace('source_', '');
            valA = a.rankings?.[srcId] ?? 9999;
            valB = b.rankings?.[srcId] ?? 9999;
        } else {
            valA = a[state.sortColumn] ?? 9999;
            valB = b[state.sortColumn] ?? 9999;
        }

        return state.sortDir === 'asc' ? valA - valB : valB - valA;
    });

    state.filteredPlayers = result;
}

// ═══════════════════════════════════════════════════════════════
// TABLE RENDERING
// ═══════════════════════════════════════════════════════════════
function rebuildTableHeaders() {
    // Remove old source columns
    dom.tableHeaderRow.querySelectorAll('.col-source').forEach(th => th.remove());

    // Insert a column for each selected source, before the "Overall" column
    const overallTh = dom.tableHeaderRow.querySelector('.col-overall');
    const activeSources = DATA_SOURCES.filter(s => state.selectedSources.has(s.id));

    activeSources.forEach(src => {
        const th = document.createElement('th');
        th.className = 'col-source';
        th.dataset.sort = `source_${src.id}`;
        th.textContent = src.name;
        dom.tableHeaderRow.insertBefore(th, overallTh);
    });

    updateSortIndicators();
}

function renderTable() {
    const players = state.filteredPlayers;

    if (state.players.length === 0 && !state.isLoading) {
        dom.tableWrapper.classList.add('hidden');
        dom.stateEmpty.classList.remove('hidden');
        dom.stateError.classList.add('hidden');
        return;
    }

    dom.tableWrapper.classList.remove('hidden');
    dom.stateEmpty.classList.add('hidden');
    dom.stateError.classList.add('hidden');

    if (players.length === 0) {
        dom.tableBody.innerHTML = `
            <tr>
                <td colspan="100" style="text-align:center; padding: 2rem; color: var(--clr-text-muted);">
                    No players match your filters.
                </td>
            </tr>`;
        return;
    }

    const activeSources = DATA_SOURCES.filter(s => state.selectedSources.has(s.id));

    dom.tableBody.innerHTML = players.map((player, idx) => {
        const sourceColumns = activeSources.map(src => {
            const rank = player.rankings?.[src.id];
            return `<td class="source-rank-cell">${rank != null ? rank : '—'}</td>`;
        }).join('');

        const isDrafted = state.draftedPlayers.has(player.name);

        return `
            <tr class="${isDrafted ? 'row-drafted' : ''}">
                <td class="draft-cell"><input type="checkbox" class="draft-checkbox" data-player-name="${escapeHTML(player.name)}" ${isDrafted ? 'checked' : ''}></td>
                <td class="rank-cell">${player.overallRank ?? (idx + 1)}</td>
                <td class="col-player">${escapeHTML(player.name)}</td>
                <td class="col-pos"><span class="badge-pos ${player.position}">${player.position}</span></td>
                <td class="team-cell">${player.team}</td>
                ${sourceColumns}
                <td class="rank-cell">${player.overallRank ?? '—'}</td>
            </tr>`;
    }).join('');
}

function updateSortIndicators() {
    dom.tableHeaderRow.querySelectorAll('th').forEach(th => {
        th.classList.remove('sort-asc', 'sort-desc');
        if (th.dataset.sort === state.sortColumn) {
            th.classList.add(state.sortDir === 'asc' ? 'sort-asc' : 'sort-desc');
        }
    });
}

// ═══════════════════════════════════════════════════════════════
// UI STATE HELPERS
// ═══════════════════════════════════════════════════════════════
function updateUIState() {
    dom.statPlayerCount.textContent = state.players.length || '—';
    dom.statSourceCount.textContent = state.selectedSources.size || '—';
    dom.statLastUpdated.textContent = '—';
    dom.statSeason.textContent = state.selectedYear;
    renderTable();
}

function setLoading(isLoading) {
    state.isLoading = isLoading;
    dom.btnRefresh.disabled = isLoading;
    dom.btnRefresh.classList.toggle('loading', isLoading);

    if (isLoading) {
        dom.stateLoading.classList.remove('hidden');
        dom.stateEmpty.classList.add('hidden');
        dom.stateError.classList.add('hidden');
        dom.tableWrapper.classList.add('hidden');
    } else {
        dom.stateLoading.classList.add('hidden');
    }
}

function updateStats(data) {
    dom.statPlayerCount.textContent = state.players.length;
    dom.statSourceCount.textContent = state.selectedSources.size;
    dom.statSeason.textContent = state.selectedYear;

    if (data.lastUpdated) {
        const d = new Date(data.lastUpdated);
        dom.statLastUpdated.textContent = d.toLocaleString();
    } else {
        dom.statLastUpdated.textContent = new Date().toLocaleString();
    }
}

function showError(message) {
    dom.errorMessage.textContent = message;
    dom.stateError.classList.remove('hidden');
    dom.stateEmpty.classList.add('hidden');
    dom.tableWrapper.classList.add('hidden');
}

// ═══════════════════════════════════════════════════════════════
// TOAST NOTIFICATIONS
// ═══════════════════════════════════════════════════════════════
function showToast(message, type = 'info') {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;
    dom.toastContainer.appendChild(toast);

    setTimeout(() => {
        toast.classList.add('fade-out');
        toast.addEventListener('animationend', () => toast.remove());
    }, 3500);
}

// ═══════════════════════════════════════════════════════════════
// UTILITIES
// ═══════════════════════════════════════════════════════════════
function escapeHTML(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}
