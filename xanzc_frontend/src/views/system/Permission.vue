<template>
  <main class="bp-crud permission-page" aria-labelledby="permission-page-title" :aria-busy="pageLoading ? 'true' : 'false'">
    <header class="page-h">
      <PageTitle id="permission-page-title">
        <span class="sub">角色、资源与数据范围均以当前选中角色为准；保存会覆盖该角色原有资源绑定。</span>
      </PageTitle>
      <div class="actions action-group" role="group" aria-label="权限配置操作">
        <el-button @click="reload">刷新权限数据</el-button>
      </div>
    </header>

    <section class="card-section filter-bar" aria-label="权限配置筛选">
      <el-form class="filter-form" inline size="default" aria-label="权限配置筛选条件">
        <el-form-item label="角色">
          <el-input v-model="kw" clearable placeholder="角色名称 / 编码" aria-label="按角色名称或编码筛选" style="width:230px" />
        </el-form-item>
        <el-form-item label="资源">
          <el-input v-model="resKw" clearable placeholder="资源名称 / URL / ID" aria-label="按资源信息筛选" style="width:260px" />
        </el-form-item>
        <el-form-item>
          <el-button @click="resetFilters">清除筛选</el-button>
        </el-form-item>
      </el-form>
    </section>

    <p v-if="loadError" class="error-state" role="alert">
      {{ loadError }} <el-button link type="primary" @click="reload">重试</el-button>
    </p>

    <section class="permission-grid" aria-label="角色资源和数据范围矩阵">
      <section
        class="card-section data-panel permission-col role-col"
        aria-label="角色列表"
        aria-labelledby="permission-roles-heading"
        :aria-busy="rolesLoading ? 'true' : 'false'"
      >
        <div class="toolbar compact-toolbar">
          <div>
            <h2 id="permission-roles-heading" class="section-title">角色</h2>
            <p class="hint">选择一个角色后编辑其资源和数据范围。</p>
          </div>
          <p class="table-state" role="status" aria-live="polite">{{ rolesLoading ? '角色加载中' : `共 ${filteredRoles.length} 个` }}</p>
        </div>
        <div class="role-list" role="list" aria-labelledby="permission-roles-heading">
          <button
            v-for="role in filteredRoles"
            :key="role.roleId"
            type="button"
            :class="['role-option', { active: role.roleId === pickedRoleId, disabled: role.recordStatus !== 0 }]"
            :aria-pressed="role.roleId === pickedRoleId ? 'true' : 'false'"
            :aria-label="`选择角色 ${role.roleChName || role.roleId}`"
            @click="onPickRole(role.roleId)"
          >
            <span class="role-info">
              <span class="role-name">{{ role.roleChName || role.roleId }}</span>
              <code class="role-code">{{ role.roleCode || '-' }}</code>
            </span>
            <span class="role-meta">
              <el-tag v-if="role.recordStatus !== 0" class="tag-warning" effect="plain" size="small">停用</el-tag>
              <span>{{ role.userCount != null ? `${role.userCount} 人` : '-' }}</span>
            </span>
          </button>
          <p v-if="!rolesLoading && !filteredRoles.length" class="empty-state">暂无可配置角色</p>
        </div>
      </section>

      <section
        class="card-section data-panel permission-col resource-col"
        aria-label="资源授权矩阵"
        aria-labelledby="permission-resources-heading"
        :aria-busy="resourcesLoading ? 'true' : 'false'"
      >
        <div class="toolbar compact-toolbar">
          <div>
            <h2 id="permission-resources-heading" class="section-title">资源授权</h2>
            <p class="hint">{{ pickedRole?.roleChName || '请先选择角色' }} · 已授权 {{ checkedIds.size }} / {{ resources.length }} 项</p>
          </div>
          <p class="table-state" role="status" aria-live="polite">{{ resourcesLoading ? '资源加载中' : dirty ? `${pendingDelta} 项待保存` : '已同步' }}</p>
        </div>

        <div class="resource-list" aria-labelledby="permission-resources-heading">
          <section v-for="group in groupedRes" :key="group.key" class="resource-group">
            <div class="resource-group-head">
              <button
                type="button"
                class="collapse-toggle"
                :aria-expanded="collapsedKeys.has(group.key) ? 'false' : 'true'"
                :aria-label="`${collapsedKeys.has(group.key) ? '展开' : '收起'} ${group.name} 资源组`"
                @click="toggleCollapse(group.key)"
              >{{ collapsedKeys.has(group.key) ? '展开' : '收起' }}</button>
              <span class="group-name">{{ group.name }}</span>
              <span class="group-count">读 {{ group.r.checked }}/{{ group.r.items.length }} · 写 {{ group.w.checked }}/{{ group.w.items.length }}</span>
              <span class="group-checks">
                <el-checkbox
                  :model-value="group.all.allChecked"
                  :indeterminate="group.all.indeterminate"
                  :disabled="resourceEditBlocked || group.all.total === 0"
                  aria-label="切换整个资源组"
                  @change="(value) => toggleGroupSide(group, 'all', value)"
                >整组</el-checkbox>
                <el-checkbox
                  :model-value="group.r.allChecked"
                  :indeterminate="group.r.indeterminate"
                  :disabled="resourceEditBlocked || group.r.items.length === 0"
                  aria-label="切换本组全部读取资源"
                  @change="(value) => toggleGroupSide(group, 'r', value)"
                >读</el-checkbox>
                <el-checkbox
                  :model-value="group.w.allChecked"
                  :indeterminate="group.w.indeterminate"
                  :disabled="resourceEditBlocked || group.w.items.length === 0"
                  aria-label="切换本组全部写入资源"
                  @change="(value) => toggleGroupSide(group, 'w', value)"
                >写</el-checkbox>
              </span>
            </div>
            <div v-show="!collapsedKeys.has(group.key)" class="resource-items">
              <div v-for="resource in group.allItems" :key="resource.resourceId" class="resource-item">
                <div class="resource-meta">
                  <span class="resource-name">{{ resource.menuName || resource.resourceId }}</span>
                  <code class="resource-url"><el-tag size="small" :class="methodCls(resource.resourceMethod)" effect="plain" disable-transitions>{{ resource.resourceMethod }}</el-tag>{{ resource.resourceUrl }}</code>
                </div>
                <el-checkbox
                  :model-value="checkedIds.has(resource.resourceId)"
                  :disabled="resourceEditBlocked"
                  :aria-label="`切换资源 ${resource.menuName || resource.resourceId}`"
                  @change="(value) => toggleOne(resource.resourceId, value)"
                >{{ rwSide(resource) === 'r' ? '读取' : '写入' }}</el-checkbox>
              </div>
            </div>
          </section>
          <p v-if="!resourcesLoading && !groupedRes.length" class="empty-state">暂无匹配资源</p>
        </div>

        <div class="resource-foot">
          <p class="hint" role="status" aria-live="polite">{{ dirty ? `资源绑定有 ${pendingDelta} 项改动，保存后将全量替换。` : '当前资源绑定已同步。' }}</p>
          <div class="action-group" role="group" aria-label="资源绑定操作">
            <el-button :disabled="resourceEditBlocked || !dirty || saving" @click="resetChecked">还原</el-button>
            <el-button type="primary" :loading="saving" :disabled="resourceEditBlocked || !dirty || saving || !pickedRoleId" @click="onSaveResources">保存资源绑定</el-button>
          </div>
        </div>
      </section>

      <section
        class="card-section data-panel permission-col scope-col"
        aria-label="数据范围矩阵"
        aria-labelledby="permission-scopes-heading"
        :aria-busy="scopeLoading ? 'true' : 'false'"
      >
        <div class="toolbar compact-toolbar">
          <div>
            <h2 id="permission-scopes-heading" class="section-title">数据范围</h2>
            <p class="hint">{{ pickedRole?.roleChName || '请先选择角色' }} 的业务数据访问边界。</p>
          </div>
          <p class="table-state" role="status" aria-live="polite">{{ scopeLoading ? '范围加载中' : `${scopeRows.length} 个业务域` }}</p>
        </div>
        <el-table :data="scopeRows" size="small" empty-text="无 BizType 配置" v-loading="scopeLoading" aria-labelledby="permission-scopes-heading">
          <el-table-column label="业务域" min-width="150">
            <template #default="{ row }">
              <div class="biz-type-name">{{ row.bizTypeLabel }}</div>
              <code class="biz-type-code">{{ row.bizType }}</code>
            </template>
          </el-table-column>
          <el-table-column label="数据范围" width="112">
            <template #default="{ row }"><el-tag :class="scopeCls(row.dataScope)" effect="plain">{{ scopeLabel(row.dataScope) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="72" fixed="right">
            <template #default="{ row }"><el-button link type="primary" size="small" @click="openScopeEditor(row)">修改</el-button></template>
          </el-table-column>
        </el-table>
        <p class="scope-legend">SELF 本人；SELF_CREATED 我创建；ORG 本机构；ORG_SUBTREE 本机构及下属；ALL 全行；NONE 无权限。变更 ALL 或 ORG_SUBTREE 属高风险操作。</p>
      </section>
    </section>

    <el-dialog v-model="scopeDlg.show" class="bp-crud-dialog" title="编辑数据范围" width="440px" :close-on-click-modal="false">
      <el-form label-width="84px">
        <el-form-item label="角色"><el-input :model-value="pickedRole?.roleChName" disabled /></el-form-item>
        <el-form-item label="BizType"><el-input :model-value="scopeDlg.bizType" disabled /></el-form-item>
        <el-form-item label="数据范围">
          <el-select v-model="scopeDlg.dataScope" style="width:100%" aria-label="选择数据范围">
            <el-option v-for="scope in SCOPE_OPTIONS" :key="scope.value" :value="scope.value" :label="`${scope.value} · ${scope.label}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="变更原因" required>
          <el-input v-model="scopeDlg.reason" type="textarea" :rows="2" placeholder="必填，写入权限变更审计日志" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="scopeDlg.saving" @click="scopeDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="scopeDlg.saving" :disabled="scopeDlg.saving" @click="onSaveScope">保存</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  listAllRoles, listResources, getScopeMatrix,
  getRoleResourceIds, replaceRoleResources, saveBizScope
} from '@/api/system';

