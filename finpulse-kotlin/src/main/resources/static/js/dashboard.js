(function () {
    function renderTrending(tickers) {
        const tbody = document.getElementById('trending-body');
        if (!tbody) return;
        if (!tickers.length) {
            tbody.innerHTML = '<tr><td colspan="3" class="text-secondary text-center py-3">No data yet.</td></tr>';
            return;
        }
        tbody.innerHTML = tickers.map((t, i) => `
            <tr>
                <td class="text-secondary">${i + 1}</td>
                <td><a href="/ticker/${t.symbol}" class="text-decoration-none fw-semibold text-info">${t.symbol}</a></td>
                <td>${t.mentionCount}</td>
            </tr>`).join('');
    }

    function renderMovers(movers) {
        const tbody = document.getElementById('movers-body');
        if (!tbody) return;
        if (!movers.length) {
            tbody.innerHTML = '<tr><td colspan="4" class="text-secondary text-center py-3">No movers data yet.</td></tr>';
            return;
        }
        tbody.innerHTML = movers.map(m => {
            const cls = m.shift >= 0 ? 'text-success' : 'text-danger';
            const sign = m.shift >= 0 ? '+' : '';
            return `
            <tr>
                <td><a href="/ticker/${m.symbol}" class="text-decoration-none fw-semibold text-info">${m.symbol}</a></td>
                <td>${m.recentScore.toFixed(3)}</td>
                <td>${m.previousScore.toFixed(3)}</td>
                <td><span class="${cls}">${sign}${m.shift.toFixed(3)}</span></td>
            </tr>`;
        }).join('');
    }

    function setRefreshing(on) {
        const btn = document.getElementById('btn-refresh');
        if (btn) btn.disabled = on;
    }

    function refresh() {
        setRefreshing(true);
        Promise.all([
            fetch('/api/v1/tickers/trending?limit=10').then(r => r.json()),
            fetch('/api/v1/tickers/movers?limit=10').then(r => r.json())
        ]).then(([trendingData, moversData]) => {
            renderTrending(trendingData.tickers || []);
            renderMovers(moversData.movers || []);
        }).catch(() => {}).finally(() => setRefreshing(false));
    }

    refresh();
    setInterval(refresh, 60000);

    document.getElementById('btn-refresh')?.addEventListener('click', refresh);
})();
