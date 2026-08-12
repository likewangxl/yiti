<template>
  <main class="users-page" aria-labelledby="users-page-title">
    <header class="page-h users-page-head">
      <div class="page-heading">
        <PageTitle id="users-page-title" title="用户管理"><span class="sub">按机构筛选 · 启停/锁解/重置密码 · 分配角色</span></PageTitle>
        <p class="page-desc">先限定机构范围，再按工号、姓名与状态查询用户。</p>
      </div>
      <div class="actions" aria-label="用户管理操作">
        <el-button @click="reload">刷新</el-button>
        <el-button @click="exportUsers">导出</el-button>
        <el-button type="primary" @click="openCreate">+ 新增用户</el-button>
      </div>
    </header>

    <div class="layout">
      <!-- 左：机构树 -->
      <section class="card-section tree-col" aria-labelledby="org-tree-title">
        <div class="card-h-mini">
          <h2 id="org-tree-title">机构范围</h2>
          <el-button size="small" type="primary" plain @click="openOrgDlg">维护</el-button>
        </div>
        <el-input
          v-model="orgKeyword"
          aria-label="搜索机构"
          placeholder="搜索机构"
          size="small"
          clearable
          :prefix-icon="Search"
          class="tree-search"
        />
        <div v-if="orgError" class="tree-error" role="alert">
          <span>{{ orgError }}</span>
          <el-button link type="primary" size="small" @click="loadOrg">重试</el-button>
        </div>
        <el-tree
          ref="orgTreeRef"
          :data="enabledOrgTree"
          aria-label="机构树"
          node-key="code"
          :props="{ label: 'name', children: 'children' }"
          :default-expand-all="true"
          :expand-on-click-node="false"
          :highlight-current="true"
          :filter-node-method="filterOrgNode"
          @node-click="onOrgClick"
          empty-text="暂无机构"
        />
      </section>

      <!-- 右：用户列表 -->
      <section class="card-section detail-col" aria-labelledby="user-list-title">
        <div class="detail-head">
          <div>
            <h2 id="user-list-title">用户列表</h2>
            <p>批量操作仅对当前勾选的用户生效。</p>
          </div>
          <span class="detail-count">共 {{ pager.total }} 条</span>
        </div>
        <!-- 筛选栏 -->
        <el-form inline size="default" class="filter-form" aria-label="用户筛选">
          <el-form-item label="机构">
            <el-tag effect="plain" closable @close="clearOrg" v-if="pickedOrg">
              {{ pickedOrgName }}（{{ pickedOrgDeptNo || '无编号' }}）
            </el-tag>
            <span v-else class="hint">未选择 · 显示全部</span>
          </el-form-item>
          <el-form-item label="工号">
            <el-input v-model="filters.username" aria-label="按工号筛选" placeholder="模糊匹配" clearable class="filter-control filter-control--text" />
          </el-form-item>
          <el-form-item label="姓名">
            <el-input v-model="filters.userchnname" aria-label="按姓名筛选" placeholder="模糊匹配" clearable class="filter-control filter-control--text" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="filters.isEnabled" aria-label="按启用状态筛选" clearable placeholder="全部" class="filter-control filter-control--state">
              <el-option :value="0" label="启用" />
              <el-option :value="1" label="停用" />
            </el-select>
          </el-form-item>
          <el-form-item label="锁定">
            <el-select v-model="filters.isLocked" aria-label="按锁定状态筛选" clearable placeholder="全部" class="filter-control filter-control--state">
              <el-option :value="0" label="正常" />
              <el-option :value="1" label="锁定" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" plain @click="reload">查询</el-button>
            <el-button @click="resetFilters">重置</el-button>
          </el-form-item>
        </el-form>

        <!-- 批量操作 -->
        <div class="batch-bar" aria-label="批量操作">
          <span class="selection-count" aria-live="polite" aria-atomic="true">已选 {{ selection.length }} 条</span>
          <el-button :loading="batchSaving" :disabled="!selection.length || batchSaving" @click="batch('active')">启用</el-button>
          <el-button :loading="batchSaving" :disabled="!selection.length || batchSaving" @click="batch('inactive')">停用</el-button>
          <el-button :loading="batchSaving" :disabled="!selection.length || batchSaving" @click="batch('lock')">锁定</el-button>
          <el-button :loading="batchSaving" :disabled="!selection.length || batchSaving" @click="batch('unlock')">解锁</el-button>
          <el-button :loading="batchSaving" :disabled="!selection.length || batchSaving" @click="batch('reset')">重置密码</el-button>
          <el-button :loading="batchSaving" :disabled="!selection.length || batchSaving" type="danger" plain @click="batch('delete')">删除</el-button>
        </div>

        <!-- 表格 -->
        <div class="table-region" aria-label="用户列表" :aria-busy="loading">
          <div v-if="loading" class="table-state" role="status" aria-live="polite">用户列表加载中</div>
          <div v-if="listError" class="table-error" role="alert">
            <span>{{ listError }}</span>
            <el-button link type="primary" size="small" :disabled="loading" @click="reload">重试</el-button>
          </div>
          <el-table
            :data="rows"
            size="default"
            v-loading="loading"
            :empty-text="listEmptyText"
            @selection-change="onSelectionChange"
          >
            <template #empty>
              <div v-if="!loading && !listError" class="table-state" role="status">{{ listEmptyText }}</div>
              <div v-else class="table-empty-spacer" aria-hidden="true"></div>
            </template>
            <el-table-column type="selection" width="42" />
            <el-table-column prop="userId" label="用户ID" width="120">
              <template #default="{row}"><code class="mono">{{ row.userId }}</code></template>
            </el-table-column>
            <el-table-column prop="username" label="工号" width="140" />
            <el-table-column prop="userchnname" label="姓名" width="120" />
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
            <el-table-column prop="createTime" label="创建时间" width="160" :formatter="fmtDateTime" />
            <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
            <!-- 部门：后端 EXT_USER_ORG ⋈ EXT_ORG_INFO 联查返回的 ORG_NAME（多机构以「、」连接） -->
            <el-table-column prop="deptName" label="部门" width="150" show-overflow-tooltip />
            <el-table-column prop="remark" label="备注" width="160" show-overflow-tooltip />
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
        </div>

        <div class="pager">
          <el-pagination
            v-model:current-page="pager.pageNo"
            v-model:page-size="pager.pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="pager.total"
            background
            aria-label="用户列表分页"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="reload"
            @current-change="reload"
          />
        </div>
      </section>
    </div>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog v-model="dlg.show" :title="dlg.editing ? '编辑用户' : '新增用户'" width="640px">
      <el-form ref="dlgFormRef" :model="dlg.form" :rules="dlg.rules" label-width="100px">
        <!-- 用户ID：编辑时只读展示，新增时不显示（后端自动生成） -->
        <el-form-item v-if="dlg.editing" label="用户ID">
          <el-input v-model="dlg.form.userId" disabled />
        </el-form-item>
        <el-form-item label="工号" prop="username">
          <el-input v-model="dlg.form.username" placeholder="如 E10001" maxlength="64" />
        </el-form-item>
        <el-form-item label="姓名" prop="userchnname">
          <el-input v-model="dlg.form.userchnname" placeholder="中文姓名" maxlength="64" />
        </el-form-item>
        <el-form-item label="用户类型" prop="userType">
          <el-radio-group v-model="dlg.form.userType">
            <el-radio v-for="o in userTypeOptions" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="dlg.form.email" placeholder="选填" maxlength="128" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="dlg.form.remark" placeholder="选填，最多 256 字" maxlength="256" />
        </el-form-item>
        <el-form-item v-if="!dlg.editing" label="初始密码" prop="initialPassword">
          <el-input v-model="dlg.form.initialPassword" type="password" show-password placeholder="6~64 位，明文提交后端" maxlength="64" />
        </el-form-item>
        <!-- 机构字段只在编辑时显示；新增模式按左侧选中机构自动归属（form.orgCode 在 openCreate 里已塞值） -->
        <el-form-item v-if="dlg.editing" label="机构" prop="orgCode">
          <el-tree-select
            v-model="dlg.form.orgCode"
            :data="enabledOrgTree"
            :props="{ label: 'name', value: 'code', children: 'children' }"
            node-key="code"
            check-strictly
            placeholder="选择机构"
            filterable
            style="width:100%"
          />
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
        :titles="['可选角色', '已分配（选中单选框即为主角色）']"
        :props="{ key: 'roleId', label: 'roleChName' }"
        filterable
        filter-placeholder="按角色名搜索"
        style="margin-top:8px"
      >
        <template #default="{ option }">
          <span class="role-xfer-item">
            <span class="role-xfer-name">
              {{ option.roleChName }}
              <el-tag v-if="option.recordStatus !== 0" type="info" size="small" effect="plain" class="role-status-tag">停用</el-tag>
            </span>
            <el-radio
              v-if="roleDlg.value.includes(option.roleId)"
              :model-value="roleDlg.primaryRoleId"
              :value="option.roleId"
              class="role-xfer-primary"
              @click.stop
              @change="roleDlg.primaryRoleId = option.roleId"
            >主角色</el-radio>
          </span>
        </template>
      </el-transfer>
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

    <!-- 机构维护弹窗 -->
    <el-dialog v-model="orgDlg.show" title="机构维护" width="780px">
      <div class="org-dlg-body">
        <div class="tree-pane">
          <el-button size="small" @click="orgDlgNewRoot">+ 新建根机构</el-button>
          <!-- 维护弹窗展示全部机构（含已禁用），禁用项灰显并标「禁用」 -->
          <el-tree
            :data="orgTree"
            node-key="code"
            :props="{ label: 'name', children: 'children' }"
            default-expand-all
            :highlight-current="true"
            :expand-on-click-node="false"
            @node-click="orgDlgPick"
            empty-text="暂无机构"
            class="org-dlg-tree"
          >
            <template #default="{ data }">
              <span :class="{ 'org-disabled': data.status === 1 }">
                {{ data.name }}
                <el-tag v-if="data.status === 1" size="small" class="tag-info" effect="plain" style="margin-left:6px">禁用</el-tag>
              </span>
            </template>
          </el-tree>
        </div>
        <div class="form-pane">
          <div v-if="!orgDlg.mode" class="hint">点击左侧节点编辑，或上方「+ 新建根机构」</div>
          <el-form v-else label-width="80px" size="default">
            <!-- 编码：界面不再展示编码，新增后端自增 -->
            <el-form-item label="名称">
              <el-input v-model="orgDlg.form.orgName" placeholder="中文名称" maxlength="100" />
            </el-form-item>
            <!-- 机构编号：新增时用户输入，编辑时只读展示 -->
            <el-form-item label="机构编号">
              <el-input v-model="orgDlg.form.deptNo" :disabled="orgDlg.mode === 'edit'" placeholder="如 720199" maxlength="60" />
            </el-form-item>
            <el-form-item label="上级">
              <el-select
                v-model="orgDlg.form.pId"
                filterable clearable
                placeholder="（根节点 / 无上级）"
                style="width:100%"
              >
                <el-option v-for="o in parentOptions" :key="o.code" :label="o.name" :value="o.code" />
              </el-select>
            </el-form-item>
            <!-- 状态：编辑模式可启用/禁用；禁用后用户管理与各处机构树不再展示该机构 -->
            <el-form-item v-if="orgDlg.mode === 'edit'" label="状态">
              <el-tag :class="orgDlg.form.status === 1 ? 'tag-info' : 'tag-success'" effect="plain">
                {{ orgDlg.form.status === 1 ? '已禁用' : '启用中' }}
              </el-tag>
              <el-button v-if="orgDlg.form.status === 1" size="small" type="success" plain
                         :loading="orgDlg.saving" style="margin-left:10px"
                         @click="orgDlgToggleStatus(0)">启用</el-button>
              <el-popconfirm v-else
                title="确认禁用该机构？禁用后用户将看不到它（机构下有用户则不允许禁用）。"
                @confirm="orgDlgToggleStatus(1)">
                <template #reference>
                  <el-button size="small" type="warning" plain :loading="orgDlg.saving" style="margin-left:10px">禁用</el-button>
                </template>
              </el-popconfirm>
            </el-form-item>
            <el-form-item v-if="orgDlg.mode === 'edit'">
              <el-button size="small" @click="orgDlgNewChild">+ 在此下新建子机构</el-button>
              <el-popconfirm
                :title="`确认删除「${orgDlg.form.orgName}」？有下级或用户会被后端拒绝。`"
                @confirm="orgDlgDelete">
                <template #reference>
                  <el-button size="small" type="danger" plain style="margin-left:8px">删除</el-button>
                </template>
              </el-popconfirm>
            </el-form-item>
          </el-form>
        </div>
      </div>
      <template #footer>
        <el-button @click="orgDlg.show = false">关闭</el-button>
        <el-button v-if="orgDlg.mode" type="primary" :loading="orgDlg.saving" @click="orgDlgSave">保存</el-button>
      </template>
    </el-dialog>

  </main>