const SCOPE_OPTIONS = [
  { value: 'SELF', label: '本人' },
  { value: 'SELF_CREATED', label: '我创建的' },
  { value: 'SELF_ASSIGNED', label: '分给我的' },
  { value: 'ORG', label: '本机构' },
  { value: 'ORG_SUBTREE', label: '本机构及下属' },
  { value: 'ALL', label: '全行' },
  { value: 'NONE', label: '无权限' },
  { value: 'WORKFLOW_PARTICIPANT', label: '流程参与者' }
];

const MODULE_RULES = [
  { prefix: '/api/portal', name: '工作台' },
  { prefix: '/api/admin/nav', name: '工作台' },
  { prefix: '/api/nav', name: '工作台' },
  { prefix: '/api/notifications', name: '工作台' },
  { prefix: '/api/employees', name: '组织架构' },
  { prefix: '/api/orgs', name: '组织架构' },
  { prefix: '/api/products', name: '产品资料库' },
  { prefix: '/api/admin/products', name: '产品资料库' },
  { prefix: '/api/documents', name: '文档下载' },
  { prefix: '/api/admin/documents', name: '文档下载' },
  { prefix: '/api/tags', name: '客户营销' },
  { prefix: '/api/cust-leads', name: '客户营销' },
  { prefix: '/api/leads', name: '客户营销' },
  { prefix: '/api/customers', name: '客户营销' },
  { prefix: '/api/cust-pool', name: '客户营销' },
  { prefix: '/api/customer-pool', name: '客户营销' },
  { prefix: '/api/cust-tags', name: '客户营销' },
  { prefix: '/api/cust-claims', name: '客户营销' },
  { prefix: '/api/claims', name: '客户营销' },
  { prefix: '/api/my-claims', name: '客户营销' },
  { prefix: '/api/touch-tasks', name: '触达任务' },
  { prefix: '/api/touch-reports', name: '触达任务' },
  { prefix: '/api/loan', name: '业务执行' },
  { prefix: '/api/business-application', name: '业务执行' },
  { prefix: '/api/support', name: '中场支持' },
  { prefix: '/api/workflow', name: '工作流' },
  { prefix: '/api/perf', name: '绩效与考核' },
  { prefix: '/api/admin/perf', name: '绩效与考核' },
  { prefix: '/api/reports', name: '报表分析' },
  { prefix: '/api/rpt', name: '报表分析' },
  { prefix: '/api/dynamic-query', name: '报表分析' },
  { prefix: '/api/data-task', name: '报表分析' },
  { prefix: '/api/admin/sql-probe', name: '数据探查' },
  { prefix: '/api/admin/sys/files', name: '文件管理' },
  { prefix: '/api/files', name: '文件管理' },
  { prefix: '/api/admin/biz-scopes', name: '权限管理' },
  { prefix: '/api/admin/roles', name: '权限管理' },
  { prefix: '/api/admin/resources', name: '权限管理' },
  { prefix: '/api/admin/users', name: '权限管理' },
  { prefix: '/api/auth', name: '认证' },
  { prefix: '/api/admin/sys', name: '系统治理' },
  { prefix: '/api/sys', name: '系统治理' },
  { prefix: '/api/admin', name: '管理后台' }
];

