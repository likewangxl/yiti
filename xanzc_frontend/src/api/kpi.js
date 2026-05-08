// KPI 规则 API —— 对接 yiti `/api/perf/kpi-schemes`（KpiSchemeController）
import { call } from './http';
import { perfKpiRules } from '@/mock';

export function listKpiSchemes(params = {}) {
  return call('get', '/perf/kpi-schemes', { params }, perfKpiRules);
}

export function getKpiScheme(id) {
  return call('get', `/perf/kpi-schemes/${id}`, {}, perfKpiRules[0] || null);
}
