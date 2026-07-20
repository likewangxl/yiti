<!--
  转交/指派弹窗：秘书岗/行长在审批流监控把某个任务交给"本机构"其他人（待接收人认领后才真正生效）。
  任务已签收 = 转交（从原办理人手上转走）；未签收的候选组任务 = 指派（从候选池直接指定办理人），
  两者走同一个 initiate 端点，仅文案区分（后端 from_emp_id 可空承载这两种语义）。
  接收人候选：无专用端点（Task 12 未提供 /transfers/candidates），按 P4 计划走
  「本机构人员」接口（OrgController /api/orgs/{orgCode}/users），机构取当前登录用户主机构
  （TaskTransferService.initiate 校验接收人主机构必须等于发起人机构，本机构以外选了也会被
  后端 WF-40911 拒绝）；节点候选资格（WF-40912）前端不做校验，交给后端 initiate 兜底。
-->
<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="v => emit('update:modelValue', v)"
    :title="isAssign ? '指派任务' : '转交任务'" width="480px" :close-on-click-modal="false" :destroy-on-close="true">
    <div class="task-brief" v-if="task">
      <div class="tb-row"><span class="tb-key">节点：</span>{{ task.nodeName || task.taskName || '-' }}</div>
      <div class="tb-row" v-if="task.businessKey"><span class="tb-key">业务键：</span><code class="mono">{{ task.businessKey }}</code></div>
      <div class="tb-row" v-if="isAssign">
        <span class="tb-key">当前处理人：</span><span class="tb-hint">尚无人签收，本次为从候选人中指派</span>
      </div>
    </div>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px" size="default">
      <el-form-item label="接收人" prop="toEmpId">
        <el-select
          v-model="form.toEmpId"
          filterable
          placeholder="选择本机构人员"
          style="width:100%"
          :loading="candidatesLoading"
          no-data-text="本机构暂无可选人员">
          <el-option
            v-for="u in candidates"
            :key="u.empId"
            :label="`${u.displayName}（${u.empId}）`"
            :value="u.empId"
          />
        </el-select>
        <div class="form-hint">仅列出本机构（{{ orgLabel }}）在职人员，后端仍会校验其是否具备该节点办理资格</div>
      </el-form-item>
      <el-form-item :label="isAssign ? '指派原因' : '转交原因'" prop="reason">
        <el-input
          v-model="form.reason"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          :placeholder="isAssign ? '请填写指派原因（必填）' : '请填写转交原因（必填）'"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="onCancel">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="onSubmit">提交</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { transferInitiate } from '@/api/workflow';
import { listOrgUsers } from '@/api/orgs';
import { useUserStore } from '@/stores/user';

const props = defineProps({
  /** v-model：弹窗显隐 */
  modelValue: { type: Boolean, default: false },
  /** 待转交任务：至少含 taskId，nodeName/businessKey 仅用于弹窗内展示 */
  task: { type: Object, default: null }
});
const emit = defineEmits(['update:modelValue', 'success']);

const userStore = useUserStore();
const orgCode = computed(() => userStore.user?.mainOrgCode || '');
const orgLabel = computed(() => userStore.orgName || orgCode.value || '本机构');

const formRef = ref(null);
const form = reactive({ toEmpId: '', reason: '' });
const rules = {
  toEmpId: [{ required: true, message: '请选择接收人', trigger: 'change' }],
  reason: [{ required: true, message: '请填写原因', trigger: 'blur' }]
};

const candidates = ref([]);
const candidatesLoading = ref(false);
async function loadCandidates() {
  if (!orgCode.value) { candidates.value = []; return; }
  candidatesLoading.value = true;
  try {
    const r = await listOrgUsers(orgCode.value, { pageSize: 200, isEnabled: 0 });
    const records = Array.isArray(r) ? r : (r?.records || []);
    // 排除自己（转交给自己无意义，后端也不会作为有效候选）
    const me = userStore.user?.empId;
    candidates.value = records.filter(u => u.empId && u.empId !== me);
  } catch {
    ElMessage.warning('本机构人员加载失败');
    candidates.value = [];
  } finally {
    candidatesLoading.value = false;
  }
}

// 弹窗打开时重置表单并拉取候选人
watch(() => props.modelValue, (v) => {
  if (v) {
    form.toEmpId = '';
    form.reason = '';
    formRef.value?.clearValidate?.();
    loadCandidates();
  }
});

const taskId = computed(() => props.task?.taskId || props.task?.id || '');
// 任务无当前处理人 = 候选组任务尚未签收 → 本次是「指派」而非「转交」，仅影响文案，接口同一个
const isAssign = computed(() => !props.task?.currentAssignee);

const submitting = ref(false);
async function onSubmit() {
  if (!taskId.value) {
    ElMessage.error(isAssign.value ? '缺少任务信息，无法指派' : '缺少任务信息，无法转交');
    return;
  }
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  submitting.value = true;
  try {
    await transferInitiate(taskId.value, { toEmpId: form.toEmpId, reason: form.reason.trim() });
    ElMessage.success(isAssign.value ? '指派已发起，等待接收人认领' : '转交已发起，等待接收人认领');
    emit('update:modelValue', false);
    emit('success');
  } catch {
    /* http.js 拦截器已 toast 错误详情（如 WF-40911 接收人不在本机构 / WF-40912 无办理资格） */
  } finally {
    submitting.value = false;
  }
}

function onCancel() {
  emit('update:modelValue', false);
}
</script>

<style scoped>
.task-brief {
  background: var(--el-fill-color-light, #f5f7fa);
  border-radius: 4px;
  padding: 10px 12px;
  margin-bottom: 14px;
  font-size: 13px;
}
.tb-row { line-height: 1.8; color: var(--el-text-color-regular, #606266); }
.tb-key { color: var(--el-text-color-secondary, #909399); }
.tb-hint { color: var(--el-color-warning, #e6a23c); }
.mono { font-family: ui-monospace, monospace; font-size: 12px; }
.form-hint { font-size: 12px; color: var(--el-text-color-secondary, #909399); margin-top: 4px; line-height: 1.5; }
</style>
