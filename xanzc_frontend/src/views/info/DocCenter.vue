<template>
  <main class="bp-crud doc-page" aria-labelledby="doc-center-title">
    <header class="page-h">
      <PageTitle id="doc-center-title"><span class="sub">科技部维护常用文档及分类</span></PageTitle>
      <div class="actions action-group" role="group" aria-label="文档中心操作">
        <el-button type="primary" @click="openUpload">上传文档</el-button>
      </div>
    </header>

    <section class="card-section filter-bar" aria-label="文档筛选">
      <el-form class="filter-form" aria-label="文档筛选" @submit.prevent="reload">
        <el-form-item label="文档名称">
          <el-input
            v-model="keyword"
            aria-label="按文档名称筛选"
            placeholder="搜索文档名称"
            clearable
            style="width:240px"
            @keyup.enter="reload"
            @clear="reload"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <div class="doc-body">
      <nav class="cat-panel card-section" aria-label="文档分类">
        <h2 class="section-title">分类</h2>
        <ul class="cat-list">
          <li>
            <button type="button" class="cat-item" :class="{ active: curCat === '' }" :aria-pressed="curCat === ''" @click="selectCat('')">
              <span>全部</span><span class="cat-count">{{ totalCount }}</span>
            </button>
          </li>
          <li v-for="c in categories" :key="c.name">
            <button type="button" class="cat-item" :class="{ active: curCat === c.name }" :aria-pressed="curCat === c.name" @click="selectCat(c.name)">
              <span>{{ c.name }}</span><span class="cat-count">{{ c.count }}</span>
            </button>
          </li>
        </ul>
      </nav>

      <section
        class="doc-table card-section data-panel"
        aria-label="文档列表"
        aria-describedby="doc-center-state"
        :aria-busy="loading ? 'true' : 'false'"
      >
        <div class="toolbar">
          <div>
            <h2 id="doc-center-heading" class="section-title">文档列表</h2>
            <p class="hint">选择左侧分类后可继续按文档名称检索；下载会使用后端返回的预签名地址。</p>
          </div>
          <p id="doc-center-state" class="table-state" role="status" aria-live="polite">
            {{ loading ? '文档列表加载中' : rows.length ? `共 ${total} 个文档` : '暂无文档数据' }}
          </p>
        </div>
        <el-table
          :data="rows"
          v-loading="loading"
          empty-text="暂无文档数据"
          aria-labelledby="doc-center-heading"
          aria-describedby="doc-center-state"
        >
          <el-table-column prop="docTitle" label="文档名称" min-width="240" show-overflow-tooltip />
          <el-table-column label="分类" width="130">
            <template #default="{row}"><el-tag class="tag-info" effect="plain">{{ row.docCategoryDesc || row.docCategory }}</el-tag></template>
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
              <el-tag :class="row.status === 'ACTIVE' ? 'tag-success' : 'tag-info'" effect="plain">
                {{ row.status === 'ACTIVE' ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{row}">
              <el-button link type="primary" size="small" :disabled="deletingId === row.id" @click="onDownload(row)">下载</el-button>
              <el-button link type="primary" size="small" :disabled="deletingId === row.id" @click="openEdit(row)">编辑</el-button>
              <el-popconfirm :title="`确认删除「${row.docTitle}」？删除后无法恢复。`" @confirm="onDelete(row)">
                <template #reference><el-button link type="danger" size="small" :loading="deletingId === row.id">删除</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
        <nav class="pager" aria-label="文档列表分页">
          <el-pagination
            v-model:current-page="pgNo"
            v-model:page-size="pgSize"
            :page-sizes="[10,20,50]"
            :total="total"
            background
            layout="total, sizes, prev, pager, next"
            @change="reload"
          />
        </nav>
      </section>
    </div>

    <el-dialog v-model="dialogVisible" class="bp-crud-dialog" :title="editing ? '编辑文档' : '上传文档'" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="文档名称" required>
          <el-input v-model="form.docTitle" aria-label="文档名称" maxlength="120" />
        </el-form-item>
        <el-form-item label="分类" required>
          <el-select
            v-model="form.docCategory"
            aria-label="文档分类"
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入分类"
            style="width:100%"
          >
            <el-option v-for="c in categories" :key="c.name" :value="c.name" :label="c.name" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editing" label="状态">
          <el-select v-model="form.status" aria-label="文档状态" style="width:140px">
            <el-option value="ACTIVE" label="启用" />
            <el-option value="DISABLED" label="禁用" />
          </el-select>
        </el-form-item>
        <el-form-item label="文件" :required="!editing">
          <el-upload
            :auto-upload="false"
            :show-file-list="true"
            :limit="1"
            :disabled="saving"
            :on-change="onFilePick"
            :on-remove="onFileRemove"
          >
            <el-button :disabled="saving">选择文件</el-button>
            <template #tip>
              <span v-if="editing && form.fileObjectId && !pickedFile" class="hint">已有文件（重新选择可替换）</span>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { fmtDateTime } from '@/utils/datetime';
import { call } from '@/api/http';
import { listDocuments, createDocument, updateDocument, deleteDocument, downloadDocument } from '@/api/documents';

const loading = ref(false);
const saving = ref(false);
const deletingId = ref(null);
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
      pageNo: pgNo.value,
      pageSize: pgSize.value
    });
    rows.value = Array.isArray(r) ? r : (r?.records || []);
    total.value = Array.isArray(r) ? r.length : (r?.total ?? 0);
  } catch { rows.value = []; total.value = 0; } finally { loading.value = false; }
}

