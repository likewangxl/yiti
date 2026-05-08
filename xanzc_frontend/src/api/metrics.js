// 指标库 API —— 对接 yiti `/api/perf/metrics`（MetricDefController）
// 提供动态指标查询页面用到的"指标树/指标列表/指标详情"。
//
// 重要：yiti 后端 `/api/perf/metrics` 是分页扁平接口，不支持 ?tree=true。
// 前端通过 status=ACTIVE 拉到当前所有"已发布"指标，然后客户端按 baseDim + metricLevel 构建二层树。
import { call } from './http';
import { perfMetricsTree, perfMetricDetail, metricsFlat } from '@/mock';

// 维度根节点（与 reportDimensions 对齐）
const DIM_LABELS = { EMP: '员工指标', ORG: '机构指标', CUST: '客户指标' };
// metric_level 子分组
const LEVEL_LABELS = { 1: '一级（基础）', 2: '二级（复合）', 3: '三级（汇总）' };

/**
 * 把 yiti 返回的扁平 records（MetricDefRespDTO[]）转成 MetricPicker 期望的 tree：
 *   [{ id, label, children: [{ id, label, children: [{ id: metricCode, label }] }] }]
 * 顶层按 baseDim 分组（员工/机构/客户）；二层按 metricLevel；叶子是单个指标。
 * 同维度同层级若指标少于 2 条，则二层折叠掉（不给 metricLevel 单独建子节点，避免出现"一级 → 1 个指标"的浪费层级）。
 */
function buildMetricTree(records) {
  const byDim = new Map();
  for (const m of records || []) {
    const dim = m.baseDim || 'OTHER';
    if (!byDim.has(dim)) byDim.set(dim, new Map());
    const byLevel = byDim.get(dim);
    const lv = m.metricLevel ?? 0;
    if (!byLevel.has(lv)) byLevel.set(lv, []);
    byLevel.get(lv).push({
      // 叶子：以 metricCode 作为 tree node-key（MetricPicker 用它作 picked 的 code）
      id: m.metricCode,
      label: m.metricName || m.metricCode,
      code: m.metricCode,
      // 把后端字段透传一份，方便后续详情面板用
      raw: m
    });
  }
  const tree = [];
  for (const [dim, byLevel] of byDim) {
    const dimNode = {
      id: `DIM_${dim}`,
      label: DIM_LABELS[dim] || dim,
      children: []
    };
    const allLeaves = [];
    for (const [lv, leaves] of [...byLevel].sort((a, b) => a[0] - b[0])) {
      // 按 metricCode 升序，UI 稳定
      leaves.sort((a, b) => (a.code || '').localeCompare(b.code || ''));
      allLeaves.push({ lv, leaves });
    }
    // 同维度只有一种 level → 折叠 level 子分组
    if (allLeaves.length === 1) {
      dimNode.children = allLeaves[0].leaves;
    } else {
      for (const { lv, leaves } of allLeaves) {
        dimNode.children.push({
          id: `DIM_${dim}_LV${lv}`,
          label: LEVEL_LABELS[lv] || `层级 ${lv}`,
          children: leaves
        });
      }
    }
    tree.push(dimNode);
  }
  return tree;
}

/**
 * 指标树（动态指标查询使用）：
 *   1) 调真接口 GET /api/perf/metrics?status=ACTIVE&pageSize=100
 *      （后端 MetricDefController 限定 @Max(100)；指标库通常 ≤ 100 条，足够覆盖）
 *   2) 拿 PageResult.records 后客户端 buildTree
 *   3) 后端不可用 / 返回空时回退 mock perfMetricsTree
 */
export async function getMetricsTree() {
  const page = await call(
    'get',
    '/perf/metrics',
    { params: { status: 'ACTIVE', pageNo: 1, pageSize: 100 } },
    null   // 不给 fallback：拿不到时直接 reject 让我们 catch 后返回 mock tree
  ).catch(() => null);

  // PageResult 形如 { records, total, pageNo, pageSize } —— 也兼容直接返回数组的环境
  const records = Array.isArray(page) ? page : page?.records;
  if (Array.isArray(records) && records.length) {
    return buildMetricTree(records);
  }
  // 兜底：mock（开发期 yiti 未启动 / VITE_USE_MOCK=true）
  return perfMetricsTree;
}

// GET /api/perf/metrics —— 扁平列表（用于搜索 / 全量浏览）
// 透传后端 PageResult；mock 兜底返回 metricsFlat（也是扁平结构）
export async function listMetrics(params = {}) {
  const page = await call('get', '/perf/metrics', { params }, null).catch(() => null);
  if (Array.isArray(page)) return page;
  if (page?.records) return page.records;
  return metricsFlat;
}

// GET /api/perf/metrics/{code} —— 单个指标详情
export function getMetricDetail(code) {
  return call(
    'get',
    `/perf/metrics/${code}`,
    {},
    () => perfMetricDetail[code] || perfMetricDetail.M0002
  );
}

// GET /api/perf/metrics/val-slots
export function getMetricSlots(baseDim) {
  // 后端要求 baseDim 必填（@NotBlank），前端不传时缺省 EMP，与 reportDimensions 默认一致
  return call('get', '/perf/metrics/val-slots', { params: { baseDim: baseDim || 'EMP' } }, []);
}

// 仅供单测；buildMetricTree 是纯函数
export const __test__ = { buildMetricTree };
