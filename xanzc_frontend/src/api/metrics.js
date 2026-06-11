// 指标库 API —— 对接 yiti `/api/perf/metrics`（MetricDefController）
// 提供动态指标查询页面用到的"指标树/指标列表/指标详情"。
//
// 重要：yiti 后端 `/api/perf/metrics` 是分页扁平接口，不支持 ?tree=true。
// 前端通过 status=ACTIVE 拉到当前所有"已发布"指标，然后客户端按 baseDim + metricLevel 构建二层树。
import { call } from './http';
import { perfMetricsTree, perfMetricDetail, metricsFlat } from '@/mock';

// 维度根节点（与 reportDimensions 对齐）
const DIM_LABELS = { EMP: '员工指标', ORG: '机构指标', CUST: '客户指标' };
// 业务类型展示顺序（与指标库 Metrics.vue 的 CATEGORY_OPTIONS 对齐），未列出的排其后，"其他"垫底
const CATEGORY_ORDER = ['规模类', '效益类', '质量类', '合规类'];
const CATEGORY_FALLBACK = '其他';

/**
 * 取单个指标的「业务类型」：优先 metricCategory 列（规模类/效益类/质量类/合规类），
 * 其次解析 metricDesc(JSON)._category 顶层，最后归入"其他"。与指标库列表分组口径一致。
 */
function categoryOf(m) {
  if (m.metricCategory && String(m.metricCategory).trim()) {
    return String(m.metricCategory).trim();
  }
  try {
    const meta = m.metricDesc ? JSON.parse(m.metricDesc) : null;
    if (meta && meta._category) return String(meta._category).split('/')[0];
  } catch { /* metricDesc 非 JSON，忽略 */ }
  return CATEGORY_FALLBACK;
}

function categorySort(a, b) {
  const ia = CATEGORY_ORDER.indexOf(a);
  const ib = CATEGORY_ORDER.indexOf(b);
  const ra = ia === -1 ? (a === CATEGORY_FALLBACK ? 999 : 500) : ia;
  const rb = ib === -1 ? (b === CATEGORY_FALLBACK ? 999 : 500) : ib;
  return ra - rb || a.localeCompare(b);
}

/**
 * 把 yiti 返回的扁平 records（MetricDefRespDTO[]）转成 MetricPicker 期望的 tree：
 *   [{ id, label, children: [{ id, label, children: [{ id: metricCode, label }] }] }]
 * 顶层按 baseDim 分组（员工/机构/客户）；二层按「业务类型」（metricCategory，如规模类/效益类）；叶子是单个指标。
 * 二层始终保留，便于「勾选某业务类型 = 选中该类全部指标」。
 */
function buildMetricTree(records) {
  const byDim = new Map();
  for (const m of records || []) {
    const dim = m.baseDim || 'OTHER';
    if (!byDim.has(dim)) byDim.set(dim, new Map());
    const byCat = byDim.get(dim);
    const cat = categoryOf(m);
    if (!byCat.has(cat)) byCat.set(cat, []);
    byCat.get(cat).push({
      // 叶子：以 metricCode 作为 tree node-key（MetricPicker 用它作 picked 的 code）
      id: m.metricCode,
      label: m.metricName || m.metricCode,
      code: m.metricCode,
      // 把后端字段透传一份，方便后续详情面板用
      raw: m
    });
  }
  const tree = [];
  for (const [dim, byCat] of byDim) {
    const dimNode = {
      id: `DIM_${dim}`,
      label: DIM_LABELS[dim] || dim,
      children: []
    };
    for (const cat of [...byCat.keys()].sort(categorySort)) {
      const leaves = byCat.get(cat).sort((a, b) => (a.code || '').localeCompare(b.code || ''));
      dimNode.children.push({
        id: `DIM_${dim}_CAT_${cat}`,
        label: cat,
        children: leaves
      });
    }
    tree.push(dimNode);
  }
  return tree;
}

/**
 * 指标树（动态指标查询使用）：
 *   1) 单次请求 GET /api/perf/metrics?status=ACTIVE
 *      —— 后端该接口已不分页（返回 List<MetricDefRespDTO> 全量，pageNo/pageSize 会被忽略），
 *         一次即可取到全部已发布指标，无需翻页。
 *   2) 客户端 buildTree（维度 → 业务类型 → 指标）
 *   3) 后端不可用 / 返回空时回退 mock perfMetricsTree
 */
export async function getMetricsTree() {
  const resp = await call(
    'get',
    '/perf/metrics',
    { params: { status: 'ACTIVE' } },
    null   // 不给 fallback：拿不到时直接 reject 让我们 catch 后返回 mock tree
  ).catch(() => null);

  // 该接口返回 List（数组）；兼容历史 PageResult({records}) 形态
  const records = Array.isArray(resp) ? resp : resp?.records;
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