function moduleOf(url) {
  const normalized = (url || '').toLowerCase();
  return MODULE_RULES.find(rule => normalized.startsWith(rule.prefix)) || { name: '其它' };
}
function rwSide(resource) {
  return (resource.resourceMethod || '').toUpperCase() === 'GET' ? 'r' : 'w';
}
function methodCls(method) {
  return ({ GET: 'tag-success', POST: 'tag-info', PUT: 'tag-warning', DELETE: 'tag-danger' }[(method || '').toUpperCase()] || 'tag-info');
}

const roles = ref([]);
const pickedRoleId = ref(null);
const kw = ref('');
const rolesLoading = ref(false);
const switchingRole = ref(false);
const filteredRoles = computed(() => {
  const query = kw.value.trim().toLowerCase();
  if (!query) return roles.value;
  return roles.value.filter(role => [role.roleChName, role.roleCode, role.roleId]
    .some(value => String(value || '').toLowerCase().includes(query)));
});
const pickedRole = computed(() => roles.value.find(role => role.roleId === pickedRoleId.value));

const resources = ref([]);
const resourcesLoading = ref(false);
const resKw = ref('');
const checkedIds = ref(new Set());
const initialChecked = ref(new Set());
const dirty = computed(() => checkedIds.value.size !== initialChecked.value.size
  || [...checkedIds.value].some(id => !initialChecked.value.has(id)));
