/**
 * 代码化大屏的来源说明与指标展示标签。
 *
 * 这些字段属于已保存 renderPackage.canvasStyle 的展示元数据：只在运行时
 * 生成给 Dashboard 的展示模型，不写回画布配置，也不参与数据请求或权限判断。
 */

const KPI_LABEL_KEYS = Object.freeze([
  'deposit',
  'depositIncrease',
  'depositAverage',
  'loan',
  'customers',
  'revenue',
  'rate',
  'retailAum',
  'retailDeposit',
  'retailDepositAverage',
  'retailRevenue',
  'retailValueCustomers',
  'retailLoan',
  'retailNplRate',
  'corpDeposit',
  'corpDepositAverage',
  'corpLoan',
  'corpRevenue',
  'corpCustomers',
  'corpNplRate'
]);
const KPI_LABEL_KEY_SET = new Set(KPI_LABEL_KEYS);
const MAX_DATA_NOTICE_LENGTH = 240;
const MAX_SOURCE_AVAILABILITY_MESSAGE_LENGTH = 120;
const MAX_METRIC_LABEL_LENGTH = 40;
const SOURCE_STATUSES = new Set(['AVAILABLE', 'NO_SOURCE', 'NO_ROWS', 'NO_VALUES', 'PARTIAL', 'STALE', 'NO_COMPLETE_BATCH', 'HISTORICAL']);
const SOURCE_SLOTS = new Set([
  'deposit', 'depositIncrease', 'depositAverage', 'loan', 'customers', 'revenue', 'rate', 'trend',
  'ranking', 'composition', 'attention', 'branches', 'branchTrend', 'citySummary',
  'corpDeposit', 'corpDepositAverage', 'corpLoan', 'corpRevenue', 'corpCustomers', 'corpNplRate',
  'corpTrend', 'corpSegments', 'corpRanking', 'corpAttention', 'corpTargets'
]);
const SOURCE_FIELD_KEYS = Object.freeze({
  deposit: new Set(['value', 'change', 'date']),
  depositIncrease: new Set(['value', 'change', 'date']),
  depositAverage: new Set(['value', 'change', 'date']),
  loan: new Set(['value', 'change', 'date']),
  customers: new Set(['value', 'change', 'date']),
  revenue: new Set(['value', 'change', 'date']),
  rate: new Set(['value', 'change', 'date']),
  trend: new Set(['date', 'deposit', 'loan', 'depositIncrease', 'customers', 'rate']),
  composition: new Set(['name', 'value', 'corporate', 'retail']),
  ranking: new Set(['orgCode', 'name', 'value', 'increase', 'average', 'change']),
  attention: new Set(['label', 'count', 'orgCode']),
  branches: new Set(['orgCode', 'orgName', 'cityCode', 'cityName', 'ownerOperatingOrgCode', 'parentOrgCode', 'lng', 'lat', 'coordSys', 'located', 'deposit', 'loan', 'customers', 'target', 'rate']),
  branchTrend: new Set(['date', 'deposit', 'loan', 'customers', 'rate']),
  citySummary: new Set(['orgCode', 'cityCode', 'cityName', 'deposit', 'loan', 'customers', 'revenue', 'rate']),
  corpDeposit: new Set(['value', 'change', 'date']),
  corpDepositAverage: new Set(['value', 'change', 'date']),
  corpLoan: new Set(['value', 'change', 'date']),
  corpRevenue: new Set(['value', 'change', 'date']),
  corpCustomers: new Set(['value', 'change', 'date']),
  corpNplRate: new Set(['value', 'change', 'date']),
  corpTrend: new Set(['date', 'deposit', 'loan']),
  corpSegments: new Set(['name', 'customers', 'loan']),
  corpRanking: new Set(['orgCode', 'name', 'deposit', 'increase', 'rate', 'nplRate']),
  corpAttention: new Set(['label', 'count', 'owner', 'deadline']),
  corpTargets: new Set(['name', 'actual', 'target'])
});
const SOURCE_LABELS = Object.freeze({
  deposit: '存款余额', depositAverage: '存款月均余额',
  loan: '贷款余额', customers: '营销有效归属客户数', revenue: '手工测试收入', rate: '目标完成率',
  depositIncrease: '存款较上月净增', trend: '经营趋势', composition: '业务构成',
  ranking: '机构排名', attention: '经营关注', branches: '支行机构', branchTrend: '支行趋势',
  citySummary: '城市汇总', corporate: '对公业务', retail: '零售业务', increase: '存款较上月净增',
  average: '存款月均余额', value: '指标值', date: '数据日期', orgCode: '机构号',
  corpDeposit: '对公存款余额', corpDepositAverage: '对公存款月日均', corpLoan: '对公贷款余额',
  corpRevenue: '对公营业收入', corpCustomers: '有效对公客户', corpNplRate: '对公不良率',
  corpTrend: '对公经营趋势', corpSegments: '对公重点客群', corpRanking: '对公机构排名',
  corpAttention: '对公经营关注', corpTargets: '对公目标',
  owner: '责任部门/人', deadline: '截止日期', actual: '实际值', target: '目标值',
  nplRate: '对公贷款不良率'
});

