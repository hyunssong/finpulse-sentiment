(function () {
    const body = document.body;
    const symbol = body.dataset.symbol;
    const limit = parseInt(body.dataset.limit, 10) || 20;
    const total = parseInt(body.dataset.total, 10) || 0;
    let offset = parseInt(body.dataset.offset, 10) || 0;

    // --- Chart.js ---
    const raw = window.sentimentData || [];
    if (raw.length > 0) {
        const ctx = document.getElementById('sentimentChart');
        new Chart(ctx, {
            type: 'line',
            data: {
                labels: raw.map(d => d.date),
                datasets: [{
                    label: 'Avg Sentiment Score',
                    data: raw.map(d => d.avgScore),
                    borderColor: '#4caf82',
                    backgroundColor: 'rgba(76,175,130,0.1)',
                    borderWidth: 2,
                    pointRadius: 3,
                    fill: true,
                    tension: 0.3
                }]
            },
            options: {
                scales: {
                    x: { ticks: { color: '#9aa0b2' }, grid: { color: '#1e2130' } },
                    y: { min: 0, max: 1, ticks: { color: '#9aa0b2' }, grid: { color: '#1e2130' } }
                },
                plugins: { legend: { labels: { color: '#e0e0e0' } } }
            }
        });
    }

    // --- Sentiment breakdown chart (positive / negative / neutral per day) ---
    fetch(`/api/v1/tickers/${symbol}/sentiment`)
        .then(r => r.json())
        .then(data => {
            const points = data.data || [];
            if (!points.length) {
                const empty = document.getElementById('breakdown-empty');
                if (empty) empty.style.display = '';
                return;
            }
            const ctx2 = document.getElementById('breakdownChart');
            new Chart(ctx2, {
                type: 'bar',
                data: {
                    labels: points.map(d => d.date),
                    datasets: [
                        { label: 'Positive', data: points.map(d => d.positive),  backgroundColor: 'rgba(63,185,80,0.75)' },
                        { label: 'Negative', data: points.map(d => d.negative),  backgroundColor: 'rgba(248,81,73,0.75)' },
                        { label: 'Neutral',  data: points.map(d => d.neutral),   backgroundColor: 'rgba(139,148,158,0.75)' }
                    ]
                },
                options: {
                    responsive: true,
                    scales: {
                        x: { stacked: true, ticks: { color: '#8b949e' }, grid: { color: '#21262d' } },
                        y: { stacked: true, ticks: { color: '#8b949e', stepSize: 1 }, grid: { color: '#21262d' } }
                    },
                    plugins: { legend: { labels: { color: '#c9d1d9' } } }
                }
            });
        })
        .catch(err => console.error('Sentiment breakdown fetch failed:', err));

    // --- Pagination ---
    const container = document.getElementById('articles-container');
    const btnPrev = document.getElementById('btn-prev');
    const btnNext = document.getElementById('btn-next');
    const pageInfo = document.getElementById('page-info');
    const countEl = document.getElementById('articles-count');

    function updateControls() {
        btnPrev.disabled = offset <= 0;
        btnNext.disabled = offset + limit >= total;
        const page = Math.floor(offset / limit) + 1;
        const pages = Math.max(1, Math.ceil(total / limit));
        pageInfo.textContent = `Page ${page} of ${pages}`;
        if (countEl) countEl.textContent = `${total} articles`;
    }

    function sentimentClass(s) {
        if (s === 'positive') return 'sentiment-positive';
        if (s === 'negative') return 'sentiment-negative';
        return 'sentiment-neutral';
    }

    function loadPage(newOffset) {
        fetch(`/api/v1/tickers/${symbol}/articles?offset=${newOffset}&limit=${limit}`)
            .then(r => r.json())
            .then(data => {
                offset = newOffset;
                container.innerHTML = data.data.length === 0
                    ? '<div class="list-group-item text-secondary text-center py-4">No articles found.</div>'
                    : data.data.map(a => `
                        <a href="${a.url}" target="_blank"
                           class="list-group-item list-group-item-action article-card">
                            <div class="d-flex justify-content-between align-items-start">
                                <div class="me-3">
                                    <div class="fw-semibold mb-1">${a.title}</div>
                                    <small class="text-secondary">${a.summary || ''}</small>
                                </div>
                                <div class="text-end text-nowrap ms-2">
                                    <div class="small ${sentimentClass(a.sentiment)}">${a.sentiment.charAt(0).toUpperCase() + a.sentiment.slice(1)}</div>
                                    <div class="small text-secondary">${a.sentimentScore.toFixed(3)}</div>
                                    <div class="small text-secondary mt-1">${a.source}</div>
                                </div>
                            </div>
                        </a>`).join('');
                updateControls();
            });
    }

    btnPrev.addEventListener('click', () => loadPage(Math.max(0, offset - limit)));
    btnNext.addEventListener('click', () => loadPage(offset + limit));

    updateControls();
})();
