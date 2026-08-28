<template>
  <el-drawer
    v-model="visible"
    class="bp-crud-dialog marketing-lead-import-detail-drawer"
    :title="`导入明细 · ${batch?.sourceFileName || batch?.batchNo || ''}`"
    size="min(1000px, 96vw)"
    destroy-on-close
  >
    <template v-if="batch">
      <el-descriptions :column="4" border>
        <el-descriptions-item label="批次号">{{ batch.batchNo || batch.id || '-' }}</el-descriptions-item>
        <el-descriptions-item label="导入时间">{{ formatTime(batch.importTime) }}</el-descriptions-item>
        <el-descriptions-item label="导入人">{{ batch.importEmpId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="导入状态"><el-tag :type="statusType(batch.importStatus)">{{ statusLabel(batch.importStatus) }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="总行数">{{ batch.totalCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="有效行">{{ batch.validCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="失败行">{{ failureCount(batch) }}</el-descriptions-item>
        <el-descriptions-item label="已生成线索">{{ batch.generatedLeadCount ?? '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="detail-toolbar">
        <div class="toolbar-actions">
          <el-button :loading="downloading === 'source'" @click="downloadSource">下载原始文件</el-button>
        </div>
        <span class="failure-hint">失败、异常、警告明细优先展示</span>
      </div>

      <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon class="detail-error" />
      <el-table :data="sortedDetails" v-loading="loading" border stripe>
        <el-table-column prop="rowNo" label="文件行号" width="90" />
        <el-table-column prop="custName" label="企业名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="unifiedCreditCode" label="统一社会信用代码" min-width="190" show-overflow-tooltip />
        <el-table-column label="校验结果" width="105"><template #default="{ row }"><el-tag :type="detailType(row.validationStatus)">{{ detailStatus(row.validationStatus) }}</el-tag></template></el-table-column>
        <el-table-column label="失败原因" min-width="260" show-overflow-tooltip><template #default="{ row }">{{ reasonOf(row) }}</template></el-table-column>
        <el-table-column label="处理结果" width="110"><template #default="{ row }">{{ handlingStatus(row.handlingStatus) }}</template></el-table-column>
      </el-table>
      <el-empty v-if="!loading && !sortedDetails.length" description="暂无导入明细" :image-size="64" />

      <div v-if="batch.importStatus === 'WAITING_CONFIRM'" class="confirm-panel">
        <el-alert title="该批次部分数据需要人工确认。请选择仅处理正常数据，或放弃当前批次并修改文件后重新导入。" type="warning" :closable="false" show-icon />
        <div class="confirm-actions">
          <el-button type="primary" @click="emit('confirm-action', 'PROCESS_VALID')">只处理正常数据</el-button>
          <el-button type="warning" @click="emit('confirm-action', 'ABANDON_REIMPORT')">放弃并重新导入</el-button>
        </div>
      </div>
    </template>
    <el-empty v-else description="请选择导入批次" :image-size="64" />
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import {
  downloadLeadImportSourceFile,
  getLeadImportBatchDetails,
} from '@/api/marketingManagement';

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  batch: { type: Object, default: null },
});
const emit = defineEmits(['update:modelValue', 'confirm-action']);
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) });
const details = ref([]);
const loading = ref(false);
const errorMessage = ref('');
const downloading = ref('');

const failureOrder = { REJECTED: 0, ERROR: 1, WARNING: 2, VALID: 3 };
const sortedDetails = computed(() => [...details.value].sort((a, b) => {
  const aOrder = failureOrder[a.validationStatus] ?? 9;
  const bOrder = failureOrder[b.validationStatus] ?? 9;
  return aOrder - bOrder || Number(a.rowNo || 0) - Number(b.rowNo || 0);
}));

watch(() => [props.modelValue, props.batch?.id], ([opened, id]) => {
  if (opened && id) loadDetails(id);
}, { immediate: true });

async function loadDetails(batchId) {
  loading.value = true;
  errorMessage.value = '';
  try {
    const result = await getLeadImportBatchDetails(batchId, { pageNo: 1, pageSize: 100 });
    details.value = result?.records || result?.list || result?.data || [];
  } catch (error) {
    details.value = [];
    errorMessage.value = `导入明细加载失败：${error?.message || '请稍后重试'}`;
  } finally {
    loading.value = false;
  }
}

const failureCount = batch => Number(batch?.rejectedCount || 0) + Number(batch?.errorCount || 0) + Number(batch?.warningCount || 0);
const reasonOf = row => row.errorMessage || row.warningMessage || row.reason || '-';
const statusLabel = value => ({ IMPORTING: '处理中', COMPLETED: '已完成', WAITING_CONFIRM: '待确认', ALL_FAILED: '全部失败', ABANDONED: '已放弃' }[value] || value || '-');
const statusType = value => ({ IMPORTING: 'warning', COMPLETED: 'success', WAITING_CONFIRM: 'warning', ALL_FAILED: 'danger', ABANDONED: 'info' }[value] || 'info');
const detailStatus = value => ({ VALID: '正常', WARNING: '需确认', REJECTED: '失败', ERROR: '错误' }[value] || value || '-');
const detailType = value => ({ VALID: 'success', WARNING: 'warning', REJECTED: 'danger', ERROR: 'danger' }[value] || 'info');
const handlingStatus = value => ({ PENDING: '待处理', GENERATED: '已生成线索', SKIPPED: '已跳过' }[value] || value || '-');
const formatTime = value => value ? String(value).replace('T', ' ').slice(0, 19) : '-';

function downloadBlob(blob, filename) {
  if (!blob) throw new Error('文件内容为空');
  const source = blob instanceof Blob ? blob : new Blob([blob]);
  const url = URL.createObjectURL(source);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

async function downloadSource() {
  if (!props.batch?.id || downloading.value) return;
  downloading.value = 'source';
  try {
    const blob = await downloadLeadImportSourceFile(props.batch.id);
    downloadBlob(blob, props.batch.sourceFileName || `线索导入_${props.batch.id}`);
  } catch (error) {
    ElMessage.error(`原始文件下载失败：${error?.message || '请稍后重试'}`);
  } finally {
    downloading.value = '';
  }
}

</script>

<style scoped lang="scss">
.detail-toolbar { align-items: center; display: flex; justify-content: space-between; gap: 14px; margin: 20px 0 12px; }
.toolbar-actions { display: flex; gap: 8px; }
.failure-hint { color: #909399; font-size: 12px; }
.detail-error { margin-bottom: 12px; }
.confirm-panel { margin-top: 18px; }
.confirm-actions { display: flex; gap: 10px; justify-content: flex-end; margin-top: 14px; }
@media (max-width: 640px) { .detail-toolbar { align-items: flex-start; flex-direction: column; } }
</style>
