#!/usr/bin/env python3
"""Generate time-series SVG charts from K6 JSONL output.

Run K6 with `--out json=/reports/timeseries/<scenario>.jsonl` first. Unlike the
summary JSON, these files contain per-sample points captured during the test.
"""

from __future__ import annotations

import json
import math
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPORTS = ROOT / "reports"
TIMESERIES = REPORTS / "timeseries"
CHARTS = REPORTS / "timeseries-charts"

COLORS = {
    "ink": "#172033",
    "muted": "#5b667a",
    "grid": "#d8dee9",
    "blue": "#2563eb",
    "green": "#15803d",
    "teal": "#0f766e",
    "amber": "#b45309",
    "red": "#dc2626",
    "panel": "#f8fafc",
}


def escape(value: object) -> str:
    return (
        str(value)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
    )


def parse_time(value: str) -> float:
    clean = value.replace("Z", "+00:00")
    if "." in clean:
        prefix, suffix = clean.split(".", 1)
        if "+" in suffix:
            fraction, offset = suffix.split("+", 1)
            clean = f"{prefix}.{fraction[:6].ljust(6, '0')}+{offset}"
        elif "-" in suffix:
            fraction, offset = suffix.split("-", 1)
            clean = f"{prefix}.{fraction[:6].ljust(6, '0')}-{offset}"
    return datetime.fromisoformat(clean).astimezone(timezone.utc).timestamp()


