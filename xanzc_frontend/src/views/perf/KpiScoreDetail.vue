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

    <!-- 查询：维度 + 指标（仅含该方案的指标）-->
    <div class="card-section">
      <el-form :inline="true" size="default">
        <el-form-item label="维度">
          <el-select v-model="subjectType" clearable placeholder="全部维度" style="width:140px" @change="onQuery">
            <el-option v-for="(label, val) in DIM" :key="val" :value="val" :label="label" />
          </el-select>
        </el-form-item>
        <el-form-item label="指标">
          <el-select v-model="metricCode" clearable filterable placeholder="全部指标" style="width:280px">
            <el-option v-for="o in metricOptions" :key="o.metricCode" :value="o.metricCode"
              :label="o.metricName ? `${o.metricName}(${o.metricCode})` : o.metricCode" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onQuery">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card-section">
      <el-table :data="rows" size="default" v-loading="loading" empty-text="暂无计算结果">
        <el-table-column label="维度" width="110">
          <template #default="{row}">{{ dimLabel(row.subjectType) }}</template>
        </el-table-column>
        <el-table-column label="指标" min-width="200">
          <template #default="{row}">{{ row.metricCode }}{{ row.metricName ? ' ' + row.metricName : '' }}</template>
        </el-table-column>
        <el-table-column label="对象" min-width="180">
          <template #default="{row}">{{ row.subjectId }}{{ row.subjectName ? ' ' + row.subjectName : '' }}</template>
        </el-table-column>
        <el-table-column label="实际值" width="130" align="right">
          <template #default="{row}">{{ fmtNum(row.actualValue) }}</template>
        </el-table-column>
        <el-table-column label="目标值" width="120" align="right">
          <template #default="{row}">{{ fmtNum(row.targetValue) }}</template>
        </el-table-column>
        <el-table-column label="基础值" width="120" align="right">
          <template #default="{row}">{{ fmtNum(row.baseValue) }}</template>
        </el-table-column>
        <el-table-column label="权重" width="100" align="right">
          <template #default="{row}">{{ fmtNum(row.weight) }}</template>
        </el-table-column>
        <el-table-column label="得分" width="110" align="right" fixed="right">
          <template #default="{row}"><strong>{{ fmtNum(row.score) }}</strong></template>
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
import { listKpiScoreResults, listKpiSchemeMetrics } from '@/api/perf';

const route = useRoute();
const router = useRouter();
const dataDate = ref(route.query.dataDate || '');
const schemeCode = ref(route.query.schemeCode || '');

const rows = ref([]);
const total = ref(0);
const pgNo = ref(1);
const pgSize = ref(20);
const loading = ref(false);
const metricCode = ref('');
const subjectType = ref('');
const metricOptions = ref([]);

async function loadMetricOptions() {
  try { metricOptions.value = (await listKpiSchemeMetrics(schemeCode.value)) || []; } catch {}
}
function onQuery() { pgNo.value = 1; loadData(); }
function onReset() { metricCode.value = ''; subjectType.value = ''; pgNo.value = 1; loadData(); }

const DIM = { EMP: '员工', ORG: '机构', CUST: '客户' };
function dimLabel(t) { return DIM[t] || t || '-'; }
function fmtNum(v) {
  if (v == null || v === '') return '-';
  const n = Number(v);
  return Number.isNaN(n) ? v : (Math.round(n * 10000) / 10000);
}
function goBack() {
  if (window.history.length > 1) router.back();
  else router.push({ name: 'PerfCompute' });
}
function onSizeChange() { pgNo.value = 1; loadData(); }
async function loadData() {
  loading.value = true;
  try {
    const r = await listKpiScoreResults({
      dataDate: dataDate.value || undefined,
      schemeCode: schemeCode.value || undefined,
      metricCode: metricCode.value || undefined,
      subjectType: subjectType.value || undefined,
      pageNo: pgNo.value, pageSize: pgSize.value
    });
    rows.value = r?.records || [];
    total.value = r?.total ?? rows.value.length;
  } catch {} finally { loading.value = false; }
}

onMounted(() => { loadMetricOptions(); loadData(); });
</script>

<style scoped lang="scss">
.page-h { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.page-h h1 { font-size: 18px; font-weight: 600; }
.page-h .sub { font-size: 13px; color: #909399; font-weight: 400; margin-left: 10px; }
.card-section { background: #fff; border-radius: 8px; padding: 14px; }
.pager { margin-top: 12px; display: flex; justify-content: flex-end; }
</style>
