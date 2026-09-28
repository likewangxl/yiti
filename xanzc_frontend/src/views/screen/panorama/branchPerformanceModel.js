/**
 * 支行 KPI 机构明细与员工排名的纯模型。
 *
 * 该模块只处理 API 已返回的结果，不生成指标定义，不补齐虚构的计分格，
 * 并把机构和员工两个维度的指标列分开识别。
 */

import { parseFiniteNumber } from './branchAchievementModel.js';

const OWN = Object.prototype.hasOwnProperty;

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function hasOwn(value, key) {
  return isObject(value) && OWN.call(value, key);
}

function text(value) {
  return value === null || value === undefined ? '' : String(value).trim();
}

function cloneValue(value, seen = new WeakMap()) {
  if (Array.isArray(value)) return value.map(item => cloneValue(item, seen));
  if (!isObject(value)) return value;
  if (seen.has(value)) return seen.get(value);
  const clone = {};
  seen.set(value, clone);
  for (const [key, item] of Object.entries(value)) clone[key] = cloneValue(item, seen);
  return clone;
}

function normalizedDimension(value) {
  const dimension = text(value).toUpperCase();
  if (!dimension) return '';
  return dimension === 'ORG' || dimension === 'EMP' ? dimension : 'UNKNOWN';
}

function normalizedDirection(value) {
  return text(value).toUpperCase() === 'DOWN' ? 'DOWN' : 'UP';
}

function recordType(record, fallback) {
  const type = text(record?.subjectType).toUpperCase();
  return type || fallback;
}

function identityOf(record) {
  const value = record?.subjectId ?? record?.id;
  return value === null || value === undefined ? '' : String(value).trim();
}

function firstOwn(record, fields) {
  if (!isObject(record)) return { present: false, value: undefined };
  for (const field of fields) {
    if (hasOwn(record, field)) return { present: true, value: record[field] };
  }
  return { present: false, value: undefined };
}

function valueFromCell(cell, fields) {
  if (!isObject(cell)) return null;
  return parseFiniteNumber(firstOwn(cell, fields).value);
}

function cellOf(record, metricCode) {
  const metrics = isObject(record?.metrics) ? record.metrics : {};
  return hasOwn(metrics, metricCode) && isObject(metrics[metricCode]) ? metrics[metricCode] : null;
}

function contextValue(record, fields) {
  const result = firstOwn(record, fields);
  const value = text(result.value);
  return value || null;
}

function contextOf(records) {
  const list = Array.isArray(records) ? records : [];
  const first = list.find(record => contextValue(record, ['schemeCode', 'scheme', 'kpiSchemeCode'])
    || contextValue(record, ['dataDate', 'date', 'asOfDate']));
  return {
    schemeCode: contextValue(first, ['schemeCode', 'scheme', 'kpiSchemeCode']),
    dataDate: contextValue(first, ['dataDate', 'date', 'asOfDate'])
  };
}

function matchesContext(record, context) {
  if (!isObject(record)) return false;
  const scheme = contextValue(record, ['schemeCode', 'scheme', 'kpiSchemeCode']);
  const date = contextValue(record, ['dataDate', 'date', 'asOfDate']);
  if (context.schemeCode && scheme && context.schemeCode !== scheme) return false;
  if (context.dataDate && date && context.dataDate !== date) return false;
  return true;
}

function metricDefinitions(metrics, context) {
  if (!Array.isArray(metrics)) return [];
  const seen = new Set();
  return metrics.reduce((result, metric, index) => {
    if (!isObject(metric)) return result;
    const metricCode = text(metric.metricCode ?? metric.code ?? metric.key);
    if (!metricCode) return result;
    const metricScheme = text(metric.schemeCode ?? metric.scheme ?? metric.kpiSchemeCode);
    if (context.schemeCode && metricScheme && metricScheme !== context.schemeCode) return result;
    const dimension = normalizedDimension(metric.baseDim);
    const uniqueKey = `${dimension}|${metricCode}`;
    if (seen.has(uniqueKey)) return result;
    seen.add(uniqueKey);
    result.push({
      source: cloneValue(metric),
      metricCode,
      metricName: text(metric.metricName ?? metric.name) || metricCode,
      baseDim: dimension,
      direction: normalizedDirection(metric.direction),
      index
    });
    return result;
  }, []);
}

