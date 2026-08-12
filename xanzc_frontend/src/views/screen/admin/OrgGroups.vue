<template>
  <div class="org-group-page">
    <div class="page-h">
      <PageTitle><span class="sub">保存的是明确机构成员快照；筛选条件不会自动跟随组织变化</span></PageTitle>
      <div class="actions"><el-button @click="reload">刷新</el-button><el-button type="primary" @click="openCreate">+ 新建机构组</el-button></div>
    </div>
    <div class="group-layout" v-loading="loading">
      <section class="card-section group-list">
        <div class="section-title">机构组</div>
        <el-input v-model="keyword" clearable placeholder="搜索编码/名称" class="group-search" />
        <button v-for="group in filteredGroups" :key="group.groupCode" class="group-item" :class="{ selected: selected?.groupCode === group.groupCode }" @click="selectGroup(group)">
          <span>{{ group.groupName }}</span><code>{{ group.groupCode }}</code>
          <el-tag size="small" :type="group.status === 'DISABLED' ? 'info' : 'success'">{{ group.status === 'DISABLED' ? '停用' : '启用' }}</el-tag>
        </button>
        <div v-if="!filteredGroups.length" class="empty">暂无机构组</div>
      </section>

      <section class="card-section member-panel">
        <template v-if="selected">
          <div class="section-title">{{ selected.groupName }} · 直接成员</div>
          <el-form inline size="small" class="member-filters">
            <el-input v-model="memberKeyword" clearable placeholder="机构编码/名称" />
            <el-select v-model="memberNature" clearable placeholder="机构性质"><el-option v-for="item in NATURES" :key="item.value" :label="item.label" :value="item.value" /></el-select>
            <el-select v-model="memberLevel" clearable placeholder="经营等级"><el-option v-for="item in LEVELS" :key="item.value" :label="item.label" :value="item.value" /></el-select>
            <el-input v-model="memberCity" clearable placeholder="城市编码/名称" />
          </el-form>
          <el-table ref="memberTable" :data="filteredProfiles" row-key="orgCode" size="small" height="460" @selection-change="onSelectionChange">
            <el-table-column type="selection" reserve-selection width="45" />
            <el-table-column prop="orgName" label="机构" min-width="150" />
            <el-table-column prop="orgCode" label="编码" width="110" />
            <el-table-column label="性质" width="100"><template #default="{ row }">{{ natureLabel(row.orgNature) }}</template></el-table-column>
            <el-table-column label="经营等级" width="115"><template #default="{ row }">{{ levelLabel(row.operatingLevel) }}</template></el-table-column>
            <el-table-column prop="cityName" label="城市" width="90" />
          </el-table>
          <div class="change-hint" aria-live="polite">
            成员变更：新增 {{ memberDiff.added.length }}，移除 {{ memberDiff.removed.length }}
            <div v-if="memberDiff.added.length || memberDiff.removed.length" class="diff-values">
              <span v-if="memberDiff.added.length">新增：{{ memberDiff.added.map(profileLabel).join('、') }}</span>
              <span v-if="memberDiff.removed.length">移除：{{ memberDiff.removed.map(profileLabel).join('、') }}</span>
            </div>
          </div>
          <div class="save-bar"><span>已选 {{ selectedCodes.length }} 个直接成员</span><el-button type="primary" @click="saveMembers">覆盖保存成员</el-button></div>
        </template>
        <div v-else class="empty">请选择左侧机构组</div>
      </section>

      <section class="card-section role-panel">
        <template v-if="selected">
          <div class="section-title">已选直接成员</div>
          <div class="selected-members" aria-label="已选直接成员">
            <el-tag v-for="code in selectedCodes" :key="code" size="small" effect="plain">
              {{ profileName(code) }}（{{ code }}）
            </el-tag>
            <span v-if="!selectedCodes.length" class="empty-inline">暂无直接成员</span>
          </div>
          <div class="section-title">角色绑定</div>
          <el-select v-model="roleCodes" multiple filterable collapse-tags placeholder="选择有效角色" style="width:100%">
            <el-option v-for="role in roles" :key="role.roleCode || role.roleId" :label="`${role.roleChName || role.roleCode} (${role.roleCode || role.roleId})`" :value="role.roleCode || role.roleId" />
          </el-select>
          <div class="role-hint">角色回答“谁能看”，机构组回答“能看哪些机构”；屏级角色还需单独配置。</div>
          <div class="change-hint" aria-live="polite">
            角色变更：新增 {{ roleDiff.added.length }}，移除 {{ roleDiff.removed.length }}
            <div v-if="roleDiff.added.length || roleDiff.removed.length" class="diff-values">
              <span v-if="roleDiff.added.length">新增：{{ roleDiff.added.map(roleLabel).join('、') }}</span>
              <span v-if="roleDiff.removed.length">移除：{{ roleDiff.removed.map(roleLabel).join('、') }}</span>
            </div>
          </div>
          <div class="save-bar"><span>已选 {{ roleCodes.length }} 个角色</span><el-button type="primary" @click="saveRoles">覆盖保存角色</el-button></div>
        </template>
      </section>
    </div>

    <el-dialog v-model="createDialog.show" title="新建命名机构组" width="520px">
      <el-form label-width="100px"><el-form-item label="组编码" required><el-input v-model="createDialog.form.groupCode" maxlength="64" /></el-form-item><el-form-item label="组名称" required><el-input v-model="createDialog.form.groupName" maxlength="100" /></el-form-item><el-form-item label="用途"><el-input model-value="REPORT_SCREEN" disabled /></el-form-item><el-form-item label="口径说明"><el-input v-model="createDialog.form.remark" type="textarea" /></el-form-item><el-form-item label="创建原因" required><el-input v-model="createDialog.form.reason" maxlength="500" placeholder="请填写本次机构组创建原因" /></el-form-item></el-form>
      <template #footer><el-button @click="createDialog.show = false">取消</el-button><el-button type="primary" @click="create">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  listOrgGroups, createOrgGroup, listOrgProfiles, saveOrgGroupMembers,
  saveOrgGroupRoles, listScreenRoles
} from '@/api/screen';
import { diffCodes, filterReportScreenOrgGroups } from '@/utils/screenScope';

