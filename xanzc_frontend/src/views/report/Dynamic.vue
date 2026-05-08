<!--
  动态指标查询 ——「维度 → 指标 → 对象 → 日期 → 查询」
  对应 HTML 中 RptDyn 组件（Reports v2: dynamic query with metric tree + saved schemes）

  接入 yiti API（路径以 src/api/*.js 为准，注意切勿与历史/mock 路径混淆）：
    GET   /api/reports/query-dimensions?dim={EMP|ORG|CUST}     —— getQueryDimensions
                                                                  （单维度指标分组树；维度本身硬编码 3 项）
    POST  /api/reports/dynamic-query                           —— queryDynamic
    POST  /api/reports/dynamic-query/export                    —— exportDynamic
    GET   /api/reports/saved-queries[/{id}]                    —— listSavedQueries / getSavedQuery
    GET   /api/employees                                       —— listEmployees   (EMP 维度对象池)
    GET   /api/orgs/tree                                       —— getOrgTree      (ORG 维度对象池)
    GET   /api/customers                                       —— listCustomers   (CUST 维度对象池)
    GET   /api/perf/metrics?status=ACTIVE&pageNo=1&pageSize=100 —— listMetrics    (指标 code→name 映射 + 默认选中)
-->
<template>
  <div class="rpt-dyn">
    <div class="page-h">
      <h1>动态指标查询</h1>
      <span class="desc">维度 → 指标 → 对象 → 日期 · 支持保存方案 / 部门共享</span>
      <div class="actions">
        <el-button :icon="Folder"   @click="schemeListVisible = true">我的方案</el-button>
        <el-button :icon="Plus"     @click="openSaveScheme">保存为方案</el-button>
        <el-button :icon="Download" @click="onExport" :loading="exporting">导出</el-button>
      </div>
    </div>

    <!-- 查询条件卡 -->
    <div class="card-section query">
      <div class="row">
        <div class="col">
          <div class="lab">① 维度</div>
          <el-radio-group v-model="dim" size="default">
            <el-radio-button v-for="d in dimensions" :key="d.code" :value="d.code">{{ d.label }}</el-radio-button>
          </el-radio-group>
        </div>

        <div class="col grow">
          <div class="lab">② 指标 (已选 {{ pickedMetrics.length }} 个)</div>
          <div class="tags click" @click="pickerVisible = true">
            <el-tag v-for="c in pickedMetrics" :key="c" closable type="info" effect="plain" @close.stop="removeMetric(c)">
              {{ metricLabel(c) }}
            </el-tag>
            <el-tag class="add" effect="plain">+ 添加</el-tag>
          </div>
        </div>

        <div class="col grow">
          <div class="lab">③ 对象 (已选 {{ subjects.length }})</div>
          <div class="tags">
            <el-tag v-for="s in subjects" :key="s.id" closable effect="plain" @close="subjects = subjects.filter(x => x !== s)">
              {{ subjectLabel(s) }}
            </el-tag>
            <el-popover :width="280" trigger="click">
              <template #reference>
                <el-tag class="add" effect="plain">+ 添加</el-tag>
              </template>
              <el-input v-model="objKw" :placeholder="objSearchPlaceholder" size="small" clearable />
              <div class="obj-pool">
                <div v-if="!objectOptions.length" class="obj-empty">暂无可选项</div>
                <div v-for="o in objectOptions" :key="o.id" class="obj-row" @click="addSubject(o)">
                  <span>{{ o.name }}</span>
                  <span class="muted">{{ o.org }}</span>
                </div>
              </div>
            </el-popover>
          </div>
        </div>

        <div class="col">
          <div class="lab">④ 数据日期</div>
          <el-date-picker v-model="date" type="date" value-format="YYYY-MM-DD" style="width:200px" />
        </div>
      </div>

      <div class="row-actions">
        <el-button @click="reset">重置</el-button>
        <el-button type="primary" :loading="querying" @click="doQuery">查询</el-button>
      </div>
    </div>

    <!-- 结果卡 -->
    <div class="card-section result" v-if="hasResult">
      <div class="card-h">
        <div class="title">查询结果（{{ rows.length }} 行 × {{ pickedMetrics.length }} 指标）</div>
        <div class="chart-tabs">
          <el-button :type="view === 'table' ? 'primary' : ''" :icon="Grid"        circle size="small" @click="view='table'" />
          <el-button :type="view === 'bar'   ? 'primary' : ''" :icon="Histogram"   circle size="small" @click="view='bar'"   />
          <el-button :type="view === 'line'  ? 'primary' : ''" :icon="TrendCharts" circle size="small" @click="view='line'"  />
          <el-button :type="view === 'pie'   ? 'primary' : ''" :icon="PieChart"    circle size="small" @click="view='pie'"   />
        </div>
      </div>

      <el-table v-if="view === 'table'" :data="rows" size="default" stripe>
        <el-table-column prop="subject" :label="dimLabel" width="160" />
        <el-table-column
          v-for="c in pickedMetrics" :key="c" :prop="c"
          :label="metricLabel(c)" align="right">
          <template #default="{ row }">{{ formatNum(row[c]) }}</template>
        </el-table-column>
      </el-table>

      <v-chart v-else class="chart" :option="chartOption" autoresize />
    </div>

    <MetricPicker
      v-model:visible="pickerVisible"
      v-model="pickedMetrics"
    />
    <SchemeSaveDialog
      v-model:visible="saveSchemeVisible"
      :context="saveContext"
      @saved="onSchemeSaved"
    />
    <SchemeListDialog
      v-model:visible="schemeListVisible"
      @load="onSchemeLoad"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { Grid, Histogram, TrendCharts, PieChart, Folder, Plus, Download } from '@element-plus/icons-vue';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { BarChart, LineChart, PieChart as EPie } from 'echarts/charts';
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components';
import VChart from 'vue-echarts';
import { queryDynamic, exportDynamic, getQueryDimensions, getSavedQuery } from '@/api/report';
import { listEmployees } from '@/api/employees';
import { getOrgTree } from '@/api/orgs';
import { listCustomers } from '@/api/customers';
import { listMetrics } from '@/api/metrics';
import MetricPicker from './components/MetricPicker.vue';
import SchemeSaveDialog from './components/SchemeSaveDialog.vue';
import SchemeListDialog from './components/SchemeListDialog.vue';

use([CanvasRenderer, BarChart, LineChart, EPie, GridComponent, TooltipComponent, LegendComponent]);

const dim = ref('EMP');
const dimensions = ref([{ code: 'EMP', label: '员工' }, { code: 'ORG', label: '机构' }, { code: 'CUST', label: '客户' }]);
// metricsList：动态从 yiti `/api/perf/metrics?status=ACTIVE` 拉取的有效指标列表（含 mock 兜底）
//   元素形如：{ metricCode, metricName, baseDim, metricLevel, unit, ... } —— 见 MetricDefRespDTO
const metricsList = ref([]);
const pickedMetrics = ref([]);   // 真实指标加载完才会填默认 4 个，避免脏 code
const subjects = ref([
  { id: 'E001', name: '张三', org: '南山支行' },
  { id: 'E002', name: '李四', org: '福田支行' },
  { id: 'E003', name: '孙七', org: '罗湖支行' },
  { id: 'E004', name: '郑九', org: '宝安支行' },
  { id: 'E005', name: '赵六', org: '龙岗支行' }
]);
const date = ref('2026-04-22');
const view = ref('table');
const querying = ref(false);
const exporting = ref(false);
const hasResult = ref(true);

// 结果数据 —— 默认填几行 demo 数字，后端返回会替换
const rows = ref([]);
function defaultRows() {
  const seed = [6420, 9100, 22, 182, 5520, 7800, 18, 156, 4280, 11200, 15, 220, 3800, 5400, 12, 98, 7100, 8300, 25, 203];
  return subjects.value.map((s, i) => {
    const obj = { subject: dim.value === 'EMP' ? '员工' + s.name : s.name };
    pickedMetrics.value.forEach((c, j) => { obj[c] = seed[(i * 4 + j) % seed.length]; });
    return obj;
  });
}

const dimLabel = computed(() => dimensions.value.find(d => d.code === dim.value)?.label || '对象');
// metricMap：tolerant 同时消化两种字段命名（后端 MetricDefRespDTO.metricCode/metricName 与 mock metricsFlat.code/label）
const metricMap = computed(() => Object.fromEntries(
  metricsList.value.map(m => [m.metricCode || m.code, m.metricName || m.label])
));
const metricLabel = (c) => metricMap.value[c] || c;
const formatNum = (v) => v == null ? '-' : (typeof v === 'number' ? v.toLocaleString() : v);

function removeMetric(c) { pickedMetrics.value = pickedMetrics.value.filter(x => x !== c); }
function reset() {
  dim.value = 'EMP';
  // 重置后默认重新挑当前维度第 1 个指标（若已加载），否则置空
  pickedMetrics.value = pickFirstMetricsForDim('EMP', 1);
  subjects.value = [];
  date.value = '2026-04-22';
  hasResult.value = false;
}

// 从 metricsList 中按维度挑前 N 个指标（用于初始默认选中和 reset）
function pickFirstMetricsForDim(d, n) {
  const list = metricsList.value
    .filter(m => (m.baseDim || 'EMP') === d)
    .slice(0, n)
    .map(m => m.metricCode || m.code)
    .filter(Boolean);
  return list;
}

// 添加对象 popover —— 候选源随 dim 切换：员工 / 机构 / 客户
const objKw = ref('');
const employees = ref([]);
const orgs = ref([]);              // 扁平：机构（去掉根节点）
const customers = ref([]);              // CUST 维度懒加载,与 EMP/ORG 一致

const objSearchPlaceholder = computed(() => ({
  EMP:  '搜索员工 / 所在机构',
  ORG:  '搜索机构',
  CUST: '搜索客户 / 行业'
}[dim.value] || '搜索对象'));

// 把 mock 的员工/机构/客户结构归一为 { id, name, org }
const objectPool = computed(() => {
  if (dim.value === 'ORG') {
    return orgs.value.map(o => ({ id: o.code, name: o.name, org: '' }));
  }
  if (dim.value === 'CUST') {
    return customers.value.map(c => ({ id: c.id, name: c.name, org: c.org || c.industry || '' }));
  }
  return employees.value.map(e => ({ id: e.id, name: e.name, org: e.org }));
});

const objectOptions = computed(() => {
  const kw = objKw.value.trim();
  return objectPool.value
    .filter(o => !subjects.value.some(s => s.id === o.id))
    .filter(o => !kw || o.name.includes(kw) || (o.org || '').includes(kw))
    .slice(0, 20);
});

function subjectLabel(s) {
  if (dim.value === 'EMP') return '员工' + s.name;
  return s.name;
}

function addSubject(o) {
  if (subjects.value.some(s => s.id === o.id)) return;
  subjects.value.push(o);
  objKw.value = '';
}

// 维度切换：清空已选对象，避免跨维度脏数据；按需懒加载对应维度的候选数据
watch(dim, async (cur, prev) => {
  if (cur === prev) return;
  subjects.value = [];
  objKw.value = '';
  hasResult.value = false;
  if (cur === 'ORG' && !orgs.value.length) {
    try {
      const tree = await getOrgTree();
      orgs.value = flattenOrgTree(tree);
    } catch (e) {}
  }
  if (cur === 'CUST' && !customers.value.length) {
    try {
      const list = await listCustomers({ pageNo: 1, pageSize: 100 });
      if (Array.isArray(list)) customers.value = list;
    } catch (e) {}
  }
});

function flattenOrgTree(nodes) {
  const out = [];
  const walk = (arr) => arr.forEach(n => {
    // 跳过最顶层「分行」根，仅保留可选支行 / 末梢机构
    if (n.children?.length) walk(n.children);
    else out.push({ code: n.code, name: n.name });
  });
  walk(nodes || []);
  return out;
}

async function doQuery() {
  if (!pickedMetrics.value.length) { ElMessage.warning('请至少选择 1 个指标'); return; }
  if (!subjects.value.length)      { ElMessage.warning('请至少选择 1 个对象'); return; }
  querying.value = true;
  try {
    const r = await queryDynamic({
      dim: dim.value,
      metrics: pickedMetrics.value,
      subjects: subjects.value.map(s => s.id),
      date: date.value
    });
    if (r?.result) rows.value = r.result;
    else if (Array.isArray(r?.rows)) rows.value = r.rows;
    else rows.value = defaultRows();
    hasResult.value = true;
    ElMessage.success(`查询成功：${rows.value.length} 行`);
  } catch (e) {
    rows.value = defaultRows();
    hasResult.value = true;
  } finally {
    querying.value = false;
  }
}

async function onExport() {
  exporting.value = true;
  try {
    const r = await exportDynamic({
      dim: dim.value,
      metrics: pickedMetrics.value,
      subjects: subjects.value.map(s => s.id),
      date: date.value
    });
    ElMessage.success(`导出任务已提交（taskId=${r?.taskId || 'mock'}）`);
  } finally {
    exporting.value = false;
  }
}

// 方案保存 / 我的方案
const saveSchemeVisible = ref(false);
const schemeListVisible = ref(false);
const pickerVisible = ref(false);
const saveContext = ref({ dim: 'EMP', metrics: 0, objects: 0, payload: {} });

function openSaveScheme() {
  saveContext.value = {
    dim: dim.value,
    metrics: pickedMetrics.value.length,
    objects: subjects.value.length,
    payload: {
      dim: dim.value,
      metrics: pickedMetrics.value,           // 传字符串 code 数组（saveQuery 内部 stringify）
      subjects: subjects.value.map(s => s.id) // 传 id 字符串数组
    }
  };
  saveSchemeVisible.value = true;
}
function onSchemeSaved(/* scheme */) { /* 不强制刷新，列表 dialog 打开时会再拉 */ }

