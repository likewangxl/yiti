<template>
<main v-bp-overflow-tooltip class="bp-crud free-report-detail" aria-labelledby="free-report-detail-title" :aria-busy="loading || metaLoading ? 'true' : 'false'">
    <header class="page-h">
      <h1 id="free-report-detail-title" class="page-title">{{ batchInfo.reportName || '报表详情' }}</h1>
      <span class="sub">{{ batchInfo.fileName }} · {{ fmtTime(batchInfo.importTime) }} · {{ batchInfo.rowCount || 0 }} 行</span>
      <div class="actions action-group" role="group" aria-label="报表详情操作">
        <el-button @click="$router.push('/report/free')">返回列表</el-button>
      </div>
    </header>

    <section class="card-section filter-bar" aria-label="自由报表数据筛选">
      <el-form inline size="default" class="filter-form" @submit.prevent>
        <el-form-item label="工号">
          <el-input v-model="searchCol1" placeholder="按工号搜索" clearable style="width:180px"
                    @keyup.enter="reload" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="searchCol2" placeholder="按姓名搜索" clearable style="width:180px"
                    @keyup.enter="reload" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" :disabled="loading" @click="onSearch">查询</el-button>
          <el-button @click="searchCol1 = ''; searchCol2 = ''; reload()">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="card-section data-panel" aria-labelledby="free-report-detail-table-title" aria-describedby="free-report-detail-state" :aria-busy="loading ? 'true' : 'false'">
      <div class="toolbar"><div><h2 id="free-report-detail-table-title" class="section-title">导入数据</h2><p class="hint">按前两列工号、姓名筛选；点击数据单元格可查看完整原始值。</p></div><p id="free-report-detail-state" class="table-state" role="status" aria-live="polite">{{ loadError || (loading ? '正在加载报表数据' : rows.length ? `共 ${total} 行` : '暂无数据') }}</p></div>
      <div v-if="metaError || loadError" class="error-state" role="alert"><span>{{ metaError || loadError }}</span><el-button link type="primary" @click="reload">重试</el-button></div>
      <el-table :data="rows" size="default" v-loading="loading" empty-text="暂无数据" stripe border
                max-height="560" style="width:100%" aria-labelledby="free-report-detail-table-title">
        <el-table-column type="index" label="序号" width="60" fixed />
        <el-table-column v-if="columns.length > 0" :prop="columns[0].key" :label="columns[0].label"
                         width="150" fixed />
        <el-table-column v-if="columns.length > 1" :prop="columns[1].key" :label="columns[1].label"
                         width="150" fixed />
        <el-table-column v-for="col in dynamicCols" :key="col.key" :prop="col.key" :label="col.label"
                         min-width="120">
          <template #default="{ row }">
            <!-- 显示导入时保留的 Excel 原样文本(-0.0 / 54.5%)；
                 悬停 title 与点击气泡均给完整原值(无科学计数法)。原始数据不改。 -->
            <el-popover placement="top" trigger="click" :width="260"
                        :disabled="cellFull(row, col.key) === cellDisplay(row, col.key)">
              <template #reference>
                <span class="cell-val" :title="cellFull(row, col.key)">{{ cellDisplay(row, col.key) }}</span>
              </template>
              <div class="cell-full">
                <div class="cell-full-label">完整值</div>
                <div class="cell-full-val">{{ cellFull(row, col.key) }}</div>
              </div>
            </el-popover>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :page-sizes="[20, 50, 100]"
          :total="total"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="onSizeChange"
          @current-change="reload"
        />
      </div>
    </section>
  </main>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { queryFreeReportData, getFreeReportColumns, listFreeReportBatches } from '@/api/report';
import { cellDisplay, cellFull } from '@/utils/cellFmt';

const route = useRoute();
const batchId = route.params.batchId;

const batchInfo = ref({});
const columns = ref([]);
const rows = ref([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(20);
const searchCol1 = ref('');
const searchCol2 = ref('');
const col1Label = computed(() => columns.value[0]?.label || '第一列');
const col2Label = computed(() => columns.value[1]?.label || '第二列');
const loading = ref(false);
const metaLoading = ref(false);
const loadError = ref('');
const metaError = ref('');

const dynamicCols = computed(() => columns.value.slice(2));

function fmtTime(t) {
  if (!t) return '';
  return String(t).replace('T', ' ').slice(0, 16);
}

async function loadBatchInfo() {
  try {
    const list = await listFreeReportBatches();
    const arr = Array.isArray(list) ? list : [];
    batchInfo.value = arr.find(b => b.id === batchId) || {};
  } catch (e) { metaError.value = e?.message || '报表批次信息加载失败'; }
}

async function loadColumns() {
  try {
    const cols = await getFreeReportColumns(batchId);
    columns.value = Array.isArray(cols) ? cols : [];
  } catch (e) { columns.value = []; metaError.value = e?.message || '报表列定义加载失败'; }
}

async function reload() {
  if (loading.value) return;
  loading.value = true;
  loadError.value = '';
  try {
    const r = await queryFreeReportData({
      batchId,
      empNo: searchCol1.value || undefined,
      empName: searchCol2.value || undefined,
      pageNo: pageNo.value,
      pageSize: pageSize.value
    });
    rows.value = r?.records || [];
    total.value = r?.total || 0;
  } catch (e) {
    rows.value = [];
    total.value = 0;
    loadError.value = e?.message || '报表数据加载失败，请重试';
  } finally { loading.value = false; }
}

function onSearch() { pageNo.value = 1; reload(); }
function onSizeChange() { pageNo.value = 1; reload(); }

onMounted(async () => {
  metaLoading.value = true;
  await Promise.all([loadBatchInfo(), loadColumns()]);
  metaLoading.value = false;
  reload();
});
</script>

<style lang="scss" scoped>
/* 数值格：有完整值可展开时给个可点击的提示 */
.cell-val { cursor: pointer; }
.cell-full-label { color: #999; font-size: 12px; margin-bottom: 4px; }
.cell-full-val { font-family: ui-monospace, monospace; word-break: break-all; }

.pager { padding: 12px 20px; display: flex; justify-content: flex-end; }
.pager :deep(.el-pagination) { flex-wrap: wrap; row-gap: 8px; justify-content: flex-end; }
.cell-full-label { color: var(--color-text-muted); }
.error-state { align-items: center; background: var(--color-danger-bg); border: 1px solid var(--color-border); color: var(--color-danger-fg); display: flex; gap: var(--space-3); justify-content: space-between; margin-bottom: var(--space-3); padding: var(--space-3); }
</style>
