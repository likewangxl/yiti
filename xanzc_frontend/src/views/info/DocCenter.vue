<template>
  <div class="doc-page">
    <div class="page-h">
      <h1>常用文档下载 <span class="sub">科技部维护</span></h1>
      <div class="actions">
        <el-button type="primary" @click="openUpload">📤 上传文档</el-button>
      </div>
    </div>

    <div class="doc-body">
      <!-- 左：分类 -->
      <div class="cat-panel">
        <div class="cat-title">分类</div>
        <ul class="cat-list">
          <li :class="{ active: curCat === '' }" @click="selectCat('')">
            <span>全部</span><span class="cat-count">{{ totalCount }}</span>
          </li>
          <li v-for="c in categories" :key="c.name" :class="{ active: curCat === c.name }" @click="selectCat(c.name)">
            <span>📁 {{ c.name }}</span><span class="cat-count">{{ c.count }}</span>
          </li>
        </ul>
      </div>

      <!-- 右：文档表格 -->
      <div class="doc-table card-section">
        <div class="table-tools">
          <el-input v-model="keyword" placeholder="搜索文档名称" clearable style="width:240px"
                    @keyup.enter="reload" @clear="reload" />
          <el-button type="primary" @click="reload">查询</el-button>
        </div>
        <el-table :data="rows" v-loading="loading" empty-text="暂无文档">
          <el-table-column prop="docTitle" label="文档名称" min-width="240" show-overflow-tooltip>
            <template #default="{row}">📄 {{ row.docTitle }}</template>
          </el-table-column>
          <el-table-column label="分类" width="130">
            <template #default="{row}"><el-tag effect="plain" type="info">{{ row.docCategoryDesc || row.docCategory }}</el-tag></template>
          </el-table-column>
          <el-table-column label="大小" width="100">
            <template #default="{row}">{{ row.fileSize != null ? fmtSize(row.fileSize) : '—' }}</template>
          </el-table-column>
          <el-table-column label="更新人" width="120">
            <template #default="{row}">{{ row.updatedByName || row.updatedBy || '—' }}</template>
          </el-table-column>
          <el-table-column label="更新时间" width="170">
            <template #default="{row}">{{ fmtDate(row.updatedTime) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{row}">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">
                {{ row.status === 'ACTIVE' ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{row}">
              <el-button link type="primary" size="small" @click="onDownload(row)">下载</el-button>
              <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-popconfirm :title="`确认删除「${row.docTitle}」？`" @confirm="onDelete(row)">
                <template #reference><el-button link type="danger" size="small">删除</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination v-model:current-page="pgNo" v-model:page-size="pgSize" :page-sizes="[10,20,50]"
                         :total="total" background layout="total, sizes, prev, pager, next" @change="reload" />
        </div>
      </div>
    </div>

    <!-- 上传 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑文档' : '上传文档'" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="文档名称" required>
          <el-input v-model="form.docTitle" maxlength="120" />
        </el-form-item>
        <el-form-item label="分类" required>
          <el-select v-model="form.docCategory" filterable allow-create default-first-option
                     placeholder="选择或输入分类" style="width:100%">
            <el-option v-for="c in categories" :key="c.name" :value="c.name" :label="c.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" v-if="editing">
          <el-select v-model="form.status" style="width:140px">
            <el-option value="ACTIVE" label="启用" />
            <el-option value="DISABLED" label="禁用" />
          </el-select>
        </el-form-item>
        <el-form-item label="文件" :required="!editing">
          <el-upload :auto-upload="false" :show-file-list="true" :limit="1" :on-change="onFilePick" :on-remove="onFileRemove">
            <el-button>选择文件</el-button>
            <template #tip>
              <span v-if="editing && form.fileObjectId && !pickedFile" class="hint">已有文件（重新选择可替换）</span>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { fmtDateTime } from '@/utils/datetime';
import { call } from '@/api/http';
import { listDocuments, createDocument, updateDocument, deleteDocument, downloadDocument } from '@/api/documents';

const loading = ref(false);
const saving = ref(false);
const rows = ref([]);
const total = ref(0);
const totalCount = ref(0);
const pgNo = ref(1);
const pgSize = ref(20);
const keyword = ref('');
const curCat = ref('');
const categories = ref([]);

const fmtDate = (v) => fmtDateTime(v);
const fmtSize = (n) => n > 1024 * 1024 ? (n / 1024 / 1024).toFixed(1) + ' MB' : (n / 1024).toFixed(0) + ' KB';

async function reload() {
  loading.value = true;
  try {
    const r = await listDocuments({
      keyword: keyword.value || undefined,
      category: curCat.value || undefined,
      pageNo: pgNo.value, pageSize: pgSize.value
    });
    rows.value = Array.isArray(r) ? r : (r?.records || []);
    total.value = Array.isArray(r) ? r.length : (r?.total ?? 0);
  } catch { rows.value = []; total.value = 0; } finally { loading.value = false; }
}

// 分类树：单独拉一页大数据去重 docCategory + 计数
async function loadCategories() {
  try {
    const r = await listDocuments({ pageNo: 1, pageSize: 500 });
    const list = Array.isArray(r) ? r : (r?.records || []);
    totalCount.value = Array.isArray(r) ? list.length : (r?.total ?? list.length);
    const map = new Map();
    for (const d of list) {
      const k = d.docCategoryDesc || d.docCategory || '未分类';
      map.set(k, (map.get(k) || 0) + 1);
    }
    categories.value = [...map.entries()].map(([name, count]) => ({ name, count }));
  } catch { categories.value = []; }
}

function selectCat(name) {
  curCat.value = name;
  pgNo.value = 1;
  reload();
}

function onDownload(row) {
  downloadDocument(row.id).catch(e => ElMessage.error(e?.message || '下载失败'));
}

// ---- 上传 / 编辑 ----
const dialogVisible = ref(false);
const editing = ref(null);
const pickedFile = ref(null);
const form = ref({ docTitle: '', docCategory: '', status: 'ACTIVE', fileObjectId: '' });
function openUpload() {
  editing.value = null; pickedFile.value = null;
  form.value = { docTitle: '', docCategory: '', status: 'ACTIVE', fileObjectId: '' };
  dialogVisible.value = true;
}
function openEdit(row) {
  editing.value = row; pickedFile.value = null;
  form.value = {
    docTitle: row.docTitle, docCategory: row.docCategory,
    status: row.status || 'ACTIVE', fileObjectId: row.fileObjectId || ''
  };
  dialogVisible.value = true;
}
function onFilePick(file) { pickedFile.value = file?.raw || null; }
function onFileRemove() { pickedFile.value = null; }

async function uploadIfNeeded() {
  if (!pickedFile.value) return form.value.fileObjectId || null;
  const fd = new FormData();
  fd.append('file', pickedFile.value);
  const res = await call('post', '/files/upload', { data: fd, headers: { 'Content-Type': 'multipart/form-data' } }, null);
  return res?.id || res?.fileObjectId || null;
}

async function submit() {
  if (!form.value.docTitle?.trim()) return ElMessage.warning('请填写文档名称');
  if (!form.value.docCategory?.trim()) return ElMessage.warning('请选择/输入分类');
  if (!editing.value && !pickedFile.value) return ElMessage.warning('请选择文件');
  saving.value = true;
  try {
    const fileObjectId = await uploadIfNeeded();
    if (editing.value) {
      await updateDocument(editing.value.id, {
        docTitle: form.value.docTitle, docCategory: form.value.docCategory,
        fileObjectId, status: form.value.status
      });
    } else {
      await createDocument({ docTitle: form.value.docTitle, docCategory: form.value.docCategory, fileObjectId });
    }
    ElMessage.success('已保存');
    dialogVisible.value = false;
    reload(); loadCategories();
  } catch (e) { ElMessage.error(e?.message || '保存失败'); } finally { saving.value = false; }
}

async function onDelete(row) {
  try { await deleteDocument(row.id); ElMessage.success('已删除'); reload(); loadCategories(); }
  catch (e) { ElMessage.error(e?.message || '删除失败'); }
}

onMounted(() => { loadCategories(); reload(); });
</script>

<style scoped>
.doc-page { }
.page-h { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-h h1 { font-size: 18px; margin: 0; }
.page-h .sub { font-size: 12px; color: #909399; font-weight: normal; margin-left: 8px; }
.doc-body { display: flex; gap: 14px; align-items: flex-start; }
.cat-panel { flex: 0 0 220px; background: #fff; border: 1px solid #ebeef5; border-radius: 4px; padding: 16px 14px; }
.cat-title { font-size: 15px; font-weight: 600; margin-bottom: 10px; }
.cat-list { list-style: none; margin: 0; padding: 0; }
.cat-list li { display: flex; justify-content: space-between; align-items: center; padding: 9px 10px; border-radius: 6px; cursor: pointer; font-size: 14px; color: #303133; }
.cat-list li:hover { background: #f5f7fa; }
.cat-list li.active { background: var(--el-color-primary-light-9); color: var(--el-color-primary); font-weight: 500; }
.cat-count { font-size: 12px; color: #909399; }
.doc-table { flex: 1; min-width: 0; }
.table-tools { display: flex; gap: 8px; margin-bottom: 12px; }
.pager { margin-top: 12px; text-align: right; }
.hint { color: #909399; font-size: 12px; }
</style>
