<template>
  <div>
    <div class="page-h">
      <PageTitle />
      <div class="actions">
        <el-button type="primary" @click="doMarkAllRead">全部标记已读</el-button>
      </div>
    </div>

    <div class="card-section">
      <el-table :data="rows" size="default" v-loading="loading" empty-text="暂无通知">
        <el-table-column label="内容" min-width="300">
          <template #default="{ row }">
            <div>{{ row.content || row.title || '-' }}</div>
            <div class="ntf-meta">{{ row.bizType }} · {{ fmtDate(row.createdTime || row.sentTime) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="180">
          <template #default="{ row }">{{ fmtDate(row.createdTime || row.sentTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isRead" size="small" effect="plain">已读</el-tag>
            <el-tag v-else size="small" type="danger" effect="plain">未读</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="pager.pageNo"
          v-model:page-size="pager.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="pager.total"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="reload"
          @current-change="reload"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { listNotifications, markAllRead } from '@/api/workspace';

function fmtDate(t) {
  if (!t) return '-';
  return String(t).replace('T', ' ').slice(0, 16);
}

const rows = ref([]);
const loading = ref(false);
const pager = reactive({ pageNo: 1, pageSize: 20, total: 0 });

async function reload() {
  loading.value = true;
  try {
    const r = await listNotifications({ pageNo: pager.pageNo, pageSize: pager.pageSize });
    rows.value = r?.records || (Array.isArray(r) ? r : []);
    pager.total = r?.total ?? rows.value.length;
  } catch { rows.value = []; }
  finally { loading.value = false; }
}

async function doMarkAllRead() {
  try {
    await markAllRead();
    rows.value.forEach(n => { n.isRead = true; });
    ElMessage.success('已全部标记已读');
  } catch {}
}

onMounted(reload);
</script>

<style lang="scss" scoped>
.pager { margin-top: 14px; display: flex; justify-content: flex-end; }
.pager :deep(.el-pagination) { flex-wrap: wrap; row-gap: 8px; justify-content: flex-end; }
.ntf-meta { font-size: 11px; color: $text-3; margin-top: 4px; }
</style>