const pendingDelta = computed(() => {
  let count = 0;
  for (const id of checkedIds.value) if (!initialChecked.value.has(id)) count += 1;
  for (const id of initialChecked.value) if (!checkedIds.value.has(id)) count += 1;
  return count;
});

const groupedRes = computed(() => {
  const query = resKw.value.trim().toLowerCase();
  const matched = query
    ? resources.value.filter(resource => [resource.resourceUrl, resource.menuName, resource.resourceId]
      .some(value => String(value || '').toLowerCase().includes(query)))
    : resources.value;
  const groups = new Map();
  for (const resource of matched) {
    const { name } = moduleOf(resource.resourceUrl);
    if (!groups.has(name)) groups.set(name, { key: name, name, allItems: [], r: { items: [], checked: 0 }, w: { items: [], checked: 0 } });
    const group = groups.get(name);
    group.allItems.push(resource);
    group[rwSide(resource)].items.push(resource);
  }
  const order = ['工作台', '客户营销', '触达任务', '业务执行', '中场支持', '工作流', '绩效与考核', '报表分析', '组织架构', '产品资料库', '文档下载', '文件管理', '数据探查', '权限管理', '认证', '系统治理', '管理后台', '其它'];
  return [...groups.values()].map(group => {
    group.allItems.sort((a, b) => String(a.resourceUrl || '').localeCompare(String(b.resourceUrl || '')));
    for (const side of ['r', 'w']) {
      group[side].checked = group[side].items.filter(resource => checkedIds.value.has(resource.resourceId)).length;
      group[side].allChecked = group[side].items.length > 0 && group[side].checked === group[side].items.length;
      group[side].indeterminate = group[side].checked > 0 && group[side].checked < group[side].items.length;
    }
    const total = group.allItems.length;
    const checked = group.r.checked + group.w.checked;
    group.all = { total, checked, allChecked: total > 0 && checked === total, indeterminate: checked > 0 && checked < total };
    return group;
  }).sort((a, b) => order.indexOf(a.name) - order.indexOf(b.name));
});

