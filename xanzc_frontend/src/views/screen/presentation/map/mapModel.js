import { displayUnitLabel, formatDisplayMetric } from '../model/displayMetricsModel';

export const MAP_MISSING_COLOR = '#65738a';
export const MAP_PALETTE = Object.freeze({
  low: '#3d78ba',
  mid: '#56c7c2',
  high: '#f4c95d'
});

function object(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function text(value) {
  return value === null || value === undefined ? '' : String(value).trim();
}

function finite(value) {
  if (value === null || value === undefined || value === '' || typeof value === 'boolean') return null;
  const number = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(number) ? number : null;
}

function presentationOf(source) {
  if (!object(source)) return {};
  if (object(source.displayPresentation)) return presentationOf(source.displayPresentation);
  if (source.displaySchemaVersion !== undefined || source.display) return source;
  if (object(source.presentation)) return presentationOf(source.presentation);
  if (object(source.canvasStyle?.presentation)) return presentationOf(source.canvasStyle.presentation);
  if (object(source.renderPackage?.canvasStyle?.presentation)) return presentationOf(source.renderPackage.canvasStyle.presentation);
  return {};
}

function metricFromSource(source, field) {
  if (!object(source) || !field) return { present: false, value: null, unit: '' };
  const candidates = [source, source.metrics, source.metricValues, source.values];
  for (const candidate of candidates) {
    if (!object(candidate) || !Object.prototype.hasOwnProperty.call(candidate, field)) continue;
    const value = candidate[field];
    if (object(value)) return { present: true, value: value.value, unit: text(value.unit || value.sourceUnit) };
    return { present: true, value, unit: text(source[`${field}Unit`] || source.unit) };
  }
  return { present: false, value: null, unit: '' };
}

function codeOf(value, ...keys) {
  if (!object(value)) return '';
  for (const key of keys) {
    const code = text(value[key]);
    if (code) return code;
  }
  return '';
}

function cityCodeOf(value) {
  return codeOf(value, 'cityCode', 'city_code');
}

function orgCodeOf(value) {
  return codeOf(value, 'orgCode', 'org_code');
}

function cityNameOf(value) {
  return text(value?.cityName ?? value?.city_name);
}

function isLocated(value) {
  const located = value?.located === true || ['1', 'true', 'yes', 'y'].includes(text(value?.located).toLowerCase());
  const lng = finite(value?.lng ?? value?.longitude);
  const lat = finite(value?.lat ?? value?.latitude);
  const coordSys = text(value?.coordSys ?? value?.coordinateSystem ?? value?.coord_sys).toUpperCase().replace(/[\s_-]/g, '');
  return located && lng !== null && lat !== null && coordSys === 'GCJ02';
}

function sourceDate(model, options = {}) {
  if (text(options.dataDate)) return text(options.dataDate);
  if (text(model?.dataDate)) return text(model.dataDate);
  const qualities = object(model?.sourceQualities) ? Object.values(model.sourceQualities) : [];
  return text(qualities.find(item => text(item?.dataDate))?.dataDate);
}

function featureCodes(geoJson) {
  return (Array.isArray(geoJson?.features) ? geoJson.features : [])
    .map(feature => codeOf(feature?.properties, 'adcode', 'cityCode', 'city_code', 'code'))
    .filter(Boolean);
}

function rankingComponentOf(presentation) {
  return (Array.isArray(presentation?.display?.components) ? presentation.display.components : [])
    .find(component => component?.componentType === 'RANKING' && component.visible !== false) || null;
}

function selectedMetricOf(presentation, mapComponent, metricKey) {
  const key = text(metricKey) || text(mapComponent?.content?.mainField);
  const rankingMetrics = Array.isArray(rankingComponentOf(presentation)?.content?.rankingMetrics)
    ? rankingComponentOf(presentation).content.rankingMetrics : [];
  const selected = rankingMetrics.find(metric => text(metric?.metricKey) === key);
  const reference = Array.isArray(mapComponent?.dataRefs) ? mapComponent.dataRefs[0] || {} : {};
  return {
    metricKey: key,
    field: text(selected?.field) || (text(metricKey) ? key : text(mapComponent?.content?.mainField)),
    label: text(selected?.label) || (text(metricKey) && key !== text(mapComponent?.content?.mainField) ? key : '') || text(reference.metricName) || key || '地图指标',
    unit: text(selected?.unit) || text(reference.unit) || text(mapComponent?.format?.displayUnit),
    direction: text(selected?.direction).toUpperCase() || 'DESC'
  };
}

function summaryMetric(summary, field, metricKey) {
  const kpis = Array.isArray(summary?.kpis) ? summary.kpis : [];
  const source = kpis.find(item => text(item?.key ?? item?.metricKey ?? item?.field) === field)
    || (metricKey && metricKey !== field ? kpis.find(item => text(item?.key ?? item?.metricKey ?? item?.field) === metricKey) : null);
  if (!source) return { present: false, value: null, unit: '' };
  return { present: true, value: source.value, unit: text(source.unit || source.sourceUnit) };
}

function rowsForCity(rows, cityCode) {
  return rows.filter(row => cityCodeOf(row) === cityCode);
}

function regionMetricValue({ cityCode, summaries, rankings, field, metricKey }) {
  const summary = summaries[cityCode];
  const summarySource = summaryMetric(summary, field, metricKey);
  if (summarySource.present) return summarySource;
  const candidates = rowsForCity(rankings, cityCode);
  // A city has no safe aggregate fallback. A single row is only safe when the
  // result is already city-grained; multiple institution rows must stay missing.
  if (candidates.length !== 1) return { present: false, value: null, unit: '' };
  return metricFromSource(candidates[0], field);
}

function pointMetricValue(point, rankings, field) {
  const orgCode = orgCodeOf(point);
  const candidates = rankings.filter(row => orgCodeOf(row) === orgCode);
  if (candidates.length === 1) return metricFromSource(candidates[0], field);
  if (!candidates.length) return metricFromSource(point, field);
  return { present: false, value: null, unit: '' };
}

function colorFor(value, min, max) {
  const number = finite(value);
  if (number === null) return MAP_MISSING_COLOR;
  const ratio = min === max ? 0.5 : Math.max(0, Math.min(1, (number - min) / (max - min)));
  if (ratio < 0.5) {
    const local = ratio * 2;
    return interpolate(MAP_PALETTE.low, MAP_PALETTE.mid, local);
  }
  return interpolate(MAP_PALETTE.mid, MAP_PALETTE.high, (ratio - 0.5) * 2);
}

function viewFitFor(level) {
  // These are presentation-local profiles. PanoramaMap's legacy callers keep
  // their existing fit values; only the versioned MAP branch opts in here.
  if (level === 'city') return { classicPadding: 1.04, reliefFitHeight: 0.84 };
  if (level === 'institution') return { classicPadding: 1.08, reliefFitHeight: 0.76 };
  return { classicPadding: 1.02, reliefFitHeight: 0.9 };
}

function interpolate(left, right, ratio) {
  const parse = color => color.match(/[\da-f]{2}/gi).map(value => Number.parseInt(value, 16));
  const a = parse(left.slice(1));
  const b = parse(right.slice(1));
  const channels = a.map((value, index) => Math.round(value + (b[index] - value) * ratio));
  return `#${channels.map(value => value.toString(16).padStart(2, '0')).join('')}`;
}

export function formatMapMetric(value, format = {}, sourceUnit = '') {
  return formatDisplayMetric(value, {
    emptyText: '暂无数据',
    ...format
  }, sourceUnit).text;
}

export function findVisibleMapComponent(source) {
  const presentation = presentationOf(source);
  if (presentation.displaySchemaVersion !== 1) return null;
  return (Array.isArray(presentation.display?.components) ? presentation.display.components : [])
    .find(component => component?.componentType === 'MAP' && component.visible !== false) || null;
}

export function mapContextForCity(region = {}, meta = {}) {
  return {
    level: 'CITY',
    cityCode: text(region.code) || null,
    cityName: text(region.name) || null,
    orgCode: null,
    ownerOperatingOrgCode: null,
    metricKey: text(meta.metricKey) || null,
    dataDate: text(meta.dataDate) || null
  };
}

export function mapContextForInstitution(institution = {}, meta = {}) {
  return {
    level: 'INSTITUTION',
    cityCode: cityCodeOf(institution) || null,
    cityName: cityNameOf(institution) || null,
    orgCode: orgCodeOf(institution) || null,
    ownerOperatingOrgCode: codeOf(institution, 'ownerOperatingOrgCode', 'owner_operating_org_code') || null,
    metricKey: text(meta.metricKey) || null,
    dataDate: text(meta.dataDate) || null
  };
}

/**
 * 将 displaySchemaVersion=1 的 MAP 组件适配为既有 PanoramaMap 所需的只读模型。
 * 城市统计只读取明确的 citySummary；不会把多个机构行相加，也不会使用
 * ownerOperatingOrgCode 代替地理 cityCode。
 */
export function buildMapModel(sourcePresentation, inputModel = {}, options = {}) {
  const presentation = presentationOf(sourcePresentation);
  const component = findVisibleMapComponent(presentation);
  if (!component) return { enabled: false, components: [], metricValues: {}, metricRawValues: {}, metricStates: {}, legend: [] };

  const model = object(inputModel) ? inputModel : {};
  const metric = selectedMetricOf(presentation, component, options.metricKey);
  const format = object(component.format) ? component.format : {};
  const summaries = object(model.citySummaries) ? model.citySummaries : {};
  const rankings = Array.isArray(model.rankings) ? model.rankings.filter(object) : [];
  const institutions = Array.isArray(model.institutions) ? model.institutions.filter(object) : [];
  const level = text(options.level).toLowerCase() || (text(options.cityCode) ? 'city' : 'province');
  const cityCode = text(options.cityCode);
  const selectedOrgCode = text(options.selectedOrgCode);
  const regionCodes = [...new Set([...Object.keys(summaries), ...institutions.map(cityCodeOf), ...featureCodes(options.geoJson)].filter(Boolean))];
  const rawValues = {};
  const metricValues = {};
  const sourceUnits = {};
  regionCodes.forEach(code => {
    const source = regionMetricValue({ cityCode: code, summaries, rankings, field: metric.field, metricKey: metric.metricKey });
    const value = finite(source.value);
    rawValues[code] = value;
    sourceUnits[code] = source.unit || metric.unit;
    metricValues[code] = formatMapMetric(value, format, source.unit || metric.unit);
  });

  const finiteValues = Object.values(rawValues).filter(value => value !== null);
  const min = finiteValues.length ? Math.min(...finiteValues) : 0;
  const max = finiteValues.length ? Math.max(...finiteValues) : 0;
  const metricStates = Object.fromEntries(regionCodes.map(code => {
    const value = rawValues[code];
    return [code, {
      value,
      state: value === null ? 'MISSING' : 'READY',
      color: colorFor(value, min, max),
      text: metricValues[code],
      unit: sourceUnits[code]
    }];
  }));
  const metricColors = Object.fromEntries(Object.entries(metricStates).map(([code, state]) => [code, state.color]));
  const scopedInstitutions = level === 'city'
    ? institutions.filter(item => cityCodeOf(item) === cityCode)
    : level === 'institution'
      ? institutions.filter(item => orgCodeOf(item) === selectedOrgCode)
      : institutions;
  const points = scopedInstitutions.map(item => ({
    ...item,
    metricValue: finite(pointMetricValue(item, rankings, metric.field).value),
    metricText: formatMapMetric(pointMetricValue(item, rankings, metric.field).value, format, metric.unit)
  }));
  const missingCoordinates = points.filter(item => !isLocated(item));
  const locatedPoints = points.filter(isLocated);
  const status = level === 'city' && !points.length ? 'NO_VISIBLE_INSTITUTIONS' : 'READY';
  return {
    enabled: true,
    component,
    level,
    status,
    noVisibleInstitutions: status === 'NO_VISIBLE_INSTITUTIONS',
    title: text(component.text?.title) || metric.label || '地图',
    subtitle: text(component.text?.subtitle),
    metricKey: metric.metricKey,
    field: metric.field,
    metricLabel: metric.label,
    metricUnit: displayUnitLabel(metric.unit),
    direction: metric.direction,
    viewFit: viewFitFor(level),
    dataDate: sourceDate(model, options),
    metricValues,
    metricRawValues: rawValues,
    metricStates,
    metricColors,
    points,
    locatedPoints,
    missingCoordinates,
    institutions: scopedInstitutions,
    legend: [
      { key: 'high', label: '高', color: MAP_PALETTE.high },
      { key: 'mid', label: '中', color: MAP_PALETTE.mid },
      { key: 'low', label: '低', color: MAP_PALETTE.low },
      { key: 'missing', label: '暂无数据', color: MAP_MISSING_COLOR }
    ]
  };
}

export const buildPresentationMapModel = buildMapModel;
export const buildMapPresentationModel = buildMapModel;
export const findMapComponent = findVisibleMapComponent;

export { isLocated, presentationOf };
