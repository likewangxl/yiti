<template>
  <el-dialog :model-value="modelValue" width="920px" top="5vh" append-to-body
             title="触达任务详情" :close-on-click-modal="false"
             @update:model-value="$emit('update:modelValue', $event)">
    <div v-loading="loading">
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="任务编号">{{ task.taskNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ customer.custName || task.custId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="执行人工号">{{ task.assigneeEmpId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="所属机构">{{ task.orgId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="任务状态">
          <el-tag :type="taskTagType(task.taskStatus)">{{ taskStatusLabel(task.taskStatus) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="SLA">
          <el-tag :type="slaTagType(task.slaStatus)">{{ slaLabel(task.slaStatus) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="计划完成">{{ fmt(task.planFinishTime) }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ fmt(task.createdTime) }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ fmt(task.successTime) }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="showLogForm" class="log-form">
        <h3>办理补录</h3>
        <el-form label-width="92px" :model="form">
          <div class="form-grid">
            <el-form-item label="触达时间" required>
              <el-date-picker v-model="form.touchTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"
                              placeholder="选择实际触达时间" style="width:100%" />
            </el-form-item>
            <el-form-item label="触达方式" required>
              <el-select v-model="form.touchMethod" style="width:100%">
                <el-option label="上门拜访" value="VISIT" />
                <el-option label="电话" value="PHONE" />
                <el-option label="微信" value="WECHAT" />
                <el-option label="其他" value="OTHER" />
              </el-select>
            </el-form-item>
          </div>
          <el-form-item label="协同人员">
            <el-input v-model="form.participants" placeholder="输入协同人员工号，多个用逗号分隔" />
          </el-form-item>
          <el-form-item label="触达小结" required>
            <el-input v-model="form.logContent" type="textarea" :rows="3" maxlength="200" show-word-limit />
          </el-form-item>
          <el-form-item label="办理定位">
            <el-input v-model="form.operatorLocation" placeholder="填写地址，或粘贴经纬度" />
          </el-form-item>
          <el-form-item label="触达照片" required>
            <div class="photo-groups">
              <div v-for="group in groups" :key="group.key" class="photo-group">
                <div class="photo-title">{{ group.label }}（{{ form.photoGroups[group.key].length }}/3）</div>
                <div class="photo-list">
                  <span v-for="url in form.photoGroups[group.key]" :key="url" class="photo-item">
                    <el-image :src="url" fit="cover" :preview-src-list="form.photoGroups[group.key]" />
                    <button type="button" @click="removePhoto(group.key, url)">×</button>
                  </span>
                  <el-upload v-if="form.photoGroups[group.key].length < 3" action="#" accept="image/*"
                             :show-file-list="false" :http-request="opts => upload(group.key, opts)">
                    <el-button size="small" :loading="uploading">上传</el-button>
                  </el-upload>
                </div>
              </div>
            </div>
          </el-form-item>
          <el-form-item>
            <el-button class="save-log-button" type="primary" :loading="saving" @click="submitLog">保存触达日志</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div v-if="showTaskActions" class="task-actions">
        <el-button class="cancel-task-button" type="danger" plain :loading="cancelling"
                   @click="cancel">取消任务</el-button>
        <el-button class="complete-task-button" type="success" :disabled="logs.length === 0"
                   :loading="completing" @click="complete">完成任务</el-button>
      </div>

      <h3 class="history-title">触达历史</h3>
      <el-empty v-if="!logs.length" description="暂无触达记录" :image-size="70" />
      <el-timeline v-else>
        <el-timeline-item v-for="log in logs" :key="log.workLogId || log.id" :timestamp="fmt(log.logTime)" placement="top">
          <el-card shadow="never">
            <div class="log-head">{{ methodLabel(log.touchMethod) }} · {{ log.createdBy || '-' }}</div>
            <div class="log-content">{{ log.logContent || '-' }}</div>
            <div v-if="participantText(log)" class="muted">协同人员：{{ participantText(log) }}</div>
            <div v-if="log.operatorLocation" class="muted">办理定位：{{ log.operatorLocation }}</div>
            <div class="history-photos">
              <template v-for="(items, key) in normalizePhotoGroups(log.photoGroups)" :key="key">
                <el-image v-for="url in items" :key="url" :src="url" fit="cover" :preview-src-list="items" />
              </template>
            </div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRouter } from 'vue-router';
import {
  addTouchLog, cancelTouchTask, completeTouchTask, getMarketingCustomer, getTouchTask,
  listTouchLogs, uploadTouchPhoto
} from '@/api/customerMarketing';
import { normalizePhotoGroups, slaLabel, slaTagType, taskStatusLabel, taskTagType } from '@/utils/touchViewModel';

const props = defineProps({
  modelValue: Boolean,
  taskId: { type: String, default: '' },
  allowWrite: { type: Boolean, default: false }
});
const emit = defineEmits(['update:modelValue', 'changed']);
const router = useRouter();
const task = ref({});
const customer = ref({});
const logs = ref([]);
const loading = ref(false);
const saving = ref(false);
const completing = ref(false);
const cancelling = ref(false);
const uploading = ref(false);
const groups = [
  { key: 'keyPerson', label: '关键人合影' },
  { key: 'doorplate', label: '企业门牌' },
  { key: 'workplace', label: '经营场所' }
];

function initialForm() {
  const now = new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 19);
  return {
    touchTime: now, touchMethod: 'VISIT', participants: '', logContent: '', operatorLocation: '',
    photoGroups: { keyPerson: [], doorplate: [], workplace: [] }
  };
}
const form = reactive(initialForm());
const writable = computed(() => ['PENDING', 'IN_PROGRESS'].includes(task.value.taskStatus));
const showLogForm = computed(() => props.allowWrite && task.value.taskStatus === 'PENDING' && logs.value.length === 0);
const showTaskActions = computed(() => props.allowWrite && writable.value);
const fmt = value => value ? String(value).replace('T', ' ').slice(0, 19) : '-';
const methodLabel = value => ({ VISIT: '上门拜访', PHONE: '电话', WECHAT: '微信', OTHER: '其他' }[value] || value || '触达');

async function load() {
  if (!props.taskId) return;
  loading.value = true;
  try {
    task.value = await getTouchTask(props.taskId) || {};
    const [loadedLogs, loadedCustomer] = await Promise.all([
      listTouchLogs(props.taskId),
      task.value.custId ? getMarketingCustomer(task.value.custId).catch(() => ({})) : Promise.resolve({})
    ]);
    logs.value = Array.isArray(loadedLogs) ? loadedLogs.slice(0, 1) : [];
    customer.value = loadedCustomer;
  } finally { loading.value = false; }
}

async function upload(key, options) {
  uploading.value = true;
  try {
    const file = options.file;
    if (!file?.type?.startsWith('image/')) throw new Error('仅支持图片文件');
    const saved = await uploadTouchPhoto(file);
    const safeName = encodeURIComponent(saved?.fileName || file.name || 'photo.jpg');
    form.photoGroups[key].push(`/api/files/${saved.id}/download#${safeName}`);
    options.onSuccess?.(saved);
  } catch (e) {
    options.onError?.(e);
    ElMessage.error(e?.message || '照片上传失败');
  } finally { uploading.value = false; }
}
function removePhoto(key, url) {
  form.photoGroups[key] = form.photoGroups[key].filter(x => x !== url);
}

async function submitLog() {
  if (!showLogForm.value) return;
  if (!form.touchTime || !form.touchMethod || !form.logContent.trim()) return ElMessage.warning('请完整填写触达时间、方式和小结');
  if (Object.values(form.photoGroups).every(items => items.length === 0)) return ElMessage.warning('请至少上传一张触达照片');
  saving.value = true;
  try {
    await addTouchLog(props.taskId, {
      clientUuid: globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random()}`,
      touchTime: form.touchTime,
      touchMethod: form.touchMethod,
      participantEmpIds: form.participants.split(/[,，]/).map(x => x.trim()).filter(Boolean),
      logContent: form.logContent.trim(),
      photoGroups: form.photoGroups,
      operatorLocation: form.operatorLocation.trim() || undefined
    });
    task.value = { ...task.value, taskStatus: 'IN_PROGRESS' };
    Object.assign(form, initialForm());
    ElMessage.success('触达日志已保存');
    await load();
    emit('changed');
  } finally { saving.value = false; }
}

function customerConfirmText() {
  const name = customer.value?.custName || customer.value?.customerName
    || task.value?.custName || task.value?.customerName;
  const custNo = customer.value?.custNo || customer.value?.customerNo
    || task.value?.custNo || task.value?.customerNo;
  if (name && custNo) return `客户“${name}”（客户号：${custNo}）`;
  if (name) return `客户“${name}”`;
  if (custNo) return `客户号“${custNo}”`;
  return '';
}

async function complete() {
  const customerText = customerConfirmText();
  const prompt = customerText
    ? `确认完成${customerText}的触达任务？完成后不可继续补录。`
    : '确认完成该触达任务？完成后不可继续补录。';
  await ElMessageBox.confirm(prompt, '完成任务', { type: 'warning' });
  completing.value = true;
  try {
    await completeTouchTask(props.taskId);
    ElMessage.success('任务已完成');
    await load();
    emit('changed');
    await offerSupportRequest();
  } finally { completing.value = false; }
}

/**
 * 触达完成后的中台支持入口是独立的后续选择。
 * 这里吞掉用户取消和导航异常，确保触达成功状态不会因后续入口失败而回滚或被误报。
 */
async function offerSupportRequest() {
  const customerText = customerConfirmText();
  const prompt = customerText
    ? `触达已完成，当前${customerText}，是否需要发起中台支持？`
    : '触达已完成，是否需要发起中台支持？';
  try {
    await ElMessageBox.confirm(prompt, '中台支持', {
      type: 'info', confirmButtonText: '需要', cancelButtonText: '暂不需要'
    });
  } catch (_) {
    return;
  }
  const custId = task.value?.custId || customer.value?.custId || customer.value?.id;
  if (!custId) return;
  try {
    await router?.push({
      path: '/bizexec/supports/new',
      query: { custId: String(custId), sourceTouchTaskId: String(props.taskId) }
    });
  } catch (_) {
    // 触达已完成；中台支持只是可选后续入口，路由失败不影响已完成结果。
  }
}

async function cancel() {
  let result;
  try {
    result = await ElMessageBox.prompt('请输入取消原因，取消后不可继续办理。', '取消任务', {
      confirmButtonText: '确认取消',
      cancelButtonText: '返回',
      inputPlaceholder: '请输入取消原因',
      inputValidator: value => value?.trim() ? true : '取消原因不能为空'
    });
  } catch {
    return;
  }
  const reason = String(result?.value || '').trim();
  if (!reason) return ElMessage.warning('取消原因不能为空');
  cancelling.value = true;
  try {
    await cancelTouchTask(props.taskId, reason);
    ElMessage.success('任务已取消');
    await load();
    emit('changed');
  } finally { cancelling.value = false; }
}

function participantText(log) {
  if (!log?.participantEmpIds) return '';
  try { return JSON.parse(log.participantEmpIds).join('、'); } catch { return log.participantEmpIds; }
}

watch(() => [props.modelValue, props.taskId], ([show]) => { if (show) load(); });
</script>

<style scoped lang="scss">
.form-grid { display:grid; grid-template-columns:1fr 1fr; gap:12px; }
.log-form { margin-top:18px; padding:14px; background:#f7f9fc; border-radius:6px; }
.task-actions { display:flex; justify-content:flex-end; margin-top:14px; }
.log-form h3,.history-title { font-size:15px; margin:0 0 14px; border-left:3px solid #409eff; padding-left:8px; }
.history-title { margin-top:20px; }
.photo-groups { width:100%; display:grid; grid-template-columns:repeat(3,1fr); gap:10px; }
.photo-group { border:1px solid #ebeef5; background:#fff; padding:10px; border-radius:5px; }
.photo-title { font-size:12px; color:#606266; margin-bottom:8px; }
.photo-list { display:flex; gap:6px; flex-wrap:wrap; align-items:center; }
.photo-item { position:relative; width:58px; height:58px; }
.photo-item .el-image,.history-photos .el-image { width:58px; height:58px; border-radius:4px; }
.photo-item button { position:absolute; right:-5px; top:-7px; border:0; border-radius:50%; color:#fff; background:#f56c6c; cursor:pointer; }
.log-head { font-weight:600; margin-bottom:6px; }
.log-content { white-space:pre-wrap; margin-bottom:6px; }
.muted { color:#909399; font-size:12px; margin-top:4px; }
.history-photos { display:flex; gap:6px; margin-top:8px; flex-wrap:wrap; }
@media (max-width:800px) { .form-grid,.photo-groups { grid-template-columns:1fr; } }
</style>
