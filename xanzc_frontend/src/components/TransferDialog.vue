<!--
  转交/指派弹窗：秘书岗/行长在审批流监控把某个任务交给"本机构"其他人（待接收人认领后才真正生效）。
  任务已签收 = 转交（从原办理人手上转走）；未签收的候选组任务 = 指派（从候选池直接指定办理人），
  两者走同一个 initiate 端点，仅文案区分（后端 from_emp_id 可空承载这两种语义）。
  接收人候选：走专用端点 GET /workflow/monitor/tasks/{taskId}/transfer-candidates
  （2026-07-21 新增），后端 listCandidates 与 initiate 的资格校验同源——已按「节点可办理者
  ∩ 本机构」展开并排除自己与原办理人，所以列出来的人提交必定通过。
  此前这里列的是「本机构全部人员」（OrgController /api/orgs/{orgCode}/users），资格只由后端
  在提交时抛 WF-40912 兜底，用户会选中注定失败的人（典型：流程发起后才被授予角色的人不在
  任务身份链接快照内，选了必被打回）——已废弃该做法，不要改回按机构拉全量再让后端兜底。
-->
<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="v => emit('update:modelValue', v)"
    class="bp-crud-dialog transfer-dialog"
    :title="isAssign ? '指派任务' : '转交任务'"
    width="520px"
    :close-on-click-modal="false"
    :close-on-press-escape="!submitting"
    :show-close="!submitting"
    :destroy-on-close="true"
  >
    <div v-if="task" class="task-brief" role="status" aria-live="polite">
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
          aria-label="选择具备当前节点办理资格的接收人"
          style="width:100%"
          :loading="candidatesLoading"
          :disabled="submitting"
          no-data-text="该节点在本机构暂无其他可办理人员">
          <el-option
            v-for="u in candidates"
            :key="u.empId"
            :label="`${u.displayName}（${u.empId}）`"
            :value="u.empId"
          />
        </el-select>
        <div class="form-hint">仅列出本机构（{{ orgLabel }}）中具备该节点办理资格的人员。</div>
        <p v-if="candidatesError" class="candidate-error" role="alert">{{ candidatesError }} <el-button link type="primary" :disabled="submitting" @click="loadCandidates">重试</el-button></p>
      </el-form-item>
      <el-form-item :label="isAssign ? '指派原因' : '转交原因'" prop="reason">
        <el-input
          v-model="form.reason"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          :disabled="submitting"
          :placeholder="isAssign ? '请填写指派原因（必填）' : '请填写转交原因（必填）'"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="submitting" @click="onCancel">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="submitting" @click="onSubmit">{{ isAssign ? '确认指派' : '确认转交' }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { transferInitiate, transferCandidates } from '@/api/workflow';
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

const taskId = computed(() => props.task?.taskId || props.task?.id || '');
// 任务无当前处理人 = 候选组任务尚未签收 → 本次是「指派」而非「转交」，仅影响文案，接口同一个
const isAssign = computed(() => !props.task?.currentAssignee);

const candidates = ref([]);
const candidatesLoading = ref(false);
const candidatesError = ref('');
const submitting = ref(false);
// 候选人完全由后端给定：机构过滤、节点办理资格、排除自己与原办理人都在 listCandidates 内完成，
// 前端不再做任何二次过滤——任何前端侧过滤都会与后端 initiate 的判定产生分叉。
async function loadCandidates() {
  if (!taskId.value || candidatesLoading.value || submitting.value) { candidates.value = taskId.value ? candidates.value : []; return; }
  candidatesLoading.value = true;
  candidatesError.value = '';
  try {
    candidates.value = await transferCandidates(taskId.value);
  } catch (error) {
    candidatesError.value = `可选接收人加载失败：${error?.message || '请重试'}`;
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
    candidatesError.value = '';
    formRef.value?.clearValidate?.();
    loadCandidates();
  }
}, { immediate: true });

async function onSubmit() {
  if (submitting.value) return;
  if (!taskId.value) {
    ElMessage.error(isAssign.value ? '缺少任务信息，无法指派' : '缺少任务信息，无法转交');
    return;
  }
  submitting.value = true;
  try {
    await formRef.value?.validate();
  } catch {
    submitting.value = false;
    return;
  }
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
  if (submitting.value) return;
  emit('update:modelValue', false);
}
</script>

<style scoped>
.task-brief {
  background: var(--color-surface-soft);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  margin-bottom: var(--space-4);
  padding: var(--space-3);
  font-size: 13px;
}
.tb-row { color: var(--color-text); line-height: 24px; }
.tb-key { color: var(--color-text-muted); }
.tb-hint { color: var(--color-warning-fg); }
.mono { color: var(--color-text-strong); font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; font-size: 12px; }
.form-hint { color: var(--color-text-muted); font-size: 12px; line-height: 18px; margin: var(--space-1) 0 0; }
.candidate-error { color: var(--color-danger-fg); font-size: 12px; line-height: 18px; margin: var(--space-2) 0 0; }
</style>
