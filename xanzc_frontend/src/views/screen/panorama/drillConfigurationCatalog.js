const CITY_DIMENSIONS = Object.freeze(['cityCode', 'orgCode', 'cityName']);
const CITY_AMOUNT = Object.freeze([
  ['city-deposit-total', '存款总额', ['deposit'], 'TOP', 'AMOUNT_TOTAL'],
  ['city-deposit-distribution', '存款业务分布', ['deposit', 'corpDeposit', 'retailDeposit'], 'LEFT', 'AMOUNT_DISTRIBUTION'],
  ['city-loan-total', '贷款总额', ['loan'], 'TOP', 'AMOUNT_TOTAL'],
  ['city-loan-distribution', '贷款业务分布', ['loan', 'corpLoan', 'retailLoan'], 'LEFT', 'AMOUNT_DISTRIBUTION'],
  ['city-settlement-deposit', '结算性存款', ['settlementDeposit'], 'TOP', 'METRIC_CARD'],
  ['city-retail-deposit', '零售存款余额', ['retailDeposit'], 'LEFT', 'METRIC_CARD'],
  ['city-retail-deposit-rate', '零售存款完成率', ['retailDepositRate'], 'LEFT', 'COMPLETION'],
  ['city-retail-loan', '零售贷款余额', ['retailLoan'], 'LEFT', 'METRIC_CARD'],
  ['city-retail-loan-rate', '零售贷款完成率', ['retailLoanRate'], 'LEFT', 'COMPLETION'],
  ['city-corp-deposit', '对公存款余额', ['corpDeposit'], 'CENTER', 'METRIC_CARD'],
  ['city-corp-deposit-rate', '对公存款完成率', ['corpDepositRate'], 'CENTER', 'COMPLETION'],
  ['city-corp-loan', '对公贷款余额', ['corpLoan'], 'CENTER', 'METRIC_CARD'],
  ['city-corp-loan-rate', '对公贷款完成率', ['corpLoanRate'], 'CENTER', 'COMPLETION'],
  ['city-revenue', '营业收入', ['revenue'], 'RIGHT', 'METRIC_CARD'],
  ['city-intermediary-income', '中间业务收入', ['intermediaryIncome'], 'RIGHT', 'METRIC_CARD']
]);

function isObject(value) { return value !== null && typeof value === 'object' && !Array.isArray(value); }
function entryKey(entry) { return String(entry?.key || entry?.slot || '').trim(); }
function draftSlots(draftComponents) {
  const slots = new Set();
  for (const component of Array.isArray(draftComponents) ? draftComponents : []) {
    const key = component?.propValue?.bindingKey || component?.bindingKey || component?.slot;
    if (key) slots.add(String(key));
  }
  return slots;
}
function sourceState(slot, slots) {
  const sourceMissing = !slots.has(slot);
  return { sourceMissing, readOnly: sourceMissing };
}
function inherited(key, label, fields, region, visualKind, slot, slots, extra = {}) {
  const fieldSemantics = extra.includeDimensions === false ? [...fields] : [...fields, ...CITY_DIMENSIONS];
  const { includeDimensions, ...metadata } = extra;
  return {
    key, title: label, label, visualLabel: label, kind: 'INHERITED_SLOT', slot,
    fieldSemantics, layoutRegion: region,
    group: region === 'TOP' ? 'overview' : 'business', visualKind, titleEditable: false,
    ...sourceState(slot, slots), ...metadata
  };
}

