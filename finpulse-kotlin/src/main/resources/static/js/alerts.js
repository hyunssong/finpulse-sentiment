(function () {
    const bar = document.getElementById('alert-bar');
    if (!bar) return;

    const es = new EventSource('/api/v1/alerts/stream');

    es.addEventListener('sentiment-alert', function (e) {
        const data = JSON.parse(e.data);
        const el = document.createElement('div');
        el.className = 'alert alert-success alert-dismissible fade show mb-0 rounded-0';
        el.setAttribute('role', 'alert');
        el.innerHTML = `
            <strong>${data.ticker}</strong> — ${data.title}
            <span class="badge bg-light text-dark ms-2">${data.sentiment} ${data.sentimentScore.toFixed(2)}</span>
            <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
        `;
        bar.appendChild(el);
        setTimeout(() => el.remove(), 10000);
    });

    es.onerror = function () { es.close(); };
})();
