<template>
  <div>
    <div class="page-h">
      <h1>审计日志</h1>
      <div class="actions"><el-button type="danger" plain>📥 导出（高危）</el-button></div>
    </div>

    <div class="card-section">
      <el-form :inline="true" size="default">
        <el-form-item label="TraceId"><el-input v-model="f.tid" style="width:180px" /></el-form-item>
        <el-form-item label="操作人"><el-input v-model="f.who" style="width:160px" /></el-form-item>
        <el-form-item label="动作"><el-select v-model="f.action" style="width:140px"><el-option value="" label="全部" /></el-select></el-form-item>
        <el-form-item label="BizType"><el-select v-model="f.biz" style="width:140px"><el-option value="" label="全部" /></el-select></el-form-item>
        <el-form-item label="资源"><el-input v-model="f.res" style="width:200px" /></el-form-item>
        <el-form-item label="时间范围"><el-date-picker v-model="f.t" type="daterange" style="width:280px" /></el-form-item>
      </el-form>
    </div>

    <div class="card-section">
      <el-table :data="rows" size="default" empty-text="暂无审计日志">
        <el-table-column label="TraceId" width="140">
          <template #default="{row}"><code class="mono">{{ row.traceId || '—' }}</code></template>
        </el-table-column>
        <el-table-column label="操作人" width="140">
          <template #default="{row}">{{ row.empName || row.empId || '—' }}</template>
        </el-table-column>
        <el-table-column label="动作" width="140">
          <template #default="{row}"><el-tag :class="actCls(row.bizAction)" effect="plain">{{ row.bizAction || '—' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="bizType" label="BizType" width="140" />
        <el-table-column label="资源" min-width="220">
          <template #default="{row}">
            <code v-if="row.resourceUrl" class="mono">{{ row.requestMethod || '' }} {{ row.resourceUrl }}</code>
            <span v-else style="color:#9CA3AF">—</span>
          </template>
        </el-table-column>
        <el-table-column label="原因" min-width="160">
          <template #default="{row}">{{ row.reason || '—' }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="90">
          <template #default="{row}">{{ row.executionTime != null ? row.executionTime + ' ms' : '—' }}</template>
        </el-table-column>
        <el-table-column prop="createdTime" label="时间" width="170" />
        <el-table-column label="操作" width="80"><template #default><el-button link type="primary" size="small">详情</el-button></template></el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { sysAuditLogs } from '@/mock';
import { listAuditLogs } from '@/api/system';
const rows = ref(sysAuditLogs);
const f = reactive({ tid: '', who: '', action: '', biz: '', res: '', t: null });
onMounted(async () => { try { const r = await listAuditLogs(); if (r) rows.value = r; } catch {} });
function actCls(a) {
  // yiti BizAction 枚举：READ / WRITE / DELETE / EXPORT / IMPORT / PERMISSION_CHANGE / STATUS_CHANGE ...
  if (a === 'DELETE') return 'tag-danger';
  if (a === 'PERMISSION_CHANGE' || a === 'EXPORT') return 'tag-warning';
  if (a === 'IMPORT' || a === 'EXECUTE_SQL') return 'tag-info';
  return 'tag-info';
}
</script>

<style lang="scss" scoped>
.mono { font-family: ui-monospace, monospace; font-size: 12px; }
</style>