</template>

<script setup>
import { ref, reactive, watch, onMounted, computed } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Search } from '@element-plus/icons-vue';
import {
  listUsers, getUser, createUser, updateUser,
  deleteUsers, resetUsersPassword, activeUsers, inactiveUsers, lockUsers, unlockUsers,
  getUserRoles, replaceUserRoles, bindUserRoles, exportUsersBlob,
  USER_STATUS_LABEL, USER_LOCK_LABEL
} from '@/api/users';
import { listAllRoles, listDictItems } from '@/api/system';
import { getOrgTree, listOrgUsers, createOrg, updateOrg, deleteOrg } from '@/api/orgs';

// === 机构树 ===
// el-table column formatter: (row, column, cellValue, index) → ISO 字符串去掉 T 截到秒
const fmtDateTime = (_row, _col, v) => v ? String(v).replace('T', ' ').slice(0, 19) : '-';

const orgTreeRef = ref(null);
const orgTree = ref([]);
const orgError = ref('');
// 只保留启用机构（status!==1）供左树 / 用户归属选择；维护弹窗仍用全量 orgTree
const enabledOrgTree = computed(() => {
  const filterEnabled = (nodes) => (nodes || [])
    .filter(n => n.status !== 1)
    .map(n => ({ ...n, children: n.children ? filterEnabled(n.children) : undefined }));
  return filterEnabled(orgTree.value);
});
const orgKeyword = ref('');
const pickedOrg = ref('');     // orgCode（内部查询用，不展示）
const pickedOrgName = ref('');
const pickedOrgDeptNo = ref(''); // 机构编号（界面展示用）
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
  pickedOrgDeptNo.value = node.deptNo || '';
  pager.pageNo = 1;
  reload();
}
function clearOrg() {
  pickedOrg.value = '';
  pickedOrgName.value = '';
  pickedOrgDeptNo.value = '';
  orgTreeRef.value?.setCurrentKey(null);
  pager.pageNo = 1;
  reload();
}

