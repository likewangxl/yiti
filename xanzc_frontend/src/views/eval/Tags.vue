<template>
<main v-bp-overflow-tooltip class="bp-crud eval-tags-page" aria-labelledby="eval-tags-page-title" :aria-busy="loading ? 'true' : 'false'">
    <header class="page-h">
      <PageTitle id="eval-tags-page-title"><span class="sub">维护可用于规则和人员评价的标签</span></PageTitle>
      <div class="actions action-group" role="group" aria-label="评价标签操作">
        <el-button @click="reload">刷新</el-button>
        <el-button type="primary" @click="openCreate">新建标签</el-button>
      </div>
    </header>

    <!-- 筛选栏 -->
    <section class="card-section filter-bar" aria-label="评价标签筛选">
      <el-form inline size="default" class="filter-form" aria-label="评价标签筛选">
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            aria-label="按标签名称筛选"
            placeholder="搜索标签名称"
            clearable
            class="keyword-input"
            @keyup.enter="reload"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="reload">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <!-- 数据表格 -->
    <section class="card-section data-panel" aria-label="评价标签列表" aria-describedby="eval-tags-table-state">
      <div class="toolbar">
        <div>
          <h2 id="eval-tags-table-heading" class="section-title">评价标签列表</h2>
          <p class="hint">标签状态会同步影响人员标签选择和评价规则配置。</p>
        </div>
        <p id="eval-tags-table-state" class="table-state" role="status" aria-live="polite">
          {{ loading ? '评价标签列表加载中' : loadError || (rows.length ? `共 ${pager.total} 个标签` : '暂无标签数据') }}
        </p>
      </div>
      <el-table :data="rows" size="default" border v-loading="loading" empty-text="暂无标签数据"
        aria-labelledby="eval-tags-table-heading" aria-describedby="eval-tags-table-state">
        <el-table-column prop="tagId" label="TAG_ID" width="100">
          <template #default="{ row }">
            <code class="mono">{{ row.tagId }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="tagName" label="标签名称" width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag
              :class="row.status === 1 ? 'tag-success' : 'tag-info'"
              effect="plain"
              size="small"
            >
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" class-name="operation-cell" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-dropdown trigger="click" popper-class="bp-crud-menu">
              <el-button link size="small" :disabled="deleting" aria-label="更多评价标签操作">更多</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item divided class="danger-item" :disabled="deleting" @click="handleDelete(row)">删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <nav class="pager" aria-label="评价标签列表分页">
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
      </nav>
    </section>

    <!-- 新建弹窗 -->
    <el-dialog v-model="createDlg.show" class="bp-crud-dialog" title="新建标签" width="480px" :close-on-click-modal="false" @closed="resetCreateForm">
      <el-form
        ref="createFormRef"
        :model="createDlg.form"
        :rules="createDlg.rules"
        label-width="90px"
        size="default"
      >
        <el-form-item label="标签名称" prop="tagName">
          <el-input
            v-model="createDlg.form.tagName"
            placeholder="请输入标签名称"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="createDlg.saving" :disabled="createDlg.saving" @click="saveCreate">保存</el-button>
      </template>
    </el-dialog>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editDlg.show" class="bp-crud-dialog" title="编辑标签" width="480px" :close-on-click-modal="false" @closed="resetEditForm">
      <el-form
        ref="editFormRef"
        :model="editDlg.form"
        :rules="editDlg.rules"
        label-width="90px"
        size="default"
      >
        <el-form-item label="标签名称" prop="tagName">
          <el-input
            v-model="editDlg.form.tagName"
            placeholder="请输入标签名称"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="editDlg.form.statusBool"
            active-text="启用"
            inactive-text="停用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="editDlg.saving" :disabled="editDlg.saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listTags, createTag, updateTag, deleteTag } from '@/api/eval';

// === 列表状态 ===
const rows = ref([]);
const loading = ref(false);
const loadError = ref('');
const filters = reactive({ keyword: '' });
const pager = reactive({ pageNo: 1, pageSize: 20, total: 0 });

