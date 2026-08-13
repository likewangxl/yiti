<template>
<main v-bp-overflow-tooltip class="bp-crud perf-import-page" aria-labelledby="perf-import-page-title" :aria-busy="loading || uploading ? 'true' : 'false'">
    <header class="page-h">
      <PageTitle id="perf-import-page-title"><span class="sub">指标结果 / KPI 结果 · 文件导入与批次追踪</span></PageTitle>
      <div class="actions action-group" role="group" aria-label="绩效导入操作">
        <el-button :loading="loading" :disabled="loading || uploading" @click="reload">刷新</el-button>
      </div>
    </header>

    <section class="card-section data-panel import-config" aria-label="绩效文件导入" aria-labelledby="perf-import-form-heading">
      <div class="toolbar">
        <div>
          <h2 id="perf-import-form-heading" class="section-title">导入数据</h2>
          <p class="hint">选择数据类型、日期和文件后提交；提交结果会生成可追踪的批次号。</p>
        </div>
      </div>
      <el-form label-width="100px" size="default" aria-label="绩效文件导入表单">
        <el-form-item label="导入类型">
          <el-radio-group v-model="kind">
            <el-radio value="METRIC_RESULT">指标结果</el-radio>
            <el-radio value="KPI_SCORE">KPI 结果</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="数据日期">
          <el-date-picker v-model="date" type="date" value-format="YYYY-MM-DD" style="width:260px" />
        </el-form-item>
        <el-form-item label="方案" v-if="kind !== 'METRIC_RESULT'">
          <el-select v-model="plan" style="width:260px" clearable placeholder="选择启用的 KPI 方案">
            <el-option v-for="s in schemes" :key="s.schemeCode" :value="s.schemeCode"
                       :label="s.schemeName ? `${s.schemeName}（${s.schemeCode}）` : s.schemeCode" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button aria-label="下载当前导入类型模板" @click="downloadTpl">下载导入模板</el-button>
        </el-form-item>
      </el-form>

      <!-- 关闭 element-plus 自动上传，自己控制（要带 importType 查询参 + 进度回调）-->
      <el-upload
        ref="uploaderRef"
        drag
        action="#"
        :auto-upload="false"
        :show-file-list="false"
        :limit="1"
        :on-change="onFilePick"
        accept=".xlsx,.xls"
        style="margin-top:12px" :disabled="uploading" aria-label="选择绩效导入文件">
        <el-icon aria-hidden="true" class="upload-icon"><upload-filled /></el-icon>
        <div class="el-upload__text">
          点击或拖拽 <em>.xlsx</em> 到此处
        </div>
        <template #tip><div class="el-upload__tip">仅支持 xlsx，最大 20MB；上传后系统将同步启动导入并返回批次号</div></template>
      </el-upload>

      <div v-if="picked" class="picked">
        已选择：<strong>{{ picked.name }}</strong>（{{ fmtSize(picked.size) }}）
        <el-button type="primary" :loading="uploading" :disabled="uploading" @click="onUpload(true)" style="margin-left:12px">立即上传</el-button>
        <el-button :disabled="uploading" @click="picked = null; uploaderRef?.clearFiles()">取消</el-button>
      </div>
    </section>

    <section class="card-section data-panel" aria-label="最近导入批次" aria-labelledby="perf-import-table-heading"
      aria-describedby="perf-import-table-state" :aria-busy="loading ? 'true' : 'false'">
      <div class="toolbar">
        <div>
          <h2 id="perf-import-table-heading" class="section-title">最近导入</h2>
          <p class="hint">批次状态会随后台处理更新；失败批次可下载错误明细后重试。</p>
        </div>
        <p id="perf-import-table-state" class="table-state" role="status" aria-live="polite">{{ importsState }}</p>
      </div>
      <div v-if="loadError" class="table-error" role="alert">
        <span>{{ loadError }}</span>
        <el-button link type="primary" @click="reload">重新加载</el-button>
      </div>
      <el-table :data="rows" size="default" :empty-text="loadError ? '加载失败，请重新加载' : '暂无导入记录'" v-loading="loading"
        aria-labelledby="perf-import-table-heading" aria-describedby="perf-import-table-state">
        <el-table-column prop="batchId" label="批次号" width="220">
          <template #default="{row}"><code class="mono">{{ row.batchId || row.id }}</code></template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{row}"><el-tag class="tag-info" effect="plain">{{ typeLabel(row.type) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="file" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="uploader" label="导入人" width="140" />
        <el-table-column label="有效/总" width="120" align="right">
          <template #default="{row}">{{ row.valid ?? 0 }}/{{ row.total ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{row}">
            <el-tag :class="statusCls(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="time" label="时间" width="170" :formatter="fmtDateTimeCol" />
        <el-table-column label="操作" class-name="operation-cell" width="360" fixed="right">
          <template #default="{row}">
            <BpAdaptiveRowActions>
              <template #primary><el-button link type="primary" size="small" @click="onRefreshOne(row)">刷新</el-button></template>
              <template #expanded>
                <el-button link type="primary" size="small" :disabled="!row.sourceObjectKey" :title="row.sourceObjectKey ? '' : '无源文件'" @click="onDownloadSource(row)">下载文件</el-button>
                <el-button link type="primary" size="small" @click="onDownloadErrors(row)">下载错误</el-button>
                <el-button v-if="row.status === 'FAILED'" link type="primary" size="small" :disabled="retryingBatchId === String(row.batchId || row.id)" @click="onRetry(row)">重试</el-button>
                <el-button link type="danger" size="small" :disabled="deletingBatchId === String(row.batchId || row.id)" @click="confirmDelete(row)">删除</el-button>
              </template>
              <template #compact><el-dropdown trigger="click" popper-class="bp-crud-menu"><el-button link size="small" aria-label="更多导入批次操作">更多</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item :disabled="!row.sourceObjectKey" :title="row.sourceObjectKey ? '' : '无源文件'" @click="onDownloadSource(row)">下载文件</el-dropdown-item><el-dropdown-item @click="onDownloadErrors(row)">下载错误</el-dropdown-item><el-dropdown-item v-if="row.status === 'FAILED'" :disabled="retryingBatchId === String(row.batchId || row.id)" @click="onRetry(row)">重试</el-dropdown-item><el-dropdown-item divided class="danger-item" :disabled="deletingBatchId === String(row.batchId || row.id)" @click="confirmDelete(row)">删除</el-dropdown-item></el-dropdown-menu></template></el-dropdown></template>
            </BpAdaptiveRowActions>
          </template>
        </el-table-column>
      </el-table>
      <nav class="pager" aria-label="导入批次分页">
        <el-pagination v-model:current-page="pgNo" v-model:page-size="pgSize" :page-sizes="[10,20,50]" :total="total" background layout="total, sizes, prev, pager, next" />
      </nav>
    </section>
  </main>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { fmtDateTimeCol } from '@/utils/datetime';
