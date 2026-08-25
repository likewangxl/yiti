export const MAP_METRIC_OPTIONS = Object.freeze([
  { code: 'KPI_ACHIEVE_RATE_ORG', label: '综合达成', title: '综合达成率' },
  { code: 'DEP_ACHIEVE_RATE_ORG', label: '存款达成', title: '存款达成率', actualCode: 'M_0265', unit: '万元',
    yoyCode: 'DEP_BAL_YOY_RATE', momCode: 'DEP_BAL_MOM_RATE' },
  { code: 'LOAN_ACHIEVE_RATE_ORG', label: '贷款达成', title: '贷款达成率', actualCode: 'M_0348', unit: '万元',
    yoyCode: 'LOAN_BAL_YOY_RATE', momCode: 'LOAN_BAL_MOM_RATE' },
  { code: 'NEW_CUST_ACHIEVE_ORG', label: '新增客户', title: '新增客户达成率', actualCode: 'NEW_VALID_CUST_ORG_MONTH', unit: '户' }
]);

export const MAP_STATUS_COLORS = Object.freeze({
  excellent: '#1f8a70', normal: '#236b8e', warning: '#b9852f', risk: '#a94a55', missing: '#27364f'
});

export function finiteNumber(value) {
  if (value === null || value === undefined || value === '') return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

export function achievementColor(rate) {
  const value = finiteNumber(rate);
  if (value === null) return MAP_STATUS_COLORS.missing;
  if (value >= 100) return MAP_STATUS_COLORS.excellent;
  if (value >= 90) return MAP_STATUS_COLORS.normal;
  if (value >= 80) return MAP_STATUS_COLORS.warning;
  return MAP_STATUS_COLORS.risk;
}

function pointInRing([x, y], ring = []) {
  let inside = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const [xi, yi] = ring[i] || [];
    const [xj, yj] = ring[j] || [];
    if (!Number.isFinite(xi) || !Number.isFinite(yi) || !Number.isFinite(xj) || !Number.isFinite(yj)) continue;
    const crosses = ((yi > y) !== (yj > y)) && (x < ((xj - xi) * (y - yi)) / ((yj - yi) || Number.EPSILON) + xi);
    if (crosses) inside = !inside;
  }
  return inside;
}

export function featureContainsPoint(feature, point) {
  const geometry = feature?.geometry;
  if (!geometry || !Array.isArray(point)) return false;
  const polygons = geometry.type === 'Polygon' ? [geometry.coordinates]
    : (geometry.type === 'MultiPolygon' ? geometry.coordinates : []);
  return polygons.some(polygon => Array.isArray(polygon?.[0]) && pointInRing(point, polygon[0])
    && !(polygon.slice(1).some(hole => pointInRing(point, hole))));
}

export function formatMetricValue(value, digits = 1) {
  const number = finiteNumber(value);
  return number === null ? '--' : number.toLocaleString('zh-CN', { minimumFractionDigits: digits, maximumFractionDigits: digits });
}

/**
 * 生成可复现的演示指标。同一区域、同一指标始终得到相同值，便于设计验收；
 * 该函数只用于前端无真实指标时的明确标识演示态，不能回写数据库。
 */
export function simulatedMetric(seed, metricCode) {
  const text = `${seed || 'UNKNOWN'}:${metricCode || 'KPI_ACHIEVE_RATE_ORG'}`;
  let hash = 2166136261;
  for (let index = 0; index < text.length; index += 1) {
    hash ^= text.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  const rate = Math.round((72 + (Math.abs(hash) % 361) / 10) * 10) / 10;
  const target = 1000 + (Math.abs(hash >>> 5) % 9000);
  const actual = Math.round(target * rate / 100);
  const yoy = Math.round((-3 + (Math.abs(hash >>> 9) % 151) / 10) * 10) / 10;
  const mom = Math.round((-2 + (Math.abs(hash >>> 13) % 81) / 10) * 10) / 10;
  return { rate, actual, target, gap: Math.max(target - actual, 0), yoy, mom };
}
