<template>
  <div>
    <div class="page-h">
      <h1>标签管理</h1>
      <span class="desc">评价标签</span>
      <div class="actions">
        <el-button @click="reload">刷新</el-button>
        <el-button type="primary" @click="openCreate">+ 新建标签</el-button>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="card-section filter-section">
      <el-form inline size="default" class="filter-form">
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            placeholder="搜索标签名称"
            clearable
            style="width:220px"
            @keyup.enter="reload"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="reload">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="card-section">
      <el-table :data="rows" size="default" v-loading="loading" empty-text="暂无标签数据">
        <el-table-column prop="tagId" label="TAG_ID" width="100">
          <template #default="{ row }">
            <code class="mono">{{ row.tagId }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="tagName" label="标签名称" min-width="180" show-overflow-tooltip />
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
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
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

    <!-- 新建弹窗 -->
    <el-dialog v-model="createDlg.show" title="新建标签" width="480px" @closed="resetCreateForm">
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
        <el-button type="primary" :loading="createDlg.saving" @click="saveCreate">保存</el-button>
      </template>
    </el-dialog>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editDlg.show" title="编辑标签" width="480px" @closed="resetEditForm">
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
        <el-button type="primary" :loading="editDlg.saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listTags, createTag, updateTag, deleteTag } from '@/api/eval';

// === 列表状态 ===
const rows = ref([]);
const loading = ref(false);
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
  } finally {
    loading.value = false;
  }
}

// === 新建弹窗 ===
const createFormRef = ref(null);
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
  try {
    await ElMessageBox.confirm(
      `确认删除标签「${row.tagName}」？删除后不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    );
  } catch {
    return;
  }
  try {
    await deleteTag(row.tagId);
    ElMessage.success('标签已删除');
    await reload();
  } catch {
    ElMessage.error('删除失败，请重试');
  }
}

onMounted(reload);
</script>

<style lang="scss" scoped>
.filter-section {
  padding: 16px 18px 4px;
  margin-bottom: 12px;
}
.card-section {
  padding: 16px 18px;
}
.filter-form {
  margin-bottom: 0;
}
.pager {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}
.pager :deep(.el-pagination) { flex-wrap: wrap; row-gap: 8px; justify-content: flex-end; }
.mono {
  font-family: ui-monospace, monospace;
  font-size: 12px;
  color: $text-2;
}
</style>