function toggleOne(id, value) {
  if (resourceEditBlocked.value) return;
  const next = new Set(checkedIds.value);
  if (value) next.add(id);
  else next.delete(id);
  checkedIds.value = next;
}
function toggleGroupSide(group, side, value) {
  if (resourceEditBlocked.value) return;
  const next = new Set(checkedIds.value);
  const items = side === 'all' ? group.allItems : group[side].items;
  for (const resource of items) {
    if (value) next.add(resource.resourceId);
    else next.delete(resource.resourceId);
  }
  checkedIds.value = next;
}
const collapsedKeys = ref(new Set());
function toggleCollapse(key) {
  const next = new Set(collapsedKeys.value);
  if (next.has(key)) next.delete(key);
  else next.add(key);
  collapsedKeys.value = next;
}
function resetChecked() {
  if (resourceEditBlocked.value) return;
  checkedIds.value = new Set(initialChecked.value);
}

const scopeMatrix = ref(null);
const scopeLoading = ref(false);
const scopeRows = computed(() => {
  if (!scopeMatrix.value || !pickedRoleId.value) return [];
  const { bizTypes = [], matrix = {}, bizTypeLabels = {} } = scopeMatrix.value;
  const cells = matrix[pickedRoleId.value] || {};
  return bizTypes.map(bizType => ({
    bizType,
    bizTypeLabel: bizTypeLabels[bizType] || bizType,
    dataScope: cells[bizType] || 'NONE'
  }));
});
const scopeCls = (scope) => (scope === 'ALL' || scope === 'ORG_SUBTREE' ? 'tag-warning' : scope === 'NONE' ? 'tag-danger' : 'tag-info');
const scopeLabel = (scope) => SCOPE_OPTIONS.find(option => option.value === scope)?.label || scope || '-';
const scopeDlg = reactive({ show: false, bizType: '', dataScope: 'SELF', reason: '', saving: false });

function openScopeEditor(row) {
  scopeDlg.bizType = row.bizType;
  scopeDlg.dataScope = row.dataScope || 'SELF';
  scopeDlg.reason = '';
  scopeDlg.show = true;
}
async function onSaveScope() {
  if (scopeDlg.saving || !pickedRoleId.value) return;
  if (!scopeDlg.reason.trim()) {
    ElMessage.warning('请填写变更原因（PERMISSION_CHANGE 审计要求）');
    return;
  }
  scopeDlg.saving = true;
  try {
    await saveBizScope(pickedRoleId.value, scopeDlg.bizType, scopeDlg.dataScope, scopeDlg.reason.trim());
    ElMessage.success('数据范围已更新');
    scopeDlg.show = false;
    await loadScope(pickedRoleId.value);
  } catch (error) {
    ElMessage.error(`数据范围保存失败：${error?.message || error}`);
  } finally {
    scopeDlg.saving = false;
  }
}

const saving = ref(false);
async function onSaveResources() {
  if (resourceEditBlocked.value || saving.value || !dirty.value || !pickedRoleId.value) return;
  saving.value = true;
  const ids = Array.from(checkedIds.value);
  try {
    await ElMessageBox.confirm(
      `将把 ${ids.length} 个资源全量授权给【${pickedRole.value?.roleChName || pickedRoleId.value}】，覆盖原有 ${initialChecked.value.size} 个绑定。`,
      '确认保存（PERMISSION_CHANGE）',
      { type: 'warning', confirmButtonText: '确认保存', cancelButtonText: '取消' }
    );
  } catch {
    saving.value = false;
    return;
  }
  let reason;
  try {
    const result = await ElMessageBox.prompt('变更原因（写入权限变更审计日志）', '填写变更原因', {
      inputPattern: /\S+/,
      inputErrorMessage: '原因不能为空',
      confirmButtonText: '继续保存',
      cancelButtonText: '取消'
    });
    reason = result.value.trim();
  } catch {
    saving.value = false;
    return;
  }
  try {
    await replaceRoleResources(pickedRoleId.value, ids, reason);
    initialChecked.value = new Set(ids);
    ElMessage.success(`资源绑定已保存（${ids.length} 项）`);
  } catch (error) {
    ElMessage.error(`资源绑定保存失败：${error?.message || error}`);
  } finally {
    saving.value = false;
  }
}

