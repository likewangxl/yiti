<template>
  <div>
    <div class="page-h">
      <h1>通知中心 <span class="sub">共 {{ pager.total }} 条 · 未读 {{ unreadCount }} 条</span></h1>
      <div class="actions">
        <el-button @click="reload">刷新</el-button>
        <el-button type="primary" :loading="markingAll" @click="onMarkAllRead" :disabled="!unreadCount">
          全部标记已读
        </el-button>
      </div>
    </div>

    <div class="card-section">
      <el-form inline size="default">
        <el-form-item label="状态">
          <el-select v-model="filters.isRead" clearable placeholder="全部" style="width:120px" @change="onFilterChange">
            <el-option :value="false" label="未读" />
            <el-option :value="true" label="已读" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onFilterChange">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card-section">
      <el-table :data="rows" size="default" v-loading="loading" empty-text="暂无通知">
        <el-table-column label="标题 / 内容" min-width="320">
          <template #default="{row}">
            <div class="ntf-title" :class="{ unread: !row.isRead }">
              <span v-if="!row.isRead" class="dot" />
              {{ row.title || '—' }}
            </div>
            <div v-if="row.content" class="ntf-content">{{ row.content }}</div>
          </template>
        </el-table-column>
        <el-table-column label="通知类型" width="120">
          <template #default="{row}">
            <el-tag :class="typeCls(row.notifyType || row.bizType)" effect="plain" size="small">
              {{ row.notifyTypeLabel || typeLabel(row.notifyType || row.bizType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="关联业务" width="180" show-overflow-tooltip>
          <template #default="{row}">
            <template v-if="row.bizId">
              <el-tag v-if="row.bizType" size="small" effect="plain" class="tag-info" style="margin-right:4px">{{ row.bizType }}</el-tag>
              <code class="mono">{{ row.bizId }}</code>
            </template>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{row}">{{ fmtDateTime(row.createdTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{row}">
            <el-tag v-if="row.isRead" size="small" effect="plain" class="tag-info">已读</el-tag>
            <el-tag v-else size="small" effect="plain" type="danger">未读</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="!row.isRead" link type="primary" size="small" @click="onMarkRead(row)">标记已读</el-button>
            <el-button v-if="row.linkUrl || row.bizId" link type="primary" size="small" @click="onJump(row)">跳转</el-button>
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

    <!-- 详情弹窗 -->
    <el-dialog v-model="dtl.show" title="通知详情" width="600px">
      <el-descriptions :column="1" border size="default" v-if="dtl.row">
        <el-descriptions-item label="标题">{{ dtl.row.title || '—' }}</el-descriptions-item>
        <el-descriptions-item label="内容">
          <div style="white-space:pre-wrap;line-height:1.6">{{ dtl.row.content || '无' }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="通知类型">
          <el-tag :class="typeCls(dtl.row.notifyType)" effect="plain" size="small">
            {{ dtl.row.notifyTypeLabel || typeLabel(dtl.row.notifyType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="业务类型">{{ dtl.row.bizType || '—' }}</el-descriptions-item>
        <el-descriptions-item label="关联业务ID">
          <code v-if="dtl.row.bizId" class="mono">{{ dtl.row.bizId }}</code>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="跳转链接">
          <a v-if="dtl.row.linkUrl" class="link" @click="onJump(dtl.row)">{{ dtl.row.linkUrl }}</a>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="dtl.row.isRead" size="small" effect="plain" class="tag-info">已读 · {{ fmtDateTime(dtl.row.readTime) }}</el-tag>
          <el-tag v-else size="small" effect="plain" type="danger">未读</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ fmtDateTime(dtl.row.createdTime) }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="dtl.show = false">关闭</el-button>
        <el-button v-if="dtl.row && !dtl.row.isRead" type="primary" @click="onMarkRead(dtl.row); dtl.show = false">标记已读</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { fmtDateTime } from '@/utils/datetime';
import { listNotifications, markRead, markAllRead, getUnreadNotificationCount } from '@/api/workspace';

const router = useRouter();
const rows = ref([]);
const loading = ref(false);
const markingAll = ref(false);
const unreadCount = ref(0);
const filters = reactive({ isRead: null });
const pager = reactive({ pageNo: 1, pageSize: 20, total: 0 });
const dtl = reactive({ show: false, row: null });

function resetFilters() {
  filters.isRead = null;
  pager.pageNo = 1;
  reload();
}
function onFilterChange() {
  pager.pageNo = 1;
  reload();
}

async function reload() {
  loading.value = true;
  try {
    const params = { pageNo: pager.pageNo, pageSize: pager.pageSize };
    if (filters.isRead !== null && filters.isRead !== '') params.isRead = filters.isRead;
    const r = await listNotifications(params);
    rows.value = r?.records || (Array.isArray(r) ? r : []);
    pager.total = r?.total ?? rows.value.length;
  } catch { rows.value = []; }
  finally { loading.value = false; }
  refreshUnread();
}

async function refreshUnread() {
  try { unreadCount.value = await getUnreadNotificationCount(); } catch {}
  window.dispatchEvent(new Event('notification-changed'));
}

function openDetail(row) {
  dtl.row = row;
  dtl.show = true;
  if (!row.isRead) onMarkRead(row);
}

async function onMarkRead(row) {
  try {
    await markRead(row.id);
    row.isRead = true;
    refreshUnread();
  } catch {}
}

async function onMarkAllRead() {
  markingAll.value = true;
  try {
    const count = await markAllRead();
    ElMessage.success(`已标记 ${count || '全部'} 条为已读`);
    rows.value.forEach(r => { r.isRead = true; });
    refreshUnread();
  } catch {} finally { markingAll.value = false; }
}

function onJump(row) {
  const bizType = row.bizType || '';
  const bizId = row.bizId;
  if (row.linkUrl) {
    router.push(row.linkUrl);
  } else if (bizType === 'TARGET_ADJUST') {
    // 目标修正 → 目标管理「待我审批」（带 taskId 自动弹审批窗）
    router.push({ path: '/perf/targets', query: { tab: 'todo', ...(bizId ? { taskId: bizId } : {}) } });
  } else if (bizType === 'ALLOC_ADJUST') {
    // 业绩调整审批 → 业绩调整「待我审批」
    router.push({ path: '/perf/adjust', query: { tab: 'todo', action: 'open', ...(bizId ? { taskId: bizId } : {}) } });
  } else if ((row.notifyType === 'WORKFLOW' || bizType === 'WORKFLOW') && bizId) {
    // 兜底：其它工作流通知仍进业绩调整待办
    router.push({ path: '/perf/adjust', query: { tab: 'todo', taskId: bizId } });
  }
  if (!row.isRead) onMarkRead(row);
}

const TYPE_MAP = {
  WORKFLOW: { label: '流程通知', cls: 'tag-info' },
  SYSTEM: { label: '系统通知', cls: 'tag-success' },
  BUSINESS: { label: '业务通知', cls: 'tag-warning' },
  PERF: { label: '绩效通知', cls: 'tag-info' },
  APPROVAL: { label: '审批通知', cls: 'tag-warning' }
};
const typeCls = (t) => TYPE_MAP[t]?.cls || 'tag-info';
const typeLabel = (t) => TYPE_MAP[t]?.label || t || '-';

onMounted(reload);
</script>

<style lang="scss" scoped>
.dot {
  display: inline-block; width: 6px; height: 6px; border-radius: 50%;
  background: $danger; margin-right: 8px; vertical-align: middle;
}
.ntf-title {
  font-size: 13px; color: $text-1;
  &.unread { font-weight: 600; }
}
.ntf-content {
  color: $text-3; font-size: 12px; margin-top: 4px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 500px;
}
.mono { font-family: ui-monospace, monospace; font-size: 12px; background: $bg-soft; padding: 2px 6px; border-radius: 3px; }
.text-muted { color: $text-4; }
.link { color: $primary; cursor: pointer; text-decoration: underline; }
.pager { display: flex; justify-content: flex-end; padding: 12px 0; }
.pager :deep(.el-pagination) { flex-wrap: wrap; row-gap: 8px; justify-content: flex-end; }
</style>
