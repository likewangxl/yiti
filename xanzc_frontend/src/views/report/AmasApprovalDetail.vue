<!--
  业绩分配审批历史 —— 详情页（报表分析）
  后端：GET /api/reports/amas-approvals/:perfAdjustNo （AmasApprovalHistoryController.detail）
  内容：① 申请/审批主信息 ② 业绩分配数据(AMAS_PERFORMANCE_ALLOCATION) ③ 审批流程(AMAS_APPR_RECORD，序号倒序)
-->
<template>
  <main class="bp-crud amas-detail" aria-labelledby="amas-detail-title" :aria-busy="loading ? 'true' : 'false'">
    <header class="page-h">
      <div class="left">
        <h1 id="amas-detail-title" class="page-title">业绩分配审批详情</h1>
        <span class="desc">{{ perfAdjustNo }}</span>
      </div>
      <el-button @click="goBack">返回查询</el-button>
    </header>

    <div v-if="errorMessage" class="error-state" role="alert"><span>{{ errorMessage }}</span><el-button link type="primary" @click="loadDetail">重试</el-button></div>

    <!-- ① 申请/审批主信息 -->
    <section class="card-section data-panel" aria-labelledby="amas-application-title">
    <h2 id="amas-application-title" class="section-title">申请信息</h2>
    <el-descriptions :column="3" border size="default" v-loading="loading">
      <el-descriptions-item label="业绩调整编号">{{ a.perfAdjustNo || '-' }}</el-descriptions-item>
      <el-descriptions-item label="申请人">{{ a.applyFullname || '-' }}（{{ a.applyUsername || '-' }}）</el-descriptions-item>
      <el-descriptions-item label="调整类型">{{ APPLY_TYPE[a.applyType] || a.applyType || '-' }}</el-descriptions-item>
      <el-descriptions-item label="申请时间">{{ a.applyTime || '-' }}</el-descriptions-item>
      <el-descriptions-item label="审批状态">
        <el-tag :type="STATUS_TAG[a.apprStatus] || 'info'" size="small">
          {{ APPR_STATUS[a.apprStatus] || a.apprStatus || '-' }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="审批进度">{{ a.apprProgress || '-' }}</el-descriptions-item>
      <el-descriptions-item label="客户">{{ a.custName || '-' }}（{{ a.custId || '-' }}）</el-descriptions-item>
      <el-descriptions-item label="账号/借据号">{{ a.iouNo || '-' }}</el-descriptions-item>
      <el-descriptions-item label="调整类型(规则)">{{ APPLY_RULE[a.applyRule] || a.applyRule || '-' }}</el-descriptions-item>
      <el-descriptions-item label="账户余额">{{ a.acctBalance || '-' }}</el-descriptions-item>
      <el-descriptions-item label="上月月均">{{ a.avgLastMonth || '-' }}</el-descriptions-item>
      <el-descriptions-item label="年日均">{{ a.avgYear || '-' }}</el-descriptions-item>
      <el-descriptions-item label="调整理由" :span="3">{{ a.adjustExplain || '-' }}</el-descriptions-item>
    </el-descriptions>
    </section>

    <!-- ② 业绩分配数据 -->
    <section class="card-section data-panel" aria-labelledby="amas-allocation-title">
      <h2 id="amas-allocation-title" class="section-title">业绩分配数据</h2>
      <el-table :data="allocations" border stripe size="default" empty-text="无分配数据" aria-labelledby="amas-allocation-title">
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="username" label="分配人工号" width="130" />
        <el-table-column prop="fullname" label="姓名" width="110" />
        <el-table-column prop="dept" label="部门" width="110" />
        <el-table-column prop="deptName" label="部门名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="ratio" label="分配比例" width="110" />
        <el-table-column label="是否原分配" width="110">
          <template #default="{ row }">{{ IS_ORIGINAL[row.isOriginal] || row.isOriginal || '-' }}</template>
        </el-table-column>
        <el-table-column label="审核状态" width="100">
          <template #default="{ row }">{{ ALLOC_STATUS[row.apprStatus] || row.apprStatus || '-' }}</template>
        </el-table-column>
      </el-table>
    </section>

    <!-- ③ 审批流程数据（序号倒序） -->
    <section class="card-section data-panel" aria-labelledby="amas-record-title">
      <h2 id="amas-record-title" class="section-title">审批流程</h2>
      <el-table :data="apprRecords" border stripe size="default" empty-text="无审批记录" aria-labelledby="amas-record-title">
        <el-table-column prop="apprSeq" label="序号" width="80" />
        <el-table-column prop="apprName" label="审批节点" min-width="140" />
        <el-table-column label="审批人" min-width="130">
          <template #default="{ row }">
            {{ row.apprFullname || '-' }}<span class="sub">（{{ row.apprUsername || '-' }}）</span>
          </template>
        </el-table-column>
        <el-table-column label="审批状态" width="100">
          <template #default="{ row }">
            <el-tag :type="STATUS_TAG[row.apprStatus] || 'info'" size="small">
              {{ APPR_STATUS[row.apprStatus] || row.apprStatus || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="apprTime" label="审批时间" width="170" />
        <el-table-column prop="apprOpinion" label="审批意见" min-width="200" show-overflow-tooltip />
      </el-table>
    </section>
  </main>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getAmasApprovalDetail } from '@/api/report';

const route = useRoute();
const router = useRouter();
const perfAdjustNo = route.params.perfAdjustNo;

const APPLY_TYPE = { '1': '公司业绩', '2': '零售业绩' };
const APPLY_RULE = { '1': '账号调整', '2': '规则调整' };
const APPR_STATUS = { '0': '待审批', '1': '已通过', '2': '已拒绝' };
const STATUS_TAG = { '0': 'warning', '1': 'success', '2': 'danger' };
const IS_ORIGINAL = { '1': '是', '2': '否' };
const ALLOC_STATUS = { '0': '待审批', '1': '同意', '2': '拒绝' };

const detail = ref({ approval: {}, allocations: [], apprRecords: [] });
const a = computed(() => detail.value.approval || {});
const allocations = computed(() => detail.value.allocations || []);
const apprRecords = computed(() => detail.value.apprRecords || []);
const loading = ref(false);
const errorMessage = ref('');

function goBack() { router.back(); }

async function loadDetail() {
  loading.value = true;
  errorMessage.value = '';
  try {
    detail.value = await getAmasApprovalDetail(perfAdjustNo);
  } catch (e) {
    detail.value = { approval: {}, allocations: [], apprRecords: [] };
    errorMessage.value = e?.message || '审批详情加载失败，请重试';
  } finally {
    loading.value = false;
  }
}

onMounted(loadDetail);
</script>

<style lang="scss" scoped>
.amas-detail { min-width: 0; }
.page-h { display: flex; align-items: center; justify-content: space-between;
  .left { display: flex; align-items: baseline; }
  .desc { font-size: 12px; color: var(--color-text-muted); margin-left: var(--space-3); }
}
.sub { color: var(--color-text-muted); font-size: 12px; }
.error-state { align-items: center; background: var(--color-danger-bg); border: 1px solid var(--color-border); color: var(--color-danger-fg); display: flex; gap: var(--space-3); justify-content: space-between; padding: var(--space-3); }
</style>
