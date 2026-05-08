// 全部 PC 模块的 mock 数据。生产环境替换成 axios 真请求即可（见 @/api/http.js）

export const workspace = {
  greet: '下午好，张三',
  desc: '当前角色：客户经理 · 机构：南山支行 · 您今天有 5 项待办、3 条未读通知，今日工作时长还剩 4 小时。',
  stats: [
    { label: '待办任务',       value: 5,    trend: '较昨日 -2',       trendType: 'down' },
    { label: '未读通知',       value: 3,    trend: '较昨日 +1',       trendType: 'up' },
    { label: '本月 KPI 总分',  value: 86.4, trend: '↑ 较上月 +3.2',   trendType: 'up' },
    { label: '进行中触达任务', value: 7,    trend: '2 项预警，1 项超时', trendType: 'down' }
  ],
  todos: [
    { name: '资产投放申请-客户A',          node: '公司部审核',     sla: 'normal',  remain: '剩 8h' },
    { name: '中场支持-客户C·跨境结算',    node: '秘书派单',       sla: 'warn',    remain: '剩 2h' },
    { name: '线索审批-客户F',              node: '机构负责人审核', sla: 'overdue', remain: '已超 1h' },
    { name: '业绩调整申请-2026Q1',         node: '资财复核',       sla: 'normal',  remain: '剩 12h' },
    { name: '资产投放申请-客户G',          node: '授信审查',       sla: 'warn',    remain: '剩 3h' }
  ],
  notifications: [
    { read: false, title: '您有 1 笔资产投放申请待办', tag: '资产投放', time: '2026-04-23 09:20' },
    { read: false, title: '客户A 触达任务即将超期',    tag: '触达任务', time: '2026-04-23 08:00' },
    { read: true,  title: '系统将于 4/24 凌晨进行版本切换', tag: '系统通知', time: '2026-04-22 18:00' },
    { read: false, title: '您有 1 笔中场支持待派单',    tag: '中场支持', time: '2026-04-22 16:42' },
    { read: true,  title: '业绩调整申请已通过',        tag: '业绩调整', time: '2026-04-22 14:30' }
  ],
  shortcuts: [
    { icon: '🧾', label: '新建线索' },
    { icon: '🤝', label: '认领客户' },
    { icon: '📍', label: '我的触达' },
    { icon: '💼', label: '资产投放' },
    { icon: '🛠', label: '中场支持' },
    { icon: '📊', label: '指标查询' },
    { icon: '📦', label: '产品资料库' },
    { icon: '📇', label: '通讯录' }
  ]
};

// === 绩效与考核 ===
export const perfMetricsTree = [
  { id: 'biz', label: '业务指标', children: [
    { id: 'deposit', label: '存款类', children: [
      { id: 'M0001', label: '存款日均' },
      { id: 'M0002', label: '存款余额' }
    ]},
    { id: 'loan', label: '贷款类', children: [
      { id: 'M0003', label: '贷款余额' },
      { id: 'M0004', label: '贷款日均' },
      { id: 'M0005', label: '不良贷款率' }
    ]},
    { id: 'fee', label: '中收类' }
  ]},
  { id: 'cust', label: '客户指标', children: [
    { id: 'M1001', label: '新增有效客户数' },
    { id: 'M1002', label: '存量活跃客户数' }
  ]},
  { id: 'risk', label: '风险指标' }
];

export const perfMetricDetail = {
  M0002: {
    code: 'M0002', name: '存款余额', cate: '业务/存款类',
    type: 'SQL', dataSource: 'EDW · t_acct_balance', status: '已发布', version: 'v3',
    sql: `SELECT cust_id, org_id, AVG(daily_bal) WHERE acct_type=DEP
  AS metric_value
FROM t_acct_balance
WHERE dt BETWEEN #{period_start} AND #{period_end}
GROUP BY cust_id, org_id`,
    slots: [
      { name: 'period_start', type: 'DATE',     required: true,  desc: '起始日期' },
      { name: 'period_end',   type: 'DATE',     required: true,  desc: '截止日期' },
      { name: 'org_scope',    type: 'STRING[]', required: false, desc: '机构范围 · 缺省全行' }
    ],
    same: [
      { code: 'M0001', name: '存款日均', type: 'SQL', status: '已发布', version: 'v3', updated: '2026-04-01' },
      { code: 'M0002', name: '存款余额', type: 'SQL', status: '已发布', version: 'v2', updated: '2026-04-01' }
    ]
  }
};

