<template>
  <section v-if="visible" class="panorama-settings" data-testid="panorama-settings" aria-labelledby="panorama-settings-title">
    <header class="panorama-settings__header">
      <div>
        <span class="panorama-settings__eyebrow">SCREEN SETTINGS</span>
        <h2 id="panorama-settings-title">经营全景设置</h2>
        <p>{{ editing ? '编辑当前大屏元数据、访问范围和发布归档。' : '创建一个空画布大屏，保存后再绑定经营全景槽位。' }}</p>
      </div>
      <button type="button" class="panorama-settings__close" data-testid="settings-close" aria-label="关闭设置" @click="close">×</button>
    </header>

    <p v-if="error" class="panorama-settings__error" data-testid="settings-error" role="alert">{{ error }}</p>
    <p v-if="loading" class="panorama-settings__loading" role="status">正在读取设置…</p>

    <form class="panorama-settings__form" @submit.prevent="saveMetadata">
      <fieldset>
        <legend>基本信息</legend>
        <label for="settings-screen-name">屏名称</label>
        <input id="settings-screen-name" data-testid="settings-screen-name" v-model="form.screenName" maxlength="50" autocomplete="off" />

        <label for="settings-screen-code">屏编码</label>
        <input id="settings-screen-code" data-testid="settings-screen-code" v-model="form.screenCode" maxlength="100" autocomplete="off"
               placeholder="留空由服务端生成 SCR_ 编码" />

        <label for="settings-view-level">查看层级</label>
        <select id="settings-view-level" data-testid="settings-view-level" v-model="form.viewLevel">
          <option v-for="level in VIEW_LEVELS" :key="level.value" :value="level.value">{{ level.label }}</option>
        </select>

        <label for="settings-business-domain">业务条线</label>
        <select id="settings-business-domain" data-testid="settings-business-domain" v-model="form.bizLine">
          <option value="">请选择业务条线</option>
          <option v-for="line in BIZ_LINES" :key="line.value" :value="line.value">{{ line.label }}</option>
        </select>

        <label for="settings-scope-mode">机构范围模式</label>
        <select id="settings-scope-mode" data-testid="settings-scope-mode" v-model="form.orgScopeMode">
          <option value="">请选择机构范围模式</option>
          <option v-for="mode in ORG_SCOPE_MODES" :key="mode.value" :value="mode.value">{{ mode.label }}</option>
        </select>

        <template v-if="form.orgScopeMode === 'NAMED_GROUP'">
          <label for="settings-org-group">命名机构组</label>
          <select id="settings-org-group" data-testid="settings-org-group" v-model="form.orgGroupCode">
            <option value="">请选择已启用机构组</option>
            <option v-for="group in orgGroups" :key="group.groupCode" :value="group.groupCode">
              {{ group.groupName || group.groupCode }}（{{ group.groupCode }}）
            </option>
          </select>
        </template>
      </fieldset>

      <fieldset v-if="editing">
        <legend>元数据审计</legend>
        <p class="panorama-settings__version">当前配置版本：{{ expectedVersion ?? '未知' }}</p>
        <label for="settings-reason">变更原因（审计）</label>
        <textarea id="settings-reason" data-testid="settings-reason" v-model="form.reason" maxlength="500"
                  placeholder="元数据或机构范围变更必须填写原因" />
      </fieldset>

      <div class="panorama-settings__actions">
        <button type="button" data-testid="settings-cancel" @click="close">取消</button>
        <button type="button" data-testid="settings-save" :disabled="saving || loading || !settingsReady" @click="saveMetadata">{{ saving ? '保存中…' : (editing ? '保存元数据' : '创建大屏') }}</button>
      </div>
    </form>

    <section v-if="editing" class="panorama-settings__section" data-testid="settings-roles">
      <header>
        <h3>查看角色白名单</h3>
        <span>角色权限通过独立高危接口保存</span>
      </header>
      <div v-if="roleOptions.length" class="panorama-settings__roles">
        <label v-for="role in roleOptions" :key="roleCodeOf(role)" class="panorama-settings__role">
          <input :data-testid="`role-${roleCodeOf(role)}`" v-model="roleCodes" type="checkbox" :value="roleCodeOf(role)" />
          <span>{{ roleNameOf(role) }}</span>
          <small>{{ roleCodeOf(role) }}</small>
        </label>
      </div>
      <p v-else class="panorama-settings__hint">暂无可配置的角色候选。</p>
      <label for="settings-role-reason">权限变更原因（审计）</label>
      <textarea id="settings-role-reason" data-testid="settings-role-reason" v-model="roleReason" maxlength="500"
                placeholder="查看角色白名单变更必须填写原因" />
      <button type="button" data-testid="settings-save-roles" :disabled="rolesSaving || loading || !settingsReady || !roleAccessReady" @click="saveRoles">
        {{ rolesSaving ? '保存中…' : '保存角色白名单' }}
      </button>
    </section>

    <section v-if="editing" class="panorama-settings__section" data-testid="settings-rollback-section">
      <header>
        <h3>发布日志与回滚</h3>
        <span>回滚会立即恢复选中的发布归档为当前发布版本，并同步覆盖草稿与绑定。</span>
      </header>
      <label for="settings-publish-log">发布归档</label>
      <select id="settings-publish-log" data-testid="settings-publish-log" v-model="rollback.publishLogId">
        <option value="">请选择发布归档</option>
        <option v-for="archive in publishLogs" :key="archive.id" :value="String(archive.id)">
          {{ archiveLabel(archive) }}
        </option>
      </select>
      <p v-if="!publishLogs.length" class="panorama-settings__hint">暂无可回滚的发布归档。</p>
      <label for="settings-rollback-reason">回滚原因（审计）</label>
      <textarea id="settings-rollback-reason" data-testid="settings-rollback-reason" v-model="rollback.reason" maxlength="500"
                placeholder="回滚大屏必须填写原因" />
      <button type="button" data-testid="settings-rollback" :disabled="rollbackSaving || loading || !settingsReady" @click="rollbackCanvas">
        {{ rollbackSaving ? '回滚中…' : '回滚选中归档' }}
      </button>
    </section>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue';
