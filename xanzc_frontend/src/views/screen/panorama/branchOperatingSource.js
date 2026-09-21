/** 只在当前授权候选中选择实际存款非空的支行，优先有有效考核目标。 */
export function chooseCoveredBranch(deposit, targets, entry, institutions = []) {
  if (!Array.isArray(deposit?.rows) || !Array.isArray(deposit?.columns)) return '';
  const fields = entry?.bind?.fields || {};
  const org = deposit.columns.indexOf(fields.orgCode);
  const value = deposit.columns.indexOf(fields.deposit);
  if (org < 0 || value < 0 || ['TEST', 'DEMO'].includes(deposit.quality?.dataClassification)) return '';
  const targetCodes = new Set();
  const tcols = targets?.columns || [];
  const tid = tcols.indexOf('org_code'), actual = tcols.indexOf('actual_value'), goal = tcols.indexOf('target_value');
  if (tid >= 0 && actual >= 0 && goal >= 0) {
    for (const row of targets.rows || []) {
      if (row[actual] != null && String(row[actual]).trim() && Number.isFinite(Number(row[actual])) && Number(row[goal]) > 0) targetCodes.add(String(row[tid]));
    }
  }
  const authorized = new Set(institutions.map(i => String(i.orgCode)));
  const candidates = deposit.rows.filter(row => authorized.has(String(row[org])) && row[value] != null
    && String(row[value]).trim() && Number.isFinite(Number(row[value])))
    .map(row => ({ code: String(row[org]), score: (targetCodes.has(String(row[org])) ? 2 : 0) + (Number(row[value]) > 0 ? 1 : 0) }));
  candidates.sort((a, b) => b.score - a.score || a.code.localeCompare(b.code, 'en', { numeric: true }));
  return candidates[0]?.code || '';
}

/** 从已发布机构明细槽读取单支行；不得使用固定分行本级过滤的单值槽。 */
export function readBranchDeposit(response, entry, orgCode, requestedDate = '') {
  if (!response || !Array.isArray(response.columns) || !Array.isArray(response.rows)) throw new Error('存款来源响应不完整');
  if (['TEST', 'DEMO'].includes(response.quality?.dataClassification)) throw new Error('存款来源为测试数据');
  const fields = entry?.bind?.fields || {};
  const units = { YUAN: 1e8, TEN_THOUSAND: 1e4, HUNDRED_MILLION: 1 };
  const scale = units[entry?.bind?.units?.deposit];
  if (!scale) throw new Error('存款原始单位未声明');
  const columns = response.columns.map(c => typeof c === 'string' ? c : c.col || c.name);
  const orgIndex = columns.indexOf(fields.orgCode), valueIndex = columns.indexOf(fields.deposit);
  if (orgIndex < 0 || valueIndex < 0) throw new Error('存款来源缺少机构或余额列');
  if (response.rows.length > 1) throw new Error('单支行存款必须至多一行，禁止跨日期合计');
  const row = response.rows[0];
  if (row && String(row[orgIndex]) !== String(orgCode)) throw new Error('存款返回机构与所选支行不一致');
  const raw = row?.[valueIndex];
  const value = raw == null || String(raw).trim() === '' || typeof raw === 'boolean' ? null : Number(raw) / scale;
  return { value: Number.isFinite(value) ? value : null,
    date: requestedDate || response.quality?.dataDate || response.dataDate || '', quality: response.quality || null };
}

function shiftedDay(asOf, months) {
  const [y, m, d] = asOf.split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1 - months, 1));
  const end = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth() + 1, 0)).getUTCDate();
  date.setUTCDate(Math.min(d, end));
  return date.toISOString().slice(0, 10);
}

export function branchHistoryDates(asOf) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(asOf || '') || Number.isNaN(Date.parse(asOf))) return { trend: [], comparison: [] };
  const [y, m] = asOf.split('-').map(Number);
  const trend = Array.from({ length: 5 }, (_, i) => new Date(Date.UTC(y, m - 1 - i, 0)).toISOString().slice(0, 10)).reverse();
  return { trend, comparison: [shiftedDay(asOf, 1), shiftedDay(asOf, 12)] };
}

/** 每次RANGE仅查询一天，按机构聚合不会跨日累加余额；并发最多2。 */
export async function loadBranchDeposit({ query, screenCode, entry, orgCode, isCurrent = () => true, onCurrent = () => {} }) {
  if (!entry?.blockId || !orgCode) throw new Error('支行存款明细尚未绑定');
  const identity = { schemaVersion: 2, screenCode, blockId: entry.blockId, contextParams: { orgCode } };
  const current = readBranchDeposit(await query({ ...identity, period: 'LATEST' }), entry, orgCode);
  if (!isCurrent()) return null;
  onCurrent(current);
  const dates = branchHistoryDates(current.date);
  const unique = [...new Set([...dates.trend, ...dates.comparison])];
  const points = new Map();
  const failures = [];
  let index = 0;
  async function worker() {
    while (index < unique.length && isCurrent()) {
      const date = unique[index++];
      try {
        const response = await query({ ...identity, period: 'RANGE', dateFrom: date, dateTo: date });
        if (!isCurrent()) return;
        points.set(date, readBranchDeposit(response, entry, orgCode, date).value);
      } catch (error) {
        if (Number(error?.response?.status || error?.status) === 401 || Number(error?.response?.status || error?.status) === 403) throw error;
        failures.push({ date, message: error.message || '历史取数失败' });
        points.set(date, null);
      }
    }
  }
  await Promise.all([worker(), worker()]);
  if (!isCurrent()) return null;
  const asPoint = date => ({ date, deposit: points.get(date) ?? null, loan: null });
  return { current, trend: [...dates.trend.map(asPoint), ...(current.date ? [{ date: current.date, deposit: current.value, loan: null }] : [])],
    comparisonTrend: dates.comparison.map(asPoint), failures };
}
