<template>
  <main v-bp-overflow-tooltip class="bp-crud notifications-page" aria-labelledby="notifications-page-title" :aria-busy="loading ? 'true' : 'false'">
    <header class="page-h">
      <PageTitle id="notifications-page-title"><span class="sub">共 {{ pager.total }} 条通知，其中 {{ unreadCount }} 条未读。</span></PageTitle>
      <div class="actions action-group" role="group" aria-label="通知中心操作">
        <el-button @click="reload">刷新</el-button>
        <el-button type="primary" :loading="markingAll" :disabled="!unreadCount || markingAll" @click="onMarkAllRead">全部标记已读</el-button>
      </div>
    </header>

    <section class="card-section filter-bar" aria-label="通知筛选">
      <el-form class="filter-form" inline size="default" aria-label="通知筛选条件">
        <el-form-item label="阅读状态">
          <el-select v-model="filters.isRead" clearable placeholder="全部" aria-label="按已读状态筛选" style="width:140px">
            <el-option :value="false" label="未读" />
            <el-option :value="true" label="已读" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onFilterChange">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section
      class="card-section data-panel notifications-table-panel"
      aria-label="通知列表"
      aria-labelledby="notifications-table-heading"
      aria-describedby="notifications-table-state"
      :aria-busy="loading ? 'true' : 'false'"
    >
      <div class="toolbar">
        <div>
          <h2 id="notifications-table-heading" class="section-title">通知列表</h2>
          <p class="hint">打开未读通知会立即调用既有已读接口；跳转行为继续遵循通知关联业务。</p>
        </div>
        <p id="notifications-table-state" class="table-state" role="status" aria-live="polite">{{ loading ? '通知加载中' : rows.length ? `共 ${pager.total} 条，未读 ${unreadCount} 条` : '暂无通知' }}</p>
      </div>
      <p v-if="loadError" class="error-state" role="alert">{{ loadError }} <el-button link type="primary" @click="reload">重试</el-button></p>
      <el-table :data="rows" size="default" v-loading="loading" empty-text="暂无通知" aria-labelledby="notifications-table-heading" aria-describedby="notifications-table-state">
        <el-table-column label="标题 / 内容" min-width="340">
          <template #default="{ row }">
            <div class="notification-title" :class="{ unread: !row.isRead }"><span v-if="!row.isRead" class="unread-dot" aria-label="未读"></span>{{ row.title || '-' }}</div>
            <div v-if="row.content" class="notification-content">{{ row.content }}</div>
          </template>
        </el-table-column>
        <el-table-column label="通知类型" width="128"><template #default="{ row }"><el-tag :class="typeCls(row.notifyType || row.bizType)" effect="plain" size="small">{{ row.notifyTypeLabel || typeLabel(row.notifyType || row.bizType) }}</el-tag></template></el-table-column>
        <el-table-column label="关联业务" width="190" show-overflow-tooltip><template #default="{ row }"><template v-if="row.bizId"><el-tag v-if="row.bizType" size="small" effect="plain" class="tag-info">{{ row.bizType }}</el-tag><code class="mono">{{ row.bizId }}</code></template><span v-else class="text-muted">-</span></template></el-table-column>
        <el-table-column label="时间" width="176"><template #default="{ row }">{{ fmtDateTime(row.createdTime) }}</template></el-table-column>
        <el-table-column label="状态" width="94" align="center"><template #default="{ row }"><el-tag v-if="row.isRead" size="small" effect="plain" class="tag-info">已读</el-tag><el-tag v-else size="small" effect="plain" class="tag-danger">未读</el-tag></template></el-table-column>
        <el-table-column label="操作" class-name="operation-cell" width="210" fixed="right">
          <template #default="{ row }">
            <div class="row-actions" role="group" :aria-label="`${row.title || '通知'} 操作`">
              <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
              <el-dropdown v-if="!row.isRead || row.linkUrl || row.bizId" trigger="click" popper-class="bp-crud-menu">
                <el-button link size="small" aria-label="更多通知操作">更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="!row.isRead" :disabled="isMarking(row.id)" @click="onMarkRead(row)">{{ isMarking(row.id) ? '标记中…' : '标记已读' }}</el-dropdown-item>
                    <el-dropdown-item v-if="row.linkUrl || row.bizId" @click="onJump(row)">跳转</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
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

    <el-dialog v-model="dtl.show" class="bp-crud-dialog" title="通知详情" width="600px">
      <el-descriptions v-if="dtl.row" :column="1" border size="default" label-width="120px" class="notification-detail">
        <el-descriptions-item label="标题">{{ dtl.row.title || '-' }}</el-descriptions-item>
        <el-descriptions-item label="内容"><div class="detail-content">{{ dtl.row.content || '无' }}</div></el-descriptions-item>
        <el-descriptions-item label="通知类型"><el-tag :class="typeCls(dtl.row.notifyType)" effect="plain" size="small">{{ dtl.row.notifyTypeLabel || typeLabel(dtl.row.notifyType) }}</el-tag></el-descriptions-item>
        <el-descriptions-item v-if="currentNode" label="当前审批环节">{{ currentNode }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag v-if="dtl.row.isRead" size="small" effect="plain" class="tag-info">已读 · {{ fmtDateTime(dtl.row.readTime) }}</el-tag><el-tag v-else size="small" effect="plain" class="tag-danger">未读</el-tag></el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ fmtDateTime(dtl.row.createdTime) }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="dtl.show = false">关闭</el-button>
        <el-button v-if="dtl.row && !dtl.row.isRead" type="primary" :loading="isMarking(dtl.row.id)" :disabled="isMarking(dtl.row.id)" @click="onMarkRead(dtl.row)">标记已读</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { fmtDateTime } from '@/utils/datetime';
