const TAB_DEFINITIONS = [
  ['retailDepositRate', '零售存款', 'rate', 'DESC', ['retailDepositRate', 'retailDepositCompletionRate', 'retailDepositCompleteRate', '测试_零售存款目标完成率']],
  ['retailLoanRate', '零售贷款', 'rate', 'DESC', ['retailLoanRate', 'retailLoanCompletionRate', 'retailLoanCompleteRate', '测试_零售贷款目标完成率']],
  ['retailNplRate', '零售贷款不良率', 'npl', 'ASC', ['retailNplRate', 'retailLoanNplRate', 'retail.nplRate']],
  ['corpDepositRate', '对公存款', 'rate', 'DESC', ['corpDepositRate', 'corpDepositCompletionRate', 'corpDepositCompleteRate', '测试_对公存款目标完成率']],
  ['corpLoanRate', '对公贷款', 'rate', 'DESC', ['corpLoanRate', 'corpLoanCompletionRate', 'corpLoanCompleteRate', '测试_对公贷款目标完成率']],
  ['corpNplRate', '对公贷款不良率', 'npl', 'ASC', ['corpNplRate', 'corporateNplRate', 'corp.nplRate', 'corpLoanNplRate']]
];

export const CITY_BRANCH_RANKING_TABS = Object.freeze(TAB_DEFINITIONS.map(([key, label, kind, direction, fields]) => Object.freeze({
  key, label, kind, direction, fields: Object.freeze([...fields])
})));

const PERCENT_UNITS = new Set(['PERCENT', 'RATIO', '%', '百分比', '比例']);

function object(value) { return value !== null && typeof value === 'object' && !Array.isArray(value); }
function text(value) { return value === null || value === undefined ? '' : String(value).trim(); }
function finite(value) {
  if (value === null || value === undefined || value === '' || typeof value === 'boolean' || Array.isArray(value) || object(value)) return null;
  if (typeof value === 'string' && value.trim() === '') return null;
  if (typeof value !== 'number' && typeof value !== 'string') return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}