import {
  getScreenCanvas,
  listOrgGroups,
  listScreenAccessRoles,
  listScreenPublishLogs,
  listScreenRoles,
  rollbackScreenCanvas,
  saveScreenAccessRoles,
  saveScreenMetadata
} from '@/api/screen';
import {
  BIZ_LINES,
  ORG_SCOPE_MODES,
  VIEW_LEVELS,
  filterActiveOrgGroups,
  filterReportScreenOrgGroups,
  normalizeScreenScope,
  validateScreenScope
} from '@/utils/screenScope';

const props = defineProps({
  screen: { type: Object, default: null },
  visible: { type: Boolean, default: false }
});
const emit = defineEmits(['update:visible', 'saved']);

const form = reactive(emptyForm());
const orgGroups = ref([]);
const roleOptions = ref([]);
const roleCodes = ref([]);
const publishLogs = ref([]);
const expectedVersion = ref(null);
const loading = ref(false);
const saving = ref(false);
const rolesSaving = ref(false);
const rollbackSaving = ref(false);
const error = ref('');
const roleReason = ref('');
const rollback = reactive({ publishLogId: '', reason: '' });
const settingsReady = ref(false);
const roleAccessReady = ref(false);
let loadGeneration = 0;
let disposed = false;

const editing = computed(() => validId(props.screen?.id) !== null);

function emptyForm() {
  return {
    screenName: '',
    screenCode: '',
    viewLevel: 'BRANCH',
    bizLine: '',
    orgScopeMode: '',
    orgGroupCode: '',
    themeJson: null,
    status: '',
    reason: ''
  };
}

function validId(value) {
  const number = Number(value);
  return Number.isSafeInteger(number) && number > 0 ? number : null;
}

function safeVersion(value) {
  if (value === null || value === undefined || (typeof value === 'string' && value.trim() === '')) return null;
  const number = Number(value);
  return Number.isSafeInteger(number) && number >= 0 ? number : null;
}