function buildCityEntries(slots) {
  const entries = CITY_AMOUNT.map(([key, label, fields, region, visualKind]) =>
    inherited(key, label, fields, region, visualKind === 'AMOUNT_DISTRIBUTION' ? 'COMPOSITION_TAB' : visualKind, 'citySummary', slots, {
      layoutGroup: key.startsWith('city-retail-') ? 'RETAIL' : key.startsWith('city-corp-') ? 'CORP'
        : ['city-revenue','city-intermediary-income'].includes(key) ? 'REVENUE' : 'OVERVIEW',
      derivedNotes: ['城市卡片读取城市汇总来源；同一来源内的其他指标字段会保留。']
    }));
  entries.push({
    key: 'city-institution-map', title: '支行网点分布', label: '支行网点分布', visualLabel: '支行网点分布',
    kind: 'READONLY_REGION', source: 'panoramaInstitutions', readOnly: true, titleEditable: false,
    layoutRegion: 'CENTER', layoutGroup:'ANALYSIS', group: 'institution', visualKind: 'INSTITUTION_MAP',
    description: '授权机构目录及已核验坐标', sourceMissing: false
  });
  entries.push(inherited('city-branch-ranking', '支行排名', [
    'orgCode', 'retailDepositRate', 'retailLoanRate', 'retailNplRate',
    'corpDepositRate', 'corpLoanRate', 'corpNplRate'
  ], 'RIGHT', 'INSTITUTION_RANKING', 'branches', slots, {
    label: '支行指标排名', visualLabel: '支行指标排名', group: 'ranking', layoutGroup:'ANALYSIS', includeDimensions: false,
    derivedNotes: ['四类完成率和两类不良率使用支行机构来源；不使用省级金额排名字段。']
  }));
  return entries;
}

function buildBranchEntries(catalogEntries, slots) {
  const linked = (Array.isArray(catalogEntries) ? catalogEntries : [])
    .filter(entry => !['branches', 'ranking', 'citySummary', 'institution-map', 'institution-ranking'].includes(entryKey(entry)))
    .map(original => {
      const key = entryKey(original);
      return {
        ...original, key: `branch-${key}`, kind: 'LINKED_COMPONENT', parentKey: key,
        sourceEntry: original, titleEditable: false, layoutRegion: original.layoutRegion || 'TOP',
        visualKind:original.visualKind || original.kind,
        layoutGroup:key.startsWith('overview-') ? 'OVERVIEW' : key.startsWith('business-retail-') ? 'RETAIL'
          : key.startsWith('business-corp-') ? 'CORP' : key.startsWith('business-revenue-') ? 'REVENUE' : 'ANALYSIS',
        group: original.group || 'shared'
      };
    });
  const branchTrendPresent = slots.has('branchTrend') || (Array.isArray(catalogEntries) && catalogEntries.some(entry => entryKey(entry) === 'branchTrend'));
  if (branchTrendPresent) linked.push({
    key: 'branch-branch-trend', title: '支行趋势', label: '支行趋势', visualLabel: '支行趋势',
    kind: 'INHERITED_SLOT', slot: 'branchTrend', fieldSemantics: ['date', 'deposit', 'loan'],
    layoutRegion: 'CENTER', group: 'trend', visualKind: 'BUSINESS_GROWTH', titleEditable: false, auxiliary:true,
    ...sourceState('branchTrend', slots)
  });
  linked.push({
    key: 'branch-institution-map', title: '未完成指标', label: '未完成指标', visualLabel: '未完成指标',
    kind: 'READONLY_REGION', readOnly: true, titleEditable: false, source: 'model.targets',
    layoutRegion: 'CENTER', layoutGroup:'ANALYSIS', group: 'targets', visualKind: 'PROGRESS', sourceMissing: false,
    description:'由目标实际值、目标值和方向计算，缺失数据不推算；此处没有独立数据绑定。'
  });
  linked.push({
    key: 'branch-employee-ranking', title: '当前支行员工排名', label: '当前支行员工排名', visualLabel: '当前支行员工排名',
    kind: 'READONLY_REGION', readOnly: true, titleEditable: false, source: 'performance-api',
    layoutRegion: 'RIGHT', layoutGroup:'ANALYSIS', group: 'employee-ranking', visualKind: 'INSTITUTION_RANKING', sourceMissing: false,
    description:'来自独立绩效方案与计分接口，按方案和日期读取；不是大屏机构排名数据源。'
  });
  return linked;
}

/** 构造父屏、城市下钻和支行下钻的配置目录；不创建缺失的真实绑定槽位。 */
export function buildDrillConfigurationCatalog({ catalogEntries = [], draftComponents = [] } = {}) {
  const slots = draftSlots(draftComponents);
  return {
    cityEntries: buildCityEntries(slots),
    branchEntries: buildBranchEntries(catalogEntries, slots)
  };
}

export { CITY_DIMENSIONS };
