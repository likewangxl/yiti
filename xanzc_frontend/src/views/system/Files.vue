<template>
  <div>
    <div class="page-h">
      <h1>文件管理</h1>
      <div class="actions"><el-button type="primary">📤 上传文件</el-button></div>
    </div>

    <div class="card-section">
      <el-form :inline="true" size="default">
        <el-form-item label="文件名"><el-input v-model="f.fileName" clearable style="width:200px" placeholder="模糊匹配" /></el-form-item>
        <el-form-item label="文件类型"><el-input v-model="f.fileType" clearable style="width:140px" placeholder="如 pdf/xlsx" /></el-form-item>
        <el-form-item label="上传人"><el-input v-model="f.uploadedBy" clearable style="width:160px" placeholder="工号" /></el-form-item>
        <el-form-item><el-button type="primary" @click="reload">查询</el-button></el-form-item>
        <el-form-item><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </div>

    <div class="card-section">
      <el-table :data="rows" size="default" empty-text="暂无文件">
        <el-table-column label="文件名" min-width="280">
          <template #default="{row}">📎 {{ row.fileName || row.name }}</template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{row}"><el-tag :class="typeCls(row.fileType || row.type)" effect="plain">{{ (row.fileType || row.type || '-').toUpperCase() }}</el-tag></template>
        </el-table-column>
        <el-table-column label="大小" width="110">
          <template #default="{row}">{{ fmtSize(row.fileSize ?? row.size) }}</template>
        </el-table-column>
        <el-table-column label="MD5" width="120">
          <template #default="{row}"><code class="mono" v-if="row.md5Hash">{{ row.md5Hash.slice(0,10) }}…</code><span v-else>—</span></template>
        </el-table-column>
        <el-table-column label="上传人" width="140">
          <template #default="{row}">{{ row.uploadedBy || row.by || '—' }}</template>
        </el-table-column>
        <el-table-column label="上传时间" width="180">
          <template #default="{row}">{{ row.uploadedTime || row.time || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="onDownload(row)">下载</el-button> |
            <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="display:flex;justify-content:flex-end;padding:12px 0">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="reload"
          @size-change="reload"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { sysFiles } from '@/mock';
import { listFiles, deleteFile } from '@/api/system';
import { API_BASE } from '@/api/http';

const rows = ref(sysFiles);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(20);
const f = reactive({ fileName: '', fileType: '', uploadedBy: '' });

const typeCls = (t) => {
  const k = (t || '').toUpperCase();
  if (['PDF'].includes(k)) return 'tag-danger';
  if (['XLSX','XLS','CSV'].includes(k)) return 'tag-success';
  if (['JPG','JPEG','PNG','GIF'].includes(k)) return 'tag-warning';
  return 'tag-info';
};

function fmtSize(n) {
  if (n == null) return '—';
  const v = Number(n);
  if (v < 1024) return v + ' B';
  if (v < 1024 * 1024) return (v / 1024).toFixed(1) + ' KB';
  if (v < 1024 * 1024 * 1024) return (v / 1024 / 1024).toFixed(1) + ' MB';
  return (v / 1024 / 1024 / 1024).toFixed(2) + ' GB';
}

async function reload() {
  try {
    const r = await listFiles({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      fileName: f.fileName || undefined,
      fileType: f.fileType || undefined,
      uploadedBy: f.uploadedBy || undefined
    });
    if (Array.isArray(r)) {
      rows.value = r;
      // 服务端 PageResult 已被 unwrapPage 抽成数组，total 拿不到 —— 用本页长度估算
      // 准确 total 需要在 system.js 里另存一份；目前先用一个简单兜底
      total.value = Math.max(total.value, (pageNo.value - 1) * pageSize.value + r.length + (r.length === pageSize.value ? 1 : 0));
    }
  } catch {}
}
function reset() {
  f.fileName = ''; f.fileType = ''; f.uploadedBy = '';
  pageNo.value = 1;
  reload();
}

function onDownload(row) {
  if (!row.id) { ElMessage.warning('mock 数据无法下载'); return; }
  // yiti 下载是 302 → MinIO 预签名 URL，浏览器跟随
  window.open(`${API_BASE}/files/${row.id}/download`, '_blank');
}
async function onDelete(row) {
  if (!row.id) { ElMessage.warning('mock 数据无法删除'); return; }
  try { await ElMessageBox.confirm(`确认删除文件 ${row.fileName}？`, '高危', { type: 'warning' }); } catch { return; }
  try { await deleteFile(row.id); ElMessage.success('已删除'); reload(); } catch {}
}

onMounted(reload);
</script>

<style lang="scss" scoped>
.mono { font-family: ui-monospace, monospace; font-size: 12px; }
</style>
