<!--
  业绩调整查询 —— 历史数据查询（只读）
  数据源：AMAS_PERF_ADJUST_APPROVAL，按申请时间倒序。
  后端：GET /api/reports/amas-approvals（列表）/ /{perfAdjustNo}（详情：分配明细 + 审批流程）
  查询：客户名称 / 申请人姓名 模糊。状态列可点击 → 弹出子页面（原/调整分配比例 + 申请信息 + 审批流程）。
-->
<template>
  <main class="bp-crud perf-adjust" aria-labelledby="perf-adjust-title">
    <div class="page-h">
      <PageTitle id="perf-adjust-title" />
      <span class="desc">AMAS 业绩调整审批数据查询，按申请时间倒序</span>
    </div>

    <!-- 顶部查询项 -->
    <section class="card-section filter-bar" aria-label="业绩调整筛选">
    <el-form :model="q" inline class="filter-form" aria-label="业绩调整筛选" @submit.prevent>
      <el-form-item label="客户名称">
        <el-input v-model="q.custName" placeholder="模糊匹配" clearable style="width:180px" @keyup.enter="onSearch" />
      </el-form-item>
      <el-form-item label="申请人姓名">
        <el-input v-model="q.applyFullname" placeholder="模糊匹配" clearable style="width:150px" @keyup.enter="onSearch" />
      </el-form-item>
      <el-form-item label="审批状态">
        <el-select v-model="q.apprStatus" placeholder="全部" clearable style="width:130px">
          <el-option label="待审批" value="0" />
          <el-option label="已通过" value="1" />
          <el-option label="已拒绝" value="2" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button @click="onReset">重置</el-button>
      </el-form-item>
    </el-form>
    </section>

    <section class="card-section data-panel" aria-label="业绩调整列表" aria-describedby="perf-adjust-table-state" :aria-busy="loading ? 'true' : 'false'">
      <div class="toolbar">
        <div>
          <h2 id="perf-adjust-table-heading" class="section-title">业绩调整列表</h2>
          <p class="hint">查看申请人、客户、审批状态及分配调整详情。</p>
        </div>
        <p id="perf-adjust-table-state" class="table-state" role="status" aria-live="polite">
          {{ errorMessage || (loading ? '业绩调整列表加载中' : rows.length ? `共 ${total} 条记录` : '暂无业绩调整数据') }}
        </p>
      </div>
      <div v-if="errorMessage" class="error-state" role="alert">
        <span>{{ errorMessage }}</span>
        <el-button link type="primary" @click="load">重试</el-button>
      </div>
    <el-table :data="rows" v-loading="loading" border stripe size="default" empty-text="暂无业绩调整数据"
              aria-labelledby="perf-adjust-table-heading" aria-describedby="perf-adjust-table-state">
      <el-table-column type="index" label="序号" width="56" />
      <el-table-column label="申请人" min-width="120">
        <template #default="{ row }">
          <div class="main">{{ row.applyFullname || row.applyUsername || '-' }}</div>
          <div class="sub" v-if="row.applyUsername">{{ row.applyUsername }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="applyTime" label="申请时间" width="170" :formatter="dash" />
      <el-table-column label="客户" min-width="170">
        <template #default="{ row }">
          <div class="main">{{ row.custName || '-' }}</div>
          <div class="sub" v-if="row.custId">{{ row.custId }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="iouNo" label="账号/借据号" min-width="150" show-overflow-tooltip :formatter="dash" />
      <el-table-column label="申请类型" width="120">
        <template #default="{ row }">{{ APPLY_TYPE[row.applyType] || row.applyType || '-' }}</template>
      </el-table-column>
      <el-table-column label="业务类型" min-width="140">
        <template #default="{ row }">{{ businessTypeText(row.businessType) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :class="STATUS_TAG[row.apprStatus] || 'tag-info'" size="small">
            {{ APPR_STATUS[row.apprStatus] || row.apprStatus || '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="详情" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager" aria-label="业绩调整列表分页">
      <el-pagination
        background layout="total, prev, pager, next, sizes"
        :total="total" :current-page="page.pageNo" :page-size="page.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="onPage" @size-change="onSize" />
    </div>
    </section>

    <!-- 业绩调整详情子页面 -->
    <el-dialog v-model="dlg.show" class="bp-crud-dialog" title="业绩调整详情" width="900px" top="6vh" append-to-body v-loading="dlg.loading">
      <section class="detail-section" aria-label="业绩调整详情" :aria-busy="dlg.loading ? 'true' : 'false'">
      <p v-if="dlg.errorMessage" class="error-state" role="alert">{{ dlg.errorMessage }}</p>
      <!-- 原分配比例 -->
      <h3 class="sec-t">原分配比例</h3>
      <el-table :data="origAlloc" border stripe size="small" empty-text="无原分配数据">
        <el-table-column prop="username" label="工号" width="140" :formatter="dash" />
        <el-table-column prop="fullname" label="姓名" width="120" :formatter="dash" />
        <el-table-column prop="deptName" label="所属机构" min-width="180" show-overflow-tooltip :formatter="dash" />
        <el-table-column prop="ratio" label="比例" width="120" align="right" :formatter="dash" />
      </el-table>

      <!-- 调整分配比例 -->
      <h3 class="sec-t">调整分配比例</h3>
      <el-table :data="adjustAlloc" border stripe size="small" empty-text="无调整分配数据">
        <el-table-column prop="username" label="工号" width="140" :formatter="dash" />
        <el-table-column prop="fullname" label="姓名" width="120" :formatter="dash" />
        <el-table-column prop="deptName" label="所属机构" min-width="180" show-overflow-tooltip :formatter="dash" />
        <el-table-column prop="ratio" label="比例" width="120" align="right" :formatter="dash" />
      </el-table>

      <!-- 申请信息 -->
      <h3 class="sec-t">申请信息</h3>
      <el-descriptions :column="3" border size="small" label-width="110px">
        <el-descriptions-item label="账户余额-存款">{{ a.acctBalance || '-' }}</el-descriptions-item>
        <el-descriptions-item label="上月月均-存款">{{ a.avgLastMonth || '-' }}</el-descriptions-item>
        <el-descriptions-item label="年日均-存款">{{ a.avgYear || '-' }}</el-descriptions-item>
        <el-descriptions-item label="账户余额-贷款">{{ a.acctBalanceLs || '-' }}</el-descriptions-item>
        <el-descriptions-item label="上月月均-贷款">{{ a.avgLastMonthLs || '-' }}</el-descriptions-item>
        <el-descriptions-item label="年日均-贷款">{{ a.avgYearLs || '-' }}</el-descriptions-item>
        <el-descriptions-item label="调整理由" :span="3">{{ a.adjustExplain || '-' }}</el-descriptions-item>
      </el-descriptions>

      <!-- 审批流程 -->
      <h3 class="sec-t">审批流程</h3>
      <el-table :data="apprRecords" border stripe size="small" empty-text="无审批记录">
        <el-table-column prop="apprName" label="审批名称" min-width="140" show-overflow-tooltip :formatter="dash" />
        <el-table-column label="审批人" min-width="130">
          <template #default="{ row }">
            <div class="main">{{ row.apprFullname || row.apprUsername || '-' }}</div>
            <div class="sub" v-if="row.apprUsername">{{ row.apprUsername }}</div>
          </template>
        </el-table-column>
        <el-table-column label="审批状态" width="100">
          <template #default="{ row }">
            <el-tag :class="REC_STATUS_TAG[row.apprStatus] || 'tag-info'" size="small">
              {{ REC_STATUS[row.apprStatus] || row.apprStatus || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="apprTime" label="审批时间" width="170" :formatter="dash" />
        <el-table-column prop="apprOpinion" label="审批意见" min-width="180" show-overflow-tooltip :formatter="dash" />
      </el-table>
      </section>
    </el-dialog>
  </main>
</template>

<script setup>
import { ref, reactive, computed } from 'vue';
import { listAmasApprovals, getAmasApprovalDetail } from '@/api/report';

// 申请类型：1,公司业绩调整；2,零售业绩调整
const APPLY_TYPE = { '1': '公司业绩调整', '2': '零售业绩调整' };
// 业务类型（多选逗号拼接）：1,存款 2,贷款 3,中收 4,结构性
const BUSINESS_TYPE = { '1': '存款', '2': '贷款', '3': '中收', '4': '结构性' };
// 主表审批状态：0,待审批；1,已通过；2,已拒绝
const APPR_STATUS = { '0': '待审批', '1': '已通过', '2': '已拒绝' };
const STATUS_TAG = { '0': 'tag-warning', '1': 'tag-success', '2': 'tag-danger' };
// 审批记录状态：0,待审批；1,通过；2,未通过
const REC_STATUS = { '0': '待审批', '1': '通过', '2': '未通过' };
const REC_STATUS_TAG = { '0': 'tag-warning', '1': 'tag-success', '2': 'tag-danger' };

// 空值统一显示 “-”
const dash = (row, col, v) => (v === null || v === undefined || v === '' ? '-' : v);

// 业务类型多选逗号拼接 → 中文逗号拼接
function businessTypeText(v) {
  if (!v) return '-';
  return String(v).split(/[,，]/).map(s => s.trim()).filter(Boolean)
    .map(c => BUSINESS_TYPE[c] || c).join('、') || '-';
}

const q = reactive({ custName: '', applyFullname: '', apprStatus: '' });
const page = reactive({ pageNo: 1, pageSize: 20 });
const rows = ref([]);
const total = ref(0);
const loading = ref(false);
const errorMessage = ref('');

async function load() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const params = {
      pageNo: page.pageNo, pageSize: page.pageSize,
      custName: q.custName || undefined,
      applyFullname: q.applyFullname || undefined,
      apprStatus: q.apprStatus || undefined,
    };
    const r = await listAmasApprovals(params);
    rows.value = r?.records || [];
    total.value = r?.total || 0;
  } catch {
    rows.value = [];
    total.value = 0;
    errorMessage.value = '业绩调整列表加载失败，请重试';
  } finally {
    loading.value = false;
  }
}
function onSearch() { page.pageNo = 1; load(); }
function onReset() {
  q.custName = ''; q.applyFullname = ''; q.apprStatus = '';
  page.pageNo = 1; load();
}
function onPage(p) { page.pageNo = p; load(); }
function onSize(s) { page.pageSize = s; page.pageNo = 1; load(); }

// ── 详情弹窗 ──────────────────────────────────────────────
const dlg = reactive({ show: false, loading: false, errorMessage: '' });
const detail = ref({ approval: {}, allocations: [], apprRecords: [] });
const a = computed(() => detail.value.approval || {});
const apprRecords = computed(() => detail.value.apprRecords || []);
// 原分配比例 IS_ORIGINAL=1，调整分配比例 IS_ORIGINAL=2
const origAlloc = computed(() => (detail.value.allocations || []).filter(x => String(x.isOriginal) === '1'));
const adjustAlloc = computed(() => (detail.value.allocations || []).filter(x => String(x.isOriginal) === '2'));

async function openDetail(row) {
  if (!row?.perfAdjustNo || dlg.loading) return;
  dlg.show = true;
  dlg.loading = true;
  dlg.errorMessage = '';
  try {
    detail.value = (await getAmasApprovalDetail(row.perfAdjustNo)) || { approval: {}, allocations: [], apprRecords: [] };
  } catch {
    detail.value = { approval: {}, allocations: [], apprRecords: [] };
    dlg.errorMessage = '业绩调整详情加载失败，请重试';
  } finally {
    dlg.loading = false;
  }
}

load();
</script>

<style lang="scss" scoped>
.perf-adjust { min-width: 0; }
.main { color: var(--color-text-strong); }
.sub { color: var(--color-text-muted); font-size: 12px; }
.link-cell { cursor: pointer; display: inline-block; }
.link-cell:hover { text-decoration: underline; }
.sec-t { color: var(--color-text-strong); font-size: 14px; margin: var(--space-4) 0 var(--space-2); padding-left: var(--space-2); border-left: 3px solid var(--color-brand-500); }
.sec-t:first-child { margin-top: 0; }
.error-state {
  align-items: center;
  background: var(--color-danger-bg);
  border: 1px solid var(--color-border);
  color: var(--color-danger-fg);
  display: flex;
  gap: var(--space-3);
  justify-content: space-between;
  margin-bottom: var(--space-3);
  padding: var(--space-2) var(--space-3);
}
</style>
