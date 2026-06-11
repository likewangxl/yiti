// 前端兜底占位数据：全部置空。
// 原则：后端不返回数据 = 空。任何接口失败/无数据时，前端只展示「空」，绝不再显示假数据。
// 这里保留各导出的「形态/键名」但内容为空，供 http.js call() 的兜底与少数组件初始 state 使用，
// 不会因缺键导致越界崩溃。需要本地无后端联调时，请走真实后端，不要再往这里塞假数据。

// === 工作台 ===
export const workspace = {
  greet: '',
  desc: '',
  stats: [],
  todos: [],
  notifications: [],
  shortcuts: []
};

// === 绩效与考核 ===
export const perfMetricsTree = [];
export const perfMetricDetail = {};
export const perfKpiRules = [];
export const perfTargets = [];
export const perfImports = [];
export const perfComputeStats = { tasks: 0, ok: 0, fail: 0, lastDuration: '' };
export const perfComputeBatches = [];

// === 报表分析 ===
export const reportDynamic = { metrics: [], subjects: [], result: [] };
export const reportDashboard = {
  org: '', date: '',
  stats: [],
  trend: { months: [], deposit: [], loan: [] },
  ranking: []
};
export const reportPresets = [];
export const reportSchemes = [];
export const reportDimensions = [];
export const reportSqlWhitelist = [];
export const reportSqlHistory = [];
export const reportSqlProbeResult = { rows: 0, time: '', traceId: '', columns: [], data: [] };

// 指标库扁平列表
export const metricsFlat = [];
// 员工 / 客户 / 机构树
export const employeesList = [];
export const customersList = [];
export const orgsTree = [];

// === 系统设置 ===
export const sysRoles = [];
export const sysResources = [];
export const sysScopeMatrix = [];
export const sysDictTypes = [];
export const sysDictItems = {};
export const sysJobs = [];
export const sysAuditLogs = [];
export const sysNotifications = [];
export const sysConfig = [];
export const sysFiles = [];
