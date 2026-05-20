<template>
  <div>
    <div class="page-h">
      <h1>资源/菜单管理 <span class="sub">PT_RESOURCE 统一表 · 通过 isMenu 区分菜单与接口</span></h1>
      <div class="actions">
        <el-button @click="reload">刷新</el-button>
        <el-button type="primary" @click="openCreate(null)">+ 新增根节点</el-button>
      </div>
    </div>

    <div class="card-section">
      <el-form inline size="default">
        <el-form-item label="搜索">
          <el-input
            v-model="keyword"
            placeholder="菜单名 / URL / ID"
            clearable
            :prefix-icon="Search"
            style="width:240px"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="kindFilter" clearable placeholder="全部" style="width:140px">
            <el-option :value="0" label="菜单" />
            <el-option :value="1" label="接口资源" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="statusFilter" clearable placeholder="全部" style="width:140px">
            <el-option :value="0" label="启用" />
            <el-option :value="1" label="停用" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card-section">
      <el-tree
        ref="treeRef"
        :data="treeData"
        node-key="resourceId"
        :props="{ label: 'menuName', children: 'children' }"
        :default-expand-all="true"
        :expand-on-click-node="false"
        :filter-node-method="filterNode"
        empty-text="暂无资源"
        v-loading="loading"
      >
        <template #default="{ node, data }">
          <div class="res-row">
            <span class="ico">{{ data.isMenu === 0 ? '📁' : '🔌' }}</span>
            <span class="name">{{ data.menuName }}</span>
            <code class="mono url">{{ data.resourceUrl }}</code>
            <el-tag :class="methodCls(data.resourceMethod)" effect="plain" size="small" class="meth">
              {{ data.resourceMethod || '-' }}
            </el-tag>
            <el-tag :class="data.status === 0 ? 'tag-success' : 'tag-warning'" effect="plain" size="small">
              {{ data.status === 0 ? '启用' : '停用' }}
            </el-tag>
            <span class="rank">#{{ data.menuRankNo ?? 0 }}</span>
            <span class="row-actions">
              <el-button link type="primary" size="small" @click.stop="openCreate(data)">+ 子节点</el-button>
              <el-button link type="primary" size="small" @click.stop="openEdit(data)">编辑</el-button>
              <el-popconfirm
                :title="`确认删除「${data.menuName}」？子节点会一并失效，操作不可逆。`"
                @confirm="doDelete(data)"
              >
                <template #reference>
                  <el-button link type="danger" size="small" @click.stop>删除</el-button>
                </template>
              </el-popconfirm>
            </span>
          </div>
        </template>
      </el-tree>
    </div>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog
      v-model="dlg.show"
      :title="dlg.editing ? '编辑资源' : (dlg.parent ? `在「${dlg.parent.menuName}」下新增子节点` : '新增根节点')"
      width="640px"
    >
      <el-form ref="dlgFormRef" :model="dlg.form" :rules="dlg.rules" label-width="110px">
        <el-form-item label="名称" prop="menuName">
          <el-input v-model="dlg.form.menuName" placeholder="菜单显示名 / 资源说明" maxlength="256" />
        </el-form-item>
        <el-form-item label="URL" prop="resourceUrl">
          <el-input v-model="dlg.form.resourceUrl" placeholder="Ant 风格，如 /system/users 或 /api/perf/**" maxlength="256" />
        </el-form-item>
        <el-form-item label="HTTP Method" prop="resourceMethod">
          <el-select v-model="dlg.form.resourceMethod" placeholder="选择 method">
            <el-option v-for="m in HTTP_METHODS" :key="m" :value="m" :label="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型" prop="isMenu">
          <el-radio-group v-model="dlg.form.isMenu">
            <el-radio v-for="o in IS_MENU_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
          </el-radio-group>
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
        <el-form-item label="所属系统" prop="sysCode">
          <el-input v-model="dlg.form.sysCode" placeholder="选填，如 XANZC" maxlength="10" />
        </el-form-item>
        <el-form-item label="父节点">
          <span v-if="dlg.editing" class="hint">编辑模式不可改父节点</span>
          <span v-else-if="dlg.parent" class="hint">
            {{ dlg.parent.menuName }} <code class="mono">({{ dlg.parent.resourceId }})</code>
          </span>
          <span v-else class="hint">无（作为根节点）</span>
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
import { ref, reactive, watch, computed, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Search } from '@element-plus/icons-vue';
import {
  listResourceTree, createResource, updateResource, deleteResource,
  HTTP_METHODS, IS_MENU_OPTIONS, END_FLAG_OPTIONS
} from '@/api/resources';