export const perfKpiRules = [
  { code: 'KPI001', name: '客户经理-公司类',  scope: '南山/福田/罗湖/宝安', items: 6, status: '启用',   version: 'v4' },
  { code: 'KPI002', name: '客户经理-零售类',  scope: '全部支行',           items: 6, status: '启用',   version: 'v3' },
  { code: 'KPI003', name: '机构负责人',        scope: '全部支行',           items: 5, status: '启用',   version: 'v2' },
  { code: 'KPI004', name: '中场支持人员',      scope: '中场支持部',         items: 4, status: '试运行', version: 'v1' }
];

export const perfTargets = [
  { id: 1, subj: '员工张三', org: '南山支行', metric: '存款日均增量（万）',  origin: 8000,  current: 8000,  done: 6420, rate: 80.3, status: '已审批' },
  { id: 2, subj: '员工张三', org: '南山支行', metric: '贷款余额增量（万）',  origin: 12000, current: 13500, done: 9100, rate: 67.4, status: '修正中', adjusted: true },
  { id: 3, subj: '员工张三', org: '南山支行', metric: '新增有效客户数',       origin: 30,    current: 30,    done: 22,   rate: 73.3, status: '已审批' },
  { id: 4, subj: '员工李四', org: '福田支行', metric: '存款日均增量（万）',  origin: 6000,  current: 6000,  done: 5520, rate: 92,   status: '已审批' },
  { id: 5, subj: '员工李四', org: '福田支行', metric: '贷款余额增量（万）',  origin: 9000,  current: 9000,  done: 7800, rate: 86.7, status: '已审批' },
  { id: 6, subj: '员工孙七', org: '罗湖支行', metric: '存款日均增量（万）',  origin: 7000,  current: 7000,  done: 4280, rate: 61.1, status: '已审批' }
];

export const perfImports = [
  { id: 'IMP-20260422-001', type: '指标结果', file: 'indicator_2026Q2.xlsx', uploader: '资财·王主管', valid: 1248, total: 1250, status: '已完成', time: '2026-04-22 09:00' },
  { id: 'IMP-20260421-002', type: '目标值',  file: 'target_2026Q2.xlsx',    uploader: '资财·王主管', valid: 320,  total: 320,  status: '已完成', time: '2026-04-21 16:30' }
];

export const perfComputeStats = { tasks: 12, ok: 11, fail: 1, lastDuration: '8m22s' };
export const perfComputeBatches = [
  { batch: 'CALC-20260423-04', plan: '2026Q2 KPI', scope: '员工张三',   trigger: '回算', who: '系统',     start: '14:20', dur: '52s',    status: '成功' },
  { batch: 'CALC-20260423-01', plan: '2026Q2 KPI', scope: '全行',       trigger: '手动', who: '资财·王主管', start: '09:00', dur: '8m22s', status: '成功' },
  { batch: 'CALC-20260422-02', plan: '2026Q2 KPI', scope: '南山支行',   trigger: '定时', who: '系统',     start: '23:00', dur: '2m11s',  status: '成功' },
  { batch: 'CALC-20260422-01', plan: '2026Q2 KPI', scope: '全行',       trigger: '手动', who: '资财·王主管', start: '17:30', dur: '—',      status: '失败' }
];

