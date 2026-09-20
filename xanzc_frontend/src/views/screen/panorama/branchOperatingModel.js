/** 支行视图只消费可信发布包与该机构的真实响应，不提供演示兜底。 */
export function parseBranchSource(response) {
  if (response?.state !== 'published' || response.screenCode !== 'SCR_CORP_OVERVIEW'
    || response.runtimeSchemaVersion !== 2 || response.orgScopeMode !== 'NAMED_GROUP') throw new Error('支行数据源发布身份不可用');
  const pkg = JSON.parse(response.renderPackageJson || '{}');
  if (pkg.schemaVersion !== 2 || !Array.isArray(pkg.components) || !pkg.bindSnapshots
    || pkg.canvasStyle?.presentation?.template !== 'corporate-overview-v1') throw new Error('支行数据源绑定不完整');
  if (!['LIVE', 'PROD'].includes(pkg.canvasStyle?.dataClassification)) throw new Error('支行总览不展示测试或演示数据源');
  // 排除现有对公屏中明确标识为测试对照的排名，也不查询与支行整屏无关的目录指标。
  const allowed = new Set(['corpDeposit', 'corpDepositAverage', 'corpLoan', 'corpRevenue', 'corpNplRate', 'corpCustomers', 'corpTrend', 'corpTargets', 'corpAttention']);
  const components = pkg.components.filter(c => allowed.has(c.propValue?.bindingKey));
  return { ...response, renderPackage: { ...pkg, components } };
}

function number(value) {
  if (value == null || (typeof value === 'string' && !value.trim()) || !['string', 'number'].includes(typeof value)) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

/** 仅同一时点指标、精确对应日期且唯一的历史点可生成增长率。 */
export function comparableChange(current, dataDate, trend, key, months) {
  if (number(current) === null || !/^\d{4}-\d{2}-\d{2}$/.test(dataDate || '')) return null;
  const [year, month, day] = dataDate.split('-').map(Number);
  const base = new Date(Date.UTC(year, month - 1 - months, 1));
  const lastDay = new Date(Date.UTC(base.getUTCFullYear(), base.getUTCMonth() + 1, 0)).getUTCDate();
  base.setUTCDate(Math.min(day, lastDay));
  const expected = base.toISOString().slice(0, 10);
  const matches = (trend || []).filter(row => row.date === expected);
  if (matches.length !== 1) return null;
  const previous = number(matches[0][key]);
  if (previous === null || previous <= 0) return null;
  const result = (Number(current) - previous) / previous * 100;
  return Number.isFinite(result) ? Math.round(result * 100) / 100 : null;
}

export function buildBranchOperatingModel({ orgCode = '', orgName = '', institutions = [], financial = {}, touch = null, touchError = '', view = {} } = {}) {
  const style = view.renderPackage?.canvasStyle || {};
  const qualities = financial.sourceQualities || {};
  const definitions = [
    ['corpDeposit', '对公一般性存款余额', '亿元'], ['corpDepositAverage', '对公一般性存款月均', '亿元'],
    ['corpLoan', '对公一般性贷款余额', '亿元'], ['corpRevenue', '对公FTP收入', '亿元'],
    ['corpNplRate', '对公贷款不良率', '%']
  ];
  const trend = financial.trend || [];
  const kpis = definitions.map(([key, label, unit]) => {
    const item = (financial.kpis || []).find(k => k.key === key) || {};
    const date = item.date || qualities[key]?.dataDate || '';
    const trendKey = key === 'corpDeposit' ? 'deposit' : key === 'corpLoan' ? 'loan' : null;
    return { key, label, value: number(item.value), unit: item.unit || unit, date,
      yoy: trendKey ? comparableChange(item.value, date, trend, trendKey, 12) : null,
      mom: trendKey ? comparableChange(item.value, date, trend, trendKey, 1) : null,
      status: (financial.issues || []).some(issue => issue.slot === key && issue.code === 'REQUEST_FAILED')
        ? '取数失败' : number(item.value) === null ? '源数据缺失' : `截至 ${date || '来源未提供日期'}` };
  });
  const targets = (financial.targets || []).map((row, index) => {
    const actual = number(row.actual), target = number(row.target);
    return { key: index ? `target-${index}` : 'deposit', label: '对公存款考核目标', actual, target, unit: '亿元',
      rate: actual !== null && target !== null && target > 0 ? actual / target * 100 : null,
      gap: actual !== null && target !== null ? target - actual : null };
  });
  kpis.push({ key: 'rate', label: '对公存款目标完成率', value: targets.length === 1 ? targets[0].rate : null,
    unit: '%', yoy: null, mom: null, status: targets.length ? '按考核目标源实际值计算' : '同口径目标待接入' });
  const touchMatches = touch && String(touch.orgId) === String(orgCode);
  const marketing = touchMatches ? [['pendingCount', '待触达'], ['inProgressCount', '进行中'], ['successCount', '已完成'], ['slaWarningCount', 'SLA预警']]
    .map(([key, label]) => ({ label, count: number(touch[key]) })) : [];
  const dates = [...new Set(kpis.map(k => k.date).filter(Boolean))].sort();
  return {
    orgCode, orgName, institutions, kpis, targets, trend, composition: [], marketing,
    projects: [], teams: [], attention: financial.attention || [],
    dataDate: dates.length > 1 ? `${dates[0]} — ${dates.at(-1)}（来源日期不同）` : dates[0] || financial.dataDate || '未提供',
    sourceLabel: view.screenCode ? '已绑定主库来源 · 单机构查询' : '正在核对数据来源', metricLabels: { deposit: '对公一般性存款余额', loan: '对公一般性贷款余额' },
    gaps: { composition: '零售存款来源含测试计算，暂不参与对公／零售构成。',
      marketing: touchError || (touch && !touchMatches ? '触达接口返回其他机构，已拒绝展示。' : '本支行触达汇总暂无可用数据。'),
      projects: '现有资产立项接口按个人查询，支行项目汇总待接入。', teams: '团队目标、贡献与工作负荷汇总待接入。' },
    sources: [
      { label: '经营来源', detail: style.dataNotice || '来自已发布对公经营大屏，按选中机构收窄查询。' },
      { label: '当前展示范围', detail: '已接入对公指标；零售测试值和手工演示收入不展示。对公指标不代表支行全部业务。' },
      { label: '目标口径', detail: style.sourceAvailability?.corpTargets?.message || '采用目标数据源的实际值及目标值，不与时点余额混算。' },
      { label: '同比与环比', detail: '时点余额仅用精确同期日期比较；历史不足显示缺失。月均和收入未绑定同口径历史。' },
      ...Object.entries(qualities).map(([key, q]) => ({ label: style.metricLabels?.[key] || key, detail: `${q.dataDate || '日期未提供'} · ${q.status || '状态未提供'}${q.message ? ` · ${q.message}` : ''}` }))
    ]
  };
}