function pick(value, ...keys) {
  for (const key of keys) {
    if (value?.[key] !== undefined && value?.[key] !== null) return value[key];
  }
  return undefined;
}

function resetForm(screen) {
  const current = screen && validId(screen.id) ? screen : null;
  const scope = normalizeScreenScope(current || {});
  Object.assign(form, emptyForm(), {
    screenName: String(pick(current, 'screenName', 'screen_name') || ''),
    screenCode: String(pick(current, 'screenCode', 'screen_code') || ''),
    viewLevel: String(pick(current, 'viewLevel', 'view_level') || (current ? scope.viewLevel : 'BRANCH')),
    bizLine: current ? String(pick(current, 'bizLine', 'biz_line', 'businessDomain', 'business_domain') || scope.bizLine || '') : '',
    orgScopeMode: current
      ? String(pick(current, 'orgScopeMode', 'org_scope_mode', 'scopeMode') || scope.orgScopeMode || '')
      : '',
    orgGroupCode: String(pick(current, 'orgGroupCode', 'org_group_code') || ''),
    themeJson: pick(current, 'themeJson', 'theme_json') ?? null,
    status: String(pick(current, 'status') || ''),
    reason: ''
  });
  const initialRoles = pick(current, 'allowedRoleCodes', 'accessRoleCodes', 'screenAccessRoleCodes', 'allowed_role_codes');
  roleCodes.value = Array.isArray(initialRoles) ? [...new Set(initialRoles.filter(Boolean).map(String))] : [];
  orgGroups.value = [];
  roleOptions.value = [];
  roleReason.value = '';
  expectedVersion.value = safeVersion(pick(current, 'canvasVersion', 'canvas_version'));
  publishLogs.value = [];
  rollback.publishLogId = '';
  rollback.reason = '';
  error.value = '';
  settingsReady.value = false;
  roleAccessReady.value = false;
}

