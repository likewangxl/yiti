<template>
  <el-dialog :model-value="modelValue" width="920px" top="5vh" append-to-body
             title="触达任务详情" :close-on-click-modal="false" class="touch-detail-dialog"
             @update:model-value="$emit('update:modelValue', $event)">
    <div v-loading="loading">
      <section class="task-overview" aria-label="触达任务概览">
        <div class="task-banner">
          <div class="task-banner-main">
            <span class="eyebrow">客户</span>
            <strong>{{ customerName || task.custId || '-' }}</strong>
            <small v-if="customerNumber">客户号：{{ customerNumber }}</small>
          </div>
          <div class="task-banner-item">
            <span class="eyebrow">任务状态</span>
            <el-tag :type="taskTagType(task.taskStatus)" effect="plain">{{ taskStatusLabel(task.taskStatus) }}</el-tag>
          </div>
          <div class="task-banner-item">
            <span class="eyebrow">SLA</span>
            <el-tag :type="slaTagType(task.slaStatus)" effect="plain">{{ slaLabel(task.slaStatus) }}</el-tag>
          </div>
        </div>
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="任务编号">{{ task.taskNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="执行人工号">{{ task.assigneeEmpId || task.assigneeId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="参与人">{{ taskParticipantText || '无' }}</el-descriptions-item>
          <el-descriptions-item label="所属机构">{{ task.orgName || task.orgId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="计划完成">{{ fmt(task.planFinishTime) }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ fmt(task.createdTime || task.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ fmt(task.successTime || task.completedTime) }}</el-descriptions-item>
          <el-descriptions-item label="历史日志">{{ logCount }} 条</el-descriptions-item>
        </el-descriptions>
      </section>

      <section v-if="showLogForm" class="log-form" aria-label="补录触达日志">
        <div class="section-heading">
          <div>
            <h3>补录触达日志</h3>
            <p>每次保存都会追加一条独立日志，已有历史记录不会被覆盖。</p>
          </div>
          <el-tag type="warning" effect="plain">可继续补录</el-tag>
        </div>
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
            <div class="location-field">
              <el-input v-model="form.operatorLocation" placeholder="填写地址，或粘贴经纬度" />
              <el-button class="location-button" type="primary" plain :loading="locating" @click="locate">获取当前位置</el-button>
            </div>
            <p v-if="locationMessage" class="location-message" role="status">{{ locationMessage }}</p>
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
      </section>

      <p v-else-if="cancelled" class="read-only-notice" role="status">
        任务已取消，仅保留历史记录，不能继续补录或办理。
      </p>

      <div v-if="showTaskActions" class="task-actions" aria-label="任务操作">
        <el-button class="cancel-task-button" type="danger" plain :loading="cancelling"
                   @click="cancel">取消任务</el-button>
        <el-button v-if="canComplete" class="complete-task-button" type="success"
                   :loading="completing" @click="complete">完成任务</el-button>
      </div>

      <div class="history-heading">
        <div>
          <h3 class="history-title">历史触达日志</h3>
          <p>按触达时间倒序展示，共 {{ logs.length }} 条；附件按日志独立留痕。</p>
        </div>
        <el-tag effect="plain">{{ logs.length }} 条</el-tag>
      </div>
      <el-empty v-if="!logs.length" description="暂无触达记录" :image-size="70" />
      <el-timeline v-else>
        <el-timeline-item v-for="(log, index) in logs" :key="logKey(log, index)" :timestamp="fmt(logTime(log))" placement="top">
          <el-card shadow="never">
            <div class="log-head">{{ methodLabel(log.touchMethod || log.type) }} · {{ log.createdBy || log.operatorEmpId || '-' }}</div>
            <div class="log-content">{{ log.logContent || log.summary || log.content || '-' }}</div>
            <div class="muted">协同人员：{{ participantText(log) || '无' }}</div>
            <div v-if="log.operatorLocation || log.location" class="muted">办理定位：{{ locationText(log.operatorLocation || log.location) }}</div>
            <div v-if="hasPhotos(log)" class="history-photos">
              <template v-for="(items, key) in logPhotoGroups(log)" :key="key">
                <el-image v-for="url in items" :key="url" :src="photoUrl(url)" fit="cover" :preview-src-list="items.map(photoUrl)" />
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
  addTouchLog, cancelTouchTask, completeTouchTask, getTouchTask,
  listTouchLogs, uploadTouchPhoto
} from '@/api/customerMarketing';
import { getMarketingCustomer } from '@/api/marketingManagement';
import { normalizePhotoGroups, slaLabel, slaTagType, taskStatusLabel, taskTagType } from '@/utils/touchViewModel';

const props = defineProps({
  modelValue: Boolean,
  taskId: { type: [String, Number], default: '' },
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
const locating = ref(false);
const locationMessage = ref('');
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
const supplementableStatuses = ['PENDING', 'IN_PROGRESS', 'SUCCESS'];
const inFlight = computed(() => ['PENDING', 'IN_PROGRESS'].includes(String(task.value.taskStatus || '').toUpperCase()));
const cancelled = computed(() => String(task.value.taskStatus || '').toUpperCase() === 'CANCELLED');
const showLogForm = computed(() => props.allowWrite
  && task.value?.canWriteLog !== false
  && supplementableStatuses.includes(String(task.value.taskStatus || '').toUpperCase()));
const showTaskActions = computed(() => props.allowWrite
  && task.value?.canOperateTask !== false
  && inFlight.value);
const canComplete = computed(() => showTaskActions.value && logs.value.length > 0);
const customerName = computed(() => customer.value?.custName || customer.value?.customerName
  || task.value?.custName || task.value?.customerName || '');
const customerNumber = computed(() => customer.value?.custNo || customer.value?.customerNo
  || task.value?.custNo || task.value?.customerNo || '');
const taskParticipantText = computed(() => participantText(task.value));
const logCount = computed(() => {
  const count = Number(task.value?.logCount);
  return Number.isFinite(count) && count >= 0 ? count : logs.value.length;
});
const fmt = value => value ? String(value).replace('T', ' ').slice(0, 19) : '-';
const methodLabel = value => ({ VISIT: '上门拜访', ONSITE: '上门拜访', PHONE: '电话', WECHAT: '微信', OTHER: '其他' }[String(value || '').toUpperCase()] || value || '触达');

function logTime(log) {
  return log?.logTime || log?.touchTime || log?.time || log?.createdTime || '';
}

function logKey(log, index = 0) {
  return log?.workLogId || log?.worklogId || log?.id || `${logTime(log)}-${index}`;
}

function normalizeLogs(value) {
  if (Array.isArray(value)) return value;
  if (Array.isArray(value?.records)) return value.records;
  if (Array.isArray(value?.data?.records)) return value.data.records;
  if (Array.isArray(value?.data)) return value.data;
  return [];
}

function compareLogTime(left, right) {
  return String(logTime(right)).localeCompare(String(logTime(left)));
}

function photoUrl(value) {
  const sourceValue = value && typeof value === 'object'
    ? value.url || value.downloadUrl || value.fileUrl || value.id || value.fileId || value.fileObjectId
    : value;
  const source = String(sourceValue || '');
  if (!source) return '';
  if (/^(?:https?:|data:|blob:|\/api\/files\/)/i.test(source)) return source;
  return `/api/files/${encodeURIComponent(source)}/download`;
}

function logPhotoGroups(log) {
  const groups = normalizePhotoGroups(log?.photoGroups);
  if (Object.values(groups).some(items => items.length > 0)) return groups;
  const legacyUrls = Array.isArray(log?.photoUrls) ? log.photoUrls : [];
  return legacyUrls.length ? { ...groups, workplace: legacyUrls } : groups;
}

function locationText(value) {
  if (!value) return '';
  if (typeof value === 'object') {
    const address = value.address || value.locationAddress || '';
    const latitude = value.latitude ?? value.lat;
    const longitude = value.longitude ?? value.lng;
    const coordinates = latitude !== undefined && longitude !== undefined
      ? `纬度 ${latitude}，经度 ${longitude}` : '';
    return [address, coordinates].filter(Boolean).join('；') || JSON.stringify(value);
  }
  const text = String(value);
  try {
    const parsed = JSON.parse(text);
    if (parsed && typeof parsed === 'object') return locationText(parsed);
  } catch (_) { /* 普通地址或经纬度文本直接展示。 */ }
  return text;
}

function geolocationMessage(error) {
  if (error?.code === 1) return '未获得定位权限，请允许浏览器定位，或手工填写地址。';
  if (error?.code === 2) return '暂时无法获取当前位置，请检查定位服务，或手工填写地址。';
  if (error?.code === 3) return '获取当前位置超时，请重试或手工填写地址。';
  return error?.message || '获取当前位置失败，请手工填写地址。';
}

async function locate() {
  const geolocation = globalThis.navigator?.geolocation;
  if (!geolocation?.getCurrentPosition) {
    locationMessage.value = '当前设备不支持定位，请手工填写地址。';
    ElMessage.warning(locationMessage.value);
    return;
  }
  locating.value = true;
  locationMessage.value = '';
  try {
    const position = await new Promise((resolve, reject) => {
      geolocation.getCurrentPosition(resolve, reject, {
        enableHighAccuracy: true,
        timeout: 10000,
        maximumAge: 30000
      });
    });
    const latitude = Number(position?.coords?.latitude);
    const longitude = Number(position?.coords?.longitude);
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
      throw new Error('定位结果缺少有效经纬度');
    }
    const accuracy = Number(position?.coords?.accuracy);
    const accuracyText = Number.isFinite(accuracy) && accuracy > 0 ? `（精度约${Math.round(accuracy)}米）` : '';
    // 触达时间是本次定位打卡的业务时间，提交日志时沿用 form.touchTime，不另造设备时间。
    form.operatorLocation = `纬度 ${latitude.toFixed(6)}，经度 ${longitude.toFixed(6)}${accuracyText}`;
    locationMessage.value = `已获取当前位置，打卡时间沿用触达时间 ${fmt(form.touchTime)}。如需更精确，可继续补充地址。`;
    ElMessage.success('当前位置已获取');
  } catch (error) {
    locationMessage.value = geolocationMessage(error);
    ElMessage.warning(locationMessage.value);
  } finally {
    locating.value = false;
  }
}

async function load() {
  if (!props.taskId) return;
  loading.value = true;
  try {
    task.value = await getTouchTask(props.taskId) || {};
    const customerId = task.value.custId || task.value.customerId;
    const [loadedLogs, loadedCustomer] = await Promise.all([
      listTouchLogs(props.taskId),
      customerId ? getMarketingCustomer(customerId).catch(() => ({})) : Promise.resolve({})
    ]);
    logs.value = normalizeLogs(loadedLogs).slice().sort(compareLogTime);
    customer.value = loadedCustomer || {};
  } finally { loading.value = false; }
}

async function upload(key, options) {
  uploading.value = true;
  try {
    const file = options.file;
    if (!file?.type?.startsWith('image/')) throw new Error('仅支持图片文件');
    const saved = await uploadTouchPhoto(file);
    const fileId = saved?.id || saved?.fileId || saved?.fileObjectId;
    if (!fileId) throw new Error('照片上传成功但未返回文件对象ID');
    const safeName = encodeURIComponent(saved?.fileName || file.name || 'photo.jpg');
    form.photoGroups[key].push(`/api/files/${encodeURIComponent(fileId)}/download#${safeName}`);
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
    Object.assign(form, initialForm());
    ElMessage.success('触达日志已保存');
    await load();
    emit('changed');
  } finally { saving.value = false; }
}

function customerConfirmText() {
  const name = customerName.value;
  const custNo = customerNumber.value;
  if (name && custNo) return `客户“${name}”（客户号：${custNo}）`;
  if (name) return `客户“${name}”`;
  if (custNo) return `客户号“${custNo}”`;
  return '';
}

async function complete() {
  if (!canComplete.value) return;
  const customerText = customerConfirmText();
  const prompt = customerText
    ? `确认完成${customerText}的触达任务？完成后仍可补录历史日志，但不能恢复办理状态。`
    : '确认完成该触达任务？完成后仍可补录历史日志，但不能恢复办理状态。';
  try {
    await ElMessageBox.confirm(prompt, '完成任务', { type: 'warning' });
  } catch (_) {
    return;
  }
  completing.value = true;
  try {
    await completeTouchTask(props.taskId);
    ElMessage.success('任务已完成');
    await load();
    emit('changed');
    await offerFollowup();
  } finally { completing.value = false; }
}

/**
 * 触达完成后的中台支持与资产立项入口是独立的后续选择。
 * 这里吞掉用户取消和导航异常，确保触达成功状态不会因后续入口失败而回滚或被误报。
 */
async function offerFollowup() {
  const customerText = customerConfirmText();
  const prompt = customerText
    ? `触达已完成，当前${customerText}。请选择下一步：发起中台支持，或进入资产立项。`
    : '触达已完成。请选择下一步：发起中台支持，或进入资产立项。';
  try {
    await ElMessageBox.confirm(prompt, '后续业务办理', {
      type: 'info', confirmButtonText: '发起中台支持', cancelButtonText: '选择资产立项',
      distinguishCancelAndClose: true
    });
  } catch (error) {
    // Element Plus 对取消按钮返回字符串 cancel；关闭弹窗或异常则直接结束后续选择。
    if (error !== 'cancel') return;
    try {
      await ElMessageBox.confirm(
        customerText ? `是否为${customerText}发起资产立项？` : '是否发起资产立项？',
        '资产立项',
        { type: 'info', confirmButtonText: '发起资产立项', cancelButtonText: '暂不处理' }
      );
    } catch (_) {
      return;
    }
    await navigateToAssetProject();
    return;
  }

  await navigateToSupport();
}

function sourceCustomerId() {
  return task.value?.custId || task.value?.customerId || customer.value?.custId || customer.value?.id;
}

function sourceWorklogId() {
  const latest = logs.value[0];
  return latest?.workLogId || latest?.worklogId || latest?.sourceWorklogId || latest?.id;
}

async function navigateToSupport() {
  const custId = sourceCustomerId();
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

async function navigateToAssetProject() {
  const custId = sourceCustomerId();
  if (!custId) return;
  const worklogId = sourceWorklogId();
  try {
    await router?.push({
      path: '/marketing/asset-projects/new',
      query: {
        custId: String(custId),
        sourceTouchTaskId: String(props.taskId),
        ...(worklogId === undefined || worklogId === null || worklogId === ''
          ? {} : { sourceWorklogId: String(worklogId) })
      }
    });
  } catch (_) {
    // 触达已完成；资产立项只是可选后续入口，路由失败不影响已完成结果。
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
  const value = log?.participantEmpIds ?? log?.participants ?? log?.participantNames;
  if (Array.isArray(value)) return value.filter(Boolean).map(item => item?.name || item?.empId || item).join('、');
  if (value === undefined || value === null || value === '') return '';
  const text = String(value).trim();
  try {
    const parsed = JSON.parse(text);
    if (Array.isArray(parsed)) return parsed.filter(Boolean).map(item => item?.name || item?.empId || item).join('、');
  } catch (_) { /* 非 JSON 的历史字段按逗号分隔处理。 */ }
  return text.split(/[,，]/).map(item => item.trim()).filter(Boolean).join('、');
}

function hasPhotos(log) {
  return Object.values(logPhotoGroups(log)).some(items => items.length > 0);
}

watch(() => [props.modelValue, props.taskId], ([show]) => { if (show) load(); });
</script>

<style scoped lang="scss">
.task-overview { display:grid; gap:12px; }
.task-banner { display:grid; grid-template-columns:2fr 1fr 1fr; gap:10px; padding:14px; border:1px solid var(--color-border, #ebeef5); border-radius:4px; background:var(--color-surface-soft, #f7f9fc); }
.task-banner-main,.task-banner-item { display:grid; align-content:center; gap:4px; min-width:0; }
.task-banner-main strong { overflow:hidden; color:var(--color-text-strong, #303133); font-size:16px; text-overflow:ellipsis; white-space:nowrap; }
.task-banner-main small,.eyebrow { color:var(--color-text-muted, #909399); font-size:11px; }
.task-banner-item .el-tag { justify-self:start; }
.form-grid { display:grid; grid-template-columns:1fr 1fr; gap:12px; }
.log-form { margin-top:18px; padding:16px; border:1px solid var(--color-border, #ebeef5); border-radius:4px; background:var(--color-surface-soft, #f7f9fc); }
.section-heading,.history-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:12px; }
.section-heading h3,.history-title { margin:0 0 4px; border-left:3px solid var(--color-brand-700); padding-left:8px; color:var(--color-text-strong); font-size:15px; }
.section-heading p,.history-heading p { margin:0; color:var(--color-text-muted, #909399); font-size:12px; }
.section-heading .el-tag,.history-heading .el-tag { flex:none; }
.task-actions { display:flex; justify-content:flex-end; gap:8px; margin-top:14px; }
.read-only-notice { margin:18px 0 0; padding:10px 12px; border:1px solid var(--color-border, #ebeef5); border-radius:4px; color:var(--color-text-muted, #606266); background:var(--color-surface-soft, #f7f9fc); font-size:12px; }
.history-heading { align-items:flex-end; margin-top:22px; }
.photo-groups { width:100%; display:grid; grid-template-columns:repeat(3,1fr); gap:10px; }
.photo-group { border:1px solid var(--color-border, #ebeef5); background:var(--color-surface, #fff); padding:10px; border-radius:4px; }
.photo-title { font-size:12px; color:var(--color-text-muted); margin-bottom:8px; }
.photo-list { display:flex; gap:6px; flex-wrap:wrap; align-items:center; }
.location-field { display:grid; grid-template-columns:minmax(0, 1fr) auto; gap:8px; align-items:start; width:100%; }
.location-message { margin:6px 0 0; color:var(--color-text-muted, #909399); font-size:12px; line-height:1.5; }
.photo-item { position:relative; width:58px; height:58px; }
.photo-item .el-image,.history-photos .el-image { width:58px; height:58px; border-radius:4px; }
.photo-item button { position:absolute; right:-5px; top:-7px; border:0; border-radius:50%; color:#fff; background:#f56c6c; cursor:pointer; }
.log-head { color:var(--color-text-strong, #303133); font-weight:600; margin-bottom:6px; }
.log-content { white-space:pre-wrap; margin-bottom:6px; }
.muted { color:var(--color-text-muted, #909399); font-size:12px; margin-top:4px; }
.history-photos { display:flex; gap:6px; margin-top:8px; flex-wrap:wrap; }
@media (max-width:800px) { .form-grid,.photo-groups,.task-banner { grid-template-columns:1fr; } .location-field { grid-template-columns:1fr; } .section-heading,.history-heading { align-items:flex-start; flex-direction:column; } }
</style>
