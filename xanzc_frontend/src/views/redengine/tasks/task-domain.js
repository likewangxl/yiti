/**
 * 红色引擎任务基础域模型。
 *
 * 这里仅保存页面需要的枚举、校验、周期窗口和响应归一化逻辑，不持有后端状态，
 * 也不在生产前端复制原型的 mock 数据。任务状态、字典值和支部数据最终以 API 为准。
 */

export const TASK_NATURES = Object.freeze({
  PERIODIC: 'PERIODIC',
  // 兼容旧原型值；新请求一律归一化为后端的 PERIODIC。
  SCHEDULED: 'SCHEDULED',
  TEMPORARY: 'TEMPORARY'
});

export const BUSINESS_TYPES = Object.freeze([
  { value: 'FOUR_DIMENSION', label: '四大维度材料上报' },
  { value: 'GENERAL', label: '普通任务' }
]);

export const AUDIENCE_TYPES = Object.freeze([
  { value: 'ALL_BRANCH', label: '全部党支部' },
  { value: 'SPECIFIED_BRANCH', label: '指定党支部' },
  { value: 'SPECIFIED_EMPLOYEE', label: '指定员工' }
]);

export const CYCLE_OPTIONS = Object.freeze([
  { value: 'WEEK_START', label: '每周初', unit: 'week', anchor: 'start' },
  { value: 'WEEK_END', label: '每周末', unit: 'week', anchor: 'end' },
  { value: 'MONTH_START', label: '每月初', unit: 'month', anchor: 'start' },
  { value: 'MONTH_END', label: '每月末', unit: 'month', anchor: 'end' },
  { value: 'QUARTER_START', label: '每季度初', unit: 'quarter', anchor: 'start' },
  { value: 'QUARTER_END', label: '每季度末', unit: 'quarter', anchor: 'end' }
]);

export const TASK_STATUS_OPTIONS = Object.freeze([
  { value: 'DRAFT', label: '草稿' },
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'CANCELLED', label: '已取消' }
]);

export const EXPORT_MAX_ROWS_PER_SHEET = 5000;

const CYCLE_MAP = Object.freeze(
  CYCLE_OPTIONS.reduce((map, item) => {
    map[item.value] = item;
    return map;
  }, {})
);

const NATURE_ALIASES = Object.freeze({
  SCHEDULED: TASK_NATURES.PERIODIC,
  PERIODIC: TASK_NATURES.PERIODIC,
  TEMPORARY: TASK_NATURES.TEMPORARY
});

const AUDIENCE_ALIASES = Object.freeze({
  ALL_BRANCH: 'ALL_BRANCH',
  ALL_BRANCHES: 'ALL_BRANCH',
  SPECIFIED_BRANCH: 'SPECIFIED_BRANCH',
  SPECIFIED_BRANCHES: 'SPECIFIED_BRANCH',
  SPECIFIED_EMPLOYEE: 'SPECIFIED_EMPLOYEE',
  SPECIFIED_EMPLOYEES: 'SPECIFIED_EMPLOYEE'
});

const DAY_MS = 24 * 60 * 60 * 1000;

function asLocalDate(value) {
  if (value instanceof Date) {
    return new Date(value.getFullYear(), value.getMonth(), value.getDate());
  }
  if (typeof value === 'string') {
    const datePart = value.match(/^(\d{4})-(\d{2})-(\d{2})/);
    if (datePart) {
      return new Date(Number(datePart[1]), Number(datePart[2]) - 1, Number(datePart[3]));
    }
  }
  const parsed = new Date(value || Date.now());
  if (Number.isNaN(parsed.getTime())) throw new Error('日期格式不正确');
  return new Date(parsed.getFullYear(), parsed.getMonth(), parsed.getDate());
}

function addDays(date, days) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
}

function formatDate(date) {
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0')
  ].join('-');
}

function quarterStartMonth(monthIndex) {
  return Math.floor(monthIndex / 3) * 3;
}

function getPeriodBounds(unit, referenceDate) {
  const date = asLocalDate(referenceDate);
  if (unit === 'week') {
    const daysFromMonday = (date.getDay() + 6) % 7;
    const start = addDays(date, -daysFromMonday);
    return { start, end: addDays(start, 6) };
  }
  if (unit === 'month') {
    const start = new Date(date.getFullYear(), date.getMonth(), 1);
    return { start, end: new Date(date.getFullYear(), date.getMonth() + 1, 0) };
  }
  const month = quarterStartMonth(date.getMonth());
  const start = new Date(date.getFullYear(), month, 1);
  return { start, end: new Date(date.getFullYear(), month + 3, 0) };
}