function canonicalUnit(value) {
  const raw = text(value).toUpperCase();
  if (raw === '％' || raw === '百分比' || raw === '比例') return 'PERCENT';
  if (raw === 'RATIO' || raw === '%' || raw === 'PERCENT') return raw === 'RATIO' ? 'RATIO' : 'PERCENT';
  if (raw === '元' || raw === 'YUAN') return 'YUAN';
  if (raw === '万元' || raw === 'TEN_THOUSAND') return 'TEN_THOUSAND';
  if (raw === '亿元' || raw === 'HUNDRED_MILLION') return 'HUNDRED_MILLION';
  return raw;
}
function orgCodeOf(item) { return text(item?.orgCode ?? item?.org_code); }
function cityCodeOf(item) { return text(item?.cityCode ?? item?.city_code); }
function metricNode(metrics, fields) {
  if (!object(metrics)) return { present: false, value: null, unit: '' };
  let missingNode = null;
  for (const field of fields) {
    if (!Object.prototype.hasOwnProperty.call(metrics, field)) continue;
    const value = metrics[field];
    const node = object(value)
      ? { present: true, field, value: value.value, unit: value.unit || value.sourceUnit || '' }
      : { present: true, field, value, unit: metrics[`${field}Unit`] || '' };
    if (node.value === null || node.value === undefined) {
      missingNode ||= node;
      continue;
    }
    return node;
  }
  return missingNode || { present: false, value: null, unit: '' };
}
function normalizeValue(node, kind) {
  if (!node?.present) return null;
  const value = finite(node.value);
  if (value === null) return null;
  const unit = canonicalUnit(node.unit);
  if (kind === 'npl' && value < 0) return null;
  if (node.unit && !PERCENT_UNITS.has(unit)) return null;
  if (unit === 'RATIO') return value * 100;
  return value;
}
function fieldsFor(tab) { return tab.fields || [tab.key]; }
function presentationOf(source) {
  if (!object(source)) return {};
  if (object(source.displayPresentation)) return presentationOf(source.displayPresentation);
  if (source.displaySchemaVersion !== undefined || source.display) return source;
  if (object(source.presentation)) return presentationOf(source.presentation);
  if (object(source.canvasStyle?.presentation)) return presentationOf(source.canvasStyle.presentation);
  if (object(source.renderPackage?.canvasStyle?.presentation)) return presentationOf(source.renderPackage.canvasStyle.presentation);
  return {};
}
function presentationBlockIds(sourcePresentation) {
  const presentation = presentationOf(sourcePresentation);
  const components = Array.isArray(presentation?.display?.components) ? presentation.display.components : [];
  return [...new Set(components.flatMap(component => Array.isArray(component?.dataRefs)
    ? component.dataRefs.map(ref => ref?.blockId).filter(id => id !== undefined && id !== null).map(String) : []))];
}
function blockRows(block) {
  if (Array.isArray(block)) return block;
  if (object(block) && Array.isArray(block.rows)) return block.rows;
  return [];
}
function blockMetricForOrg({ blockResults, blockIds, orgCode, tab }) {
  if (!object(blockResults) || !blockIds.length) return null;
  const matches = [];
  let ambiguous = false;
  for (const blockId of blockIds) {
    const block = blockResults[blockId] ?? blockResults[String(blockId)];
    const rows = blockRows(block).filter(row => orgCodeOf(row) === orgCode);
    if (rows.length !== 1) {
      if (rows.length > 1 && rows.some(row => metricNode(row, fieldsFor(tab)).present)) ambiguous = true;
      continue;
    }
    const node = metricNode(rows[0], fieldsFor(tab));
    if (!node.present) continue;
    matches.push({
      ...node,
      unit: node.unit || block?.unitByField?.[node.field] || block?.unit || ''
    });
  }
  if (ambiguous) return null;
  if (!matches.length) return null;
  const validMatches = matches.filter(node => normalizeValue(node, tab.kind) !== null);
  if (!validMatches.length) return null;
  const normalized = validMatches.map(node => normalizeValue(node, tab.kind));
  if (normalized.some(value => value !== normalized[0])) return null;
  return validMatches[0];
}
function readBranchValue(branch, tab, source) {
  const direct = metricNode(branch?.metrics, fieldsFor(tab));
  if (direct.present) return normalizeValue(direct, tab.kind);
  const block = blockMetricForOrg({ ...source, orgCode: orgCodeOf(branch), tab });
  return normalizeValue(block, tab.kind);
}
function comparisonRank(left, right, direction) {
  const diff = direction === 'ASC' ? left.value - right.value : right.value - left.value;
  if (diff !== 0) return diff;
  return left.orgCode.localeCompare(right.orgCode);
}

export function buildCityBranchRankingModel(input = {}) {
  const cityCode = text(input.cityCode);
  const institutions = Array.isArray(input.institutions) ? input.institutions.filter(object) : [];
  const branches = institutions.filter(branch => cityCodeOf(branch) === cityCode);
  const configuredBlockIds = presentationBlockIds(input.sourcePresentation);
  const sourceBlocks = object(input.blockResults) ? input.blockResults : {};
  const orgScopedBlockIds = Object.entries(sourceBlocks).filter(([, block]) => blockRows(block).some(row => orgCodeOf(row))).map(([id]) => id);
  const blockIds = [...new Set([...configuredBlockIds, ...orgScopedBlockIds])];
  const source = { blockResults: input.blockResults, blockIds };
  const activeTabKey = CITY_BRANCH_RANKING_TABS.some(tab => tab.key === input.activeTabKey)
    ? input.activeTabKey : CITY_BRANCH_RANKING_TABS[0].key;
  const tab = CITY_BRANCH_RANKING_TABS.find(item => item.key === activeTabKey);
  const rows = [];
  const missingRows = [];
  branches.forEach(branch => {
    const value = readBranchValue(branch, tab, source);
    const row = { orgCode: orgCodeOf(branch), orgName: text(branch.orgName ?? branch.org_name) || orgCodeOf(branch), value, unit: '%' };
    if (value === null) missingRows.push(row);
    else rows.push(row);
  });
  rows.sort((left, right) => comparisonRank(left, right, tab.direction));
  rows.forEach((row, index) => {
    row.rank = index > 0 && row.value === rows[index - 1].value ? rows[index - 1].rank : index + 1;
  });
  return {
    tabs: CITY_BRANCH_RANKING_TABS,
    activeTabKey,
    tab,
    rows,
    missingRows,
    missingCount: missingRows.length,
    total: rows.length
  };
}
