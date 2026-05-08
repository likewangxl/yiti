<template>
  <div>
    <div class="page-h">
      <h1>系统配置 · KV</h1>
      <div class="actions"><el-button type="primary">+ 新增配置</el-button></div>
    </div>

    <div class="card-section">
      <el-table :data="rows" size="default" empty-text="暂无配置">
        <el-table-column label="Key" width="280">
          <template #default="{row}"><code class="mono">{{ row.configKey }}</code></template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{row}">
            <el-tag :class="typeCls(row.valueType)" effect="plain">{{ row.valueType || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="configValue" label="当前值" min-width="160" show-overflow-tooltip />
        <el-table-column prop="remark" label="说明" min-width="220" />
        <el-table-column label="状态" width="90">
          <template #default="{row}"><el-tag :class="row.status === 'ACTIVE' ? 'tag-success' : 'tag-warning'" effect="plain">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="updatedTime" label="更新时间" width="170" />
        <el-table-column label="操作" width="80">
          <template #default><el-button link type="primary" size="small">编辑</el-button></template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { sysConfig } from '@/mock';
import { listConfigs } from '@/api/system';
const rows = ref(sysConfig);
const typeCls = (t) => ({ BOOLEAN: 'tag-success', NUMBER: 'tag-warning', JSON: 'tag-info' }[t] || 'tag-info');
onMounted(async () => {
  try {
    const r = await listConfigs();
    // 真后端返回数组（unwrapPage 已处理），空数组也是合法响应
    if (Array.isArray(r)) rows.value = r;
  } catch {}
});
</script>

<style lang="scss" scoped>
.mono { font-family: ui-monospace, monospace; font-size: 12px; background: $bg-soft; padding: 2px 6px; border-radius: 3px; }
</style>