// === 报表分析 ===
export const reportDynamic = {
  metrics: [
    { code: 'M0001', label: '存款日均' },
    { code: 'M0003', label: '贷款余额' },
    { code: 'M1001', label: '新增有效客户' },
    { code: 'M_FEE', label: '中收入' }
  ],
  subjects: ['员工张三', '员工李四', '员工孙七', '员工郑九', '员工赵六'],
  result: [
    { subject: '员工张三', M0001: 6420, M0003: 9100,  M1001: 22, M_FEE: 182 },
    { subject: '员工李四', M0001: 5520, M0003: 7800,  M1001: 18, M_FEE: 156 },
    { subject: '员工孙七', M0001: 4280, M0003: 11200, M1001: 15, M_FEE: 220 },
    { subject: '员工郑九', M0001: 3800, M0003: 5400,  M1001: 12, M_FEE: 98 },
    { subject: '员工赵六', M0001: 7100, M0003: 8300,  M1001: 25, M_FEE: 203 }
  ]
};

export const reportDashboard = {
  org: '深圳分行', date: '2026-04-22',
  stats: [
    { label: '存款日均',       value: 418.6, unit: '亿', trend: '↑ 较月初 +3.2%',   trendType: 'up' },
    { label: '贷款余额',       value: 312.4, unit: '亿', trend: '↑ 较月初 +2.1%',   trendType: 'up' },
    { label: '不良贷款率',     value: 1.42,  unit: '%',  trend: '↓ 较月初 -0.08%',  trendType: 'down' },
    { label: '中间业务收入',   value: 2840,  unit: '万', trend: '↑ 12.4%',          trendType: 'up' },
    { label: '本月新增有效客户', value: 428,  unit: '',   trend: '↑ 较月初 +18',     trendType: 'up' }
  ],
  // 滚动 12 个月（截至 data.date 当月）：2025-05 → 2026-04
  trend: {
    months: ['25-05','25-06','25-07','25-08','25-09','25-10','25-11','25-12','26-01','26-02','26-03','26-04'],
    deposit: [380, 386, 392, 398, 401, 405, 408, 410, 414, 416, 417, 418.6],
    loan:    [280, 285, 288, 292, 295, 298, 300, 303, 305, 308, 310, 312.4]
  },
  ranking: [
    { org: '南山支行', val: 86.4 },
    { org: '福田支行', val: 78.2 },
    { org: '罗湖支行', val: 65.8 },
    { org: '宝安支行', val: 58.4 },
    { org: '龙岗支行', val: 52.1 },
    { org: '前海支行', val: 41.2 }
  ]
};

export const reportPresets = [
  { code: 'TOUCH_MON',   title: '触达任务监控', desc: '日/周/月维度的任务统计与超期分析', visits: 24, route: '/report/preset/touch'   },
  { code: 'PERF_SUM',    title: '绩效汇总',     desc: 'KPI 总分、指标完成率、机构对比',    visits: 18, route: '/report/preset/perf'    },
  { code: 'CUST_POOL',   title: '客户池统计',   desc: '认领率、流转、池规模、机构分布',    visits: 12, route: '/report/preset/cust'    },
  { code: 'ASSET_STAT',  title: '资产投放统计', desc: '审批时效、通过率、节点耗时',        visits: 9,  route: '/report/preset/asset'   },
  { code: 'SUPPORT_STAT',title: '中场支持统计', desc: '场景 A/B、产品分布、办理时效',      visits: 6,  route: '/report/preset/support' },
  { code: 'LOAN_MON',    title: '存贷款月报',   desc: '存贷款余额、增量、机构排名',        visits: 30, route: '/report/preset/loan'    }
];

// 动态查询的"我的方案" —— 字段对齐后端 SavedQuerySummaryDTO（仅作 fallback；真接口可用时不会用到这里）
// metrics/subjects 仅在 mock 详情兜底场景有用，列表层只看 id/name/dim/createdTime/updatedTime。
export const reportSchemes = [
  { id: 's1', name: '示例-员工存贷款查询', dim: 'EMP',  metrics: ['M0001','M0003','M_FEE'], subjects: ['E001','E002'], createdTime: '2026-04-22T09:00:00', updatedTime: '2026-04-22T09:00:00' },
  { id: 's2', name: '示例-机构对比 Q2',     dim: 'ORG',  metrics: ['M0005','M0006'],         subjects: ['BJ_CY','SH_PD'], createdTime: '2026-04-15T09:00:00', updatedTime: '2026-04-15T09:00:00' },
  { id: 's3', name: '示例-客户营销月报',     dim: 'CUST', metrics: ['M0001','M0006'],         subjects: ['CM_C001','CM_C005'], createdTime: '2026-04-01T09:00:00', updatedTime: '2026-04-01T09:00:00' }
];

