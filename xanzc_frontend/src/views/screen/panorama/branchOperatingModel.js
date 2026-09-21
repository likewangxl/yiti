/** 支行视图只消费可信发布包与该机构的真实响应，不提供演示兜底。 */
export function parseBranchSource(response) {
  if (response?.state !== 'published' || response.screenCode !== 'SCR_CORP_OVERVIEW'
    || response.runtimeSchemaVersion !== 2 || response.orgScopeMode !== 'NAMED_GROUP') throw new Error('支行数据源发布身份不可用');
  const pkg = JSON.parse(response.renderPackageJson || '{}');
  if (pkg.schemaVersion !== 2 || !Array.isArray(pkg.components) || !pkg.bindSnapshots
    || pkg.canvasStyle?.presentation?.template !== 'corporate-overview-v1') throw new Error('支行数据源绑定不完整');
  if (!['LIVE', 'PROD'].includes(pkg.canvasStyle?.dataClassification)) throw new Error('支行总览不展示测试或演示数据源');
  // 单值/趋势来源固定机构1，且月均/贷款有随机数定义审计记录。支行只借用机构
  // 明细槽的存款字段；不使用其中月均、贷款、净增或把它包装成同层绩效排名。
  const ranking = pkg.components.find(c => c.propValue?.bindingKey === 'corpRanking');
  const rankBind = ranking ? pkg.bindSnapshots[String(ranking.blockId)]?.bind : null;
  const branchDepositBinding = rankBind?.fields?.orgCode && rankBind?.fields?.deposit
    ? { blockId: ranking.blockId, bind: rankBind } : null;
  const allowed = new Set(['corpRevenue', 'corpNplRate', 'corpCustomers', 'corpTargets', 'corpAttention']);
  const components = pkg.components.filter(c => allowed.has(c.propValue?.bindingKey));
  return { ...response, branchDepositBinding, renderPackage: { ...pkg, components } };
}

