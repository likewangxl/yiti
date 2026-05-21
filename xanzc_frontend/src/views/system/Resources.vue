<template>
  <div>
    <div class="page-h">
      <h1>菜单管理 <span class="sub">仅显示 PT_RESOURCE.IS_MENU=1 的菜单节点；接口资源在「资源管理」单独维护</span></h1>
      <div class="actions">
        <el-button @click="reload">刷新</el-button>
        <el-button type="primary" @click="openCreate(null)">+ 新增一级菜单</el-button>
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
            <span class="ico">{{ data.menuIconUrl || '📂' }}</span>
            <span class="name">{{ data.menuName }}</span>
            <code class="mono url">{{ data.resourceUrl }}</code>
            <el-tag :class="data.status === 0 ? 'tag-success' : 'tag-warning'" effect="plain" size="small">
              {{ data.status === 0 ? '启用' : '停用' }}
            </el-tag>
            <span class="rank">排序 #{{ data.menuRankNo ?? 0 }}</span>
            <span class="row-actions">
              <el-button link type="primary" size="small" @click.stop="openCreate(data)">+ 子菜单</el-button>
              <el-button link type="primary" size="small" @click.stop="openEdit(data)">编辑</el-button>
              <el-popconfirm
                :title="`确认删除「${data.menuName}」？子菜单会一并失效，操作不可逆。`"
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
          <!-- URL 输入 + 前端路由建议（菜单类资源参考 xanpd 的"resource_url 下拉路由列表"）。
               用 el-autocomplete 替代纯文本：菜单可从已注册路由选，接口仍可手填 Ant 风格 path -->
          <el-autocomplete
            v-model="dlg.form.resourceUrl"
            :fetch-suggestions="fetchUrlSuggest"
            placeholder="Ant 风格，如 /system/users 或 /api/perf/**"
            maxlength="256"
            clearable
            style="width:100%"
          />
        </el-form-item>
        <el-form-item label="图标" prop="menuIconUrl">
          <div class="icon-pick">
            <span class="icon-preview clickable" @click="iconPicker.show = true" title="点击选择图标">
              {{ dlg.form.menuIconUrl || '📂' }}
            </span>
            <el-button @click="iconPicker.show = true">选择图标</el-button>
            <el-button v-if="dlg.form.menuIconUrl" link @click="dlg.form.menuIconUrl = ''">清除</el-button>
          </div>
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

    <!-- 图标选择器：常用 emoji 网格（按类别分组） -->
    <el-dialog v-model="iconPicker.show" title="选择菜单图标" width="560px" append-to-body>
      <div v-for="g in ICON_GROUPS" :key="g.name" class="icon-group">
        <div class="icon-group-name">{{ g.name }}</div>
        <div class="icon-grid">
          <span
            v-for="emo in g.icons"
            :key="emo"
            class="icon-cell"
            :class="{ active: dlg.form.menuIconUrl === emo }"
            :title="emo"
            @click="pickIcon(emo)">{{ emo }}</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="iconPicker.show = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, watch, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Search } from '@element-plus/icons-vue';
import {
  listResourceTree, createResource, updateResource, deleteResource,
  HTTP_METHODS, IS_MENU_OPTIONS, END_FLAG_OPTIONS
} from '@/api/resources';

const router = useRouter();
// URL 建议：把当前前端注册路由扁平化作为 autocomplete 数据源（菜单类资源选 path 更准）
function flatRouterPaths() {
  const result = [];
  const layout = router.options.routes.find(r => r.path === '/' && r.children?.length);
  for (const r of layout?.children || []) {
    if (!r.path || !r.meta?.title) continue;
    result.push({ value: '/' + r.path, label: r.meta.title });
  }
  return result;
}
function fetchUrlSuggest(queryString, cb) {
  const all = flatRouterPaths();
  const q = (queryString || '').toLowerCase();
  cb(all.filter(s => s.value.toLowerCase().includes(q) || (s.label || '').toLowerCase().includes(q)));
}

// Emoji 图标库（按类别分组，跟 sidebar 当前用的 emoji 风格一致）。
// 未来想升级到 element-plus SVG icon 时，把这里改成图标名 + 模板用 <component :is> 即可。
const ICON_GROUPS = [
  { name: '导航 / 主页',  icons: ['🏠','🏡','📂','📁','📋','📌','📍','🗂','🗃','🧭'] },
  { name: '业绩 / 数据',  icons: ['📊','📈','📉','💹','💰','💵','💴','💷','💶','💳','🧮','📐'] },
  { name: '客户 / 营销',  icons: ['👥','👤','🧑','👨‍💼','👩‍💼','📞','📧','💬','🤝','🎯','🎁','📢'] },
  { name: '业务 / 流程',  icons: ['✅','❌','📝','📃','📄','📅','📆','🗓','⏰','⏳','✍','📥','📤','🚀'] },
  { name: '系统 / 设置',  icons: ['⚙','🔧','🔨','🛠','🔑','🔒','🔓','🛡','📡','💻','🖥','🖨'] },
  { name: '通知 / 警告',  icons: ['🔔','🔕','⚠','❗','❓','💡','🌟','⭐','🎉','🚨'] }
];
const iconPicker = reactive({ show: false });
function pickIcon(emo) {
  dlg.form.menuIconUrl = emo;
  iconPicker.show = false;
}

