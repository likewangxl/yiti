<template>
  <div>
    <div class="page-h">
      <h1>自由报表</h1>
      <div class="actions">
        <el-button v-if="isOperator" @click="downloadTemplate">下载模板</el-button>
        <el-button v-if="isOperator" type="primary" @click="importDlg.show = true">导入 Excel</el-button>
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
            <el-tag :type="row.status === 'DISABLED' ? 'info' : (row.status === 'SUCCESS' ? 'success' : 'danger')" size="small" effect="plain">
              {{ row.status === 'DISABLED' ? '已禁用' : (row.status === 'SUCCESS' ? '成功' : row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="$router.push(`/report/free/${row.id}`)">查看</el-button>
            <el-button link type="primary" size="small" @click="doDownload(row)">下载</el-button>
            <template v-if="isOperator">
              <el-button v-if="row.status === 'DISABLED'" link type="success" size="small" @click="doEnable(row)">启用</el-button>
              <el-popconfirm v-else :title="`禁用后其他人将无法查看「${row.reportName}」，确认禁用？`" @confirm="doDisable(row)">
                <template #reference>
                  <el-button link type="warning" size="small">禁用</el-button>
                </template>
              </el-popconfirm>
              <el-popconfirm :title="`确认删除「${row.reportName}」？数据将不可恢复。`" @confirm="doDelete(row)">
                <template #reference>
                  <el-button link type="danger" size="small">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
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
            <div style="padding:20px 0">点击或拖拽 .xlsx 到此处（支持多文件，单个最大 50MB）</div>
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
  importFreeReport, listFreeReportBatches, downloadFreeReportFile, deleteFreeReportBatch,
  disableFreeReportBatch, enableFreeReportBatch
} from '@/api/report';
import { useUserStore } from '@/stores/user';

// 仅 系统管理员(SYS_ADMIN) / 资财部负责人(FINANCE_LEADER) / 自由报表操作人(R_2FAB45A1)：
// 可导入/禁用/启用/删除。只看「当前激活角色」（current-user 返回全部角色但只给激活的打 primary 标记，
// 切换角色后按钮随之变化；后端同样按激活角色判定）。
const userStore = useUserStore();
const OPERATOR_ROLES = ['R_2FAB45A1', 'SYS_ADMIN', 'FINANCE_LEADER'];
const isOperator = computed(() => {
  const roles = userStore.user?.roles || [];
  // 优先用 activeRoleId 锁定当前激活角色，再退回 primary 标记
  const activeId = userStore.activeRoleId;
  const active = (activeId && roles.find(r => r.roleId === activeId))
              || roles.find(r => r.primary);
  return !!active && OPERATOR_ROLES.includes(active.roleCode);
});

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

// 下载导入模板：只含「工号、姓名」两列表头（顺序须与后端导入校验一致——第1列工号、第2列姓名）
async function downloadTemplate() {
  try {
    const XLSX = await import('xlsx');
    const ws = XLSX.utils.aoa_to_sheet([['工号', '姓名']]);
    ws['!cols'] = [{ wch: 18 }, { wch: 18 }];
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, '导入模板');
    XLSX.writeFile(wb, '自由报表导入模板.xlsx');
  } catch (e) {
    ElMessage.error('模板下载失败：' + (e?.message || e));
  }
}

// 导入
const importDlg = ref({ show: false, reportName: '', files: [], uploading: false });
const importUploaderRef = ref(null);

const MAX_IMPORT_BYTES = 50 * 1024 * 1024; // 单文件 50MB，与后端 multipart/FileService 对齐

function onFilePick(file, fileList) {
  // 上传前本地拦截超 50MB 的文件：提示并从待上传列表移除，不发请求
  if (file.size > MAX_IMPORT_BYTES) {
    ElMessage.error(`文件「${file.name}」超过 50MB，无法导入`);
    const idx = fileList.indexOf(file);
    if (idx > -1) fileList.splice(idx, 1);
  }
  importDlg.value.files = fileList
    .filter(f => f.raw && f.raw.size <= MAX_IMPORT_BYTES)
    .map(f => f.raw);
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

async function doDisable(row) {
  try {
    await disableFreeReportBatch(row.id);
    ElMessage.success('已禁用，其他人将无法查看');
    reload();
  } catch (e) { ElMessage.error('禁用失败：' + (e?.message || '')); }
}

async function doEnable(row) {
  try {
    await enableFreeReportBatch(row.id);
    ElMessage.success('已启用');
    reload();
  } catch (e) { ElMessage.error('启用失败：' + (e?.message || '')); }
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
.pager :deep(.el-pagination) { flex-wrap: wrap; row-gap: 8px; justify-content: flex-end; }
</style>
