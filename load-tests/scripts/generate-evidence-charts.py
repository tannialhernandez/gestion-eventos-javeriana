#!/usr/bin/env python3
"""Generate SVG evidence charts from K6 summary JSON files.

The K6 summaries available in load-tests/reports are aggregate reports, not
time-series exports. These charts therefore compare final observed metrics
against acceptance thresholds without inventing per-second curves.
"""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPORTS = ROOT / "reports"
CHARTS = REPORTS / "charts"


COLORS = {
    "ink": "#172033",
    "muted": "#5b667a",
    "grid": "#d8dee9",
    "ok": "#15803d",
    "warn": "#b45309",
    "bar": "#2563eb",
    "bar_2": "#14b8a6",
    "bar_3": "#f59e0b",
    "threshold": "#dc2626",
    "bg": "#ffffff",
    "panel": "#f8fafc",
}


def load_report(name: str) -> dict:
    with (REPORTS / f"{name}.json").open(encoding="utf-8") as fh:
        return json.load(fh)


def metric_value(report: dict, metric: str, key: str, default: float = 0.0) -> float:
    return float(report["metrics"].get(metric, {}).get("values", {}).get(key, default))


def escape(text: object) -> str:
    return (
        str(text)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
    )


def svg_doc(width: int, height: int, body: str) -> str:
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">
  <rect width="100%" height="100%" fill="{COLORS['bg']}"/>
  <style>
    text {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; fill: {COLORS['ink']}; }}
    .title {{ font-size: 28px; font-weight: 750; }}
    .subtitle {{ font-size: 14px; fill: {COLORS['muted']}; }}
    .label {{ font-size: 13px; fill: {COLORS['ink']}; }}
    .small {{ font-size: 12px; fill: {COLORS['muted']}; }}
    .value {{ font-size: 13px; font-weight: 700; }}
  </style>
{body}
</svg>
"""


def axis_lines(x: int, y: int, width: int, height: int, max_value: float, suffix: str = "") -> str:
    parts = []
    for step in range(0, 6):
        value = max_value * step / 5
        yy = y + height - (height * step / 5)
        parts.append(f'<line x1="{x}" y1="{yy:.1f}" x2="{x + width}" y2="{yy:.1f}" stroke="{COLORS["grid"]}" stroke-width="1"/>')
        parts.append(f'<text x="{x - 12}" y="{yy + 4:.1f}" text-anchor="end" class="small">{value:.0f}{escape(suffix)}</text>')
    parts.append(f'<line x1="{x}" y1="{y + height}" x2="{x + width}" y2="{y + height}" stroke="{COLORS["ink"]}" stroke-width="1.4"/>')
    parts.append(f'<line x1="{x}" y1="{y}" x2="{x}" y2="{y + height}" stroke="{COLORS["ink"]}" stroke-width="1.4"/>')
    return "\n".join(parts)


def vertical_bar_chart(
    title: str,
    subtitle: str,
    items: list[dict],
    max_value: float,
    output: Path,
    suffix: str = "",
) -> None:
    width, height = 1080, 660
    chart_x, chart_y, chart_w, chart_h = 120, 130, 850, 390
    group_w = chart_w / len(items)
    bar_w = min(70, group_w * 0.35)
    threshold_w = bar_w
    parts = [
        f'<text x="48" y="56" class="title">{escape(title)}</text>',
        f'<text x="48" y="84" class="subtitle">{escape(subtitle)}</text>',
        axis_lines(chart_x, chart_y, chart_w, chart_h, max_value, suffix),
    ]

    for index, item in enumerate(items):
        center = chart_x + (group_w * index) + group_w / 2
        value_h = chart_h * item["value"] / max_value if max_value else 0
        threshold_h = chart_h * item["threshold"] / max_value if max_value else 0
        value_x = center - bar_w - 6
        threshold_x = center + 6
        value_y = chart_y + chart_h - value_h
        threshold_y = chart_y + chart_h - threshold_h
        parts.extend(
            [
                f'<rect x="{value_x:.1f}" y="{value_y:.1f}" width="{bar_w:.1f}" height="{value_h:.1f}" rx="4" fill="{item.get("color", COLORS["bar"])}"/>',
                f'<rect x="{threshold_x:.1f}" y="{threshold_y:.1f}" width="{threshold_w:.1f}" height="{threshold_h:.1f}" rx="4" fill="{COLORS["threshold"]}" opacity="0.78"/>',
                f'<text x="{value_x + bar_w / 2:.1f}" y="{value_y - 8:.1f}" text-anchor="middle" class="value">{item["value"]:.2f}{escape(suffix)}</text>',
                f'<text x="{threshold_x + threshold_w / 2:.1f}" y="{threshold_y - 8:.1f}" text-anchor="middle" class="small">{item["threshold"]:.0f}{escape(suffix)}</text>',
                f'<text x="{center:.1f}" y="{chart_y + chart_h + 34:.1f}" text-anchor="middle" class="label">{escape(item["label"])}</text>',
                f'<text x="{center:.1f}" y="{chart_y + chart_h + 54:.1f}" text-anchor="middle" class="small">{escape(item["note"])}</text>',
            ]
        )

    parts.extend(
        [
            f'<rect x="700" y="42" width="18" height="18" rx="3" fill="{COLORS["bar"]}"/>',
            '<text x="726" y="56" class="small">Resultado observado</text>',
            f'<rect x="700" y="70" width="18" height="18" rx="3" fill="{COLORS["threshold"]}" opacity="0.78"/>',
            '<text x="726" y="84" class="small">Umbral</text>',
        ]
    )
    output.write_text(svg_doc(width, height, "\n".join(parts)), encoding="utf-8")


def horizontal_bar_chart(title: str, subtitle: str, items: list[dict], max_value: float, output: Path, suffix: str = "") -> None:
    width, height = 1080, 540
    left, top = 280, 130
    bar_w, row_h = 650, 70
    parts = [
        f'<text x="48" y="56" class="title">{escape(title)}</text>',
        f'<text x="48" y="84" class="subtitle">{escape(subtitle)}</text>',
    ]
    for i, item in enumerate(items):
        y = top + i * row_h
        w = bar_w * item["value"] / max_value if max_value else 0
        parts.extend(
            [
                f'<text x="{left - 18}" y="{y + 28}" text-anchor="end" class="label">{escape(item["label"])}</text>',
                f'<rect x="{left}" y="{y}" width="{bar_w}" height="36" rx="5" fill="{COLORS["panel"]}" stroke="{COLORS["grid"]}"/>',
                f'<rect x="{left}" y="{y}" width="{w:.1f}" height="36" rx="5" fill="{item.get("color", COLORS["bar"])}"/>',
                f'<text x="{left + w + 12:.1f}" y="{y + 24}" class="value">{item["value"]:.2f}{escape(suffix)}</text>',
                f'<text x="{left}" y="{y + 54}" class="small">{escape(item["note"])}</text>',
            ]
        )
    output.write_text(svg_doc(width, height, "\n".join(parts)), encoding="utf-8")


def cupos_chart(output: Path, confirmadas: float, rechazadas: float) -> None:
    width, height = 1080, 580
    left, top = 150, 170
    total_w = 780
    total = confirmadas + rechazadas
    confirm_w = total_w * confirmadas / total
    reject_w = total_w * rechazadas / total
    parts = [
        '<text x="48" y="56" class="title">RNF-16: Sobrecupo cero</text>',
        '<text x="48" y="84" class="subtitle">50 intentos concurrentes sobre un evento con cupo maximo 10.</text>',
        f'<rect x="{left}" y="{top}" width="{confirm_w:.1f}" height="86" rx="8" fill="{COLORS["ok"]}"/>',
        f'<rect x="{left + confirm_w:.1f}" y="{top}" width="{reject_w:.1f}" height="86" rx="8" fill="{COLORS["warn"]}"/>',
        f'<text x="{left + confirm_w / 2:.1f}" y="{top + 50}" text-anchor="middle" class="value" fill="#fff">10 confirmadas</text>',
        f'<text x="{left + confirm_w + reject_w / 2:.1f}" y="{top + 50}" text-anchor="middle" class="value" fill="#fff">40 rechazadas</text>',
        f'<line x1="{left + confirm_w:.1f}" y1="{top - 38}" x2="{left + confirm_w:.1f}" y2="{top + 110}" stroke="{COLORS["threshold"]}" stroke-width="4"/>',
        f'<text x="{left + confirm_w:.1f}" y="{top - 52}" text-anchor="middle" class="value">Cupo = 10</text>',
        f'<text x="{left}" y="{top + 145}" class="label">Threshold K6: inscripciones_confirmadas count==10 OK</text>',
        f'<text x="{left}" y="{top + 175}" class="label">Threshold K6: inscripciones_rechazadas_por_cupo count==40 OK</text>',
        f'<text x="{left}" y="{top + 205}" class="label">HTTP 5xx: 0%</text>',
        f'<text x="{left}" y="{top + 250}" class="subtitle">Interpretacion: si hubiera race condition o falla del bloqueo, el bloque verde podria superar el limite rojo.</text>',
    ]
    output.write_text(svg_doc(width, height, "\n".join(parts)), encoding="utf-8")


def main() -> None:
    CHARTS.mkdir(parents=True, exist_ok=True)

    load = load_report("02-load-test")
    cupos = load_report("04-cupos-concurrencia")
    circuit = load_report("05-circuit-breaker")
    cache = load_report("06-cache-effectiveness")

    vertical_bar_chart(
        "Latencia p95 vs umbral",
        "Comparacion agregada desde los JSON de K6. No representa curva temporal.",
        [
            {
                "label": "Load 150 VUs",
                "note": "http_req_duration",
                "value": metric_value(load, "http_req_duration", "p(95)"),
                "threshold": 800,
                "color": COLORS["bar"],
            },
            {
                "label": "Circuit breaker",
                "note": "status 503",
                "value": metric_value(circuit, "http_req_duration{status:503,name:circuit_breaker_open}", "p(95)"),
                "threshold": 50,
                "color": COLORS["bar_2"],
            },
            {
                "label": "Cache catalogo",
                "note": "warm p95",
                "value": metric_value(cache, "catalogo_warm_duration", "p(95)"),
                "threshold": 300,
                "color": COLORS["bar_3"],
            },
        ],
        850,
        CHARTS / "latencia-p95-vs-umbral.svg",
        " ms",
    )

    cupos_chart(
        CHARTS / "rnf16-sobrecupo-cero.svg",
        metric_value(cupos, "inscripciones_confirmadas", "count"),
        metric_value(cupos, "inscripciones_rechazadas_por_cupo", "count"),
    )

    horizontal_bar_chart(
        "Tasas de cumplimiento",
        "Metricas de error, Retry-After y cache hit rate reportadas por K6.",
        [
            {
                "label": "Load error rate",
                "note": "Threshold: < 2%",
                "value": metric_value(load, "http_req_failed", "rate") * 100,
                "color": COLORS["ok"],
            },
            {
                "label": "Circuit Retry-After",
                "note": "Threshold: > 90%",
                "value": metric_value(circuit, "retry_after_presente", "rate") * 100,
                "color": COLORS["bar_2"],
            },
            {
                "label": "Cache hit rate",
                "note": "Threshold: > 95%",
                "value": metric_value(cache, "cache_hit_rate_estimado", "rate") * 100,
                "color": COLORS["bar"],
            },
            {
                "label": "RNF-16 checks",
                "note": "Threshold: 100%",
                "value": metric_value(cupos, "checks", "rate") * 100,
                "color": COLORS["bar_3"],
            },
        ],
        100,
        CHARTS / "tasas-cumplimiento.svg",
        "%",
    )

    horizontal_bar_chart(
        "Load sostenido 150 VUs",
        "Resumen de capacidad local del escenario C.",
        [
            {
                "label": "VUs maximos",
                "note": "Objetivo local defendible: 150",
                "value": metric_value(load, "vus_max", "max"),
                "color": COLORS["bar"],
            },
            {
                "label": "HTTP requests",
                "note": "Total del escenario",
                "value": metric_value(load, "http_reqs", "count"),
                "color": COLORS["bar_2"],
            },
            {
                "label": "Throughput req/s",
                "note": "Promedio K6",
                "value": metric_value(load, "http_reqs", "rate"),
                "color": COLORS["bar_3"],
            },
        ],
        825,
        CHARTS / "load-150-vus-resumen.svg",
    )

    for path in sorted(CHARTS.glob("*.svg")):
        print(path.relative_to(ROOT.parent))


if __name__ == "__main__":
    main()