// 维度元数据 —— 后端 GET /api/reports/query-dimensions
export const reportDimensions = [
  { code: 'EMP',  label: '员工' },
  { code: 'ORG',  label: '机构' },
  { code: 'CUST', label: '客户' }
];

// SQL 探查 —— 表白名单 / 历史 / 默认结果
export const reportSqlWhitelist = [
  'mart_cust_deposit_daily',
  'mart_loan_balance_daily',
  'mart_kpi_result',
  'dim_customer',
  'dim_org',
  'dim_employee',
  'dim_product'
];
export const reportSqlHistory = [
  {
    id: 'SQL-20260422-04', who: '员工张三', reason: '排查存款数据',
    sql: `SELECT cust_name, industry, SUM(amount) AS deposit_inc
FROM mart_cust_deposit_daily d
JOIN dim_customer c ON d.cust_id = c.id
WHERE d.org_id = '0001' AND d.biz_date >= '2026-04-16'
GROUP BY cust_name, industry
ORDER BY deposit_inc DESC`,
    rows: 4, dur: '421ms', traceId: '7af3..2e91', time: '2026-04-22 14:32'
  },
  {
    id: 'SQL-20260421-09', who: '资财·王主管', reason: '机构对比',
    sql: `SELECT org_id, AVG(daily_bal) AS avg_loan
FROM mart_loan_balance_daily
WHERE biz_date BETWEEN '2026-03-01' AND '2026-03-31'
GROUP BY org_id
ORDER BY avg_loan DESC`,
    rows: 12, dur: '612ms', traceId: '5e80..1a04', time: '2026-04-21 17:08'
  },
  {
    id: 'SQL-20260420-12', who: '员工李四', reason: '抽检 KPI',
    sql: `SELECT cust_id, kpi_score
FROM mart_kpi_result
WHERE org_id = '0002' AND period = '2026Q1'
ORDER BY kpi_score DESC`,
    rows: 38, dur: '230ms', traceId: '8fe2..6b21', time: '2026-04-20 10:45'
  }
];
export const reportSqlProbeResult = {
  rows: 4,
  time: '421ms',
  traceId: '7af3..2e91',
  columns: ['cust_name', 'industry', 'deposit_inc'],
  data: [
    { cust_name: '客户A（科技股份）', industry: '软件', deposit_inc: 28400000 },
    { cust_name: '客户G（建筑工程）', industry: '建筑', deposit_inc: 22100000 },
    { cust_name: '客户E（医药公司）', industry: '医药', deposit_inc: 18600000 },
    { cust_name: '客户N（重点客户）', industry: '能源', deposit_inc: 16400000 }
  ]
};

// 指标库扁平列表（用于搜索）—— `/api/perf/metrics`
export const metricsFlat = [
  { code: 'M0001', label: '存款日均',     cate: '业务/存款类', type: 'SQL',    status: '已发布' },
  { code: 'M0002', label: '存款余额',     cate: '业务/存款类', type: 'SQL',    status: '已发布' },
  { code: 'M0003', label: '贷款余额',     cate: '业务/贷款类', type: 'SQL',    status: '已发布' },
  { code: 'M0004', label: '贷款日均',     cate: '业务/贷款类', type: 'SQL',    status: '已发布' },
  { code: 'M0005', label: '不良贷款率',   cate: '业务/贷款类', type: 'GROOVY', status: '已发布' },
  { code: 'M0006', label: '新增有效客户数', cate: '客户指标',  type: 'SQL',    status: '已发布' },
  { code: 'M0007', label: '中收入',         cate: '业务/中收类', type: 'SQL',    status: '已发布' },
  { code: 'M0008', label: '存量活跃客户数', cate: '客户指标',  type: 'SQL',    status: '已发布' }
];

