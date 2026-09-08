<template>
  <section class="panorama-integration-readiness" data-testid="integration-readiness" aria-labelledby="integration-readiness-title">
    <header class="readiness-header">
      <div>
        <span class="readiness-eyebrow">接入预检</span>
        <h2 id="integration-readiness-title">真实数据接入检查</h2>
        <p class="readiness-disclaimer" data-testid="readiness-disclaimer">{{ readiness.disclaimer }}</p>
      </div>
      <div class="readiness-summary" data-testid="readiness-summary" aria-live="polite">
        <strong>{{ readiness.slotCount }} 个槽位</strong>
        <span>结构可用 {{ readiness.structurallyAvailableCount }}</span>
        <span>未配置 {{ readiness.unconfiguredCount }}</span>
        <span>配置错误 {{ readiness.configErrorCount }}</span>
      </div>
    </header>

    <div class="readiness-legend" aria-label="接入检查状态说明">
      <span class="readiness-status status-unconfigured">未配置</span>
      <span class="readiness-status status-available">结构可用，待核对</span>
      <span class="readiness-status status-error">配置错误</span>
    </div>

    <div class="readiness-table-wrap">
      <table class="readiness-table">
        <caption class="visually-hidden">{{ readiness.slotCount }} 个经营大屏绑定槽位的静态接入检查</caption>
        <thead>
          <tr>
            <th scope="col">槽位</th>
            <th scope="col">结构状态</th>
            <th scope="col">来源</th>
            <th scope="col">已映射字段</th>
            <th scope="col">原始单位</th>
            <th scope="col">周期</th>
            <th scope="col">风险与待核对项</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="entry in readiness.entries" :key="entry.slot" :data-testid="`readiness-slot-${entry.slot}`">
            <th scope="row">{{ entry.label }}</th>
            <td>
              <span :class="['readiness-status', statusClass(entry.status)]" :data-testid="`readiness-status-${entry.slot}`">
                {{ entry.statusLabel }}
              </span>
            </td>
            <td>
              <span v-if="entry.datasourceName">{{ entry.datasourceName }}</span>
              <span v-else class="readiness-muted">未配置</span>
            </td>
            <td>
              <ul v-if="entry.mappedFields.length" class="readiness-inline-list">
                <li v-for="field in entry.mappedFields" :key="field.semantic">
                  {{ field.label }}：{{ field.column }}
                </li>
              </ul>
              <span v-else class="readiness-muted">—</span>
            </td>
            <td>
              <ul v-if="entry.originalUnits.length" class="readiness-inline-list">
                <li v-for="unit in entry.originalUnits" :key="unit.semantic">
                  {{ unit.semantic }}：{{ unit.label }}
                </li>
              </ul>
              <span v-else class="readiness-muted">—</span>
            </td>
            <td>{{ entry.periodLabel || entry.period || '—' }}</td>
            <td>
              <ul v-if="entry.issues.length || entry.pendingChecks.length" class="readiness-issue-list">
                <li v-for="issue in entry.issues" :key="`error-${issue.code}-${issue.message}`" class="readiness-issue-error">{{ issue.message }}</li>
                <li v-for="check in entry.pendingChecks" :key="`pending-${check.code}-${check.message}`" class="readiness-issue-pending">{{ check.message }}</li>
              </ul>
              <span v-else class="readiness-muted">—</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <section class="readiness-org-checks" aria-labelledby="readiness-org-title">
      <header class="readiness-org-header">
        <div>
          <h3 id="readiness-org-title">机构档案与机构组检查</h3>
          <p>以下检查不会自动运行；点击后才读取管理接口。结果仅用于发现档案/直接成员缺口，不代表大屏已取数或已发布。</p>
        </div>
        <div class="readiness-org-links">
          <a href="#/screen-admin/org-profiles" data-testid="goto-org-profiles" @click.prevent="go('/screen-admin/org-profiles')">管理机构画像</a>
          <a href="#/screen-admin/org-groups" data-testid="goto-org-groups" @click.prevent="go('/screen-admin/org-groups')">管理机构组</a>
        </div>
      </header>

      <div class="readiness-check-actions">
        <button type="button" data-testid="check-org-profiles" :disabled="profileCheck.loading" @click="checkOrgProfiles">
          {{ profileCheck.loading ? '正在检查机构画像…' : '检查机构画像' }}
        </button>
        <button type="button" data-testid="check-org-group" :disabled="groupCheck.loading || !configuredGroupCode" @click="checkOrgGroup">
          {{ groupCheck.loading ? '正在检查机构组…' : '检查当前屏机构组' }}
        </button>
        <span v-if="!configuredGroupCode" class="readiness-muted">当前屏未配置命名机构组</span>
      </div>

      <div v-if="profileCheck.loading" class="readiness-check-state" data-testid="org-profile-check-loading" role="status">正在读取机构画像检查所需的管理目录…</div>
      <div v-else-if="profileCheck.error" class="readiness-check-error" data-testid="org-profile-check-error" role="alert">{{ profileCheck.error }}</div>
      <div v-else-if="profileCheck.result" class="readiness-check-result" data-testid="org-profile-check-result">
        <p class="readiness-check-summary">机构画像：共 {{ profileCheck.result.total }} 条；已配置 {{ profileCheck.result.profileConfigured }} 条；缺画像 {{ profileCheck.result.missingProfile }} 条；启用 {{ profileCheck.result.enabled }} 条；停用 {{ profileCheck.result.disabled }} 条；定位 {{ profileCheck.result.located }} 条；缺坐标 {{ profileCheck.result.missingCoordinates }} 条。</p>
        <ul v-if="profileCheck.result.entries?.length" class="readiness-profile-list">
          <li v-for="entry in profileCheck.result.entries" :key="entry.orgCode">
            <strong>{{ entry.orgName || entry.orgCode }}</strong><span>（{{ entry.orgCode }}）</span>
            <span v-if="entry.issues?.length">：{{ entry.issues.map(profileIssueLabel).join('、') }}</span>
          </li>
        </ul>
      </div>

      <div v-if="groupCheck.loading" class="readiness-check-state" data-testid="org-group-check-loading" role="status">正在读取机构组检查所需的管理目录…</div>
      <div v-else-if="groupCheck.error" class="readiness-check-error" data-testid="org-group-check-error" role="alert">{{ groupCheck.error }}</div>
      <div v-else-if="groupCheck.result" class="readiness-check-result" data-testid="org-group-check-result">
        <p class="readiness-check-summary">
          当前屏配置机构组：<strong>{{ groupCheck.result.groupName || groupCheck.result.groupCode }}</strong>
          （{{ groupCheck.result.groupCode }}），状态 {{ groupCheck.result.statusLabel }}，直接成员 {{ groupCheck.result.directMemberCodes.length }} 个。
        </p>
        <p v-if="groupCheck.result.directMemberCodes.length" class="readiness-direct-members">直接成员：{{ groupCheck.result.directMemberCodes.join('、') }}</p>
        <p v-if="groupCheck.result.issues.length" class="readiness-issue-error">{{ groupCheck.result.issues.join('；') }}</p>
      </div>
    </section>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { listOrgGroups, listOrgProfiles } from '@/api/screen';
