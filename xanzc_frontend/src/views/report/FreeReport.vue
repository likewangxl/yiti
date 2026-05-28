<template>
  <div>
    <div class="page-h">
      <h1>自由报表</h1>
      <div class="actions">
        <el-button type="primary" @click="importDlg.show = true">导入 Excel</el-button>
      </div>
    </div>

    <div class="card-section">
      <el-form inline size="default">
        <el-form-item label="报表名称">
          <el-select v-model="keyword" clearable filterable placeholder="全部" style="width:220px"
                     @change="reload">
            <el-option v-for="n in reportNames" :key="n" :value="n" :label="n" />
          </el-select>
        </el-form-item>
        <el-form-item label="导入时间">
          <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD"
                          range-separator="~" start-placeholder="开始" end-placeholder="结束"
                          style="width:260px" @change="reload" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="reload">查询</el-button>
          <el-button @click="keyword = ''; dateRange = null; reload()">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card-section table">
      <el-table :data="pagedBatches" size="default" v-loading="loading" empty-text="暂无导入记录" stripe>
        <el-table-column prop="reportName" label="报表名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column label="导入人" width="120">
          <template #default="{row}">{{ row.uploaderName || row.uploaderEmpId || '-' }}</template>
        </el-table-column>
        <el-table-column label="导入时间" width="160">
          <template #default="{row}">{{ fmtTime(row.importTime) }}</template>
        </el-table-column>
        <el-table-column prop="rowCount" label="行数" width="80" align="right" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{row}">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small" effect="plain">
              {{ row.status === 'SUCCESS' ? '成功' : row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="$router.push(`/report/free/${row.id}`)">查看</el-button>
            <el-button link type="primary" size="small" @click="doDownload(row)">下载</el-button>
            <el-popconfirm :title="`确认删除「${row.reportName}」？数据将不可恢复。`" @confirm="doDelete(row)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="pgNo" v-model:page-size="pgSize" :page-sizes="[10,20,50]" :total="batches.length" background layout="total, sizes, prev, pager, next" />
      </div>
    </div>

    <!-- 导入弹框 -->
    <el-dialog v-model="importDlg.show" title="导入 Excel" width="500px">
      <el-form label-width="80px">
        <el-form-item label="报表名称">
          <el-input v-model="importDlg.reportName" placeholder="不填则默认为文件名" />
        </el-form-item>
        <el-form-item label="选择文件">
          <el-upload ref="importUploaderRef" drag action="#" :auto-upload="false" :show-file-list="true"
                     multiple :on-change="onFilePick" accept=".xlsx,.xls">
            <div style="padding:20px 0">点击或拖拽 .xlsx 到此处（支持多文件）</div>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="importDlg.uploading" @click="doImport">确认导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import {
  importFreeReport, listFreeReportBatches, downloadFreeReportFile, deleteFreeReportBatch
} from '@/api/report';

const batches = ref([]);
const pgNo = ref(1);
const pgSize = ref(20);
const pagedBatches = computed(() => batches.value.slice((pgNo.value - 1) * pgSize.value, pgNo.value * pgSize.value));
const loading = ref(false);
const keyword = ref('');
const dateRange = ref(null);
const allBatches = ref([]);
const reportNames = computed(() => [...new Set(allBatches.value.map(b => b.reportName).filter(Boolean))]);

function fmtTime(t) {
  if (!t) return '-';
  return String(t).replace('T', ' ').slice(0, 16);
}

async function reload() {
  loading.value = true;
  try {
    const params = {};
    if (keyword.value) params.keyword = keyword.value;
    if (dateRange.value?.[0]) params.dateFrom = dateRange.value[0];
    if (dateRange.value?.[1]) params.dateTo = dateRange.value[1];
    const r = await listFreeReportBatches(params);
    batches.value = Array.isArray(r) ? r : [];
    if (!params.keyword && !params.dateFrom) allBatches.value = batches.value;
  } catch { batches.value = []; }
  finally { loading.value = false; }
}

// 导入
const importDlg = ref({ show: false, reportName: '', files: [], uploading: false });
const importUploaderRef = ref(null);

function onFilePick(file, fileList) {
  importDlg.value.files = fileList.filter(f => f.raw).map(f => f.raw);
}

async function doImport() {
  if (!importDlg.value.files.length) return ElMessage.warning('请选择文件');
  importDlg.value.uploading = true;
  try {
    for (const file of importDlg.value.files) {
      await importFreeReport(importDlg.value.reportName.trim(), file);
    }
    ElMessage.success(`${importDlg.value.files.length} 个文件导入成功`);
    importDlg.value.show = false;
    importDlg.value.reportName = '';
    importDlg.value.files = [];
    importUploaderRef.value?.clearFiles();
    reload();
  } catch (e) {
    ElMessage.error(e?.message || '导入失败');
  } finally { importDlg.value.uploading = false; }
}

async function doDownload(row) {
  try {
    // 用原生 axios 绕过 interceptor，直接拿 blob
    const { default: axios } = await import('axios');
    const resp = await axios.get(`/api/reports/free/batches/${row.id}/download-file`, {
      responseType: 'blob',
      withCredentials: true
    });
    const blob = new Blob([resp.data]);
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = row.fileName || row.reportName || '报表.xlsx';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  } catch (e) {
    ElMessage.error('下载失败');
  }
}

async function doDelete(row) {
  try {
    await deleteFreeReportBatch(row.id);
    ElMessage.success('已删除');
    reload();
  } catch { ElMessage.error('删除失败'); }
}

onMounted(reload);
</script>

<style lang="scss" scoped>
.page-h {
  display: flex; align-items: baseline; gap: 12px; margin-bottom: 12px;
  h1 { font-size: 18px; font-weight: 600; }
  .actions { margin-left: auto; display: flex; gap: 8px; }
}
.table { padding: 0; padding-bottom: 12px; }
.pager { display: flex; justify-content: flex-end; padding: 12px 0; }
</style>