const NATURES = [
  { value: 'DEPARTMENT', label: '部门' }, { value: 'LOCAL_BRANCH', label: '本地经营机构' },
  { value: 'SECONDARY_BRANCH', label: '异地分行' }, { value: 'OUTLET', label: '下属网点' }, { value: 'OTHER', label: '其他' }
];
const LEVELS = [{ value: 'PRIMARY', label: '一级经营机构' }, { value: 'SUBORDINATE', label: '下属机构' }, { value: 'NONE', label: '非经营机构' }];
const groups = ref([]); const profiles = ref([]); const roles = ref([]); const loading = ref(false); const selected = ref(null);
const keyword = ref(''); const memberKeyword = ref(''); const memberNature = ref(''); const memberLevel = ref(''); const memberCity = ref('');
const selectedCodes = ref([]); const roleCodes = ref([]);
const initialSelectedCodes = ref([]); const initialRoleCodes = ref([]);
const memberTable = ref(null);
const syncingTableSelection = ref(false);
const createDialog = reactive({ show: false, form: { groupCode: '', groupName: '', remark: '', reason: '' } });
const filteredGroups = computed(() => groups.value.filter(x => `${x.groupCode} ${x.groupName}`.toLowerCase().includes(keyword.value.toLowerCase())));
const selectedSet = computed(() => new Set(selectedCodes.value));
const filteredProfiles = computed(() => profiles.value.filter(x => {
  const text = `${x.orgCode} ${x.orgName}`.toLowerCase();
  return (!memberKeyword.value || text.includes(memberKeyword.value.toLowerCase()))
    && (!memberNature.value || x.orgNature === memberNature.value)
    && (!memberLevel.value || x.operatingLevel === memberLevel.value)
    && (!memberCity.value || `${x.cityCode || ''} ${x.cityName || ''}`.toLowerCase().includes(memberCity.value.toLowerCase()));
}));
const memberDiff = computed(() => diffCodes(initialSelectedCodes.value, selectedCodes.value));
const roleDiff = computed(() => diffCodes(initialRoleCodes.value, roleCodes.value));
function natureLabel(v) { return NATURES.find(x => x.value === v)?.label || v || '-'; }
function levelLabel(v) { return LEVELS.find(x => x.value === v)?.label || v || '-'; }
function profileName(code) { return profiles.value.find(x => String(x.orgCode) === String(code))?.orgName || code; }
function profileLabel(code) {
  const profile = profiles.value.find(x => String(x.orgCode) === String(code));
  return profile?.orgName ? `${profile.orgName}（${code}）` : String(code);
}
function roleLabel(code) {
  const role = roles.value.find(x => String(x.roleCode || x.roleId) === String(code));
  const name = role?.roleChName || role?.roleCode;
  return name ? `${name}（${role.roleCode || code}）` : String(code);
}
function orgCodesOf(group) { return group?.memberOrgCodes || group?.orgCodes || (group?.members || []).map(x => x.orgCode || x); }
function roleCodesOf(group) { return group?.roleCodes || group?.allowedRoleCodes || (group?.roles || []).map(x => x.roleCode || x); }

/**
 * Element Plus 表格不会根据 selectedCodes 自动回填勾选状态；每次切组/筛选后显式同步，
 * 否则管理员看到的已有成员未勾选，第一次修改任一行会把其余成员误清空。
 */
async function syncTableSelection() {
  await nextTick();
  if (!memberTable.value?.toggleRowSelection) return;
  syncingTableSelection.value = true;
  try {
    for (const row of filteredProfiles.value) {
      memberTable.value.toggleRowSelection(row, selectedSet.value.has(String(row.orgCode)), false);
    }
  } finally {
    syncingTableSelection.value = false;
  }
}