def load_points(file_name: str) -> list[dict]:
    path = TIMESERIES / file_name
    points: list[dict] = []
    if not path.exists():
        return points
    with path.open(encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            try:
                row = json.loads(line)
            except json.JSONDecodeError:
                continue
            if row.get("type") == "Point":
                points.append(row)
    return points


def quantile(values: list[float], q: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = (len(ordered) - 1) * q
    lower = math.floor(index)
    upper = math.ceil(index)
    if lower == upper:
        return ordered[int(index)]
    return ordered[lower] + (ordered[upper] - ordered[lower]) * (index - lower)


def bucket_duration(points: list[dict], metric: str, bucket_seconds: int, tag_filter: dict[str, str] | None = None) -> list[tuple[float, float]]:
    selected = []
    for point in points:
        if point.get("metric") != metric:
            continue
        tags = point["data"].get("tags", {})
        if tag_filter and any(str(tags.get(key)) != value for key, value in tag_filter.items()):
            continue
        selected.append(point)
    if not selected:
        return []
    start = min(parse_time(point["data"]["time"]) for point in selected)
    buckets: dict[int, list[float]] = defaultdict(list)
    for point in selected:
        bucket = int((parse_time(point["data"]["time"]) - start) // bucket_seconds) * bucket_seconds
        buckets[bucket].append(float(point["data"]["value"]))
    return [(bucket, quantile(values, 0.95)) for bucket, values in sorted(buckets.items())]


def cumulative_counter(points: list[dict], metric: str) -> list[tuple[float, float]]:
    selected = [point for point in points if point.get("metric") == metric]
    if not selected:
        return []
    start = min(parse_time(point["data"]["time"]) for point in points)
    total = 0.0
    series = []
    for point in sorted(selected, key=lambda row: row["data"]["time"]):
        total += float(point["data"].get("value", 0))
        second = round(parse_time(point["data"]["time"]) - start, 3)
        series.append((second, total))
    return compress_last_by_x(series)


def cumulative_rate(points: list[dict], metric: str) -> list[tuple[float, float]]:
    selected = [point for point in points if point.get("metric") == metric]
    if not selected:
        return []
    start = min(parse_time(point["data"]["time"]) for point in points)
    ok = 0.0
    total = 0.0
    series = []
    for point in sorted(selected, key=lambda row: row["data"]["time"]):
        total += 1
        ok += float(point["data"].get("value", 0))
        second = round(parse_time(point["data"]["time"]) - start, 3)
        series.append((second, (ok / total) * 100))
    return compress_last_by_x(series)


def gauge(points: list[dict], metric: str, bucket_seconds: int) -> list[tuple[float, float]]:
    selected = [point for point in points if point.get("metric") == metric]
    if not selected:
        return []
    start = min(parse_time(point["data"]["time"]) for point in selected)
    buckets: dict[int, list[float]] = defaultdict(list)
    for point in selected:
        bucket = int((parse_time(point["data"]["time"]) - start) // bucket_seconds) * bucket_seconds
        buckets[bucket].append(float(point["data"]["value"]))
    return [(bucket, sum(values) / len(values)) for bucket, values in sorted(buckets.items())]


def compress_last_by_x(series: list[tuple[float, float]]) -> list[tuple[float, float]]:
    compressed: dict[float, float] = {}
    for x, y in series:
        compressed[x] = y
    return sorted(compressed.items())


def svg_doc(width: int, height: int, body: str) -> str:
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">
  <rect width="100%" height="100%" fill="#ffffff"/>
  <style>
    text {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; fill: {COLORS['ink']}; }}
    .title {{ font-size: 28px; font-weight: 750; }}
    .subtitle {{ font-size: 14px; fill: {COLORS['muted']}; }}
    .axis {{ font-size: 12px; fill: {COLORS['muted']}; }}
    .legend {{ font-size: 13px; fill: {COLORS['ink']}; }}
  </style>
{body}
</svg>
"""


def path_for(series: list[tuple[float, float]], x0: int, y0: int, width: int, height: int, max_x: float, max_y: float) -> str:
    if not series:
        return ""
    points = []
    for x, y in series:
        px = x0 + (x / max_x * width if max_x else 0)
        py = y0 + height - (y / max_y * height if max_y else 0)
        points.append(f"{px:.1f},{py:.1f}")
    return "M" + " L".join(points)


def line_chart(title: str, subtitle: str, series: list[dict], output: Path, y_label: str, max_y: float | None = None) -> None:
    width, height = 1180, 620
    x0, y0, chart_w, chart_h = 100, 130, 940, 360
    max_x = max((max((x for x, _ in item["points"]), default=0) for item in series), default=0) or 1
    calculated_y = max((max((y for _, y in item["points"]), default=0) for item in series), default=0)
    ceiling_y = max_y if max_y is not None else max(1, calculated_y * 1.18)
    parts = [
        f'<text x="48" y="56" class="title">{escape(title)}</text>',
        f'<text x="48" y="84" class="subtitle">{escape(subtitle)}</text>',
    ]
    for step in range(0, 6):
        y_value = ceiling_y * step / 5
        yy = y0 + chart_h - chart_h * step / 5
        parts.append(f'<line x1="{x0}" y1="{yy:.1f}" x2="{x0 + chart_w}" y2="{yy:.1f}" stroke="{COLORS["grid"]}"/>')
        parts.append(f'<text x="{x0 - 12}" y="{yy + 4:.1f}" text-anchor="end" class="axis">{y_value:.0f}</text>')
    for step in range(0, 6):
        x_value = max_x * step / 5
        xx = x0 + chart_w * step / 5
        if max_x < 10:
            label = f"{x_value:.1f}s"
        elif max_x < 60:
            label = f"{x_value:.0f}s"
        else:
            label = f"{x_value:.0f}s"
        parts.append(f'<text x="{xx:.1f}" y="{y0 + chart_h + 28}" text-anchor="middle" class="axis">{label}</text>')
    parts.append(f'<text x="{x0}" y="{y0 + chart_h + 58}" class="axis">Tiempo desde inicio</text>')
    parts.append(f'<text x="{x0 - 76}" y="{y0 - 16}" class="axis">{escape(y_label)}</text>')
    parts.append(f'<line x1="{x0}" y1="{y0 + chart_h}" x2="{x0 + chart_w}" y2="{y0 + chart_h}" stroke="{COLORS["ink"]}" stroke-width="1.4"/>')
    parts.append(f'<line x1="{x0}" y1="{y0}" x2="{x0}" y2="{y0 + chart_h}" stroke="{COLORS["ink"]}" stroke-width="1.4"/>')

    legend_y = 130
    for index, item in enumerate(series):
        color = item["color"]
        path = path_for(item["points"], x0, y0, chart_w, chart_h, max_x, ceiling_y)
        if path:
            parts.append(f'<path d="{path}" fill="none" stroke="{color}" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round"/>')
            for point_x, point_y in item["points"]:
                if point_x in (item["points"][0][0], item["points"][-1][0]):
                    px = x0 + point_x / max_x * chart_w
                    py = y0 + chart_h - point_y / ceiling_y * chart_h
                    parts.append(f'<circle cx="{px:.1f}" cy="{py:.1f}" r="4" fill="{color}"/>')
        parts.append(f'<rect x="910" y="{legend_y + index * 30}" width="16" height="16" rx="3" fill="{color}"/>')
        parts.append(f'<text x="934" y="{legend_y + 13 + index * 30}" class="legend">{escape(item["label"])}</text>')

    output.write_text(svg_doc(width, height, "\n".join(parts)), encoding="utf-8")


def main() -> None:
    CHARTS.mkdir(parents=True, exist_ok=True)

    load = load_points("02-load-test.jsonl")
    cupos = load_points("04-cupos-concurrencia.jsonl")
    circuit = load_points("05-circuit-breaker.jsonl")
    cache = load_points("06-cache-effectiveness.jsonl")

    if load:
        line_chart(
            "Load 150 VUs: latencia p95 en el tiempo",
            "Buckets de 10 segundos desde la salida JSONL de K6.",
            [
                {"label": "http_req_duration p95", "color": COLORS["blue"], "points": bucket_duration(load, "http_req_duration", 10)},
                {"label": "VUs promedio", "color": COLORS["teal"], "points": gauge(load, "vus", 10)},
            ],
            CHARTS / "load-150-vus-latencia-tiempo.svg",
            "ms / VUs",
        )

    if cupos:
        line_chart(
            "RNF-16: inscripciones acumuladas en el tiempo",
            "Cupo limitado: exactamente 10 confirmadas y 40 rechazadas.",
            [
                {"label": "Confirmadas", "color": COLORS["green"], "points": cumulative_counter(cupos, "inscripciones_confirmadas")},
                {"label": "Rechazadas por cupo", "color": COLORS["amber"], "points": cumulative_counter(cupos, "inscripciones_rechazadas_por_cupo")},
            ],
            CHARTS / "rnf16-cupos-tiempo.svg",
            "conteo acumulado",
            max_y=50,
        )

    if circuit:
        line_chart(
            "RNF-14: circuito abierto en el tiempo",
            "p95 por buckets de 5 segundos para respuestas HTTP 503.",
            [
                {
                    "label": "503 p95",
                    "color": COLORS["red"],
                    "points": bucket_duration(
                        circuit,
                        "http_req_duration",
                        5,
                        {"name": "circuit_breaker_open", "status": "503"},
                    ),
                },
                {"label": "Retry-After acumulado %", "color": COLORS["blue"], "points": cumulative_rate(circuit, "retry_after_presente")},
            ],
            CHARTS / "rnf14-circuit-breaker-tiempo.svg",
            "ms / %",
        )

    if cache:
        line_chart(
            "Cache Redis: hit rate en el tiempo",
            "Tasa acumulada de hits estimados y p95 de catalogo warm.",
            [
                {"label": "Hit rate acumulado %", "color": COLORS["green"], "points": cumulative_rate(cache, "cache_hit_rate_estimado")},
                {"label": "Catalogo warm p95", "color": COLORS["blue"], "points": bucket_duration(cache, "catalogo_warm_duration", 5)},
            ],
            CHARTS / "cache-hit-rate-tiempo.svg",
            "% / ms",
            max_y=110,
        )

    for chart in sorted(CHARTS.glob("*.svg")):
        print(chart.relative_to(ROOT.parent))


if __name__ == "__main__":
    main()
