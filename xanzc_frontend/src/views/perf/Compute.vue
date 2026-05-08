<template>
  <div>
    <div class="page-h">
      <h1>考核计算</h1>
      <span class="desc">手工触发 / 回算 / 快照</span>
      <div class="actions"><el-button type="primary">▶ 触发计算</el-button></div>
    </div>

    <div class="stats">
      <div class="stat"><div class="label">本月计算任务</div><div class="value">{{ s.tasks }}</div></div>
      <div class="stat"><div class="label">成功</div><div class="value" style="color:#16A34A">{{ s.ok }}</div></div>
      <div class="stat"><div class="label">失败</div><div class="value" style="color:#DC2626">{{ s.fail }}</div></div>
      <div class="stat"><div class="label">最近耗时</div><div class="value">{{ s.lastDuration }}</div></div>
    </div>

    <div class="card-section">
      <el-table :data="rows" size="default">
        <el-table-column prop="batch" label="计算批次" width="190" />
        <el-table-column prop="plan" label="方案" width="140" />
        <el-table-column prop="scope" label="触发范围" width="140" />
        <el-table-column label="触发方式" width="100">
          <template #default="{row}">
            <el-tag :class="triggerCls(row.trigger)" effect="plain">{{ row.trigger }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="who" label="触发人" width="120" />
        <el-table-column prop="start" label="开始时间" width="100" />
        <el-table-column prop="dur" label="耗时" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{row}">
            <el-tag :class="row.status === '成功' ? 'tag-success' : 'tag-danger'" effect="plain">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{row}">
            <el-button link type="primary" size="small">{{ row.status === '失败' ? '查看错误' : '查看快照' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { perfComputeStats, perfComputeBatches } from '@/mock';
import { getComputeStats, listComputeBatches } from '@/api/perf';
const s = ref(perfComputeStats);
const rows = ref(perfComputeBatches);
const triggerCls = (t) => ({ 手动: 'tag-info', 定时: 'tag-success', 回算: 'tag-warning' }[t] || '');
onMounted(async () => {
  try { const r = await getComputeStats(); if (r) s.value = r; } catch {}
  try { const r = await listComputeBatches(); if (r) rows.value = r; } catch {}
});
</script>

<style lang="scss" scoped>
.stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 12px; }
</style>
