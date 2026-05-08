// 报表分析 API —— 全部对接 yiti `/api/reports/...`
//
// 后端控制器分布（来自 yiti/report-analytics-center/...controller/）：
//   - DynamicQueryController            POST /api/reports/dynamic-query
//   - DynamicQueryExportController      POST /api/reports/dynamic-query/export
//   - SavedQueryController              GET/POST/PUT/DELETE /api/reports/saved-queries[/{id}]
//   - DashboardController               GET /api/reports/dashboard/{president|org/{code}|emp/{id}}
//   - RptSqlProbeController             POST /api/reports/sql-probe/execute
//                                       GET  /api/reports/sql-probe/history[/{id}]
//                                       GET  /api/reports/sql-probe/schema-whitelist
//   - MetaController                    GET  /api/reports/query-dimensions
//   - RptExportController               GET  /api/reports/export-tasks/{id}[/download]
//
// 所有调用走 http.js 的 call(method, path, config, fallback)，后端不可用时使用 mock 兜底。
import { call } from './http';
import {
  reportDynamic, reportDashboard, reportPresets, reportSchemes,
  reportDimensions, reportSqlWhitelist, reportSqlHistory, reportSqlProbeResult
} from '@/mock';

// ===== 维度元数据 =====
//
// yiti MetaController：GET /api/reports/query-dimensions?dim={EMP|ORG|CUST}
//   - dim 为后端 @RequestParam @NotBlank，不传会 400 (VALID_002)
//   - 返回 QueryDimensionRespDTO：{ dim, dimName, metrics:[{ groupCode, groupName, children:[...] }] }
//     —— 形态是「单维度的指标分组树」，**不是** dim 列表（[EMP/ORG/CUST] 在前端硬编码）
//
// mock fallback 仍是 [{code,label}, ...] 数组（历史形态），调用方需自己识别响应类型。
export function getQueryDimensions(dim) {
  return call('get', '/reports/query-dimensions', { params: { dim } }, reportDimensions);
}

// ===== 动态查询 =====
//
// 后端 DynamicQueryReqDTO:{ dim, subjectIds:[id...], metricCodes:[code...], dataDate:"2026-04-22" }
// 后端 DynamicQueryRespDTO:{ dim, dataDate, columns:[{metricCode,metricName,unit}],
//                             rows:[{ subjectId, subjectName, [metricCode]:value, ... }], rowCount }
//
// view 习惯使用 row.subject 渲染对象列(table 的 prop="subject"),这里把 subjectName 派生成 subject。
// mock fallback 形态:{ metrics, subjects, result:[{ subject, [code]:value }] } 保持不变。
export async function queryDynamic(payload) {
  const body = {
    dim: payload.dim,
    subjectIds:  (payload.subjects || []).map(s => (typeof s === 'string' ? s : s?.id)).filter(Boolean),
    metricCodes: (payload.metrics  || []).map(m => (typeof m === 'string' ? m : m?.code)).filter(Boolean),
    dataDate:    payload.date  // 后端 LocalDate 接受 ISO 字符串
  };
  const r = await call('post', '/reports/dynamic-query', { data: body }, reportDynamic);
  if (Array.isArray(r?.rows)) {
    r.rows = r.rows.map(row => {
      // 后端 subjectName -> view 期望的 subject;保留 subjectId 备用
      const out = { ...row };
      if (out.subject == null && out.subjectName != null) out.subject = out.subjectName;
      return out;
    });
  }
  return r;
}
export function exportDynamic(payload) {
  const body = {
    dim: payload.dim,
    subjectIds:  (payload.subjects || []).map(s => (typeof s === 'string' ? s : s?.id)).filter(Boolean),
    metricCodes: (payload.metrics  || []).map(m => (typeof m === 'string' ? m : m?.code)).filter(Boolean),
    dataDate:    payload.date
  };
  return call('post', '/reports/dynamic-query/export', { data: body }, () => ({ taskId: 'EXP-MOCK-' + Date.now() }));
}