import { analyzeOrgProfiles } from '@/utils/orgProfileReadiness';
import { analyzeIntegrationReadiness } from './integrationReadiness';

const props = defineProps({
  slotOrder: { type: Array, default: undefined },
  screens: { type: Array, default: () => [] },
  screen: { type: Object, default: null },
  canvas: { type: Object, default: null },
  datasources: { type: Array, default: () => [] },
  bindingState: { type: Object, default: () => ({}) }
});

const router = useRouter();
const disposed = ref(false);
const checkGeneration = ref(0);
const readiness = computed(() => analyzeIntegrationReadiness({
  slotOrder: props.slotOrder,
  screens: props.screens,
  screen: props.screen,
  canvas: props.canvas,
  datasources: props.datasources,
  bindingState: props.bindingState
}));

const profileCheck = reactive({ loading: false, error: '', result: null });
const groupCheck = reactive({ loading: false, error: '', result: null });

const screenIdentity = computed(() => [
  props.screen?.id || props.screen?.screenId || props.screen?.screen_id || '',
  props.screen?.screenCode || props.screen?.screen_code || '',
  props.screen?.bizLine || props.screen?.biz_line || '',
  props.screen?.orgScopeMode || props.screen?.org_scope_mode || '',
  props.screen?.orgGroupCode || props.screen?.org_group_code || '',
  props.canvas?.canvasVersion || props.canvas?.canvas_version || ''
].join('|'));

function statusClass(status) {
  return {
    UNCONFIGURED: 'status-unconfigured',
    STRUCTURALLY_AVAILABLE: 'status-available',
    CONFIG_ERROR: 'status-error'
  }[status] || 'status-error';
}

function rowsOf(result, subject) {
  if (Array.isArray(result)) return result;
  if (result && Array.isArray(result.records)) return result.records;
  if (result && Array.isArray(result.list)) return result.list;
  throw new Error(`${subject || '管理目录'}接口返回格式无法识别；期望数组、records 或 list`);
}

function statusCode(error) {
  const value = error?.response?.status ?? error?.status ?? error?.code;
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : null;
}

