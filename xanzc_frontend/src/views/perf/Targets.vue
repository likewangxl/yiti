<template>
  <div>
    <div class="page-h">
      <h1>目标管理</h1>
      <span class="desc">单条 / 批量 / 修正（修正会触发回算）</span>
      <div class="actions">
        <el-button>📥 导入目标矩阵</el-button>
        <el-button>下载模板</el-button>
        <el-button type="primary">+ 新增目标</el-button>
      </div>
    </div>

    <div class="card-section">
      <el-form :inline="true" size="default">
        <el-form-item label="方案">
          <el-select v-model="plan" style="width:200px"><el-option value="2026Q2" label="2026Q2 KPI" /></el-select>
        </el-form-item>
        <el-form-item label="对象类型">
          <el-select v-model="subj" style="width:140px"><el-option value="EMP" label="员工" /><el-option value="ORG" label="机构" /></el-select>
        </el-form-item>
        <el-form-item label="指标"><el-select v-model="m" style="width:200px"><el-option value="" label="全部" /></el-select></el-form-item>
        <el-form-item label="状态"><el-select v-model="st" style="width:140px"><el-option value="" label="全部" /></el-select></el-form-item>
      </el-form>
    </div>

    <div class="card-section">
      <el-table :data="rows" size="default">
        <el-table-column label="对象" width="140">
          <template #default="{row}">
            <el-avatar :size="22" style="background:#003D7A;font-size:11px">{{ row.subj.charAt(2) }}</el-avatar>
            <span style="margin-left:8px">{{ row.subj }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="org" label="机构" width="120" />
        <el-table-column prop="metric" label="指标" min-width="180" />
        <el-table-column prop="origin" label="原目标" width="100" align="right" />
        <el-table-column label="当前目标" width="140" align="right">
          <template #default="{row}">
            <span>{{ row.current.toLocaleString() }}</span>
            <span v-if="row.adjusted" style="color:#DC2626;margin-left:4px">↑修正</span>
          </template>
        </el-table-column>
        <el-table-column prop="done" label="累计完成" width="120" align="right" />
        <el-table-column label="完成率" width="100" align="right">
          <template #default="{row}">
            <span :style="{ color: row.rate >= 90 ? '#16A34A' : row.rate >= 75 ? '#D97706' : '#DC2626' }">{{ row.rate }}%</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{row}">
            <el-tag :class="row.status === '已审批' ? 'tag-success' : 'tag-warning'" effect="plain">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default><el-button link type="primary" size="small">修正</el-button></template>
        </el-table-column>
      </el-table>
      <el-alert type="warning" :closable="false" style="margin-top:12px"
        title="目标修正审批通过后将触发 KPI 历史回算（生成新批次 CALC-YYMMDD-xxx）。" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { perfTargets } from '@/mock';
import { listTargets } from '@/api/perf';
const rows = ref(perfTargets);
const plan = ref('2026Q2'), subj = ref('EMP'), m = ref(''), st = ref('');
onMounted(async () => { try { const r = await listTargets({ plan: plan.value, subjectType: subj.value }); if (r) rows.value = r; } catch {} });
</script>
