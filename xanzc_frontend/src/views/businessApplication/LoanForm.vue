<template>
  <el-dialog
    v-model="visible"
    class="bp-crud-dialog loan-form-dialog"
    :title="editingId ? '编辑资产立项草稿' : '新建资产立项'"
    width="min(900px, 94vw)"
    :close-on-click-modal="false"
    destroy-on-close
    @closed="resetForm"
  >
    <el-alert
      title="客户必须为已审批通过的公司类客户；附件需先保存草稿后关联上传。"
      type="info"
      :closable="false"
      show-icon
      class="form-alert"
    />
    <el-form ref="formRef" :model="form" label-position="top" class="loan-form" @submit.prevent>
      <section class="form-section">
        <h2>申请对象</h2>
        <div class="form-grid">
          <el-form-item label="客户" required>
            <el-select
              v-model="form.custId"
              filterable
              remote
              reserve-keyword
              clearable
              :remote-method="searchCustomers"
              :loading="customerLoading"
              :disabled="customerLocked"
              placeholder="输入客户名称、客户号搜索"
              style="width: 100%"
              @change="onCustomerChange"
            >
              <el-option
                v-for="customer in customerOptions"
                :key="customer.id"
                :label="customerLabel(customer)"
                :value="customer.id"
              />
            </el-select>
            <p v-if="form.sourceTouchTaskId" class="field-hint">
              {{ customerLocked ? '该申请来源于触达任务，客户已锁定。' : '请选择该触达任务对应客户，服务端将校验客户一致性。' }}
            </p>
          </el-form-item>
          <el-form-item label="客户名称">
            <el-input :model-value="form.custName || '请选择客户'" disabled />
          </el-form-item>
          <el-form-item label="来源触达任务">
            <el-input v-model="form.sourceTouchTaskId" disabled placeholder="非必填" />
          </el-form-item>
        </div>
      </section>

      <section class="form-section">
        <h2>业务信息</h2>
        <div class="form-grid">
          <el-form-item label="项目类型" required>
            <el-select v-model="form.projectType" placeholder="请选择项目类型" style="width: 100%">
              <el-option v-for="item in projectTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="业务类型" required>
            <el-select v-model="form.bizType" placeholder="请选择业务类型" style="width: 100%">
              <el-option v-for="item in bizTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="主要担保方式" required>
            <el-select v-model="form.guaranteeType" placeholder="请选择担保方式" style="width: 100%">
              <el-option v-for="item in guaranteeTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="授信金额（万元）" required>
            <el-input-number v-model="form.creditAmount" :min="0" :precision="2" :controls="false" style="width: 100%" />
          </el-form-item>
          <el-form-item label="授信敞口金额（万元）" required>
            <el-input-number v-model="form.creditExposureAmount" :min="0" :precision="2" :controls="false" style="width: 100%" />
          </el-form-item>
        </div>
        <p class="field-hint">授信金额必须大于 0，授信敞口金额不得大于授信金额。</p>
      </section>

      <section class="form-section">
        <h2>附件</h2>
        <el-upload
          v-model:file-list="fileList"
          action="#"
          :auto-upload="false"
          multiple
          :limit="20"
          accept=".jpg,.jpeg,.png,.gif,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.zip,.rar"
          :before-remove="beforeFileRemove"
          :on-change="onFileChange"
          :on-remove="onFileRemove"
        >
          <el-button>选择附件</el-button>
          <template #tip><div class="el-upload__tip">保存草稿后上传，单文件不超过 50MB。</div></template>
        </el-upload>
      </section>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button :loading="saving" :disabled="saving" @click="saveDraft">保存草稿</el-button>
      <el-button type="primary" :loading="submitting" :disabled="saving || submitting" @click="saveAndSubmit">保存并提交</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, nextTick, reactive, ref, toRaw, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  createLoanApplication,
  listLoanCustomerCandidates,
  submitLoanApplication,
  updateLoanApplication,
  uploadLoanAttachment
} from '@/api/businessApplication';
import { useDict } from '@/composables/useDict';

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  loan: { type: Object, default: null },
  sourceTouchTaskId: { type: String, default: '' }
});
const emit = defineEmits(['update:modelValue', 'saved']);

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
});
const formRef = ref(null);
const editingId = ref('');
const saving = ref(false);
const submitting = ref(false);
const customerLoading = ref(false);
const customerOptions = ref([]);
const fileList = ref([]);
const pendingFiles = ref([]);
const pendingFileItems = ref([]);
const uploadedPendingIds = new Map();
const MAX_ATTACHMENT_SIZE = 50 * 1024 * 1024;

const { options: projectTypeOptions } = useDict('PROJECT_TYPE');
const { options: bizTypeOptions } = useDict('BIZ_TYPE');
const { options: guaranteeTypeOptions } = useDict('GUARANTEE_TYPE');

