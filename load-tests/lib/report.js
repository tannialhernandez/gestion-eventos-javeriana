export function reportPath(fileName) {
  return `${__ENV.REPORT_DIR || 'load-tests/reports'}/${fileName}`;
}

function metricValue(metric, key) {
  if (!metric || !metric.values || metric.values[key] === undefined) return '-';
  const value = metric.values[key];
  return typeof value === 'number' ? value.toFixed(2) : String(value);
}

export function textSummary(data) {
  const duration = metricValue(data.metrics.http_req_duration, 'avg');
  const p95 = metricValue(data.metrics.http_req_duration, 'p(95)');
  const p99 = metricValue(data.metrics.http_req_duration, 'p(99)');
  const failed = metricValue(data.metrics.http_req_failed, 'rate');

  return [
    '',
    'K6 load-test summary',
    `  checks...............: ${metricValue(data.metrics.checks, 'rate')}`,
    `  http_req_duration avg: ${duration} ms`,
    `  http_req_duration p95: ${p95} ms`,
    `  http_req_duration p99: ${p99} ms`,
    `  http_req_failed rate.: ${failed}`,
    '',
  ].join('\n');
}

function thresholdRows(data) {
  return Object.entries(data.metrics)
    .filter(([, metric]) => metric.thresholds)
    .flatMap(([name, metric]) => Object.entries(metric.thresholds)
      .map(([threshold, result]) => `
        <tr>
          <td>${name}</td>
          <td><code>${threshold}</code></td>
          <td class="${result.ok ? 'ok' : 'fail'}">${result.ok ? 'OK' : 'FAIL'}</td>
        </tr>`))
    .join('');
}

function metricRows(data) {
  return Object.entries(data.metrics)
    .filter(([, metric]) => metric.values)
    .map(([name, metric]) => `
      <tr>
        <td>${name}</td>
        <td>${metricValue(metric, 'count')}</td>
        <td>${metricValue(metric, 'rate')}</td>
        <td>${metricValue(metric, 'avg')}</td>
        <td>${metricValue(metric, 'p(95)')}</td>
        <td>${metricValue(metric, 'p(99)')}</td>
      </tr>`)
    .join('');
}

export function htmlReport(data, title) {
  const generatedAt = new Date().toISOString();
  return `<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <title>${title}</title>
  <style>
    body { font-family: Inter, system-ui, sans-serif; margin: 32px; color: #172033; }
    h1, h2 { margin-bottom: 8px; }
    .grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin: 24px 0; }
    .card { border: 1px solid #d8dee8; border-radius: 6px; padding: 14px; background: #f8fafc; }
    .label { color: #5b677a; font-size: 12px; text-transform: uppercase; }
    .value { font-size: 24px; font-weight: 700; margin-top: 6px; }
    table { border-collapse: collapse; width: 100%; margin: 18px 0 28px; }
    th, td { border-bottom: 1px solid #e6ebf2; padding: 10px 8px; text-align: left; }
    th { background: #f1f5f9; font-size: 13px; }
    .ok { color: #0f7b3b; font-weight: 700; }
    .fail { color: #b42318; font-weight: 700; }
    code { background: #eef2f7; padding: 2px 5px; border-radius: 4px; }
  </style>
</head>
<body>
  <h1>${title}</h1>
  <p>Generado: ${generatedAt}</p>
  <div class="grid">
    <div class="card"><div class="label">p95 HTTP</div><div class="value">${metricValue(data.metrics.http_req_duration, 'p(95)')} ms</div></div>
    <div class="card"><div class="label">p99 HTTP</div><div class="value">${metricValue(data.metrics.http_req_duration, 'p(99)')} ms</div></div>
    <div class="card"><div class="label">Error rate</div><div class="value">${metricValue(data.metrics.http_req_failed, 'rate')}</div></div>
    <div class="card"><div class="label">Checks</div><div class="value">${metricValue(data.metrics.checks, 'rate')}</div></div>
  </div>
  <h2>Thresholds</h2>
  <table>
    <thead><tr><th>Metrica</th><th>Threshold</th><th>Resultado</th></tr></thead>
    <tbody>${thresholdRows(data)}</tbody>
  </table>
  <h2>Metricas</h2>
  <table>
    <thead><tr><th>Metrica</th><th>count</th><th>rate</th><th>avg</th><th>p95</th><th>p99</th></tr></thead>
    <tbody>${metricRows(data)}</tbody>
  </table>
</body>
</html>`;
}
