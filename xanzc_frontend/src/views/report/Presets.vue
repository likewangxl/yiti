<!--
  预置报表 —— 聚合 3 个固定模板汇总报表
  后端：
    GET  /api/reports/perf-summary          C.3 绩效汇总（必填 dim/cycleType）
    GET  /api/reports/customer-pool-summary C.4 客户池汇总（全可选）
    GET  /api/reports/touch-task-summary    C.2 机构触达汇总（必填 startDate/endDate）
  导出三 endpoint 各对应 /export POST 异步任务
-->
<template>
  <div class="rpt-presets">
    <div class="page-h">
      <h1>预置报表</h1>
      <span class="desc">点击卡片打开对应汇总报表</span>
    </div>

    <div class="grid">
      <div v-for="c in CARDS" :key="c.type" class="card-item" @click="openCard(c)">
        <div class="ico">{{ c.icon }}</div>
        <div class="t">{{ c.title }}</div>
        <div class="d">{{ c.desc }}</div>
        <div class="f">
          <span class="v">{{ c.endpoint }}</span>
          <a class="more">查看 →</a>
        </div>
      </div>
    </div>

    <!-- 通用汇总抽屉：按 type 切换表单/表格 -->
    <el-drawer
      v-model="drawer.show"
      :title="drawer.title"
      size="1100px"
      :destroy-on-close="true">
      <!-- 参数表单 -->
      <el-form :model="form" inline label-width="80px" size="default" class="filter-form">
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
          <el-button type="primary" :loading="loading" @click="loadData">查询</el-button>
          <el-button :loading="exporting" :disabled="!rows.length" @click="doExport">导出</el-button>
        </el-form-item>
      </el-form>

      <!-- 数据表（用通用 columns 自适应后端返回字段）-->
      <el-table :data="rows" size="small" v-loading="loading" border max-height="540" empty-text="暂无数据">
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
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue';
import { ElMessage } from 'element-plus';
import {
  getPerfSummary, getCustPoolSummary, getTouchSummary,
  exportPerfSummary, exportCustPoolSummary, exportTouchSummary
} from '@/api/report';

const CARDS = [
  { type: 'perf',  icon: '📈', title: '绩效汇总',   desc: '按员工/机构/客户维度汇总 KPI 得分与完成率',          endpoint: 'GET /reports/perf-summary' },
  { type: 'cust',  icon: '👥', title: '客户池汇总', desc: '各机构客户池：总量 / VIP / 普通 / 潜力',              endpoint: 'GET /reports/customer-pool-summary' },
  { type: 'touch', icon: '📞', title: '机构触达汇总', desc: '指定时间窗口内各机构触达任务执行情况',              endpoint: 'GET /reports/touch-task-summary' }
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
  const p = buildParams();
  if (p._err) return ElMessage.warning(p._err);
  loading.value = true;
  try {
    const fn = { perf: getPerfSummary, cust: getCustPoolSummary, touch: getTouchSummary }[drawer.type];
    const r = await fn(p);
    rows.value = Array.isArray(r) ? r : (r?.records || []);
    total.value = r?.total ?? rows.value.length;
  } catch (e) {
    ElMessage.error('查询失败：' + (e?.message || e));
    rows.value = [];
  } finally { loading.value = false; }
}

async function doExport() {
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
  .page-h { display: flex; align-items: baseline; gap: 12px; margin-bottom: 12px;
    h1 { font-size: 18px; font-weight: 600; color: $text-1; }
    .desc { color: $text-3; font-size: 12px; }
  }
  .grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
  .card-item {
    background: #fff;
    border: 1px solid $border-1;
    border-radius: 4px;
    padding: 22px 22px 18px;
    cursor: pointer;
    transition: .15s;
    &:hover { border-color: $primary-400; box-shadow: 0 4px 12px rgba(30,91,186,.15); transform: translateY(-2px); }
    .ico { font-size: 32px; margin-bottom: 10px; }
    .t { font-size: 16px; font-weight: 600; color: $text-1; margin-bottom: 8px; }
    .d { font-size: 13px; color: $text-3; line-height: 1.6; min-height: 42px; }
    .f { margin-top: 14px; display: flex; align-items: center; padding-top: 12px; border-top: 1px dashed $border-1;
      .v { font-size: 11px; color: $text-3; font-family: ui-monospace, monospace; }
      .more { margin-left: auto; color: $primary; font-size: 13px; }
    }
  }
}
.filter-form { margin-bottom: 12px; }
.pager { margin-top: 12px; display: flex; justify-content: flex-end; }
.pager :deep(.el-pagination) { flex-wrap: wrap; row-gap: 8px; justify-content: flex-end; }
</style>
