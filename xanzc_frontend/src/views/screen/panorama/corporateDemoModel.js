/**
 * 对公总览的开发态固定示例。
 * 数字、机构与坐标仅服务视觉和交互验收，运行态不得 import 本文件。
 * 客群标签可能交叉，因此示例只展示来源行，不计算客群合计。
 */

const demoInstitutions = Object.freeze([
  { orgCode: 'DEMO-CORP-610100', name: '西安市分行', cityCode: '610100', cityName: '西安市', located: false },
  { orgCode: 'DEMO-CORP-610500', name: '渭南市分行', cityCode: '610500', cityName: '渭南市', located: false },
  { orgCode: 'DEMO-CORP-610400', name: '咸阳市分行', cityCode: '610400', cityName: '咸阳市', located: false },
  { orgCode: 'DEMO-CORP-610300', name: '宝鸡市分行', cityCode: '610300', cityName: '宝鸡市', located: false },
  { orgCode: 'DEMO-CORP-610800', name: '榆林市分行', cityCode: '610800', cityName: '榆林市', located: false },
  { orgCode: 'DEMO-CORP-UNKNOWN', name: '对公数据服务中心', cityCode: null, cityName: null, located: false }
]);

const demoRankings = Object.freeze([
  { orgCode: 'DEMO-CORP-610100', name: '西安市分行', deposit: 420.60, increase: 12.40, rate: 98.20, nplRate: 1.40, cityCode: '610100' },
  { orgCode: 'DEMO-CORP-610500', name: '渭南市分行', deposit: 116.20, increase: -4.20, rate: 76.30, nplRate: 2.30, cityCode: '610500' },
  { orgCode: 'DEMO-CORP-610400', name: '咸阳市分行', deposit: 141.80, increase: 0, rate: 88.10, nplRate: null, cityCode: '610400' },
  { orgCode: 'DEMO-CORP-610300', name: '宝鸡市分行', deposit: 98.75, increase: 3.10, rate: 82.40, nplRate: 1.90, cityCode: '610300' },
  { orgCode: 'DEMO-CORP-610800', name: '榆林市分行', deposit: null, increase: 2.60, rate: null, nplRate: 2.70, cityCode: '610800' },
  { orgCode: 'DEMO-CORP-UNKNOWN', name: '对公数据服务中心', deposit: null, increase: 0, rate: null, nplRate: null, cityCode: null }
]);

export const corporateDemoModel = Object.freeze({
  demo: true,
  demoOnly: true,
  dataOrigin: 'LOCAL_CORPORATE_DEMO_FIXTURE',
  disclaimer: '本地演示 · 非业务数据；陕西行政区为随包真实边界，机构资料为固定示例。',
  title: '对公经营总览',
  scopeLabel: '陕西省全辖（示例）',
  dataDate: '2026-08-31',
  kpis: Object.freeze([
    { key: 'corpDeposit', label: '对公存款余额', value: 1286.42, unit: '亿元', change: -0.14 },
    { key: 'corpDepositAverage', label: '对公存款月日均', value: 1287.26, unit: '亿元', change: null },
    { key: 'corpLoan', label: '对公贷款余额', value: 968.35, unit: '亿元', change: 0.83 },
    { key: 'corpRevenue', label: '对公营业收入', value: 28.68, unit: '亿元', change: 8.10 },
    { key: 'corpCustomers', label: '有效对公客户', value: 6.112, unit: '万户', change: 4.10 },
    { key: 'corpNplRate', label: '对公不良率', value: 1.28, unit: '%', change: 0.06 }
  ]),
  trend: Object.freeze([
    { date: '2026-03', deposit: 1210.30, loan: 902.12 },
    { date: '2026-04', deposit: 1235.60, loan: 918.40 },
    { date: '2026-05', deposit: 1250.90, loan: 935.86 },
    { date: '2026-06', deposit: 1274.40, loan: 949.08 },
    { date: '2026-07', deposit: 1288.20, loan: 960.42 },
    { date: '2026-08', deposit: 1286.42, loan: 968.35 }
  ]),
  segments: Object.freeze([
    { name: '科技型企业', customers: 0.812, loan: 246.20 },
    { name: '绿色金融客户', customers: 0.546, loan: 188.40 },
    { name: '普惠小微', customers: 1.864, loan: 172.80 },
    { name: '先进制造业', customers: 0.428, loan: 132.60 }
  ]),
  rankings: demoRankings,
  attention: Object.freeze([
    {
      label: '重点项目落地协调', count: 4, owner: '公司业务部', deadline: '2026-09-15',
      detail: {
        description: '示例中有4项重点项目待确认落地路径，需明确授信、资金与产品协同安排。',
        coordination: '请公司业务部会同相关机构确认项目计划，并在截止日前反馈推进情况。',
        source: '重点项目协调清单（演示）'
      }
    },
    {
      label: '存款到期维护', count: 0, owner: null, deadline: null,
      detail: {
        description: '当前示例数量为0，责任部门与期限尚未提供；该数值不能解释为事项已完成。',
        coordination: '补齐责任部门、统计期间与维护期限后，再确认是否需要进一步协调。',
        source: '对公存款到期清单（演示）'
      }
    },
    {
      label: '风险客户跟进', count: 2, owner: '风险管理部', deadline: '2026-09-20',
      detail: {
        description: '示例中有2项风险客户跟进事项，需核对风险口径、责任机构和后续安排。',
        coordination: '请风险管理部与公司业务部确认口径及跟进计划，不以清单数量自动生成风险结论。',
        source: '对公风险跟进台账（演示）'
      }
    },
    {
      label: '负增机构推动', count: 1, owner: '公司业务部', deadline: '2026-09-18',
      detail: {
        description: '渭南市分行对公存款较上期净减4.20亿元，需核查客户资金变化和后续维护安排。',
        coordination: '请公司业务部会同渭南市分行核实负增原因，明确跟进计划；不以单月负增直接作绩效结论。',
        source: '对公机构存款监测清单（演示）'
      }
    }
  ]),
  targets: Object.freeze([
    { name: '年度对公存款净增', actual: 72.30, target: 60.00 },
    { name: '对公存款月日均较年初净增', actual: -3.60, target: 12.00 },
    { name: '年度对公营业收入', actual: 28.68, target: 36.00 },
    { name: '年度对公中间业务收入', actual: null, target: null },
    { name: '年度对公贷款净增', actual: 18.35, target: 25.00 }
  ]),
  institutions: demoInstitutions,
  issues: Object.freeze([])
});

export default corporateDemoModel;
