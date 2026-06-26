<!--
  数据导入查询 —— 历史数据查询
  批次列表来自 amas_dt_import_sup（按批次聚合），点击「查看数据」→ amas_dt_import_details 按表头透视成动态表。
  后端：GET /api/reports/data-imports（列表） / GET /api/reports/data-imports/{batchNum}（透视数据）
-->
<template>
  <div class="data-import">
    <div class="page-h">
      <h1>数据导入查询</h1>
      <span class="desc">导入批次列表，点击「查看数据」展示该批次的导入数据</span>
    </div>

    <div class="card-section">
      <el-form inline size="default" class="filter-form" @submit.prevent>
        <el-form-item label="批次号">
          <el-input v-model="q.batchNum" placeholder="模糊" clearable style="width:180px" />
        </el-form-item>
        <el-form-item label="数据名称">
          <el-input v-model="q.dataName" placeholder="模糊" clearable style="width:180px" />
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD"
                          start-placeholder="起" end-placeholder="止" style="width:240px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="rows" v-loading="loading" border stripe size="default" empty-text="暂无导入批次">
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="batchNum" label="批次号" min-width="160" show-overflow-tooltip />
        <el-table-column prop="dataName" label="数据名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="创建人" min-width="130">
          <template #default="{ row }">{{ row.createFullname || row.createUsername || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column prop="columnCount" label="列数" width="80" align="right" />
        <el-table-column prop="dtExplain" label="说明" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openData(row)">查看</el-button>
            <el-button link type="primary" :loading="row._downloading" @click="onDownload(row)">下载</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="pageNo" v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="total" background
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="reload" @current-change="reload" />
      </div>
    </div>

    <!-- 透视数据弹框：动态表头 + 行（服务端分页，前两列模糊过滤） -->
    <el-dialog v-model="dlg.show" :title="dlg.title" width="1200px" top="4vh" class="view-dialog">
      <el-form v-if="dlg.columns.length" inline size="default" class="dlg-filter" @submit.prevent>
        <el-form-item v-if="dlg.columns[0]" :label="dlg.columns[0].label">
          <el-input v-model="dlg.f1" placeholder="模糊" clearable style="width:160px"
                    @keyup.enter="onDlgSearch" />
        </el-form-item>
        <el-form-item v-if="dlg.columns[1]" :label="dlg.columns[1].label">
          <el-input v-model="dlg.f2" placeholder="模糊" clearable style="width:160px"
                    @keyup.enter="onDlgSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onDlgSearch">查询</el-button>
          <el-button @click="onDlgReset">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="dlg.rows" v-loading="dlg.loading" border stripe size="default" max-height="62vh"
                empty-text="该批次暂无数据">
        <el-table-column type="index" label="#" width="64"
                         :index="(i) => (dlg.pageNo - 1) * dlg.pageSize + i + 1" />
        <el-table-column v-for="c in dlg.columns" :key="c.key" :prop="c.key" :label="c.label"
                         min-width="150" show-overflow-tooltip />
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="dlg.pageNo" v-model:page-size="dlg.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="dlg.total" background small
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadDlgPage" @current-change="loadDlgPage" />
      </div>
      <template #footer><el-button @click="dlg.show = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { listDataImports, getDataImportData, exportDataImport } from '@/api/history';

const loading = ref(false);
const rows = ref([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(20);
const q = reactive({ batchNum: '', dataName: '' });
const dateRange = ref(null);

async function reload() {
  loading.value = true;
  try {
    const params = { pageNo: pageNo.value, pageSize: pageSize.value };
    if (q.batchNum) params.batchNum = q.batchNum.trim();
    if (q.dataName) params.dataName = q.dataName.trim();
    if (dateRange.value?.[0]) params.createTimeStart = dateRange.value[0];
    if (dateRange.value?.[1]) params.createTimeEnd = dateRange.value[1] + ' 23:59:59';
    const r = await listDataImports(params);
    rows.value = r.records || [];
    total.value = r.total || 0;
  } catch { rows.value = []; total.value = 0; }
  finally { loading.value = false; }
}
function onSearch() { pageNo.value = 1; reload(); }
function onReset() { q.batchNum = ''; q.dataName = ''; dateRange.value = null; pageNo.value = 1; reload(); }

const dlg = reactive({ show: false, loading: false, title: '', batchNum: '',
  columns: [], rows: [], pageNo: 1, pageSize: 20, total: 0, f1: '', f2: '' });

// 打开查看弹框：重置分页/过滤并加载首页
async function openData(row) {
  dlg.title = `导入数据 · ${row.dataName || row.batchNum}`;
  dlg.batchNum = row.batchNum;
  dlg.columns = []; dlg.rows = []; dlg.pageNo = 1; dlg.total = 0;
  dlg.f1 = ''; dlg.f2 = '';
  dlg.show = true;
  await loadDlgPage();
}

// 服务端分页加载弹框当前页（表头每页都带回；前两列模糊过滤随参带上）
async function loadDlgPage() {
  if (!dlg.batchNum) return;
  dlg.loading = true;
  try {
    const params = { pageNo: dlg.pageNo, pageSize: dlg.pageSize };
    // 列 key 取自上一次返回的动态表头；首次加载无表头则不带过滤
    if (dlg.columns[0] && dlg.f1.trim()) { params.col1Key = dlg.columns[0].key; params.col1Kw = dlg.f1.trim(); }
    if (dlg.columns[1] && dlg.f2.trim()) { params.col2Key = dlg.columns[1].key; params.col2Kw = dlg.f2.trim(); }
    const d = await getDataImportData(dlg.batchNum, params);
    dlg.columns = d.columns || [];
    dlg.rows = d.rows || [];
    dlg.total = d.total || 0;
  } catch { ElMessage.error('加载数据失败'); }
  finally { dlg.loading = false; }
}

// 弹框内按前两列模糊查询：回到第 1 页
function onDlgSearch() { dlg.pageNo = 1; loadDlgPage(); }
function onDlgReset() { dlg.f1 = ''; dlg.f2 = ''; dlg.pageNo = 1; loadDlgPage(); }

// 下载整个批次为 Excel（后端全量生成，不分页）
async function onDownload(row) {
  row._downloading = true;
  try {
    await exportDataImport(row.batchNum, `数据导入_${row.dataName || row.batchNum}.xlsx`);
  } catch { ElMessage.error('下载失败'); }
  finally { row._downloading = false; }
}

onMounted(reload);
</script>

<style lang="scss" scoped>
.filter-form { margin-bottom: 12px; }
.dlg-filter { margin-bottom: 10px; }
.pager { margin-top: 12px; display: flex; justify-content: flex-end; }
</style>

<!-- 非 scoped：el-dialog 默认 teleport 到 body，scoped 选择器选不中，用自定义 class 限定全局样式 -->
<style lang="scss">
.view-dialog .el-dialog__title { font-size: 18px; font-weight: 600; }
.view-dialog .el-table { font-size: 15px; }
.view-dialog .el-table th.el-table__cell { font-size: 15px; font-weight: 600; }
.view-dialog .el-table .cell { line-height: 1.6; padding-top: 2px; padding-bottom: 2px; }
.view-dialog .dlg-filter .el-form-item__label { font-size: 15px; }
.view-dialog .dlg-filter .el-input__inner { font-size: 15px; }
</style>
