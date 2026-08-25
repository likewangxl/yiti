<template>
  <el-drawer v-model="visible" title="资产立项详情" size="min(860px, 94vw)" destroy-on-close>
    <div v-loading="loading" class="loan-detail" aria-live="polite">
      <div v-if="errorMessage" class="error-state" role="alert">{{ errorMessage }}</div>
      <template v-else-if="loan">
        <section class="detail-section">
          <div class="detail-heading">
            <div>
              <h2>{{ loan.applyNo || loan.id || '资产立项申请' }}</h2>
              <p>{{ loan.custInfo?.custName || loan.custInfo?.name || loan.custName || loan.custId || '-' }}</p>
            </div>
            <el-tag :type="statusType(loan.status)" effect="plain">{{ statusLabel(loan.status) }}</el-tag>
          </div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="客户">{{ loan.custInfo?.custName || loan.custInfo?.name || loan.custName || loan.custId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="归属机构">{{ loan.ownerOrgName || loan.ownerOrgId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="项目类型">{{ labelOf(projectTypeOptions, loan.projectType) }}</el-descriptions-item>
            <el-descriptions-item label="业务类型">{{ labelOf(bizTypeOptions, loan.bizType) }}</el-descriptions-item>
            <el-descriptions-item label="主要担保方式">{{ labelOf(guaranteeTypeOptions, loan.guaranteeType) }}</el-descriptions-item>
            <el-descriptions-item label="授信金额（万元）">{{ loan.creditAmount ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="授信敞口（万元）">{{ loan.creditExposureAmount ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建人">{{ loan.createdByName || loan.createdBy || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatTime(loan.createdTime) }}</el-descriptions-item>
            <el-descriptions-item label="流程实例">{{ loan.processInstanceId || '-' }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="detail-section" aria-label="流程节点">
          <h3>流程节点</h3>
          <el-table v-if="processNodes.length" :data="processNodes" size="small" border>
            <el-table-column prop="nodeName" label="节点" min-width="180" />
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column prop="assigneeName" label="处理人" width="120" />
            <el-table-column prop="finishTime" label="完成时间" min-width="160" />
          </el-table>
          <el-empty v-else description="暂无流程节点" />
        </section>

        <section class="detail-section" aria-label="审批历史">
          <h3>审批历史</h3>
          <el-table v-if="history.length" :data="history" size="small" border>
            <el-table-column prop="nodeName" label="节点" min-width="160" />
            <el-table-column prop="operatorName" label="处理人" width="120" />
            <el-table-column prop="action" label="动作" width="110" />
            <el-table-column prop="comment" label="意见" min-width="220" show-overflow-tooltip />
            <el-table-column prop="operateTime" label="时间" min-width="160" />
          </el-table>
          <el-empty v-else description="暂无审批历史" />
        </section>

        <section class="detail-section" aria-label="申请附件">
          <h3>申请附件</h3>
          <LeadAttachmentPreview :attachments="attachments" />
          <el-empty v-if="!attachments.length" description="暂无附件" />
        </section>
      </template>
      <el-empty v-else-if="!loading" description="暂无资产立项详情" />
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import LeadAttachmentPreview from '@/components/LeadAttachmentPreview.vue';
import { getLoanApplication, listLoanAttachments } from '@/api/businessApplication';
import { getProcessHistory, getProcessNodes } from '@/api/workflow';
import { useDict } from '@/composables/useDict';

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  loanId: { type: String, default: '' },
  processInstanceId: { type: String, default: '' }
});
const emit = defineEmits(['update:modelValue', 'loaded']);
const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
});

const loan = ref(null);
const attachments = ref([]);
const processNodes = ref([]);
const history = ref([]);
const loading = ref(false);
const errorMessage = ref('');
const { options: projectTypeOptions } = useDict('PROJECT_TYPE');
const { options: bizTypeOptions } = useDict('BIZ_TYPE');
const { options: guaranteeTypeOptions } = useDict('GUARANTEE_TYPE');

const statusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  IN_APPROVAL: { label: '审批中', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' },
  REJECTED: { label: '驳回结束', type: 'danger' },
  CANCELLED: { label: '已撤回', type: 'info' }
};
const statusLabel = status => statusMap[status]?.label || status || '-';
const statusType = status => statusMap[status]?.type || 'info';
const formatTime = value => value ? String(value).replace('T', ' ').slice(0, 19) : '-';

function labelOf(options, value) {
  return options.value?.find(item => item.value === value)?.label || value || '-';
}

function nodeRows(result) {
  const rows = Array.isArray(result) ? result : (result?.nodes || result?.records || []);
  return rows.map(row => ({
    ...row,
    nodeName: row.nodeName || row.name || row.nodeKey || '-',
    finishTime: formatTime(row.finishTime || row.endTime)
  }));
}

function historyRows(result) {
  const rows = Array.isArray(result) ? result : (result?.records || []);
  return rows.map(row => ({
    ...row,
    comment: row.comment || row.opinion || '-',
    operateTime: formatTime(row.operateTime)
  }));
}

async function load() {
  if (!props.loanId) {
    loan.value = null;
    attachments.value = [];
    processNodes.value = [];
    history.value = [];
    return;
  }
  loading.value = true;
  errorMessage.value = '';
  try {
    // 贷款详情与业务附件互不依赖，先并行；拿到流程实例后再并行读取节点和历史。
    const [loanResult, fileResult] = await Promise.all([
      getLoanApplication(props.loanId),
      listLoanAttachments(props.loanId)
    ]);
    loan.value = loanResult || null;
    attachments.value = Array.isArray(fileResult) ? fileResult : (fileResult?.records || []);
    const processId = props.processInstanceId || loan.value?.processInstanceId;
    if (processId) {
      const [nodesResult, historyResult] = await Promise.all([
        getProcessNodes(processId),
        getProcessHistory(processId)
      ]);
      processNodes.value = nodeRows(nodesResult);
      history.value = historyRows(historyResult);
    } else {
      processNodes.value = [];
      history.value = [];
    }
    emit('loaded', loan.value);
  } catch (error) {
    loan.value = null;
    attachments.value = [];
    processNodes.value = [];
    history.value = [];
    errorMessage.value = error?.message || '资产立项详情加载失败，请重试';
  } finally {
    loading.value = false;
  }
}

watch(() => [props.modelValue, props.loanId, props.processInstanceId], ([isOpen]) => {
  if (isOpen) load();
}, { immediate: true });

defineExpose({ loan, attachments, processNodes, history, load });
</script>

<style scoped lang="scss">
.loan-detail { display: flex; flex-direction: column; gap: 16px; min-height: 220px; }
.detail-section { padding: 14px 16px; border: 1px solid var(--color-border); border-radius: var(--radius-control); background: var(--color-surface); }
.detail-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.detail-heading h2 { margin: 0; color: var(--color-text-strong); font-size: 16px; line-height: 24px; }
.detail-heading p { margin: 2px 0 0; color: var(--color-text-muted); font-size: 12px; }
.detail-section h3 { margin: 0 0 10px; color: var(--color-text-strong); font-size: 14px; }
.error-state { padding: 24px; color: var(--color-danger-fg); text-align: center; }
</style>