const loadError = ref('');
const resourceCatalogError = ref(false);
const roleResourceError = ref(false);
const resourceEditBlocked = computed(() => resourcesLoading.value
  || resourceCatalogError.value
  || roleResourceError.value
  || !pickedRoleId.value);
const pageLoading = computed(() => rolesLoading.value || resourcesLoading.value || scopeLoading.value);
function recordLoadError(scope, error) {
  loadError.value = `${scope}加载失败：${error?.message || '请检查权限或稍后重试'}`;
}
async function loadRoles() {
  rolesLoading.value = true;
  try {
    const result = await listAllRoles();
    const rows = result?.records || (Array.isArray(result) ? result : []);
    roles.value = rows;
    if (!rows.some(role => role.roleId === pickedRoleId.value)) pickedRoleId.value = rows[0]?.roleId || null;
  } catch (error) {
    roles.value = [];
    pickedRoleId.value = null;
    recordLoadError('角色', error);
  } finally {
    rolesLoading.value = false;
  }
}
function flattenTree(nodes, out = []) {
  for (const node of nodes || []) {
    out.push(node);
    if (Array.isArray(node.children) && node.children.length) flattenTree(node.children, out);
  }
  return out;
}
async function loadResources() {
  resourcesLoading.value = true;
  resourceCatalogError.value = false;
  try {
    const result = await listResources();
    resources.value = Array.isArray(result) ? flattenTree(result) : [];
  } catch (error) {
    resources.value = [];
    resourceCatalogError.value = true;
    recordLoadError('资源', error);
  } finally {
    resourcesLoading.value = false;
  }
}
async function loadRoleChecked(roleId) {
  if (!roleId) {
    roleResourceError.value = false;
    initialChecked.value = new Set();
    checkedIds.value = new Set();
    return;
  }
  resourcesLoading.value = true;
  roleResourceError.value = false;
  try {
    const ids = await getRoleResourceIds(roleId);
    const selected = new Set(Array.isArray(ids) ? ids : []);
    initialChecked.value = selected;
    checkedIds.value = new Set(selected);
  } catch (error) {
    roleResourceError.value = true;
    recordLoadError('角色资源', error);
  } finally {
    resourcesLoading.value = false;
  }
}
async function loadScope(roleId) {
  if (!roleId) {
    scopeMatrix.value = null;
    return;
  }
  scopeLoading.value = true;
  try {
    const result = await getScopeMatrix(roleId);
    scopeMatrix.value = result?.bizTypes ? result : { bizTypes: [], matrix: {} };
  } catch (error) {
    scopeMatrix.value = { bizTypes: [], matrix: {} };
    recordLoadError('数据范围', error);
  } finally {
    scopeLoading.value = false;
  }
}
async function onPickRole(id) {
  if (switchingRole.value || id === pickedRoleId.value) return;
  switchingRole.value = true;
  try {
    if (dirty.value) {
      await ElMessageBox.confirm('当前角色有未保存改动，切换后会丢失。是否继续？', '确认切换角色', {
        type: 'warning', confirmButtonText: '继续切换', cancelButtonText: '取消'
      });
    }
    pickedRoleId.value = id;
  } catch {
    // 用户取消切换时保留当前角色与未保存的资源勾选。
  } finally {
    switchingRole.value = false;
  }
}
async function reload() {
  loadError.value = '';
  await Promise.all([loadRoles(), loadResources()]);
  if (pickedRoleId.value) await Promise.all([loadRoleChecked(pickedRoleId.value), loadScope(pickedRoleId.value)]);
}
function resetFilters() {
  kw.value = '';
  resKw.value = '';
}