// 员工列表 —— `/api/employees`
export const employeesList = [
  { id: 'E001', name: '张三', org: '南山支行', orgCode: '0001', role: '客户经理',     status: '在职' },
  { id: 'E002', name: '李四', org: '福田支行', orgCode: '0002', role: '客户经理',     status: '在职' },
  { id: 'E003', name: '孙七', org: '罗湖支行', orgCode: '0003', role: '客户经理',     status: '在职' },
  { id: 'E004', name: '郑九', org: '宝安支行', orgCode: '0004', role: '客户经理',     status: '在职' },
  { id: 'E005', name: '赵六', org: '龙岗支行', orgCode: '0005', role: '客户经理',     status: '在职' },
  { id: 'E006', name: '吴八', org: '前海支行', orgCode: '0006', role: '机构负责人',   status: '在职' },
  { id: 'E007', name: '王二', org: '南山支行', orgCode: '0001', role: '客户经理',     status: '在职' },
  { id: 'E008', name: '刘一', org: '福田支行', orgCode: '0002', role: '机构负责人',   status: '在职' }
];

// 客户列表 —— `/api/customers`（动态查询 dim=CUST 时使用）
export const customersList = [
  { id: 'C001', name: '客户A（科技股份）', org: '南山支行', industry: '软件' },
  { id: 'C002', name: '客户B（智能制造）', org: '福田支行', industry: '装备制造' },
  { id: 'C003', name: '客户C（跨境电商）', org: '罗湖支行', industry: '批发零售' },
  { id: 'C004', name: '客户E（医药公司）', org: '宝安支行', industry: '医药' },
  { id: 'C005', name: '客户G（建筑工程）', org: '龙岗支行', industry: '建筑' },
  { id: 'C006', name: '客户N（重点客户）', org: '前海支行', industry: '能源' }
];

// 机构树 —— `/api/orgs/tree`
export const orgsTree = [
  { code: '0000', name: '深圳分行', children: [
    { code: '0001', name: '南山支行' },
    { code: '0002', name: '福田支行' },
    { code: '0003', name: '罗湖支行' },
    { code: '0004', name: '宝安支行' },
    { code: '0005', name: '龙岗支行' },
    { code: '0006', name: '前海支行' }
  ]}
];

// === 系统设置 ===
export const sysRoles = [
  { id: 'CM',     name: '客户经理',    count: 245 },
  { id: 'OG',     name: '机构负责人',  count: 32 },
  { id: 'COMP',   name: '公司部',      count: 18 },
  { id: 'FIN',    name: '资财',        count: 8 },
  { id: 'TECH',   name: '科技部',      count: 12 },
  { id: 'SUP_S',  name: '中场支持秘书', count: 4 },
  { id: 'SUP',    name: '中场支持人员', count: 28 },
  { id: 'CRED',   name: '授信审查',    count: 14 },
  { id: 'PRES',   name: '分行行长',    count: 6 }
];

export const sysResources = [
  { id: 'workspace', label: '工作台', kind: 'group', children: [
    { id: 'workspace.overview', label: '概览', r: true, w: false },
    { id: 'workspace.todo',     label: '待办', r: true, w: false }
  ]},
  { id: 'cust', label: '客户营销', kind: 'group', children: [
    { id: 'cust.lead.add',     label: '线索·新建', r: true, w: true },
    { id: 'cust.lead.audit',   label: '线索·审批', r: false, w: false },
    { id: 'cust.list',         label: '客户列表', r: true, w: false },
    { id: 'cust.pool',         label: '待认领池', r: true, w: false }
  ]},
  { id: 'biz', label: '业务执行', kind: 'group', children: [
    { id: 'biz.asset.start', label: '资产投放·发起',       r: true, w: true },
    { id: 'biz.asset.audit', label: '资产投放·机构审批',   r: false, w: false },
    { id: 'biz.support',     label: '中场支持',             r: true, w: true }
  ]},
  { id: 'rpt', label: '报表', kind: 'group', children: [
    { id: 'rpt.dyn',  label: '动态查询',  r: true,  w: false },
    { id: 'rpt.sql',  label: 'SQL 探查',  r: false, w: false },
    { id: 'rpt.dash', label: '行长仪表盘', r: false, w: false }
  ]}
];