async function loadSettings() {
  if (!props.visible) return;
  const token = ++loadGeneration;
  const target = props.screen;
  const targetId = validId(target?.id);
  resetForm(target);
  loading.value = true;
  try {
    const id = targetId;
    const canvasPromise = id && expectedVersion.value === null
      ? Promise.resolve().then(() => getScreenCanvas(id))
      : Promise.resolve(null);
    const settled = await Promise.allSettled([
      Promise.resolve().then(() => listOrgGroups({ status: 'ACTIVE' })),
      Promise.resolve().then(() => listScreenRoles({ recordStatus: 0 })),
      id ? Promise.resolve().then(() => listScreenAccessRoles(id)) : Promise.resolve([]),
      id ? Promise.resolve().then(() => listScreenPublishLogs(id)) : Promise.resolve([]),
      canvasPromise
    ]);
    if (!isCurrentTarget(target, token)) return;
    const [groupsState, rolesState, accessState, logsState, canvasState] = settled;
    const groupsResult = groupsState.status === 'fulfilled' ? groupsState.value : [];
    const rolesResult = rolesState.status === 'fulfilled' ? rolesState.value : [];
    const accessResult = accessState.status === 'fulfilled' ? accessState.value : null;
    const logsResult = logsState.status === 'fulfilled' ? logsState.value : [];
    const canvasResult = canvasState.status === 'fulfilled' ? canvasState.value : null;
    const groups = Array.isArray(groupsResult) ? groupsResult : (groupsResult?.records || []);
    orgGroups.value = filterActiveOrgGroups(filterReportScreenOrgGroups(groups));
    if (groupsState.status === 'rejected') {
      error.value = appendError(error.value, `读取机构组失败：${groupsState.reason?.message || '接口请求失败'}`);
    }
    const roles = Array.isArray(rolesResult) ? rolesResult : (Array.isArray(rolesResult?.records) ? rolesResult.records : null);
    const roleCandidatesReady = rolesState.status === 'fulfilled' && Array.isArray(roles);
    roleOptions.value = roleCandidatesReady ? roles.filter(Boolean) : [];
    if (!roleCandidatesReady && id) {
      roleAccessReady.value = false;
      const roleReason = rolesState.status === 'rejected'
        ? rolesState.reason?.message || '接口请求失败'
        : '服务端未返回角色候选';
      error.value = appendError(error.value, `读取角色候选失败：${roleReason}；已禁止保存角色权限`);
    }
    if (Array.isArray(accessResult) && roleCandidatesReady) {
      roleCodes.value = [...new Set(accessResult.filter(Boolean).map(String))];
      roleAccessReady.value = true;
    } else if (id) {
      roleAccessReady.value = false;
      if (!Array.isArray(accessResult)) {
        const accessReason = accessState.status === 'rejected'
          ? accessState.reason?.message || '接口请求失败'
          : '服务端未返回角色白名单';
        error.value = appendError(error.value, `读取查看角色白名单失败：${accessReason}；已禁止保存角色权限`);
      }
    }
    const archives = Array.isArray(logsResult) ? logsResult : (logsResult?.records || []);
    publishLogs.value = archives
      .filter(item => validId(item?.id) !== null)
      .slice(0, 10);
    if (logsState.status === 'rejected' && id) {
      error.value = appendError(error.value, `读取发布归档失败：${logsState.reason?.message || '接口请求失败'}`);
    }
    if (expectedVersion.value === null) expectedVersion.value = safeVersion(canvasResult?.canvasVersion ?? canvasResult?.canvas_version);
    // 保留服务端已保存但候选接口暂未返回的角色，避免打开设置时静默丢失勾选状态。
    const selected = new Set(roleCodes.value);
    const missing = [...selected]
      .filter(code => !roleOptions.value.some(role => roleCodeOf(role) === code))
      .map(roleCode => ({ roleCode, roleChName: roleCode }));
    roleOptions.value = [...roleOptions.value, ...missing];
    const versionReady = !id || expectedVersion.value !== null;
    settingsReady.value = versionReady;
    if (id && !versionReady) {
      error.value = appendError(error.value, '未能读取当前配置版本；已禁止写入大屏设置');
    }
  } catch (loadError) {
    if (!isCurrentTarget(target, token)) return;
    error.value = loadError?.message || '读取大屏设置失败';
  } finally {
    if (isCurrentTarget(target, token)) loading.value = false;
  }
}

function isCurrentTarget(target, token) {
  return !disposed && props.visible && token === loadGeneration && props.screen === target;
}

function appendError(current, message) {
  if (!current) return message;
  if (current.includes(message)) return current;
  return `${current}；${message}`;
}

function roleCodeOf(role) {
  return String(pick(role, 'roleCode', 'role_code', 'code', 'roleId', 'role_id') || '');
}

function roleNameOf(role) {
  return String(pick(role, 'roleChName', 'roleName', 'name', 'role_name') || roleCodeOf(role));
}

function archiveLabel(archive) {
  const id = validId(archive?.id);
  const when = archive?.publishedAt || archive?.published_at || '时间未知';
  const by = archive?.publishedBy || archive?.published_by;
  return `${id ? `归档 #${id}` : '无效归档'} · ${when}${by ? ` · ${by}` : ''}`;
}

function close() {
  emit('update:visible', false);
}

function metadataPayload(screen = props.screen) {
  const name = String(form.screenName || '').trim();
  const screenCode = String(form.screenCode || '').trim();
  const editingTarget = validId(screen?.id) !== null;
  const body = {
    screenName: name,
    viewLevel: form.viewLevel,
    bizLine: form.bizLine,
    orgScopeMode: form.orgScopeMode,
    orgGroupCode: form.orgScopeMode === 'NAMED_GROUP' ? String(form.orgGroupCode || '').trim() : (editingTarget ? '' : null)
  };
  if (editingTarget) {
    body.id = validId(screen.id);
    body.screenCode = screenCode;
    if (form.themeJson !== null && form.themeJson !== undefined) body.themeJson = form.themeJson;
    if (form.status === 'ACTIVE' || form.status === 'DISABLED') body.status = form.status;
    body.expectedVersion = expectedVersion.value;
    body.reason = String(form.reason || '').trim();
  } else if (screenCode) {
    body.screenCode = screenCode;
  }
  return body;
}

