const DISPLAY_UNITS = Object.freeze({
  YUAN: '元',
  TEN_THOUSAND: '万元',
  TEN_THOUSAND_YUAN: '万元',
  HUNDRED_MILLION: '亿元',
  HUNDRED_MILLION_YUAN: '亿元',
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

// Summary keys are business-specific; generic ranking fields belong to the
// business model passed by the page and are never sourced from branch KPIs.
function businessDefinitions(business) {
  const retail = business === 'retail';
  const prefix = retail ? 'retail' : 'corp';
  const name = retail ? '零售' : '对公';
  const definitions = [
    ['deposit', `${name}存款余额`, 'Deposit', ['deposit', 'depositBalance', 'balance'], '亿元', 'amount'],
    ['average', `${name}存款月日均`, 'DepositAverage', ['average', 'depositAverage', 'averageDeposit', 'monthAverage'], '亿元', 'amount'],
    ['loan', retail ? '个人贷款' : '对公贷款余额', 'Loan', ['loan'], '亿元', 'amount'],
    ['increase', `${name}存款净增`, 'DepositIncrease', ['increase', 'depositIncrease'], '亿元', 'amount'],
    ['revenue', `${name}营业收入`, 'Revenue', ['revenue'], '亿元', 'amount'],
    [retail ? 'valueCustomers' : 'customers', retail ? '零售价值客户' : '有效对公客户', retail ? 'ValueCustomers' : 'Customers', retail ? ['valueCustomers'] : ['customers'], '万户', 'customers'],
    ['rate', `${name}目标完成率`, 'CompletionRate', ['rate', 'completionRate'], '%', 'rate'],
    ['nplRate', retail ? '个贷不良率' : '对公不良率', 'NplRate', ['nplRate'], '%', 'rate']
  ];
  if (retail) definitions.unshift(['aum', '零售AUM', 'Aum', ['aum'], '亿元', 'amount']);
  return definitions.map(([key, label, suffix, aliases, unit, kind]) => ({ key, label, aliases, summaryAliases: [`${prefix}${suffix}`], legacySummaryAliases: !retail ? ({ deposit: ['deposit'], increase: ['increase', 'depositIncrease'], rate: ['rate', 'completionRate'] }[key] || []) : [], unit, kind }));
}

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
  const source = kpis.find(item => (definition.summaryAliases || definition.aliases).includes(text(item?.key)));
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

function fallbackMetricSource(definition, cityInstitutions, cityRankings, business) {
  if (cityInstitutions.length !== 1) return null;
  const ranking = cityRankings.length === 1 && (!business || (orgCodeOf(cityInstitutions[0]) && orgCodeOf(cityRankings[0]) === orgCodeOf(cityInstitutions[0]))) ? readMetricSource(cityRankings[0], definition.aliases) : null;
  if (ranking && finiteMetric(ranking.value) !== null) return ranking;
  if (business) return null;
  const institution = readMetricSource(cityInstitutions[0], definition.aliases);
  return institution && finiteMetric(institution.value) !== null ? institution : null;
}

function buildCityDetail(code, summary, institutions, rankings, modelDate, definitions, business, options) {
  const cityInstitutions = institutions.filter(item => cityCodeOf(item) === code);
  const cityRankings = rankings.filter(item => cityCodeOf(item) === code);
  let fallbackUsed = false;
  const detailInstitutions = cityInstitutions.map(item => {
    const orgCode = orgCodeOf(item);
    const identity = { orgCode, orgName: orgNameOf(item, orgCode) };
    if (!business) return identity;
    const matches = cityRankings.filter(row => orgCodeOf(row) === orgCode);
    const ranking = orgCode && matches.length === 1 ? matches[0] : null;
    return { ...identity, dataDate: ranking ? (text(ranking.date ?? ranking.dataDate) || text(modelDate)) : '', metrics: definitions
      .filter(definition => ['deposit', 'average', 'aum', 'increase', 'rate', 'nplRate'].includes(definition.key))
      .map(definition => {
        const source = readMetricSource(ranking, definition.aliases);
        return { key: definition.key, label: definition.label, value: formatCityMapMetric(source?.value, source?.unit || definition.unit, definition.kind) };
      }) };

  });
  const metrics = definitions.map(definition => {
    const source = options.allowCityMetrics === false ? null : sourceKpi(summary, definition);
    const fallback = source === null && options.allowCityMetrics !== false ? fallbackMetricSource(definition, cityInstitutions, cityRankings, business) : null;
    if (fallback) fallbackUsed = true;
    const metricSource = source || fallback;
    const formatted = formatCityMapMetric(metricSource?.value, metricSource?.unit || definition.unit, definition.kind);
    return {
      key: definition.key,
      label: text(source?.label) || definition.label,
      value: formatted
    };
  });
  const institutionDates = [...new Set(detailInstitutions.map(item => item.dataDate).filter(Boolean))];
  const detail = {
    institutionCount: cityInstitutions.length,
    locatedCount: cityInstitutions.filter(isLocated).length,
    dataDate: (options.allowCityMetrics === false ? '' : text(summary?.dataDate)) || (business ? (institutionDates.length === 1 ? institutionDates[0] : '') : text(modelDate)),
    metrics,
    institutions: detailInstitutions
  };
  if (options.scopeLabel) detail.scopeLabel = options.scopeLabel;
  else if (fallbackUsed) detail.scopeLabel = '当前范围单家机构';
  else if (business && !metrics.some(metric => metric.value !== '暂无数据')) detail.scopeLabel = '地市汇总未接入 · 下列为机构明细';
  return detail;
}

/**
 * Build the province map's city hover contract from the current authorized
 * model. City summary values are city-level source values; institution metrics
 * are deliberately never added together to invent a city total.
 */
export function buildCityMapDetails(input = {}, options = {}) {
  const model = sourceModel(input);
  const business = ['retail', 'corporate'].includes(options.business) ? options.business : '';
  const definitions = business ? businessDefinitions(business) : CITY_METRIC_DEFINITIONS;
  // Runtime quality dates belong to a specific source slot; the model date may
  // have been populated by an unrelated KPI and is unsafe for business rows.
  const detailSourceDate = business
    ? model.sourceQualities?.[business === 'retail' ? 'retailRanking' : 'corpRanking']?.dataDate
    : model.dataDate;
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
    buildCityDetail(code, summaries[code], institutions, rankings, detailSourceDate, definitions, business, options)
  ]));
}

// Keep a descriptive alias for callers that name the province-map projection.
export const buildProvinceMapCityDetails = buildCityMapDetails;

export { CITY_METRIC_DEFINITIONS };

/** Project the same vetted metric as the hover card; never synthesize a total. */
export function cityMapMetricValues(details, key) {
  return Object.fromEntries(Object.entries(details).flatMap(([code, detail]) => {
    const metric = detail.metrics.find(item => item.key === key);
    if (metric && metric.value !== '暂无数据') return [[code, metric.value]];
    return detail.institutionCount > 0 ? [[code, `${detail.institutionCount}家机构`]] : [];
  }));
}
