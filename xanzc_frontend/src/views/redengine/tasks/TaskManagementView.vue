<template>
  <div class="re-task-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">任务管理</h2>
        <p class="page-desc">发布定时任务和临时任务，查看各党支部填报进度</p>
      </div>
      <el-button type="primary" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        新增任务
      </el-button>
    </div>

    <div v-if="loadError" class="load-error" role="alert">{{ loadError }}</div>

    <el-card class="filter-card" shadow="never">
      <el-form class="filter-form" @submit.prevent>
        <el-form-item label="任务标题">
          <el-input
            class="filter-control"
            v-model="query.title"
            clearable
            placeholder="请输入任务标题"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="任务性质">
          <el-select class="filter-control" v-model="query.nature" clearable placeholder="请选择" style="width: 132px">
            <el-option label="定时任务" value="PERIODIC" />
            <el-option label="临时任务" value="TEMPORARY" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务类型">
          <el-select class="filter-control" v-model="query.typeCode" clearable placeholder="请选择" style="width: 168px">
            <el-option
              v-for="option in taskTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="周期">
          <el-select class="filter-control" v-model="query.cycle" clearable placeholder="请选择" style="width: 132px">
            <el-option v-for="option in cycleOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select class="filter-control" v-model="query.status" clearable placeholder="请选择" style="width: 132px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item class="filter-actions">
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            查询
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never" v-loading="loading">
      <template #header>
        <div class="table-header">
          <span class="table-title">已发布任务</span>
          <span class="table-count">共 {{ total }} 条</span>
        </div>
      </template>

      <el-table v-if="rows.length" :data="rows" stripe style="width: 100%">
        <el-table-column prop="title" label="任务标题" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button class="title-link" link type="primary" data-test="task-title" @click="openDetail(row)">
              {{ row.title || '—' }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="任务性质" width="100">
          <template #default="{ row }">{{ natureLabel(row.nature) }}</template>
        </el-table-column>
        <el-table-column prop="typeName" label="任务类型" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.typeName || '—' }}</template>
        </el-table-column>
        <el-table-column label="任务对象" min-width="130">
          <template #default="{ row }">{{ audienceLabel(row.audienceType) }}</template>
        </el-table-column>
        <el-table-column label="周期/时间" min-width="230">
          <template #default="{ row }">{{ formatTaskWindow(row) }}</template>
        </el-table-column>
        <el-table-column prop="publishedAt" label="发布时间" width="170" />
        <el-table-column label="填报进度" width="145" align="center">
          <template #default="{ row }">
            <span class="progress-text">{{ row.submittedCount }}/{{ row.targetCount }}</span>
            <span v-if="row.unreportedCount" class="unreported-text">未报 {{ row.unreportedCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="95" align="center">
          <template #default="{ row }">
            <span :class="['status-badge', statusClass(row.status)]">{{ statusLabel(row.status) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-else-if="!loadError" description="暂无已发布任务" />

      <div class="pager">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Plus, Refresh, Search } from '@element-plus/icons-vue';
import { listTasks } from '@/api/redengine';
import {
  BUSINESS_TYPES,
  CYCLE_OPTIONS,
  audienceLabel,
  buildTaskQuery,
  formatTaskWindow,
  natureLabel,
  normalizeTaskList
} from './task-domain';

const router = useRouter();
const cycleOptions = CYCLE_OPTIONS;
const taskTypeOptions = BUSINESS_TYPES;
const query = reactive({ title: '', nature: '', typeCode: '', cycle: '', status: 'PUBLISHED' });
const appliedQuery = ref({ status: 'PUBLISHED' });
const pageNo = ref(1);
const pageSize = ref(10);
const total = ref(0);
const rows = ref([]);
const loading = ref(false);
const loadError = ref('');

const STATUS_LABELS = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  CANCELLED: '已取消'
};

function statusLabel(status) {
  return STATUS_LABELS[status] || status || '—';
}

function statusClass(status) {
  return String(status || '').toLowerCase() || 'unknown';
}

async function load() {
  loading.value = true;
  loadError.value = '';
  try {
    const result = await listTasks(buildTaskQuery(appliedQuery.value, pageNo.value, pageSize.value));
    const page = normalizeTaskList(result);
    rows.value = page.records;
    total.value = page.total;
  } catch {
    rows.value = [];
    total.value = 0;
    loadError.value = '任务列表加载失败，请稍后重试';
  } finally {
    loading.value = false;
  }
}

async function handleSearch() {
  appliedQuery.value = { ...query };
  pageNo.value = 1;
  await load();
}

async function handleReset() {
  Object.assign(query, { title: '', nature: '', typeCode: '', cycle: '', status: 'PUBLISHED' });
  appliedQuery.value = { status: 'PUBLISHED' };
  pageNo.value = 1;
  await load();
}

async function handlePageChange(nextPage) {
  pageNo.value = nextPage;
  await load();
}

async function handleSizeChange(nextSize) {
  pageSize.value = nextSize;
  pageNo.value = 1;
  await load();
}

function handleAdd() {
  router.push('/redengine/task-management/new');
}

function openDetail(row) {
  if (!row?.taskId) {
    ElMessage.warning('任务编号缺失，无法打开详情');
    return;
  }
  router.push(`/redengine/task-management/${row.taskId}`);
}

onMounted(load);

defineExpose({
  load,
  handleSearch,
  handleReset,
  handlePageChange,
  handleSizeChange,
  openDetail,
  taskTypeOptions,
  loadError
});
</script>

<style scoped lang="scss">
.re-task-page {
  padding: 0;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}

.page-title {
  margin: 0 0 6px;
  color: #1e293b;
  font-size: 22px;
  font-weight: 700;
}

.page-desc {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.load-error {
  margin-bottom: 16px;
  padding: 10px 14px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  color: #991b1b;
  background: #fef2f2;
  font-size: 13px;
}

.filter-card,
.table-card {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.filter-card {
  margin-bottom: 16px;
}

.filter-form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 220px), 1fr));
  align-items: end;
  column-gap: var(--space-4);
  row-gap: var(--space-4);
  min-width: 0;
  margin: 0;
}

