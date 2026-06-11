<template>
  <div>
    <div class="page-h">
      <h1>KPI 计算结果详情
        <span class="sub">数据日期 {{ dataDate || '-' }} · 方案 {{ schemeCode || '-' }}</span>
      </h1>
      <div class="actions">
        <el-button @click="goBack">← 返回</el-button>
        <el-button @click="loadData">刷新</el-button>
      </div>
    </div>

    <!-- 查询：维度（按对象分组展示，列动态为该方案的各指标组）-->
    <div class="card-section">
      <el-form :inline="true" size="default">
        <el-form-item label="维度">
          <el-select v-model="subjectType" clearable placeholder="全部维度" style="width:140px" @change="onQuery">
            <el-option v-for="(label, val) in DIM" :key="val" :value="val" :label="label" />
          </el-select>
        </el-form-item>
        <el-form-item label="对象">
          <el-input v-model="subjectKeyword" clearable placeholder="按对象名称模糊查询" style="width:200px"
                    @keyup.enter="onQuery" @clear="onQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onQuery">查询</el-button>
          <el-button @click="onReset">重置</el-button>
          <el-button type="success" plain :loading="exporting==='scores'" @click="onExportScores">导出KPI得分</el-button>
          <el-button type="success" plain :loading="exporting==='details'" @click="onExportDetails">导出KPI明细</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card-section">
      <!-- 固定列：对象ID / 姓名 / 考核得分(合计)；之后每个指标一个分组列(指标名 + 实际/目标/基础/完成率/得分) -->
      <el-table :data="records" size="small" border v-loading="loading" empty-text="暂无计算结果">
        <!-- 维度：同一对象ID 可能在不同维度各有一行（如员工/客户工号撞号），按 对象ID+对象类型 分组 -->
        <el-table-column label="维度" width="80" fixed>
          <template #default="{row}">{{ DIM[row.subjectType] || row.subjectType || '-' }}</template>
        </el-table-column>
        <el-table-column label="对象ID" min-width="130" fixed prop="subjectId" />
        <el-table-column label="姓名" min-width="110" fixed>
          <template #default="{row}">{{ row.subjectName || '-' }}</template>
        </el-table-column>
        <el-table-column label="考核得分" width="100" fixed align="right">
          <template #default="{row}"><strong>{{ fmtNum(row.totalScore) }}</strong></template>
        </el-table-column>

        <el-table-column v-for="m in metrics" :key="m.metricCode"
          :label="m.metricName || m.metricCode" align="center">
          <el-table-column label="实际值" width="100" align="right">
            <template #default="{row}">{{ fmtNum(cell(row, m).actual) }}</template>
          </el-table-column>
          <el-table-column label="目标值" width="100" align="right">
            <template #default="{row}">{{ fmtNum(cell(row, m).target) }}</template>
          </el-table-column>
          <el-table-column label="基础值" width="100" align="right">
            <template #default="{row}">{{ fmtNum(cell(row, m).base) }}</template>
          </el-table-column>
          <el-table-column label="完成率" width="100" align="right">
            <template #default="{row}">{{ fmtRate(cell(row, m).completeRate) }}</template>
          </el-table-column>
          <el-table-column label="得分" width="90" align="right">
            <template #default="{row}"><strong>{{ fmtNum(cell(row, m).score) }}</strong></template>
          </el-table-column>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination v-model:current-page="pgNo" v-model:page-size="pgSize" :page-sizes="[20, 50, 100]"
          :total="total" background layout="total, sizes, prev, pager, next"
          @current-change="loadData" @size-change="onSizeChange" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { listKpiScoreResults, exportKpiScores, exportKpiScoreDetails } from '@/api/perf';

const route = useRoute();
const router = useRouter();
const dataDate = ref(route.query.dataDate || '');
const schemeCode = ref(route.query.schemeCode || '');

const metrics = ref([]);   // 指标列定义 [{metricCode, metricName}]
const records = ref([]);   // 对象行 [{subjectId, subjectName, totalScore, metrics:{code:{actual,target,base,completeRate,score}}}]
const total = ref(0);
const pgNo = ref(1);
const pgSize = ref(20);
const loading = ref(false);
const subjectType = ref('');
const subjectKeyword = ref(''); // 对象名称模糊查询关键字

const DIM = { EMP: '员工', ORG: '机构', CUST: '客户' };
const EMPTY_CELL = {};
/** 取某行某指标的格数据（缺失返回空对象，模板按字段取值即为 '-'） */
function cell(row, m) {
  return (row.metrics && row.metrics[m.metricCode]) || EMPTY_CELL;
}
function fmtNum(v) {
  if (v == null || v === '') return '-';
  const n = Number(v);
  return Number.isNaN(n) ? v : (Math.round(n * 10000) / 10000);
}
function fmtRate(v) {
  if (v == null || v === '') return '-';
  const n = Number(v);
  return Number.isNaN(n) ? v : `${Math.round(n * 100) / 100}%`;
}

function onQuery() { pgNo.value = 1; loadData(); }
function onReset() { subjectType.value = ''; subjectKeyword.value = ''; pgNo.value = 1; loadData(); }
function onSizeChange() { pgNo.value = 1; loadData(); }
function goBack() {
  if (window.history.length > 1) router.back();
  else router.push({ name: 'PerfCompute' });
}

const exporting = ref('');
/** 触发浏览器下载 blob */
function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
function exportParams() {
  return {
    dataDate: dataDate.value || undefined,
    schemeCode: schemeCode.value || undefined,
    subjectType: subjectType.value || undefined
  };
}
async function onExportScores() {
  exporting.value = 'scores';
  try {
    const blob = await exportKpiScores(exportParams());
    saveBlob(blob, `KPI得分_${dataDate.value || ''}.xlsx`);
  } catch { ElMessage.error('导出失败'); } finally { exporting.value = ''; }
}
async function onExportDetails() {
  exporting.value = 'details';
  try {
    const blob = await exportKpiScoreDetails(exportParams());
    saveBlob(blob, `KPI明细_${dataDate.value || ''}.xlsx`);
  } catch { ElMessage.error('导出失败'); } finally { exporting.value = ''; }
}

async function loadData() {
  loading.value = true;
  try {
    const r = await listKpiScoreResults({
      dataDate: dataDate.value || undefined,
      schemeCode: schemeCode.value || undefined,
      subjectType: subjectType.value || undefined,
      subjectKeyword: subjectKeyword.value || undefined,
      pageNo: pgNo.value, pageSize: pgSize.value
    });
    metrics.value = r?.metrics || [];
    records.value = r?.records || [];
    total.value = r?.total ?? records.value.length;
  } catch { metrics.value = []; records.value = []; total.value = 0; } finally { loading.value = false; }
}

onMounted(loadData);
</script>

<style scoped lang="scss">
.page-h { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.page-h h1 { font-size: 18px; font-weight: 600; }
.page-h .sub { font-size: 13px; color: #909399; font-weight: 400; margin-left: 10px; }
.card-section { background: #fff; border-radius: 8px; padding: 14px; }
.pager { margin-top: 12px; display: flex; justify-content: flex-end; }
</style>
