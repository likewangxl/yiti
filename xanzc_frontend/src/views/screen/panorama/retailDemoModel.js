/**
 * 零售总览的开发态固定示例。
 *
 * 这里的数据只用于 /screen-preview/retail 的视觉与交互验收，不能作为业务
 * 口径或机构真实位置。零售 AUM 独立给出，刻意不由储蓄余额和个人贷款相加；
 * 客户分层也保留可能重叠的客户数，不在页面层求和。
 */

const demoInstitutions = Object.freeze([
  { orgCode: 'DEMO-RETAIL-610100', name: '西安市分行', cityCode: '610100', cityName: '西安市', located: false },
  { orgCode: 'DEMO-RETAIL-610500', name: '渭南市分行', cityCode: '610500', cityName: '渭南市', located: false },
  { orgCode: 'DEMO-RETAIL-610400', name: '咸阳市分行', cityCode: '610400', cityName: '咸阳市', located: false },
  { orgCode: 'DEMO-RETAIL-610300', name: '宝鸡市分行', cityCode: '610300', cityName: '宝鸡市', located: false },
  { orgCode: 'DEMO-RETAIL-610800', name: '榆林市分行', cityCode: '610800', cityName: '榆林市', located: false },
  // 没有 cityCode 的目录身份必须仍能从“全部机构”查到，页面不从名称猜归属。
  { orgCode: 'DEMO-RETAIL-UNKNOWN', name: '零售数据服务中心', cityCode: null, cityName: null, located: false }
]);

const demoRankings = Object.freeze([
  { orgCode: 'DEMO-RETAIL-610100', name: '西安市分行', aum: 240.60, increase: 12.40, rate: 98.20, nplRate: 1.40, cityCode: '610100' },
  { orgCode: 'DEMO-RETAIL-610500', name: '渭南市分行', aum: 98.20, increase: -4.20, rate: 76.30, nplRate: 2.30, cityCode: '610500' },
  { orgCode: 'DEMO-RETAIL-610400', name: '咸阳市分行', aum: 121.80, increase: 0, rate: 88.10, nplRate: null, cityCode: '610400' },
  { orgCode: 'DEMO-RETAIL-610300', name: '宝鸡市分行', aum: 88.75, increase: 3.10, rate: 82.40, nplRate: 1.90, cityCode: '610300' },
  { orgCode: 'DEMO-RETAIL-610800', name: '榆林市分行', aum: null, increase: 2.60, rate: null, nplRate: 2.70, cityCode: '610800' },
  { orgCode: 'DEMO-RETAIL-UNKNOWN', name: '零售数据服务中心', aum: null, increase: 0, rate: null, nplRate: null, cityCode: null }
]);

export const retailDemoModel = Object.freeze({
  demo: true,
  demoOnly: true,
  dataOrigin: 'LOCAL_RETAIL_DEMO_FIXTURE',
  disclaimer: '本地演示 · 非业务数据；陕西行政区为随包真实边界，机构资料为固定示例。',
  title: '零售经营总览',
  scopeLabel: '陕西省全辖（示例）',
  dataDate: '2026-08-31',
  kpis: Object.freeze([
    { key: 'retailAum', label: '零售AUM', value: 852.60, unit: '亿元', change: 1.72 },
    { key: 'retailDeposit', label: '储蓄余额', value: 572.16, unit: '亿元', change: -0.33 },
    { key: 'retailRevenue', label: '零售营业收入', value: 12.68, unit: '亿元', change: 8.10 },
    { key: 'retailValueCustomers', label: '价值客户', value: 18.62, unit: '万户', change: 4.10 },
    { key: 'retailLoan', label: '个人贷款', value: 368.35, unit: '亿元', change: 5.40 },
    { key: 'retailNplRate', label: '个贷不良率', value: 1.28, unit: '%', change: 0.06 },
    { key: 'retailDepositAverage', label: '储蓄月日均', value: 568.42, unit: '亿元', change: null }
  ]),
  trend: Object.freeze([
    { date: '2026-03', aum: 780.30, deposit: 536.12 },
    { date: '2026-04', aum: 795.60, deposit: 541.40 },
    { date: '2026-05', aum: 810.90, deposit: 557.86 },
    { date: '2026-06', aum: 824.40, deposit: 563.08 },
    { date: '2026-07', aum: 838.20, deposit: 574.04 },
    { date: '2026-08', aum: 852.60, deposit: 572.16 }
  ]),
  segments: Object.freeze([
    { name: '私行客户', customers: 0.12, aum: 126.20 },
    { name: '财富客户', customers: 1.86, aum: 335.00 },
    { name: '潜力客户', customers: 16.64, aum: 244.00 },
    { name: '基础客户', customers: 98.20, aum: 147.40 }
  ]),
  rankings: demoRankings,
  attention: Object.freeze([
    {
      label: '重点客户维护', count: 4, owner: '零售金融部', deadline: '2026-09-15',
      detail: {
        description: '示例中有4项重点客户维护安排待确认，需明确维护责任、客户沟通计划与回访反馈方式。',
        coordination: '请零售金融部协调相关机构确认维护安排，并在截止日前反馈推进情况。',
        source: '重点客户维护清单（演示）'
      }
    },
    {
      label: '风险数据待核验', count: 0, owner: null, deadline: null,
      detail: {
        description: '当前示例数量为0，责任部门与核验期限尚未提供；该数值不能用于判断风险已核验或事项已完成。',
        coordination: '补齐责任部门、核验期限与数据来源后，再确认是否需要进一步协调。',
        source: '风险数据核验清单（演示）'
      }
    },
    {
      label: '收入目标沟通', count: 2, owner: '计划财务部', deadline: '2026-09-20',
      detail: {
        description: '示例中有2项收入目标口径沟通事项，需核对统计期间和收入归集范围，确保实际值与目标可比。',
        coordination: '请计划财务部与零售金融部确认目标口径及归集说明，形成一致的反馈结论。',
        source: '零售收入目标沟通台账（演示）'
      }
    },
    {
      label: '资产负增机构跟进', count: 1, owner: '零售金融部', deadline: '2026-09-18',
      detail: {
        description: '渭南市分行AUM较上月净减4.20亿元，需核查客户资产流出原因、统计口径及后续维护安排。',
        coordination: '请零售金融部会同渭南市分行核实负增原因，明确责任人与跟进计划；不以单月负增直接作绩效结论。',
        source: '机构AUM月度监测清单（演示）'
      }
    }
  ]),
  targets: Object.freeze([
    { name: '年度AUM净增', actual: 72.30, target: 60.00 },
    { name: '年度储蓄余额净增', actual: -3.60, target: 12.00 },
    { name: '年度零售收入', actual: 12.68, target: 16.00 },
    { name: '年度财富中收净增', actual: null, target: null },
    { name: '年度个人贷款净增', actual: 18.35, target: 25.00 }
  ]),
  institutions: demoInstitutions,
  issues: Object.freeze([])
});

export default retailDemoModel;