import { parseApprovalNode } from '@/utils/notify';
import { listNotifications, markRead, markAllRead, getUnreadNotificationCount } from '@/api/workspace';

const router = useRouter();
const rows = ref([]);
const loading = ref(false);
const loadError = ref('');
const markingAll = ref(false);
const unreadCount = ref(0);
const filters = reactive({ isRead: null });
const pager = reactive({ pageNo: 1, pageSize: 20, total: 0 });
const dtl = reactive({ show: false, row: null });
const currentNode = computed(() => parseApprovalNode(dtl.row?.content));

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
  loadError.value = '';
  try {
    const params = { pageNo: pager.pageNo, pageSize: pager.pageSize };
    if (filters.isRead !== null && filters.isRead !== '') params.isRead = filters.isRead;
    const result = await listNotifications(params);
    rows.value = result?.records || (Array.isArray(result) ? result : []);
    pager.total = result?.total ?? rows.value.length;
  } catch (error) {
    rows.value = [];
    pager.total = 0;
    loadError.value = `通知加载失败：${error?.message || '请稍后重试'}`;
  } finally {
    loading.value = false;
  }
  await refreshUnread();
}
async function refreshUnread() {
  try {
    unreadCount.value = await getUnreadNotificationCount();
  } catch {
    // 未读角标失败不覆盖已成功加载的通知列表。
  }
  window.dispatchEvent(new Event('notification-changed'));
}

const markingIds = ref(new Set());
const isMarking = (id) => id != null && markingIds.value.has(String(id));
function setMarking(id, value) {
  const next = new Set(markingIds.value);
  if (value) next.add(String(id));
  else next.delete(String(id));
  markingIds.value = next;
}
function openDetail(row) {
  dtl.row = row;
  dtl.show = true;
  if (!row.isRead) onMarkRead(row);
}
async function onMarkRead(row) {
  if (!row?.id || row.isRead || isMarking(row.id)) return;
  setMarking(row.id, true);
  try {
    await markRead(row.id);
    row.isRead = true;
    await refreshUnread();
  } catch (error) {
    ElMessage.error(`标记已读失败：${error?.message || error}`);
  } finally {
    setMarking(row.id, false);
  }
}
async function onMarkAllRead() {
  if (markingAll.value || !unreadCount.value) return;
  markingAll.value = true;
  try {
    const count = await markAllRead();
    ElMessage.success(`已标记 ${count || '全部'} 条为已读`);
    rows.value.forEach(row => { row.isRead = true; });
    await refreshUnread();
  } catch (error) {
    ElMessage.error(`全部标记已读失败：${error?.message || error}`);
  } finally {
    markingAll.value = false;
  }
}
function onJump(row) {
  const bizType = row.bizType || '';
  const bizId = row.bizId;
  if (row.linkUrl) router.push(row.linkUrl);
  else if (bizType === 'TARGET_ADJUST') router.push({ path: '/perf/targets', query: { tab: 'todo', ...(bizId ? { taskId: bizId } : {}) } });
  else if (bizType === 'ALLOC_ADJUST') router.push({ path: '/perf/adjust', query: { tab: 'todo', action: 'open', ...(bizId ? { taskId: bizId } : {}) } });
  else if ((row.notifyType === 'WORKFLOW' || bizType === 'WORKFLOW') && bizId) router.push({ path: '/perf/adjust', query: { tab: 'todo', taskId: bizId } });
  if (!row.isRead) onMarkRead(row);
}

const TYPE_MAP = {
  WORKFLOW: { label: '流程通知', cls: 'tag-info' },
  SYSTEM: { label: '系统通知', cls: 'tag-success' },
  BUSINESS: { label: '业务通知', cls: 'tag-warning' },
  PERF: { label: '绩效通知', cls: 'tag-info' },
  APPROVAL: { label: '审批通知', cls: 'tag-warning' }
};
const typeCls = (type) => TYPE_MAP[type]?.cls || 'tag-info';
const typeLabel = (type) => TYPE_MAP[type]?.label || type || '-';

onMounted(reload);
</script>

<style lang="scss" scoped>
.notifications-table-panel { min-width: 0; }
.notification-title { color: var(--color-text-strong); font-size: 14px; }
.notification-title.unread { font-weight: 600; }
.unread-dot { background: var(--color-danger-fg); border-radius: 50%; display: inline-block; height: 6px; margin-right: var(--space-2); width: 6px; }
.notification-content { color: var(--color-text-muted); font-size: 12px; line-height: 18px; margin-top: var(--space-1); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mono { color: var(--color-text); font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace; font-size: 12px; margin-left: var(--space-1); }
.text-muted { color: var(--color-text-muted); }
.row-actions { align-items: center; display: flex; flex-wrap: wrap; gap: var(--space-1); }
.notification-detail :deep(.el-descriptions__label) { min-width: 120px; white-space: nowrap; }
.detail-content { line-height: 1.6; white-space: pre-wrap; }
.error-state { background: var(--color-danger-bg); border-left: 3px solid var(--color-danger-fg); color: var(--color-danger-fg); font-size: 12px; line-height: 18px; margin-bottom: var(--space-3); padding: var(--space-2) var(--space-3); }
</style>