function validationError(screen = props.screen) {
  if (!String(form.screenName || '').trim()) return '请填写屏名称';
  const errors = validateScreenScope(form);
  if (errors.length) return errors[0];
  const editingTarget = validId(screen?.id) !== null;
  if (editingTarget && expectedVersion.value === null) return '未能读取当前版本，不能覆盖保存元数据';
  if (editingTarget && !String(form.reason || '').trim()) return '元数据变更必须填写原因';
  return '';
}

async function saveMetadata() {
  if (saving.value || loading.value || !settingsReady.value || !props.visible) return false;
  const target = props.screen;
  const token = loadGeneration;
  error.value = validationError(target);
  if (error.value) return false;
  saving.value = true;
  try {
    const payload = metadataPayload(target);
    const response = await saveScreenMetadata(payload);
    if (!isCurrentTarget(target, token)) return false;
    const screenId = validId(target?.id) !== null ? validId(target.id) : validId(response) ?? response;
    emit('saved', { action: 'metadata', screenId, response, payload });
    close();
    return true;
  } catch (saveError) {
    if (!isCurrentTarget(target, token)) return false;
    error.value = saveError?.message || '保存大屏元数据失败';
    return false;
  } finally {
    saving.value = false;
  }
}

async function saveRoles() {
  if (rolesSaving.value || loading.value || !settingsReady.value || !props.visible) return false;
  const target = props.screen;
  const token = loadGeneration;
  const id = validId(target?.id);
  const reason = String(roleReason.value || '').trim();
  if (!id) { error.value = '新建大屏需先保存元数据后再配置角色'; return false; }
  if (!roleAccessReady.value) { error.value = '查看角色白名单读取失败，已禁止保存角色权限'; return false; }
  if (expectedVersion.value === null) { error.value = '未能读取当前版本，不能覆盖保存角色'; return false; }
  if (!reason) { error.value = '查看角色白名单变更必须填写原因'; return false; }
  rolesSaving.value = true;
  try {
    const codes = [...new Set(roleCodes.value.filter(Boolean).map(String))];
    const version = expectedVersion.value;
    await saveScreenAccessRoles(id, { roleCodes: codes, expectedVersion: version, reason });
    if (!isCurrentTarget(target, token)) return false;
    emit('saved', { action: 'roles', screenId: id, roleCodes: codes, expectedVersion: version, reason });
    close();
    return true;
  } catch (saveError) {
    if (!isCurrentTarget(target, token)) return false;
    error.value = saveError?.message || '保存查看角色失败';
    return false;
  } finally {
    rolesSaving.value = false;
  }
}

async function rollbackCanvas() {
  if (rollbackSaving.value || loading.value || !settingsReady.value || !props.visible) return false;
  const target = props.screen;
  const token = loadGeneration;
  const screenId = validId(target?.id);
  const publishLogId = validId(rollback.publishLogId);
  const reason = String(rollback.reason || '').trim();
  if (!screenId) { error.value = '未选择可回滚的大屏'; return false; }
  if (publishLogId === null) { error.value = '请选择发布归档'; return false; }
  if (expectedVersion.value === null) { error.value = '未能读取当前版本，不能回滚'; return false; }
  if (!reason) { error.value = '回滚大屏必须填写原因'; return false; }
  rollbackSaving.value = true;
  try {
    const version = expectedVersion.value;
    await rollbackScreenCanvas({ screenId, publishLogId, expectedVersion: version, reason });
    if (!isCurrentTarget(target, token)) return false;
    emit('saved', { action: 'rollback', screenId, publishLogId, expectedVersion: version, reason });
    close();
    return true;
  } catch (rollbackError) {
    if (!isCurrentTarget(target, token)) return false;
    error.value = rollbackError?.message || '回滚大屏失败';
    return false;
  } finally {
    rollbackSaving.value = false;
  }
}

