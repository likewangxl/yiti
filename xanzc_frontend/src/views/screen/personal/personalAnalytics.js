const PALETTE = [
  "#4be5ec",
  "#ffcb6b",
  "#8da7ff",
  "#ff8ca8",
  "#a7e36f",
  "#c18cff",
  "#67d7c5",
  "#ff9f43",
  "#54a0ff",
  "#ee5253",
];

export const finite = (value) => {
  if (
    value === null ||
    value === undefined ||
    typeof value === "boolean" ||
    (typeof value === "string" && value.trim() === "")
  )
    return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
};
const day = (value) => String(value || "").slice(0, 10);
const dateValue = (value) => {
  const raw = day(value);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(raw)) return null;
  const result = new Date(`${raw}T00:00:00`);
  return result.getFullYear() === Number(raw.slice(0, 4)) &&
    result.getMonth() + 1 === Number(raw.slice(5, 7)) &&
    result.getDate() === Number(raw.slice(8, 10))
    ? result
    : null;
};
const pad = (value) => String(value).padStart(2, "0");
const iso = (value) =>
  `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}`;
const month = (value) => `${value.getFullYear()}-${pad(value.getMonth() + 1)}`;

export function normalizeRange(value) {
  return ["7d", "6m", "1y"].includes(value) ? value : "7d";
}
export function getStableColor(code, configured) {
  return configured
    ? String(configured)
    : PALETTE[
        Math.abs(
          [...String(code || "metric")].reduce(
            (hash, char) => ((hash << 5) - hash + char.charCodeAt(0)) | 0,
            0,
          ),
        ) % PALETTE.length
      ];
}
export function metricColors(metrics) {
  const used = new Set();
  return (Array.isArray(metrics) ? metrics : []).map((metric, index) => {
    if (metric.color) {
      used.add(metric.color);
      return metric.color;
    }
    let color = PALETTE[index % PALETTE.length];
    let cursor = index;
    while (used.has(color) && cursor < index + PALETTE.length * 2)
      color = PALETTE[++cursor % PALETTE.length];
    used.add(color);
    return color;
  });
}

function rangeStart(end, range) {
  const result = new Date(end);
  if (range === "7d") result.setDate(result.getDate() - 6);
  else result.setMonth(result.getMonth() - (range === "6m" ? 5 : 11), 1);
  return result;
}
function validPoints(history) {
  return (Array.isArray(history) ? history : [])
    .map((item) => ({ date: day(item?.date), value: finite(item?.value) }))
    .filter((item) => dateValue(item.date))
    .sort((a, b) => a.date.localeCompare(b.date));
}
export function filterHistory(history, range = "7d", endDate) {
  const points = validPoints(history);
  const explicitEnd = dateValue(endDate);
  const end =
    explicitEnd ||
    (points.length ? dateValue(points[points.length - 1].date) : null);
  if (!end) return [];
  const normalized = normalizeRange(range);
  const start = rangeStart(end, normalized);
  const inRange = points.filter(
    (point) => dateValue(point.date) >= start && dateValue(point.date) <= end,
  );
  if (normalized === "7d") {
    const map = new Map(inRange.map((point) => [point.date, point.value]));
    const result = [];
    for (
      let cursor = new Date(start);
      cursor <= end;
      cursor.setDate(cursor.getDate() + 1)
    ) {
      const current = iso(cursor);
      result.push({
        date: current,
        value: map.has(current) ? map.get(current) : null,
      });
    }
    return result;
  }
  const result = [];
  for (
    let cursor = new Date(start.getFullYear(), start.getMonth(), 1);
    cursor <= end;
    cursor.setMonth(cursor.getMonth() + 1)
  ) {
    const key = month(cursor);
    const observations = inRange.filter((point) => point.date.startsWith(key));
    const last = observations[observations.length - 1];
    result.push({ date: key, value: last?.value ?? null });
  }
  return result;
}
export function growthSeries(points) {
  const base = points.find((point) => finite(point.value) !== null)?.value;
  return points.map((point) => ({
    ...point,
    value:
      base === null ||
      base === 0 ||
      base === undefined ||
      finite(point.value) === null
        ? null
        : ((point.value - base) / Math.abs(base)) * 100,
  }));
}
export function actualSeries(points) {
  return points.map((point) => ({ ...point, value: finite(point.value) }));
}
export function buildTrendOption(
  metrics,
  range = "7d",
  endDate,
  mode = "growth",
) {
  const list = Array.isArray(metrics) ? metrics : [];
  const colors = metricColors(list);
  const buckets = list
    .flatMap((metric) =>
      filterHistory(metric.history, range, endDate).map((point) => point.date),
    )
    .filter((date, index, all) => all.indexOf(date) === index)
    .sort();
  const series = list.map((metric, index) => {
    const points = filterHistory(metric.history, range, endDate);
    const map = new Map(points.map((point) => [point.date, point.value]));
    const values = buckets.map((date) => ({
      date,
      value: map.get(date) ?? null,
    }));
    const plotted =
      mode === "actual" ? actualSeries(values) : growthSeries(values);
    return {
      name: metric.metricName || metric.metricCode || "指标",
      type: "line",
      connectNulls: false,
      showSymbol: false,
      smooth: false,
      itemStyle: { color: colors[index] },
      lineStyle: { color: colors[index] },
      data: plotted.map((point) => point.value),
    };
  });
  return {
    color: colors,
    tooltip: { trigger: "axis" },
    legend: { textStyle: { color: "#bfd2ef" } },
    grid: { left: 48, right: 18, top: 38, bottom: 32 },
    xAxis: { type: "category", data: buckets, axisLabel: { color: "#91a9d1" } },
    yAxis: {
      type: "value",
      name: mode === "actual" ? "实际值" : "增长率（%）",
      axisLabel: {
        color: "#91a9d1",
        formatter: (value) => (mode === "actual" ? value : `${value}%`),
      },
    },
    series,
  };
}
export function orgShare(metric) {
  const current = finite(metric?.currentValue);
  const organization = finite(metric?.orgValue);
  const unit = String(metric?.unit || "").trim();
  const orgUnit = String(metric?.orgUnit || "").trim();
  const dataDate = dateValue(metric?.dataDate);
  const orgDataDate = dateValue(metric?.orgDataDate);
  if (
    current === null ||
    organization === null ||
    organization <= 0 ||
    !unit ||
    !orgUnit ||
    unit !== orgUnit ||
    !dataDate ||
    !orgDataDate ||
    day(metric.dataDate) !== day(metric.orgDataDate)
  )
    return null;
  return (current / organization) * 100;
}
export function progressRate(metric) {
  const current = finite(metric?.currentValue);
  const target = finite(metric?.targetValue);
  return current === null || target === null || target <= 0
    ? null
    : (current / target) * 100;
}
