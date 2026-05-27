<template>
  <div>
    <div class="page-h">
      <h1>任务调度</h1>
      <div class="actions"><el-button type="primary">+ 新建任务</el-button></div>
    </div>
    <div class="card-section">
      <el-table :data="rows" size="default" empty-text="暂无任务">
        <el-table-column label="任务 Key" min-width="240">
          <template #default="{row}"><code class="mono">{{ row.jobKey }}</code></template>
        </el-table-column>
        <el-table-column prop="jobName" label="任务名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="Cron" width="160">
          <template #default="{row}"><code class="mono">{{ row.cronExpr }}</code></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{row}">
            <el-tag :class="statusCls(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="允许手动" width="100" align="center">
          <template #default="{row}">{{ row.allowManualTrigger ? '✓' : '—' }}</template>
        </el-table-column>
        <el-table-column prop="lastRunTime" label="上次执行" width="170" />
        <el-table-column prop="nextRunTime" label="下次执行" width="170" />
        <el-table-column label="操作" width="190">
          <template #default="{row}">
            <el-button link type="primary" size="small">日志</el-button> |
            <el-button v-if="row.status === 'ACTIVE'" link type="primary" size="small" @click="onPause(row)">暂停</el-button>
            <el-button v-else link type="primary" size="small" @click="onResume(row)">恢复</el-button> |
            <el-button v-if="row.allowManualTrigger" link type="primary" size="small" @click="onTrigger(row)">触发</el-button>
            <span v-else style="color:#9CA3AF">触发</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { sysJobs } from '@/mock';
import { listJobs, pauseJob, resumeJob, triggerJob } from '@/api/system';
const rows = ref(sysJobs);
const statusCls = (s) => ({ ACTIVE: 'tag-success', PAUSED: 'tag-warning', DISABLED: 'tag-danger' }[s] || 'tag-info');
const statusLabel = (s) => ({ ACTIVE: '运行中', PAUSED: '已暂停', DISABLED: '已禁用' }[s] || s || '-');

async function reload() {
  try { const r = await listJobs(); if (Array.isArray(r)) rows.value = r; } catch {}
}
onMounted(reload);

async function onPause(row) {
  try { await pauseJob(row.id); ElMessage.success('已暂停'); reload(); } catch (e) { /* http.js 已弹错 */ }
}
async function onResume(row) {
  try { await resumeJob(row.id); ElMessage.success('已恢复'); reload(); } catch {}
}
async function onTrigger(row) {
  try {
    await ElMessageBox.confirm(`确认手动触发任务 ${row.jobKey}？`, '提示', { type: 'warning' });
  } catch { return; }
  try { await triggerJob(row.id); ElMessage.success('已触发'); } catch {}
}
</script>

<style lang="scss" scoped>
.mono { font-family: ui-monospace, monospace; font-size: 12px; background: $bg-soft; padding: 2px 6px; border-radius: 3px; }
</style>