async function reload() {
  loading.value = true;
  try {
    const [g, p, r] = await Promise.all([listOrgGroups({ purpose: 'REPORT_SCREEN' }), listOrgProfiles({}), listScreenRoles({ recordStatus: 0 })]);
    const returnedGroups = Array.isArray(g) ? g : (g?.records || []);
    groups.value = filterReportScreenOrgGroups(returnedGroups);
    profiles.value = Array.isArray(p) ? p : (p?.records || []);
    roles.value = Array.isArray(r) ? r : (r?.records || []);
    if (selected.value) {
      const next = groups.value.find(x => x.groupCode === selected.value.groupCode) || null;
      selected.value = next;
      if (next) selectGroup(next);
    }
  } catch { groups.value = []; profiles.value = []; roles.value = []; }
  finally { loading.value = false; }
}
function selectGroup(group) {
  selected.value = group;
  selectedCodes.value = orgCodesOf(group).map(String);
  roleCodes.value = roleCodesOf(group).map(String);
  initialSelectedCodes.value = [...selectedCodes.value];
  initialRoleCodes.value = [...roleCodes.value];
  syncTableSelection();
}
function onSelectionChange(rows) {
  if (syncingTableSelection.value) return;
  // 过滤条件只改变当前可见行；不可见成员必须保留，避免筛选后保存误删其他成员。
  const visibleCodes = new Set(filteredProfiles.value.map(row => String(row.orgCode)));
  const visibleSelected = new Set(rows.map(row => String(row.orgCode)));
  selectedCodes.value = [
    ...selectedCodes.value.filter(code => !visibleCodes.has(String(code))),
    ...filteredProfiles.value.filter(row => visibleSelected.has(String(row.orgCode))).map(row => String(row.orgCode))
  ];
}
watch(filteredProfiles, () => syncTableSelection(), { flush: 'post' });
async function confirmReason(title) {
  try {
    const reason = await ElMessageBox.prompt('覆盖式保存会替换现有配置，请填写变更原因。', title, { inputPlaceholder: '例如：季度经营机构调整', inputValidator: v => String(v || '').trim() ? true : '变更原因必填' });
    return reason.value;
  } catch { return null; }
}
async function saveMembers() {
  const reason = await confirmReason('确认覆盖机构组成员'); if (!reason || !selected.value) return;
  await saveOrgGroupMembers(selected.value.groupCode, {
    orgCodes: [...selectedCodes.value], version: selected.value.version, reason
  });
  ElMessage.success('成员已保存'); await reload();
}
async function saveRoles() {
  const reason = await confirmReason('确认覆盖机构组角色'); if (!reason || !selected.value) return;
  await saveOrgGroupRoles(selected.value.groupCode, {
    roleCodes: [...roleCodes.value], version: selected.value.version, reason
  });
  ElMessage.success('角色绑定已保存'); await reload();
}
function openCreate() { createDialog.form = { groupCode: '', groupName: '', remark: '', reason: '' }; createDialog.show = true; }
async function create() {
  if (!createDialog.form.groupCode.trim() || !createDialog.form.groupName.trim()) { ElMessage.warning('组编码和名称必填'); return; }
  if (!createDialog.form.reason.trim()) { ElMessage.warning('创建原因必填'); return; }
  await createOrgGroup({ ...createDialog.form, groupPurpose: 'REPORT_SCREEN', status: 'ACTIVE', reason: createDialog.form.reason.trim() });
  createDialog.show = false; ElMessage.success('机构组已创建'); await reload();
}
onMounted(reload);
</script>

<style scoped>
.group-layout { display: grid; grid-template-columns: 250px minmax(420px, 1fr) 280px; gap: 12px; min-height: 620px; }
.group-list, .member-panel, .role-panel { min-width: 0; }
.section-title { color: #1f2d3d; font-weight: 600; margin-bottom: 10px; }
.group-search { margin-bottom: 8px; }
.group-item { width: 100%; display: grid; grid-template-columns: 1fr auto; gap: 3px 6px; text-align: left; padding: 9px; border: 1px solid #ebeef5; background: #fff; cursor: pointer; color: #303133; }
.group-item.selected { border-color: #409eff; background: #ecf5ff; }
.group-item code { color: #909399; font-size: 11px; }
.group-item :deep(.el-tag) { grid-column: 2; grid-row: 1 / span 2; align-self: center; }
.member-filters { display: flex; gap: 8px; margin-bottom: 8px; }
.member-filters .el-input { width: 160px; }
.member-filters .el-select { width: 130px; }
.save-bar { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: 10px; color: #606266; font-size: 12px; }
.role-hint { color: #7d9bc9; font-size: 12px; line-height: 1.6; margin-top: 12px; }
.change-hint { color: #606266; font-size: 12px; margin-top: 8px; }
.diff-values { display: grid; gap: 3px; margin-top: 4px; color: #7d9bc9; overflow-wrap: anywhere; }
.selected-members { display: flex; flex-wrap: wrap; gap: 6px; min-height: 42px; margin-bottom: 18px; align-content: flex-start; }
.empty-inline { color: #909399; font-size: 12px; line-height: 24px; }
.empty { color: #909399; text-align: center; padding: 60px 0; }
@media (max-width: 1200px) { .group-layout { grid-template-columns: 220px minmax(360px, 1fr); } .role-panel { grid-column: 1 / -1; } }
</style>
