<!--
  分行行长仪表盘 —— 对应 HTML RptPres
  接入 yiti API：
    GET /api/reports/dashboard/president?orgCode=&date=
-->
<template>
  <div class="rpt-dash">
    <div class="page-h">
      <h1>分行行长仪表盘</h1>
      <span class="desc">{{ data.org }} · 数据日期 {{ data.date }}</span>
      <div class="actions">
        <el-date-picker
          v-model="queryDate"
          type="date"
          value-format="YYYY-MM-DD"
          :disabled-date="disabledDate"
          :clearable="false"
          :prefix-icon="Calendar"
          placeholder="切换日期"
          style="width: 180px"
          @change="onSwitchDate"
        />
        <el-button type="primary" :icon="Download" :loading="exporting" @click="onExportPdf">导出 PDF</el-button>
      </div>
    </div>

    <!-- 5 项 KPI 卡 -->
    <div class="stats">
      <div v-for="s in data.stats" :key="s.label" class="stat">
        <div class="label">{{ s.label }}</div>
        <div class="value">{{ s.value }}<span class="unit">{{ s.unit }}</span></div>
        <div class="trend" :class="s.trendType">{{ s.trend }}</div>
      </div>
    </div>

    <!-- 主区：趋势图 + 机构存款排名 -->
    <div class="cols">
      <div class="card-section chart-card">
        <div class="card-h">
          <div class="title">存款 / 贷款 趋势（近 12 个月）</div>
          <div class="legend">
            <span><i style="background:#003D7A"></i>存款</span>
            <span><i style="background:#60a5fa"></i>贷款</span>
          </div>
        </div>
        <v-chart class="chart" :option="trendOption" autoresize />
      </div>

      <div class="card-section">
        <div class="card-h"><div class="title">机构存款排名</div></div>
        <div class="rank">
          <div v-for="(r, i) in data.ranking" :key="r.org" class="row">
            <span class="no" :class="{ top: i < 3 }">{{ i + 1 }}</span>
            <span class="org">{{ r.org }}</span>
            <div class="bar">
              <div class="fill" :style="{ width: r.val + '%', background: barColor(r, i) }" />
            </div>
            <span class="num">{{ r.val }}亿</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Calendar, Download } from '@element-plus/icons-vue';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { LineChart } from 'echarts/charts';
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components';
import VChart from 'vue-echarts';
import { reportDashboard } from '@/mock';
import { getDashboardPresident, exportDashboardPdf } from '@/api/report';

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent]);

const data = ref(reportDashboard);
const queryDate = ref(reportDashboard.date);
const loading = ref(false);
const exporting = ref(false);

// 不允许选未来日期
function disabledDate(d) { return d && d.getTime() > Date.now(); }

async function loadDashboard(date) {
  loading.value = true;
  try {
    const r = await getDashboardPresident({ orgCode: '0000', date });
    if (r) {
      data.value = r;
      queryDate.value = r.date || date;
    }
  } catch (e) {
    // call() 已兜底 mock，这里仅保证 date 同步
    data.value = { ...data.value, date };
    queryDate.value = date;
  } finally {
    loading.value = false;
  }
}

onMounted(() => loadDashboard(queryDate.value));

const trendOption = computed(() => ({
  grid: { top: 30, right: 24, bottom: 30, left: 50 },
  tooltip: { trigger: 'axis' },
  xAxis: { type: 'category', data: data.value.trend.months, boundaryGap: false, axisLine: { lineStyle: { color: '#e5e7eb' } }, axisLabel: { color: '#9ca3af' } },
  yAxis: { type: 'value', axisLine: { show: false }, axisLabel: { color: '#9ca3af' }, splitLine: { lineStyle: { color: '#f3f4f6' } } },
  series: [
    {
      name: '存款', type: 'line', smooth: true,
      data: data.value.trend.deposit,
      lineStyle: { color: '#003D7A', width: 2 },
      itemStyle: { color: '#003D7A' },
      areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [
        { offset: 0, color: 'rgba(30,91,186,.4)' }, { offset: 1, color: 'rgba(30,91,186,0)' }
      ] } },
      symbol: 'circle', symbolSize: 6
    },
    {
      name: '贷款', type: 'line', smooth: true,
      data: data.value.trend.loan,
      lineStyle: { color: '#60a5fa', width: 2 },
      itemStyle: { color: '#60a5fa' },
      areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [
        { offset: 0, color: 'rgba(96,165,250,.3)' }, { offset: 1, color: 'rgba(96,165,250,0)' }
      ] } },
      symbol: 'circle', symbolSize: 6
    }
  ]
}));

function barColor(r, i) {
  // 红/黄/绿三色分档，参考 HTML：前 2 名绿；3-4 黄；5-6 红
  if (r.color === 'g' || i < 2) return '#16A34A';
  if (r.color === 'y' || i < 4) return '#D97706';
  return '#DC2626';
}

function onSwitchDate(d) {
  if (!d) return;
  loadDashboard(d);
}
async function onExportPdf() {
  exporting.value = true;
  try {
    const r = await exportDashboardPdf({ orgCode: '0000', date: queryDate.value });
    ElMessage.success(`PDF 导出任务已提交（taskId=${r?.taskId || '-'}）`);
  } catch (e) {
    ElMessage.error('导出失败：' + (e?.message || '未知错误'));
  } finally {
    exporting.value = false;
  }
}
</script>

<style lang="scss" scoped>
.rpt-dash {
  .page-h { display: flex; align-items: baseline; gap: 12px; margin-bottom: 12px;
    h1 { font-size: 18px; font-weight: 600; color: $text-1; }
    .desc { color: $text-3; font-size: 12px; }
    .actions { margin-left: auto; display: flex; gap: 8px; }
  }
  .stats { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; margin-bottom: 12px;
    .stat { background: #fff; border: 1px solid $border-1; border-radius: 4px; padding: 14px 16px;
      .label { font-size: 12px; color: $text-3; }
      .value { display: flex; align-items: baseline; gap: 4px; font-size: 22px; font-weight: 600; color: $text-1; margin: 6px 0;
        .unit { font-size: 13px; color: $text-3; font-weight: 400; }
      }
      .trend { font-size: 12px;
        &.up { color: $success; }
        &.down { color: $danger; }
      }
    }
  }
  .cols { display: grid; grid-template-columns: 1fr 360px; gap: 12px; }
  .chart-card { padding: 16px 20px; }
  .chart { height: 320px; }
  .card-h { display: flex; align-items: center; padding: 0 0 12px; border-bottom: 1px solid $border-1; margin-bottom: 12px;
    .title { font-size: 14px; font-weight: 600; }
    .legend { margin-left: auto; display: flex; gap: 16px; font-size: 12px; color: $text-3;
      span { display: flex; align-items: center; gap: 4px; }
      i { width: 10px; height: 10px; border-radius: 2px; }
    }
  }
  .rank { padding: 8px 16px;
    .row { display: flex; align-items: center; gap: 12px; padding: 8px 0;
      .no { width: 22px; height: 22px; border-radius: 50%; background: #e5e7eb; color: $text-3;
        display: grid; place-items: center; font-size: 12px; font-weight: 600;
        &.top { background: $primary; color: #fff; }
      }
      .org { width: 84px; font-size: 13px; }
      .bar { flex: 1; height: 6px; background: $border-3; border-radius: 3px; overflow: hidden;
        .fill { height: 100%; transition: width .3s; }
      }
      .num { width: 50px; text-align: right; font-size: 12px; color: $text-2; font-variant-numeric: tabular-nums; }
    }
  }
}
</style>