function shiftPeriod(bounds, unit, amount) {
  if (unit === 'week') {
    return { start: addDays(bounds.start, amount * 7), end: addDays(bounds.end, amount * 7) };
  }
  if (unit === 'month') {
    const start = new Date(bounds.start.getFullYear(), bounds.start.getMonth() + amount, 1);
    return { start, end: new Date(start.getFullYear(), start.getMonth() + 1, 0) };
  }
  const start = new Date(bounds.start.getFullYear(), bounds.start.getMonth() + amount * 3, 1);
  return { start, end: new Date(start.getFullYear(), start.getMonth() + 3, 0) };
}

function periodDays(bounds) {
  return Math.round((bounds.end.getTime() - bounds.start.getTime()) / DAY_MS) + 1;
}

function windowForAnchor(meta, bounds, duration) {
  if (meta.anchor === 'start') {
    return { start: bounds.start, end: addDays(bounds.start, duration - 1) };
  }
  return { start: addDays(bounds.end, -(duration - 1)), end: bounds.end };
}

function canonicalNature(value) {
  return NATURE_ALIASES[value] || value || '';
}

function canonicalAudience(value) {
  return AUDIENCE_ALIASES[value] || value || '';
}

export function isPeriodicNature(value) {
  return canonicalNature(value) === TASK_NATURES.PERIODIC;
}

export function isFourDimensionTask(task = {}) {
  return task.isFourDimension === true
    || task.businessType === 'FOUR_DIMENSION'
    || task.typeCode === 'FOUR_DIMENSION'
    || task.type === 'FOUR_DIMENSION'
    || task.typeName === '四大维度材料上报';
}

/**
 * 计算下一次自然周期窗口。开始锚点从周期初向后顺延，结束锚点从周期末向前倒推，
 * 持续天数包含首尾且不能超过周、月或季度的自然天数。
 */
export function calculateTaskWindow(cycle, duration, referenceDate = new Date()) {
  const meta = CYCLE_MAP[cycle];
  const numberOfDays = Number(duration);
  if (!meta) throw new Error('不支持的周期');
  if (!Number.isInteger(numberOfDays) || numberOfDays < 1) {
    throw new Error('持续天数必须为正整数');
  }

  const reference = asLocalDate(referenceDate);
  let bounds = getPeriodBounds(meta.unit, reference);
  if (meta.anchor === 'start') {
    // 配置任务从当前周期之后的周期初开始，避免发布后覆盖已经开始的窗口。
    bounds = shiftPeriod(bounds, meta.unit, 1);
  } else {
    // 结束锚点默认使用当前周期末；如果当前日期已进入倒推窗口，则取下一周期末。
    let candidate = windowForAnchor(meta, bounds, numberOfDays);
    if (reference.getTime() >= candidate.start.getTime()) {
      bounds = shiftPeriod(bounds, meta.unit, 1);
    }
  }

  // 先确定发布后实际采用的完整周期，再校验窗口长度。跨月/跨季度时，
  // 目标周期的自然天数可能比当前参考周期更短，不能只校验参考日所在周期。
  if (numberOfDays > periodDays(bounds)) {
    throw new Error(meta.unit === 'week'
      ? '持续天数不能超过周周期'
      : meta.unit === 'month'
        ? '持续天数不能超过当月自然天数'
        : '持续天数不能超过季度自然天数');
  }

  const result = windowForAnchor(meta, bounds, numberOfDays);
  return { start: formatDate(result.start), end: formatDate(result.end) };
}

export function cycleLabel(cycle) {
  return CYCLE_MAP[cycle]?.label || cycle || '—';
}

export function natureLabel(nature) {
  const canonical = canonicalNature(nature);
  return canonical === TASK_NATURES.PERIODIC ? '定时任务' : canonical === TASK_NATURES.TEMPORARY ? '临时任务' : nature || '—';
}

export function audienceLabel(audienceType) {
  const canonical = canonicalAudience(audienceType);
  return AUDIENCE_TYPES.find((item) => item.value === canonical)?.label || audienceType || '—';
}

export function businessTypeLabel(businessType) {
  return BUSINESS_TYPES.find((item) => item.value === businessType)?.label || businessType || '—';
}