function cellValues(cell) {
  const actualField = firstOwn(cell, ['actual', 'actualValue']);
  const targetField = firstOwn(cell, ['target', 'targetValue']);
  const baseField = firstOwn(cell, ['base', 'baseValue']);
  const actual = parseFiniteNumber(actualField.value);
  const target = parseFiniteNumber(targetField.value);
  const base = baseField.present ? parseFiniteNumber(baseField.value) : null;
  const effectiveActual = actual !== null && base !== null ? actual - base : null;
  const safeActual = effectiveActual !== null && Number.isFinite(effectiveActual) ? effectiveActual : null;
  const sourceRateField = firstOwn(cell, ['completeRate', 'completionRate']);
  const sourceRate = parseFiniteNumber(sourceRateField.value);
  let completionRate = sourceRate;
  if (target === null || target <= 0) completionRate = null;
  else if (completionRate === null && safeActual !== null && target !== null) {
    const calculated = safeActual / target * 100;
    completionRate = Number.isFinite(calculated) ? calculated : null;
  }
  return {
    actual,
    target,
    base,
    effectiveActual: safeActual,
    completionRate,
    score: valueFromCell(cell, ['score'])
  };
}

function dimensionCodes(records) {
  const codes = new Set();
  for (const record of records) {
    const values = isObject(record?.metrics) ? record.metrics : {};
    for (const code of Object.keys(values)) codes.add(String(code));
  }
  return codes;
}

function splitDefinitions(definitions, orgRecords, empRecords) {
  const orgCodes = dimensionCodes(orgRecords);
  const empCodes = dimensionCodes(empRecords);
  const org = [], emp = [];
  const orgSeen = new Set(), empSeen = new Set();
  for (const definition of definitions) {
    if ((definition.baseDim === 'ORG' || (!definition.baseDim && orgCodes.has(definition.metricCode)))
      && !orgSeen.has(definition.metricCode)) {
      orgSeen.add(definition.metricCode);
      org.push(definition);
    }
    if ((definition.baseDim === 'EMP' || (!definition.baseDim && empCodes.has(definition.metricCode)))
      && !empSeen.has(definition.metricCode)) {
      empSeen.add(definition.metricCode);
      emp.push(definition);
    }
  }
  return { org, emp };
}

function classifyMetric(cell, definition) {
  if (!cell) return 'missing';
  const values = cellValues(cell);
  // 排名完整性以有限 score 为硬门槛；零分是有效计分，空白/非法分数才是缺失。
  if (values.score === null) return 'missing';
  // 零目标不可分类，即使后端错误地带有一个非空完成率也不能显示为达标。
  if (values.target === null || values.target <= 0 || values.effectiveActual === null) return 'missing';
  if (values.completionRate === null) return 'missing';
  const completed = values.completionRate >= 100;
  return completed ? 'completed' : 'incomplete';
}

function orgItem(definition, record) {
  const cell = cellOf(record, definition.metricCode);
  const values = cellValues(cell);
  const sourceCell = cell ? cloneValue(cell) : {};
  return {
    ...cloneValue(definition.source),
    ...sourceCell,
    key: definition.metricCode,
    label: definition.metricName,
    metricCode: definition.metricCode,
    metricName: definition.metricName,
    baseDim: 'ORG',
    sourceActual: values.actual,
    actual: values.effectiveActual,
    actualValue: values.effectiveActual,
    base: values.base,
    target: values.target,
    targetValue: values.target,
    rate: values.completionRate,
    // 保留来源完成率的权威标记；当 actual/target 都存在时，成就模型仍按冻结契约自行计算。
    rateOnly: values.completionRate !== null,
    completionRate: values.completionRate,
    completeRate: values.completionRate,
    score: values.score,
    direction: definition.direction
  };
}