/**
 * 载入方案 —— 从列表项 → 调详情接口 → 切维度 → 等候选数据加载完 → ID 数组反查为对象 → 触发查询。
 * 列表项 SavedQuerySummaryDTO 不含 metricCodes/subjectIds，必须先调 getSavedQuery 拿详情。
 */
async function onSchemeLoad(scheme) {
  if (!scheme?.id) return;
  let detail;
  try { detail = await getSavedQuery(scheme.id); }
  catch { ElMessage.error('载入方案失败'); return; }
  if (!detail) return;

  // 1. 切维度（会触发 watch dim：清空 subjects + 按需懒加载 ORG/CUST 候选）
  if (detail.dim && detail.dim !== dim.value) {
    dim.value = detail.dim;
    // 等候选池就绪（最多 1.5s，每 100ms 探一次）
    for (let i = 0; i < 15; i++) {
      if (objectPool.value.length > 0) break;
      await new Promise(r => setTimeout(r, 100));
    }
  }

  // 2. 反显指标（数组形态：['M0001', ...]）
  if (Array.isArray(detail.metrics)) pickedMetrics.value = [...detail.metrics];

  // 3. 反显对象 —— ID 数组 → 候选池里反查为 {id, name, org} 对象
  if (Array.isArray(detail.subjects)) {
    const pool = objectPool.value;
    subjects.value = detail.subjects.map(id => {
      const found = pool.find(o => o.id === id);
      return found || { id, name: id, org: '' };  // 找不到时退化为只显示 ID
    });
  }
  ElMessage.success(`已载入方案：${detail.name}`);
  doQuery();
}