// === 列表 ===
const rows = ref([]);
const loading = ref(false);
const listError = ref('');
const filters = reactive({ username: '', userchnname: '', isEnabled: null, isLocked: null });
const pager = reactive({ pageNo: 1, pageSize: 20, total: 0 });
const selection = ref([]);
const batchSaving = ref(false);
const listEmptyText = computed(() => {
  if (pickedOrg.value) return '当前机构暂无符合筛选条件的用户';
  const hasStatusFilter = value => value !== null && value !== undefined && value !== '';
  if (filters.username?.trim() || filters.userchnname?.trim() || hasStatusFilter(filters.isEnabled) || hasStatusFilter(filters.isLocked)) {
    return '暂无符合筛选条件的用户';
  }
  return '暂无用户数据';
});
// 用户类型字典（USER_TYPE：1-员工 / 2-虚拟员工），编辑/新增用户用单选
const userTypeOptions = ref([]);
async function loadUserTypeDict() {
  try {
    const items = await listDictItems('USER_TYPE');
    userTypeOptions.value = (Array.isArray(items) ? items : [])
      .map(d => ({ value: d.dictCode ?? d.itemCode ?? d.value, label: d.dictLabel ?? d.itemLabel ?? d.label }));
  } catch { userTypeOptions.value = []; }
}