// ===== 保存方案（动态查询的"我的方案"）=====
//
// 后端契约（SavedQuerySaveReqDTO / SavedQuerySummaryDTO / SavedQueryDetailRespDTO）：
//   保存入参   { name, dim, subjectIds: '["E001",...]'(JSON字符串), metricCodes: '["M0001",...]' }
//   列表项     { id, name, dim, createdTime, updatedTime }
//   详情       { id, name, dim, subjectIds, metricCodes, version, createdTime, updatedTime }
//
// 前端 view 用的友好形态：metrics/subjects 是数组。这里在 api 层做双向适配。

/** 列表 —— 透传后端摘要 DTO（mock fallback 同结构）。 */
export function listSavedQueries(params = {}) {
  return call('get', '/reports/saved-queries', { params }, reportSchemes);
}

/** 详情 —— 把 subjectIds / metricCodes JSON 字符串解成数组，并派生 view 友好字段 subjects/metrics。 */
export async function getSavedQuery(id) {
  const r = await call('get', `/reports/saved-queries/${id}`, {},
    () => reportSchemes.find(s => s.id === id) || reportSchemes[0]);
  if (!r) return r;
  // 真接口 → JSON 字符串；mock fallback → 可能本身就是数组
  if (typeof r.metricCodes === 'string') {
    try { r.metrics = JSON.parse(r.metricCodes); } catch { r.metrics = []; }
  } else if (Array.isArray(r.metrics)) {
    // mock 兜底已是数组
  } else {
    r.metrics = [];
  }
  if (typeof r.subjectIds === 'string') {
    try { r.subjects = JSON.parse(r.subjectIds); } catch { r.subjects = []; }
  } else if (Array.isArray(r.subjects)) {
    // mock 兜底已是数组
  } else {
    r.subjects = [];
  }
  return r;
}

/**
 * 保存方案。
 * @param payload {{ name:string, dim:string, metrics:string[], subjects:Array<string|{id:string}> }}
 * 内部把 metrics → metricCodes JSON 字符串、subjects → subjectIds JSON 字符串。
 */
export function saveQuery(payload) {
  const subjectIds  = (payload.subjects || []).map(s => (typeof s === 'string' ? s : s?.id)).filter(Boolean);
  const metricCodes = (payload.metrics  || []).map(m => (typeof m === 'string' ? m : m?.code)).filter(Boolean);
  const body = {
    name: payload.name,
    dim:  payload.dim,
    subjectIds:  JSON.stringify(subjectIds),
    metricCodes: JSON.stringify(metricCodes)
  };
  return call('post', '/reports/saved-queries', { data: body },
    () => ({ id: 'SQ-MOCK-' + Date.now(), name: body.name, dim: body.dim, subjectIds: body.subjectIds, metricCodes: body.metricCodes }));
}

/** 更新方案（部分字段）。乐观锁 version 由 getSavedQuery 取出后回带。 */
export function updateSavedQuery(id, payload) {
  const body = {};
  if (payload.name != null) body.name = payload.name;
  if (payload.dim  != null) body.dim  = payload.dim;
  if (Array.isArray(payload.subjects)) {
    body.subjectIds = JSON.stringify(payload.subjects.map(s => (typeof s === 'string' ? s : s?.id)).filter(Boolean));
  }
  if (Array.isArray(payload.metrics)) {
    body.metricCodes = JSON.stringify(payload.metrics.map(m => (typeof m === 'string' ? m : m?.code)).filter(Boolean));
  }
  if (payload.version != null) body.version = payload.version;
  return call('put', `/reports/saved-queries/${id}`, { data: body }, () => ({ id, ...payload }));
}

export function deleteSavedQuery(id) {
  return call('delete', `/reports/saved-queries/${id}`, {}, () => ({ ok: true }));
}

// ===== 行长仪表盘 =====
// 后端 DashboardController 入参约定为 ?dataDate=YYYY-MM-DD（V1.14 # 2 后），
// 前端历史调用方仍传 { date }（看 Dashboard.vue / getDashboard 兼容入口），
// 这里做一次性映射 date → dataDate，避免每个调用点重复改造。
function mapDashboardParams(params = {}) {
  const { date, dataDate, ...rest } = params || {};
  const finalDate = dataDate ?? date;
  return finalDate != null ? { ...rest, dataDate: finalDate } : rest;
}

