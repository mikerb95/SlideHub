(function () {
    const POLL_MS = Math.max(1000, Math.min(3000, Number(window.statusConfig?.pollIntervalMs || 2000)));
    const tbody = document.getElementById('checks-body');
    const errorBox = document.getElementById('status-error');
    const pollText = document.getElementById('poll-interval');
    const lastUpdatedText = document.getElementById('last-updated');

    pollText.textContent = String(POLL_MS);

    function formatDate(value) {
        if (!value) return '--';
        const d = new Date(value);
        if (Number.isNaN(d.getTime())) return '--';
        return d.toLocaleTimeString();
    }

    function badge(status) {
        const ok = status === 'ok';
        const bg = ok ? 'var(--sh-green)' : 'var(--sh-red)';
        const fg = ok ? 'var(--sh-deep)' : '#fff';
        return `<span style="display:inline-block;padding:2px 8px;border-radius:999px;background:${bg};color:${fg};font-size:11px;font-family:var(--sh-font-mono);font-weight:700;">${status.toUpperCase()}</span>`;
    }

    function renderRows(checks) {
        if (!checks || checks.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" class="py-4 text-[var(--sh-text-3)] font-mono text-xs">Sin datos</td></tr>';
            return;
        }

        tbody.innerHTML = checks.map((check) => {
            const latency = check.latencyMs === null || check.latencyMs === undefined ? '--' : check.latencyMs;
            const detail = (check.detail || '--').replace(/</g, '&lt;').replace(/>/g, '&gt;');
            return `
                <tr style="border-bottom:1px solid var(--sh-border);">
                    <td class="py-2 text-[var(--sh-text-2)] font-mono text-xs">${check.name}</td>
                    <td class="py-2">${badge(check.status)}</td>
                    <td class="py-2 text-[var(--sh-text-2)] font-mono text-xs">${latency}</td>
                    <td class="py-2 text-[var(--sh-text-3)] font-mono text-xs">${formatDate(check.lastCheckedAt)}</td>
                    <td class="py-2 text-[var(--sh-text-3)] font-mono text-xs">${detail}</td>
                </tr>
            `;
        }).join('');
    }

    function sanitizeText(value) {
        return String(value || '')
            .replace(/\s+/g, ' ')
            .trim();
    }

    function snippet(value, max = 160) {
        const clean = sanitizeText(value);
        return clean.length > max ? clean.slice(0, max) + '…' : clean;
    }

    async function loadChecks() {
        try {
            errorBox.classList.add('hidden');
            const response = await fetch('/status/api/checks', { cache: 'no-store' });
            const contentType = response.headers.get('content-type') || 'unknown';
            const responseText = await response.text();

            if (!response.ok) {
                throw new Error(`HTTP ${response.status} · content-type=${contentType} · body=${snippet(responseText)}`);
            }

            if (!contentType.toLowerCase().includes('application/json')) {
                throw new Error(`Expected JSON but got ${contentType} · body=${snippet(responseText)}`);
            }

            let payload;
            try {
                payload = JSON.parse(responseText);
            } catch (parseError) {
                throw new Error(`Invalid JSON (${parseError.message}) · body=${snippet(responseText)}`);
            }

            renderRows(payload.checks || []);
            lastUpdatedText.textContent = formatDate(payload.generatedAt);
        } catch (err) {
            errorBox.textContent = 'Error cargando verificaciones: ' + (err.message || 'desconocido');
            errorBox.classList.remove('hidden');
        }
    }

    loadChecks();
    setInterval(loadChecks, POLL_MS);
})();
