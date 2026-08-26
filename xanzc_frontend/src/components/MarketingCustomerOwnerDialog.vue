<template>
  <el-dialog
    v-model="visible"
    class="bp-crud-dialog marketing-customer-owner-dialog"
    :title="allowUnassign ? '管理客户主办权' : '转交客户主办权'"
    width="min(560px, 94vw)"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <el-alert
      v-if="allowUnassign"
      title="设置为无主办人后，全行客户经理均可查看该客户；该操作不会把客户自动写入待认领池。"
      type="info"
      :closable="false"
      show-icon
      class="dialog-alert"
    />
    <el-descriptions v-if="customer" :column="1" border class="customer-brief">
      <el-descriptions-item label="客户名称">{{ customer.custName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="当前主办">
        {{ customer.mainManagerName || customer.mainManagerId || '无' }}
        <template v-if="customer.mainManagerId">（{{ customer.mainManagerId }}）</template>
      </el-descriptions-item>
    </el-descriptions>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="owner-form">
      <el-form-item v-if="allowUnassign" label="主办权动作" prop="transferAction">
        <el-radio-group v-model="form.transferAction">
          <el-radio-button value="TRANSFER">转交给其他客户经理</el-radio-button>
          <el-radio-button value="UNASSIGN">设置为无</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="form.transferAction === 'TRANSFER'" label="目标客户经理" prop="targetManagerId">
        <el-select
          v-model="form.targetManagerId"
          filterable
          remote
          clearable
          reserve-keyword
          :remote-method="searchManagers"
          :loading="managerLoading"
          placeholder="输入工号或姓名搜索"
          style="width: 100%"
        >
          <el-option
            v-for="manager in managerOptions"
            :key="manager.empId || manager.id"
            :value="manager.empId || manager.id"
            :label="managerLabel(manager)"
          />
        </el-select>
        <div class="form-hint">只能选择有效客户经理，当前登录人不能作为目标。</div>
      </el-form-item>
      <el-form-item label="转交原因" prop="reason">
        <el-input v-model="form.reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="请填写主办权变更原因" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="submitting" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确认{{ form.transferAction === 'UNASSIGN' ? '设置' : '转交' }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, computed, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { searchEmployees } from '@/api/employees';

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  customer: { type: Object, default: null },
  allowUnassign: { type: Boolean, default: false },
  submitting: { type: Boolean, default: false },
});
const emit = defineEmits(['update:modelValue', 'submit']);
const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
});
const formRef = ref(null);
const form = reactive({ transferAction: 'TRANSFER', targetManagerId: '', reason: '' });
const managerOptions = ref([]);
const managerLoading = ref(false);
const rules = {
  transferAction: [{ required: true, message: '请选择主办权动作', trigger: 'change' }],
  targetManagerId: [{ validator: (_rule, value, done) => {
    if (form.transferAction === 'TRANSFER' && !String(value || '').trim()) done(new Error('请选择目标客户经理'));
    else done();
  }, trigger: 'change' }],
  reason: [{ required: true, message: '请填写转交原因', trigger: 'blur' }],
};

watch(() => props.modelValue, value => {
  if (!value) return;
  form.transferAction = 'TRANSFER';
  form.targetManagerId = '';
  form.reason = '';
  managerOptions.value = props.customer?.mainManagerId
    ? [{ empId: props.customer.mainManagerId, displayName: props.customer.mainManagerName }]
    : [];
  formRef.value?.clearValidate?.();
}, { immediate: true });

watch(() => form.transferAction, value => {
  if (value === 'UNASSIGN') form.targetManagerId = '';
});

async function searchManagers(keyword) {
  if (!String(keyword || '').trim()) {
    managerOptions.value = [];
    return;
  }
  managerLoading.value = true;
  try {
    const result = await searchEmployees(String(keyword).trim(), 30);
    managerOptions.value = Array.isArray(result) ? result : result?.records || [];
  } catch (error) {
    managerOptions.value = [];
    ElMessage.error(`客户经理加载失败：${error?.message || '请稍后重试'}`);
  } finally {
    managerLoading.value = false;
  }
}

const managerLabel = manager => `${manager.displayName || manager.name || manager.empName || manager.empId || manager.id}（${manager.empId || manager.id || '-'}）${manager.mainOrgName || manager.orgName ? ` · ${manager.mainOrgName || manager.orgName}` : ''}`;

async function submit() {
  if (props.submitting) return;
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  const payload = {
    transferAction: form.transferAction,
    targetManagerId: form.transferAction === 'TRANSFER' ? form.targetManagerId : undefined,
    reason: form.reason.trim(),
  };
  emit('submit', payload);
}
</script>

<style scoped lang="scss">
.dialog-alert { margin-bottom: 16px; }
.customer-brief { margin-bottom: 18px; }
.owner-form { margin-top: 6px; }
.form-hint { color: #909399; font-size: 12px; line-height: 18px; margin-top: 4px; }
</style>