function isRecord(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function parseRecord(value) {
  if (isRecord(value)) return value;
  if (typeof value !== 'string' || !value.trim()) return {};
  try {
    const parsed = JSON.parse(value);
    return isRecord(parsed) ? parsed : {};
  } catch {
    return {};
  }
}

function own(source, key) {
  return Object.prototype.hasOwnProperty.call(source, key);
}

function normalizeText(value, maxLength, rejectMarkup = false) {
  if (typeof value !== 'string') return '';
  const text = value.trim();
  if (!text || Array.from(text).length > maxLength) return '';
  // Keep control characters out of the UI. Vue interpolation still escapes the
  // notice, so angle brackets in a notice remain literal text rather than HTML.
  if (/[\u0000-\u001F\u007F]/.test(text)) return '';
  if (rejectMarkup && /[<>]/.test(text)) return '';
  return text;
}

function canvasStyleOf(view) {
  const packageValue = view?.renderPackage ?? view?.render_package
    ?? view?.renderPackageJson ?? view?.render_package_json;
  const renderPackage = parseRecord(packageValue);
  return parseRecord(renderPackage.canvasStyle ?? renderPackage.canvas_style);
}

function normalizeMetricLabels(value) {
  const source = parseRecord(value);
  const labels = {};
  for (const key of KPI_LABEL_KEYS) {
    if (!own(source, key)) continue;
    const label = normalizeText(source[key], MAX_METRIC_LABEL_LENGTH, true);
    if (label) labels[key] = label;
  }
  return labels;
}

function normalizeDate(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value.trim())) return '';
  const date = value.trim();
  const parsed = new Date(`${date}T00:00:00Z`);
  return Number.isNaN(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== date ? '' : date;
}

function normalizeAvailabilityEntry(value) {
  const source = parseRecord(value);
  const status = SOURCE_STATUSES.has(source.status) ? source.status : '';
  if (!status) return {};
  const result = { status, message: normalizeText(source.message, MAX_SOURCE_AVAILABILITY_MESSAGE_LENGTH, true) };
  const dataDate = normalizeDate(source.dataDate);
  if (dataDate) result.dataDate = dataDate;
  return result;
}

function normalizeSourceAvailability(value) {
  const source = parseRecord(value);
  const result = {};
  for (const slot of SOURCE_SLOTS) {
    if (!own(source, slot)) continue;
    const entry = parseRecord(source[slot]);
    const normalized = {};
    const direct = normalizeAvailabilityEntry(entry);
    if (!direct.status) continue;
    Object.assign(normalized, direct);
    const fields = parseRecord(entry.fields);
    const fieldResult = {};
    for (const [semantic, fieldValue] of Object.entries(fields)) {
      if (!SOURCE_FIELD_KEYS[slot]?.has(semantic)) continue;
      const field = normalizeAvailabilityEntry(fieldValue);
      if (!field.status && !field.message && !field.dataDate) continue;
      fieldResult[semantic] = field;
    }
    if (Object.keys(fieldResult).length) normalized.fields = fieldResult;
    if (Object.keys(normalized).length) result[slot] = normalized;
  }
  return result;
}

/**
 * 读取已保存的 canvasStyle 展示元数据。
 * @param {object} view 当前大屏视图响应
 * @returns {{ dataNotice: string, metricLabels: Record<string, string> }}
 */