function resetFilters() {
  filters.username = '';
  filters.userchnname = '';
  filters.isEnabled = null;
  filters.isLocked = null;
  pager.pageNo = 1;
  reload();
}
function onSelectionChange(rs) { selection.value = rs; }

// 导出全部用户（含绑定角色）：blob 直接下载，不跳转/不开新标签页；忽略筛选条件
async function exportUsers() {
  try {
    const blob = await exportUsersBlob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = '用户列表.xlsx';
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  } catch (e) {
    ElMessage.error('导出失败：' + (e?.message || '请稍后重试'));
  }
}

async function reload() {
  loading.value = true;
  listError.value = '';
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
      // 机构用户接口用单个 keyword（同时模糊工号/用户名/姓名），把工号、姓名合并传过去；
      // 状态/锁定单独传 isEnabled/isLocked（后端已支持）
      const keyword = filters.userchnname?.trim() || filters.username?.trim() || undefined;
      r = await listOrgUsers(pickedOrg.value, {
        pageNo: pager.pageNo,
        pageSize: pager.pageSize,
        keyword,
        isEnabled: filters.isEnabled ?? undefined,
        isLocked: filters.isLocked ?? undefined
      });
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
      // listOrgUsers 与 listUsers 字段不归一会导致按机构筛选时状态/锁定/创建时间空白，
      // 老接口现已同步补齐这几个字段，这里 fallback 仅作 null 防御
      isEnabled: u.isEnabled ?? null,
      isLocked: u.isLocked ?? null,
      createTime: u.createTime ?? null,
      remark: u.remark ?? '',
      deptName: u.deptName ?? '',
    }));
    // 后端 PageResult 总数（拦截器抽走后只剩 records，需要单独 total 时改用原 wrapper）
    pager.total = r?.total ?? rows.value.length;
  } catch {
    rows.value = [];
    pager.total = 0;
    // 默认 GET 仍可能在 API 层回退为空数据；这里只呈现真正抛到页面的可捕获异常。
    listError.value = '用户列表暂时无法加载，请重试。';
  } finally { loading.value = false; }
}