function number(value) {
  if (value == null || (typeof value === 'string' && !value.trim()) || !['string', 'number'].includes(typeof value)) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

const DISPLAY_AMOUNT_UNIT = '万元';
const CANONICAL_AMOUNT_UNIT = '亿元';

function displayAmount(value) {
  if (value == null) return value;
  const parsed = number(value);
  return parsed === null ? value : parsed * 10000;
}

/** 将规范模型复制为支行阅读单位，保持来源模型的亿元口径不变。 */
export function toBranchDisplayUnits(model = {}) {
  if (!model || typeof model !== 'object') return model;
  const display = { ...model };
  display.kpis = Array.isArray(model.kpis) ? model.kpis.map(item => {
    if (!item || typeof item !== 'object' || item.unit !== CANONICAL_AMOUNT_UNIT) return item && typeof item === 'object' ? { ...item } : item;
    return { ...item, value: displayAmount(item.value), unit: DISPLAY_AMOUNT_UNIT };
  }) : model.kpis;
  display.targets = Array.isArray(model.targets) ? model.targets.map(item => {
    if (!item || typeof item !== 'object' || item.unit !== CANONICAL_AMOUNT_UNIT) return item && typeof item === 'object' ? { ...item } : item;
    return {
      ...item,
      actual: displayAmount(item.actual),
      target: displayAmount(item.target),
      gap: displayAmount(item.gap),
      unit: DISPLAY_AMOUNT_UNIT
    };
  }) : model.targets;
  const trendAlreadyDisplayed = model.trendUnit === DISPLAY_AMOUNT_UNIT;
  display.trend = Array.isArray(model.trend) ? model.trend.map(row => {
    if (!row || typeof row !== 'object') return row;
    if (trendAlreadyDisplayed) return { ...row };
    return { ...row, deposit: displayAmount(row.deposit), loan: displayAmount(row.loan) };
  }) : model.trend;
  display.trendUnit = DISPLAY_AMOUNT_UNIT;
  display.composition = Array.isArray(model.composition) ? model.composition.map(item => {
    if (!item || typeof item !== 'object' || item.unit !== CANONICAL_AMOUNT_UNIT) return item && typeof item === 'object' ? { ...item } : item;
    return { ...item, value: displayAmount(item.value), unit: DISPLAY_AMOUNT_UNIT };
  }) : model.composition;
  return display;
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

export function buildBranchOperatingModel({ orgCode = '', orgName = '', institutions = [], financial = {}, touch = null, touchError = '', view = {}, touchPeriod = {} } = {}) {
  const style = view.renderPackage?.canvasStyle || {};
  const qualities = financial.sourceQualities || {};
  const definitions = [
    ['corpDeposit', '对公一般性存款余额', '亿元'], ['corpRevenue', '对公FTP收入（折人民币）', '亿元'],
    ['corpNplRate', '对公不良率（分类样本）', '%']
  ];
  const trend = (financial.trend || []).map(row => ({ ...row, loan: null }));
  const comparisons = financial.comparisonTrend || trend;
  const kpis = definitions.map(([key, label, unit]) => {
    const item = (financial.kpis || []).find(k => k.key === key) || {};
    const date = item.date || qualities[key]?.dataDate || '';
    const trendKey = key === 'corpDeposit' ? 'deposit' : key === 'corpLoan' ? 'loan' : null;
    const staleAgeDays = number(qualities[key]?.ageDays);
    return { key, label, value: number(item.value), unit: item.unit || unit, date,
      yoy: trendKey ? comparableChange(item.value, date, comparisons, trendKey, 12) : null,
      mom: trendKey ? comparableChange(item.value, date, comparisons, trendKey, 1) : null,
      status: (financial.issues || []).some(issue => issue.slot === key && issue.code === 'REQUEST_FAILED')
        ? '取数失败' : key === 'corpDeposit' && qualities[key]?.status === 'STALE' && staleAgeDays !== null
          ? `历史批次 · ${staleAgeDays}天前` : number(item.value) === null ? '源数据缺失' : `截至 ${date || '来源未提供日期'}` };
  });
  const targetDate = qualities.corpTargets?.dataDate || '';
  const targets = (financial.targets || []).map((row, index) => {
    const actual = number(row.actual), target = number(row.target);
    return { key: index ? `target-${index}` : 'deposit', label: '对公存款考核目标', actual, target, unit: '亿元', date: targetDate,
      rate: actual !== null && target !== null && target > 0 ? actual / target * 100 : null,
      gap: actual !== null && target !== null ? target - actual : null };
  });
  kpis.push({ key: 'rate', label: '对公存款目标完成率', value: targets.length === 1 ? targets[0].rate : null,
    unit: '%', date: targetDate, yoy: null, mom: null, status: targets.length ? '按考核目标源实际值计算' : '同口径目标待接入' });
  const touchMatches = touch && String(touch.orgId) === String(orgCode);
  const marketing = touchMatches ? [['pendingCount', '待触达'], ['inProgressCount', '进行中'], ['successCount', '已完成'], ['cancelledCount', '已取消'], ['slaWarningCount', 'SLA预警']]
    .map(([key, label]) => ({ label, count: number(touch[key]) })) : [];
  kpis.push(...[['touchTotal', '本月触达任务', 'totalCount'], ['touchCompleted', '本月完成触达', 'successCount']].map(([key, label, field]) => ({
    key, label, unit: '项', value: touchMatches ? number(touch[field]) : null,
    date: touchPeriod.endDate || '', yoy: null, mom: null, status: touchMatches ? '本月任务统计' : '触达汇总待获取'
  })));
  const dates = [...new Set(kpis.map(k => k.date).filter(Boolean))].sort();
  return {
    orgCode, orgName, institutions, kpis, targets, trend, composition: [], marketing,
    targetDate,
    parkedMetrics: ['存款月均：历史定义含随机数，待重新核验', '对公贷款：历史定义含随机数，待重新核验', '零售业务结构：真实来源待接入'],
    projects: [], teams: [], attention: financial.attention || [],
    dataDate: dates.length > 1 ? `${dates[0]} — ${dates.at(-1)}（来源日期不同）` : dates[0] || financial.dataDate || '未提供',
    sourceLabel: view.screenCode ? '已绑定主库来源 · 单机构查询' : '正在核对数据来源', metricLabels: { deposit: '对公一般性存款余额', loan: '对公贷款（待核验）' },
    gaps: { comparison: '月均来源待核验，暂不与时点余额进行对照。', composition: '零售存款来源含测试计算，暂不参与对公／零售构成。',
      marketing: touchError || (touch && !touchMatches ? '触达接口返回其他机构，已拒绝展示。' : '本支行触达汇总暂无可用数据。'),
      projects: '现有资产立项接口按个人查询，支行项目汇总待接入。', teams: '团队目标、贡献与工作负荷汇总待接入。' },
    sources: [
      { label: '本屏取数', detail: '存款改用已发布机构明细槽；逐支行查询，历史每次仅查询一天，不继承固定机构1的单值/趋势源。' },
      { label: '原屏来源说明', detail: style.dataNotice || '来自已发布对公经营大屏，按选中机构收窄查询。' },
      { label: '当前展示范围', detail: '已接入对公指标；零售测试值和手工演示收入不展示。对公指标不代表支行全部业务。' },
      { label: '目标口径', detail: style.sourceAvailability?.corpTargets?.message || '采用目标数据源的实际值及目标值，不与时点余额混算。' },
      { label: '指标核验', detail: '月均/对公贷款存在随机数定义的历史审计记录，未重新核验前不展示。LIVE只表示接口接入。' },
      { label: '同比与环比', detail: '存款余额用精确同期单日查询比较；近六月趋势为前五个月末与当前截至日。缺日留空，历史不足不推算。' },
      ...Object.entries(qualities).map(([key, q]) => ({ label: style.metricLabels?.[key] || key, detail: `${q.dataDate || '日期未提供'} · ${q.status || '状态未提供'}${q.message ? ` · ${q.message}` : ''}` }))
    ]
  };
}
