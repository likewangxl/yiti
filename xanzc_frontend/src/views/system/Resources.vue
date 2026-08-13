<template>
<main v-bp-overflow-tooltip class="bp-crud resources-page" aria-labelledby="resources-page-title">
    <header class="page-h">
      <PageTitle id="resources-page-title">
        <span class="sub">维护菜单层级；叶子菜单可单独配置角色访问范围。</span>
      </PageTitle>
      <div class="actions action-group" role="group" aria-label="资源管理操作">
        <el-button :icon="Refresh" @click="reload">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate(null)">新增一级菜单</el-button>
      </div>
    </header>

    <section class="card-section filter-bar" aria-label="菜单筛选">
      <el-form class="filter-form" inline size="default" aria-label="菜单筛选条件" @submit.prevent="reload">
        <el-form-item label="关键字">
          <el-input
            v-model="keyword"
            :prefix-icon="Search"
            clearable
            placeholder="菜单名称 / 路由路径"
            aria-label="按菜单名称或路由路径筛选"
            style="width:280px"
            @keyup.enter="reload"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="reload">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section
      class="card-section data-panel menu-card"
      aria-label="菜单资源树"
      aria-labelledby="resources-table-heading"
      aria-describedby="resources-table-state"
      :aria-busy="loading ? 'true' : 'false'"
    >
      <div class="toolbar">
        <div>
          <h2 id="resources-table-heading" class="section-title">菜单资源树</h2>
          <p class="hint">目录用于分组；叶子菜单必须关联已注册的前端路由。</p>
        </div>
        <p id="resources-table-state" class="table-state" role="status" aria-live="polite">
          {{ loading ? '菜单资源加载中' : filteredTreeData.length ? `显示 ${filteredTreeData.length} 个一级节点` : '暂无菜单资源' }}
        </p>
      </div>
      <p v-if="loadError" class="error-state" role="alert">
        {{ loadError }} <el-button link type="primary" @click="reload">重试</el-button>
      </p>

      <el-table
        :data="filteredTreeData"
        row-key="resourceId"
        :tree-props="{ children: 'children' }"
        default-expand-all
        v-loading="loading"
        empty-text="暂无菜单资源"
        class="menu-table"
        size="default"
        :indent="22"
        aria-labelledby="resources-table-heading"
        aria-describedby="resources-table-state"
      >
        <el-table-column label="菜单名称" min-width="240">
          <template #default="{ row }">
            <span class="menu-name" :class="{ 'is-group': row.menuEndFlag !== '1' }">
              {{ row.menuName }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="路由路径" min-width="250">
          <template #default="{ row }">
            <code v-if="row.resourceUrl" class="path-chip">{{ row.resourceUrl }}</code>
            <span v-else class="hint">目录节点</span>
          </template>
        </el-table-column>

        <el-table-column label="形态" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              size="small"
              effect="plain"
              :class="row.menuEndFlag === '1' ? 'tag-info' : 'tag-success'"
            >
              {{ row.menuEndFlag === '1' ? '叶子菜单' : '分组目录' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="排序" width="90" align="center">
          <template #default="{ row }"><span class="rank-num">{{ row.menuRankNo ?? 0 }}</span></template>
        </el-table-column>

        <el-table-column label="操作" class-name="operation-cell" width="300" align="right" fixed="right">
          <template #default="{ row }">
            <div class="row-actions" role="group" :aria-label="`${row.menuName} 操作`">
              <el-button link type="primary" size="small" @click.stop="openEdit(row)">编辑</el-button>
              <el-dropdown trigger="click" popper-class="bp-crud-menu">
                <el-button link size="small" aria-label="更多菜单操作">更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click.stop="openCreate(row)">新增子菜单</el-dropdown-item>
                    <el-dropdown-item v-if="row.menuEndFlag === '1'" @click.stop="openAssign(row)">分配角色</el-dropdown-item>
                    <el-dropdown-item divided class="danger-item" :disabled="isDeleting(row.resourceId)" @click.stop="confirmDelete(row)">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog
      v-model="dlg.show"
      class="bp-crud-dialog"
      :title="dlg.editing ? '编辑菜单' : (dlg.parent ? `在「${dlg.parent.menuName}」下新增子菜单` : '新增一级菜单')"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form ref="dlgFormRef" :model="dlg.form" :rules="dlg.rules" label-width="90px" label-position="right">
        <el-form-item label="名称" prop="menuName">
          <el-input v-model="dlg.form.menuName" placeholder="如：客户管理" maxlength="64" />
        </el-form-item>
        <el-form-item label="路由路径" prop="resourceUrl" :required="dlg.form.menuEndFlag === '1'">
          <el-select
            v-model="dlg.form.resourceUrl"
            :placeholder="dlg.form.menuEndFlag === '1' ? '叶子菜单必须选择已注册路由' : '目录节点可不选择路由'"
            filterable
            clearable
            style="width:100%"
          >
            <el-option v-for="route in routeOptions" :key="route.value" :value="route.value" :label="route.label">
              <span>{{ route.label }}</span><span class="route-mono">{{ route.value }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="节点形态" prop="menuEndFlag">
          <el-radio-group v-model="dlg.form.menuEndFlag">
            <el-radio v-for="option in END_FLAG_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序号" prop="menuRankNo">
          <el-input-number v-model="dlg.form.menuRankNo" :min="0" :step="1" />
          <span class="hint inline-hint">数字越小越靠前</span>
        </el-form-item>
        <el-form-item label="隐藏菜单">
          <el-switch v-model="dlg.form.status" :active-value="1" :inactive-value="0" />
          <span class="hint inline-hint">开启后所有用户不可见</span>
        </el-form-item>
        <el-form-item label="父节点">
          <span v-if="dlg.editing" class="hint">编辑模式不可变更父节点</span>
          <span v-else-if="dlg.parent" class="hint">{{ dlg.parent.menuName }} <code class="mono">({{ dlg.parent.resourceId }})</code></span>
          <span v-else class="hint">无（作为一级菜单）</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="dlg.saving" @click="dlg.show = false">取消</el-button>
        <el-button type="primary" :loading="dlg.saving" :disabled="dlg.saving" @click="saveDlg">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="assign.show"
      class="bp-crud-dialog"
      :title="`分配角色 · ${assign.menuName}`"
      width="560px"
      :close-on-click-modal="false"
    >
      <p class="assign-tip">保存后立即替换该叶子菜单已绑定的角色范围，并记录权限变更审计。</p>
      <el-select
        v-model="assign.roleIds"
        multiple
        filterable
        clearable
        placeholder="选择角色（默认显示已绑定角色）"
        style="width:100%"
        v-loading="assign.loading"
        aria-label="选择可访问该菜单的角色"
      >
        <el-option
          v-for="role in roleOptions"
          :key="role.roleId"
          :value="role.roleId"
          :label="role.roleChName || role.roleCode || role.roleId"
        >
          <span>{{ role.roleChName || role.roleCode || role.roleId }}</span><span class="route-mono">{{ role.roleCode }}</span>
        </el-option>
      </el-select>
      <template #footer>
        <el-button :disabled="assign.saving" @click="assign.show = false">取消</el-button>
        <el-button type="primary" :loading="assign.saving" :disabled="assign.saving" @click="saveAssign">保存</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus, Refresh, Search } from '@element-plus/icons-vue';
import {
  listResourceTree, createResource, updateResource, deleteResource,
  getResourceRoles, assignResourceRoles,
  END_FLAG_OPTIONS
} from '@/api/resources';
import { listAllRoles } from '@/api/system';
import { useMenuStore } from '@/stores/menu';

const router = useRouter();
const menuStore = useMenuStore();

const routeOptions = computed(() => {
  const result = [];
  const layout = router.options.routes.find(route => route.path === '/' && route.children?.length);
  for (const route of layout?.children || []) {
    if (!route.path || !route.meta?.title || route.meta?.hidden) continue;
    result.push({ value: '/' + route.path, label: route.meta.title });
  }
  return result.sort((a, b) => a.value.localeCompare(b.value));
});

const rawTree = ref([]);
const loading = ref(false);
const loadError = ref('');
const keyword = ref('');
const tableState = computed(() => {
  if (loading.value) return '菜单资源加载中';
  if (!filteredTreeData.value.length) return '暂无菜单资源';
  return `显示 ${filteredTreeData.value.length} 个一级节点`;
});

async function reload() {
  loading.value = true;
  loadError.value = '';
  try {
    const tree = await listResourceTree({});
    rawTree.value = Array.isArray(tree) ? tree : [];
  } catch (error) {
    rawTree.value = [];
    loadError.value = `菜单资源加载失败：${error?.message || '请稍后重试'}`;
  } finally {
    loading.value = false;
  }
}

function resetFilters() {
  keyword.value = '';
  reload();
}

function pruneMenusOnly(nodes) {
  const walk = (node) => {
    const children = (node.children || []).map(walk).filter(Boolean);
    return node.isMenu === 1 ? { ...node, children } : null;
  };
  return (nodes || []).map(walk).filter(Boolean);
}
const treeData = computed(() => pruneMenusOnly(rawTree.value));
const filteredTreeData = computed(() => {
  const query = String(keyword.value || '').toLowerCase().trim();
  if (!query) return treeData.value;
  const walk = (node) => {
    const children = (node.children || []).map(walk).filter(Boolean);
    const selfMatch = String(node.menuName || '').toLowerCase().includes(query)
      || String(node.resourceUrl || '').toLowerCase().includes(query);
    return selfMatch || children.length ? { ...node, children } : null;
  };
  return treeData.value.map(walk).filter(Boolean);
});

const dlgFormRef = ref(null);
const dlg = reactive({
  show: false, editing: null, parent: null, saving: false,
  form: {
    menuName: '', resourceUrl: '', resourceMethod: 'MENU',
    isMenu: 1, menuEndFlag: '1', menuRankNo: 0, sysCode: ''
  },
  rules: {
    menuName: [{ required: true, message: '菜单名必填', trigger: 'blur' }, { max: 64, message: '不超过 64 位', trigger: 'blur' }],
    resourceUrl: [{
      validator: (_rule, value, callback) => (dlg.form.menuEndFlag === '1' && !value
        ? callback(new Error('叶子菜单必须选择路由路径')) : callback()),
      trigger: 'change'
    }],
    menuEndFlag: [{ required: true, message: '请选择节点形态', trigger: 'change' }]
  }
});

function openCreate(parent) {
  dlg.editing = null;
  dlg.parent = parent || null;
  dlg.form = {
    menuName: '', resourceUrl: '', resourceMethod: 'MENU', isMenu: 1,
    menuEndFlag: parent ? '1' : '0', menuRankNo: 0, sysCode: parent?.sysCode || ''
  };
  dlg.show = true;
}

function openEdit(row) {
  dlg.editing = row.resourceId;
  dlg.parent = null;
  dlg.form = {
    menuName: row.menuName,
    resourceUrl: row.resourceUrl,
    resourceMethod: row.resourceMethod || 'MENU',
    isMenu: 1,
    menuEndFlag: row.menuEndFlag || '1',
    menuRankNo: row.menuRankNo ?? 0,
    status: row.status ?? 0,
    sysCode: row.sysCode || ''
  };
  dlg.show = true;
}

async function saveDlg() {
  if (dlg.saving) return;
  try {
    await dlgFormRef.value?.validate();
  } catch {
    return;
  }
  dlg.saving = true;
  try {
    // 严格保持后端 DTO 白名单：sysCode 仅用于前端继承，不得作为写入字段。
    const { sysCode, ...rest } = dlg.form;
    if (dlg.editing) {
      await updateResource(dlg.editing, rest);
      ElMessage.success('菜单已更新');
    } else {
      // 创建 DTO 不接收 status，避免严格反序列化时把“隐藏菜单”误传给后端。
      const { status, ...payload } = rest;
      if (dlg.parent) payload.parentResourceId = dlg.parent.resourceId;
      await createResource(payload);
      ElMessage.success('菜单已创建');
    }
    menuStore.load(true);
    dlg.show = false;
    await reload();
  } catch (error) {
    ElMessage.error(`${dlg.editing ? '更新' : '创建'}失败：${error?.message || error}`);
  } finally {
    dlg.saving = false;
  }
}

const deletingIds = ref(new Set());
const isDeleting = (id) => deletingIds.value.has(String(id));
function setDeleting(id, value) {
  const next = new Set(deletingIds.value);
  if (value) next.add(String(id));
  else next.delete(String(id));
  deletingIds.value = next;
}

async function doDelete(row) {
  const id = row?.resourceId;
  if (id == null || isDeleting(id)) return;
  setDeleting(id, true);
  try {
    await deleteResource(id, '前端删除');
    ElMessage.success('菜单已删除');
    menuStore.load(true);
    await reload();
  } catch (error) {
    ElMessage.error(`删除失败：${error?.message || error}`);
  } finally {
    setDeleting(id, false);
  }
}

async function confirmDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.menuName}」？子菜单会一并失效，操作不可逆。`, '删除确认', {
      type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消'
    });
  } catch { return; }
  await doDelete(row);
}

const rawRoles = ref([]);
const roleOptions = computed(() => rawRoles.value.filter(role => Number(role.recordStatus) === 0));
const assign = reactive({ show: false, saving: false, loading: false, resourceId: '', menuName: '', roleIds: [] });

async function openAssign(row) {
  assign.resourceId = row.resourceId;
  assign.menuName = row.menuName;
  assign.roleIds = [];
  assign.show = true;
  assign.loading = true;
  try {
    const [roles, bound] = await Promise.all([
      rawRoles.value.length ? Promise.resolve(rawRoles.value) : listAllRoles({}),
      getResourceRoles(row.resourceId)
    ]);
    rawRoles.value = Array.isArray(roles) ? roles : (roles?.records || []);
    assign.roleIds = (bound || []).map(String);
  } catch (error) {
    ElMessage.error(`角色加载失败：${error?.message || error}`);
  } finally {
    assign.loading = false;
  }
}

async function saveAssign() {
  if (assign.saving || !assign.resourceId) return;
  assign.saving = true;
  try {
    await assignResourceRoles(assign.resourceId, assign.roleIds, '菜单分配角色');
    ElMessage.success('角色范围已保存');
    assign.show = false;
  } catch (error) {
    ElMessage.error(`保存失败：${error?.message || error}`);
  } finally {
    assign.saving = false;
  }
}

onMounted(reload);
</script>

<style lang="scss" scoped>
.resources-page { min-width: 0; }
.menu-card { min-width: 0; }
.menu-table :deep(.el-table__row:hover > td) { background: var(--color-brand-100); }
.menu-table :deep(.el-table__placeholder) { width: var(--space-4); }
.menu-name { color: var(--color-text-strong); font-size: 14px; }
.menu-name.is-group { font-weight: 600; }
.path-chip,
.mono,
.route-mono {
  font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace;
}
.path-chip {
  background: var(--color-surface-soft);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  color: var(--color-text);
  display: inline-block;
  font-size: 12px;
  padding: 2px var(--space-2);
}
.rank-num {
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}
.row-actions { display: flex; gap: var(--space-1); justify-content: flex-end; }
.route-mono { color: var(--color-text-muted); float: right; font-size: 12px; }
.inline-hint { margin-left: var(--space-2); }
.assign-tip,
.error-state {
  color: var(--color-text);
  font-size: 12px;
  line-height: 18px;
}
.assign-tip { margin-bottom: var(--space-3); }
.error-state {
  background: var(--color-danger-bg);
  border-left: 3px solid var(--color-danger-fg);
  color: var(--color-danger-fg);
  margin: 0 0 var(--space-3);
  padding: var(--space-2) var(--space-3);
}
</style>
