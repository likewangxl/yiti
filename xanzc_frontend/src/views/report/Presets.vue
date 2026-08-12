<!--
  预置报表 —— 聚合 3 个固定模板汇总报表
  后端：
    GET  /api/reports/perf-summary          C.3 绩效汇总（必填 dim/cycleType）
    GET  /api/reports/customer-pool-summary C.4 客户池汇总（全可选）
    GET  /api/reports/touch-task-summary    C.2 机构触达汇总（必填 startDate/endDate）
  导出三 endpoint 各对应 /export POST 异步任务
-->
<template>
  <main class="bp-crud rpt-presets" aria-labelledby="presets-report-title" :aria-busy="loading || exporting ? 'true' : 'false'">
    <header class="page-h">
      <PageTitle id="presets-report-title" />
      <span class="desc">点击卡片打开对应汇总报表</span>
    </header>

    <section class="grid" aria-label="预置报表类型">
      <button v-for="card in CARDS" :key="card.type" type="button" class="card-item" :aria-label="`查看${card.title}`" @click="openCard(card)">
        <el-icon class="ico" aria-hidden="true"><component :is="card.icon" /></el-icon>
        <span class="t">{{ card.title }}</span>
        <span class="d">{{ card.desc }}</span>
        <span class="f"><span class="v">{{ card.endpoint }}</span><span class="more">查看报表</span></span>
      </button>
    </section>

    <!-- 通用汇总抽屉：按 type 切换表单/表格 -->
    <el-drawer
      v-model="drawer.show"
      class="bp-crud-dialog"
      :title="drawer.title"
      size="1100px" aria-label="预置报表查询"
      :destroy-on-close="true">
      <!-- 参数表单 -->
      <section class="card-section filter-bar" aria-label="预置报表筛选">
      <el-form :model="form" inline label-width="80px" size="default" class="filter-form" @submit.prevent>
        <!-- 绩效汇总 -->
        <template v-if="drawer.type === 'perf'">
          <el-form-item label="维度" required>
            <el-select v-model="form.dim" style="width:120px">
              <el-option label="员工 EMP" value="EMP" />
              <el-option label="机构 ORG" value="ORG" />
              <el-option label="客户 CUST" value="CUST" />
            </el-select>
          </el-form-item>
          <el-form-item label="考核周期" required>
            <el-select v-model="form.cycleType" style="width:120px">
              <el-option label="日 DAY" value="DAY" />
              <el-option label="月 MONTH" value="MONTH" />
              <el-option label="季 QUARTER" value="QUARTER" />
              <el-option label="年 YEAR" value="YEAR" />
            </el-select>
          </el-form-item>
          <el-form-item label="主体 ID">
            <el-input v-model="form.subjectIdsStr" placeholder="逗号分隔，如 admin,E10001（留空查全部）" style="width:280px" />
          </el-form-item>
        </template>
        <!-- 客户池汇总 -->
        <template v-if="drawer.type === 'cust'">
          <el-form-item label="机构编码">
            <el-input v-model="form.orgId" placeholder="留空查全部" style="width:200px" />
          </el-form-item>
        </template>
        <!-- 触达任务汇总 -->
        <template v-if="drawer.type === 'touch'">
          <el-form-item label="开始日期" required>
            <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" placeholder="开始" />
          </el-form-item>
          <el-form-item label="结束日期" required>
            <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" placeholder="结束" />
          </el-form-item>
          <el-form-item label="机构编码">
            <el-input v-model="form.orgId" placeholder="留空查全部" style="width:200px" />
          </el-form-item>
        </template>
        <el-form-item>
          <el-button type="primary" :loading="loading" :disabled="loading" @click="loadData">查询</el-button>
          <el-button :loading="exporting" :disabled="!rows.length || exporting" @click="doExport">导出</el-button>
        </el-form-item>
      </el-form>
      </section>

      <!-- 数据表（用通用 columns 自适应后端返回字段）-->
      <section class="card-section data-panel" aria-labelledby="preset-table-title" :aria-busy="loading ? 'true' : 'false'">
      <div class="toolbar"><div><h2 id="preset-table-title" class="section-title">{{ drawer.title || '汇总结果' }}</h2><p class="hint">按当前筛选条件读取真实汇总数据。</p></div><p class="table-state" role="status" aria-live="polite">{{ errorMessage || (loading ? '正在加载汇总数据' : rows.length ? `共 ${total} 条数据` : '暂无数据') }}</p></div>
      <div v-if="errorMessage" class="error-state" role="alert"><span>{{ errorMessage }}</span><el-button link type="primary" @click="loadData">重试</el-button></div>
      <el-table :data="rows" size="default" v-loading="loading" border max-height="540" empty-text="暂无数据" aria-labelledby="preset-table-title">
        <el-table-column v-for="col in autoColumns" :key="col" :prop="col" :label="prettyCol(col)" min-width="120" />
      </el-table>

      <!-- 分页 -->
      <div class="pager" v-if="total > pageSize">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :page-sizes="[20, 50, 100]"
          :total="total"
          background small
          layout="total, sizes, prev, pager, next"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
      </section>
    </el-drawer>
  </main>
