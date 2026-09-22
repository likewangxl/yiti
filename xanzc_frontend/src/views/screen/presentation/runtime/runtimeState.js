/**
 * 新展示协议的运行时状态模型。
 *
 * 运行时状态只描述当前一轮已经拿到的证据：它不会从标题、旧组件或
 * 其他槽位的数值推断缺失值，也不会把不同来源日期合并成一个快照。
 * 查询本身仍由 usePanoramaData 负责；本模块只负责稳定的状态归一化和
 * 来源说明，便于所有展示组件使用同一套语义。
 */

export const RUNTIME_STATUS = Object.freeze({
  READY: 'READY',
  LOADING: 'LOADING',
  UNCONFIGURED: 'UNCONFIGURED',
  NO_DATA: 'NO_DATA',
  NOT_APPLICABLE: 'NOT_APPLICABLE',
  STALE: 'STALE',
  ERROR: 'ERROR',
  PERMISSION_DENIED: 'PERMISSION_DENIED'
});

export const RUNTIME_STATUS_LABELS = Object.freeze({
  [RUNTIME_STATUS.READY]: '可用',
  [RUNTIME_STATUS.LOADING]: '正在刷新',
  [RUNTIME_STATUS.UNCONFIGURED]: '未配置',
  [RUNTIME_STATUS.NO_DATA]: '缺数',
  [RUNTIME_STATUS.NOT_APPLICABLE]: '目标不适用',
  [RUNTIME_STATUS.STALE]: '过期',
  [RUNTIME_STATUS.ERROR]: '错误',
  [RUNTIME_STATUS.PERMISSION_DENIED]: '权限失效'
});

const QUALITY_STATUSES = new Set(['COMPLETE', 'STALE', 'PARTIAL', 'NO_COMPLETE_BATCH']);
const STATIC_UNCONFIGURED_STATUSES = new Set(['NO_SOURCE', 'UNCONFIGURED', 'MISSING_CONFIG', 'MISSING_BINDING_SNAPSHOT', 'MISSING_DATASOURCE']);
const STATIC_NOT_APPLICABLE_STATUSES = new Set([
  'NOT_APPLICABLE', 'NOT_APPLICABLE_TARGET', 'TARGET_NOT_APPLICABLE', 'UNSUPPORTED', 'SLOT_NOT_ALLOWED_FOR_TEMPLATE'
]);
const INSTITUTION_CONFIG_CODES = new Set([
  'AUTHORIZED_DIRECTORY_REQUIRED', 'FILTER_RULES_UNCONFIRMED', 'ORG_CODE_MISSING',
  'OPERATING_LEVEL_MISSING', 'OPERATING_LEVEL_NOT_ALLOWED', 'ORG_NATURE_MISSING',
  'ORG_NATURE_NOT_ALLOWED', 'UNAUTHORIZED', 'INACTIVE', 'DUPLICATE_ORG_CODE'
]);
const INSTITUTION_DATA_CODES = new Set([
  'UNAUTHORIZED_CONTRIBUTION', 'DUPLICATE_CONTRIBUTION', 'PARENT_CHILD_OVERLAP',
  'OPERATING_OWNERSHIP_OVERLAP'
]);
const NO_DATA_CODES = new Set([
  'NO_DATA', 'NO_VALUES', 'NO_ROWS', 'NULL_RESPONSE', 'QUALITY_MISSING', 'NO_COMPLETE_BATCH',
  'MISSING_VALUE', 'MISSING_FIELD', 'MISSING_COLUMN'
]);
const PERMISSION_CODES = new Set(['PERMISSION_DENIED', 'FORBIDDEN', 'UNAUTHORIZED', 'AUTH_EXPIRED', 'HTTP_401', 'HTTP_403']);