export function formatTaskWindow(task = {}) {
  if (isPeriodicNature(task.nature ?? task.taskNature)) {
    const start = task.windowStart || task.startAt || task.window?.start || task.window?.startDate
      || task.windowStartDate || task.scheduleWindow?.startDate || '—';
    const end = task.windowEnd || task.endAt || task.window?.end || task.window?.endDate
      || task.windowEndDate || task.scheduleWindow?.endDate || '—';
    return `${cycleLabel(task.cycle ?? task.cycleType)} · ${start} 至 ${end}`;
  }
  return `${task.startAt || task.temporaryStartTime || task.windowStart || '—'} 至 ${task.endAt || task.temporaryEndTime || task.windowEnd || '—'}`;
}

export function validateTaskDraft(draft = {}) {
  const errors = {};
  const title = String(draft.title || '').trim();
  const description = String(draft.description || '').trim();

  if (!title) errors.title = '请输入任务标题';
  if (!description) errors.description = '请输入任务说明';
  const nature = canonicalNature(draft.nature ?? draft.taskNature);
  const businessType = String(draft.businessType || draft.typeCode || draft.type || '').trim();
  const audienceType = canonicalAudience(draft.audienceType ?? draft.targetType);
  const fileTypes = draft.allowedFileTypes ?? draft.fileTypeCodes;
  const startAt = draft.startAt ?? draft.temporaryStartTime;
  const endAt = draft.endAt ?? draft.temporaryEndTime;

  if (!businessType) errors.businessType = '请选择任务类型';
  if (![TASK_NATURES.PERIODIC, TASK_NATURES.TEMPORARY].includes(nature)) {
    errors.nature = '请选择任务性质';
  }

  if (!AUDIENCE_TYPES.some((item) => item.value === audienceType)) {
    errors.audienceType = '请选择任务对象';
  } else if (audienceType === 'SPECIFIED_BRANCH'
    && (!Array.isArray(draft.targetBranchIds) || draft.targetBranchIds.length === 0)) {
    errors.targetBranchIds = '请至少选择一个党支部';
  } else if (audienceType === 'SPECIFIED_EMPLOYEE'
    && (!Array.isArray(draft.targetEmployeeIds) || draft.targetEmployeeIds.length === 0)) {
    errors.targetEmployeeIds = '请至少选择一名员工';
  }

  if (nature === TASK_NATURES.PERIODIC) {
    if (!CYCLE_MAP[draft.cycle]) errors.cycle = '请选择周期';
    if (!Number.isInteger(Number(draft.durationDays)) || Number(draft.durationDays) < 1) {
      errors.durationDays = '请输入有效的持续天数';
    } else if (!errors.cycle) {
      try {
        calculateTaskWindow(draft.cycle, Number(draft.durationDays), draft.referenceDate || new Date());
      } catch (error) {
        errors.durationDays = error.message;
      }
    }
  }

  if (nature === TASK_NATURES.TEMPORARY) {
    if (!startAt) errors.startAt = '请选择开始时间';
    if (!endAt) errors.endAt = '请选择截止时间';
    if (startAt && endAt && new Date(endAt).getTime() < new Date(startAt).getTime()) {
      errors.endAt = '截止时间不能早于开始时间';
    }
  }

  if (draft.requiresFile && (!Array.isArray(fileTypes) || fileTypes.length === 0)) {
    errors.allowedFileTypes = '至少选择一种允许文件类型';
  }

  return { valid: Object.keys(errors).length === 0, errors };
}

export function buildTaskQuery(query = {}, pageNo = 1, pageSize = 10) {
  const params = { pageNo, pageSize };
  const aliases = { nature: 'taskNature', typeCode: 'businessType', cycle: 'cycleType' };
  Object.entries(query).forEach(([key, value]) => {
    if (value === undefined || value === null) return;
    if (typeof value === 'string' && value.trim() === '') return;
    if (Array.isArray(value) && value.length === 0) return;
    const targetKey = aliases[key] || key;
    let normalized = typeof value === 'string' ? value.trim() : value;
    if (targetKey === 'taskNature') normalized = canonicalNature(normalized);
    if (targetKey === 'businessType' && normalized === 'NOTICE') normalized = 'GENERAL';
    params[targetKey] = normalized;
  });
  return params;
}