import { UploadFilled } from '@element-plus/icons-vue';
import BpAdaptiveRowActions from '@/components/BpAdaptiveRowActions.vue';
import {
  listImports, uploadImportFile,
  refreshImportStatus, retryImport, deleteImportBatch, downloadImportErrors,
  downloadImportSourceFile, listKpiRules
} from '@/api/perf';

const kind = ref('METRIC_RESULT');
const date = ref(new Date().toISOString().slice(0, 10));
const plan = ref('');
const rows = ref([]);
// 指标结果导入不需要方案：选中「指标结果」时方案项隐藏且值清空（默认空）
watch(kind, (k) => {
  if (k === 'METRIC_RESULT') plan.value = '';
}, { immediate: true });
const loading = ref(false);
const loadError = ref('');
const pgNo = ref(1);
const pgSize = ref(20);
const total = ref(0);
// 服务端分页：rows 即当前页数据，分页变化重新拉取
watch([pgNo, pgSize], () => reload());

const TYPE_LABEL = { METRIC_RESULT: '指标结果', KPI_SCORE: 'KPI 结果', ALLOC: 'KPI 结果', TARGET: '目标值' };
const typeLabel = (t) => TYPE_LABEL[t] || t || '-';
const STATUS_LABEL = {
  CREATED: '已创建', RUNNING: '导入中', PROCESSING: '导入中',
  SUCCESS: '已完成', FAILED: '失败', PENDING: '排队中'
};
const statusLabel = (s) => STATUS_LABEL[s] || s || '-';
const statusCls = (s) => ({
  SUCCESS: 'tag-success',
  FAILED: 'tag-danger',
  RUNNING: 'tag-warning',
  PROCESSING: 'tag-warning',
  CREATED: 'tag-info',
  PENDING: 'tag-info'
}[s] || 'tag-info');
const fmtSize = (n) => n ? (n > 1024*1024 ? (n/1024/1024).toFixed(2) + ' MB' : (n/1024).toFixed(0) + ' KB') : '-';
const importsState = computed(() => loading.value
  ? '导入批次加载中'
  : loadError.value
    ? '导入批次加载失败'
    : rows.value.length
      ? `共 ${total.value} 条导入批次`
      : '暂无导入批次');