function isRecord(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function text(value) {
  return value === null || value === undefined ? '' : String(value).trim();
}

function positiveStatus(value) {
  const number = Number(value);
  return number === 401 || number === 403 ? number : null;
}

function statusFromError(value) {
  if (!isRecord(value)) return null;
  const status = positiveStatus(value.status ?? value.response?.status ?? value.response?.data?.status
    ?? value.response?.data?.code ?? value.code);
  return status;
}

function issueList(runtimeIssues, slot) {
  if (Array.isArray(runtimeIssues)) return runtimeIssues.filter(item => item?.slot === slot);
  const value = runtimeIssues?.[slot];
  return Array.isArray(value) ? value : [];
}

function issueMessage(issue, fallback = '') {
  return text(issue?.message || issue?.detail || issue?.error || fallback);
}

function issueStatus(issue) {
  const rawStatus = text(issue?.status || issue?.code).toUpperCase();
  if (PERMISSION_CODES.has(rawStatus) || positiveStatus(issue?.status ?? issue?.statusCode ?? issue?.httpStatus)) {
    return RUNTIME_STATUS.PERMISSION_DENIED;
  }
  if (rawStatus === 'STALE' || rawStatus === 'EXPIRED' || rawStatus === 'EXPIRED_BATCH') {
    return RUNTIME_STATUS.STALE;
  }
  if (STATIC_NOT_APPLICABLE_STATUSES.has(rawStatus)) return RUNTIME_STATUS.NOT_APPLICABLE;
  if (STATIC_UNCONFIGURED_STATUSES.has(rawStatus)) return RUNTIME_STATUS.UNCONFIGURED;
  if (INSTITUTION_CONFIG_CODES.has(rawStatus)) return RUNTIME_STATUS.UNCONFIGURED;
  if (INSTITUTION_DATA_CODES.has(rawStatus)) return RUNTIME_STATUS.ERROR;
  if (NO_DATA_CODES.has(rawStatus)) return RUNTIME_STATUS.NO_DATA;
  if (rawStatus === 'MISSING_BRANCH_CONTEXT') return RUNTIME_STATUS.NOT_APPLICABLE;
  if (rawStatus === 'REQUEST_FAILED' || rawStatus === 'INVALID_REQUEST' || rawStatus === 'QUALITY_NOT_USABLE'
    || rawStatus === 'BATCH_MISMATCH' || rawStatus === 'DATA_CLASSIFICATION_MISMATCH') {
    return RUNTIME_STATUS.ERROR;
  }
  return '';
}

function qualityStatus(quality, guard) {
  const guardStatus = text(guard?.status || guard?.guardStatus).toUpperCase();
  const raw = guardStatus || text(quality?.status).toUpperCase();
  if (raw === 'STALE' || raw === 'EXPIRED') return RUNTIME_STATUS.STALE;
  if (raw === 'PARTIAL' || raw === 'NO_COMPLETE_BATCH') return RUNTIME_STATUS.NO_DATA;
  return '';
}

function qualityMessage(quality, guard, status) {
  const explicit = issueMessage(guard) || issueMessage(quality);
  if (explicit) return explicit;
  return status === RUNTIME_STATUS.STALE ? '当前展示为过期完整批次' : status === RUNTIME_STATUS.NO_DATA ? '当前没有完整数据' : '';
}

function normalizeDate(value) {
  return text(value);
}

function dateOfQuality(quality) {
  return normalizeDate(quality?.dataDate || quality?.data_date);
}

function configuredSet(value) {
  if (Array.isArray(value)) return new Set(value.map(text).filter(Boolean));
  if (value instanceof Set) return new Set([...value].map(text).filter(Boolean));
  if (isRecord(value)) return new Set(Object.keys(value).filter(key => value[key]));
  return new Set();
}

function staticEntry(staticAvailability, slot) {
  const value = staticAvailability?.[slot];
  return isRecord(value) ? value : {};
}

function stateRank(status) {
  return ({
    [RUNTIME_STATUS.PERMISSION_DENIED]: 6,
    [RUNTIME_STATUS.ERROR]: 5,
    [RUNTIME_STATUS.STALE]: 4,
    [RUNTIME_STATUS.NO_DATA]: 3,
    [RUNTIME_STATUS.NOT_APPLICABLE]: 2,
    [RUNTIME_STATUS.UNCONFIGURED]: 1,
    [RUNTIME_STATUS.LOADING]: 1,
    [RUNTIME_STATUS.READY]: 0
  })[status] ?? 0;
}

function statusText(status) {
  return RUNTIME_STATUS_LABELS[status] || status || '未知';
}

/**
 * 归一化单个展示槽位。`valuePresent: true` 明确包含合法零值；只有
 * `false` 或明确缺数 issue 才会返回 NO_DATA。
 */
export function buildRuntimeSlotState(input = {}) {
  const configured = input.configured !== false;
  const applicable = input.applicable !== false;
  const issues = Array.isArray(input.issues) ? input.issues : [];
  const quality = isRecord(input.quality) ? input.quality : null;
  const guard = isRecord(input.qualityGuard) ? input.qualityGuard : null;
  const permission = positiveStatus(input.permissionStatus) || statusFromError(input.error);
  const staticStatus = text(input.staticStatus || input.staticAvailability?.status).toUpperCase();
  const issueState = issues.map(issueStatus).find(Boolean) || '';
  let status = RUNTIME_STATUS.READY;
  let message = '';

  if (permission || issueState === RUNTIME_STATUS.PERMISSION_DENIED) {
    status = RUNTIME_STATUS.PERMISSION_DENIED;
    message = issueMessage(issues.find(item => issueStatus(item) === status), input.error?.message || '当前会话无权读取数据');
  } else if (!configured || STATIC_UNCONFIGURED_STATUSES.has(staticStatus) || issueState === RUNTIME_STATUS.UNCONFIGURED) {
    status = RUNTIME_STATUS.UNCONFIGURED;
    message = issueMessage(issues.find(item => issueStatus(item) === status), input.staticAvailability?.message || '当前槽位未配置数据来源');
  } else if (!applicable || STATIC_NOT_APPLICABLE_STATUSES.has(staticStatus) || issueState === RUNTIME_STATUS.NOT_APPLICABLE) {
    status = RUNTIME_STATUS.NOT_APPLICABLE;
    message = issueMessage(issues.find(item => issueStatus(item) === status), input.staticAvailability?.message || '当前目标不适用');
  } else if (qualityStatus(quality, guard) === RUNTIME_STATUS.STALE) {
    status = RUNTIME_STATUS.STALE;
    message = qualityMessage(quality, guard, status);
  } else if (issueState === RUNTIME_STATUS.ERROR) {
    status = RUNTIME_STATUS.ERROR;
    message = issueMessage(issues.find(item => issueStatus(item) === status), '当前数据请求失败');
  } else if (text(input.error)) {
    status = RUNTIME_STATUS.ERROR;
    message = text(input.error);
  } else if (issueState === RUNTIME_STATUS.NO_DATA || qualityStatus(quality, guard) === RUNTIME_STATUS.NO_DATA
    || input.valuePresent === false) {
    status = RUNTIME_STATUS.NO_DATA;
    message = issueMessage(issues.find(item => issueStatus(item) === status), qualityMessage(quality, guard, status) || '当前没有有效数据');
  } else if (input.loading) {
    status = RUNTIME_STATUS.LOADING;
    message = '正在刷新';
  }

  const dataDate = normalizeDate(input.dataDate || dateOfQuality(quality));
  const sourceAsOf = quality?.sourceAsOf ?? input.sourceAsOf ?? null;
  const sourceIssue = issues.find(item => item?.code || item?.status) || null;
  return {
    slot: text(input.slot),
    status,
    state: status,
    statusLabel: statusText(status),
    message,
    rawStatus: text(sourceIssue?.status || sourceIssue?.code || quality?.status).toUpperCase(),
    value: input.value,
    dataDate,
    batchId: text(input.batchId || quality?.batchId),
    qualityStatus: text(quality?.status),
    sourceAsOf,
    configured,
    applicable
  };
}

function normalizeQuality(value) {
  if (!isRecord(value)) return null;
  const status = text(value.status).toUpperCase();
  return {
    ...value,
    status: QUALITY_STATUSES.has(status) ? status : status || null,
    batchId: text(value.batchId || value.batch_id),
    dataDate: dateOfQuality(value),
    sourceAsOf: value.sourceAsOf ?? value.source_as_of ?? null
  };
}

function inferBatchQuality(sourceQualities) {
  const values = Object.values(sourceQualities || {}).map(normalizeQuality).filter(Boolean);
  if (!values.length) return null;
  const first = values[0];
  const sameIdentity = values.every(item => item.batchId === first.batchId
    && item.dataDate === first.dataDate
    && item.version === first.version
    && item.dataClassification === first.dataClassification);
  return sameIdentity && first.batchId ? first : null;
}

function collectSourceDates(sourceQualities, sourceDates) {
  const result = {};
  if (isRecord(sourceDates)) {
    for (const [slot, value] of Object.entries(sourceDates)) if (normalizeDate(value)) result[slot] = normalizeDate(value);
  }
  if (isRecord(sourceQualities)) {
    for (const [slot, quality] of Object.entries(sourceQualities)) {
      const date = dateOfQuality(quality);
      if (date) result[slot] = date;
    }
  }
  return result;
}

/**
 * 构造展示协议使用的页面级运行时说明。
 * `enabled` 只由调用方显式决定；旧 presentation 可以完全不渲染此模型。
 */
export function buildRuntimePresentation(input = {}) {
  const enabled = input.enabled === true;
  const configured = configuredSet(input.configuredSlots);
  const staticAvailability = isRecord(input.staticAvailability) ? input.staticAvailability : {};
  const sourceQualities = isRecord(input.sourceQualities) ? input.sourceQualities : {};
  const sourceDates = collectSourceDates(sourceQualities, input.sourceDates);
  const sourceAsOf = {};
  const slotNames = new Set([
    ...configured,
    ...Object.keys(input.runtimeIssues || {}),
    ...Object.keys(sourceQualities),
    ...Object.keys(sourceDates),
    ...Object.keys(staticAvailability)
  ]);
  const slots = {};
  for (const slot of slotNames) {
    const quality = normalizeQuality(sourceQualities[slot]);
    const issues = issueList(input.runtimeIssues, slot);
    const valuePresence = isRecord(input.valuePresence) && Object.prototype.hasOwnProperty.call(input.valuePresence, slot)
      ? input.valuePresence[slot] : undefined;
    const state = buildRuntimeSlotState({
      slot,
      configured: configured.has(slot),
      applicable: input.applicableSlots ? configuredSet(input.applicableSlots).has(slot) : true,
      issues,
      quality,
      qualityGuard: input.qualityGuard,
      permissionStatus: input.permissionStatus,
      error: input.error,
      loading: input.loading,
      valuePresent: valuePresence,
      dataDate: sourceDates[slot],
      sourceAsOf: quality?.sourceAsOf,
      staticAvailability: staticEntry(staticAvailability, slot)
    });
    slots[slot] = state;
    if (quality?.sourceAsOf) sourceAsOf[slot] = quality.sourceAsOf;
  }

  const quality = normalizeQuality(input.quality) || inferBatchQuality(sourceQualities);
  const guard = normalizeQuality(input.qualityGuard) || (isRecord(input.qualityGuard) ? input.qualityGuard : null);
  const effectiveQuality = quality || null;
  let pageStatus = RUNTIME_STATUS.READY;
  if (input.loading) pageStatus = RUNTIME_STATUS.LOADING;
  const permission = positiveStatus(input.permissionStatus) || statusFromError(input.error);
  if (permission || Object.values(slots).some(item => item.status === RUNTIME_STATUS.PERMISSION_DENIED)) {
    pageStatus = RUNTIME_STATUS.PERMISSION_DENIED;
  } else if (Object.values(slots).some(item => item.status === RUNTIME_STATUS.ERROR)) {
    pageStatus = RUNTIME_STATUS.ERROR;
  } else if (guard?.status === 'STALE' || quality?.status === 'STALE'
    || Object.values(slots).some(item => item.status === RUNTIME_STATUS.STALE)) {
    pageStatus = RUNTIME_STATUS.STALE;
  } else if (text(input.error)) {
    pageStatus = RUNTIME_STATUS.ERROR;
  } else if (quality?.status === 'PARTIAL' || quality?.status === 'NO_COMPLETE_BATCH'
    || Object.values(slots).some(item => item.status === RUNTIME_STATUS.NO_DATA)) {
    pageStatus = RUNTIME_STATUS.NO_DATA;
  } else if (Object.values(slots).some(item => item.status === RUNTIME_STATUS.UNCONFIGURED)) {
    pageStatus = RUNTIME_STATUS.UNCONFIGURED;
  }

  const batch = effectiveQuality ? {
    ...effectiveQuality,
    status: guard?.status === 'STALE' ? 'STALE' : effectiveQuality.status,
    explanation: issueMessage(guard) || qualityMessage(effectiveQuality, null, pageStatus)
  } : guard ? {
    ...guard,
    status: text(guard.status || 'STALE').toUpperCase(),
    explanation: issueMessage(guard)
  } : null;
  const dates = Object.values(sourceDates);
  const distinctDates = [...new Set(dates.filter(Boolean))];
  const message = issueMessage(input.error)
    || issueMessage(input.qualityGuard)
    || (pageStatus === RUNTIME_STATUS.PERMISSION_DENIED ? '当前会话无权读取数据'
      : pageStatus === RUNTIME_STATUS.ERROR ? '部分数据请求失败'
        : pageStatus === RUNTIME_STATUS.STALE ? '当前展示保留上一完整批次，数据已过期或刷新未完成'
          : pageStatus === RUNTIME_STATUS.NO_DATA ? '当前没有可用数据'
            : quality?.mixedPeriod === true ? '本次批次存在混合统计期间，请核对数据日期' : '');

  return {
    enabled,
    status: pageStatus,
    state: pageStatus,
    statusLabel: statusText(pageStatus),
    message,
    queriedAt: text(input.queriedAt),
    dataDate: distinctDates.length === 1 ? distinctDates[0] : '',
    dataDates: distinctDates,
    sourceDates,
    sourceAsOf,
    batch,
    batchId: text(batch?.batchId),
    slots,
    hasMixedDates: distinctDates.length > 1,
    hasMixedPeriod: quality?.mixedPeriod === true,
    configuredSlots: [...configured]
  };
}

export { issueStatus, statusText };
