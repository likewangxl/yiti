<template>
<main v-bp-overflow-tooltip class="bp-crud roles-page" aria-labelledby="roles-page-title">
    <header class="page-h">
      <PageTitle id="roles-page-title"><span class="sub">角色 CRUD · 已绑用户查看 · 资源/数据范围请去【权限配置】</span></PageTitle>
      <div class="actions action-group" role="group" aria-label="角色管理操作">
        <el-button @click="reload">刷新</el-button>
        <el-button @click="goPermission">→ 配置资源/数据范围</el-button>
        <el-button type="primary" @click="openCreate">+ 新增角色</el-button>
      </div>
    </header>

    <section class="card-section filter-bar" aria-label="角色筛选">
      <el-form class="filter-form" inline size="default" aria-label="角色筛选">
        <el-form-item label="关键字">
          <el-input v-model="filters.keyword" aria-label="按角色名称或编码筛选" placeholder="角色名 / 角色编码" clearable style="width:220px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.recordStatus" aria-label="按角色状态筛选" clearable placeholder="全部" style="width:140px">
            <el-option :value="0" label="可用" />
            <el-option :value="1" label="不可用" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="reload">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="card-section data-panel" aria-label="角色列表" aria-describedby="roles-table-state" :aria-busy="loading ? 'true' : 'false'">
      <div class="toolbar">
        <div>
          <h2 id="roles-table-heading" class="section-title">角色列表</h2>
          <p class="hint">查看角色状态、已绑用户及菜单分配。</p>
        </div>
        <p id="roles-table-state" class="table-state" role="status" aria-live="polite">
          {{ loading ? '角色列表加载中' : rows.length ? `共 ${pager.total} 个角色` : '暂无角色数据' }}
        </p>
      </div>

      <el-table :data="rows" size="default" v-loading="loading" empty-text="暂无角色数据" aria-labelledby="roles-table-heading" aria-describedby="roles-table-state">
        <el-table-column prop="roleCode" label="角色编码" width="160">
          <template #default="{row}"><code class="mono">{{ row.roleCode }}</code></template>
        </el-table-column>
        <el-table-column prop="roleChName" label="角色名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="用户数" width="100" align="right">
          <template #default="{row}">{{ row.userCount ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{row}">
            <el-tag :class="row.recordStatus === 0 ? 'tag-success' : 'tag-warning'" effect="plain" size="small">
              {{ row.recordStatus === 0 ? '可用' : '不可用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="160" :formatter="fmtDateTime" />
        <el-table-column label="操作" class-name="operation-cell" width="320" fixed="right">
          <template #default="{row}">
            <BpAdaptiveRowActions>
              <template #primary><el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button></template>
              <template #expanded><el-button link type="primary" size="small" @click="openMenuDlg(row)">分配菜单</el-button><el-button v-if="loginScreenReadable" link type="primary" size="small" @click="openLoginScreenDlg(row)">登录首屏</el-button><el-button link type="primary" size="small" @click="openUsers(row)">已绑用户</el-button><el-button link type="danger" size="small" @click="confirmDelete(row)">删除</el-button></template>
              <template #compact><el-dropdown trigger="click" popper-class="bp-crud-menu"><el-button link size="small" aria-label="更多角色操作">更多</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item @click="openMenuDlg(row)">分配菜单</el-dropdown-item><el-dropdown-item v-if="loginScreenReadable" @click="openLoginScreenDlg(row)">登录首屏</el-dropdown-item><el-dropdown-item @click="openUsers(row)">已绑用户</el-dropdown-item><el-dropdown-item divided class="danger-item" @click="confirmDelete(row)">删除</el-dropdown-item></el-dropdown-menu></template></el-dropdown></template>
            </BpAdaptiveRowActions>
          </template>
        </el-table-column>
      </el-table>

      <nav class="pager" aria-label="角色列表分页">
        <el-pagination
          v-model:current-page="pager.pageNo"
          v-model:page-size="pager.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="pager.total"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="reload"
          @current-change="reload"
        />
      </nav>
    </section>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog v-model="dlg.show" class="bp-crud-dialog" :title="dlg.editing ? '编辑角色' : '新增角色'" width="520px">
      <el-form ref="dlgFormRef" :model="dlg.form" :rules="dlg.rules" label-width="100px">
        <!-- 角色编码：新增时不显示（后端自动生成 R_XXXXXXXX），编辑时只读展示 -->
        <el-form-item v-if="dlg.editing" label="角色编码">
          <el-input v-model="dlg.form.roleCode" disabled />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleChName">
          <el-input v-model="dlg.form.roleChName" placeholder="中文名称" maxlength="100" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="dlg.form.remark" type="textarea" :rows="2" placeholder="选填" maxlength="100" />
        </el-form-item>
        <el-form-item v-if="dlg.editing" label="状态" prop="recordStatus">
          <el-radio-group v-model="dlg.form.recordStatus">
            <el-radio :value="0">启用</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg.show = false">取消</el-button>
        <el-button type="primary" :loading="dlg.saving" @click="saveDlg">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="loginScreenDlg.show" class="bp-crud-dialog" :title="`登录首屏 · ${loginScreenDlg.role?.roleChName || ''}`" width="min(520px, calc(100vw - 32px))" :close-on-click-modal="false">
      <el-form v-if="loginScreenDlg.role" label-width="110px" :disabled="loginScreenDlg.saving || !loginScreenDlg.editable">
        <p v-if="loginScreenDlg.error" class="hint" role="alert">{{ loginScreenDlg.error }}</p>
        <el-form-item label="默认大屏"><el-select v-model="loginScreenDlg.form.target" :disabled="!loginScreenDlg.loaded || loginScreenDlg.loading" style="width:100%"><el-option v-for="item in LOGIN_SCREEN_TARGETS" :key="item.value" :value="item.value" :label="item.label" /></el-select></el-form-item>
        <el-form-item label="备用优先级"><el-input-number v-model="loginScreenDlg.form.priority" :disabled="!loginScreenDlg.loaded || loginScreenDlg.loading" :min="0" :max="10000" /></el-form-item>
        <p class="hint">仅决定登录后的默认展示，不授予大屏访问权限。</p>
        <el-form-item label="变更原因"><el-input v-model="loginScreenDlg.form.reason" :disabled="!loginScreenDlg.loaded || loginScreenDlg.loading" type="textarea" :rows="2" maxlength="200" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="loginScreenDlg.show = false">取消</el-button><el-button v-if="loginScreenDlg.conflict" @click="openLoginScreenDlg(loginScreenDlg.role)">重新读取</el-button><el-button type="primary" :disabled="!loginScreenDlg.loaded || !loginScreenDlg.editable" :loading="loginScreenDlg.saving" @click="saveLoginScreenDlg">保存</el-button></template>
    </el-dialog>

    <!-- 分配菜单弹窗（参考 xanpd role.vue：左侧角色名 + 右侧菜单树勾选） -->
    <el-dialog v-model="menuDlg.show" class="bp-crud-dialog" :title="`分配菜单 · ${menuDlg.role?.roleChName || ''}`" width="520px" :close-on-click-modal="false">
      <el-form label-width="80px" v-if="menuDlg.role">
        <el-form-item label="角色">
          <el-input :value="menuDlg.role.roleChName" disabled />
        </el-form-item>
        <el-form-item label="菜单树">
          <div v-loading="menuDlg.loading" class="menu-tree-wrap">
            <!-- :key 每次打开递增强制重建 el-tree，default-checked-keys 才作为初始勾选生效；
                 否则二次打开时 el-tree 复用旧实例，内部勾选状态不刷新 → 取消后保存会把旧勾选送回去 -->
            <el-tree
              v-if="!menuDlg.loading"
              :key="menuDlg.openSeq"
              ref="menuTreeRef"
              :data="menuDlg.tree"
              show-checkbox
              node-key="resourceId"
              default-expand-all
              :default-checked-keys="menuDlg.checkedIds"
              :props="{ label: 'menuName', children: 'children' }"
              empty-text="暂无菜单"
            >
              <template #default="{ data }">
                <span>{{ data.menuIconUrl || '' }} {{ data.menuName }}</span>
              </template>
            </el-tree>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="menuDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="menuDlg.saving" @click="saveMenuDlg">保存</el-button>
      </template>
    </el-dialog>

    <!-- 已绑用户弹窗 -->
    <el-dialog v-model="userDlg.show" class="bp-crud-dialog" :title="`已绑用户 · ${userDlg.role?.roleChName || ''}`" width="760px">
      <el-table :data="userDlg.rows" size="default" v-loading="userDlg.loading" max-height="420" empty-text="暂无用户">
        <!-- 后端 RoleUserRespDTO 字段：empId/username/displayName/orgCode/orgName/isEnabled/bindTime -->
        <el-table-column label="工号" width="120">
          <template #default="{row}"><code class="mono">{{ row.empId || row.userId || '-' }}</code></template>
        </el-table-column>
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column label="姓名" width="120">
          <template #default="{row}">{{ row.displayName || row.userchnname || '-' }}</template>
        </el-table-column>
        <el-table-column label="机构" min-width="180" show-overflow-tooltip>
          <template #default="{row}">
            {{ row.orgName || '-' }}
            <span v-if="row.orgCode" class="sub-id">({{ row.orgCode }})</span>
          </template>
        </el-table-column>
        <el-table-column label="绑定时间" width="160">
          <template #default="{row}">{{ fmtBindTime(row.bindTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{row}">
            <el-tag :class="row.isEnabled === 0 ? 'tag-success' : 'tag-warning'" effect="plain" size="small">
              {{ row.isEnabled === 0 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="userDlg.pageNo"
          v-model:page-size="userDlg.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="userDlg.total"
          background
          small
          layout="total, sizes, prev, pager, next"
          @size-change="loadRoleUsers"
          @current-change="loadRoleUsers"
        />
      </div>
      <template #footer>
        <el-button @click="userDlg.show = false">关闭</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getRoleLoginScreen, saveRoleLoginScreen } from '@/api/loginScreen';
import { usePermissionStore } from '@/stores/permission';
import { useUserStore } from '@/stores/user';
import BpAdaptiveRowActions from '@/components/BpAdaptiveRowActions.vue';
import {
  listRoles, createRole, updateRole, deleteRole, listRoleUsers,
  getMenuTree, getRoleMenuIds, replaceRoleMenus
} from '@/api/system';

const router = useRouter();
let permissionStore = null;
const userStore = useUserStore();
permissionStore = usePermissionStore();
const permissionReady = ref(false);
const loginScreenReadable = computed(() => permissionReady.value && permissionStore?.loaded
  && permissionStore.canAccess('/api/admin/sys/login-screens/*'));
function goPermission() { router.push('/system/permission'); }

// 后端 LocalDateTime 返回 ISO 字符串（2026-04-07T19:20:37），截到分钟方便阅读
function fmtBindTime(t) {
  if (!t) return '-';
  return String(t).replace('T', ' ').slice(0, 16);
}
// el-table column formatter: (row, column, cellValue, index) → 格式化显示
const fmtDateTime = (_row, _col, v) => v ? String(v).replace('T', ' ').slice(0, 19) : '-';

// === 列表 ===
const rows = ref([]);
const loading = ref(false);
const filters = reactive({ keyword: '', recordStatus: null });
const pager = reactive({ pageNo: 1, pageSize: 20, total: 0 });

function resetFilters() {
  filters.keyword = '';
  filters.recordStatus = null;
  pager.pageNo = 1;
  reload();
}
async function reload() {
  loading.value = true;
  try {
    const r = await listRoles({
      pageNo: pager.pageNo,
      pageSize: pager.pageSize,
      keyword: filters.keyword || undefined,
      recordStatus: filters.recordStatus ?? undefined
    });
    // r 可能是 {total, records} 或纯数组（mock fallback）
    if (r && !Array.isArray(r) && Array.isArray(r.records)) {
      rows.value = r.records;
      pager.total = r.total ?? 0;
    } else {
      rows.value = Array.isArray(r) ? r : [];
      pager.total = rows.value.length;
    }
  } catch { rows.value = []; }
  finally { loading.value = false; }
}

// === 新增 / 编辑 ===
const dlgFormRef = ref(null);
const dlg = reactive({
  show: false, editing: null, saving: false,
  form: { roleCode: '', roleChName: '', remark: '', sysCode: '', recordStatus: 0 },
  rules: {
    // 角色编码：编辑时只读不校验，新增时后端自动生成，前端不再校验
    roleChName: [{ required: true, message: '角色名称必填', trigger: 'blur' }, { max: 100, message: '不超过 100 位', trigger: 'blur' }],
    remark:     [{ max: 100, message: '不超过 100 位', trigger: 'blur' }],
    sysCode:    [{ max: 10, message: '不超过 10 位', trigger: 'blur' }]
  }
});
function openCreate() {
  dlg.editing = null;
  dlg.form = { roleCode: '', roleChName: '', remark: '', sysCode: '', recordStatus: 0 };
  dlg.show = true;
}
function openEdit(row) {
  dlg.editing = row.roleId;
  dlg.form = {
    roleCode: row.roleCode,
    roleChName: row.roleChName,
    remark: row.remark || '',
    sysCode: row.sysCode || '',
    recordStatus: typeof row.recordStatus === 'number' ? row.recordStatus : 0
  };
  dlg.show = true;
}
async function saveDlg() {
  try { await dlgFormRef.value?.validate(); } catch { return; }
  dlg.saving = true;
  try {
    if (dlg.editing) {
      await updateRole(dlg.editing, {
        roleChName: dlg.form.roleChName,
        remark: dlg.form.remark,
        recordStatus: dlg.form.recordStatus
      });
      ElMessage.success('已更新');
    } else {
      // 新增不传 roleCode，由后端自动生成（R_XXXXXXXX UUID 8 位大写）
      await createRole({
        roleChName: dlg.form.roleChName,
        remark: dlg.form.remark || undefined,
        sysCode: dlg.form.sysCode || undefined
      });
      ElMessage.success('已创建');
    }
    dlg.show = false;
    await reload();
  } catch (e) {
    ElMessage.error((dlg.editing ? '更新失败：' : '创建失败：') + (e?.message || e));
  } finally { dlg.saving = false; }
}

async function doDelete(row) {
  try {
    await deleteRole(row.roleId, '前端删除');
    ElMessage.success('已删除');
    await reload();
  } catch (e) {
    ElMessage.error('删除失败：' + (e?.message || e));
  }
}

async function confirmDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除角色 ${row.roleChName}？已绑用户将解绑。`, '删除确认', {
      type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消'
    });
  } catch { return; }
  await doDelete(row);
}

// === 分配菜单弹窗 ===
const menuTreeRef = ref(null);
const menuDlg = reactive({
  show: false, role: null, loading: false, saving: false,
  tree: [], checkedIds: [], reason: '',
  openSeq: 0  // 每次 open 递增，给 el-tree 当 :key 触发重建
});
async function openMenuDlg(row) {
  menuDlg.role = row;
  menuDlg.reason = '';
  menuDlg.show = true;
  menuDlg.loading = true;
  menuDlg.openSeq++;
  try {
    const [tree, checked] = await Promise.all([
      getMenuTree(),
      getRoleMenuIds(row.roleId)
    ]);
    menuDlg.tree = Array.isArray(tree) ? tree : [];
    menuDlg.checkedIds = Array.isArray(checked) ? checked : [];
  } catch {
    menuDlg.tree = [];
    menuDlg.checkedIds = [];
  } finally { menuDlg.loading = false; }
}
async function saveMenuDlg() {
  // 只送叶子节点 ID（leafOnly=true）。原因：
  //  el-tree 在 check-strictly=false（默认）下，default-checked-keys 含父节点 ID 会自动联动勾选全部子。
  //  之前同时送 checkedKeys + halfCheckedKeys 把半选父也存进 PT_ROLE_RESOURCE，
  //  下次打开时 default-checked-keys 含父 → 用户原本取消的子被自动重新勾上 → "取消没生效"。
  //  父分组节点的"是否勾选"由叶子子节点的勾选状态自动派生，不需要单独存。
  const menuIds = menuTreeRef.value?.getCheckedKeys(true) || [];
  menuDlg.saving = true;
  try {
    await replaceRoleMenus(menuDlg.role.roleId, menuIds, menuDlg.reason.trim());
    ElMessage.success(`已分配 ${menuIds.length} 个菜单`);
    menuDlg.show = false;
  } catch (e) {
    ElMessage.error('保存失败：' + (e?.message || e));
  } finally { menuDlg.saving = false; }
}

// === 已绑用户弹窗 ===
const userDlg = reactive({
  show: false, role: null, loading: false,
  rows: [], pageNo: 1, pageSize: 20, total: 0
});
const LOGIN_SCREEN_TARGETS = Object.freeze([
  { value: 'WORKSPACE', label: '工作台' }, { value: 'PERSONAL', label: '个人经营大屏' },
  { value: 'BRANCH', label: '支行经营大屏' }, { value: 'PROVINCE', label: '分行经营总览' },
  { value: 'RETAIL', label: '零售经营大屏' }, { value: 'CORPORATE', label: '对公经营大屏' }
]);
const loginScreenDlg = reactive({ show: false, role: null, loading: false, loaded: false, editable: false, saving: false, conflict: false, error: '', form: { target: 'WORKSPACE', priority: 100, reason: '', expectedVersion: '' } });
let loginScreenGeneration = 0;
function canReadLoginScreen() {
  return loginScreenReadable.value;
}
async function openLoginScreenDlg(row) {
  loginScreenDlg.saving = false;
  loginScreenDlg.role = row;
  loginScreenDlg.show = true; loginScreenDlg.loading = true; loginScreenDlg.loaded = false; loginScreenDlg.editable = false; loginScreenDlg.conflict = false; loginScreenDlg.error = '';
  loginScreenDlg.form = { target: 'WORKSPACE', priority: 100, reason: '', expectedVersion: '' };
  const generation = ++loginScreenGeneration;
  if (!canReadLoginScreen()) { loginScreenDlg.loading = false; loginScreenDlg.error = '当前权限快照未确认或无权配置登录首屏'; return; }
  try {
    const result = await getRoleLoginScreen(row.roleId);
    if (generation !== loginScreenGeneration || loginScreenDlg.role?.roleId !== row.roleId) return;
    const target = String(result?.target || '');
    const priority = result?.priority;
    if (String(result?.roleId || '') !== String(row.roleId) || !LOGIN_SCREEN_TARGETS.some(item => item.value === target)
      || !Number.isInteger(priority) || priority < 0 || priority > 10000 || typeof result?.version !== 'string') {
      throw new Error('登录首屏配置响应无效');
    }
    loginScreenDlg.form.target = target;
    loginScreenDlg.form.priority = priority;
    loginScreenDlg.form.expectedVersion = String(result.version || '');
    loginScreenDlg.editable = result.editable === true;
    loginScreenDlg.loaded = true;
  } catch (error) { if (generation === loginScreenGeneration) { loginScreenDlg.error = `读取登录首屏失败：${error?.message || error}`; ElMessage.error(loginScreenDlg.error); } }
  finally { if (generation === loginScreenGeneration) loginScreenDlg.loading = false; }
}
async function saveLoginScreenDlg() {
  if (!loginScreenDlg.loaded || !loginScreenDlg.editable || loginScreenDlg.loading || loginScreenDlg.saving || !canReadLoginScreen()) return;
  const reason = String(loginScreenDlg.form.reason || '').trim();
  if (!reason) { ElMessage.error('请填写变更原因'); return; }
  loginScreenDlg.saving = true;
  loginScreenDlg.conflict = false;
  const token = loginScreenGeneration;
  const roleId = loginScreenDlg.role?.roleId;
  const snapshot = { target: loginScreenDlg.form.target, priority: loginScreenDlg.form.priority, expectedVersion: loginScreenDlg.form.expectedVersion, user: userStore.user };
  const current = () => token === loginScreenGeneration && loginScreenDlg.show && loginScreenDlg.role?.roleId === roleId
    && userStore.user === snapshot.user && loginScreenDlg.form.target === snapshot.target
    && Number(loginScreenDlg.form.priority) === Number(snapshot.priority) && loginScreenDlg.form.expectedVersion === snapshot.expectedVersion;
  try {
    const result = await saveRoleLoginScreen(loginScreenDlg.role.roleId, {
      target: loginScreenDlg.form.target, priority: loginScreenDlg.form.priority,
      expectedVersion: loginScreenDlg.form.expectedVersion || '', reason
    });
    if (!current()) return;
    const reread = await getRoleLoginScreen(loginScreenDlg.role.roleId);
    if (!current()) return;
    if (reread?.target !== loginScreenDlg.form.target || Number(reread?.priority) !== Number(loginScreenDlg.form.priority) || !reread?.version || (result?.version && reread.version !== result.version)) throw new Error('保存回读不一致');
    loginScreenDlg.form.expectedVersion = reread.version;
    loginScreenDlg.show = false;
    ElMessage.success('登录首屏已保存');
  } catch (error) {
    if (!current()) return;
    const conflict = Number(error?.response?.status || error?.status) === 409 || String(error?.code || '').toUpperCase().includes('CONFLICT') || String(error?.code || '').toUpperCase() === 'GOV-40910';
    loginScreenDlg.conflict = conflict;
    ElMessage.error(conflict ? '配置版本已变化，请点击“重新读取”后再保存' : `保存失败：${error?.message || error}`);
  }
  finally { if (token === loginScreenGeneration) loginScreenDlg.saving = false; }
}
function openUsers(row) {
  userDlg.role = row;
  userDlg.pageNo = 1;
  userDlg.show = true;
  loadRoleUsers();
}
async function loadRoleUsers() {
  if (!userDlg.role) return;
  userDlg.loading = true;
  try {
    const r = await listRoleUsers(userDlg.role.roleId, { pageNo: userDlg.pageNo, pageSize: userDlg.pageSize });
    userDlg.rows = Array.isArray(r) ? r : (r?.records || []);
    userDlg.total = r?.total ?? userDlg.rows.length;
  } catch { userDlg.rows = []; }
  finally { userDlg.loading = false; }
}

async function loadLoginScreenPermission() {
  try { if (!permissionStore) throw new Error('permission store unavailable'); await permissionStore.load(); permissionReady.value = true; } catch { permissionReady.value = false; }
}
onMounted(() => { void loadLoginScreenPermission(); void reload(); });
watch(() => loginScreenDlg.show, (show) => { if (!show) loginScreenGeneration += 1; });
watch(() => userStore.user, () => { loginScreenGeneration += 1; loginScreenDlg.show = false; });
onBeforeUnmount(() => { loginScreenGeneration += 1; });
</script>

<style lang="scss" scoped>
.mono { color: var(--color-text); font-family: ui-monospace, monospace; font-size: 12px; }
.menu-tree-wrap {
  max-height: 360px;
  overflow: auto;
  width: 100%;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-control);
  padding: var(--space-2);
}
</style>