/** 重置筛选条件并刷新 */
function resetFilters() {
  filters.keyword = '';
  pager.pageNo = 1;
  reload();
}

/** 加载标签列表 */
async function reload() {
  loading.value = true;
  loadError.value = '';
  try {
    const params = {
      pageNo: pager.pageNo,
      pageSize: pager.pageSize,
      keyword: filters.keyword || undefined
    };
    const r = await listTags(params);
    // 拦截器已解包：r 为 PageResult 对象 { records, total, pageNo, pageSize }
    rows.value = r?.records || [];
    pager.total = r?.total ?? 0;
  } catch {
    rows.value = [];
    loadError.value = '评价标签加载失败，请刷新重试';
  } finally {
    loading.value = false;
  }
}

// === 新建弹窗 ===
const createFormRef = ref(null);
const deleting = ref(false);
const createDlg = reactive({
  show: false,
  saving: false,
  form: { tagName: '' },
  rules: {
    tagName: [{ required: true, message: '标签名称必填', trigger: 'blur' }]
  }
});

/** 打开新建弹窗 */
function openCreate() {
  createDlg.show = true;
}

/** 重置新建表单 */
function resetCreateForm() {
  createDlg.form = { tagName: '' };
  createFormRef.value?.clearValidate();
}

/** 保存新建 */
async function saveCreate() {
  try {
    await createFormRef.value?.validate();
  } catch {
    return;
  }
  createDlg.saving = true;
  try {
    await createTag(createDlg.form.tagName);
    ElMessage.success('标签已创建');
    createDlg.show = false;
    await reload();
  } catch {
    ElMessage.error('创建失败，请重试');
  } finally {
    createDlg.saving = false;
  }
}

// === 编辑弹窗 ===
const editFormRef = ref(null);
const editDlg = reactive({
  show: false,
  saving: false,
  tagId: null,
  form: { tagName: '', statusBool: true },
  rules: {
    tagName: [{ required: true, message: '标签名称必填', trigger: 'blur' }]
  }
});

/** 打开编辑弹窗，回显当前行数据 */
function openEdit(row) {
  editDlg.tagId = row.tagId;
  editDlg.form = {
    tagName: row.tagName,
    // status=1 表示启用，转为 boolean 供 el-switch 绑定
    statusBool: row.status === 1
  };
  editDlg.show = true;
}

/** 重置编辑表单 */
function resetEditForm() {
  editDlg.tagId = null;
  editDlg.form = { tagName: '', statusBool: true };
  editFormRef.value?.clearValidate();
}

/** 保存编辑 */
async function saveEdit() {
  try {
    await editFormRef.value?.validate();
  } catch {
    return;
  }
  editDlg.saving = true;
  try {
    // boolean → number：启用=1，停用=0
    const status = editDlg.form.statusBool ? 1 : 0;
    await updateTag(editDlg.tagId, editDlg.form.tagName, status);
    ElMessage.success('标签已更新');
    editDlg.show = false;
    await reload();
  } catch {
    ElMessage.error('更新失败，请重试');
  } finally {
    editDlg.saving = false;
  }
}

// === 删除 ===
/** 确认后删除标签 */
async function handleDelete(row) {
  if (deleting.value) return;
  try {
    await ElMessageBox.confirm(
      `确认删除标签「${row.tagName}」？删除后不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    );
  } catch {
    return;
  }
  deleting.value = true;
  try {
    await deleteTag(row.tagId);
    ElMessage.success('标签已删除');
    await reload();
  } catch {
    ElMessage.error('删除失败，请重试');
  } finally {
    deleting.value = false;
  }
}

onMounted(reload);
</script>

<style lang="scss" scoped>
.keyword-input { width: 220px; }
.mono {
  font-family: ui-monospace, monospace;
  font-size: 12px;
  color: var(--color-text-muted);
}
</style>
