const DISPLAY_UNITS = Object.freeze({
  YUAN: '元',
  TEN_THOUSAND: '万元',
  HUNDRED_MILLION: '亿元',
  COUNT: '个',
  TEN_THOUSAND_COUNT: '万户',
  PERCENT: '%'
});

const CITY_METRIC_DEFINITIONS = Object.freeze([
  { key: 'deposit', label: '存款余额', aliases: ['deposit'], unit: '亿元', kind: 'amount' },
  { key: 'loan', label: '贷款余额', aliases: ['loan'], unit: '亿元', kind: 'amount' },
  {
    key: 'depositIncrease', label: '存款较上月净增', aliases: ['depositIncrease', 'increase'], unit: '亿元', kind: 'amount'
  },
  {
    key: 'depositAverage', label: '存款月均余额', aliases: ['depositAverage', 'average'], unit: '亿元', kind: 'amount'
  },
  { key: 'customers', label: '营销有效归属客户数', aliases: ['customers'], unit: '万户', kind: 'customers' },
  {
    key: 'rate', label: '目标完成率', aliases: ['rate', 'targetRate', 'completionRate', 'targetCompletionRate'], unit: '%', kind: 'rate'
  },
  { key: 'revenue', label: '手工测试收入', aliases: ['revenue'], unit: '亿元', kind: 'amount' }
]);