// 图表 option
const chartOption = computed(() => {
  const xs = rows.value.map(r => r.subject);
  const series = pickedMetrics.value.map(c => ({
    name: metricLabel(c),
    type: view.value === 'bar' ? 'bar' : view.value === 'line' ? 'line' : 'pie',
    data: view.value === 'pie'
      ? rows.value.map(r => ({ name: r.subject, value: r[c] || 0 }))
      : rows.value.map(r => r[c] || 0),
    smooth: view.value === 'line',
    radius: view.value === 'pie' ? '60%' : undefined
  }));
  if (view.value === 'pie') {
    // 饼图只展示第一个指标
    return {
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [series[0] || { type: 'pie', data: [] }]
    };
  }
  return {
    tooltip: { trigger: 'axis' },
    legend: { top: 0, right: 0 },
    grid: { top: 36, left: 60, right: 20, bottom: 30 },
    xAxis: { type: 'category', data: xs },
    yAxis: { type: 'value' },
    series
  };
});

onMounted(async () => {
  // 元数据并行：维度 / 员工 / 指标库（指标用于编号→名称映射 + 默认选中）
  // 注：getQueryDimensions(dim) 后端必传 dim，否则 400 VALID_002；
  //     真接口返回 {dim, dimName, metrics:[...]} 单维度树（不是 dim 列表），
  //     这里 Array.isArray 兜底主要给 mock 模式 [{code,label}] 用，真接口下静默 no-op，
  //     dim 列表保持本地硬编码 EMP/ORG/CUST 即可。
  await Promise.all([
    getQueryDimensions(dim.value).then(d => Array.isArray(d) && d.length && (dimensions.value = d)).catch(() => {}),
    listEmployees().then(e => Array.isArray(e) && (employees.value = e)).catch(() => {}),
    listMetrics({ status: 'ACTIVE', pageNo: 1, pageSize: 100 })
      .then(list => { if (Array.isArray(list)) metricsList.value = list; })
      .catch(() => { metricsList.value = []; })
  ]);

  // 指标库到位后挑前 4 个 EMP 维度的指标作为默认选中（真实数据驱动，避免脏 code）
  if (!pickedMetrics.value.length) {
    pickedMetrics.value = pickFirstMetricsForDim('EMP', 4);
  }
  rows.value = defaultRows();
});
</script>

