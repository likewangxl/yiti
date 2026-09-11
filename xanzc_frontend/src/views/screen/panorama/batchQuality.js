/**
 * 分行大屏批次质量契约的前端边界。
 *
 * Quality 是 report 返回的权威批次信息。这里仅做字段归一化、批次身份
 * 比对和展示状态合并，不根据 rows、列名或当前时间猜造质量字段。
 */

export const BATCH_QUALITY_STATUSES = Object.freeze([
  'COMPLETE', 'STALE', 'NO_COMPLETE_BATCH', 'PARTIAL'
]);

const USABLE_STATUSES = new Set(['COMPLETE', 'STALE']);
const VALID_DATA_CLASSIFICATIONS = new Set(['TEST', 'PROD']);

function isRecord(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function pick(value, ...keys) {
  if (!isRecord(value)) return undefined;
  for (const key of keys) {
    if (value[key] !== undefined && value[key] !== null) return value[key];
  }
  return undefined;
}

function text(value) {
  if (value === undefined || value === null) return null;
  const result = String(value).trim();
  return result || null;
}

function integer(value) {
  if (value === undefined || value === null || value === '') return null;
  const result = Number(value);
  return Number.isInteger(result) ? result : null;
}

function booleanOrNull(value) {
  if (value === undefined || value === null || value === '') return null;
  if (typeof value === 'boolean') return value;
  if (value === 1 || value === '1' || String(value).toLowerCase() === 'true') return true;
  if (value === 0 || value === '0' || String(value).toLowerCase() === 'false') return false;
  return null;
}

function cloneValue(value) {
  if (Array.isArray(value)) return value.map(cloneValue);
  if (isRecord(value)) return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, cloneValue(item)]));
  return value;
}

function list(value) {
  if (Array.isArray(value)) return value.map(cloneValue);
  if (value === undefined || value === null || value === '') return [];
  return [cloneValue(value)];
}

function normalizeHistoryCoverage(value) {
  if (!Array.isArray(value)) return [];
  return value.filter(isRecord).map(item => ({
    dataDate: text(pick(item, 'dataDate', 'data_date')),
    expected: integer(pick(item, 'expected')),
    received: integer(pick(item, 'received')),
    expectedSubjects: integer(pick(item, 'expectedSubjects', 'expected_subjects')),
    receivedSubjects: integer(pick(item, 'receivedSubjects', 'received_subjects')),
    complete: booleanOrNull(pick(item, 'complete')),
    missingSubjects: list(pick(item, 'missingSubjects', 'missing_subjects')),
    missing: list(pick(item, 'missing'))
  }));
}

/** 从 axios 解包结果或直接响应中取出二维表响应。 */
export function unwrapBatchResponse(response) {
  if (!isRecord(response)) return response;
  if (response.columns === undefined && response.rows === undefined && isRecord(response.data)) {
    return response.data;
  }
  return response;
}

/** 只读取响应里的 quality，不从 rows、时间戳或静态配置推断。 */
export function readBatchQuality(response) {
  const table = unwrapBatchResponse(response);
  if (!isRecord(table)) return null;
  const raw = isRecord(table.quality)
    ? table.quality
    : isRecord(table.meta?.quality) ? table.meta.quality : null;
  return normalizeBatchQuality(raw);
}

/** 兼容 Java Jackson camelCase 与历史 snake_case，未知字段不扩散到展示模型。 */
export function normalizeBatchQuality(raw) {
  if (!isRecord(raw)) return null;
  const statusValue = text(pick(raw, 'status', 'qualityStatus', 'quality_status'));
  const status = statusValue && BATCH_QUALITY_STATUSES.includes(statusValue.toUpperCase())
    ? statusValue.toUpperCase() : statusValue;
  const sourceAsOf = pick(raw, 'sourceAsOf', 'source_as_of');
  const classification = text(pick(raw, 'dataClassification', 'data_classification'));
  return {
    batchId: text(pick(raw, 'batchId', 'batch_id')),
    dataDate: text(pick(raw, 'dataDate', 'data_date')),
    version: text(pick(raw, 'version')),
    dataClassification: classification ? classification.toUpperCase() : null,
    status: status || null,
    calculatedAt: text(pick(raw, 'calculatedAt', 'calculated_at')),
    sourceAsOf: isRecord(sourceAsOf) || Array.isArray(sourceAsOf) ? cloneValue(sourceAsOf) : null,
    expected: integer(pick(raw, 'expected')),
    received: integer(pick(raw, 'received')),
    expectedSubjects: integer(pick(raw, 'expectedSubjects', 'expected_subjects')),
    receivedSubjects: integer(pick(raw, 'receivedSubjects', 'received_subjects')),
    missingSubjects: list(pick(raw, 'missingSubjects', 'missing_subjects')),
    missing: list(pick(raw, 'missing')),
    mixedPeriod: booleanOrNull(pick(raw, 'mixedPeriod', 'mixed_period')),
    selectedComplete: booleanOrNull(pick(raw, 'selectedComplete', 'selected_complete')),
    newerIncomplete: list(pick(raw, 'newerIncomplete', 'newer_incomplete')),
    historyCoverage: normalizeHistoryCoverage(pick(raw, 'historyCoverage', 'history_coverage')),
    maxAgeDays: integer(pick(raw, 'maxAgeDays', 'max_age_days')),
    ageDays: integer(pick(raw, 'ageDays', 'age_days')),
    message: text(pick(raw, 'message'))
  };
}

export function isUsableBatchQuality(quality) {
  const normalized = normalizeBatchQuality(quality);
  return Boolean(normalized
    && USABLE_STATUSES.has(normalized.status)
    && normalized.batchId
    && normalized.dataDate
    && normalized.version
    && VALID_DATA_CLASSIFICATIONS.has(normalized.dataClassification)
    && normalized.selectedComplete === true
  );
}

