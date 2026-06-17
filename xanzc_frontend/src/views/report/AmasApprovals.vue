<!--
  业绩分配审批历史 —— 列表页（报表分析）
  主表：AMAS_PERF_ADJUST_APPROVAL，按申请时间倒序。
  后端：GET /api/reports/amas-approvals （AmasApprovalHistoryController.list）
  点击「详情」→ /report/amas-approvals/:perfAdjustNo
-->
<template>
  <div class="amas-approvals">
    <div class="page-h">
      <h1>业绩分配查询</h1>
      <span class="desc">历史业绩调整（AMAS）与业绩调整（平台）两类申请查询，按申请时间倒序</span>
    </div>

    <el-tabs v-model="activeTab" class="page-tabs">
    <el-tab-pane label="业绩调整" name="adjust">
      <el-form :model="q2" inline class="filter-form" @submit.prevent>
        <el-form-item label="申请人工号">
          <el-input v-model="q2.applicant" placeholder="精确" clearable style="width:150px" />
        </el-form-item>
        <el-form-item label="客户">
          <el-input v-model="q2.custKeyword" placeholder="客户号/客户名" clearable style="width:180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="q2.status" placeholder="全部" clearable style="width:130px">
            <el-option label="审批中" value="IN_APPROVAL" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已驳回" value="REJECTED" />
            <el-option label="已撤回" value="WITHDRAWN" />
          </el-select>
        </el-form-item>
        <el-form-item label="申请时间">
          <el-date-picker
            v-model="dateRange2" type="daterange" value-format="YYYY-MM-DD"
            start-placeholder="起" end-placeholder="止" style="width:240px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch2">查询</el-button>
          <el-button @click="onReset2">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="rows2" v-loading="loading2" border stripe size="default">
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column label="申请人" min-width="130">
          <template #default="{ row }">
            <template v-if="row.createdByName">{{ row.createdByName }}<span class="sub">（{{ row.createdByNo || row.createdBy }}）</span></template>
            <template v-else>{{ row.createdByNo || row.createdBy || '-' }}</template>
          </template>
        </el-table-column>
        <el-table-column label="客户类型" width="100">
          <template #default="{ row }">{{ CUST_TYPE[row.custType] || row.custType || '-' }}</template>
        </el-table-column>
        <el-table-column label="客户" min-width="160">
          <template #default="{ row }">
            {{ row.custName || '-' }}<span class="sub">{{ row.custId ? '（' + row.custId + '）' : '' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="accountNo" label="账号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="createdTime" label="申请时间" width="170" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="ALC_TAG[row.status] || 'info'" size="small">
              {{ ALC_STATUS[row.status] || row.status || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goAllocDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          background layout="total, prev, pager, next, sizes"
          :total="total2" :current-page="page2.pageNo" :page-size="page2.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          @current-change="onPage2" @size-change="onSize2" />
      </div>
    </el-tab-pane>

    <el-tab-pane label="历史业绩调整" name="history">
    <!-- 顶部查询项 -->
    <el-form :model="q" inline class="filter-form" @submit.prevent>
      <el-form-item label="申请人工号">
        <el-input v-model="q.applyUsername" placeholder="精确" clearable style="width:150px" />
      </el-form-item>
      <el-form-item label="客户">
        <el-input v-model="q.custKeyword" placeholder="客户号/客户名" clearable style="width:180px" />
      </el-form-item>
      <el-form-item label="审批状态">
        <el-select v-model="q.apprStatus" placeholder="全部" clearable style="width:130px">
          <el-option label="待审批" value="0" />
          <el-option label="已通过" value="1" />
          <el-option label="已拒绝" value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="申请时间">
        <el-date-picker
          v-model="dateRange" type="daterange" value-format="YYYY-MM-DD"
          start-placeholder="起" end-placeholder="止" style="width:240px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button @click="onReset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="rows" v-loading="loading" border stripe size="default">
      <el-table-column type="index" label="序号" width="60" />
      <el-table-column label="申请人" min-width="130">
        <template #default="{ row }">
          {{ row.applyFullname || '-' }}<span class="sub">（{{ row.applyUsername || '-' }}）</span>
        </template>
      </el-table-column>
      <el-table-column label="调整类型" width="110">
        <template #default="{ row }">{{ APPLY_TYPE[row.applyType] || row.applyType || '-' }}</template>
      </el-table-column>
      <el-table-column label="客户" min-width="160">
        <template #default="{ row }">
          {{ row.custName || '-' }}<span class="sub">{{ row.custId ? '（' + row.custId + '）' : '' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="iouNo" label="账号/借据号" min-width="140" show-overflow-tooltip />
      <el-table-column prop="applyTime" label="申请时间" width="170" />
      <el-table-column label="审批状态" width="100">
        <template #default="{ row }">
          <el-tag :type="STATUS_TAG[row.apprStatus] || 'info'" size="small">
            {{ APPR_STATUS[row.apprStatus] || row.apprStatus || '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        background layout="total, prev, pager, next, sizes"
        :total="total" :current-page="page.pageNo" :page-size="page.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="onPage" @size-change="onSize" />
    </div>
    </el-tab-pane>
    </el-tabs>

    <!-- 查看调整申请（共享只读组件，业绩调整页与本页共用，内容一致） -->
    <AllocAdjustViewDialog v-model="viewShow" :apply-id="viewId" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { listAmasApprovals, listAllocAdjustApplies } from '@/api/report';
import AllocAdjustViewDialog from '@/components/AllocAdjustViewDialog.vue';

const router = useRouter();
const route = useRoute();

const activeTab = ref('adjust');

// 历史业绩调整（AMAS）字典
const APPLY_TYPE = { '1': '公司业绩', '2': '零售业绩' };
const APPR_STATUS = { '0': '待审批', '1': '已通过', '2': '已拒绝' };
const STATUS_TAG = { '0': 'warning', '1': 'success', '2': 'danger' };
// 业绩调整（PERF_ALLOC_ADJUST_APPLY）字典
const CUST_TYPE = { CORP: '公司', RETAIL: '零售' };
const ALC_STATUS = { DRAFT: '草稿', IN_APPROVAL: '审批中', APPROVED: '已通过', REJECTED: '已驳回', WITHDRAWN: '已撤回' };
const ALC_TAG = { DRAFT: 'info', IN_APPROVAL: 'warning', APPROVED: 'success', REJECTED: 'danger', WITHDRAWN: 'info' };

// ---- 历史业绩调整 Tab 状态 ----
const q = reactive({ applyUsername: '', custKeyword: '', apprStatus: '' });
const dateRange = ref([]);
const page = reactive({ pageNo: 1, pageSize: 20 });
const rows = ref([]);
const total = ref(0);
const loading = ref(false);

// ---- 业绩调整 Tab 状态 ----
const q2 = reactive({ applicant: '', custKeyword: '', status: '' });
const dateRange2 = ref([]);
const page2 = reactive({ pageNo: 1, pageSize: 20 });
const rows2 = ref([]);
const total2 = ref(0);
const loading2 = ref(false);
let loaded1 = false; // 历史业绩调整 懒加载标志
let loaded2 = false; // 业绩调整 懒加载标志

// 把两个 Tab 的页码+筛选 + 当前 Tab 写进 URL，使「详情→返回」能还原状态（不回第 1 页/不丢 Tab）
function syncUrl() {
  const query = {};
  if (activeTab.value !== 'adjust') query.tab = activeTab.value;
  // 历史
  if (q.applyUsername) query.applyUsername = q.applyUsername;
  if (q.custKeyword) query.custKeyword = q.custKeyword;
  if (q.apprStatus) query.apprStatus = q.apprStatus;
  if (dateRange.value?.[0]) query.ds = dateRange.value[0];
  if (dateRange.value?.[1]) query.de = dateRange.value[1];
  if (page.pageNo !== 1) query.pageNo = String(page.pageNo);
  if (page.pageSize !== 20) query.pageSize = String(page.pageSize);
  // 业绩调整
  if (q2.applicant) query.a_ap = q2.applicant;
  if (q2.custKeyword) query.a_ck = q2.custKeyword;
  if (q2.status) query.a_st = q2.status;
  if (dateRange2.value?.[0]) query.a_ds = dateRange2.value[0];
  if (dateRange2.value?.[1]) query.a_de = dateRange2.value[1];
  if (page2.pageNo !== 1) query.a_pn = String(page2.pageNo);
  if (page2.pageSize !== 20) query.a_ps = String(page2.pageSize);
  router.replace({ query });
}

function restoreFromUrl() {
  const qq = route.query;
  if (qq.tab) activeTab.value = qq.tab;
  q.applyUsername = qq.applyUsername || '';
  q.custKeyword = qq.custKeyword || '';
  q.apprStatus = qq.apprStatus || '';
  dateRange.value = (qq.ds || qq.de) ? [qq.ds || '', qq.de || ''] : [];
  page.pageNo = qq.pageNo ? Number(qq.pageNo) : 1;
  page.pageSize = qq.pageSize ? Number(qq.pageSize) : 20;
  q2.applicant = qq.a_ap || '';
  q2.custKeyword = qq.a_ck || '';
  q2.status = qq.a_st || '';
  dateRange2.value = (qq.a_ds || qq.a_de) ? [qq.a_ds || '', qq.a_de || ''] : [];
  page2.pageNo = qq.a_pn ? Number(qq.a_pn) : 1;
  page2.pageSize = qq.a_ps ? Number(qq.a_ps) : 20;
}

// ---- 历史业绩调整 加载 ----
async function load() {
  loaded1 = true;
  syncUrl();
  loading.value = true;
  try {
    const params = {
      pageNo: page.pageNo, pageSize: page.pageSize,
      applyUsername: q.applyUsername || undefined,
      custKeyword: q.custKeyword || undefined,
      apprStatus: q.apprStatus || undefined,
      applyTimeStart: dateRange.value?.[0] ? dateRange.value[0] + ' 00:00:00' : undefined,
      applyTimeEnd: dateRange.value?.[1] ? dateRange.value[1] + ' 23:59:59' : undefined,
    };
    const r = await listAmasApprovals(params);
    rows.value = r?.records || [];
    total.value = r?.total || 0;
  } finally {
    loading.value = false;
  }
}
function onSearch() { page.pageNo = 1; load(); }
function onReset() {
  q.applyUsername = ''; q.custKeyword = ''; q.apprStatus = '';
  dateRange.value = []; page.pageNo = 1; load();
}
function onPage(p) { page.pageNo = p; load(); }
function onSize(s) { page.pageSize = s; page.pageNo = 1; load(); }
function goDetail(row) {
  router.push({ name: 'ReportAmasApprovalDetail', params: { perfAdjustNo: row.perfAdjustNo } });
}

// ---- 业绩调整 加载 ----
async function load2() {
  loaded2 = true;
  syncUrl();
  loading2.value = true;
  try {
    const params = {
      pageNo: page2.pageNo, pageSize: page2.pageSize,
      applicant: q2.applicant || undefined,
      custKeyword: q2.custKeyword || undefined,
      status: q2.status || undefined,
      createdStart: dateRange2.value?.[0] ? dateRange2.value[0] + ' 00:00:00' : undefined,
      createdEnd: dateRange2.value?.[1] ? dateRange2.value[1] + ' 23:59:59' : undefined,
    };
    const r = await listAllocAdjustApplies(params);
    rows2.value = r?.records || [];
    total2.value = r?.total || 0;
  } finally {
    loading2.value = false;
  }
}
function onSearch2() { page2.pageNo = 1; load2(); }
function onReset2() {
  q2.applicant = ''; q2.custKeyword = ''; q2.status = '';
  dateRange2.value = []; page2.pageNo = 1; load2();
}
function onPage2(p) { page2.pageNo = p; load2(); }
function onSize2(s) { page2.pageSize = s; page2.pageNo = 1; load2(); }
// 详情：本页直接打开共享「查看调整申请」弹框（与业绩调整页同一组件，内容一致）
const viewShow = ref(false);
const viewId = ref('');
function goAllocDetail(row) {
  viewId.value = row.id;
  viewShow.value = true;
}

// 切 Tab 时同步 URL + 首次懒加载目标 Tab
watch(activeTab, (t) => {
  syncUrl();
  if (t === 'history' && !loaded1) load();
  if (t === 'adjust' && !loaded2) load2();
});

onMounted(() => {
  restoreFromUrl();
  if (activeTab.value === 'history') load(); else load2();
});
</script>

<style lang="scss" scoped>
.amas-approvals { padding: 4px 2px; }
.page-h { margin-bottom: 12px;
  h1 { font-size: 18px; margin: 0; display: inline-block; }
  .desc { font-size: 12px; color: #909399; margin-left: 12px; }
}
.filter-form { margin-bottom: 8px; }
.sub { color: #909399; font-size: 12px; }
.pager { margin-top: 12px; display: flex; justify-content: flex-end; }
.sec-t { font-size: 14px; margin: 16px 0 8px; padding-left: 8px; border-left: 3px solid #409eff; }
.node { margin-left: 8px; color: #303133; font-weight: 500; }
.approval-meta { font-size: 12px; color: #606266; margin-top: 4px; }
.approval-opinion { font-size: 12px; color: #303133; margin-top: 2px; }
</style>