function isRecord(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function text(value) {
  return value === null || value === undefined ? '' : String(value).trim();
}

function finiteMetric(value) {
  if (value === null || value === undefined || value === '' || typeof value === 'boolean') return null;
  const number = typeof value === 'number' ? value : Number(String(value).trim());
  return Number.isFinite(number) ? number : null;
}

function formatNumber(value) {
  const number = finiteMetric(value);
  if (number === null) return null;
  if (number !== 0 && Math.abs(number) < 0.01) {
    return new Intl.NumberFormat('en-US', { maximumFractionDigits: 8, minimumFractionDigits: 4 }).format(number);
  }
  return new Intl.NumberFormat('en-US', {
    maximumFractionDigits: 2,
    minimumFractionDigits: Number.isInteger(number) ? 0 : 2
  }).format(number);
}

function normalizeDisplayUnit(unit, fallback) {
  const source = text(unit);
  if (!source) return fallback;
  return DISPLAY_UNITS[source.toUpperCase()] || source;
}

/**
 * Keep the source unit on map details while retaining the dashboard's numeric
 * precision. A small value expressed in 亿元 may still use the existing
 * dashboard convention of showing 万元; a value already expressed in 万元 is
 * never re-scaled as 亿元.
 */
export function formatCityMapMetric(value, unit = '', kind = '') {
  const number = finiteMetric(value);
  if (number === null) return '暂无数据';

  const normalizedUnit = normalizeDisplayUnit(unit, kind === 'customers' ? '万户' : kind === 'rate' ? '%' : '');
  if (kind === 'customers' && normalizedUnit === '万户' && number !== 0 && Math.abs(number) < 1) {
    const households = Math.round(number * 10000);
    return `${households === 0 ? '<1' : new Intl.NumberFormat('en-US').format(households)}户`;
  }
  if (kind === 'amount' && normalizedUnit === '亿元' && number !== 0 && Math.abs(number) < 1) {
    const wanYuan = number * 10000;
    const digits = Math.abs(wanYuan) >= 100 ? 2 : 4;
    return `${new Intl.NumberFormat('en-US', { maximumFractionDigits: digits, minimumFractionDigits: digits }).format(wanYuan)}万元`;
  }
  return `${formatNumber(number)}${normalizedUnit}`;
}

function cityCodeOf(value) {
  return text(value?.cityCode ?? value?.city_code);
}

function orgCodeOf(value) {
  return text(value?.orgCode ?? value?.org_code);
}

function orgNameOf(value, orgCode) {
  return text(value?.orgName ?? value?.org_name ?? value?.name) || orgCode;
}

function isLocated(value) {
  const located = value?.located;
  const flag = typeof located === 'boolean'
    ? located
    : ['1', 'true', 'yes', 'y'].includes(text(located).toLowerCase());
  const lng = finiteMetric(value?.lng ?? value?.longitude);
  const lat = finiteMetric(value?.lat ?? value?.latitude);
  return flag && lng !== null && lat !== null;
}

function cityCodeFromFeature(feature) {
  return text(feature?.properties?.adcode
    ?? feature?.properties?.cityCode
    ?? feature?.properties?.city_code
    ?? feature?.properties?.code);
}

function sourceModel(input) {
  if (isRecord(input?.model)) return input.model;
  return isRecord(input) ? input : {};
}

function sourceKpi(summary, definition) {
  const kpis = Array.isArray(summary?.kpis) ? summary.kpis : [];
  const source = kpis.find(item => definition.aliases.includes(text(item?.key)));
  if (source) return source;
  if (kpis.some(item => text(item?.key).startsWith('retail'))) return null;
  return kpis.find(item => definition.legacySummaryAliases?.includes(text(item?.key))) || null;
}

function readMetricSource(source, aliases) {
  if (!isRecord(source)) return null;
  const metrics = isRecord(source.metrics) ? source.metrics : {};
  for (const alias of aliases) {
    if (Object.prototype.hasOwnProperty.call(metrics, alias)) {
      const nested = metrics[alias];
      if (isRecord(nested)) return { value: nested.value, unit: nested.unit || nested.sourceUnit || '' };
      return { value: nested, unit: source[`${alias}Unit`] || source.unit || '' };
    }
    if (Object.prototype.hasOwnProperty.call(source, alias)) {
      return { value: source[alias], unit: source[`${alias}Unit`] || source.unit || '' };
    }
  }
  return null;
}

function fallbackMetricSource(definition, cityInstitutions, cityRankings) {
  if (cityInstitutions.length !== 1) return null;
  const ranking = cityRankings.length === 1 ? readMetricSource(cityRankings[0], definition.aliases) : null;
  if (ranking && finiteMetric(ranking.value) !== null) return ranking;
  const institution = readMetricSource(cityInstitutions[0], definition.aliases);
  return institution && finiteMetric(institution.value) !== null ? institution : null;
}

function buildCityDetail(code, summary, institutions, rankings, modelDate) {
  const cityInstitutions = institutions.filter(item => cityCodeOf(item) === code);
  const cityRankings = rankings.filter(item => cityCodeOf(item) === code);
  let fallbackUsed = false;
  const detailInstitutions = cityInstitutions.map(item => {
    const orgCode = orgCodeOf(item);
    return { orgCode, orgName: orgNameOf(item, orgCode) };
  });
  const metrics = CITY_METRIC_DEFINITIONS.map(definition => {
    const source = sourceKpi(summary, definition);
    const fallback = source === null ? fallbackMetricSource(definition, cityInstitutions, cityRankings) : null;
    if (fallback) fallbackUsed = true;
    const metricSource = source || fallback;
    const formatted = formatCityMapMetric(metricSource?.value, metricSource?.unit || definition.unit, definition.kind);
    return {
      key: definition.key,
      label: text(source?.label) || definition.label,
      value: formatted
    };
  });
  const detail = {
    institutionCount: cityInstitutions.length,
    locatedCount: cityInstitutions.filter(isLocated).length,
    dataDate: text(summary?.dataDate) || text(modelDate),
    metrics,
    institutions: detailInstitutions
  };
  if (fallbackUsed) detail.scopeLabel = '当前范围单家机构';
  return detail;
}

/**
 * Build the province map's city hover contract from the current authorized
 * model. City summary values are city-level source values; institution metrics
 * are deliberately never added together to invent a city total.
 */
export function buildCityMapDetails(input = {}, options = {}) {
  const model = sourceModel(input);
  const institutions = Array.isArray(model.institutions) ? model.institutions.filter(isRecord) : [];
  const rankings = Array.isArray(model.rankings) ? model.rankings.filter(isRecord) : [];
  const summaries = isRecord(model.citySummaries) ? model.citySummaries : {};
  const featureCodes = Array.isArray(options.cityCodes)
    ? options.cityCodes.map(text)
    : Array.isArray(options.geoJson?.features)
      ? options.geoJson.features.map(cityCodeFromFeature)
      : [];
  const cityCodes = new Set([
    ...Object.keys(summaries).map(text),
    ...institutions.map(cityCodeOf),
    ...featureCodes
  ].filter(Boolean));

  return Object.fromEntries([...cityCodes].map(code => [
    code,
    buildCityDetail(code, summaries[code], institutions, rankings, model.dataDate)
  ]));
}

// Keep a descriptive alias for callers that name the province-map projection.
export const buildProvinceMapCityDetails = buildCityMapDetails;

export { CITY_METRIC_DEFINITIONS };
