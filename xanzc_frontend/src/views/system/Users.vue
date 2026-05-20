<template>
  <div>
    <div class="page-h">
      <h1>用户管理 <span class="sub">按机构筛选 · 启停/锁解/重置密码 · 分配角色</span></h1>
      <div class="actions">
        <el-button @click="reload">刷新</el-button>
        <el-button type="primary" @click="openCreate">+ 新增用户</el-button>
      </div>
    </div>

    <div class="layout">
      <!-- 左：机构树 -->
      <div class="card-section tree-col">
        <div class="card-h-mini">机构</div>
        <el-input
          v-model="orgKeyword"
          placeholder="搜索机构"
          size="small"
          clearable
          :prefix-icon="Search"
          class="tree-search"
        />
        <el-tree
          ref="orgTreeRef"
          :data="orgTree"
          node-key="code"
          :props="{ label: 'name', children: 'children' }"
          :default-expand-all="true"
          :expand-on-click-node="false"
          :highlight-current="true"
          :filter-node-method="filterOrgNode"
          @node-click="onOrgClick"
          empty-text="暂无机构"
        />
      </div>

      <!-- 右：用户列表 -->
      <div class="card-section detail-col">
        <!-- 筛选栏 -->
        <el-form inline size="default" class="filter-form">
          <el-form-item label="机构">
            <el-tag effect="plain" closable @close="clearOrg" v-if="pickedOrg">
              {{ pickedOrgName }}（{{ pickedOrg }}）
            </el-tag>
            <span v-else class="hint">未选择 · 显示全部</span>
          </el-form-item>
          <el-form-item label="用户名">
            <el-input v-model="filters.username" placeholder="模糊匹配" clearable style="width:180px" />
          </el-form-item>
          <el-form-item label="姓名">
            <el-input v-model="filters.userchnname" placeholder="模糊匹配" clearable style="width:180px" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="filters.isEnabled" clearable placeholder="全部" style="width:120px">
              <el-option :value="0" label="启用" />
              <el-option :value="1" label="停用" />
            </el-select>
          </el-form-item>
          <el-form-item label="锁定">
            <el-select v-model="filters.isLocked" clearable placeholder="全部" style="width:120px">
              <el-option :value="0" label="正常" />
              <el-option :value="1" label="锁定" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="reload">查询</el-button>
            <el-button @click="resetFilters">重置</el-button>
          </el-form-item>
        </el-form>

        <!-- 批量操作 -->
        <div class="batch-bar">
          <span class="hint">已选 {{ selection.length }} 条</span>
          <el-button :disabled="!selection.length" @click="batch('active')">启用</el-button>
          <el-button :disabled="!selection.length" @click="batch('inactive')">停用</el-button>
          <el-button :disabled="!selection.length" @click="batch('lock')">锁定</el-button>
          <el-button :disabled="!selection.length" @click="batch('unlock')">解锁</el-button>
          <el-button :disabled="!selection.length" @click="batch('reset')">重置密码</el-button>
          <el-button :disabled="!selection.length" type="danger" plain @click="batch('delete')">删除</el-button>
        </div>

        <!-- 表格 -->
        <el-table
          :data="rows"
          size="default"
          v-loading="loading"
          empty-text="暂无用户"
          @selection-change="onSelectionChange"
        >
          <el-table-column type="selection" width="42" />
          <el-table-column prop="userId" label="工号" width="120">
            <template #default="{row}"><code class="mono">{{ row.userId }}</code></template>
          </el-table-column>
          <el-table-column prop="username" label="用户名" width="140" />
          <el-table-column prop="userchnname" label="姓名" width="120" />
          <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
          <el-table-column prop="remark" label="手机号" width="140" />
          <el-table-column label="状态" width="80">
            <template #default="{row}">
              <el-tag :class="row.isEnabled === 0 ? 'tag-success' : 'tag-warning'" effect="plain" size="small">
                {{ USER_STATUS_LABEL[row.isEnabled] || '-' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="锁定" width="80">
            <template #default="{row}">
              <el-tag :class="row.isLocked === 1 ? 'tag-danger' : 'tag-info'" effect="plain" size="small">
                {{ USER_LOCK_LABEL[row.isLocked] || '-' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="160" />
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{row}">
              <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button link type="primary" size="small" @click="openAssignRoles(row)">分配角色</el-button>
              <el-popconfirm
                :title="`确认删除用户 ${row.username}？`"
                @confirm="batch('delete', [row.userId])"
              >
                <template #reference>
                  <el-button link type="danger" size="small">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="pager.pageNo"
            v-model:page-size="pager.pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="pager.total"
            background
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="reload"
            @current-change="reload"
          />
        </div>
      </div>
    </div>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog v-model="dlg.show" :title="dlg.editing ? '编辑用户' : '新增用户'" width="640px">
      <el-form ref="dlgFormRef" :model="dlg.form" :rules="dlg.rules" label-width="100px">
        <el-form-item label="工号" prop="userId">
          <el-input v-model="dlg.form.userId" :disabled="!!dlg.editing" placeholder="如 U10001" maxlength="32" />
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="dlg.form.username" placeholder="登录名" maxlength="64" />
        </el-form-item>
        <el-form-item label="姓名" prop="userchnname">
          <el-input v-model="dlg.form.userchnname" placeholder="中文姓名" maxlength="64" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="dlg.form.email" placeholder="选填" maxlength="128" />
        </el-form-item>
        <el-form-item label="手机号" prop="remark">
          <el-input v-model="dlg.form.remark" placeholder="选填" maxlength="20" />
        </el-form-item>
        <el-form-item v-if="!dlg.editing" label="初始密码" prop="initialPassword">
          <el-input v-model="dlg.form.initialPassword" type="password" show-password placeholder="6~64 位，明文提交后端" maxlength="64" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg.show = false">取消</el-button>
        <el-button type="primary" :loading="dlg.saving" @click="saveDlg">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色弹窗 -->
    <el-dialog v-model="roleDlg.show" :title="`分配角色 · ${roleDlg.user?.username || ''}`" width="720px">
      <div class="role-dlg-tip">已选中作为最终状态提交：差量由前端计算（新增 + 解绑）。</div>
      <el-transfer
        v-model="roleDlg.value"
        :data="roleDlg.options"
        :titles="['可选角色', '已分配']"
        :props="{ key: 'roleId', label: 'roleChName' }"
        filterable
        filter-placeholder="按角色名搜索"
        style="margin-top:8px"
      />
      <el-form label-width="100px" style="margin-top:12px">
        <el-form-item label="备注理由" required>
          <el-input v-model="roleDlg.reason" placeholder="审计必填，简短说明本次调整原因" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="roleDlg.saving" @click="saveRoles">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted, computed } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Search } from '@element-plus/icons-vue';
import {
  listUsers, createUser, updateUser,
  deleteUsers, resetUsersPassword, activeUsers, inactiveUsers, lockUsers, unlockUsers,
  getUserRoles, replaceUserRoles,
  USER_STATUS_LABEL, USER_LOCK_LABEL
} from '@/api/users';
import { listRoles } from '@/api/system';
import { getOrgTree, listOrgUsers } from '@/api/orgs';

// === 机构树 ===
const orgTreeRef = ref(null);
const orgTree = ref([]);
const orgKeyword = ref('');
const pickedOrg = ref('');     // orgCode
const pickedOrgName = ref('');
watch(orgKeyword, v => orgTreeRef.value?.filter(v ?? ''));
function filterOrgNode(value, data) {
  if (!value) return true;
  const v = String(value).toLowerCase();
  return (
    String(data.name || '').toLowerCase().includes(v) ||
    String(data.code || '').toLowerCase().includes(v)
  );
}
function onOrgClick(node) {
  pickedOrg.value = node.code;
  pickedOrgName.value = node.name;
  pager.pageNo = 1;
  reload();
}
function clearOrg() {
  pickedOrg.value = '';
  pickedOrgName.value = '';
  pager.pageNo = 1;
  reload();
}

// === 列表 ===
const rows = ref([]);
const loading = ref(false);
const filters = reactive({ username: '', userchnname: '', isEnabled: null, isLocked: null });
const pager = reactive({ pageNo: 1, pageSize: 20, total: 0 });
const selection = ref([]);

function resetFilters() {
  filters.username = '';
  filters.userchnname = '';
  filters.isEnabled = null;
  filters.isLocked = null;
  pager.pageNo = 1;
  reload();
}
function onSelectionChange(rs) { selection.value = rs; }

async function reload() {
  loading.value = true;
  try {
    const params = {
      pageNo: pager.pageNo,
      pageSize: pager.pageSize,
      username: filters.username || undefined,
      userchnname: filters.userchnname || undefined,
      isEnabled: filters.isEnabled ?? undefined,
      isLocked: filters.isLocked ?? undefined
    };
    // 已选机构时走机构子路由，未选时走全量
    let r;
    if (pickedOrg.value) {
      r = await listOrgUsers(pickedOrg.value, params);
    } else {
      r = await listUsers(params);
    }
    const records = Array.isArray(r) ? r : (r?.records || []);
    // listOrgUsers（OrgController）返回 empId/displayName，与 listUsers（UserController）的 userId/userchnname 字段不一致；
    // 按机构筛选时若不归一，row.userId 为 undefined，分配角色 URL 会变 /admin/users/undefined/roles 触发 AUTH-40403
    rows.value = records.map(u => ({
      ...u,
      userId: u.userId ?? u.empId,
      userchnname: u.userchnname ?? u.displayName,
    }));
    // 后端 PageResult 总数（拦截器抽走后只剩 records，需要单独 total 时改用原 wrapper）
    pager.total = r?.total ?? rows.value.length;
  } catch {
    rows.value = [];
  } finally { loading.value = false; }
}

// === 新增 / 编辑弹窗 ===
const dlgFormRef = ref(null);
const dlg = reactive({
  show: false, editing: null, saving: false,
  form: { userId: '', username: '', userchnname: '', email: '', remark: '', initialPassword: '' },
  rules: {
    userId:          [{ required: true, message: '工号必填', trigger: 'blur' }, { max: 32, message: '不超过 32 位', trigger: 'blur' }],
    username:        [{ required: true, message: '用户名必填', trigger: 'blur' }, { max: 64, message: '不超过 64 位', trigger: 'blur' }],
    userchnname:     [{ required: true, message: '姓名必填', trigger: 'blur' }, { max: 64, message: '不超过 64 位', trigger: 'blur' }],
    email:           [{ pattern: /^[^@\s]+@[^@\s]+\.[^@\s]+$/, message: '邮箱格式不正确', trigger: 'blur' }],
    remark:          [{ pattern: /^$|^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
    initialPassword: [{ required: true, message: '初始密码必填', trigger: 'blur' }, { min: 6, max: 64, message: '6~64 位', trigger: 'blur' }]
  }
});
function openCreate() {
  dlg.editing = null;
  dlg.form = { userId: '', username: '', userchnname: '', email: '', remark: '', initialPassword: '' };
  dlg.show = true;
}
function openEdit(row) {
  dlg.editing = row.userId;
  dlg.form = {
    userId: row.userId,
    username: row.username,
    userchnname: row.userchnname,
    email: row.email || '',
    remark: row.remark || '',
    initialPassword: ''
  };
  dlg.show = true;
}
async function saveDlg() {
  try { await dlgFormRef.value?.validate(); } catch { return; }
  dlg.saving = true;
  try {
    if (dlg.editing) {
      const { userId, ...rest } = dlg.form;
      // 编辑时只提交可改字段，避免 partial update 把 initialPassword 等带过去
      const payload = { username: rest.username, userchnname: rest.userchnname, email: rest.email, remark: rest.remark };
      await updateUser(userId, payload);
      ElMessage.success('已更新');
    } else {
      await createUser({ ...dlg.form });
      ElMessage.success('已创建');
    }
    dlg.show = false;
    await reload();
  } catch (e) {
    ElMessage.error(dlg.editing ? '更新失败' : '创建失败');
  } finally { dlg.saving = false; }
}

// === 分配角色弹窗 ===
const roleDlg = reactive({
  show: false, user: null, saving: false,
  options: [],     // [{roleId, roleChName, ...}]
  value: [],       // 已选 roleId[]
  reason: ''
});
async function openAssignRoles(user) {
  roleDlg.user = user;
  roleDlg.value = [];
  roleDlg.reason = '';
  roleDlg.show = true;
  // 并行拉全量角色 + 用户已绑
  try {
    const [allRoles, bound] = await Promise.all([
      listRoles({ pageNo: 1, pageSize: 999 }),
      getUserRoles(user.userId)
    ]);
    // listRoles 已经 unwrapPage —— 实际上是 RoleRespDTO 数组
    const opts = (Array.isArray(allRoles) ? allRoles : []).map(r => ({
      roleId: r.roleId || r.id,
      roleChName: r.roleChName || r.name || r.roleId || r.id
    }));
    roleDlg.options = opts;
    roleDlg.value = (Array.isArray(bound) ? bound : []).map(r => r.roleId || r.id);
  } catch {
    roleDlg.options = [];
    roleDlg.value = [];
  }
}
async function saveRoles() {
  if (!roleDlg.reason || !roleDlg.reason.trim()) {
    ElMessage.warning('请填写备注理由（审计必填）');
    return;
  }
  roleDlg.saving = true;
  try {
    const r = await replaceUserRoles(roleDlg.user.userId, roleDlg.value, roleDlg.reason.trim());
    ElMessage.success(`已保存（新增 ${r.added}，解绑 ${r.removed}）`);
    roleDlg.show = false;
  } catch (e) {
    ElMessage.error('保存失败：' + (e?.message || e));
  } finally { roleDlg.saving = false; }
}

// === 批量操作 ===
const BATCH_DISPATCH = {
  active:   { fn: activeUsers,        msg: '已启用', confirm: false },
  inactive: { fn: inactiveUsers,      msg: '已停用', confirm: '确认停用所选用户？' },
  lock:     { fn: lockUsers,          msg: '已锁定', confirm: '确认锁定所选用户？' },
  unlock:   { fn: unlockUsers,        msg: '已解锁', confirm: false },
  reset:    { fn: resetUsersPassword, msg: '已重置密码', confirm: '确认将所选用户密码重置为默认值？' },
  delete:   { fn: deleteUsers,        msg: '已删除',     confirm: '确认删除所选用户？此操作不可逆。' }
};
async function batch(action, overrideIds = null) {
  const ids = overrideIds || selection.value.map(r => r.userId);
  if (!ids.length) return ElMessage.warning('请先勾选用户');
  const d = BATCH_DISPATCH[action];
  if (!d) return;
  if (d.confirm) {
    try { await ElMessageBox.confirm(d.confirm, '确认', { type: 'warning' }); } catch { return; }
  }
  try {
    await d.fn(ids);
    ElMessage.success(d.msg);
    await reload();
  } catch (e) {
    ElMessage.error('操作失败：' + (e?.message || e));
  }
}

// === 启动 ===
async function loadOrg() {
  try {
    const t = await getOrgTree();
    orgTree.value = Array.isArray(t) ? t : [];
  } catch { orgTree.value = []; }
}
onMounted(async () => {
  await Promise.all([loadOrg(), reload()]);
});
</script>

<style lang="scss" scoped>
.page-h h1 .sub { font-size: 13px; color: $text-3; margin-left: 12px; font-weight: 400; }
.layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 12px;
}
.tree-col {
  padding: 16px;
  max-height: calc(100vh - 200px);
  overflow: auto;
}
.tree-search { margin-bottom: 10px; }
.card-h-mini {
  font-size: 14px; font-weight: 600;
  padding: 0 0 12px;
  border-bottom: 1px solid $border-1;
  margin-bottom: 10px;
  color: $text-1;
}
.detail-col {
  padding: 16px 18px;
  max-height: calc(100vh - 200px);
  overflow: auto;
}
.filter-form { margin-bottom: 8px; }
.batch-bar {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  padding: 8px 12px; background: $bg-soft; border-radius: 4px;
  margin-bottom: 10px;
}
.hint { color: $text-3; font-size: 12px; }
.pager { margin-top: 14px; display: flex; justify-content: flex-end; }
.mono { font-family: ui-monospace, monospace; font-size: 12px; }
.role-dlg-tip { color: $text-3; font-size: 12px; }
</style>