function rowSummary(record, definitions) {
  const cells = definitions.map(definition => {
    const cell = cellOf(record, definition.metricCode);
    return { definition, cell, state: classifyMetric(cell, definition), values: cellValues(cell) };
  });
  const counts = {
    completedCount: cells.filter(item => item.state === 'completed').length,
    incompleteCount: cells.filter(item => item.state === 'incomplete').length,
    missingCount: cells.filter(item => item.state === 'missing').length,
    metricCount: definitions.length
  };
  const scoresComplete = cells.length > 0 && cells.every(item => item.values.score !== null);
  const totalScore = parseFiniteNumber(record?.totalScore);
  const summary = {
    subjectId: identityOf(record),
    subjectName: record?.subjectName ?? record?.name ?? '',
    totalScore,
    ...counts
  };
  return { summary, rankable: scoresComplete && totalScore !== null };
}

function rankRows(rows) {
  const ranked = rows
    .filter(row => row.rankable)
    .map(row => row.summary)
    .sort((left, right) => right.totalScore - left.totalScore);
  ranked.forEach((row, index) => {
    row.rank = index > 0 && row.totalScore === ranked[index - 1].totalScore
      ? ranked[index - 1].rank
      : index + 1;
  });
  return ranked;
}

function aggregateScore(ranking, selector) {
  if (!ranking.length) return null;
  const values = ranking.map(selector);
  const sum = values.reduce((total, value) => total + value, 0);
  return Number.isFinite(sum) ? sum / values.length : null;
}

/**
 * 生成选中支行机构指标和同方案同日期的员工排名。
 *
 * @param {{metrics?: Array<object>, orgRecords?: Array<object>, empRecords?: Array<object>}} input KPI 结果
 * @returns {{orgItems: Array<object>, ranking: Array<object>, unranked: Array<object>, participantCount: number, rankedCount: number, averageScore: number|null, highestScore: number|null, metricCount: number}}
 */
export function buildBranchPerformanceModel(input = {}) {
  const { metrics = [], orgRecords = [], empRecords = [] } = isObject(input) ? input : {};
  const sourceOrgRecords = Array.isArray(orgRecords) ? orgRecords : [];
  const sourceEmpRecords = Array.isArray(empRecords) ? empRecords : [];
  const context = contextOf(sourceOrgRecords);
  const currentOrgRecords = sourceOrgRecords.filter(record => recordType(record, 'ORG') === 'ORG' && matchesContext(record, context));
  const currentEmpRecords = sourceEmpRecords.filter(record => recordType(record, 'EMP') === 'EMP' && matchesContext(record, context));
  const definitions = metricDefinitions(metrics, context);
  const split = splitDefinitions(definitions, currentOrgRecords, currentEmpRecords);
  const selectedOrg = currentOrgRecords[0] || null;
  const orgItems = split.org.map(definition => orgItem(definition, selectedOrg));
  const summaries = currentEmpRecords.map(record => ({ record, ...rowSummary(record, split.emp) }));
  const ranking = rankRows(summaries);
  const rankedIds = new Set(ranking.map(row => row.subjectId));
  const unranked = summaries.filter(item => !rankedIds.has(item.summary.subjectId)).map(item => item.summary);
  const allDefinitions = [];
  const allSeen = new Set();
  for (const definition of [...split.org, ...split.emp]) {
    const uniqueKey = `${definition.baseDim}|${definition.metricCode}`;
    if (allSeen.has(uniqueKey)) continue;
    allSeen.add(uniqueKey);
    allDefinitions.push(definition);
  }
  return {
    orgItems,
    ranking,
    unranked,
    participantCount: currentEmpRecords.length,
    rankedCount: ranking.length,
    averageScore: aggregateScore(ranking, row => row.totalScore),
    highestScore: ranking.length ? ranking[0].totalScore : null,
    metricCount: allDefinitions.length
  };
}

export { buildBranchCoreMetrics } from './branchAchievementModel.js';