async function reload() {
  loading.value = true;
  loadError.value = '';
  try {
    const r = await listImports({ pageNo: pgNo.value, pageSize: pgSize.value });
    const records = Array.isArray(r) ? r : (r?.records || []);
    rows.value = records.map(b => ({
      batchId: b.id,
      id: b.id,
      type: b.importType,
      file: b.fileName,
      fileName: b.fileName,
      sourceObjectKey: b.sourceObjectKey,
      uploader: b.createdBy,
      valid: b.successRows ?? 0,
      total: b.totalRows ?? 0,
      status: b.status,
      time: b.createdTime
    }));
    total.value = Array.isArray(r) ? records.length : (r?.total ?? records.length);
  } catch (error) {
    rows.value = [];
    total.value = 0;
    loadError.value = `导入批次加载失败：${error?.message || '请稍后重试'}`;
  } finally { loading.value = false; }
}

// 下载源文件（旧数据无 sourceObjectKey 时按钮已置灰，这里再兜底）
async function onDownloadSource(row) {
  if (!row.sourceObjectKey) { ElMessage.warning('该批次无源文件'); return; }
  try {
    await downloadImportSourceFile(row.batchId || row.id, row.fileName);
  } catch (e) {
    ElMessage.error(e?.message || '下载失败');
  }
}

// 方案下拉：只取状态=启用(ACTIVE)的 KPI 方案
const schemes = ref([]);
async function loadSchemes() {
  try {
    const raw = await listKpiRules({ status: 'ACTIVE', pageSize: 100 });
    schemes.value = Array.isArray(raw) ? raw : (raw?.records || []);
  } catch { schemes.value = []; }
}

// === 上传 ===
const uploaderRef = ref(null);
const picked = ref(null);
const uploading = ref(false);
const retryingBatchId = ref('');
const deletingBatchId = ref('');
function onFilePick(file) {
  // file 是 element-plus 包装：{ name, size, raw: File }
  if (!file?.raw) return;
  if (file.raw.size > 20 * 1024 * 1024) {
    ElMessage.warning('文件超过 20MB 限制');
    uploaderRef.value?.clearFiles();
    return;
  }
  picked.value = file.raw;
}
async function onUpload(archive = true) {
  if (uploading.value) return;
  if (!picked.value) return ElMessage.warning('请先选择文件');
  // V1.12 微调：METRIC_RESULT 必填 dataDate（前端 picker 默认今天），缺失提前拦截避免后端 422
  if (kind.value === 'METRIC_RESULT' && !date.value) {
    return ElMessage.warning('请选择数据日期');
  }
  // KPI 结果导入：数据日期 + KPI 方案均必填（落库 PERF_KPI_SCORE 的 data_date / scheme_code）
  if (kind.value === 'KPI_SCORE') {
    if (!date.value) return ElMessage.warning('请选择数据日期');
    if (!plan.value) return ElMessage.warning('请选择 KPI 方案');
  }
  uploading.value = true;
  try {
    const result = await uploadImportFile(kind.value, picked.value, date.value,
      { uploader: '当前用户', schemeCode: plan.value, archiveSource: archive });
    const { batchId, errorRows = 0, errorSummary } = result || {};
    if (errorRows > 0) {
      // 2026-05-19 微调：行级校验失败的批次（例如机构号不在 EXT_ORG_INFO）必须明显告警，
      // 不能再让用户以为"已提交"=数据都进库了。errorSummary 由后端直接拼好供前端展示
      ElMessage({
        type: 'warning',
        dangerouslyUseHTMLString: false,
        showClose: true,
        duration: 0,
        message: `批次号 ${batchId} 已提交，但有 ${errorRows} 行未入库：\n${errorSummary || '详见错误明细'}`,
      });
    } else {
      ElMessage.success(`已提交，批次号 ${batchId}`);
    }
    picked.value = null;
    uploaderRef.value?.clearFiles();
    await reload();
    // 自动拉一次最新状态
    setTimeout(() => onRefreshOne({ batchId }), 1500);
  } catch {
    ElMessage.error('上传失败');
  } finally { uploading.value = false; }
}

// === 行操作 ===
async function onRefreshOne(row) {
  await refreshImportStatus(row.batchId || row.id);
  reload();
}
async function onDownloadErrors(row) {
  try {
    await downloadImportErrors(row.batchId || row.id);
  } catch { ElMessage.error('下载错误明细失败'); }
}
async function onRetry(row) {
  const batchId = row?.batchId || row?.id;
  const key = String(batchId || '');
  if (!key || retryingBatchId.value === key) return;
  retryingBatchId.value = key;
  try {
    await retryImport(batchId);
    ElMessage.success('已触发重试');
    reload();
  } catch { ElMessage.error('重试失败'); }
  finally { retryingBatchId.value = ''; }
}
async function onDelete(row) {
  const batchId = row?.batchId || row?.id;
  const key = String(batchId || '');
  if (!key || deletingBatchId.value === key) return;
  deletingBatchId.value = key;
  try {
    await deleteImportBatch(batchId, '前端列表删除');
    ElMessage.success('已删除');
    reload();
  } catch { ElMessage.error('删除失败'); }
  finally { deletingBatchId.value = ''; }
}

