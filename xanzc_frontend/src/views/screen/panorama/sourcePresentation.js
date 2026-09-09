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
  'retailNplRate'
]);
const KPI_LABEL_KEY_SET = new Set(KPI_LABEL_KEYS);
const MAX_DATA_NOTICE_LENGTH = 240;
const MAX_METRIC_LABEL_LENGTH = 40;

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

/**
 * 读取已保存的 canvasStyle 展示元数据。
 * @param {object} view 当前大屏视图响应
 * @returns {{ dataNotice: string, metricLabels: Record<string, string> }}
 */
export function resolveSourcePresentation(view) {
  const canvasStyle = canvasStyleOf(view);
  return {
    dataNotice: normalizeText(canvasStyle.dataNotice, MAX_DATA_NOTICE_LENGTH),
    metricLabels: normalizeMetricLabels(canvasStyle.metricLabels)
  };
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

export { KPI_LABEL_KEYS, MAX_DATA_NOTICE_LENGTH, MAX_METRIC_LABEL_LENGTH };
