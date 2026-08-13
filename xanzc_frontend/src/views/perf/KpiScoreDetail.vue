<template>
  <main class="bp-crud perf-kpi-score-detail-page" aria-labelledby="perf-kpi-score-detail-page-title" :aria-busy="loading || exporting ? 'true' : 'false'">
    <header class="page-h">
      <PageTitle id="perf-kpi-score-detail-page-title">
        <span class="sub">数据日期 {{ dataDate || '-' }} · 方案 {{ schemeCode || '-' }}</span>
      </PageTitle>
      <div class="actions action-group" role="group" aria-label="KPI 得分详情操作">
        <el-button @click="goBack">返回</el-button>
        <el-button :loading="loading" :disabled="loading || exporting" @click="loadData">刷新</el-button>
      </div>
    </header>

    <!-- 查询：维度（按对象分组展示，列动态为该方案的各指标组）-->
    <section class="card-section data-panel filter-bar" aria-label="KPI 得分筛选">
      <el-form :inline="true" size="default" aria-label="KPI 得分筛选">
        <el-form-item label="维度">
          <el-select v-model="subjectType" clearable placeholder="全部维度" style="width:140px" @change="onQuery">
            <el-option v-for="(label, val) in DIM" :key="val" :value="val" :label="label" />
          </el-select>
        </el-form-item>
        <el-form-item label="对象">
          <el-input v-model="subjectKeyword" clearable placeholder="按对象名称模糊查询" style="width:200px"
                    @keyup.enter="onQuery" @clear="onQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onQuery">查询</el-button>
          <el-button @click="onReset">重置</el-button>
          <el-button type="success" plain :loading="exporting==='scores'" :disabled="loading || !!exporting" @click="onExportScores">导出KPI得分</el-button>
          <el-button type="success" plain :loading="exporting==='details'" :disabled="loading || !!exporting" @click="onExportDetails">导出KPI明细</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="card-section data-panel score-table-panel" aria-label="KPI 得分明细" aria-labelledby="perf-kpi-score-detail-table-heading"
      aria-describedby="perf-kpi-score-detail-table-state" :aria-busy="loading ? 'true' : 'false'">
      <div class="toolbar">
        <div>
          <h2 id="perf-kpi-score-detail-table-heading" class="section-title">KPI 得分明细</h2>
          <p class="hint">按对象查看实际值、目标值、完成率、权重与得分；横向列较多时可在表格内滚动。</p>
        </div>
        <p id="perf-kpi-score-detail-table-state" class="table-state" role="status" aria-live="polite">{{ scoreState }}</p>
      </div>
      <div v-if="loadError" class="table-error" role="alert">
        <span>{{ loadError }}</span>
        <el-button link type="primary" @click="loadData">重新加载</el-button>
      </div>
      <!-- 固定列：对象ID / 姓名 / 考核得分(合计)；之后每个指标一个分组列(指标名 + 实际/目标/基础/完成率/权重/得分) -->
      <el-table :data="records" size="small" border v-loading="loading"
        :empty-text="loadError ? '加载失败，请重新加载' : '暂无计算结果'"
        aria-labelledby="perf-kpi-score-detail-table-heading" aria-describedby="perf-kpi-score-detail-table-state">
        <!-- 维度：同一对象ID 可能在不同维度各有一行（如员工/客户工号撞号），按 对象ID+对象类型 分组 -->
        <el-table-column label="维度" width="80" fixed>
          <template #default="{row}">{{ DIM[row.subjectType] || row.subjectType || '-' }}</template>
        </el-table-column>
        <el-table-column label="对象ID" min-width="130" fixed prop="subjectId" />
        <el-table-column label="姓名" min-width="110" fixed>
          <template #default="{row}">{{ row.subjectName || '-' }}</template>
        </el-table-column>
        <el-table-column label="考核得分" width="100" fixed align="right">
          <template #default="{row}"><strong>{{ fmtNum(row.totalScore) }}</strong></template>
        </el-table-column>

        <el-table-column v-for="m in metrics" :key="m.metricCode"
          :label="m.metricName || m.metricCode" align="center">
          <el-table-column label="实际值" width="100" align="right">
            <template #default="{row}">{{ fmtNum(cell(row, m).actual) }}</template>
          </el-table-column>
          <el-table-column label="目标值" width="100" align="right">
            <template #default="{row}">{{ fmtNum(cell(row, m).target) }}</template>
          </el-table-column>
          <el-table-column label="基础值" width="100" align="right">
            <template #default="{row}">{{ fmtNum(cell(row, m).base) }}</template>
          </el-table-column>
          <el-table-column label="完成率" width="100" align="right">
            <template #default="{row}">{{ fmtRate(cell(row, m).completeRate) }}</template>
          </el-table-column>
          <el-table-column label="权重" width="90" align="right">
            <template #default>{{ fmtNum(m.weight) }}</template>
          </el-table-column>
          <el-table-column label="得分" width="90" align="right">
            <template #default="{row}"><strong>{{ fmtNum(cell(row, m).score) }}</strong></template>
          </el-table-column>
        </el-table-column>
      </el-table>

      <nav class="pager" aria-label="KPI 得分明细分页">
        <el-pagination v-model:current-page="pgNo" v-model:page-size="pgSize" :page-sizes="[20, 50, 100]"
          :total="total" background layout="total, sizes, prev, pager, next"
          @current-change="loadData" @size-change="onSizeChange" />
      </nav>
    </section>
  </main>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { listKpiScoreResults, exportKpiScores, exportKpiScoreDetails } from '@/api/perf';