:deep(.el-card__body) {
  padding: 16px;
}

:deep(.filter-card .el-card__body) {
  padding-bottom: 0;
  min-width: 0;
}

:deep(.filter-form .el-form-item) {
  min-width: 0;
  margin: 0;
  display: flex;
  align-items: center;
}

:deep(.filter-form .el-form-item__label) {
  flex: 0 0 auto;
  padding-right: var(--space-2);
  color: #475569;
  font-size: 13px;
  white-space: nowrap;
}

:deep(.filter-form .el-form-item__content) {
  min-width: 0;
  flex: 1;
}

.filter-control {
  width: 100% !important;
  min-width: 0;
}

.filter-actions {
  display: flex;
  align-items: center;
  margin-right: 0 !important;
  white-space: nowrap;
}

:deep(.filter-actions .el-form-item__content) {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}

.table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.table-title {
  color: #1e293b;
  font-size: 15px;
  font-weight: 600;
}

.table-count {
  color: #94a3b8;
  font-size: 12px;
}

.title-link {
  max-width: 100%;
  padding: 0;
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.progress-text {
  color: #334155;
  font-size: 13px;
}

.unreported-text {
  display: block;
  color: #b45309;
  font-size: 11px;
}

.status-badge {
  display: inline-block;
  padding: 3px 9px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}

.status-badge.published { color: #166534; background: #dcfce7; }
.status-badge.draft { color: #64748b; background: #f1f5f9; }
.status-badge.ended { color: #475569; background: #e2e8f0; }
.status-badge.cancelled { color: #b91c1c; background: #fee2e2; }
.status-badge.unknown { color: #64748b; background: #f1f5f9; }

.pager {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
}

:deep(.el-table) {
  .el-table__header th {
    color: #475569;
    font-size: 13px;
    font-weight: 600;
    background: #f8fafc;
  }

  .el-table__row td {
    padding: 10px 0;
    color: #334155;
    font-size: 13px;
  }
}
</style>
