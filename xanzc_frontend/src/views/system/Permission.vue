<template>
  <div>
    <div class="page-h">
      <h1>权限配置</h1>
      <span class="desc">RBAC 角色 × 资源 × 数据范围 — 与 project_ana §4.6.1 对齐</span>
      <div class="actions">
        <el-button @click="reload">刷新</el-button>
      </div>
    </div>

    <div class="three-cols">
      <!-- ============= 左：角色列表 ============= -->
      <div class="card-section col">
        <div class="col-h">
          <div>角色 ({{ roles.length }})</div>
          <el-input v-model="kw" size="small" placeholder="搜索" style="width:120px" clearable />
        </div>
        <div class="role-list">
          <div
            v-for="r in filteredRoles" :key="r.roleId"
            :class="['role', { active: r.roleId === pickedRoleId }]"
            @click="onPickRole(r.roleId)"
          >
            <div class="r-info">
              <div class="r-name">👤 {{ r.roleChName || r.roleId }}</div>
              <div class="r-code"><code>{{ r.roleCode }}</code></div>
            </div>
            <span class="cnt">{{ r.userCount != null ? r.userCount + ' 人' : '-' }}</span>
          </div>
        </div>
      </div>

      <!-- ============= 中：资源（按业务模块 + R/W 双勾选） ============= -->
      <div class="card-section col">
        <div class="col-h">
          <div>资源 ({{ pickedRole?.roleChName || '-' }} · 已勾 {{ checkedIds.size }} / {{ resources.length }})</div>
          <el-input v-model="resKw" size="small" placeholder="搜索" style="width:150px" clearable />
        </div>
        <div class="res-list">
          <div v-for="g in groupedRes" :key="g.key" class="res-group-block">
            <div class="res-group">
              <span class="caret" @click="toggleCollapse(g.key)" :title="collapsedKeys.has(g.key) ? '展开' : '折叠'">{{ collapsedKeys.has(g.key) ? '▶' : '▼' }}</span>
              <span class="ico">{{ g.icon }}</span>
              <span class="g-name" @click="toggleCollapse(g.key)" style="cursor:pointer">{{ g.name }}</span>
              <span class="cnt">R {{ g.r.checked }}/{{ g.r.items.length }} · W {{ g.w.checked }}/{{ g.w.items.length }}</span>
              <span class="rw-all">
                <el-checkbox
                  :model-value="g.all.allChecked"
                  :indeterminate="g.all.indeterminate"
                  :disabled="g.all.total === 0"
                  @change="(v) => toggleGroupSide(g, 'all', v)"
                  size="small"
                  title="整组 R+W 一键勾选 / 取消"
                />
                <span class="g-all-label">整组</span>
              </span>
              <span class="rw-h">
                <el-checkbox
                  :model-value="g.r.allChecked"
                  :indeterminate="g.r.indeterminate"
                  :disabled="g.r.items.length === 0"
                  @change="(v) => toggleGroupSide(g, 'r', v)"
                  size="small"
                  title="全选 R（GET 接口）"
                />
                <span style="color:#16A34A">R</span>
                <el-checkbox
                  :model-value="g.w.allChecked"
                  :indeterminate="g.w.indeterminate"
                  :disabled="g.w.items.length === 0"
                  @change="(v) => toggleGroupSide(g, 'w', v)"
                  size="small"
                  title="全选 W（POST/PUT/DELETE 接口）"
                />
                <span style="color:#D97706">W</span>
              </span>
            </div>
            <div v-for="r in g.allItems" :key="r.resourceId" v-show="!collapsedKeys.has(g.key)" class="res-item">
              <div class="res-meta">
                <span class="res-name">{{ r.menuName || r.resourceId }}</span>
                <code class="res-url"><el-tag size="small" :class="methodCls(r.resourceMethod)" effect="plain" disable-transitions>{{ r.resourceMethod }}</el-tag> {{ r.resourceUrl }}</code>
              </div>
              <span class="rw-cell">
                <el-checkbox
                  v-if="rwSide(r) === 'r'"
                  :model-value="checkedIds.has(r.resourceId)"
                  @change="(v) => toggleOne(r.resourceId, v)"
                  size="small"
                />
                <span v-else class="dash">—</span>
                <el-checkbox
                  v-if="rwSide(r) === 'w'"
                  :model-value="checkedIds.has(r.resourceId)"
                  @change="(v) => toggleOne(r.resourceId, v)"
                  size="small"
                />
                <span v-if="rwSide(r) !== 'w'" class="dash">—</span>
              </span>
            </div>
          </div>
          <div v-if="groupedRes.length === 0" class="empty">暂无资源</div>
        </div>
        <div class="col-foot">
          <span class="dirty" v-if="dirty">⚠ {{ pendingDelta }} 条改动未保存</span>
          <span class="dirty ok" v-else>✓ 已同步</span>
          <el-button size="small" @click="resetChecked" :disabled="!dirty">还原</el-button>
          <el-button size="small" type="primary" @click="onSaveResources" :loading="saving" :disabled="!dirty">保存绑定</el-button>
        </div>
      </div>

      <!-- ============= 右：数据范围矩阵（BizType × DataScope） ============= -->
      <div class="card-section col">
        <div class="col-h">
          <div>数据范围（{{ pickedRole?.roleChName || '-' }}）</div>
          <el-tag v-if="scopeLoading" effect="plain" class="tag-info" size="small">加载中</el-tag>
        </div>
        <el-table :data="scopeRows" size="small" empty-text="无 BizType 配置" :max-height="999">
          <el-table-column prop="bizType" label="BizType" width="130" />
          <el-table-column label="DataScope" width="160">
            <template #default="{row}">
              <el-tag :class="scopeCls(row.dataScope)" effect="plain">{{ scopeLabel(row.dataScope) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="原因" min-width="120">
            <template #default="{row}"><span style="color:#6B7280;font-size:11px">{{ row.reason || '默认' }}</span></template>
          </el-table-column>
          <el-table-column label="操作" width="70">
            <template #default="{row}"><el-button link type="primary" size="small" @click="openScopeEditor(row)">修改</el-button></template>
          </el-table-column>
        </el-table>
        <div class="legend">
          <code>SELF</code> 本人 · <code>SELF_CREATED</code> 我创建 · <code>ORG</code> 本机构 · <code>ORG_SUBTREE</code> 本机构及下属 · <code>ALL</code> 全行 · <code>NONE</code> 无权限<br>
          <span style="color:#D97706">⚠ 修改 ALL/ORG_SUBTREE 为高危（PERMISSION_CHANGE 审计）</span>
        </div>
      </div>
    </div>

    <!-- 数据范围编辑 dialog -->
    <el-dialog v-model="scopeDlg.show" title="编辑数据范围" width="440px">
      <el-form label-width="84px">
        <el-form-item label="角色"><el-input :value="pickedRole?.roleChName" disabled /></el-form-item>
        <el-form-item label="BizType"><el-input :value="scopeDlg.bizType" disabled /></el-form-item>
        <el-form-item label="DataScope">
          <el-select v-model="scopeDlg.dataScope" style="width:100%">
            <el-option v-for="s in SCOPE_OPTIONS" :key="s.value" :value="s.value">
              <span>{{ s.value }}</span>
              <span style="color:#9CA3AF;font-size:11px;margin-left:8px">{{ s.label }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="scopeDlg.reason" type="textarea" :rows="2" placeholder="变更原因（写入审计日志）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="scopeDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="scopeDlg.saving" @click="onSaveScope">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { sysRoles, sysResources, sysScopeMatrix } from '@/mock';
import {
  listRoles, listResources, getScopeMatrix,
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

// ============= URL 前缀 → 业务模块映射（与原型菜单分类对齐） =============
// 顺序敏感：从最具体的前缀往最宽匹配
const MODULE_RULES = [
  { prefix: '/api/portal',          name: '工作台',         icon: '🏠' },
  { prefix: '/api/admin/nav',       name: '工作台',         icon: '🏠' },
  { prefix: '/api/nav',             name: '工作台',         icon: '🏠' },
  { prefix: '/api/notifications',   name: '工作台',         icon: '🏠' },
  { prefix: '/api/employees',       name: '组织架构',       icon: '👥' },
  { prefix: '/api/orgs',            name: '组织架构',       icon: '👥' },
  { prefix: '/api/products',        name: '产品资料库',     icon: '📦' },
  { prefix: '/api/admin/products',  name: '产品资料库',     icon: '📦' },
  { prefix: '/api/documents',       name: '文档下载',       icon: '📄' },
  { prefix: '/api/admin/documents', name: '文档下载',       icon: '📄' },
  { prefix: '/api/tags',            name: '客户营销',       icon: '🎯' },
  { prefix: '/api/cust-leads',      name: '客户营销',       icon: '🎯' },
  { prefix: '/api/leads',           name: '客户营销',       icon: '🎯' },
  { prefix: '/api/customers',       name: '客户营销',       icon: '🎯' },
  { prefix: '/api/cust-pool',       name: '客户营销',       icon: '🎯' },
  { prefix: '/api/customer-pool',   name: '客户营销',       icon: '🎯' },
  { prefix: '/api/cust-tags',       name: '客户营销',       icon: '🎯' },
  { prefix: '/api/cust-claims',     name: '客户营销',       icon: '🎯' },
  { prefix: '/api/claims',          name: '客户营销',       icon: '🎯' },
  { prefix: '/api/my-claims',       name: '客户营销',       icon: '🎯' },
  { prefix: '/api/touch-tasks',     name: '触达任务',       icon: '📞' },
  { prefix: '/api/touch-reports',   name: '触达任务',       icon: '📞' },
  { prefix: '/api/loan',            name: '业务执行',       icon: '🏦' },
  { prefix: '/api/business-application', name: '业务执行',  icon: '🏦' },
  { prefix: '/api/support',         name: '中场支持',       icon: '🤝' },
  { prefix: '/api/workflow',        name: '工作流',         icon: '🔄' },
  { prefix: '/api/perf',            name: '绩效与考核',     icon: '📈' },
  { prefix: '/api/admin/perf',      name: '绩效与考核',     icon: '📈' },
  { prefix: '/api/reports',         name: '报表分析',       icon: '📊' },
  { prefix: '/api/rpt',             name: '报表分析',       icon: '📊' },
  { prefix: '/api/dynamic-query',   name: '报表分析',       icon: '📊' },
  { prefix: '/api/data-task',       name: '报表分析',       icon: '📊' },
  { prefix: '/api/admin/sql-probe', name: '数据探查',       icon: '🔍' },
  { prefix: '/api/admin/sys/files', name: '文件管理',       icon: '📎' },
  { prefix: '/api/files',           name: '文件管理',       icon: '📎' },
  { prefix: '/api/admin/biz-scopes',name: '权限管理',       icon: '🔐' },
  { prefix: '/api/admin/roles',     name: '权限管理',       icon: '🔐' },
  { prefix: '/api/admin/resources', name: '权限管理',       icon: '🔐' },
  { prefix: '/api/admin/users',     name: '权限管理',       icon: '🔐' },
  { prefix: '/api/auth',            name: '认证',           icon: '🔑' },
  { prefix: '/api/admin/sys',       name: '系统治理',       icon: '⚙️' },
  { prefix: '/api/sys',             name: '系统治理',       icon: '⚙️' },
  { prefix: '/api/admin',           name: '管理后台',       icon: '🛠' }
];
function moduleOf(url) {
  const u = (url || '').toLowerCase();
  for (const r of MODULE_RULES) {
    if (u.startsWith(r.prefix)) return r;
  }
  return { name: '其它', icon: '📌' };
}
function rwSide(r) {
  return (r.resourceMethod || '').toUpperCase() === 'GET' ? 'r' : 'w';
}
function methodCls(m) {
  const k = (m || '').toUpperCase();
  if (k === 'GET') return 'tag-success';
  if (k === 'POST') return 'tag-info';
  if (k === 'PUT') return 'tag-warning';
  if (k === 'DELETE') return 'tag-danger';
  return 'tag-info';
}

// ============= 角色列 =============
const roles = ref([]);
const pickedRoleId = ref(null);
const kw = ref('');
const filteredRoles = computed(() => {
  const k = kw.value.trim().toLowerCase();
  if (!k) return roles.value;
  return roles.value.filter(r =>
    (r.roleChName || '').toLowerCase().includes(k) ||
    (r.roleCode || '').toLowerCase().includes(k) ||
    (r.roleId || '').toLowerCase().includes(k));
});
const pickedRole = computed(() => roles.value.find(r => r.roleId === pickedRoleId.value));

// ============= 资源列 =============
const resources = ref([]);
const resKw = ref('');
const checkedIds = ref(new Set());
const initialChecked = ref(new Set());
const dirty = computed(() => {
  if (checkedIds.value.size !== initialChecked.value.size) return true;
  for (const id of checkedIds.value) if (!initialChecked.value.has(id)) return true;
  return false;
});
const pendingDelta = computed(() => {
  let d = 0;
  for (const id of checkedIds.value) if (!initialChecked.value.has(id)) d++;
  for (const id of initialChecked.value) if (!checkedIds.value.has(id)) d++;
  return d;
});

const groupedRes = computed(() => {
  const k = resKw.value.trim().toLowerCase();
  const filt = k
    ? resources.value.filter(r =>
        (r.resourceUrl || '').toLowerCase().includes(k) ||
        (r.menuName || '').toLowerCase().includes(k) ||
        (r.resourceId || '').toLowerCase().includes(k))
    : resources.value;
  const map = new Map();
  for (const r of filt) {
    const m = moduleOf(r.resourceUrl);
    const key = m.name;
    if (!map.has(key)) map.set(key, { key, name: m.name, icon: m.icon, allItems: [], r: { items: [], checked: 0 }, w: { items: [], checked: 0 } });
    const g = map.get(key);
    g.allItems.push(r);
    const side = rwSide(r);
    g[side].items.push(r);
  }
  // 排序内部 + 计算勾选
  const groups = [];
  for (const g of map.values()) {
    g.allItems.sort((a, b) => (a.resourceUrl || '').localeCompare(b.resourceUrl || ''));
    g.r.checked  = g.r.items.filter(r => checkedIds.value.has(r.resourceId)).length;
    g.w.checked  = g.w.items.filter(r => checkedIds.value.has(r.resourceId)).length;
    g.r.allChecked = g.r.items.length > 0 && g.r.checked === g.r.items.length;
    g.r.indeterminate = g.r.checked > 0 && g.r.checked < g.r.items.length;
    g.w.allChecked = g.w.items.length > 0 && g.w.checked === g.w.items.length;
    g.w.indeterminate = g.w.checked > 0 && g.w.checked < g.w.items.length;
    // 整组 R+W 合计，给「一键勾选整组菜单」主复选框用
    const allTotal = g.r.items.length + g.w.items.length;
    const allChecked = g.r.checked + g.w.checked;
    g.all = {
      total: allTotal,
      checked: allChecked,
      allChecked: allTotal > 0 && allChecked === allTotal,
      indeterminate: allChecked > 0 && allChecked < allTotal,
    };
    groups.push(g);
  }
  // 按 MODULE_RULES 顺序排
  const order = ['工作台','客户营销','触达任务','业务执行','中场支持','工作流','绩效与考核','报表分析','组织架构','产品资料库','文档下载','文件管理','数据探查','权限管理','认证','系统治理','管理后台','其它'];
  groups.sort((a, b) => order.indexOf(a.name) - order.indexOf(b.name));
  return groups;
});

function toggleOne(id, v) {
  const next = new Set(checkedIds.value);
  if (v) next.add(id); else next.delete(id);
  checkedIds.value = next;
}
function toggleGroupSide(g, side, v) {
  const next = new Set(checkedIds.value);
  // side='all' 时整组 R+W 一起切；保留 'r'/'w' 单侧切作为细粒度
  const items = side === 'all' ? g.allItems : g[side].items;
  for (const r of items) {
    if (v) next.add(r.resourceId); else next.delete(r.resourceId);
  }
  checkedIds.value = next;
}
// 折叠/展开整个业务模块分组，让长资源列表的浏览体验更接近"按菜单整体浏览"
const collapsedKeys = ref(new Set());
function toggleCollapse(key) {
  const next = new Set(collapsedKeys.value);
  if (next.has(key)) next.delete(key); else next.add(key);
  collapsedKeys.value = next;
}
function resetChecked() {
  checkedIds.value = new Set(initialChecked.value);
}

// ============= 数据范围列 =============
const scopeMatrix = ref(null);
const scopeLoading = ref(false);
const scopeRows = computed(() => {
  if (!scopeMatrix.value) return [];
  const { bizTypes, matrix } = scopeMatrix.value;
  const rid = pickedRoleId.value;
  const cell = matrix?.[rid] || {};
  return (bizTypes || []).map(bt => ({
    bizType: bt,
    dataScope: cell[bt] || 'NONE',
    reason: '' // 后端 matrix 接口未返回 reason，编辑时再带
  }));
});
const scopeCls = (s) => {
  if (s === 'ALL' || s === 'ORG_SUBTREE') return 'tag-warning';
  if (s === 'NONE') return 'tag-danger';
  return 'tag-info';
};
const scopeLabel = (s) => SCOPE_OPTIONS.find(o => o.value === s)?.label || s || '-';

const scopeDlg = reactive({ show: false, bizType: '', dataScope: 'SELF', reason: '', saving: false });
function openScopeEditor(row) {
  scopeDlg.bizType = row.bizType;
  scopeDlg.dataScope = row.dataScope || 'SELF';
  scopeDlg.reason = '';
  scopeDlg.show = true;
}
async function onSaveScope() {
  if (!scopeDlg.reason.trim()) { ElMessage.warning('请填写变更原因（PERMISSION_CHANGE 审计要求）'); return; }
  scopeDlg.saving = true;
  try {
    await saveBizScope(pickedRoleId.value, scopeDlg.bizType, scopeDlg.dataScope, scopeDlg.reason.trim());
    ElMessage.success('数据范围已更新');
    scopeDlg.show = false;
    await loadScope(pickedRoleId.value);
  } catch (e) { /* http.js 已弹错 */ } finally { scopeDlg.saving = false; }
}

// ============= 保存资源绑定 =============
const saving = ref(false);
async function onSaveResources() {
  const ids = Array.from(checkedIds.value);
  try {
    await ElMessageBox.confirm(
      `将把 ${ids.length} 个资源全量授权给【${pickedRole.value?.roleChName}】，覆盖原有 ${initialChecked.value.size} 个绑定。`,
      '确认保存（PERMISSION_CHANGE）', { type: 'warning' }
    );
  } catch { return; }
  let reason;
  try {
    const r = await ElMessageBox.prompt('变更原因（审计）', '提示', {
      inputPattern: /\S+/, inputErrorMessage: '原因不能为空'
    });
    reason = r.value;
  } catch { return; }

  saving.value = true;
  try {
    await replaceRoleResources(pickedRoleId.value, ids, reason);
    initialChecked.value = new Set(ids);
    ElMessage.success(`已保存（${ids.length} 个资源）`);
  } catch (e) { /* http.js 已弹错 */ } finally { saving.value = false; }
}

// ============= 数据加载 =============
async function loadRoles() {
  try {
    const r = await listRoles({ pageNo: 1, pageSize: 999 });
    const arr = r?.records || (Array.isArray(r) ? r : []);
    if (arr.length) {
      roles.value = arr;
      if (!pickedRoleId.value) pickedRoleId.value = arr[0].roleId;
    } else {
      roles.value = sysRoles.map(x => ({
        roleId: x.id || x.roleId, roleChName: x.name || x.roleChName,
        roleCode: x.code || x.roleCode, userCount: x.count
      }));
      pickedRoleId.value = roles.value[0]?.roleId;
    }
  } catch {}
}
async function loadResources() {
  try {
    const r = await listResources();
    if (Array.isArray(r) && r.length) {
      resources.value = r;
    } else {
      const flat = [];
      for (const g of sysResources) {
        flat.push({ resourceId: g.id, menuName: g.label, resourceUrl: '/' + g.id, resourceMethod: 'GET' });
        for (const c of (g.children || [])) {
          flat.push({ resourceId: c.id, menuName: c.label, resourceUrl: '/' + c.id, resourceMethod: 'GET' });
        }
      }
      resources.value = flat;
    }
  } catch {}
}
async function loadRoleChecked(roleId) {
  if (!roleId) return;
  try {
    const ids = await getRoleResourceIds(roleId);
    const set = new Set(Array.isArray(ids) ? ids : []);
    initialChecked.value = set;
    checkedIds.value = new Set(set);
  } catch {
    initialChecked.value = new Set();
    checkedIds.value = new Set();
  }
}
async function loadScope(roleId) {
  if (!roleId) return;
  scopeLoading.value = true;
  try {
    const r = await getScopeMatrix(roleId);
    if (r && r.bizTypes) scopeMatrix.value = r;
    else scopeMatrix.value = { roles: [], bizTypes: [], matrix: {} };
  } catch { scopeMatrix.value = { roles: [], bizTypes: [], matrix: {} }; }
  finally { scopeLoading.value = false; }
}

async function onPickRole(id) {
  if (id === pickedRoleId.value) return;
  if (dirty.value) {
    try { await ElMessageBox.confirm('当前角色有未保存改动，切换会丢失，确定？', '提示', { type: 'warning' }); }
    catch { return; }
  }
  pickedRoleId.value = id;
}

async function reload() {
  await Promise.all([loadRoles(), loadResources()]);
  if (pickedRoleId.value) {
    await Promise.all([loadRoleChecked(pickedRoleId.value), loadScope(pickedRoleId.value)]);
  }
}

watch(pickedRoleId, async (id) => {
  if (id) await Promise.all([loadRoleChecked(id), loadScope(id)]);
});

onMounted(reload);
</script>

<style lang="scss" scoped>
.three-cols { display: grid; grid-template-columns: 1fr 1.5fr 1.1fr; gap: 12px; }
.col { padding: 0; height: calc(100vh - 160px); display: flex; flex-direction: column; overflow: hidden; }
.col-h {
  padding: 12px 14px; border-bottom: 1px solid $border-1; background: $bg-strip;
  font-weight: 500; font-size: 13px;
  display: flex; align-items: center; gap: 8px;
  > div:first-child { flex: 1; }
}
.col-foot {
  padding: 10px 14px; border-top: 1px solid $border-1;
  display: flex; align-items: center; gap: 8px;
  .dirty { font-size: 12px; color: $warning; flex: 1;
    &.ok { color: $success; }
  }
}
.role-list, .res-list { flex: 1; overflow: auto; }

.role {
  padding: 10px 14px; display: flex; align-items: center; cursor: pointer;
  border-left: 3px solid transparent;
  &:hover { background: $bg-soft; }
  &.active {
    background: $primary-100; border-left-color: $primary;
    .r-name { color: $primary; font-weight: 500; }
  }
  .r-info { flex: 1; min-width: 0;
    .r-name { font-size: 13px; }
    .r-code { font-size: 11px; color: $text-3; margin-top: 2px;
      code { font-family: ui-monospace, monospace; background: rgba(0,0,0,.04); padding: 0 4px; border-radius: 2px; }
    }
  }
  .cnt { color: $text-3; font-size: 11px; white-space: nowrap; }
  &.active .cnt { color: $primary; }
}

.res-group-block { border-bottom: 1px solid $border-3; }
.res-group {
  padding: 8px 14px; font-size: 12.5px; font-weight: 500;
  background: $bg-soft; position: sticky; top: 0; z-index: 1;
  display: flex; align-items: center; gap: 8px;
  .caret { cursor: pointer; user-select: none; font-size: 10px; color: $text-3;
    width: 14px; text-align: center; transition: color .12s;
    &:hover { color: $primary; }
  }
  .ico { font-size: 14px; }
  .g-name { flex: 1; }
  .cnt { color: $text-3; font-size: 11px; font-weight: normal; }
  .rw-h { display: flex; align-items: center; gap: 4px; font-size: 11px; font-weight: 600;
    .el-checkbox { margin-right: 0; }
  }
  .rw-all { display: flex; align-items: center; gap: 4px; font-size: 11px; font-weight: 700;
    padding-right: 8px; margin-right: 4px; border-right: 1px solid $border-3;
    .g-all-label { color: $primary; }
    .el-checkbox { margin-right: 0; }
  }
}
.res-item {
  padding: 6px 14px;
  display: grid;
  grid-template-columns: 1fr 60px;
  align-items: center; gap: 8px; font-size: 13px;
  &:hover { background: $bg-soft; }
  .res-meta { min-width: 0;
    .res-name { font-size: 12.5px; }
    .res-url {
      display: flex; align-items: center; gap: 6px;
      margin-top: 3px;
      color: $text-3; font-size: 11px;
      font-family: ui-monospace, monospace;
      :deep(.el-tag) { font-family: ui-monospace, monospace; padding: 0 5px; height: 18px; line-height: 18px; font-size: 10.5px; }
    }
  }
  .rw-cell { display: flex; align-items: center; justify-content: space-around; gap: 4px;
    .dash { color: $text-4; font-size: 13px; width: 14px; text-align: center; }
  }
}
.empty { padding: 30px 14px; text-align: center; color: $text-3; font-size: 12px; }

.legend {
  padding: 10px 14px; border-top: 1px solid $border-1;
  font-size: 11px; color: $text-3; line-height: 1.7;
  code { font-family: ui-monospace, monospace; background: rgba(0,0,0,.04); padding: 0 4px; border-radius: 2px; color: $text-2; }
}
</style>