export const sysScopeMatrix = [
  { biz: 'LEAD',     scope: '本人',   reason: '默认' },
  { biz: 'CUST',     scope: '本人',   reason: '默认' },
  { biz: 'TOUCH',    scope: '本人',   reason: '默认' },
  { biz: 'ASSET',    scope: '本机构', reason: '2026-03-01 申请' },
  { biz: 'SUPPORT',  scope: '本人',   reason: '默认' },
  { biz: 'RPT_DYN',  scope: '本机构', reason: '2026-04-10 调整' },
  { biz: 'RPT_PRES', scope: '无权限', reason: '—' },
  { biz: 'SQL',      scope: '无权限', reason: '—' },
  { biz: 'PERM',     scope: '无权限', reason: '—' }
];

export const sysDictTypes = [
  { code: 'IND', label: '行业分类' },
  { code: 'CUS', label: '客户类型' },
  { code: 'ENT', label: '企业性质' },
  { code: 'GRP', label: '集团归属' },
  { code: 'GUA', label: '担保方式' },
  { code: 'BIZ', label: '业务类型' },
  { code: 'PRJ', label: '项目类型' },
  { code: 'BizType',   label: 'BizType' },
  { code: 'DataScope', label: 'DataScope' },
  { code: 'NTF', label: '通知类型' }
];
export const sysDictItems = {
  IND: [
    { code: 'IND001', label: '软件',       value: 'SOFTWARE',  sort: 1, status: '启用' },
    { code: 'IND002', label: '装备制造',   value: 'EQUIPMENT', sort: 2, status: '启用' },
    { code: 'IND003', label: '批发零售',   value: 'WHOLESALE', sort: 3, status: '启用' },
    { code: 'IND004', label: '医药',       value: 'PHARMA',    sort: 4, status: '启用' },
    { code: 'IND005', label: '建筑',       value: 'CONSTRUCT', sort: 5, status: '启用' },
    { code: 'IND006', label: '互联网',     value: 'INTERNET',  sort: 6, status: '启用' },
    { code: 'IND099', label: 'X-未识别',   value: 'UNKNOWN',   sort: 99,status: '禁用' }
  ]
};

export const sysJobs = [
  { code: 'JOB001', name: '日终批量·指标计算', cron: '0 0 1 * * ?',     status: '运行中', manual: true,  last: '2026-04-23 01:00', next: '2026-04-24 01:00', risk: '普通' },
  { code: 'JOB002', name: '触达任务·SLA 检查',  cron: '0 */15 * * * ?',  status: '运行中', manual: true,  last: '2026-04-23 09:15', next: '2026-04-23 09:30', risk: '普通' },
  { code: 'JOB003', name: '通知·汇总推送',      cron: '0 0 9 * * MON',   status: '运行中', manual: false, last: '2026-04-22 09:00', next: '2026-04-29 09:00', risk: '普通' },
  { code: 'JOB004', name: '数据归档',           cron: '0 0 2 1 * ?',     status: '已暂停', manual: true,  last: '2026-03-01 02:00', next: '—',                risk: '高危' },
  { code: 'JOB005', name: 'KPI·历史回算',       cron: '手动触发',         status: '运行中', manual: true,  last: '2026-04-22 17:30', next: '—',                risk: '高危' }
];

