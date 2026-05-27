<!-- 我的方案列表 Dialog —— 载入 / 编辑 / 删除
     后端 SavedQuerySummaryDTO 列表字段：id / name / dim / createdTime / updatedTime
     共享方案目前后端未实现，列表全部为本人 PERSONAL 方案。 -->
<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="$emit('update:visible', $event)"
    title="我的查询方案"
    width="780px"
  >
    <el-table :data="schemes" v-loading="loading" size="default" stripe empty-text="暂无已保存方案">
      <el-table-column prop="name" label="方案名" min-width="240" />
      <el-table-column prop="dim" label="维度" width="100">
        <template #default="{ row }">{{ dimLabel(row.dim) }}</template>
      </el-table-column>
      <el-table-column label="更新时间" width="180">
        <template #default="{ row }">{{ formatTime(row.updatedTime || row.createdTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="load(row)">载入</el-button>
          <el-divider direction="vertical" />
          <el-button link size="small" @click="rename(row)">编辑</el-button>
          <el-divider direction="vertical" />
          <el-button type="danger" link size="small" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listSavedQueries, deleteSavedQuery, updateSavedQuery } from '@/api/report';

const props = defineProps({ visible: Boolean });
const emit = defineEmits(['update:visible', 'load']);

const schemes = ref([]);
const loading = ref(false);

const DIM_LABEL = { EMP: '员工', ORG: '机构', CUST: '客户' };
const dimLabel = (d) => DIM_LABEL[d] || d || '-';

function formatTime(t) {
  if (!t) return '-';
  // 后端 LocalDateTime 序列化成 ISO 字符串 "2026-05-07T08:30:00"
  return String(t).replace('T', ' ').slice(0, 16);
}

watch(() => props.visible, (v) => { if (v) refresh(); });

async function refresh() {
  loading.value = true;
  try { schemes.value = await listSavedQueries() || []; }
  finally { loading.value = false; }
}

function load(row) {
  // 仅传 id，由父组件再调 getSavedQuery 拿详情(列表 DTO 不含 metricCodes/subjectIds)
  emit('load', row);
  emit('update:visible', false);
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确认删除方案"${row.name}"？`, '确认', { type: 'warning' });
  } catch { return; }
  try {
    await deleteSavedQuery(row.id);
    ElMessage.success('删除成功');
    refresh();
  } catch (e) {}
}

async function rename(row) {
  let val;
  try {
    const r = await ElMessageBox.prompt('修改方案名称', '编辑方案', {
      inputValue: row.name,
      inputPattern: /\S/,
      inputErrorMessage: '名称不能为空'
    });
    val = r.value.trim();
  } catch { return; }
  if (!val || val === row.name) return;
  try {
    await updateSavedQuery(row.id, { name: val });
    row.name = val;
    ElMessage.success('已保存');
    // 重新拉一次以刷新 updatedTime
    refresh();
  } catch (e) {
    // http.js 拦截器已 ElMessage.error
  }
}
</script>