// === 数据 ===
const rawTree = ref([]);
const loading = ref(false);
const treeRef = ref(null);
const keyword = ref('');
const statusFilter = ref(null);

watch(keyword, v => treeRef.value?.filter(v ?? ''));
watch([statusFilter], reload);

function resetFilters() {
  keyword.value = '';
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

// 菜单管理只展示 IS_MENU=1 节点；接口资源（IS_MENU=0）保留在树里但不显示，
// 父节点 IS_MENU=1 但子节点全是接口的，子节点会被剪掉，父节点保留
function pruneMenusOnly(nodes) {
  const walk = (n) => {
    const children = (n.children || []).map(walk).filter(Boolean);
    if (n.isMenu === 1) return { ...n, children };
    return null;
  };
  return nodes.map(walk).filter(Boolean);
}
const treeData = computed(() => pruneMenusOnly(rawTree.value));

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
  // 菜单管理强制 isMenu=1 + resourceMethod='MENU'，前端不暴露这两个字段
  form: {
    menuName: '', resourceUrl: '', resourceMethod: 'MENU',
    isMenu: 1, menuEndFlag: '1', menuRankNo: 0, sysCode: '', menuIconUrl: ''
  },
  rules: {
    menuName:    [{ required: true, message: '菜单名必填', trigger: 'blur' }, { max: 256, message: '不超过 256 位', trigger: 'blur' }],
    resourceUrl: [{ required: true, message: '路由 path 必填', trigger: 'blur' }, { max: 256, message: '不超过 256 位', trigger: 'blur' }],
    menuEndFlag: [{ required: true, message: '请选择节点形态', trigger: 'change' }]
  }
});
function openCreate(parent) {
  dlg.editing = null;
  dlg.parent = parent || null;
  dlg.form = {
    menuName: '', resourceUrl: '',
    resourceMethod: 'MENU',  // 菜单节点统一 MENU，不暴露给用户
    isMenu: 1,               // 菜单管理只建菜单
    menuEndFlag: parent ? '1' : '0',  // 新增子默认叶子，新增根默认非叶子
    menuRankNo: 0,
    sysCode: parent?.sysCode || '',
    menuIconUrl: ''
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
    sysCode: row.sysCode || '',
    menuIconUrl: row.menuIconUrl || ''
  };
  dlg.show = true;
}
async function saveDlg() {
  try { await dlgFormRef.value?.validate(); } catch { return; }
  dlg.saving = true;
  try {
    // 后端 ResourceUpdateReqDTO 严格反序列化，只认 8 个字段：
    //   resourceUrl/status/parentResourceId/menuEndFlag/isMenu/menuName/menuRankNo/resourceMethod
    // 提交前剥离前端额外字段：sysCode（DB 有但 DTO 无）、menuIconUrl（DB 有但 DTO 无，图标暂无法持久化）
    // eslint-disable-next-line no-unused-vars
    const { sysCode, menuIconUrl, ...rest } = dlg.form;
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
  }
}
.hint { color: $text-3; font-size: 12px; margin-left: 8px; }
.mono { font-family: ui-monospace, monospace; font-size: 12px; }

// 图标选择器输入行 + emoji 网格
.icon-pick { display: flex; align-items: center; gap: 8px; width: 100%;
  .icon-preview { display: inline-block; width: 32px; height: 32px; line-height: 32px;
    text-align: center; font-size: 18px;
    border: 1px solid $border-1; border-radius: 4px; background: $bg-soft; flex-shrink: 0;
    &.clickable { cursor: pointer; transition: .12s;
      &:hover { border-color: $primary-400; background: #fff; }
    }
  }
}
.icon-group { margin-bottom: 12px;
  .icon-group-name { font-size: 12px; color: $text-3; margin-bottom: 6px; }
  .icon-grid { display: grid; grid-template-columns: repeat(12, 1fr); gap: 4px; }
  .icon-cell { display: inline-block; width: 32px; height: 32px; line-height: 32px;
    text-align: center; font-size: 18px; cursor: pointer; border-radius: 4px;
    border: 1px solid transparent; transition: .12s;
    &:hover { background: $bg-soft; border-color: $primary-400; }
    &.active { background: rgba(30,91,186,.08); border-color: $primary; box-shadow: 0 0 0 2px rgba(30,91,186,.15); }
  }
}
</style>