watch(
  () => [props.visible, props.screen?.id],
  ([isVisible]) => {
    if (isVisible) {
      loadSettings();
    } else {
      // Invalidate an in-flight response when the drawer closes.  Otherwise
      // its finally/catch path could re-enable writes or surface stale errors
      // after the next screen is opened.
      loadGeneration += 1;
      settingsReady.value = false;
      roleAccessReady.value = false;
      loading.value = false;
    }
  },
  { immediate: true }
);

onBeforeUnmount(() => {
  disposed = true;
  loadGeneration += 1;
  loading.value = false;
});

defineExpose({ form, loadSettings, saveMetadata, saveRoles, rollbackCanvas });
</script>

<style scoped>
.panorama-settings { box-sizing: border-box; width: min(720px, 100%); max-height: 100%; overflow: auto; padding: 24px; color: #1f2d3d; background: #f4f7fb; }
.panorama-settings__header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 18px; }
.panorama-settings__eyebrow { color: #4767d8; font-size: 11px; letter-spacing: 1.5px; }
.panorama-settings h2, .panorama-settings h3, .panorama-settings p { margin-top: 0; }
.panorama-settings__header p, .panorama-settings__section header span, .panorama-settings__hint { color: #718096; font-size: 13px; }
.panorama-settings__close { border: 0; background: transparent; font-size: 24px; line-height: 1; cursor: pointer; color: #718096; }
.panorama-settings__error { padding: 10px 12px; border: 1px solid #ffd2d6; border-radius: 6px; background: #fff0f1; color: #a11a2b; }
.panorama-settings__loading { color: #4767d8; }
.panorama-settings__form, .panorama-settings__section { margin-top: 14px; padding: 16px; border: 1px solid #e3eaf2; border-radius: 8px; background: #fff; }
.panorama-settings fieldset { display: grid; grid-template-columns: 130px minmax(0, 1fr); gap: 10px 12px; border: 0; padding: 0; margin: 0 0 16px; }
.panorama-settings legend { grid-column: 1 / -1; width: 100%; padding: 0 0 8px; font-weight: 700; }
.panorama-settings label { align-self: center; font-size: 13px; font-weight: 600; }
.panorama-settings input, .panorama-settings select, .panorama-settings textarea { box-sizing: border-box; width: 100%; min-height: 34px; padding: 7px 9px; border: 1px solid #cad5e2; border-radius: 6px; background: #fff; color: inherit; font: inherit; }
.panorama-settings textarea { min-height: 68px; resize: vertical; }
.panorama-settings__version { grid-column: 1 / -1; margin: 0; color: #718096; font-size: 13px; }
.panorama-settings__actions { display: flex; justify-content: flex-end; gap: 8px; }
.panorama-settings button { min-height: 34px; padding: 0 12px; border: 1px solid #cad5e2; border-radius: 6px; background: #fff; color: inherit; cursor: pointer; }
.panorama-settings [data-testid='settings-save'], .panorama-settings__section > button { border-color: #4767d8; background: #4767d8; color: #fff; }
.panorama-settings button:disabled { opacity: .55; cursor: not-allowed; }
.panorama-settings__section header { display: flex; justify-content: space-between; gap: 12px; align-items: baseline; margin-bottom: 12px; }
.panorama-settings__section h3 { margin-bottom: 0; }
.panorama-settings__roles { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; margin-bottom: 14px; }
.panorama-settings__role { display: flex; gap: 8px; align-items: center; padding: 8px; border: 1px solid #e3eaf2; border-radius: 6px; font-weight: 400 !important; }
.panorama-settings__role input { width: auto; min-height: auto; }
.panorama-settings__role small { margin-left: auto; color: #718096; }
.panorama-settings__section > label, .panorama-settings__section > textarea, .panorama-settings__section > select { display: block; margin-bottom: 8px; }
.panorama-settings__section > textarea { margin-bottom: 12px; }
@media (max-width: 560px) { .panorama-settings { padding: 16px; } .panorama-settings fieldset { grid-template-columns: 1fr; } .panorama-settings label { margin-top: 4px; } .panorama-settings__roles { grid-template-columns: 1fr; } .panorama-settings__section header { display: block; } }
</style>
