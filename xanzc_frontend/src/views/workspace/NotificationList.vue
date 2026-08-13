<template>
<main v-bp-overflow-tooltip class="bp-crud notification-list-page" aria-labelledby="notification-list-title">
    <header class="page-h">
      <PageTitle id="notification-list-title" />
      <div class="actions action-group" role="group" aria-label="通知列表操作">
        <el-button
          type="primary"
          aria-label="全部标记已读"
          :loading="markingAllRead"
          :disabled="markingAllRead"
          @click="doMarkAllRead"
        >
          全部标记已读
        </el-button>
      </div>
    </header>

    <section
      class="card-section data-panel"
      aria-label="通知列表"
      aria-describedby="notification-list-state"
      :aria-busy="loading ? 'true' : 'false'"
    >
      <div class="toolbar">
        <div>
          <h2 id="notification-list-heading" class="section-title">通知列表</h2>
          <p class="hint">未读通知会保留明确状态，标记已读后即时刷新当前列表。</p>
        </div>
        <p id="notification-list-state" class="table-state" role="status" aria-live="polite">
          {{ loading ? '通知列表加载中' : rows.length ? `共 ${pager.total} 条通知` : '暂无通知数据' }}
        </p>
      </div>

      <el-table
        :data="rows"
        size="default"
        v-loading="loading"
        empty-text="暂无通知数据"
        aria-labelledby="notification-list-heading"
        aria-describedby="notification-list-state"
      >
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
            <el-tag v-else class="tag-danger" size="small" effect="plain">未读</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <nav class="pager" aria-label="通知列表分页">
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
      </nav>
    </section>
  </main>
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
const markingAllRead = ref(false);
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
  if (markingAllRead.value) return;
  markingAllRead.value = true;
  try {
    await markAllRead();
    rows.value.forEach(n => { n.isRead = true; });
    ElMessage.success('已全部标记已读');
  } catch (e) {
    ElMessage.error(e?.message || '标记已读失败');
  } finally {
    markingAllRead.value = false;
  }
}

onMounted(reload);
</script>

<style lang="scss" scoped>
.ntf-meta {
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 18px;
  margin-top: var(--space-1);
}
</style>