const emptyForm = () => ({
  custId: '',
  custName: '',
  sourceTouchTaskId: props.sourceTouchTaskId || '',
  projectType: '',
  bizType: '',
  guaranteeType: '',
  creditAmount: undefined,
  creditExposureAmount: undefined,
  attachmentIds: []
});
const form = reactive(emptyForm());
const customerLocked = computed(() => Boolean(form.sourceTouchTaskId && form.custId));

function customerLabel(customer) {
  return `${customer?.name || customer?.custName || customer?.id || '-'}（${customer?.custNo || customer?.id || '-'}）`;
}

function normalizeCustomer(customer) {
  if (!customer) return null;
  return {
    ...customer,
    id: customer.id || customer.custId || customer.custNo,
    name: customer.name || customer.custName || customer.customerName || customer.id
  };
}

function isEligibleCustomer(customer) {
  const type = customer?.customerType || customer?.custType || customer?.customerCategory;
  const status = customer?.status || customer?.custStatus;
  // 现有 CUSTOMER_TYPE 字典的对公值为 CORP；兼容旧值，但缺失类型不放行。
  return ['CORP', 'COMPANY', 'CORPORATE', '企业', '公司'].includes(type)
    && (!status || ['ACTIVE', 'APPROVED'].includes(status));
}

async function searchCustomers(keyword) {
  const value = String(keyword || '').trim();
  if (!value) {
    customerOptions.value = [];
    return [];
  }
  customerLoading.value = true;
  try {
    const rows = await listLoanCustomerCandidates({ keyword: value, status: 'ACTIVE', pageNo: 1, pageSize: 20 });
    customerOptions.value = rows.map(normalizeCustomer).filter(item => item?.id && isEligibleCustomer(item));
    return customerOptions.value;
  } finally {
    customerLoading.value = false;
  }
}

function selectCustomer(customer) {
  const normalized = normalizeCustomer(customer);
  if (!normalized?.id) return;
  form.custId = normalized.id;
  form.custName = normalized.name;
  if (!customerOptions.value.some(item => item.id === normalized.id)) customerOptions.value.push(normalized);
}

function onCustomerChange(id) {
  const selected = customerOptions.value.find(item => item.id === id);
  form.custName = selected?.name || '';
}

function onFileChange(uploadFile, uploadFiles) {
  const files = uploadFiles || [];
  const oversized = files.filter(item => Number(item?.raw?.size ?? item?.size) > MAX_ATTACHMENT_SIZE);
  if (oversized.length) {
    oversized.forEach(item => ElMessage.warning(`附件 ${item.name || '未命名文件'} 超过 50MB，未加入上传列表`));
  }
  fileList.value = files.filter(item => !oversized.includes(item));
  pendingFileItems.value = fileList.value.filter(item => item.raw);
  pendingFiles.value = pendingFileItems.value.map(item => toRaw(item.raw));
}

function onFileRemove(_uploadFile, uploadFiles) {
  fileList.value = uploadFiles || [];
  pendingFileItems.value = fileList.value.filter(item => item.raw);
  pendingFiles.value = pendingFileItems.value.map(item => toRaw(item.raw));
}

function beforeFileRemove(uploadFile) {
  if (uploadFile?.raw) return true;
  ElMessage.warning('已上传附件暂不支持在此删除');
  return false;
}

function validateForm() {
  if (!form.custId) return '请选择客户';
  if (!form.projectType) return '请选择项目类型';
  if (!form.bizType) return '请选择业务类型';
  if (!form.guaranteeType) return '请选择主要担保方式';
  const credit = Number(form.creditAmount);
  const exposure = Number(form.creditExposureAmount);
  if (!Number.isFinite(credit) || credit <= 0) return '请输入大于 0 的授信金额';
  if (!Number.isFinite(exposure) || exposure < 0) return '请输入不小于 0 的授信敞口金额';
  if (exposure > credit) return '授信敞口金额不能大于授信金额';
  return '';
}

function payload() {
  return {
    custId: form.custId,
    sourceTouchTaskId: form.sourceTouchTaskId || undefined,
    projectType: form.projectType,
    bizType: form.bizType,
    guaranteeType: form.guaranteeType,
    creditAmount: Number(form.creditAmount),
    creditExposureAmount: Number(form.creditExposureAmount)
  };
}

function responseId(result) {
  return result?.id || result?.loanId || result?.data?.id || editingId.value;
}