// 后端 PresidentDashboardRespDTO 的字段名与前端 mock / Dashboard.vue 模板的字段名不一致，
// 这里把后端响应翻译成前端期望的同形格式：
//   后端 dataDate                    → 前端 date
//   后端 depositTrend / loanTrend   → 前端 trend.{months, deposit, loan}
//   后端 orgRanking[]                → 前端 ranking[]{org, val, color}
//   后端无 org 字段                  → 前端 org 给占位文案
// stats 数组（V1.14 # 2 已对齐）原样透传。
// 命中条件：响应是后端真接口（不含 mock 标志 trend.months）才适配。
function adaptDashboardResp(r) {
  if (!r || typeof r !== 'object') return r;
  // mock 数据本身已是前端格式（trend.months 数组），直接返回不适配
  if (r.trend && Array.isArray(r.trend.months)) return r;
  // 后端真实响应：把 DTO 字段映射成前端约定字段
  return {
    org: r.org || '总行',
    date: r.dataDate || r.date || '',
    stats: Array.isArray(r.stats) ? r.stats : [],
    trend: {
      months: r.depositTrend?.xaxis || r.depositTrend?.xAxis || [],
      deposit: r.depositTrend?.series?.[0]?.data || [],
      loan: r.loanTrend?.series?.[0]?.data || []
    },
    ranking: (r.orgRanking || []).map((o, i) => ({
      org: o.orgName || o.orgId || o.orgCode || '',
      val: Number(o.actual ?? o.val ?? 0),
      color: i < 2 ? 'g' : i < 4 ? 'y' : 'r'
    })),
    // 透传后端原字段，方便其他组件按需消费（含 V1.14 # 2 stats 元数据）
    summaryMetrics: r.summaryMetrics,
    topCustomers: r.topCustomers,
    dataVersion: r.dataVersion
  };
}

export function getDashboardPresident(params = {}) {
  return call('get', '/reports/dashboard/president',
    { params: mapDashboardParams(params) }, reportDashboard).then(adaptDashboardResp);
}
export function getDashboardByOrg(orgCode, params = {}) {
  return call('get', `/reports/dashboard/org/${orgCode}`,
    { params: mapDashboardParams(params) }, reportDashboard).then(adaptDashboardResp);
}
export function getDashboardByEmp(empId, params = {}) {
  return call('get', `/reports/dashboard/emp/${empId}`,
    { params: mapDashboardParams(params) }, reportDashboard).then(adaptDashboardResp);
}
// 兼容老调用（Dashboard.vue 旧版本）
export function getDashboard(orgCode = '0001', date) {
  return getDashboardPresident({ orgCode, date });
}
// 行长仪表盘 —— PDF 导出（异步任务）
export function exportDashboardPdf(payload) {
  return call('post', '/reports/dashboard/president/export', { data: payload },
    () => ({ taskId: 'PDF-MOCK-' + Date.now(), status: 'PENDING' }));
}

// ===== 预置报表 =====
export function listPresets() {
  return call('get', '/reports/presets', {}, reportPresets);
}

// ===== SQL 探查 =====
export function executeSqlProbe(payload) {
  return call('post', '/reports/sql-probe/execute', { data: payload }, reportSqlProbeResult);
}
export function getSqlWhitelist() {
  return call('get', '/reports/sql-probe/schema-whitelist', {}, reportSqlWhitelist);
}
export function getSqlHistory(params = {}) {
  return call('get', '/reports/sql-probe/history', { params }, reportSqlHistory);
}
export function getSqlHistoryItem(id) {
  return call('get', `/reports/sql-probe/history/${id}`, {}, () => reportSqlHistory.find(h => h.id === id) || reportSqlHistory[0]);
}

// ===== 导出任务 =====
export function getExportTask(taskId) {
  return call('get', `/reports/export-tasks/${taskId}`, {}, () => ({ taskId, status: 'DONE', url: '#' }));
}