function requestError(subject, error) {
  if (statusCode(error) === 403 || String(error?.code || '').toUpperCase() === 'FORBIDDEN') {
    return `${subject}检查被拒绝（403）。当前管理账号没有读取权限，旧检查结果已清空。`;
  }
  return `${subject}检查失败：${error?.message || '管理接口请求失败'}；旧检查结果已清空。`;
}

function configuredGroupCodeOf(screen) {
  const mode = String(screen?.orgScopeMode || screen?.org_scope_mode || '').toUpperCase();
  if (mode !== 'NAMED_GROUP') return '';
  return String(screen?.orgGroupCode || screen?.org_group_code || '').trim();
}

const configuredGroupCode = computed(() => configuredGroupCodeOf(props.screen));

function directMemberCodesOf(group) {
  const raw = group?.memberOrgCodes ?? group?.member_org_codes ?? group?.orgCodes ?? group?.org_codes;
  if (Array.isArray(raw)) return raw.map(item => String(item?.orgCode || item?.org_code || item || '').trim()).filter(Boolean);
  if (Array.isArray(group?.members)) {
    return group.members.map(item => String(item?.orgCode || item?.org_code || item || '').trim()).filter(Boolean);
  }
  return [];
}

function groupStatusLabel(group) {
  const status = String(group?.status || '').toUpperCase();
  if (status === 'ACTIVE') return '启用';
  if (status === 'DISABLED') return '停用';
  return status ? `未知（${status}）` : '未知';
}

const PROFILE_ISSUE_LABELS = Object.freeze({
  UNCONFIGURED: '未配置画像',
  UNKNOWN_STATUS: '画像状态待核验',
  MISSING_COORDINATES: '待定位',
  MISSING_CITY: '城市编码待维护',
  MISSING_ADDRESS: '地址需在地址与定位中核对',
  SCREEN_MAP_DEMO: '坐标带演示标记，需核对'
});

function profileIssueLabel(issue) {
  const key = String(issue || '').trim();
  return PROFILE_ISSUE_LABELS[key] || '问题待核验';
}

async function checkOrgProfiles() {
  const token = ++checkGeneration.value;
  const screenToken = screenIdentity.value;
  // 两类检查共用一条“当前屏”代际；点击新的检查即废弃另一类旧结果，
  // 并立即释放其 loading，迟到响应仍由 token 校验丢弃。
  groupCheck.loading = false;
  groupCheck.error = '';
  groupCheck.result = null;
  profileCheck.loading = true;
  profileCheck.error = '';
  profileCheck.result = null;
  try {
    const result = await listOrgProfiles({});
    if (disposed.value || token !== checkGeneration.value || screenToken !== screenIdentity.value) return;
    profileCheck.result = analyzeOrgProfiles(rowsOf(result, '机构画像'));
    if (disposed.value || token !== checkGeneration.value || screenToken !== screenIdentity.value) {
      profileCheck.result = null;
      return;
    }
  } catch (error) {
    if (disposed.value || token !== checkGeneration.value || screenToken !== screenIdentity.value) return;
    profileCheck.result = null;
    profileCheck.error = requestError('机构画像', error);
  } finally {
    // 另一类检查可能推进共享代际；当前请求既然已被废弃，也必须释放自己的 loading，
    // 否则用户会看到按钮永久禁用。屏幕已经切换时 resetChecks 已经负责清空状态。
    if (!disposed.value && screenToken === screenIdentity.value) profileCheck.loading = false;
  }
}

async function checkOrgGroup() {
  const groupCode = configuredGroupCode.value;
  if (!groupCode) return;
  const token = ++checkGeneration.value;
  const screenToken = screenIdentity.value;
  profileCheck.loading = false;
  profileCheck.error = '';
  profileCheck.result = null;
  groupCheck.loading = true;
  groupCheck.error = '';
  groupCheck.result = null;
  try {
    const result = await listOrgGroups({});
    if (disposed.value || token !== checkGeneration.value || screenToken !== screenIdentity.value) return;
    const group = rowsOf(result, '机构组').find(item => String(item?.groupCode || item?.group_code || '').trim() === groupCode);
    if (!group) {
      groupCheck.error = `当前屏配置的机构组 ${groupCode} 未在管理接口返回，不能按组名推断成员；旧检查结果已清空。`;
      return;
    }
    const hasMemberField = Array.isArray(group.memberOrgCodes)
      || Array.isArray(group.member_org_codes)
      || Array.isArray(group.orgCodes)
      || Array.isArray(group.org_codes)
      || Array.isArray(group.members);
    groupCheck.result = {
      groupCode,
      groupName: String(group.groupName || group.group_name || '').trim(),
      statusLabel: groupStatusLabel(group),
      directMemberCodes: directMemberCodesOf(group),
      issues: hasMemberField ? [] : ['接口未返回直接成员，不能以组名推断或自动补齐成员']
    };
  } catch (error) {
    if (disposed.value || token !== checkGeneration.value || screenToken !== screenIdentity.value) return;
    groupCheck.result = null;
    groupCheck.error = requestError('机构组', error);
  } finally {
    if (!disposed.value && screenToken === screenIdentity.value) groupCheck.loading = false;
  }
}