function identityEqual(left, right) {
  return left?.batchId === right?.batchId
    && left?.dataDate === right?.dataDate
    && left?.version === right?.version
    && left?.dataClassification === right?.dataClassification;
}

/** 锁定首响应后，所有后续 response 都必须通过此身份检查。 */
export function compareBatchQuality(reference, candidate) {
  const expected = normalizeBatchQuality(reference);
  const actual = normalizeBatchQuality(candidate);
  if (!actual) return { ok: false, code: 'QUALITY_MISSING', message: '响应缺少 report Quality' };
  if (!isUsableBatchQuality(expected)) {
    return { ok: false, code: 'ANCHOR_NOT_USABLE', message: expected?.message || '首响应没有可用完整批次' };
  }
  if (!isUsableBatchQuality(actual)) {
    return {
      ok: false,
      code: 'QUALITY_NOT_USABLE',
      message: actual.message || `批次质量状态不可用: ${actual.status || 'UNKNOWN'}`
    };
  }
  if (!identityEqual(expected, actual)) {
    const classificationMismatch = expected.dataClassification !== actual.dataClassification;
    return {
      ok: false,
      code: classificationMismatch ? 'DATA_CLASSIFICATION_MISMATCH' : 'BATCH_MISMATCH',
      message: classificationMismatch
        ? '响应数据分类与首响应不一致'
        : '响应批次、版本或数据日期与首响应不一致'
    };
  }
  return { ok: true, reference: expected, candidate: actual };
}

function stableItemKey(value) {
  if (isRecord(value)) {
    return JSON.stringify(Object.keys(value).sort().reduce((out, key) => {
      out[key] = value[key];
      return out;
    }, {}));
  }
  return String(value);
}

function unionLists(qualities, key) {
  const values = qualities.flatMap(item => Array.isArray(item?.[key]) ? item[key] : []);
  const seen = new Set();
  return values.filter(item => {
    const itemKey = stableItemKey(item);
    if (seen.has(itemKey)) return false;
    seen.add(itemKey);
    return true;
  });
}

function mergeSourceAsOf(qualities) {
  const result = {};
  for (const quality of qualities) {
    const source = quality?.sourceAsOf;
    if (isRecord(source)) Object.assign(result, cloneValue(source));
    else if (Array.isArray(source)) {
      for (const item of source) {
        if (!isRecord(item)) continue;
        const key = text(pick(item, 'source', 'name', 'key'));
        if (key) result[key] = cloneValue(pick(item, 'asOf', 'sourceAsOf', 'value'));
      }
    }
  }
  return Object.keys(result).length ? result : null;
}

function maxKnown(left, right) {
  if (Number.isInteger(left) && Number.isInteger(right)) return Math.max(left, right);
  return Number.isInteger(left) ? left : Number.isInteger(right) ? right : null;
}

function mergeHistoryCoverage(qualities) {
  const byDate = new Map();
  let anonymousIndex = 0;
  for (const quality of qualities) {
    for (const item of quality?.historyCoverage || []) {
      const key = item.dataDate || `__missing_date_${anonymousIndex++}`;
      const previous = byDate.get(key);
      if (!previous) {
        byDate.set(key, {
          ...item,
          missingSubjects: [...(item.missingSubjects || [])],
          missing: [...(item.missing || [])]
        });
        continue;
      }
      const complete = previous.complete === false || item.complete === false
        ? false
        : previous.complete === true && item.complete === true
          ? true : (previous.complete ?? item.complete ?? null);
      byDate.set(key, {
        ...previous,
        expected: maxKnown(previous.expected, item.expected),
        received: maxKnown(previous.received, item.received),
        expectedSubjects: maxKnown(previous.expectedSubjects, item.expectedSubjects),
        receivedSubjects: maxKnown(previous.receivedSubjects, item.receivedSubjects),
        complete,
        missingSubjects: unionLists([previous, item], 'missingSubjects'),
        missing: unionLists([previous, item], 'missing')
      });
    }
  }
  return [...byDate.values()].sort((left, right) => String(left.dataDate || '').localeCompare(String(right.dataDate || '')));
}

/** 合并同一不可变批次的来源质量；只合并已通过身份检查的响应。 */
export function mergeBatchQualities(values = []) {
  const qualities = values.map(normalizeBatchQuality).filter(Boolean);
  if (!qualities.length) return null;
  const first = qualities[0];
  const result = {
    ...first,
    sourceAsOf: mergeSourceAsOf(qualities),
    historyCoverage: mergeHistoryCoverage(qualities),
    missingSubjects: unionLists(qualities, 'missingSubjects'),
    missing: unionLists(qualities, 'missing'),
    newerIncomplete: unionLists(qualities, 'newerIncomplete'),
    mixedPeriod: qualities.some(item => item.mixedPeriod === true) ? true : first.mixedPeriod,
    status: qualities.some(item => item.status === 'PARTIAL')
      ? 'PARTIAL' : qualities.some(item => item.status === 'STALE') ? 'STALE' : first.status
  };
  const messages = [...new Set(qualities.map(item => item.message).filter(Boolean))];
  result.message = messages.length ? messages.join('；') : null;
  return result;
}

export function batchQualityLabel(status) {
  return ({
    COMPLETE: '完整批次',
    STALE: '过期完整批次',
    PARTIAL: '部分批次',
    NO_COMPLETE_BATCH: '无完整批次'
  })[String(status || '').toUpperCase()] || '批次质量未知';
}

export { USABLE_STATUSES };
