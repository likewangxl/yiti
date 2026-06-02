<template>
  <div>
    <div class="page-h">
      <h1>数据导入 <span class="sub">指标结果 / KPI 结果</span></h1>
      <div class="actions">
        <el-button @click="reload">刷新</el-button>
      </div>
    </div>

    <div class="card-section">
      <el-form label-width="100px" size="default">
        <el-form-item label="导入类型">
          <el-radio-group v-model="kind">
            <el-radio value="METRIC_RESULT">指标结果</el-radio>
            <el-radio value="ALLOC">KPI 结果</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="数据日期">
          <el-date-picker v-model="date" type="date" value-format="YYYY-MM-DD" style="width:260px" />
        </el-form-item>
        <el-form-item label="方案" v-if="kind !== 'METRIC_RESULT'">
          <el-select v-model="plan" style="width:260px">
            <el-option value="2026Q2" label="2026Q2 KPI" />
            <el-option value="2026Q1" label="2026Q1 KPI" />
            <el-option value="2025Y"  label="2025Y KPI"  />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="downloadTpl">📥 下载导入模板</el-button>
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
        style="margin-top:12px">
        <el-icon style="font-size:48px;color:#1E5BBA"><upload-filled /></el-icon>
        <div class="el-upload__text">
          点击或拖拽 <em>.xlsx</em> 到此处
        </div>
        <template #tip><div class="el-upload__tip">仅支持 xlsx，最大 20MB；上传后系统将同步启动导入并返回批次号</div></template>
      </el-upload>

      <div v-if="picked" class="picked">
        已选择：<strong>{{ picked.name }}</strong>（{{ fmtSize(picked.size) }}）
        <el-button type="primary" :loading="uploading" @click="onUpload" style="margin-left:12px">立即上传</el-button>
        <el-button @click="picked = null; uploaderRef?.clearFiles()">取消</el-button>
      </div>
    </div>

    <div class="card-section">
      <div class="section-title">最近导入</div>
      <el-table :data="pagedRows" size="default" empty-text="暂无导入记录" v-loading="loading">
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
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="onRefreshOne(row)">刷新</el-button>
            <el-button link type="primary" size="small" @click="onDownloadErrors(row)">下载错误</el-button>
            <el-button v-if="row.status === 'FAILED'" link type="primary" size="small" @click="onRetry(row)">重试</el-button>
            <el-popconfirm
              :title="`确认删除批次 ${row.batchId}？`"
              @confirm="onDelete(row)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="pgNo" v-model:page-size="pgSize" :page-sizes="[10,20,50]" :total="rows.length" background layout="total, sizes, prev, pager, next" />
      </div>
      <div class="empty-tip">
        ⓘ 后端暂未提供"全局批次列表"接口；本表仅展示当前浏览器最近 50 次本地上传记录（点"刷新"可拉取每条最新状态）。
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { fmtDateTimeCol } from '@/utils/datetime';
import { UploadFilled } from '@element-plus/icons-vue';
import {
  listImports, uploadImportFile,
  refreshImportStatus, retryImport, deleteImportBatch, downloadImportErrors
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
const pgNo = ref(1);
const pgSize = ref(20);
const pagedRows = computed(() => rows.value.slice((pgNo.value - 1) * pgSize.value, pgNo.value * pgSize.value));

const TYPE_LABEL = { METRIC_RESULT: '指标结果', ALLOC: 'KPI 结果', TARGET: '目标值' };
const typeLabel = (t) => TYPE_LABEL[t] || t || '-';
const STATUS_LABEL = { PROCESSING: '导入中', SUCCESS: '已完成', FAILED: '失败', PENDING: '排队中' };
const statusLabel = (s) => STATUS_LABEL[s] || s || '-';
const statusCls = (s) => ({
  SUCCESS: 'tag-success',
  FAILED: 'tag-danger',
  PROCESSING: 'tag-warning',
  PENDING: 'tag-info'
}[s] || 'tag-info');
const fmtSize = (n) => n ? (n > 1024*1024 ? (n/1024/1024).toFixed(2) + ' MB' : (n/1024).toFixed(0) + ' KB') : '-';

async function reload() {
  loading.value = true;
  try {
    const r = await listImports();
    rows.value = Array.isArray(r) ? r : [];
  } catch {} finally { loading.value = false; }
}

// === 上传 ===
const uploaderRef = ref(null);
const picked = ref(null);
const uploading = ref(false);
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
async function onUpload() {
  if (!picked.value) return ElMessage.warning('请先选择文件');
  // V1.12 微调：METRIC_RESULT 必填 dataDate（前端 picker 默认今天），缺失提前拦截避免后端 422
  if (kind.value === 'METRIC_RESULT' && !date.value) {
    return ElMessage.warning('请选择数据日期');
  }
  uploading.value = true;
  try {
    const result = await uploadImportFile(kind.value, picked.value, date.value, { uploader: '当前用户' });
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
  try {
    await retryImport(row.batchId || row.id);
    ElMessage.success('已触发重试');
    reload();
  } catch { ElMessage.error('重试失败'); }
}
async function onDelete(row) {
  try {
    await deleteImportBatch(row.batchId || row.id, '前端列表删除');
    ElMessage.success('已删除');
    reload();
  } catch { ElMessage.error('删除失败'); }
}

async function downloadTpl() {
  // 指标定义 / 指标结果：直接下载 public/templates 下的真实模板文件
  const STATIC_TPL = {
    METRIC_DEF:    { url: '/templates/指标表上传模板.xlsx',  name: '指标表上传模板.xlsx' },
    METRIC_RESULT: { url: '/templates/指标结果模板.xlsx', name: '指标结果模板.xlsx' }
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

onMounted(reload);
</script>

<style lang="scss" scoped>
.page-h h1 .sub { font-size: 13px; color: $text-3; margin-left: 12px; font-weight: 400; }
.hint { color: $text-3; font-size: 12px; margin-left: 8px; }
.picked { padding: 12px 16px; background: $bg-soft; border-radius: 4px; margin-top: 12px; font-size: 13px; }
.empty-tip { color: $text-3; font-size: 12px; padding: 10px 16px; }
.section-title { font-size: 14px; font-weight: 600; color: $text-1; padding: 14px 16px 10px; }
.mono { font-family: ui-monospace, monospace; font-size: 12px; }
.pager { display: flex; justify-content: flex-end; padding: 12px 0; }
.pager :deep(.el-pagination) { flex-wrap: wrap; row-gap: 8px; justify-content: flex-end; }
</style>
