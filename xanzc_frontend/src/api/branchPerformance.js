import http, { API_BASE } from './http';

// 支行排名必须使用完整真实查询，禁止 GET fallback 或全局开发 mock 补零。
export function listBranchKpiSchemes(params = {}) {
  return http.get(`${API_BASE}/perf/kpi-schemes`, { params: { status: 'ACTIVE', ...params }, silent: true, timeout: 60000 });
}

export function listBranchKpiResults(params) {
  return http.get(`${API_BASE}/perf/kpi-score/results`, { params, silent: true, timeout: 60000 });
}
