const PERCENT_UNIT = '%';

/**
 * 百分比来源只有在各构成项合计处于浮点误差范围内的 100 时，才可以直接落到 100% 基准带。
 * 这里不替来源数据做未经授权的归一化。
 */
export const PERCENT_TOTAL_TOLERANCE = 1e-6;

export const COMPOSITION_COLORS = Object.freeze([
  '#49cbd9',
  '#6877d7',
  '#7894dd',
  '#8d79c9',
  '#4e9dbd',
  '#6b83ad'
]);

/**
 * 读取可参与展示的有限数值。
 * 数字字符串可由查询结果直接传入，空字符串、布尔值和非有限值保持无效。
 */
export function parseCompositionNumber(value) {
  if (typeof value !== 'number' && typeof value !== 'string') return null;
  if (typeof value === 'string' && value.trim() === '') return null;
  try {
    const number = Number(value);
    return Number.isFinite(number) ? number : null;
  } catch {
    return null;
  }
}

function decimalPlaces(value) {
  const source = String(value ?? '').trim().toLowerCase();
  if (!source) return 2;
  if (source.includes('e')) {
    const [mantissa, exponentText] = source.split('e');
    const exponent = Number(exponentText);
    const mantissaPlaces = mantissa.includes('.') ? mantissa.split('.')[1].length : 0;
    return Number.isFinite(exponent) ? Math.max(0, mantissaPlaces - exponent) : 2;
  }
  const decimal = source.split('.')[1];
  return decimal ? decimal.replace(/[^0-9]/g, '').length : 0;
}

/** 保留极小金额的可读边界，避免视觉格式把正值误显示成 0。 */
export function formatCompositionNumber(value) {
  const number = parseCompositionNumber(value);
  if (number === null) return '—';
  if (number > 0 && number < 0.000001) return '<0.000001';
  const maxFractionDigits = Math.min(8, Math.max(2, decimalPlaces(value)));
  const minimumFractionDigits = Number.isInteger(number) ? 0 : Math.min(2, maxFractionDigits);
  return new Intl.NumberFormat('en-US', {
    maximumFractionDigits: maxFractionDigits,
    minimumFractionDigits: minimumFractionDigits
  }).format(number);
}

/** 份额默认显示一位小数，小于 1% 时保留更多位以避免把小项隐藏成 0。 */
export function formatCompositionPercent(value) {
  const number = parseCompositionNumber(value);
  if (number === null) return '—';
  if (number === 0) return '0%';
  if (number > 0 && number < 0.001) return '<0.001%';
  const maximumFractionDigits = Math.abs(number) < 1 ? 3 : 1;
  return `${new Intl.NumberFormat('en-US', {
    maximumFractionDigits,
    minimumFractionDigits: 0
  }).format(number)}%`;
}

function normalizeUnit(value) {
  if (value === null || value === undefined) return '';
  return String(value).trim();
}

function normalizeName(value, index) {
  const name = value === null || value === undefined ? '' : String(value).trim();
  return name || `构成项${index + 1}`;
}

function normalizeItem(source, index) {
  const raw = source && typeof source === 'object' && !Array.isArray(source) ? source : {};
  const value = parseCompositionNumber(raw.value);
  let valueIssue = '';
  if (value === null) valueIssue = '数值缺失或不是有限数值';
  else if (value < 0) valueIssue = '数值不能为负';

  return {
    index,
    name: normalizeName(raw.name, index),
    value,
    rawValue: raw.value,
    unit: normalizeUnit(raw.unit),
    valueText: formatCompositionNumber(raw.value),
    valueIssue,
    share: null,
    shareText: '占比不可计算',
    segmentTitle: '',
    segmentAriaLabel: ''
  };
}

function buildIssueMessage(items, units, total) {
  const reasons = [];
  const hasMissingUnit = items.some(item => !item.unit);
  if (hasMissingUnit || units.length !== 1) reasons.push('单位必须明确且统一');
  if (items.some(item => item.value === null || item.value < 0)) reasons.push('存在缺失、非有限或负值');
  if (!reasons.length && total === 0) return '构成合计为0，无法计算占比';
  if (!reasons.length && total === null) reasons.push('构成合计不是有限数值');
  return `${reasons.join('，')}，不能计算占比`;
}