async function confirmDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除批次 ${row.batchId}？`, '删除确认', {
      type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消'
    });
  } catch { return; }
  await onDelete(row);
}

async function downloadTpl() {
  // 指标定义 / 指标结果：直接下载 public/templates 下的真实模板文件
  const STATIC_TPL = {
    METRIC_DEF:    { url: '/templates/指标表上传模板.xlsx',  name: '指标表上传模板.xlsx' },
    METRIC_RESULT: { url: '/templates/指标结果模板.xlsx', name: '指标结果模板.xlsx' },
    KPI_SCORE:     { url: '/templates/KPI结果导入模板.xlsx', name: 'KPI结果导入模板.xlsx' }
  };
  const staticTpl = STATIC_TPL[kind.value];
  if (staticTpl) {
    window.open(staticTpl.url, '_blank');
    return;
  }

  // 其他类型：前端用 SheetJS 动态生成
  const TPL = {
    ALLOC: {
      headers: ['方案编码', '对象编号', '得分', '周期键'],
      sheet:   'KPI结果',
      file:    'KPI结果导入模板'
    },
    TARGET: {
      headers: ['方案编码', '对象类型', '对象编号', '指标编码', '目标值', '周期键'],
      sheet:   '目标值',
      file:    '目标值导入模板'
    }
  }[kind.value];
  if (!TPL) return ElMessage.warning('未识别的导入类型');

  try {
    const XLSX = await import('xlsx');
    const ws = XLSX.utils.aoa_to_sheet([TPL.headers]);
    ws['!cols'] = TPL.headers.map(h => ({ wch: Math.max(12, h.length * 2 + 2) }));
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, TPL.sheet);
    XLSX.writeFile(wb, `${TPL.file}.xlsx`);
    ElMessage.success(`已下载 ${TYPE_LABEL[kind.value]} .xlsx 模板`);
  } catch (e) {
    ElMessage.error('生成模板失败：' + (e?.message || e));
  }
}

onMounted(() => { reload(); loadSchemes(); });
</script>

<style lang="scss" scoped>
.page-h h1 .sub { font-size: 13px; color: $text-3; margin-left: 12px; font-weight: 400; }
.hint { color: var(--color-text-muted); font-size: 12px; margin: 4px 0 0; }
.toolbar { align-items: flex-start; display: flex; justify-content: space-between; gap: var(--space-4); padding: 0 0 var(--space-3); }
.section-title { color: var(--color-text-strong); font-size: 16px; font-weight: 600; line-height: 24px; margin: 0; }
.table-state { color: var(--color-text-muted); font-size: 12px; margin: 2px 0 0; white-space: nowrap; }
.table-error { align-items: center; background: var(--color-danger-bg); border-left: 3px solid var(--color-danger-fg); color: var(--color-danger-fg); display: flex; font-size: 12px; gap: var(--space-3); justify-content: space-between; margin-bottom: var(--space-3); padding: var(--space-2) var(--space-3); }
.picked { align-items: center; background: var(--color-surface-soft); border: 1px solid var(--color-border); border-radius: var(--radius-control); display: flex; flex-wrap: wrap; gap: var(--space-2); margin-top: var(--space-3); padding: var(--space-3) var(--space-4); font-size: 13px; }
.upload-icon { color: var(--color-brand-500); font-size: 48px; }
.empty-tip { color: var(--color-text-muted); font-size: 12px; padding: 10px 16px; }
.mono { font-family: ui-monospace, monospace; font-size: 12px; }
.pager { display: flex; justify-content: flex-end; padding: 12px 0; }
.pager :deep(.el-pagination) { flex-wrap: wrap; row-gap: 8px; justify-content: flex-end; }
:where(.perf-import-page) :deep(.el-upload-dragger) { border-color: var(--color-border-strong); border-radius: var(--radius-control); }
:where(.perf-import-page) :deep(.el-upload-dragger:hover) { border-color: var(--color-brand-500); }
@media (prefers-reduced-motion: reduce) {
  :where(.perf-import-page) :deep(*) { transition-duration: 0.01ms !important; animation-duration: 0.01ms !important; }
}
</style>
