/**
 * 将授权机构目录和已有排名返回整理为完整榜单模型。
 *
 * 这个模型不负责取数、不猜测指标，也不做 TOP10/分页裁剪；机构是否授权
 * 由输入目录决定，指标字段和排序方向由配置明确决定。缺失值只进入未参与区，
 * 合法的 0 和负数都可以参与排名。
 */

export const DEFAULT_RANKING_INTERVAL_MS = 10000;

const DEFAULT_DIRECTION = 'DESC';

const TRUE_STATUS_VALUES = new Set([
  '1', 'TRUE', 'YES', 'Y', 'ACTIVE', 'ENABLED', 'AUTHORIZED', 'ALLOW', 'ALLOWED', 'GRANTED'
]);
const FALSE_STATUS_VALUES = new Set([
  '0', 'FALSE', 'NO', 'N', 'INACTIVE', 'DISABLED', 'STOPPED', 'OFF',
  'UNAUTHORIZED', 'NOT_AUTHORIZED', 'DENIED', 'FORBIDDEN', 'REVOKED', 'REJECTED',
  'NO_ACCESS', 'EXPIRED'
]);

function isRecord(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function text(value) {
  return value === null || value === undefined ? '' : String(value).trim();
}

export function finiteRankingValue(value) {
  if (value === null || value === undefined || value === '' || typeof value === 'boolean') return null;
  if (typeof value === 'string' && value.trim() === '') return null;
  const number = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(number) ? number : null;
}

function orgCodeOf(value) {
  if (!isRecord(value)) return '';
  // `id`, `code` and the array index are not authorization identities.  A
  // ranking row may only join the server directory through its declared
  // organization code.
  return text(value.orgCode ?? value.org_code);
}

function orgNameOf(value, fallback = '') {
  if (!isRecord(value)) return text(fallback) || '未命名机构';
  return text(value.orgName ?? value.org_name ?? value.name ?? fallback) || '未命名机构';
}

function parseStatusFlag(value) {
  if (typeof value === 'boolean') return { known: true, value };
  if (typeof value === 'number') return Number.isFinite(value) && (value === 0 || value === 1)
    ? { known: true, value: value === 1 }
    : { known: false, value: false };
  const normalized = text(value).toUpperCase();
  if (TRUE_STATUS_VALUES.has(normalized)) return { known: true, value: true };
  if (FALSE_STATUS_VALUES.has(normalized)) return { known: true, value: false };
  return { known: false, value: false };
}

function explicitStatus(value, keys) {
  if (!isRecord(value)) return { known: false, value: false };
  for (const key of keys) {
    if (Object.prototype.hasOwnProperty.call(value, key)) return parseStatusFlag(value[key]);
  }
  return { known: false, value: false };
}

function normalizeInstitution(value, index = 0, sourceAuthorized = false) {
  if (!isRecord(value)) {
    return { valid: false, issue: issue('ORG_CODE_MISSING', '授权目录记录缺少机构编码') };
  }
  const orgCode = orgCodeOf(value);
  if (!orgCode) {
    return { valid: false, issue: issue('ORG_CODE_MISSING', '授权目录记录缺少机构编码') };
  }
  const active = explicitStatus(value, ['active', 'isActive', 'enabled', 'is_enabled', 'status']);
  const authorized = explicitStatus(value, [
    'authorized', 'isAuthorized', 'hasAccess', 'accessGranted', 'permissionGranted',
    'hasPermission', 'isPermitted', 'permission', 'access', 'permissionStatus',
    'authorizationStatus', 'authStatus'
  ]);
  if (active.known && !active.value) {
    return { valid: false, issue: issue('INACTIVE', '授权目录机构已停用', orgCode) };
  }
  if (authorized.known && !authorized.value) {
    return { valid: false, issue: issue('UNAUTHORIZED', '授权目录机构未获当前屏授权', orgCode) };
  }
  if (!sourceAuthorized && (!active.known || !authorized.known)) {
    return {
      valid: false,
      issue: issue('DIRECTORY_STATUS_UNCONFIRMED', '授权目录未明确确认机构 active/authorized 状态', orgCode)
    };
  }
  return {
    valid: true,
    value: {
      ...value,
      orgCode,
      name: orgNameOf(value, orgCode),
      active: true,
      authorized: true,
      index
    }
  };
}

function normalizeMetric(value, index = 0) {
  const source = isRecord(value) ? value : {};
  const metricKey = text(source.metricKey ?? source.key ?? source.field ?? `metric-${index}`);
  const field = text(source.field ?? source.metricField ?? metricKey);
  const rawDirection = text(source.direction || DEFAULT_DIRECTION).toUpperCase();
  const direction = rawDirection === 'ASC' || rawDirection === 'DESC' ? rawDirection : DEFAULT_DIRECTION;
  return {
    ...source,
    metricKey,
    field,
    label: text(source.label ?? source.name ?? metricKey) || metricKey,
    unit: text(source.unit),
    direction,
    configValid: Boolean(metricKey && field && (rawDirection === 'ASC' || rawDirection === 'DESC' || !source.direction)),
    index
  };
}

function normalizeInput(input, rows, rankingMetrics) {
  if (Array.isArray(input)) {
    return {
      institutions: input,
      rows: Array.isArray(rows) ? rows : [],
      rankingMetrics: Array.isArray(rankingMetrics) ? rankingMetrics : [],
      responseMeta: {}
    };
  }
  const source = isRecord(input) ? input : {};
  const rowsSource = source.rows ?? source.rankingRows ?? source.rankings ?? [];
  const authorizedDirectory = Array.isArray(source.authorizedDirectory) ? source.authorizedDirectory : null;
  const nestedRows = isRecord(rowsSource) ? rowsSource.rows ?? rowsSource.data ?? [] : [];
  const nestedMeta = isRecord(rowsSource)
    ? (rowsSource.responseMeta ?? rowsSource.meta ?? rowsSource.coverage ?? rowsSource)
    : null;
  const responseMeta = {
    ...(isRecord(nestedMeta) ? nestedMeta : {}),
    ...(isRecord(source.pagination) ? source.pagination : {}),
    ...(isRecord(source.coverage) ? source.coverage : {}),
    ...(isRecord(source.meta) ? source.meta : {}),
    ...(isRecord(source.rankingMeta) ? source.rankingMeta : {}),
    ...(isRecord(source.responseMeta) ? source.responseMeta : {})
  };
  if (responseMeta.limit === undefined && source.limit !== undefined) responseMeta.limit = source.limit;
  if (responseMeta.hasMore === undefined && responseMeta.has_more === undefined) {
    if (source.hasMore !== undefined) responseMeta.hasMore = source.hasMore;
    else if (source.has_more !== undefined) responseMeta.has_more = source.has_more;
  }
  if (responseMeta.truncated === undefined && responseMeta.isTruncated === undefined) {
    if (source.truncated !== undefined) responseMeta.truncated = source.truncated;
    else if (source.isTruncated !== undefined) responseMeta.isTruncated = source.isTruncated;
  }
  return {
    ...source,
    institutions: authorizedDirectory ?? source.institutions ?? source.authorizedInstitutions ?? source.directory ?? [],
    sourceAuthorized: source.sourceAuthorized === true || Boolean(authorizedDirectory),
    rows: Array.isArray(rowsSource) ? rowsSource : nestedRows,
    rankingMetrics: source.rankingMetrics ?? source.metricOptions ?? source.component?.content?.rankingMetrics ?? [],
    responseMeta
  };
}

function valueFromRow(row, field) {
  if (!isRecord(row)) return null;
  const sources = [row, row.metrics, row.values, row.metricValues];
  for (const source of sources) {
    if (!isRecord(source) || !Object.prototype.hasOwnProperty.call(source, field)) continue;
    return finiteRankingValue(source[field]);
  }
  return null;
}

function compareCodes(left, right) {
  const a = text(left?.orgCode);
  const b = text(right?.orgCode);
  if (a === b) return (left?.sourceIndex ?? 0) - (right?.sourceIndex ?? 0);
  return a < b ? -1 : 1;
}

function uniqueIssues(issues) {
  const seen = new Set();
  return issues.filter(issue => {
    const key = `${issue?.code || ''}|${issue?.orgCode || ''}|${issue?.message || ''}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function issue(code, message, orgCode = '') {
  return { code, orgCode: text(orgCode), message };
}

function metadataValue(meta, keys) {
  const candidates = [meta, meta?.coverage, meta?.pagination, meta?.page];
  for (const candidate of candidates) {
    if (!isRecord(candidate)) continue;
    for (const key of keys) {
      if (candidate[key] !== undefined && candidate[key] !== null) return candidate[key];
    }
  }
  return undefined;
}

function explicitTrue(value) {
  if (value === true) return true;
  if (typeof value === 'string') return value.trim().toLowerCase() === 'true' || value.trim() === '1';
  return value === 1;
}

function responseCoverage(responseMeta, rowCount) {
  const meta = isRecord(responseMeta) ? responseMeta : {};
  const rawLimit = metadataValue(meta, ['limit', 'maxRows', 'max_rows', 'pageSize', 'page_size']);
  const limit = Number(rawLimit);
  const validLimit = Number.isSafeInteger(limit) && limit > 0 ? limit : null;
  const limitReached = validLimit !== null && rowCount >= validLimit;
  const hasMore = explicitTrue(metadataValue(meta, ['hasMore', 'has_more']));
  const truncated = explicitTrue(metadataValue(meta, ['truncated', 'isTruncated', 'truncatedPossible', 'truncated_possible']));
  return {
    limit: validLimit,
    limitReached,
    hasMore,
    truncated,
    incomplete: limitReached || hasMore || truncated
  };
}

function buildMetricResult(institutions, rankingRows, metric, baseIssues = [], responseMeta = {}) {
  const expected = institutions.map(item => ({
    orgCode: item.orgCode,
    name: item.name,
    source: item
  }));
  const directoryByCode = new Map(expected.map(item => [item.orgCode, item]));
  const groupedRows = new Map();
  const unauthorizedCodes = new Set();
  let missingOrgCodeCount = 0;
  const inputRows = Array.isArray(rankingRows) ? rankingRows : [];

  inputRows.forEach((raw, sourceIndex) => {
    const orgCode = orgCodeOf(raw);
    if (!orgCode || !directoryByCode.has(orgCode)) {
      if (orgCode) unauthorizedCodes.add(orgCode);
      else missingOrgCodeCount += 1;
      return;
    }
    const row = { ...(isRecord(raw) ? raw : {}), orgCode, sourceIndex };
    const list = groupedRows.get(orgCode) || [];
    list.push(row);
    groupedRows.set(orgCode, list);
  });

  const issues = [...baseIssues];
  if (missingOrgCodeCount > 0) {
    issues.push(issue('MISSING_ORG_CODE', `排名返回 ${missingOrgCodeCount} 行缺少机构编码`));
  }
  unauthorizedCodes.forEach(orgCode => issues.push(issue('UNAUTHORIZED_ORG', '返回了不在授权目录中的机构数据', orgCode)));
  const duplicateCodes = new Set([...groupedRows.entries()].filter(([, rows]) => rows.length > 1).map(([orgCode]) => orgCode));
  duplicateCodes.forEach(orgCode => issues.push(issue('DUPLICATE_ORG', '同一机构返回多条排名记录，已拒绝参与排名', orgCode)));

  const received = [];
  const missing = [];
  const rankable = [];
  expected.forEach(item => {
    const rows = groupedRows.get(item.orgCode) || [];
    if (duplicateCodes.has(item.orgCode)) {
      missing.push({ orgCode: item.orgCode, name: item.name, value: null, state: 'MISSING', reason: 'DUPLICATE_ORG' });
      return;
    }
    if (!rows.length) {
      missing.push({ orgCode: item.orgCode, name: item.name, value: null, state: 'MISSING', reason: 'NO_ROW' });
      return;
    }
    const row = rows[0];
    const value = valueFromRow(row, metric.field);
    const receivedRow = {
      ...row,
      orgCode: item.orgCode,
      name: orgNameOf(row, item.name),
      value,
      unit: text(row.unit ?? metric.unit),
      state: value === null ? 'MISSING' : 'RECEIVED'
    };
    received.push(receivedRow);
    if (value === null) {
      missing.push({ ...receivedRow, reason: 'NO_VALUE' });
      return;
    }
    rankable.push({ ...receivedRow, state: 'RANKABLE' });
  });

  rankable.sort((left, right) => {
    const valueOrder = metric.direction === 'ASC' ? left.value - right.value : right.value - left.value;
    return valueOrder || compareCodes(left, right);
  });
  rankable.forEach((row, index) => {
    const previous = rankable[index - 1];
    row.rank = !previous || previous.value !== row.value ? index + 1 : previous.rank;
  });
  missing.sort(compareCodes);

  if (!metric.configValid) issues.push(issue('INVALID_METRIC_CONFIG', '指标配置缺少有效字段或排序方向'));
  if (!rankable.length && expected.length) issues.push(issue('NO_METRIC_VALUES', `指标“${metric.label}”没有可参与排名的有限数值`));

  const coverage = responseCoverage(responseMeta, inputRows.length);
  if (coverage.limitReached) {
    issues.push(issue('RESULT_LIMIT_REACHED', `排名返回行数达到声明上限 ${coverage.limit}`));
  }
  if (coverage.hasMore) issues.push(issue('RESULT_HAS_MORE', '排名返回声明仍有更多数据未返回'));
  if (coverage.truncated) issues.push(issue('RESULT_TRUNCATED', '排名返回声明已被截断'));

  const expectedCount = expected.length;
  const receivedCount = received.length;
  const rankableCount = rankable.length;
  const missingCount = missing.length;
  const incomplete = coverage.incomplete || baseIssues.length > 0 || missingOrgCodeCount > 0 || unauthorizedCodes.size > 0 || (
    expectedCount > 0 && (receivedCount < expectedCount || missingCount > 0 || duplicateCodes.size > 0)
  );
  const complete = !incomplete;
  const summary = `已获得 ${receivedCount}/${expectedCount} 家授权机构 · 可排名 ${rankableCount} 家 · 未参与 ${missingCount} 家${incomplete ? ' · 数据不完整' : ''}`;
  return {
    ...metric,
    available: rankableCount > 0,
    expected,
    received,
    rankable,
    missing,
    rows: [...rankable, ...missing],
    expectedCount,
    receivedCount,
    rankableCount,
    missingCount,
    incomplete,
    complete,
    issues: uniqueIssues(issues),
    summary,
    coverage,
    unauthorizedCount: unauthorizedCodes.size,
    missingOrgCodeCount,
    duplicateCount: duplicateCodes.size
  };
}

/**
 * 构建一个或多个机构排名指标。
 * 支持 `buildInstitutionRankingModel({ ... })`，也兼容三个数组参数调用。
 */
export function buildInstitutionRankingModel(input = {}, rows, rankingMetrics) {
  const source = normalizeInput(input, rows, rankingMetrics);
  const directoryInput = Array.isArray(source.institutions) ? source.institutions : [];
  const sourceAuthorized = source.sourceAuthorized === true;
  const directory = [];
  const directoryCodes = new Set();
  const baseIssues = [];
  directoryInput.forEach((raw, index) => {
    const normalized = normalizeInstitution(raw, index, sourceAuthorized);
    if (!normalized.valid) {
      baseIssues.push(normalized.issue);
      return;
    }
    const institution = normalized.value;
    if (directoryCodes.has(institution.orgCode)) {
      baseIssues.push(issue('DUPLICATE_DIRECTORY_ORG', '授权目录包含重复机构编码，已保留首条', institution.orgCode));
      return;
    }
    directoryCodes.add(institution.orgCode);
    directory.push(institution);
  });

  const metrics = (Array.isArray(source.rankingMetrics) ? source.rankingMetrics : []).map(normalizeMetric);
  const metricResults = metrics.map(metric => buildMetricResult(directory, source.rows, metric, baseIssues, source.responseMeta));
  const requestedMetricKey = text(source.activeMetricKey || source.metricKey);
  const activeMetric = metricResults.find(item => item.metricKey === requestedMetricKey) || metricResults[0] || null;
  return {
    enabled: Boolean(metricResults.length),
    expected: activeMetric?.expected || directory.map(item => ({ orgCode: item.orgCode, name: item.name, source: item })),
    received: activeMetric?.received || [],
    rankable: activeMetric?.rankable || [],
    missing: activeMetric?.missing || [],
    rows: activeMetric?.rows || [],
    expectedCount: activeMetric?.expectedCount ?? directory.length,
    receivedCount: activeMetric?.receivedCount ?? 0,
    rankableCount: activeMetric?.rankableCount ?? 0,
    missingCount: activeMetric?.missingCount ?? directory.length,
    incomplete: activeMetric?.incomplete ?? (baseIssues.length > 0 || directory.length > 0),
    complete: activeMetric?.complete ?? (baseIssues.length === 0 && directory.length === 0),
    issues: uniqueIssues(activeMetric?.issues || baseIssues),
    summary: activeMetric?.summary
      || `已获得 0/${directory.length} 家授权机构 · 可排名 0 家 · 未参与 ${directory.length} 家${baseIssues.length ? ' · 数据不完整' : ''}`,
    metrics: metricResults,
    metric: activeMetric,
    activeMetricKey: activeMetric?.metricKey || '',
    available: Boolean(activeMetric?.available)
  };
}

export { normalizeMetric };
