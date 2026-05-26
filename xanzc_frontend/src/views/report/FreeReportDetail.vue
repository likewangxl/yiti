<template>
  <div>
    <div class="page-h">
      <h1>{{ batchInfo.reportName || '报表详情' }}</h1>
      <span class="sub">{{ batchInfo.fileName }} · {{ fmtTime(batchInfo.importTime) }} · {{ batchInfo.rowCount || 0 }} 行</span>
      <div class="actions">
        <el-button @click="$router.push('/report/free')">← 返回列表</el-button>
      </div>
    </div>

    <div class="card-section">
      <el-form inline size="default">
        <el-form-item label="姓名搜索">
          <el-input v-model="keyword" placeholder="姓名/工号" clearable style="width:200px"
                    @keyup.enter="reload" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="reload">查询</el-button>
          <el-button @click="keyword = ''; reload()">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card-section table">
      <el-table :data="rows" size="default" v-loading="loading" empty-text="暂无数据" stripe border
                max-height="560" style="width:100%">
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { queryFreeReportData, getFreeReportColumns, listFreeReportBatches } from '@/api/report';

const route = useRoute();
const batchId = route.params.batchId;

const batchInfo = ref({});
const columns = ref([]);
const rows = ref([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(20);
const keyword = ref('');
const loading = ref(false);

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
  } catch {}
}

async function loadColumns() {
  try {
    const cols = await getFreeReportColumns(batchId);
    columns.value = Array.isArray(cols) ? cols : [];
  } catch { columns.value = []; }
}

async function reload() {
  loading.value = true;
  try {
    const r = await queryFreeReportData({
      batchId,
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

onMounted(async () => {
  await Promise.all([loadBatchInfo(), loadColumns()]);
  reload();
});
</script>

<style lang="scss" scoped>
.page-h {
  display: flex; align-items: baseline; gap: 12px; margin-bottom: 12px;
  h1 { font-size: 18px; font-weight: 600; }
  .sub { color: #999; font-size: 12px; }
  .actions { margin-left: auto; }
}
.table { padding: 0; padding-bottom: 12px; }
.pager { padding: 12px 20px; display: flex; justify-content: flex-end; }
</style>
