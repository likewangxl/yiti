<template>
  <div>
    <div class="page-h">
      <h1>菜单管理 <span class="sub">PT_RESOURCE.IS_MENU=1 的菜单节点；接口资源在「资源管理」单独维护</span></h1>
      <div class="actions">
        <el-input
          v-model="keyword"
          placeholder="按菜单名 / 路径 模糊搜索"
          :prefix-icon="Search"
          clearable
          size="default"
          class="search-box"
        />
        <el-button @click="reload" :icon="Refresh">刷新</el-button>
        <el-button type="primary" @click="openCreate(null)" :icon="Plus">新增一级菜单</el-button>
      </div>
    </div>

    <div class="card-section menu-card">
      <el-table
        :data="filteredTreeData"
        row-key="resourceId"
        :tree-props="{ children: 'children' }"
        default-expand-all
        v-loading="loading"
        empty-text="暂无菜单"
        class="menu-table"
        size="default"
        :indent="22"
      >
        <el-table-column label="菜单名称" min-width="240">
          <template #default="{ row }">
            <span class="menu-name" :class="{ 'is-group': row.menuEndFlag !== '1' }">
              {{ row.menuName }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="路由路径" min-width="220">
          <template #default="{ row }">
            <code class="path-chip">{{ row.resourceUrl }}</code>
          </template>
        </el-table-column>

        <el-table-column label="形态" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              size="small"
              effect="plain"
              :class="row.menuEndFlag === '1' ? 'tag-info' : 'tag-success'"
            >
              {{ row.menuEndFlag === '1' ? '叶子' : '分组' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="排序" width="80" align="center">
          <template #default="{ row }">
            <span class="rank-num">{{ row.menuRankNo ?? 0 }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="220" align="right" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="openCreate(row)">+ 子菜单</el-button>
            <el-button link type="primary" size="small" @click.stop="openEdit(row)">编辑</el-button>
            <el-popconfirm
              :title="`确认删除「${row.menuName}」？子菜单会一并失效，操作不可逆。`"
              @confirm="doDelete(row)"
            >
              <template #reference>
                <el-button link type="danger" size="small" @click.stop>删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog
      v-model="dlg.show"
      :title="dlg.editing ? '编辑菜单' : (dlg.parent ? `在「${dlg.parent.menuName}」下新增子菜单` : '新增一级菜单')"
      width="600px"
    >
      <el-form ref="dlgFormRef" :model="dlg.form" :rules="dlg.rules" label-width="90px" label-position="right">
        <el-form-item label="名称" prop="menuName">
          <el-input v-model="dlg.form.menuName" placeholder="如：客户管理" maxlength="64" />
        </el-form-item>

        <el-form-item label="路由路径" prop="resourceUrl">
          <el-select
            v-model="dlg.form.resourceUrl"
            placeholder="从已注册前端路由中选择"
            filterable
            style="width:100%"
          >
            <el-option
              v-for="r in routeOptions"
              :key="r.value"
              :value="r.value"
              :label="r.label"
            >
              <span>{{ r.label }}</span>
              <span class="route-mono">{{ r.value }}</span>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="节点形态" prop="menuEndFlag">
          <el-radio-group v-model="dlg.form.menuEndFlag">
            <el-radio v-for="o in END_FLAG_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="排序号" prop="menuRankNo">
          <el-input-number v-model="dlg.form.menuRankNo" :min="0" :step="1" />
          <span class="hint">数字越小越靠前</span>
        </el-form-item>

        <el-form-item label="隐藏菜单">
          <el-switch v-model="dlg.form.status" :active-value="1" :inactive-value="0" />
          <span class="hint" style="margin-left:8px">开启后所有用户不可见</span>
        </el-form-item>

        <el-form-item label="父节点">
          <span v-if="dlg.editing" class="hint">编辑模式不可改父节点</span>
          <span v-else-if="dlg.parent" class="hint">
            {{ dlg.parent.menuName }} <code class="mono">({{ dlg.parent.resourceId }})</code>
          </span>
          <span v-else class="hint">无（作为一级菜单）</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg.show = false">取消</el-button>
        <el-button type="primary" :loading="dlg.saving" @click="saveDlg">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Search, Refresh, Plus } from '@element-plus/icons-vue';
import {
  listResourceTree, createResource, updateResource, deleteResource,
  END_FLAG_OPTIONS
} from '@/api/resources';

const router = useRouter();
// 菜单 URL 强制下拉选 — 选项来自 router 已注册路由扁平化
// 业务约束：新建菜单只能指向真存在的前端页面，避免点进去空白
const routeOptions = computed(() => {
  const result = [];
  const layout = router.options.routes.find(r => r.path === '/' && r.children?.length);
  for (const r of layout?.children || []) {
    if (!r.path || !r.meta?.title) continue;
    if (r.meta?.hidden) continue;
    result.push({ value: '/' + r.path, label: r.meta.title });
  }
  return result.sort((a, b) => a.value.localeCompare(b.value));
});

// === 数据 ===
const rawTree = ref([]);
const loading = ref(false);
const keyword = ref('');

async function reload() {
  loading.value = true;
  try {
    rawTree.value = await listResourceTree({});
  } catch { rawTree.value = []; }
  finally { loading.value = false; }
}

// 菜单管理只展示 IS_MENU=1 节点；接口资源（IS_MENU=0）剪掉
function pruneMenusOnly(nodes) {
  const walk = (n) => {
    const children = (n.children || []).map(walk).filter(Boolean);
    if (n.isMenu === 1) return { ...n, children };
    return null;
  };
  return nodes.map(walk).filter(Boolean);
}
const treeData = computed(() => pruneMenusOnly(rawTree.value));

// 关键字过滤：递归剪枝，命中节点 + 命中节点的祖先链保留
const filteredTreeData = computed(() => {
  const q = String(keyword.value || '').toLowerCase().trim();
  if (!q) return treeData.value;
  const walk = (n) => {
    const childMatches = (n.children || []).map(walk).filter(Boolean);
    const selfMatch =
      String(n.menuName || '').toLowerCase().includes(q) ||
      String(n.resourceUrl || '').toLowerCase().includes(q);
    if (selfMatch || childMatches.length) return { ...n, children: childMatches };
    return null;
  };
  return treeData.value.map(walk).filter(Boolean);
});

// === 新增 / 编辑 ===
const dlgFormRef = ref(null);
const dlg = reactive({
  show: false, editing: null, parent: null, saving: false,
  form: {
    menuName: '', resourceUrl: '', resourceMethod: 'MENU',
    isMenu: 1, menuEndFlag: '1', menuRankNo: 0, sysCode: ''
  },
  rules: {
    menuName:    [{ required: true, message: '菜单名必填', trigger: 'blur' }, { max: 64, message: '不超过 64 位', trigger: 'blur' }],
    resourceUrl: [{ required: true, message: '路由路径必填', trigger: 'change' }],
    menuEndFlag: [{ required: true, message: '请选择节点形态', trigger: 'change' }]
  }
});
function openCreate(parent) {
  dlg.editing = null;
  dlg.parent = parent || null;
  dlg.form = {
    menuName: '', resourceUrl: '',
    resourceMethod: 'MENU',
    isMenu: 1,
    menuEndFlag: parent ? '1' : '0',
    menuRankNo: 0,
    sysCode: parent?.sysCode || ''
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
  try { await dlgFormRef.value?.validate(); } catch { return; }
  dlg.saving = true;
  try {
    // 后端 ResourceUpdateReqDTO 严格反序列化，剥离 sysCode（DB 有但 DTO 无）
    // eslint-disable-next-line no-unused-vars
    const { sysCode, ...rest } = dlg.form;
    if (dlg.editing) {
      await updateResource(dlg.editing, rest);
      ElMessage.success('已更新');
    } else {
      const payload = { ...rest };
      if (dlg.parent) payload.parentResourceId = dlg.parent.resourceId;
      await createResource(payload);
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
    await deleteResource(row.resourceId, '前端删除');
    ElMessage.success('已删除');
    await reload();
  } catch (e) {
    ElMessage.error('删除失败：' + (e?.message || e));
  }
}

onMounted(reload);
</script>

<style lang="scss" scoped>
.page-h h1 .sub { font-size: 13px; color: $text-3; margin-left: 12px; font-weight: 400; }
.page-h .actions { display: flex; align-items: center; gap: 10px; }
.search-box { width: 260px; }

.menu-card { padding: 4px 8px 8px; }

// 表格层级缩进留出更宽，菜单名带组样式，路径走 mono chip
.menu-table {
  :deep(.el-table__row) td { padding: 10px 0; }
  :deep(.el-table__row:hover > td) { background: $bg-soft; }
  :deep(.el-table__placeholder) { width: 16px; }
}

.menu-name {
  color: $text-1;
  font-size: 13px;
  &.is-group { font-weight: 600; color: $text-1; }
}

.path-chip {
  display: inline-block;
  padding: 2px 8px;
  font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace;
  font-size: 12px;
  color: $text-2;
  background: $bg-soft;
  border-radius: 3px;
  border: 1px solid $border-1;
}

.rank-num {
  display: inline-block;
  min-width: 28px;
  padding: 0 6px;
  height: 20px; line-height: 20px;
  font-size: 12px;
  color: $text-3;
  background: $bg-soft;
  border-radius: 10px;
}

.route-mono {
  margin-left: 8px;
  color: #9CA3AF;
  font-size: 11px;
  font-family: ui-monospace, monospace;
}

.hint { color: $text-3; font-size: 12px; margin-left: 8px; }
.mono { font-family: ui-monospace, monospace; font-size: 12px; }
</style>