function buildTaskTargets(draft = {}) {
  const audienceType = canonicalAudience(draft.audienceType ?? draft.targetType);
  if (audienceType === 'ALL_BRANCH') return [{ targetType: 'ALL_BRANCH' }];
  if (audienceType === 'SPECIFIED_BRANCH') {
    return (draft.targetBranchIds || []).filter((id) => id !== undefined && id !== null && id !== '')
      .map((partyOrgId) => ({ targetType: 'SPECIFIED_BRANCH', partyOrgId }));
  }
  if (audienceType === 'SPECIFIED_EMPLOYEE') {
    return (draft.targetEmployeeIds || []).filter(Boolean)
      .map((employeeId) => ({ targetType: 'SPECIFIED_EMPLOYEE', employeeId }));
  }
  return [];
}

/**
 * 将表单模型转换为 red-engine-center 的 ReTaskCreateReqDTO，剔除仅用于展示的字段。
 * 任务对象按后端的三种 targetType 展开；员工对象由服务端按所属党支部去重生成一份 assignment。
 */
export function buildTaskCreatePayload(draft = {}) {
  const nature = canonicalNature(draft.nature ?? draft.taskNature);
  const businessType = draft.businessType || draft.typeCode || draft.type || '';
  const periodic = nature === TASK_NATURES.PERIODIC;
  const fileTypes = draft.allowedFileTypes ?? draft.fileTypeCodes ?? [];
  const itemCodes = draft.itemCodes ?? draft.detailItemCodes ?? [];
  return {
    title: String(draft.title || '').trim(),
    description: String(draft.description || '').trim(),
    taskNature: nature,
    businessType: businessType === 'NOTICE' ? 'GENERAL' : businessType,
    cycleType: periodic ? (draft.cycle || null) : null,
    durationDays: periodic && draft.durationDays !== undefined && draft.durationDays !== null
      ? Number(draft.durationDays)
      : null,
    temporaryStartTime: periodic ? null : (draft.startAt ?? draft.temporaryStartTime ?? null),
    temporaryEndTime: periodic ? null : (draft.endAt ?? draft.temporaryEndTime ?? null),
    requiresFile: Boolean(draft.requiresFile),
    fileTypeCodes: draft.requiresFile ? [...fileTypes] : [],
    targets: buildTaskTargets(draft),
    itemCodes: businessType === 'FOUR_DIMENSION' ? [...itemCodes] : []
  };
}

export function normalizePageResult(result) {
  if (Array.isArray(result)) return { records: result, total: result.length };
  const records = result?.records || result?.list || result?.content || result?.rows || result?.data;
  const normalizedRecords = Array.isArray(records) ? records : [];
  return {
    records: normalizedRecords,
    total: Number(result?.total ?? result?.totalCount ?? normalizedRecords.length)
  };
}

export function normalizeTask(task = {}) {
  const taskNature = canonicalNature(task.taskNature ?? task.nature);
  const businessType = task.businessType ?? task.typeCode ?? task.type;
  const cycleType = task.cycleType ?? task.cycle;
  const targets = Array.isArray(task.targets) ? task.targets : [];
  const audienceType = canonicalAudience(task.audienceType
    ?? (targets.length === 1 ? targets[0]?.targetType : targets.length ? targets[0]?.targetType : ''));
  const resolvedTypeName = task.typeName ?? task.businessTypeName ?? task.typeLabel
    ?? (BUSINESS_TYPES.find((item) => item.value === businessType)?.label)
    ?? task.type ?? businessType ?? '—';
  return {
    ...task,
    taskId: task.taskId ?? task.id,
    taskNature,
    nature: taskNature,
    businessType,
    typeCode: task.typeCode ?? businessType,
    typeName: resolvedTypeName,
    cycleType,
    cycle: cycleType,
    audienceType,
    publishedAt: task.publishedAt ?? task.publishTime,
    startAt: task.startAt ?? task.temporaryStartTime ?? task.windowStart,
    endAt: task.endAt ?? task.temporaryEndTime ?? task.windowEnd,
    fileTypeCodes: Array.isArray(task.fileTypeCodes)
      ? task.fileTypeCodes
      : (Array.isArray(task.allowedFileTypes) ? task.allowedFileTypes : []),
    allowedFileTypes: Array.isArray(task.allowedFileTypes)
      ? task.allowedFileTypes
      : (Array.isArray(task.fileTypeCodes) ? task.fileTypeCodes : []),
    itemCodes: Array.isArray(task.itemCodes) ? task.itemCodes : [],
    targetCount: Number(task.targetCount ?? 0),
    submittedCount: Number(task.submittedCount ?? 0),
    approvedCount: Number(task.approvedCount ?? 0),
    rejectedCount: Number(task.rejectedCount ?? 0),
    unreportedCount: Number(task.unreportedCount ?? 0)
  };
}