function setItemLabels(items, mode) {
  return items.map(item => {
    const unitText = item.unit || '单位缺失';
    const numericText = item.valueText === '—' ? '数值 —' : `${item.valueText} ${unitText}`;
    let shareText = item.shareText;
    if (mode === 'percent') shareText = '原始比例';
    const segmentDetail = `${item.name}，${numericText}，${item.share === null ? item.shareText : `占比 ${formatCompositionPercent(item.share)}`}`;
    return {
      ...item,
      shareText,
      segmentTitle: segmentDetail,
      segmentAriaLabel: segmentDetail
    };
  });
}

/**
 * 将业务构成整理为可直接渲染的模型。
 *
 * shareMode 为 amount 时按统一单位的合计计算；为 percent 时保留来源百分比；
 * unavailable 时所有明细仍保留，但不绘制会误导的 100% 份额带。
 */
export function buildCompositionBreakdown(sourceItems = []) {
  const items = Array.isArray(sourceItems) ? sourceItems.map(normalizeItem) : [];
  if (!items.length) {
    return {
      items: [],
      total: null,
      totalText: '—',
      unitLabel: '—',
      shareMode: 'unavailable',
      trackState: 'unavailable',
      statusMessage: '暂无构成数据',
      baseNote: '等待构成明细'
    };
  }

  const units = [...new Set(items.map(item => item.unit).filter(Boolean))];
  const hasMissingUnit = items.some(item => !item.unit);
  const sameUnit = !hasMissingUnit && units.length === 1;
  const allValuesValid = items.every(item => item.value !== null && item.value >= 0);
  const summedValue = sameUnit && allValuesValid
    ? items.reduce((sum, item) => sum + item.value, 0)
    : null;
  const total = summedValue !== null && Number.isFinite(summedValue) ? summedValue : null;
  const unitLabel = sameUnit ? units[0] : (units.length ? '单位待核对' : '单位待补充');

  if (total === null) {
    const statusMessage = buildIssueMessage(items, units, total);
    return {
      items: setItemLabels(items, 'unavailable'),
      total: null,
      totalText: '—',
      unitLabel,
      shareMode: 'unavailable',
      trackState: 'unavailable',
      statusMessage,
      baseNote: ''
    };
  }

  if (total === 0) {
    return {
      items: setItemLabels(items, 'unavailable'),
      total,
      totalText: formatCompositionNumber(total),
      unitLabel,
      shareMode: 'unavailable',
      trackState: 'unavailable',
      statusMessage: '构成合计为0，无法计算占比',
      baseNote: ''
    };
  }

  if (unitLabel === PERCENT_UNIT) {
    if (Math.abs(total - 100) > PERCENT_TOTAL_TOLERANCE) {
      const itemsWithRawRatio = items.map(item => ({
        ...item,
        share: null,
        shareText: '比例口径待核对'
      }));
      return {
        items: setItemLabels(itemsWithRawRatio, 'unavailable'),
        total,
        totalText: formatCompositionNumber(total),
        unitLabel,
        shareMode: 'unavailable',
        trackState: 'unavailable',
        statusMessage: `比例口径待核对（构成项合计 ${formatCompositionPercent(total)}）`,
        baseNote: ''
      };
    }

    const percentItems = items.map(item => ({
      ...item,
      share: item.value,
      shareText: '原始比例'
    }));
    return {
      items: setItemLabels(percentItems, 'percent'),
      total,
      totalText: formatCompositionNumber(total),
      unitLabel,
      shareMode: 'percent',
      trackState: 'valid',
      statusMessage: '',
      baseNote: '来源比例合计约100%'
    };
  }

  const amountItems = items.map(item => ({
    ...item,
    share: (item.value / total) * 100,
    shareText: `占比 ${formatCompositionPercent((item.value / total) * 100)}`
  }));
  return {
    items: setItemLabels(amountItems, 'amount'),
    total,
    totalText: formatCompositionNumber(total),
    unitLabel,
    shareMode: 'amount',
    trackState: 'valid',
    statusMessage: '',
    baseNote: '占比基于构成合计'
  };
}
