<template>
  <div>
    <div class="page-h">
      <h1>角色管理 <span class="sub">角色 CRUD · 已绑用户查看 · 资源/数据范围请去【权限配置】</span></h1>
      <div class="actions">
        <el-button @click="reload">刷新</el-button>
        <el-button @click="goPermission">→ 配置资源/数据范围</el-button>
        <el-button type="primary" @click="openCreate">+ 新增角色</el-button>
      </div>
    </div>

    <div class="card-section">
      <el-form inline size="default">
        <el-form-item label="关键字">
          <el-input v-model="filters.keyword" placeholder="角色名 / 角色编码" clearable style="width:220px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.recordStatus" clearable placeholder="全部" style="width:140px">
            <el-option :value="0" label="可用" />
            <el-option :value="1" label="不可用" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="reload">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card-section">
      <el-table :data="rows" size="default" v-loading="loading" empty-text="暂无角色">
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
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" size="small" @click="openMenuDlg(row)">分配菜单</el-button>
            <el-button link type="primary" size="small" @click="openUsers(row)">已绑用户</el-button>
            <el-popconfirm
              :title="`确认删除角色 ${row.roleChName}？已绑用户将解绑。`"
              @confirm="doDelete(row)"
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
          :page-sizes="[10, 20, 50]"
          :total="pager.total"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="reload"
          @current-change="reload"
        />
      </div>
    </div>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog v-model="dlg.show" :title="dlg.editing ? '编辑角色' : '新增角色'" width="520px">
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

    <!-- 分配菜单弹窗（参考 xanpd role.vue：左侧角色名 + 右侧菜单树勾选） -->
    <el-dialog v-model="menuDlg.show" :title="`分配菜单 · ${menuDlg.role?.roleChName || ''}`" width="520px" :close-on-click-modal="false">
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
    <el-dialog v-model="userDlg.show" :title="`已绑用户 · ${userDlg.role?.roleChName || ''}`" width="760px">
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import {
  listRoles, createRole, updateRole, deleteRole, listRoleUsers,
  getMenuTree, getRoleMenuIds, replaceRoleMenus
} from '@/api/system';

const router = useRouter();
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

onMounted(reload);
</script>

<style lang="scss" scoped>
.page-h h1 .sub { font-size: 13px; color: $text-3; margin-left: 12px; font-weight: 400; }
.pager { margin-top: 14px; display: flex; justify-content: flex-end; }
.hint { color: $text-3; font-size: 12px; margin-top: 4px; }
.mono { font-family: ui-monospace, monospace; font-size: 12px; }
.menu-tree-wrap {
  max-height: 360px;
  overflow: auto;
  width: 100%;
  border: 1px solid $border-2;
  border-radius: 4px;
  padding: 6px 8px;
}
</style>