// === 新增 / 编辑弹窗 ===
const dlgFormRef = ref(null);
const dlg = reactive({
  show: false, editing: null, saving: false,
  form: { userId: '', username: '', userchnname: '', email: '', remark: '', initialPassword: '' },
  rules: {
    username:        [{ required: true, message: '工号必填', trigger: 'blur' }, { max: 64, message: '不超过 64 位', trigger: 'blur' }],
    userchnname:     [{ required: true, message: '姓名必填', trigger: 'blur' }, { max: 64, message: '不超过 64 位', trigger: 'blur' }],
    email:           [{ pattern: /^[^@\s]+@[^@\s]+\.[^@\s]+$/, message: '邮箱格式不正确', trigger: 'blur' }],
    remark:          [{ max: 256, message: '备注不超过 256 字', trigger: 'blur' }],
    userType:        [{ required: true, message: '请选择用户类型', trigger: 'change' }],
    initialPassword: [{ required: true, message: '初始密码必填', trigger: 'blur' }, { min: 6, max: 64, message: '6~64 位', trigger: 'blur' }]
  }
});
function openCreate() {
  // 新增必须先在左侧机构树选中一个机构，否则没法确定归属
  if (!pickedOrg.value) {
    ElMessage.warning('请先在左侧机构树选中一个机构，再新增用户');
    return;
  }
  dlg.editing = null;
  dlg.form = {
    userId: '', username: '', userchnname: '', email: '', remark: '',
    initialPassword: '', orgCode: pickedOrg.value, userType: '1'
  };
  dlg.show = true;
}
async function openEdit(row) {
  dlg.editing = row.userId;
  // 列表 row 不含 orgCode，调单查接口反显当前机构
  let orgCode = row.orgCode || pickedOrg.value || '';
  let userType = row.userType || '';
  try {
    const detail = await getUser(row.userId);
    if (detail?.orgCode) orgCode = detail.orgCode;
    if (detail?.userType != null) userType = detail.userType;
  } catch { /* 单查失败 fallback 现有值 */ }
  dlg.form = {
    userId: row.userId,
    username: row.username,
    userchnname: row.userchnname,
    email: row.email || '',
    remark: row.remark || '',
    initialPassword: '',
    orgCode,
    userType
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
      const payload = {
        username: rest.username, userchnname: rest.userchnname,
        email: rest.email, remark: rest.remark, orgCode: rest.orgCode,
        userType: rest.userType
      };
      await updateUser(userId, payload);
      ElMessage.success('已更新');
    } else {
      // 新增不传 userId，由后端自动生成
      const { userId, ...newForm } = dlg.form;
      await createUser(newForm);
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
  options: [],          // [{roleId, roleChName, ...}]
  value: [],            // 已选 roleId[]
  reason: '',
  primaryRoleId: ''     // 主角色（必为 value 中之一）
});
// 已分配集合变化时维持主角色有效：被解绑则默认取第一个已分配角色
watch(() => roleDlg.value.slice(), (val) => {
  if (!val.length) { roleDlg.primaryRoleId = ''; return; }
  if (!val.includes(roleDlg.primaryRoleId)) roleDlg.primaryRoleId = val[0];
});
async function openAssignRoles(user) {
  roleDlg.user = user;
  roleDlg.value = [];
  roleDlg.reason = '';
  roleDlg.show = true;
  // 并行拉全量角色 + 用户已绑
  try {
    const [allRoles, bound] = await Promise.all([
      listAllRoles(),
      getUserRoles(user.userId)
    ]);
    const roleArr = allRoles?.records || (Array.isArray(allRoles) ? allRoles : []);
    const boundArr = Array.isArray(bound) ? bound : [];
    const boundSet = new Set(boundArr.map(r => r.roleId || r.id));
    const opts = roleArr.map(r => {
      const roleId = r.roleId || r.id;
      const recordStatus = typeof r.recordStatus === 'number' ? r.recordStatus : 0;
      return {
        roleId,
        roleChName: r.roleChName || r.name || roleId,
        recordStatus,
        // 已禁用(recordStatus!==0)且未分配的角色：置灰不可选；已分配的禁用角色保留可解绑
        disabled: recordStatus !== 0 && !boundSet.has(roleId)
      };
    });
    roleDlg.options = opts;
    roleDlg.value = boundArr.map(r => r.roleId || r.id);
    // 回显主角色：后端 primary=true 的角色，缺失则取第一个
    const primary = boundArr.find(r => r.primary);
    roleDlg.primaryRoleId = (primary && (primary.roleId || primary.id)) || roleDlg.value[0] || '';
  } catch {
    roleDlg.options = [];
    roleDlg.value = [];
    roleDlg.primaryRoleId = '';
  }
}
async function saveRoles() {
  if (!roleDlg.reason || !roleDlg.reason.trim()) {
    ElMessage.warning('请填写备注理由（审计必填）');
    return;
  }
  if (roleDlg.value.length && !roleDlg.value.includes(roleDlg.primaryRoleId)) {
    ElMessage.warning('请选择主角色');
    return;
  }
  roleDlg.saving = true;
  try {
    const reason = roleDlg.reason.trim();
    const r = await replaceUserRoles(roleDlg.user.userId, roleDlg.value, reason);
    // 同步主角色：幂等重绑全量角色并携带 primaryRoleId（后端据此切换 DEFAULT_ASSIGN）
    if (roleDlg.value.length && roleDlg.primaryRoleId) {
      await bindUserRoles(roleDlg.user.userId, roleDlg.value, reason, roleDlg.primaryRoleId);
    }
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
  if (batchSaving.value) return;
  const ids = overrideIds || selection.value.map(r => r.userId);
  if (!ids.length) return ElMessage.warning('请先勾选用户');
  const d = BATCH_DISPATCH[action];
  if (!d) return;
  batchSaving.value = true;
  try {
    if (d.confirm) {
      try { await ElMessageBox.confirm(d.confirm, '确认', { type: 'warning' }); } catch { return; }
    }
    await d.fn(ids);
    ElMessage.success(d.msg);
    await reload();
  } catch (e) {
    ElMessage.error('操作失败：' + (e?.message || e));
  } finally { batchSaving.value = false; }
}

// === 启动 ===
async function loadOrg() {
  orgError.value = '';
  try {
    const t = await getOrgTree();
    orgTree.value = Array.isArray(t) ? t : [];
  } catch {
    orgTree.value = [];
    // 沿用默认 GET fallback；仅当调用确实抛错时才给出可恢复的上下文提示。
    orgError.value = '机构树暂时无法加载，请重试。';
  }
}
// === 机构维护弹窗 ===
const orgDlg = reactive({
  show: false, saving: false,
  mode: null,       // 'create' | 'edit' | null
  picked: null,     // 当前选中的树节点 { code, name }
  form: { orgCode: '', orgName: '', pId: '', deptNo: '' },
  parentLabel: ''
});

// 机构树扁平化为 [{code,name}]，供「上级」模糊搜索下拉用
const flatOrgs = computed(() => {
  const out = [];
  const walk = (nodes) => {
    for (const n of nodes || []) {
      out.push({ code: n.code, name: n.name });
      if (n.children?.length) walk(n.children);
    }
  };
  walk(orgTree.value);
  return out;
});
// 子机构编码 → 父机构编码 映射（编辑时回填当前上级）
const orgParentMap = computed(() => {
  const map = {};
  const walk = (nodes, parentCode) => {
    for (const n of nodes || []) {
      map[n.code] = parentCode || '';
      if (n.children?.length) walk(n.children, n.code);
    }
  };
  walk(orgTree.value, '');
  return map;
});
// 上级候选：全部机构，排除自身（编辑时不能把自己设为自己的上级；成环由后端再校验）
const parentOptions = computed(() =>
  flatOrgs.value.filter(o => o.code !== orgDlg.form.orgCode));
function openOrgDlg() {
  orgDlg.show = true;
  orgDlg.mode = null;
  orgDlg.picked = null;
}
function orgDlgPick(node) {
  orgDlg.mode = 'edit';
  orgDlg.picked = node;
  // pId 回填当前上级编码（根节点为空），供「上级」下拉默认选中
  orgDlg.form = {
    orgCode: node.code,
    orgName: node.name,
    pId: orgParentMap.value[node.code] || '',
    deptNo: node.deptNo || '',
    status: node.status ?? 0
  };
  orgDlg.parentLabel = '当前节点';
}
// 启用(0)/禁用(1)机构：禁用时若机构下有用户，后端返回 AUTH-40303，前端提示
async function orgDlgToggleStatus(targetStatus) {
  if (!orgDlg.picked) return;
  orgDlg.saving = true;
  try {
    await updateOrg(orgDlg.picked.code, { organState: targetStatus });
    ElMessage.success(targetStatus === 1 ? '已禁用' : '已启用');
    orgDlg.form.status = targetStatus;
    await loadOrg();
    // 禁用/启用后刷新左侧列表（禁用的机构会从左树消失）
    if (pickedOrg.value === orgDlg.picked.code && targetStatus === 1) clearOrg();
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || '操作失败');
  } finally { orgDlg.saving = false; }
}
function orgDlgNewRoot() {
  orgDlg.mode = 'create';
  orgDlg.picked = null;
  orgDlg.form = { orgCode: '', orgName: '', pId: '', deptNo: '' };
  orgDlg.parentLabel = '（根节点）';
}
function orgDlgNewChild() {
  if (!orgDlg.picked) { ElMessage.warning('请先选中一个父节点'); return; }
  const parent = orgDlg.picked;
  orgDlg.mode = 'create';
  orgDlg.form = { orgCode: '', orgName: '', pId: parent.code, deptNo: '' };
  orgDlg.parentLabel = `${parent.name}（${parent.deptNo || '无编号'}）`;
}
async function orgDlgSave() {
  if (!orgDlg.form.orgName?.trim()) { ElMessage.warning('请填机构名称'); return; }
  orgDlg.saving = true;
  try {
    if (orgDlg.mode === 'create') {
      // 编码不再前端填写，后端自增；机构编号 deptNo 由用户输入
      await createOrg({
        orgName: orgDlg.form.orgName.trim(),
        pId: orgDlg.form.pId || '',
        deptNo: orgDlg.form.deptNo?.trim() || ''
      });
      ElMessage.success('已新增');
    } else {
      // 上级可改：pId 为空表示设为根节点；后端校验不能选自身/子孙(成环)
      await updateOrg(orgDlg.picked.code, {
        orgName: orgDlg.form.orgName.trim(),
        pId: orgDlg.form.pId || ''
      });
      ElMessage.success('已更新');
    }
    await loadOrg();
    orgDlg.mode = null;
    orgDlg.picked = null;
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存失败');
  } finally { orgDlg.saving = false; }
}
async function orgDlgDelete() {
  if (!orgDlg.picked) return;
  try {
    await deleteOrg(orgDlg.picked.code);
    ElMessage.success('已删除');
    await loadOrg();
    orgDlg.mode = null;
    orgDlg.picked = null;
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '删除失败');
  }
}