function resetChecks() {
  ++checkGeneration.value;
  profileCheck.loading = false;
  profileCheck.error = '';
  profileCheck.result = null;
  groupCheck.loading = false;
  groupCheck.error = '';
  groupCheck.result = null;
}

function go(path) {
  if (router?.push) router.push(path);
}

watch(screenIdentity, resetChecks);

onBeforeUnmount(() => {
  disposed.value = true;
  ++checkGeneration.value;
  profileCheck.result = null;
  groupCheck.result = null;
});

defineExpose({ checkOrgProfiles, checkOrgGroup, resetChecks, profileCheck, groupCheck });
</script>

<style scoped>
.panorama-integration-readiness { max-width: 1240px; margin: 16px auto 0; padding: 16px; color: #1f2d3d; background: #fff; border: 1px solid #e3eaf2; border-radius: 8px; }
.readiness-header, .readiness-org-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.readiness-eyebrow { color: #4767d8; font-size: 11px; letter-spacing: 1.5px; }
.readiness-header h2, .readiness-org-header h3 { margin: 4px 0; }
.readiness-header p, .readiness-org-header p { margin: 0; color: #718096; font-size: 12px; line-height: 18px; }
.readiness-disclaimer { max-width: 840px; }
.readiness-summary { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px 12px; color: #718096; font-size: 12px; }
.readiness-summary strong { color: #1f2d3d; }
.readiness-legend { display: flex; flex-wrap: wrap; gap: 8px; margin: 14px 0 10px; }
.readiness-status { display: inline-block; padding: 3px 7px; border-radius: 999px; font-size: 11px; white-space: nowrap; }
.status-unconfigured { color: #718096; background: #f1f4f8; }
.status-available { color: #176b50; background: #eaf8f1; }
.status-error { color: #a11a2b; background: #fff0f1; }
.readiness-table-wrap { overflow-x: auto; }
.readiness-table { width: 100%; min-width: 940px; border-collapse: collapse; font-size: 12px; }
.readiness-table th, .readiness-table td { padding: 9px 8px; border-top: 1px solid #edf1f5; text-align: left; vertical-align: top; }
.readiness-table thead th { color: #718096; font-weight: 600; background: #f8fafc; border-top: 0; }
.readiness-table tbody th { min-width: 88px; font-weight: 600; }
.readiness-inline-list, .readiness-issue-list, .readiness-profile-list { padding: 0; margin: 0; list-style: none; }
.readiness-inline-list li, .readiness-issue-list li { line-height: 18px; white-space: nowrap; }
.readiness-muted { color: #9aa8b8; }
.readiness-issue-error { color: #a11a2b; }
.readiness-issue-pending { color: #8b5b00; }
.readiness-org-checks { margin-top: 18px; padding-top: 16px; border-top: 1px solid #edf1f5; }
.readiness-org-links { display: flex; gap: 12px; font-size: 12px; white-space: nowrap; }
.readiness-org-links a { color: #2f55ce; }
.readiness-check-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-top: 12px; }
button { min-height: 32px; padding: 0 10px; color: inherit; background: #fff; border: 1px solid #cad5e2; border-radius: 6px; cursor: pointer; }
button:disabled { cursor: not-allowed; opacity: .55; }
.readiness-check-state, .readiness-check-error, .readiness-check-result { margin-top: 10px; padding: 9px 10px; border-radius: 6px; font-size: 12px; line-height: 18px; }
.readiness-check-state { color: #4767d8; background: #f1f4ff; }
.readiness-check-error { color: #a11a2b; background: #fff0f1; }
.readiness-check-result { color: #38506b; background: #f8fafc; }
.readiness-check-summary, .readiness-direct-members { margin: 0; }
.readiness-profile-list { margin-top: 6px; display: grid; gap: 3px; }
.readiness-profile-list span { color: #718096; }
.visually-hidden { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
@media (max-width: 760px) { .readiness-header, .readiness-org-header { display: block; } .readiness-summary { justify-content: flex-start; margin-top: 10px; } .readiness-org-links { margin-top: 10px; } }
</style>