export function normalizeTaskList(result) {
  const page = normalizePageResult(result);
  return { ...page, records: page.records.map(normalizeTask) };
}

function normalizeDisplayValue(value) {
  return value === undefined || value === null || String(value).trim() === '' ? '--' : value;
}

export function normalizeAssignment(assignment = {}) {
  const submission = assignment.submission || assignment.currentSubmission || {};
  const status = submission.status ?? assignment.submissionStatus ?? assignment.status
    ?? assignment.stage ?? assignment.assignmentStatus ?? '';
  const unreported = assignment.isUnreported === true
    || ['UNREPORTED', 'TODO', 'OVERDUE_UNREPORTED'].includes(status);
  const rawFiles = assignment.files ?? submission.files;
  const files = Array.isArray(rawFiles)
    ? rawFiles.map((file) => ({
      ...file,
      fileId: file.fileId ?? file.id ?? file.fileObjectId,
      fileName: file.fileName ?? file.name ?? '未命名附件'
    }))
    : [];

  return {
    ...assignment,
    assignmentId: assignment.assignmentId ?? assignment.id,
    branchId: assignment.branchId ?? assignment.partyOrgId ?? assignment.orgId,
    branchName: assignment.branchName ?? assignment.partyOrgName ?? assignment.orgName ?? '—',
    status,
    assignmentStatus: assignment.assignmentStatus ?? assignment.status,
    isUnreported: unreported,
    submitterName: unreported ? '--' : normalizeDisplayValue(
      assignment.submitterName ?? assignment.submitter ?? submission.submitterName ?? submission.submitter
    ),
    submittedAt: unreported ? '--' : normalizeDisplayValue(
      assignment.submittedAt ?? assignment.submitTime ?? submission.submittedAt ?? submission.submittedTime
    ),
    content: normalizeDisplayValue(assignment.content ?? assignment.formData ?? submission.content),
    files
  };
}

export function normalizeAssignmentPage(result) {
  const page = normalizePageResult(result);
  return { ...page, records: page.records.map(normalizeAssignment) };
}

export function getTaskRoute(task = {}) {
  if (isFourDimensionTask(task)) return 'materials';
  return 'task-entry';
}

export function buildExportRequest(task = {}, detailItemCodes = []) {
  const codes = Array.isArray(detailItemCodes) ? detailItemCodes.filter(Boolean) : [];
  const isFourDimension = isFourDimensionTask(task);
  if (isFourDimension && codes.length === 0) throw new Error('四大维度任务导出前请选择明细项');
  return { itemCodes: codes };
}

export function getExportSheetCount(totalRows, maxRowsPerSheet = EXPORT_MAX_ROWS_PER_SHEET) {
  const total = Math.max(0, Number(totalRows) || 0);
  const maxRows = Number(maxRowsPerSheet);
  if (!Number.isInteger(maxRows) || maxRows < 1) throw new Error('Sheet 行数上限必须为正整数');
  return total === 0 ? 0 : Math.ceil(total / maxRows);
}

export function linkifyDescription(description) {
  const source = String(description || '');
  const parts = [];
  // 中文标点在说明文本中通常是链接和下一句的分隔符，不能被 URL 一并吞掉。
  const pattern = /https?:\/\/[^\s\u3000<>，。；！？、)）】】]+/gi;
  let cursor = 0;
  let match;
  while ((match = pattern.exec(source))) {
    if (match.index > cursor) parts.push({ type: 'text', value: source.slice(cursor, match.index) });
    const raw = match[0];
    const trailing = raw.match(/[，。；！？、)）】】]+$/)?.[0] || '';
    const value = trailing ? raw.slice(0, -trailing.length) : raw;
    parts.push({ type: 'link', value, href: value, target: '_blank', rel: 'noreferrer noopener' });
    if (trailing) parts.push({ type: 'text', value: trailing });
    cursor = match.index + raw.length;
  }
  if (cursor < source.length) parts.push({ type: 'text', value: source.slice(cursor) });
  return parts.length ? parts : [{ type: 'text', value: source }];
}