watch(pickedRoleId, async (id) => {
  if (id) await Promise.all([loadRoleChecked(id), loadScope(id)]);
});
onMounted(reload);
</script>

<style lang="scss" scoped>
.permission-page { min-width: 0; }
.permission-grid {
  display: grid;
  gap: var(--space-4);
  grid-template-columns: minmax(250px, .78fr) minmax(520px, 1.55fr) minmax(330px, 1fr);
  min-height: min(680px, calc(100vh - 274px));
}
.permission-col {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  padding: var(--space-4);
}
.compact-toolbar { align-items: flex-start; flex: 0 0 auto; }
.role-list,
.resource-list { flex: 1; min-height: 0; overflow: auto; }
.role-list { margin-inline: calc(var(--space-4) * -1); }
.role-option {
  align-items: center;
  background: transparent;
  border: 0;
  border-left: 3px solid transparent;
  color: var(--color-text-strong);
  cursor: pointer;
  display: flex;
  font: inherit;
  gap: var(--space-2);
  justify-content: space-between;
  min-height: 60px;
  padding: var(--space-2) var(--space-4);
  text-align: left;
  width: 100%;
}
.role-option:hover { background: var(--color-surface-soft); }
.role-option.active { background: var(--color-brand-100); border-left-color: var(--color-brand-700); }
.role-option.disabled { color: var(--color-text-muted); }
.role-info { display: grid; gap: var(--space-1); min-width: 0; }
.role-name { font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.role-code,
.biz-type-code,
.resource-url {
  color: var(--color-text-muted);
  font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace;
  font-size: 12px;
}
.role-meta { align-items: flex-end; display: grid; font-size: 12px; gap: var(--space-1); white-space: nowrap; }
.resource-list { border-top: 1px solid var(--color-border); }
.resource-group { border-bottom: 1px solid var(--color-border); }
.resource-group-head {
  align-items: center;
  background: var(--color-surface-soft);
  display: grid;
  gap: var(--space-2);
  grid-template-columns: auto minmax(92px, 1fr) auto auto;
  min-height: 44px;
  padding: var(--space-2) var(--space-3);
}
.collapse-toggle {
  background: transparent;
  border: 0;
  color: var(--color-brand-700);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  padding: var(--space-1);
}
.group-name { color: var(--color-text-strong); font-size: 13px; font-weight: 600; }
.group-count { color: var(--color-text-muted); font-size: 12px; white-space: nowrap; }
.group-checks { align-items: center; display: flex; gap: var(--space-2); white-space: nowrap; }
.group-checks :deep(.el-checkbox) { margin-right: 0; }
.resource-item {
  align-items: center;
  display: grid;
  gap: var(--space-3);
  grid-template-columns: minmax(0, 1fr) auto;
  min-height: 54px;
  padding: var(--space-2) var(--space-3);
}
.resource-item:hover { background: var(--color-surface-soft); }
.resource-meta { display: grid; gap: var(--space-1); min-width: 0; }
.resource-name { color: var(--color-text-strong); font-size: 13px; }
.resource-url { align-items: center; display: flex; gap: var(--space-2); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.resource-url :deep(.el-tag) { font-family: inherit; flex: 0 0 auto; }
.resource-foot {
  align-items: center;
  border-top: 1px solid var(--color-border);
  display: flex;
  flex: 0 0 auto;
  gap: var(--space-3);
  justify-content: space-between;
  margin-top: var(--space-3);
  padding-top: var(--space-3);
}
.resource-foot .hint { margin: 0; }
.scope-legend {
  border-top: 1px solid var(--color-border);
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 18px;
  margin-top: var(--space-3);
  padding-top: var(--space-3);
}
.biz-type-name { color: var(--color-text-strong); font-size: 13px; }
.empty-state { color: var(--color-text-muted); font-size: 12px; padding: var(--space-6) var(--space-4); text-align: center; }
.error-state {
  background: var(--color-danger-bg);
  border-left: 3px solid var(--color-danger-fg);
  color: var(--color-danger-fg);
  font-size: 12px;
  line-height: 18px;
  margin: 0;
  padding: var(--space-2) var(--space-3);
}
</style>