async function uploadPending(loanId) {
  const files = pendingFileItems.value.filter(item => item?.raw).map(item => ({ item, file: toRaw(item.raw) }));
  const uploadedIds = [];
  for (const { item, file } of files) {
    const key = item.uid || fileKey(file);
    if (uploadedPendingIds.has(key)) {
      uploadedIds.push(uploadedPendingIds.get(key));
      continue;
    }
    if (Number(file?.size) > MAX_ATTACHMENT_SIZE) {
      throw new Error(`附件 ${file.name || '未命名文件'} 超过 50MB`);
    }
    const result = await uploadLoanAttachment(file, loanId);
    const fileId = result?.id || result?.fileObjectId;
    if (!fileId) throw new Error(`附件 ${file.name || '未命名文件'} 上传成功但未返回文件 ID`);
    uploadedPendingIds.set(key, fileId);
    uploadedIds.push(fileId);
  }
  return uploadedIds;
}

function fileKey(file) {
  return file?.uid || `${file?.name || ''}|${file?.size || 0}|${file?.lastModified || 0}`;
}

async function persist(andSubmit) {
  const validationMessage = validateForm();
  if (validationMessage) {
    ElMessage.warning(validationMessage);
    return null;
  }
  if (andSubmit) submitting.value = true;
  else saving.value = true;
  try {
    const data = payload();
    let id = editingId.value;
    let result;
    if (id) result = await updateLoanApplication(id, data);
    else result = await createLoanApplication(data);
    id = responseId(result);
    if (!id) throw new Error('保存成功但未返回申请 id');
    editingId.value = id;
    const uploadedIds = await uploadPending(id);
    if (uploadedIds.length) form.attachmentIds = [...form.attachmentIds, ...uploadedIds];
    if (andSubmit) {
      try {
        await submitLoanApplication(id);
      } catch (error) {
        // 后端明确要求并行申请二次确认时再重试，其他错误必须原样抛出。
        if (error?.code !== 'BIZ-40907') throw error;
        await ElMessageBox.confirm('该客户已有在途申请，是否仍要提交？', '并行申请确认', {
          type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '取消'
        });
        await submitLoanApplication(id, { confirmParallel: true });
      }
    }
    ElMessage.success(andSubmit ? '资产立项申请已提交' : '资产立项草稿已保存');
    emit('saved', { id, submitted: andSubmit, result });
    visible.value = false;
    return id;
  } finally {
    saving.value = false;
    submitting.value = false;
  }
}

function saveDraft() { return persist(false); }
function saveAndSubmit() { return persist(true); }

function resetForm() {
  Object.assign(form, emptyForm());
  editingId.value = '';
  customerOptions.value = [];
  fileList.value = [];
  pendingFiles.value = [];
  pendingFileItems.value = [];
  uploadedPendingIds.clear();
  nextTick(() => formRef.value?.clearValidate?.());
}

function fillLoan(loan) {
  resetForm();
  if (!loan) return;
  editingId.value = loan.id || '';
  Object.assign(form, emptyForm(), {
    ...loan,
    custName: loan.custName || loan.custInfo?.custName || loan.custInfo?.name || '',
    sourceTouchTaskId: loan.sourceTouchTaskId || props.sourceTouchTaskId || '',
    attachmentIds: (loan.attachments || []).map(file => file.id)
  });
  if (form.custId) customerOptions.value = [{ id: form.custId, name: form.custName || form.custId }];
  fileList.value = (loan.attachments || []).map(file => ({
    name: file.fileName || file.name || file.id,
    status: 'success',
    uid: file.id,
    url: `/api/files/${encodeURIComponent(file.id)}/download`
  }));
}

watch(() => [props.modelValue, props.loan], ([isOpen, loan]) => {
  if (isOpen) fillLoan(loan);
}, { immediate: true });

defineExpose({
  form,
  editingId,
  searchCustomers,
  selectCustomer,
  validateForm,
  saveDraft,
  saveAndSubmit,
  onFileChange,
  onFileRemove,
  beforeFileRemove,
  customerLocked,
  pendingFiles,
  MAX_ATTACHMENT_SIZE,
  resetForm
});
</script>

<style scoped lang="scss">
.loan-form-dialog :deep(.el-dialog__body) { padding-top: 8px; }
.form-alert { margin-bottom: 16px; }
.loan-form { display: flex; flex-direction: column; gap: 8px; }
.form-section { padding: 14px 16px 4px; border: 1px solid var(--color-border); border-radius: var(--radius-control); background: var(--color-surface); }
.form-section h2 { margin: 0 0 12px; color: var(--color-text-strong); font-size: 14px; font-weight: 600; }
.form-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 0 16px; }
.form-grid :deep(.el-form-item) { min-width: 0; }
.field-hint { margin: -2px 0 10px; color: var(--color-text-muted); font-size: 12px; line-height: 18px; }
.el-upload__tip { color: var(--color-text-muted); font-size: 12px; }
@media (max-width: 800px) { .form-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 560px) { .form-grid { grid-template-columns: 1fr; } }
</style>