onMounted(async () => {
  await Promise.all([loadOrg(), reload(), loadUserTypeDict()]);
});
</script>

<style lang="scss" scoped>
.users-page {
  max-width: var(--layout-content-max-width);
  margin: 0 auto;
}
.users-page-head {
  align-items: flex-start;
  margin-bottom: var(--space-4);
}
.page-heading { min-width: 0; }
.page-h h1 .sub {
  margin-left: var(--space-3);
  color: var(--color-text-muted);
  font-size: 12px;
  font-weight: 400;
}
.page-desc {
  margin-top: var(--space-1);
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 18px;
}
.actions { align-items: center; }
.layout {
  display: grid;
  grid-template-columns: minmax(264px, 304px) minmax(0, 1fr);
  align-items: start;
  gap: var(--space-3);
}
.tree-col,
.detail-col {
  min-width: 0;
  margin-bottom: 0;
  border-color: var(--color-border);
  box-shadow: var(--shadow-surface);
}
.tree-col {
  padding: var(--space-4);
  max-height: calc(100vh - 200px);
  overflow: auto;
}
// 让 el-tree 节点不折行，机构名/编码超长时容器出横向滚动条
.tree-col :deep(.el-tree) {
  display: inline-block;
  min-width: 100%;
}
.tree-col :deep(.el-tree-node__content) {
  white-space: nowrap;
}
.tree-search { margin-bottom: var(--space-3); }
.tree-error,
.table-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  background: var(--color-danger-bg);
  color: var(--color-danger-fg);
  font-size: 12px;
  line-height: 18px;
}
.org-dlg-body { display: flex; gap: var(--space-4); height: 460px; }
.org-dlg-body .tree-pane {
  width: 320px;
  padding-right: var(--space-3);
  overflow: auto;
  border-right: 1px solid var(--color-border);
}
.org-dlg-body .form-pane { flex: 1; overflow: auto; }
.org-dlg-tree { margin-top: var(--space-3); }
.org-disabled { color: var(--color-text-muted); text-decoration: line-through; }
.card-h-mini {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-3);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--color-border);
}
.card-h-mini h2,
.detail-head h2 {
  margin: 0;
  color: var(--color-text-strong);
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
}
.detail-col {
  padding: var(--space-4) var(--space-6);
  max-height: calc(100vh - 200px);
  overflow: auto;
}
.detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-3);
}
.detail-head p,
.detail-count {
  margin: var(--space-1) 0 0;
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 18px;
}
.detail-count { flex-shrink: 0; font-variant-numeric: tabular-nums; }
.filter-form {
  margin-bottom: var(--space-3);
  padding: var(--space-3) var(--space-4) 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  background: var(--color-surface-soft);
}
.filter-form :deep(.filter-control--text) { width: 180px; }
.filter-form :deep(.filter-control--state) { width: 120px; }
.batch-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  background: var(--color-surface-soft);
}
.selection-count {
  min-width: 76px;
  color: var(--color-text);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.hint { color: var(--color-text-muted); font-size: 12px; }
.table-region {
  position: relative;
  min-height: 360px;
}
.table-region :deep(.el-table) { min-height: 360px; }
.table-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 48px;
  padding: var(--space-3);
  color: var(--color-text-muted);
  font-size: 14px;
  line-height: 22px;
  text-align: center;
}
.table-empty-spacer { min-height: 96px; }
.pager { display: flex; justify-content: flex-end; margin-top: var(--space-4); }
/* 用户多→页码按钮多时，分页整行会超出容器宽度，右对齐导致最左"共X条"被挤出视区。
   让 el-pagination 内部允许换行，保证 total/sizes 始终可见 */
.pager :deep(.el-pagination) { flex-wrap: wrap; justify-content: flex-end; row-gap: var(--space-2); }
.mono { font-family: ui-monospace, monospace; font-size: 12px; }
.role-dlg-tip { color: var(--color-text-muted); font-size: 12px; }
.role-xfer-item { display: flex; align-items: center; justify-content: space-between; width: 100%; }
.role-xfer-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.role-status-tag { margin-left: var(--space-2); }
.role-xfer-primary { flex-shrink: 0; margin-left: var(--space-2); }
/* 「已分配」面板(右侧最后一个)加宽 90px：默认 200px → 290px */
:deep(.el-transfer-panel:last-child) { width: 290px; }
</style>