// === 数据 ===
const rawTree = ref([]);
const loading = ref(false);
const treeRef = ref(null);
const keyword = ref('');
const kindFilter = ref(null);
const statusFilter = ref(null);

watch(keyword, v => treeRef.value?.filter(v ?? ''));
watch([kindFilter, statusFilter], reload);

function methodCls(m) {
  return ({
    GET: 'tag-info',
    POST: 'tag-success',
    PUT: 'tag-warning',
    DELETE: 'tag-danger',
    PATCH: 'tag-warning'
  })[m] || 'tag-info';
}

function resetFilters() {
  keyword.value = '';
  kindFilter.value = null;
  statusFilter.value = null;
  reload();
}

async function reload() {
  loading.value = true;
  try {
    const params = {};
    if (statusFilter.value != null) params.status = statusFilter.value;
    rawTree.value = await listResourceTree(params);
  } catch { rawTree.value = []; }
  finally { loading.value = false; }
}

// 类型过滤：剪掉不匹配的叶子节点，保留有匹配后代的父节点
function pruneByKind(nodes, kind) {
  if (kind == null) return nodes;
  const walk = (n) => {
    const children = (n.children || []).map(walk).filter(Boolean);
    const selfMatch = n.isMenu === kind;
    if (selfMatch || children.length) {
      return { ...n, children };
    }
    return null;
  };
  return nodes.map(walk).filter(Boolean);
}
const treeData = computed(() => pruneByKind(rawTree.value, kindFilter.value));

function filterNode(value, data) {
  if (!value) return true;
  const v = String(value).toLowerCase();
  return (
    String(data.menuName || '').toLowerCase().includes(v) ||
    String(data.resourceUrl || '').toLowerCase().includes(v) ||
    String(data.resourceId || '').toLowerCase().includes(v)
  );
}

// === 新增 / 编辑 ===
const dlgFormRef = ref(null);
const dlg = reactive({
  show: false, editing: null, parent: null, saving: false,
  form: {
    menuName: '', resourceUrl: '', resourceMethod: 'GET',
    isMenu: 0, menuEndFlag: '1', menuRankNo: 0, sysCode: ''
  },
  rules: {
    menuName:       [{ required: true, message: '名称必填', trigger: 'blur' }, { max: 256, message: '不超过 256 位', trigger: 'blur' }],
    resourceUrl:    [{ required: true, message: 'URL 必填', trigger: 'blur' }, { max: 256, message: '不超过 256 位', trigger: 'blur' }],
    resourceMethod: [{ required: true, message: '请选择 HTTP method', trigger: 'change' }],
    isMenu:         [{ required: true, message: '请选择类型', trigger: 'change' }],
    menuEndFlag:    [{ required: true, message: '请选择节点形态', trigger: 'change' }]
  }
});
function openCreate(parent) {
  dlg.editing = null;
  dlg.parent = parent || null;
  dlg.form = {
    menuName: '', resourceUrl: '',
    resourceMethod: 'GET',
    isMenu: parent ? parent.isMenu : 0,
    menuEndFlag: '1',
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
    resourceMethod: row.resourceMethod || 'GET',
    isMenu: row.isMenu ?? 0,
    menuEndFlag: row.menuEndFlag || '1',
    menuRankNo: row.menuRankNo ?? 0,
    sysCode: row.sysCode || ''
  };
  dlg.show = true;
}
async function saveDlg() {
  try { await dlgFormRef.value?.validate(); } catch { return; }
  dlg.saving = true;
  try {
    if (dlg.editing) {
      await updateResource(dlg.editing, { ...dlg.form });
      ElMessage.success('已更新');
    } else {
      const payload = { ...dlg.form };
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
.res-row {
  display: flex; align-items: center; gap: 10px;
  flex: 1; min-width: 0;
  padding: 4px 0;
  .ico { font-size: 14px; }
  .name { color: $text-1; font-weight: 500; }
  .url { color: $text-3; font-size: 12px; flex-shrink: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .meth { margin-left: 4px; }
  .rank { color: $text-3; font-size: 12px; margin-left: 4px; }
  .row-actions {
    margin-left: auto;
    display: flex; gap: 4px;
    visibility: hidden;
  }
}
:deep(.el-tree-node__content):hover .res-row .row-actions { visibility: visible; }
.hint { color: $text-3; font-size: 12px; margin-left: 8px; }
.mono { font-family: ui-monospace, monospace; font-size: 12px; }
</style>