<style lang="scss" scoped>
.rpt-dyn {
  .page-h { display: flex; align-items: baseline; gap: 12px; margin-bottom: 12px;
    h1 { font-size: 18px; font-weight: 600; color: $text-1; }
    .desc { color: $text-3; font-size: 12px; }
    .actions { margin-left: auto; display: flex; gap: 8px; }
  }
  .query .row { display: flex; gap: 16px; align-items: flex-start; flex-wrap: wrap; }
  .query .col { display: flex; flex-direction: column; gap: 6px; min-width: 180px; }
  .query .col.grow { flex: 1; min-width: 240px; }
  .query .lab { font-size: 12px; color: $text-3; }
  .query .tags { display: flex; flex-wrap: wrap; gap: 6px; padding: 6px 8px; min-height: 36px; border: 1px solid $border-2; border-radius: 4px; align-items: center;
    &.click { cursor: pointer; }
    .add { cursor: pointer; border-style: dashed; color: $primary-400; }
  }
  .row-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }

  .result { padding: 16px 20px; }
  .card-h { display: flex; align-items: center; padding: 0 0 12px; border-bottom: 1px solid $border-1; margin-bottom: 14px;
    .title { font-size: 14px; font-weight: 600; }
    .chart-tabs { margin-left: auto; display: flex; gap: 4px; }
  }
  .chart { height: 360px; }
  .obj-pool { max-height: 240px; overflow: auto; margin-top: 8px;
    .obj-row { display: flex; padding: 6px 8px; cursor: pointer; border-radius: 4px; font-size: 13px;
      &:hover { background: $bg-soft; }
      .muted { margin-left: auto; color: $text-4; font-size: 12px; }
    }
  }
}
</style>
