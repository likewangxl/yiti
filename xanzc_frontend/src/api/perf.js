import { call } from './http';
import {
  perfMetricsTree, perfMetricDetail, perfKpiRules,
  perfTargets, perfImports, perfComputeStats, perfComputeBatches
} from '@/mock';

// === 指标库 ===
export function getMetricsTree() {
  return call('get', '/perf/metric-def/tree', {}, perfMetricsTree);
}
export function getMetricDetail(code) {
  return call('get', `/perf/metric-def/${code}`, {}, () => perfMetricDetail[code] || perfMetricDetail.M0002);
}

// === KPI 规则 ===
export function listKpiRules(params = {}) {
  return call('get', '/perf/kpi-scheme', { params }, perfKpiRules);
}

// === 目标管理 ===
export function listTargets(params = {}) {
  return call('get', '/perf/target-plan', { params }, perfTargets);
}

// === 数据导入 ===
export function listImports(params = {}) {
  return call('get', '/perf/import-batch', { params }, perfImports);
}
export function uploadImportFile(formData) {
  return call('post', '/perf/import-batch', { data: formData, headers: { 'Content-Type': 'multipart/form-data' } }, { id: 'IMP-MOCK-' + Date.now() });
}

// === 业绩调整 ===
export function submitAdjust(payload) {
  return call('post', '/perf/alloc-adjust', { data: payload }, { id: 'ADJ-MOCK-' + Date.now() });
}

// === 考核计算 ===
export function getComputeStats() {
  return call('get', '/perf/run-task/stats', {}, perfComputeStats);
}
export function listComputeBatches(params = {}) {
  return call('get', '/perf/run-task', { params }, perfComputeBatches);
}
export function triggerCompute(payload) {
  return call('post', '/perf/run-task/trigger', { data: payload }, { batch: 'CALC-MOCK-' + Date.now() });
}
