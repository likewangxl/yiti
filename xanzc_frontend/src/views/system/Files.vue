<template>
  <main class="bp-crud files-page" aria-labelledby="files-page-title" :aria-busy="loading ? 'true' : 'false'">
    <header class="page-h">
      <PageTitle id="files-page-title"><span class="sub">集中查看已上传的业务附件；上传仍在所属业务单据中完成。</span></PageTitle>
      <div class="actions action-group" role="group" aria-label="文件管理操作">
        <el-button @click="reload">刷新</el-button>
      </div>
    </header>

    <section class="card-section filter-bar" aria-label="文件筛选">
      <el-form class="filter-form" inline size="default" aria-label="文件筛选条件" @submit.prevent="onSearch">
        <el-form-item label="文件名"><el-input v-model="f.fileName" clearable placeholder="模糊匹配" aria-label="按文件名筛选" style="width:220px" @keyup.enter="onSearch" /></el-form-item>
        <el-form-item label="文件类型"><el-input v-model="f.fileType" clearable placeholder="如 pdf / xlsx" aria-label="按文件类型筛选" style="width:160px" @keyup.enter="onSearch" /></el-form-item>
        <el-form-item label="上传人"><el-input v-model="f.uploadedBy" clearable placeholder="工号" aria-label="按上传人工号筛选" style="width:160px" @keyup.enter="onSearch" /></el-form-item>
        <el-form-item><el-button type="primary" @click="onSearch">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </section>

    <section
      class="card-section data-panel files-table-panel"
      aria-label="文件列表"
      aria-labelledby="files-table-heading"
      aria-describedby="files-table-state"
      :aria-busy="loading ? 'true' : 'false'"
    >
      <div class="toolbar">
        <div>
          <h2 id="files-table-heading" class="section-title">文件列表</h2>
          <p class="hint">下载会沿用受控下载地址；删除前需确认，删除成功后刷新当前页。</p>
        </div>
        <p id="files-table-state" class="table-state" role="status" aria-live="polite">{{ loading ? '文件列表加载中' : rows.length ? `共 ${total} 个文件` : '暂无文件' }}</p>
      </div>
      <p v-if="loadError" class="error-state" role="alert">{{ loadError }} <el-button link type="primary" @click="reload">重试</el-button></p>
      <el-table :data="rows" size="default" empty-text="暂无文件" v-loading="loading" aria-labelledby="files-table-heading" aria-describedby="files-table-state">
        <el-table-column label="文件名" min-width="300"><template #default="{ row }"><span class="file-name">{{ row.fileName || row.name }}</span></template></el-table-column>
        <el-table-column label="类型" width="112"><template #default="{ row }"><el-tag :class="typeCls(row.fileType || row.type)" effect="plain">{{ (row.fileType || row.type || '-').toUpperCase() }}</el-tag></template></el-table-column>
        <el-table-column label="大小" width="120"><template #default="{ row }">{{ fmtSize(row.fileSize ?? row.size) }}</template></el-table-column>
        <el-table-column label="MD5" width="132"><template #default="{ row }"><code v-if="row.md5Hash" class="mono">{{ row.md5Hash.slice(0, 10) }}…</code><span v-else>-</span></template></el-table-column>
        <el-table-column label="上传人" width="150"><template #default="{ row }">{{ row.uploadedBy || row.by || '-' }}</template></el-table-column>
        <el-table-column label="上传时间" width="180"><template #default="{ row }">{{ row.uploadedTime || row.time || '-' }}</template></el-table-column>
        <el-table-column label="操作" width="176" fixed="right">
          <template #default="{ row }">
            <div class="row-actions" role="group" :aria-label="`${row.fileName || row.name || '文件'} 操作`">
              <el-button link type="primary" size="small" @click="onDownload(row)">下载</el-button>
              <el-button link type="danger" size="small" :loading="isDeleting(row.id)" :disabled="isDeleting(row.id)" @click="onDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <nav class="pager" aria-label="文件列表分页">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="reload"
          @size-change="onSizeChange"
        />
      </nav>
    </section>
  </main>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listFiles, deleteFile } from '@/api/system';
import { API_BASE } from '@/api/http';

const rows = ref([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(20);
const loading = ref(false);
const loadError = ref('');
const f = reactive({ fileName: '', fileType: '', uploadedBy: '' });

const typeCls = (type) => {
  const normalized = (type || '').toUpperCase();
  if (['PDF'].includes(normalized)) return 'tag-danger';
  if (['XLSX', 'XLS', 'CSV'].includes(normalized)) return 'tag-success';
  if (['JPG', 'JPEG', 'PNG', 'GIF'].includes(normalized)) return 'tag-warning';
  return 'tag-info';
};
function fmtSize(value) {
  if (value == null) return '-';
  const bytes = Number(value);
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

async function reload() {
  loading.value = true;
  loadError.value = '';
  try {
    const result = await listFiles({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      fileName: f.fileName || undefined,
      fileType: f.fileType || undefined,
      uploadedBy: f.uploadedBy || undefined
    });
    const items = result?.records || (Array.isArray(result) ? result : []);
    rows.value = items;
    total.value = result?.total ?? items.length;
  } catch (error) {
    rows.value = [];
    total.value = 0;
    loadError.value = `文件列表加载失败：${error?.message || '请稍后重试'}`;
  } finally {
    loading.value = false;
  }
}
function onSearch() { pageNo.value = 1; reload(); }
function onSizeChange() { pageNo.value = 1; reload(); }
function reset() {
  Object.assign(f, { fileName: '', fileType: '', uploadedBy: '' });
  pageNo.value = 1;
  reload();
}

function onDownload(row) {
  if (!row.id) {
    ElMessage.warning('当前文件没有可下载的文件标识');
    return;
  }
  // 后端返回受控的存储下载跳转，浏览器沿用既有会话与权限校验。
  window.open(`${API_BASE}/files/${row.id}/download`, '_blank');
}

const deletingIds = ref(new Set());
const isDeleting = (id) => id != null && deletingIds.value.has(String(id));
function setDeleting(id, value) {
  const next = new Set(deletingIds.value);
  if (value) next.add(String(id));
  else next.delete(String(id));
  deletingIds.value = next;
}
async function onDelete(row) {
  if (!row?.id) {
    ElMessage.warning('当前文件没有可删除的文件标识');
    return;
  }
  if (isDeleting(row.id)) return;
  setDeleting(row.id, true);
  try {
    await ElMessageBox.confirm(
      `确认删除文件「${row.fileName || row.name}」？删除后无法恢复。`,
      '确认删除文件',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    );
  } catch {
    setDeleting(row.id, false);
    return;
  }
  try {
    await deleteFile(row.id);
    ElMessage.success('文件已删除');
    await reload();
  } catch (error) {
    ElMessage.error(`删除失败：${error?.message || error}`);
  } finally {
    setDeleting(row.id, false);
  }
}

onMounted(reload);
</script>

<style lang="scss" scoped>
.files-table-panel { min-width: 0; }
.file-name { color: var(--color-text-strong); font-weight: 500; }
.mono { color: var(--color-text); font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace; font-size: 12px; }
.row-actions { align-items: center; display: flex; gap: var(--space-1); }
.error-state { background: var(--color-danger-bg); border-left: 3px solid var(--color-danger-fg); color: var(--color-danger-fg); font-size: 12px; line-height: 18px; margin-bottom: var(--space-3); padding: var(--space-2) var(--space-3); }
</style>