const route = useRoute();
const router = useRouter();
const dataDate = ref(route.query.dataDate || '');
const schemeCode = ref(route.query.schemeCode || '');
const schemeName = ref(route.query.schemeName || '');
// 导出文件名前缀：优先方案名称，缺失回退方案编码
function exportPrefix() { return schemeName.value || schemeCode.value || 'KPI'; }

const metrics = ref([]);   // 指标列定义 [{metricCode, metricName, weight}]
const records = ref([]);   // 对象行 [{subjectId, subjectName, totalScore, metrics:{code:{actual,target,base,completeRate,score}}}]
const total = ref(0);
const pgNo = ref(1);
const pgSize = ref(20);
const loading = ref(false);
const loadError = ref('');
const subjectType = ref('');
const subjectKeyword = ref(''); // 对象名称模糊查询关键字
const scoreState = computed(() => loading.value
  ? 'KPI 得分明细加载中'
  : loadError.value
    ? 'KPI 得分明细加载失败'
    : records.value.length
      ? `共 ${total.value} 条对象记录`
      : '暂无 KPI 得分明细');

const DIM = { EMP: '员工', ORG: '机构', CUST: '客户' };
const EMPTY_CELL = {};
/** 取某行某指标的格数据（缺失返回空对象，模板按字段取值即为 '-'） */
function cell(row, m) {
  return (row.metrics && row.metrics[m.metricCode]) || EMPTY_CELL;
}
function fmtNum(v) {
  if (v == null || v === '') return '-';
  const n = Number(v);
  return Number.isNaN(n) ? v : (Math.round(n * 10000) / 10000);
}
function fmtRate(v) {
  if (v == null || v === '') return '-';
  const n = Number(v);
  return Number.isNaN(n) ? v : `${Math.round(n * 100) / 100}%`;
}

function onQuery() { pgNo.value = 1; loadData(); }
function onReset() { subjectType.value = ''; subjectKeyword.value = ''; pgNo.value = 1; loadData(); }
function onSizeChange() { pgNo.value = 1; loadData(); }
function goBack() {
  if (window.history.length > 1) router.back();
  else router.push({ name: 'PerfCompute' });
}

const exporting = ref('');
/** 触发浏览器下载 blob */
function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
function exportParams() {
  return {
    dataDate: dataDate.value || undefined,
    schemeCode: schemeCode.value || undefined,
    subjectType: subjectType.value || undefined
  };
}
async function onExportScores() {
  exporting.value = 'scores';
  try {
    const blob = await exportKpiScores(exportParams());
    saveBlob(blob, `${exportPrefix()}_得分_${dataDate.value || ''}.xlsx`);
  } catch { ElMessage.error('导出失败'); } finally { exporting.value = ''; }
}
async function onExportDetails() {
  exporting.value = 'details';
  try {
    const blob = await exportKpiScoreDetails(exportParams());
    saveBlob(blob, `${exportPrefix()}_明细_${dataDate.value || ''}.xlsx`);
  } catch { ElMessage.error('导出失败'); } finally { exporting.value = ''; }
}

async function loadData() {
  loading.value = true;
  loadError.value = '';
  try {
    const r = await listKpiScoreResults({
      dataDate: dataDate.value || undefined,
      schemeCode: schemeCode.value || undefined,
      subjectType: subjectType.value || undefined,
      subjectKeyword: subjectKeyword.value || undefined,
      pageNo: pgNo.value, pageSize: pgSize.value
    });
    metrics.value = r?.metrics || [];
    records.value = r?.records || [];
    total.value = r?.total ?? records.value.length;
  } catch (error) {
    metrics.value = [];
    records.value = [];
    total.value = 0;
    loadError.value = `KPI 得分明细加载失败：${error?.message || '请稍后重试'}`;
  } finally { loading.value = false; }
}

onMounted(loadData);
</script>

<style scoped lang="scss">
.page-h { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--space-3); }
.page-h h1 { font-size: 18px; font-weight: 600; }
.page-h .sub { color: var(--color-text-muted); font-size: 13px; font-weight: 400; margin-left: 10px; }
.toolbar { align-items: flex-start; display: flex; justify-content: space-between; gap: var(--space-4); padding-bottom: var(--space-3); }
.section-title { color: var(--color-text-strong); font-size: 16px; font-weight: 600; line-height: 24px; margin: 0; }
.hint { color: var(--color-text-muted); font-size: 12px; line-height: 18px; margin: 4px 0 0; }
.table-state { color: var(--color-text-muted); font-size: 12px; margin: 2px 0 0; white-space: nowrap; }
.table-error { align-items: center; background: var(--color-danger-bg); border-left: 3px solid var(--color-danger-fg); color: var(--color-danger-fg); display: flex; font-size: 12px; gap: var(--space-3); justify-content: space-between; margin-bottom: var(--space-3); padding: var(--space-2) var(--space-3); }
.score-table-panel { min-width: 0; }
.pager { margin-top: 12px; display: flex; justify-content: flex-end; }
.pager :deep(.el-pagination) { flex-wrap: wrap; row-gap: 8px; justify-content: flex-end; }
@media (prefers-reduced-motion: reduce) {
  :where(.perf-kpi-score-detail-page) :deep(*) { transition-duration: 0.01ms !important; animation-duration: 0.01ms !important; }
}
</style>