async function loadCategories() {
  try {
    const r = await listDocuments({ pageNo: 1, pageSize: 500 });
    const list = Array.isArray(r) ? r : (r?.records || []);
    totalCount.value = Array.isArray(r) ? list.length : (r?.total ?? list.length);
    const categoryCount = new Map();
    for (const doc of list) {
      const name = doc.docCategoryDesc || doc.docCategory || '未分类';
      categoryCount.set(name, (categoryCount.get(name) || 0) + 1);
    }
    categories.value = [...categoryCount.entries()].map(([name, count]) => ({ name, count }));
  } catch { categories.value = []; }
}

function selectCat(name) {
  curCat.value = name;
  pgNo.value = 1;
  reload();
}

function resetFilters() {
  keyword.value = '';
  curCat.value = '';
  pgNo.value = 1;
  reload();
}

function onDownload(row) {
  downloadDocument(row.id).catch(e => ElMessage.error(e?.message || '下载失败'));
}

const dialogVisible = ref(false);
const editing = ref(null);
const pickedFile = ref(null);
const form = ref({ docTitle: '', docCategory: '', status: 'ACTIVE', fileObjectId: '' });

function openUpload() {
  editing.value = null;
  pickedFile.value = null;
  form.value = { docTitle: '', docCategory: '', status: 'ACTIVE', fileObjectId: '' };
  dialogVisible.value = true;
}

function openEdit(row) {
  editing.value = row;
  pickedFile.value = null;
  form.value = {
    docTitle: row.docTitle,
    docCategory: row.docCategory,
    status: row.status || 'ACTIVE',
    fileObjectId: row.fileObjectId || ''
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
  if (saving.value) return;
  if (!form.value.docTitle?.trim()) return ElMessage.warning('请填写文档名称');
  if (!form.value.docCategory?.trim()) return ElMessage.warning('请选择或输入分类');
  if (!editing.value && !pickedFile.value) return ElMessage.warning('请选择文件');
  saving.value = true;
  try {
    const fileObjectId = await uploadIfNeeded();
    if (editing.value) {
      await updateDocument(editing.value.id, {
        docTitle: form.value.docTitle,
        docCategory: form.value.docCategory,
        fileObjectId,
        status: form.value.status
      });
    } else {
      await createDocument({ docTitle: form.value.docTitle, docCategory: form.value.docCategory, fileObjectId });
    }
    ElMessage.success('已保存');
    dialogVisible.value = false;
    reload();
    loadCategories();
  } catch (e) { ElMessage.error(e?.message || '保存失败'); } finally { saving.value = false; }
}

async function onDelete(row) {
  if (deletingId.value === row.id) return;
  deletingId.value = row.id;
  try {
    await deleteDocument(row.id);
    ElMessage.success('已删除');
    reload();
    loadCategories();
  } catch (e) { ElMessage.error(e?.message || '删除失败'); } finally { deletingId.value = null; }
}

onMounted(() => { loadCategories(); reload(); });
</script>

<style scoped>
.doc-body { align-items: flex-start; display: flex; gap: var(--space-4); }
.cat-panel { flex: 0 0 220px; padding: var(--space-4); }
.cat-list { display: grid; gap: var(--space-1); list-style: none; margin: 0; padding: 0; }
.cat-item { align-items: center; background: transparent; border: 0; border-radius: var(--radius-control); color: var(--color-text); cursor: pointer; display: flex; font: inherit; font-size: 14px; justify-content: space-between; min-height: 36px; padding: 0 var(--space-2); text-align: left; width: 100%; }
.cat-item:hover { background: var(--color-surface-soft); }
.cat-item.active { background: var(--color-brand-100); color: var(--color-brand-700); font-weight: 500; }
.cat-count { color: var(--color-text-muted); font-size: 12px; font-variant-numeric: tabular-nums; }
.doc-table { flex: 1; min-width: 0; }
</style>