export function resolveSourcePresentation(view) {
  const canvasStyle = canvasStyleOf(view);
  return {
    dataNotice: normalizeText(canvasStyle.dataNotice, MAX_DATA_NOTICE_LENGTH),
    metricLabels: normalizeMetricLabels(canvasStyle.metricLabels),
    sourceAvailability: normalizeSourceAvailability(canvasStyle.sourceAvailability)
  };
}

/** 返回具体语义的运行时/静态状态，供组件就近展示缺失原因。 */
export function resolveDataStatus(presentation, runtimeIssues, slot, semantic = '') {
  const issues = Array.isArray(runtimeIssues)
    ? runtimeIssues.filter(item => item?.slot === slot)
    : runtimeIssues?.[slot];
  const issueList = Array.isArray(issues) ? issues : [];
  const availability = presentation?.sourceAvailability?.[slot];
  const staticEntry = availability?.fields?.[semantic] || availability;
  const issue = issueList.find(item => item?.field === 'request' || item?.code === 'REQUEST_FAILED')
    || issueList.find(item => item?.field === semantic)
    || issueList.find(item => !item?.field || item.field === 'value' || !semantic);
  if (issue?.code === 'NO_VALUES' && ['NO_VALUES', 'PARTIAL'].includes(staticEntry?.status) && staticEntry.message) {
    return { status: staticEntry.status, message: staticEntry.message };
  }
  if (issue) {
    if (issue.code === 'NO_VALUES') {
      const label = SOURCE_LABELS[semantic] || SOURCE_LABELS[slot] || '当前指标';
      return { status: 'NO_VALUES', message: `${label}当前无有效值` };
    }
    return { status: 'RUNTIME', message: normalizeText(issue.message, MAX_DATA_NOTICE_LENGTH, true) || '取数失败' };
  }
  // Quality is returned by report for the whole batch. It takes precedence over
  // a static AVAILABLE declaration, which only describes configured source
  // readiness and cannot certify this refresh's data.
  const runtimeQuality = presentation?.runtimeQuality;
  const qualityStatus = String(runtimeQuality?.guardStatus || runtimeQuality?.status || '').toUpperCase();
  if (qualityStatus && qualityStatus !== 'COMPLETE') {
    return {
      status: qualityStatus,
      message: normalizeText(runtimeQuality?.guardMessage || runtimeQuality?.message, MAX_DATA_NOTICE_LENGTH, true)
        || ({ STALE: '当前展示为超过时效的完整批次', PARTIAL: '本次批次数据不完整', NO_COMPLETE_BATCH: '当前没有完整批次' }[qualityStatus] || '本次批次质量不可用')
    };
  }
  if (runtimeQuality?.mixedPeriod === true) {
    return { status: 'MIXED_PERIOD', message: '本次批次存在混合统计期间，请核对数据日期' };
  }
  if (staticEntry?.status && staticEntry.status !== 'AVAILABLE') {
    return { status: staticEntry.status, message: staticEntry.message || '暂无有效数据' };
  }
  return { status: 'UNAVAILABLE', message: '暂无有效数据' };
}

/**
 * 只为 Dashboard 创建不可变的 KPI 展示覆盖模型。
 * 数值、单位、数组和原始 model 均保持不变，未知 key 与无效标签不处理。
 * @param {object} model usePanoramaData 返回的模型
 * @param {object} metricLabels canvasStyle.metricLabels
 * @returns {object} 仅在需要覆盖时创建浅副本的展示模型
 */
export function applyMetricLabels(model, metricLabels) {
  if (!isRecord(model) || !Array.isArray(model.kpis)) return model;
  const labels = normalizeMetricLabels(metricLabels);
  if (!Object.keys(labels).length) return model;

  let changed = false;
  const kpis = model.kpis.map(item => {
    if (!isRecord(item) || typeof item.key !== 'string' || !KPI_LABEL_KEY_SET.has(item.key)) return item;
    const label = labels[item.key];
    if (!label || item.label === label) return item;
    changed = true;
    return { ...item, label };
  });
  return changed ? { ...model, kpis } : model;
}

export { KPI_LABEL_KEYS, MAX_DATA_NOTICE_LENGTH, MAX_SOURCE_AVAILABILITY_MESSAGE_LENGTH, MAX_METRIC_LABEL_LENGTH, SOURCE_STATUSES };