export const sysAuditLogs = [
  { traceId: '7af3..2e91', who: '资财·王主管', action: '导入',     bizType: 'KPI 结果', resource: 'IMP-20260422-001',     reason: '月度数据导入', dur: '12.4s',  time: '2026-04-22 09:00' },
  { traceId: '9bc2..8f01', who: '科技·张工',   action: '权限变更', bizType: '权限管理', resource: 'ROLE_CM',              reason: '新增报表查看权限', dur: '0.4s', time: '2026-04-22 11:20' },
  { traceId: '3df1..4c22', who: '员工张三',     action: 'SQL 探查', bizType: 'SQL',     resource: 'mart_cust_deposit_daily', reason: '排查存款数据', dur: '421ms', time: '2026-04-22 14:32' },
  { traceId: '5e80..1a04', who: '机构·王行长', action: '删除',     bizType: '线索',     resource: 'L20260418002',          reason: '客户业务取消', dur: '0.2s',   time: '2026-04-22 15:00' },
  { traceId: '8fe2..6b21', who: '员工李四',     action: '导出',     bizType: '触达任务', resource: 'TouchExport_20260420',  reason: '月度统计', dur: '2.1s',     time: '2026-04-22 16:45' }
];

export const sysNotifications = [
  { read: false, title: '您有 1 笔资产投放申请待办', type: 'WORKFLOW', biz: '资产投放', no: 'ASSET-2026-1042', time: '2026-04-23 09:20' },
  { read: false, title: '客户A 触达任务即将超期',   type: 'BUSINESS', biz: '触达任务', no: 'TOUCH-2026-2188', time: '2026-04-23 08:00' },
  { read: true,  title: '系统将于 4/24 凌晨进行版本切换', type: 'SYSTEM', biz: '系统通知', no: '-',          time: '2026-04-22 18:00' },
  { read: false, title: '您有 1 笔中场支持待派单',   type: 'WORKFLOW', biz: '中场支持', no: 'SUP-2026-0309',  time: '2026-04-22 16:42' },
  { read: true,  title: '业绩调整申请已通过',         type: 'BUSINESS', biz: '业绩调整', no: 'ADJ-2026-022',   time: '2026-04-22 14:30' }
];

export const sysConfig = [
  { key: 'touch.first.sla.hours',   type: 'INT',  value: '24',   desc: '首次触达 SLA（小时）',  by: '科技·张工',   at: '2026-03-01 10:00' },
  { key: 'touch.repeat.sla.hours',  type: 'INT',  value: '48',   desc: '重新触达 SLA',           by: '科技·张工',   at: '2026-03-01 10:00' },
  { key: 'touch.cancel.freeze.days',type: 'INT',  value: '7',    desc: '触达取消后冻结天数',     by: '科技·张工',   at: '2026-03-01 10:00' },
  { key: 'claim.parallel.enabled',  type: 'BOOL', value: 'true', desc: '允许并行认领',           by: '科技·张工',   at: '2026-04-01 09:00' },
  { key: 'sql.row.limit',           type: 'INT',  value: '1000', desc: 'SQL 探查最大行数',       by: '科技·张工',   at: '2026-04-10 14:30' },
  { key: 'kpi.auto.recalc',         type: 'BOOL', value: 'true', desc: '目标修正自动回算',       by: '资财·王主管', at: '2026-04-15 11:00' },
  { key: 'notify.batch.size',       type: 'INT',  value: '500',  desc: '通知批量推送大小',       by: '科技·张工',   at: '2026-02-20 16:00' }
];

export const sysFiles = [
  { name: '客户A_营业执照.pdf',    type: 'PDF',  size: '124 KB', biz: '资产投放', no: 'A20261042',           by: '员工张三', time: '2026-04-22 09:30' },
  { name: '客户A_财务报表.xlsx',   type: 'XLSX', size: '380 KB', biz: '资产投放', no: 'A20261042',           by: '员工张三', time: '2026-04-22 09:32' },
  { name: '触达照片_TK20260023.jpg', type: 'JPG', size: '2.1 MB', biz: '触达任务', no: 'TK20260023',          by: '员工张三', time: '2026-04-23 10:35' },
  { name: 'leads_2026Q2.xlsx',     type: 'XLSX', size: '54 KB',  biz: '线索批量导入', no: 'BATCH-20260423-091420', by: '员工张三', time: '2026-04-23 09:14' },
  { name: 'indicator_2026Q2.xlsx', type: 'XLSX', size: '1.2 MB', biz: 'KPI 数据导入',  no: 'IMP-20260422-001',     by: '资财·王主管', time: '2026-04-22 09:00' }
];
