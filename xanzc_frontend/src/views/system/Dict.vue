<template>
  <div>
    <div class="page-h">
      <h1>字典管理</h1>
      <div class="actions"><el-button type="primary">+ 新增字典项</el-button></div>
    </div>

    <div class="layout">
      <div class="card-section types">
        <div class="hh">字典类型 ({{ types.length }})</div>
        <div v-for="t in types" :key="dictKey(t)"
             :class="['ti', { active: dictKey(t) === picked }]"
             @click="picked = dictKey(t)">
          <div class="t1">{{ dictKey(t) }}</div>
          <div class="t2">{{ dictLabel(t) }} · {{ t.itemCount ?? 0 }} 项</div>
        </div>
      </div>

      <div class="card-section items">
        <el-table :data="rows" size="default" empty-text="暂无字典项">
          <el-table-column label="编码" width="160">
            <template #default="{row}"><code class="mono">{{ row.dictCode || row.code }}</code></template>
          </el-table-column>
          <el-table-column label="标签" min-width="180">
            <template #default="{row}">{{ row.dictLabel || row.label }}</template>
          </el-table-column>
          <el-table-column label="值" min-width="160">
            <template #default="{row}">{{ row.dictValue || row.value }}</template>
          </el-table-column>
          <el-table-column label="排序" width="80" align="center">
            <template #default="{row}">{{ row.sortOrder ?? row.sort }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{row}">
              <el-tag :class="row.status === 'ACTIVE' ? 'tag-success' : 'tag-warning'" effect="plain">{{ row.status === 'ACTIVE' ? '启用' : (row.status || '-') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160">
            <template #default><el-button link type="primary" size="small">编辑</el-button> | <el-button link type="primary" size="small">禁用</el-button></template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue';
import { sysDictTypes, sysDictItems } from '@/mock';
import { listDictTypes, listDictItems } from '@/api/system';

// mock 用 {code,label}，真后端用 {dictType,dictTypeLabel}，兼容两套
const dictKey   = (t) => t.dictType ?? t.code;
const dictLabel = (t) => t.dictTypeLabel ?? t.label ?? t.dictType ?? t.code;

const types = ref(sysDictTypes);
const picked = ref(dictKey(sysDictTypes[0] || {}));
const rows = ref(sysDictItems[picked.value] || []);

async function loadItems(t) {
  try { const r = await listDictItems(t); if (Array.isArray(r)) rows.value = r; } catch {}
}
onMounted(async () => {
  try {
    const r = await listDictTypes();
    if (Array.isArray(r) && r.length) {
      types.value = r;
      picked.value = dictKey(r[0]);  // 真后端第一个类型置选中
    }
  } catch {}
  loadItems(picked.value);
});
watch(picked, loadItems);
</script>

<style lang="scss" scoped>
.layout { display: grid; grid-template-columns: 220px 1fr; gap: 12px; }
.types { padding: 0;
  .hh { padding: 12px 14px; border-bottom: 1px solid $border-1; font-weight: 500; font-size: 13px; }
  .ti { padding: 10px 14px; cursor: pointer; font-size: 12.5px;
    &:hover { background: $bg-soft; }
    &.active { background: $primary-100; color: $primary; }
    .t1 { font-family: ui-monospace, monospace; font-size: 12px; font-weight: 500; }
    .t2 { color: $text-3; font-size: 11px; margin-top: 2px; }
    &.active .t2 { color: $primary-400; }
  }
}
.mono { font-family: ui-monospace, monospace; font-size: 12px; }
.items { padding: 0; }
</style>
