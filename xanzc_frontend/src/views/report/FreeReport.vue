<template>
  <div>
    <div class="page-h">
      <h1>KPI/积分自由报表</h1>
      <div class="actions">
        <el-button @click="batchDlg.show = true">查看最近导入文件信息</el-button>
        <el-button type="primary" @click="importDlg.show = true">导入 Excel</el-button>
      </div>
    </div>

    <!-- 查询条件 -->
    <div class="card-section">
      <el-form inline size="default">
        <el-form-item label="报表">
          <el-select v-model="currentBatchId" placeholder="选择报表批次" style="width:260px"
                     @change="onBatchChange" filterable>
            <el-option v-for="b in batches" :key="b.id" :value="b.id"
                       :label="`${b.reportName} (${fmtTime(b.importTime)})`" />
          </el-select>
        </el-form-item>
        <el-form-item label="姓名搜索">
          <el-input v-model="keyword" placeholder="姓名/工号" clearable style="width:180px"
                    @keyup.enter="reload" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="reload">查询</el-button>
          <el-button @click="keyword = ''; reload()">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 动态表格 -->
    <div class="card-section table">
      <el-table :data="rows" size="default" v-loading="loading" empty-text="暂无数据" stripe border
                max-height="520" style="width:100%">
        <el-table-column v-if="columns.length > 0" :prop="columns[0].key" :label="columns[0].label"
                         width="140" fixed />
        <el-table-column v-if="columns.length > 1" :prop="columns[1].key" :label="columns[1].label"
                         width="140" fixed />
        <el-table-column v-for="col in dynamicCols" :key="col.key" :prop="col.key" :label="col.label"
                         min-width="120" show-overflow-tooltip />
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :page-sizes="[20, 50, 100]"
          :total="total"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="reload"
          @current-change="reload"
        />
      </div>
    </div>

    <!-- 导入弹框 -->
    <el-dialog v-model="importDlg.show" title="导入 Excel" width="500px">
      <el-form label-width="80px">
        <el-form-item label="报表名称">
          <el-input v-model="importDlg.reportName" placeholder="如：2026年5月KPI积分" />
        </el-form-item>
        <el-form-item label="选择文件">
          <el-upload
            ref="importUploaderRef"
            drag action="#" :auto-upload="false" :show-file-list="false" :limit="1"
            :on-change="onImportFilePick" accept=".xlsx,.xls">
            <div style="padding:20px 0">
              <div v-if="importDlg.file">已选：{{ importDlg.file.name }}</div>
              <div v-else>点击或拖拽 .xlsx 到此处</div>
            </div>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="importDlg.uploading" @click="doImport">确认导入</el-button>
      </template>
    </el-dialog>

    <!-- 导入批次列表弹框 -->
    <el-dialog v-model="batchDlg.show" title="最近导入文件信息" width="700px" @opened="loadBatches">
      <el-table :data="batches" size="small" v-loading="batchDlg.loading" max-height="400" empty-text="暂无导入记录">
        <el-table-column prop="reportName" label="报表名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="fileName" label="文件名" min-width="160" show-overflow-tooltip />
        <el-table-column label="导入人" width="100">
          <template #default="{row}">{{ row.uploaderName || row.uploaderEmpId || '-' }}</template>
        </el-table-column>
        <el-table-column label="导入时间" width="150">
          <template #default="{row}">{{ fmtTime(row.importTime) }}</template>
        </el-table-column>
        <el-table-column prop="rowCount" label="行数" width="60" align="right" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="doDownload(row)">下载</el-button>
            <el-popconfirm :title="`确认删除批次 ${row.reportName}？`" @confirm="doDeleteBatch(row)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import {
  importFreeReport, queryFreeReportData, getFreeReportColumns,
  listFreeReportBatches, downloadFreeReportFile, deleteFreeReportBatch
} from '@/api/report';

const batches = ref([]);
const currentBatchId = ref('');
const columns = ref([]);
const rows = ref([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(20);
const keyword = ref('');
const loading = ref(false);

const dynamicCols = computed(() => columns.value.slice(2));

function fmtTime(t) {
  if (!t) return '-';
  return String(t).replace('T', ' ').slice(0, 16);
}

async function loadBatches() {
  batchDlg.loading = true;
  try {
    const r = await listFreeReportBatches();
    batches.value = Array.isArray(r) ? r : [];
  } catch { batches.value = []; }
  finally { batchDlg.loading = false; }
}

async function onBatchChange(batchId) {
  if (!batchId) return;
  try {
    const cols = await getFreeReportColumns(batchId);
    columns.value = Array.isArray(cols) ? cols : [];
  } catch { columns.value = []; }
  pageNo.value = 1;
  reload();
}

async function reload() {
  if (!currentBatchId.value) return;
  loading.value = true;
  try {
    const r = await queryFreeReportData({
      batchId: currentBatchId.value,
      keyword: keyword.value || undefined,
      pageNo: pageNo.value,
      pageSize: pageSize.value
    });
    rows.value = r?.records || [];
    total.value = r?.total || 0;
  } catch {
    rows.value = [];
    total.value = 0;
  } finally { loading.value = false; }
}

// 导入
const importDlg = ref({ show: false, reportName: '', file: null, uploading: false });
const importUploaderRef = ref(null);

function onImportFilePick(file) {
  if (file?.raw) importDlg.value.file = file.raw;
}

async function doImport() {
  if (!importDlg.value.reportName?.trim()) return ElMessage.warning('请输入报表名称');
  if (!importDlg.value.file) return ElMessage.warning('请选择文件');
  importDlg.value.uploading = true;
  try {
    await importFreeReport(importDlg.value.reportName.trim(), importDlg.value.file);
    ElMessage.success('导入成功');
    importDlg.value.show = false;
    importDlg.value.reportName = '';
    importDlg.value.file = null;
    importUploaderRef.value?.clearFiles();
    await loadBatches();
    if (batches.value.length) {
      currentBatchId.value = batches.value[0].id;
      onBatchChange(currentBatchId.value);
    }
  } catch (e) {
    ElMessage.error(e?.message || '导入失败');
  } finally { importDlg.value.uploading = false; }
}

// 批次列表弹框
const batchDlg = ref({ show: false, loading: false });

async function doDownload(row) {
  try {
    const r = await downloadFreeReportFile(row.id);
    if (r?.url) window.open(r.url, '_blank');
    else ElMessage.warning('无下载链接');
  } catch { ElMessage.error('下载失败'); }
}

async function doDeleteBatch(row) {
  try {
    await deleteFreeReportBatch(row.id);
    ElMessage.success('已删除');
    await loadBatches();
    if (currentBatchId.value === row.id) {
      currentBatchId.value = batches.value[0]?.id || '';
      if (currentBatchId.value) onBatchChange(currentBatchId.value);
      else { rows.value = []; columns.value = []; total.value = 0; }
    }
  } catch { ElMessage.error('删除失败'); }
}

onMounted(async () => {
  await loadBatches();
  if (batches.value.length) {
    currentBatchId.value = batches.value[0].id;
    onBatchChange(currentBatchId.value);
  }
});
</script>

<style lang="scss" scoped>
.page-h {
  display: flex; align-items: baseline; gap: 12px; margin-bottom: 12px;
  h1 { font-size: 18px; font-weight: 600; }
  .actions { margin-left: auto; display: flex; gap: 8px; }
}
.table { padding: 0; padding-bottom: 12px; }
.pager { padding: 12px 20px; display: flex; justify-content: flex-end; }
</style>