</template>

<script setup>
import { ref, reactive, computed } from 'vue';
import { ElMessage } from 'element-plus';
import { DataAnalysis, Promotion, UserFilled } from '@element-plus/icons-vue';
import {
  getPerfSummary, getCustPoolSummary, getTouchSummary,
  exportPerfSummary, exportCustPoolSummary, exportTouchSummary
} from '@/api/report';

const CARDS = [
  { type: 'perf',  icon: DataAnalysis, title: '绩效汇总',   desc: '按员工、机构或客户维度汇总 KPI 得分与完成率', endpoint: 'GET /reports/perf-summary' },
  { type: 'cust',  icon: UserFilled, title: '客户池汇总', desc: '查询各机构客户池总量及 VIP、普通、潜力客户分布', endpoint: 'GET /reports/customer-pool-summary' },
  { type: 'touch', icon: Promotion, title: '机构触达汇总', desc: '统计指定时间窗口内各机构触达任务的执行情况', endpoint: 'GET /reports/touch-task-summary' }
];

const drawer = reactive({ show: false, type: '', title: '' });
const form = reactive({
  dim: 'EMP', cycleType: 'MONTH', subjectIdsStr: '',
  orgId: '',
  startDate: '', endDate: ''
});
const rows = ref([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(20);
const loading = ref(false);
const exporting = ref(false);
const errorMessage = ref('');

// 自动从第一行 keys 派生表头；后端返回字段不固定时这样最稳
const autoColumns = computed(() => {
  if (!rows.value.length) return [];
  return Object.keys(rows.value[0]).filter(k => typeof rows.value[0][k] !== 'object');
});
// 简单 camelCase → 中文（覆盖常见列名）
const COL_LABEL = {
  orgCode: '机构编码', orgName: '机构', totalCount: '总数',
  vipCount: 'VIP', normalCount: '普通', potentialCount: '潜力',
  empId: '员工号', empName: '姓名', subjectId: '主体 ID', subjectName: '主体名',
  cycleType: '周期', cycleDate: '周期日期', dim: '维度',
  kpiScore: 'KPI 得分', completionRate: '完成率',
  startDate: '开始', endDate: '结束',
  touchCount: '触达数', successCount: '成功', failCount: '失败'
};
const prettyCol = (k) => COL_LABEL[k] || k;

function openCard(card) {
  drawer.type = card.type;
  drawer.title = card.title;
  // 重置表单 + 数据
  form.dim = 'EMP';
  form.cycleType = 'MONTH';
  form.subjectIdsStr = '';
  form.orgId = '';
  // touch 默认最近 30 天
  const now = new Date();
  const past = new Date(Date.now() - 30 * 86400000);
  form.endDate = now.toISOString().slice(0, 10);
  form.startDate = past.toISOString().slice(0, 10);
  rows.value = [];
  total.value = 0;
  pageNo.value = 1;
  drawer.show = true;
}

function buildParams() {
  const p = { pageNo: pageNo.value, pageSize: pageSize.value };
  if (drawer.type === 'perf') {
    if (!form.dim) return { _err: '请选维度' };
    if (!form.cycleType) return { _err: '请选考核周期' };
    p.dim = form.dim;
    p.cycleType = form.cycleType;
    // subjectIds 后端 @ModelAttribute 接收 List 时支持逗号分隔
    const ids = form.subjectIdsStr.trim();
    if (ids) p.subjectIds = ids;
  } else if (drawer.type === 'cust') {
    if (form.orgId) p.orgId = form.orgId;
  } else if (drawer.type === 'touch') {
    if (!form.startDate || !form.endDate) return { _err: '请选开始/结束日期' };
    p.startDate = form.startDate;
    p.endDate = form.endDate;
    if (form.orgId) p.orgId = form.orgId;
  }
  return p;
}

async function loadData() {
  if (loading.value) return;
  const p = buildParams();
  if (p._err) return ElMessage.warning(p._err);
  loading.value = true;
  errorMessage.value = '';
  try {
    const fn = { perf: getPerfSummary, cust: getCustPoolSummary, touch: getTouchSummary }[drawer.type];
    const r = await fn(p);
    rows.value = Array.isArray(r) ? r : (r?.records || []);
    total.value = r?.total ?? rows.value.length;
  } catch (e) {
    errorMessage.value = e?.message || '预置报表查询失败，请稍后重试';
    ElMessage.error('查询失败：' + errorMessage.value);
    rows.value = [];
  } finally { loading.value = false; }
}

async function doExport() {
  if (exporting.value) return;
  exporting.value = true;
  try {
    const p = buildParams();
    if (p._err) return ElMessage.warning(p._err);
    const fn = { perf: exportPerfSummary, cust: exportCustPoolSummary, touch: exportTouchSummary }[drawer.type];
    const r = await fn(p);
    if (r?.taskId) {
      ElMessage.success(`已提交导出任务 ${r.taskId}，请到导出任务列表下载`);
    } else {
      ElMessage.success('已提交导出任务');
    }
  } catch (e) {
    ElMessage.error('导出失败：' + (e?.message || e));
  } finally { exporting.value = false; }
}
</script>

<style lang="scss" scoped>
.rpt-presets {
  .grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-4); }
  .card-item {
    align-items: flex-start; background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius-control); color: var(--color-text); cursor: pointer; display: flex; flex-direction: column; min-height: 192px; padding: var(--space-6); text-align: left;
    transition: border-color 180ms ease-out, box-shadow 180ms ease-out, transform 180ms ease-out;
    &:hover { border-color: var(--color-brand-500); box-shadow: var(--shadow-surface); transform: translateY(-1px); }
    .ico { color: var(--color-brand-700); font-size: 24px; margin-bottom: var(--space-3); }
    .t { color: var(--color-text-strong); font-size: 16px; font-weight: 600; margin-bottom: var(--space-2); }
    .d { color: var(--color-text); font-size: 14px; line-height: 22px; min-height: 44px; }
    .f { align-items: center; border-top: 1px solid var(--color-border); display: flex; margin-top: auto; padding-top: var(--space-3); width: 100%;
      .v { color: var(--color-text-muted); font-family: ui-monospace, monospace; font-size: 12px; }
      .more { color: var(--color-brand-700); font-size: 14px; font-weight: 500; margin-left: auto; }
    }
  }
}
.pager { margin-top: 12px; display: flex; justify-content: flex-end; }
.pager :deep(.el-pagination) { flex-wrap: wrap; row-gap: 8px; justify-content: flex-end; }
.error-state { align-items: center; background: var(--color-danger-bg); border: 1px solid var(--color-border); color: var(--color-danger-fg); display: flex; gap: var(--space-3); justify-content: space-between; margin-bottom: var(--space-3); padding: var(--space-3); }
</style>
