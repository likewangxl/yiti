import { CORPORATE_TEMPLATE } from './corporateBindings';
import { RETAIL_TEMPLATE } from './retailBindings';

const IDENTITY_FIELDS = Object.freeze([
  'orgCode', 'orgName', 'cityCode', 'cityName', 'ownerOperatingOrgCode',
  'parentOrgCode', 'lng', 'lat', 'coordSys', 'located'
]);

function entry(slot, label, fieldSemantics, options = {}) {
  const { titleEditable = false, derivedNotes = [] } = options;
  return Object.freeze({
    key: slot,
    slot: options.slot || slot,
    label,
    fieldSemantics: Object.freeze([...fieldSemantics]),
    kind: 'LEGACY_COMPONENT',
    editable: true,
    titleEditable,
    derivedNotes: Object.freeze([...derivedNotes])
  });
}

const CORPORATE_ENTRIES = Object.freeze([
  entry('corpDeposit', '对公存款余额', ['value', 'change', 'date'], { titleEditable: true }),
  entry('corpDepositAverage', '对公存款月日均', ['value', 'change', 'date'], { titleEditable: true }),
  entry('corpLoan', '对公贷款余额', ['value', 'change', 'date'], { titleEditable: true }),
  entry('corpRevenue', '对公营业收入', ['value', 'change', 'date'], { titleEditable: true }),
  entry('corpCustomers', '有效对公客户', ['value', 'change', 'date'], { titleEditable: true }),
  entry('corpNplRate', '对公不良率', ['value', 'change', 'date'], { titleEditable: true }),
  entry('corpTrend', '对公经营趋势', ['date', 'deposit', 'loan']),
  entry('corpSegments', '对公重点客群', ['name', 'customers', 'loan'], {
    derivedNotes: ['户均贷款由客群贷款与客户数派生，仅展示不可绑定']
  }),
  entry('corpRanking', '对公机构排名 / 数值对照', ['orgCode', 'name', 'deposit', 'increase', 'rate', 'nplRate'], {
    derivedNotes: ['排名展示取决于机构层级与数据完整性，数据不完整时仅作数值对照']
  }),
  entry('corpAttention', '对公经营关注', ['label', 'count', 'owner', 'deadline']),
  entry('corpTargets', '对公目标', ['name', 'actual', 'target'], {
    derivedNotes: ['目标进度与差额由实际值和目标值计算，无需单独绑定']
  }),
  entry('branches', '支行机构', IDENTITY_FIELDS)
]);

const RETAIL_ENTRIES = Object.freeze([
  entry('retailAum', '零售AUM', ['value', 'change', 'date'], { titleEditable: true }),
  entry('retailDeposit', '储蓄存款余额', ['value', 'change', 'date'], { titleEditable: true }),
  entry('retailDepositAverage', '储蓄月日均', ['value', 'change', 'date'], { titleEditable: true }),
  entry('retailRevenue', '零售营业收入', ['value', 'change', 'date'], { titleEditable: true }),
  entry('retailValueCustomers', '价值客户', ['value', 'change', 'date'], { titleEditable: true }),
  entry('retailLoan', '个人贷款', ['value', 'change', 'date'], { titleEditable: true }),
  entry('retailNplRate', '个贷不良率', ['value', 'change', 'date'], { titleEditable: true }),
  // retailDataAdapter 输出 depositAverage 后，RetailTrend 使用余额/月日均分支。
  entry('retailTrend', '储蓄余额与月日均趋势', ['date', 'deposit', 'depositAverage']),
  entry('retailSegments', '零售客群', ['name', 'customers', 'aum'], {
    derivedNotes: ['客户占比、资产占比和户均AUM由客户数与资产额派生，仅展示不可绑定']
  }),
  entry('retailRanking', '零售机构存款对照', ['orgCode', 'name', 'deposit', 'average', 'date'], {
    slot: 'retailRanking',
    derivedNotes: ['差额由月日均余额减存款余额计算，无需单独绑定。历史排名绑定保留。']
  }),
  entry('retailAttention', '零售经营关注', ['label', 'count', 'owner', 'deadline']),
  entry('retailTargets', '零售目标', ['name', 'actual', 'target'], {
    derivedNotes: ['目标进度与差额由实际值和目标值计算，无需单独绑定']
  }),
  entry('branches', '支行机构', IDENTITY_FIELDS)
]);

/** 返回非 schema1 对公/零售运行页实际消费的 legacy 组件目录。 */
export function buildLegacyComponentCatalog(template) {
  const entries = template === CORPORATE_TEMPLATE ? CORPORATE_ENTRIES
    : template === RETAIL_TEMPLATE ? RETAIL_ENTRIES : [];
  const labels={corpSegments:'重点客群信贷',corpAttention:'需要协调的事项',corpRanking:'对公机构排名 / 数值对照',corpTargets:'对公经营目标',corpTrend:'对公存贷款趋势',
    retailSegments:'客户分层',retailAttention:'经营事项与数据核验',retailTrend:'零售存款余额与月日均趋势',retailRanking:'机构存款对比',retailTargets:'零售经营目标',branches:'陕西省机构分布'};
  return entries.map(item => ({ ...item, label:labels[item.key] || item.label, fieldSemantics: [...item.fieldSemantics], derivedNotes: [...item.derivedNotes],
    ...(item.key==='branches' ? {kind:'INSTITUTION_MAP',readOnly:true,editable:false,titleEditable:false,derivedNotes:['地图身份与坐标来自授权机构目录，地图经营指标沿用机构排名；不能通过这里覆盖机构台账。']} : {}) }));
}
