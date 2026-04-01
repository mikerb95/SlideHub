(function () {
    const POLL_MS = Math.max(1000, Math.min(3000, Number(window.statusConfig?.pollIntervalMs || 2000)));
    const tbody = document.getElementById('checks-body');
    const errorBox = document.getElementById('status-error');
    const pollText = document.getElementById('poll-interval');
    const lastUpdatedText = document.getElementById('last-updated');
    const diagnosticModal = document.getElementById('diagnostic-modal');

    let checksCache = [];

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

        checksCache = checks;

        tbody.innerHTML = checks.map((check) => {
            const latency = check.latencyMs === null || check.latencyMs === undefined ? '--' : check.latencyMs;
            const detail = (check.detail || '--').replace(/</g, '&lt;').replace(/>/g, '&gt;');
            return `
                <tr style="border-bottom:1px solid var(--sh-border);">
                    <td class="py-2 text-[var(--sh-text-2)] font-mono text-xs">${check.name}</td>
                    <td class="py-2">${badge(check.status)}</td>
                    <td class="py-2 text-[var(--sh-text-2)] font-mono text-xs">${latency}</td>
                    <td class="py-2 text-[var(--sh-text-3)] font-mono text-xs">${formatDate(check.lastCheckedAt)}</td>
                    <td class="py-2 text-[var(--sh-text-3)] font-mono text-xs">
                        <div>${detail}</div>
                        <button type="button"
                            data-service-name="${escapeHtmlAttr(check.name)}"
                            class="sh-btn sh-btn-ghost text-[0.65rem] py-0.5 px-2 mt-1"
                            style="border-color: var(--sh-border-subtle);"
                        >
                            <i class="fa-solid fa-circle-info"></i> Diagnóstico
                        </button>
                    </td>
                </tr>
            `;
        }).join('');
    }

    function escapeHtmlAttr(value) {
        return String(value || '')
            .replace(/&/g, '&amp;')
            .replace(/"/g, '&quot;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');
    }

    function parseConnectionProps(check) {
        const detail = check.detail || '';

        if (detail.startsWith('GET ')) {
            const target = detail.split(' -> ')[0].replace('GET ', '').trim();
            const host = target.includes('/') ? target.split('/')[0] : target;
            const path = target.includes('/') ? target.substring(target.indexOf('/')) : '/';
            return {
                protocol: 'HTTP',
                target,
                host,
                path,
                env: expectedEnvByService(check.name)
            };
        }

        if (detail.startsWith('TCP ')) {
            const target = detail.split(' -> ')[0].replace('TCP ', '').trim();
            const [host, port] = target.split(':');
            return {
                protocol: 'TCP',
                target,
                host: host || '--',
                port: port || '--',
                env: expectedEnvByService(check.name)
            };
        }

        return {
            protocol: '--',
            target: '--',
            host: '--',
            env: expectedEnvByService(check.name)
        };
    }

    function expectedEnvByService(serviceName) {
        const map = {
            'state-service': 'STATE_SERVICE_URL',
            'ai-service': 'AI_SERVICE_URL',
            gateway: 'GATEWAY_URL',
            render: 'RENDER_SERVICE_URL',
            redis: 'REDIS_HOST / REDIS_PORT',
            mongodb: 'MONGODB_URI',
            postgres: 'DATABASE_URL',
            'aws-s3': 'AWS_S3_BUCKET / AWS_REGION'
        };
        return map[serviceName] || '--';
    }

    function deriveDiagnosis(check) {
        const detail = (check.detail || '').toLowerCase();
        const causes = [];
        const fixes = [];

        if (detail.includes('not configured')) {
            causes.push('La variable de entorno requerida no está definida en este servicio.');
            fixes.push(`Configura ${expectedEnvByService(check.name)} y reinicia el servicio.`);
            return { causes, fixes };
        }

        if (detail.includes('http 429')) {
            causes.push('Rate limit del proveedor externo o servicio temporalmente saturado.');
            fixes.push('Reintentar con backoff (ya implementado) y validar cuota del proveedor.');
            fixes.push('Usar token/API key con mayor cuota si aplica.');
        }

        if (detail.includes('connectexception')) {
            causes.push('El host está caído, dormido o la URL/puerto no es alcanzable.');
            fixes.push('Verifica URL/puerto y que el servicio esté levantado.');
            fixes.push('En Render free tier, confirma keep-alive y tráfico reciente.');
        }

        if (detail.includes('unknownhostexception')) {
            causes.push('Hostname inválido o DNS no resolvible desde el contenedor.');
            fixes.push('Corrige el hostname en variables de entorno y vuelve a desplegar.');
        }

        if (detail.includes('timedout') || detail.includes('timeout')) {
            causes.push('Tiempo de respuesta excedido por latencia de red o servicio lento.');
            fixes.push('Aumenta timeout de health-check o reduce carga del servicio destino.');
        }

        if (detail.includes('http 5')) {
            causes.push('El servicio respondió error interno (5xx).');
            fixes.push('Revisar logs del servicio destino para stacktrace y dependencia fallida.');
        }

        if (check.status === 'ok') {
            causes.push('Conectividad y respuesta correctas para el objetivo.');
            fixes.push('Sin acción requerida. Monitorear tendencia de latencia.');
        }

        if (causes.length === 0) {
            causes.push('Fallo no clasificado automáticamente.');
            fixes.push('Inspeccionar logs del servicio y validar variables de entorno asociadas.');
        }

        return { causes, fixes };
    }

    function rowItem(label, value) {
        return `<div class="diag-item"><strong>${label}</strong><span>${escapeHtml(value)}</span></div>`;
    }

    function escapeHtml(value) {
        return String(value || '--')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');
    }

    function openDiagnostic(check) {
        if (!diagnosticModal) {
            return;
        }

        const title = document.getElementById('diag-title');
        const connection = document.getElementById('diag-connection');
        const latency = document.getElementById('diag-latency');
        const causes = document.getElementById('diag-causes');
        const fixes = document.getElementById('diag-fixes');

        const props = parseConnectionProps(check);
        const analysis = deriveDiagnosis(check);

        title.textContent = check.name;

        connection.innerHTML = [
            rowItem('Protocol', props.protocol),
            rowItem('Target', props.target || '--'),
            rowItem('Host', props.host || '--'),
            rowItem('Path/Port', props.path || props.port || '--'),
            rowItem('Config', props.env || '--')
        ].join('');

        latency.innerHTML = [
            rowItem('Status', (check.status || '--').toUpperCase()),
            rowItem('Latency (ms)', check.latencyMs === null || check.latencyMs === undefined ? '--' : String(check.latencyMs)),
            rowItem('Last Check', formatDate(check.lastCheckedAt)),
            rowItem('Detail', check.detail || '--')
        ].join('');

        causes.innerHTML = analysis.causes.map((c) => `<li>${escapeHtml(c)}</li>`).join('');
        fixes.innerHTML = analysis.fixes.map((f) => `<li>${escapeHtml(f)}</li>`).join('');

        diagnosticModal.classList.remove('hidden');
    }

    function closeDiagnostic() {
        if (diagnosticModal) {
            diagnosticModal.classList.add('hidden');
        }
    }

    tbody.addEventListener('click', (event) => {
        const btn = event.target.closest('button[data-service-name]');
        if (!btn) {
            return;
        }

        const serviceName = btn.getAttribute('data-service-name');
        const check = checksCache.find((item) => item.name === serviceName);
        if (check) {
            openDiagnostic(check);
        }
    });

    window.closeStatusDiagnostic = closeDiagnostic;

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
