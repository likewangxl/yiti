<template>
  <section class="page bp-crud" v-bp-overflow-tooltip>
    <header class="page-head">
      <div>
        <PageTitle />
        <span>本人认领关系按服务端结果拆分为未触达和已触达；已取消或他人开户关闭的关系保留追溯。</span>
      </div>
    </header>

    <el-card shadow="never" class="filter-card">
      <el-tabs v-model="tab" @tab-change="changeTab">
        <el-tab-pane label="未触达" name="UNTOUCHED" />
        <el-tab-pane label="已触达" name="TOUCHED" />
      </el-tabs>
      <el-form inline @submit.prevent>
        <el-form-item label="关键词">
          <el-input
            v-model="keyword"
            clearable
            placeholder="客户名称 / 统一社会信用代码"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="sourceType" clearable placeholder="全部来源" style="width: 170px">
            <el-option label="公开认领" value="PUBLIC" />
            <el-option label="指定范围认领" value="SCOPE" />
            <el-option label="主办专属" value="OWNER" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-table :data="rows" v-loading="loading" border stripe class="table">
      <el-table-column label="客户名称" min-width="190" show-overflow-tooltip>
        <template #default="{ row }">{{ row.custName || row.customerName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="unifiedCreditCode" label="统一社会信用代码" min-width="190" show-overflow-tooltip />
      <el-table-column label="来源" width="135">
        <template #default="{ row }">{{ sourceLabel(row.allocationSource || row.sourceType || row.source) }}</template>
      </el-table-column>
      <el-table-column label="认领时间" width="170">
        <template #default="{ row }">{{ fmt(row.claimTime || row.claimedAt) }}</template>
      </el-table-column>
      <el-table-column label="触达状态" width="120">
        <template #default="{ row }">
          <el-tag :type="touchTagType(row)">{{ touchStatusLabel(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="当前结果 / 取消原因" min-width="230" show-overflow-tooltip>
        <template #default="{ row }">
          <div v-if="relationReason(row)" class="disabled-reason">
            <strong>{{ relationResultLabel(row) }}</strong>
            <span>{{ relationReason(row) }}</span>
          </div>
          <span v-else>{{ relationResultLabel(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="客户摘要" min-width="260" show-overflow-tooltip>
        <template #default="{ row }">
          <div class="customer-summary">
            <span v-if="row.contactPerson || row.contactMobile">联系人：{{ [row.contactPerson, row.contactMobile].filter(Boolean).join(' / ') }}</span>
            <span v-if="row.industryName || row.customerTypeName">{{ [row.industryName, row.customerTypeName].filter(Boolean).join(' · ') }}</span>
            <span v-if="row.tagNames?.length">客户标签：<span v-for="tagName in row.tagNames" :key="tagName">{{ tagName }}</span></span>
            <span v-else-if="tagNames(row).length">客户标签：{{ tagNames(row).join('、') }}</span>
            <span v-if="!row.contactPerson && !row.contactMobile && !row.industryName && !row.customerTypeName && !tagNames(row).length">-</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="235" fixed="right" class-name="operation-cell">
        <template #default="{ row }">
          <template v-if="isRelationOperable(row)">
            <el-button
              v-if="hasRunningTask(row)"
              link
              type="primary"
              @click="viewTask(row)"
            >办理触达</el-button>
            <el-button
              v-else-if="isTerminalTask(row)"
              link
              type="warning"
              :disabled="row.canReTouch === false"
              @click="openStart(row, true)"
            >再次触达</el-button>
            <el-button
              v-else
              link
              type="primary"
              :disabled="row.canTouch === false"
              @click="openStart(row, false)"
            >发起触达</el-button>
          </template>
          <span v-else class="operation-disabled">不可操作：{{ relationReason(row) || '当前认领关系已关闭' }}</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="pageNo"
        v-model:page-size="pageSize"
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-sizes="[10, 20, 50]"
        @change="load"
      />
    </div>

    <el-dialog
      v-model="startDlg.show"
      :title="startDlg.followUp ? '再次发起触达' : '发起首次触达'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-alert
        title="提交后，系统将按发起时间 + 后台触达时限配置自动计算计划完成时间"
        type="info"
        :closable="false"
        show-icon
        class="start-sla-hint"
      />
      <el-form label-width="160px" class="start-customer-info">
        <el-form-item label="客户名称">{{ startDlg.row?.custName || startDlg.row?.customerName || '-' }}</el-form-item>
        <el-form-item label="客户统一社会信用代码">{{ startDlg.row?.unifiedCreditCode || '-' }}</el-form-item>
        <el-form-item label="客户联系人">{{ startDlg.row?.contactPerson || '-' }}</el-form-item>
        <el-form-item label="联系方式">{{ startDlg.row?.contactMobile || '-' }}</el-form-item>
        <el-form-item v-if="startDlg.followUp" label="再次触达原因" required>
          <el-input v-model="startDlg.reason" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="startDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="startDlg.saving" @click="submitStart">确认发起</el-button>
      </template>
    </el-dialog>

    <TouchTaskDetailDialog
      v-model="detail.show"
      :task-id="detail.taskId"
      :allow-write="detail.allowWrite"
      @changed="load"
    />
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import TouchTaskDetailDialog from '@/components/TouchTaskDetailDialog.vue';
import { listClaimedCustomers, startFirstTouch, startFollowUpTouch } from '@/api/customerMarketing';

const tab = ref('UNTOUCHED');
const keyword = ref('');
const sourceType = ref('');
const rows = ref([]);
const total = ref(0);
const loading = ref(false);
const pageNo = ref(1);
const pageSize = ref(20);
const detail = reactive({ show: false, taskId: '', allowWrite: true });
const startDlg = reactive({ show: false, row: null, followUp: false, reason: '', saving: false });

function pageOf(result) {
  if (Array.isArray(result)) return { records: result, total: result.length };
  const candidates = [result, result?.page, result?.data, result?.data?.page];
  const page = candidates.find(item => Array.isArray(item?.records)
    || Array.isArray(item?.list)
    || Array.isArray(item?.content)
    || Array.isArray(item?.rows));
  if (!page) return { records: [], total: 0 };
  const records = page.records || page.list || page.content || page.rows;
  return { records, total: Number(page.total ?? page.totalCount ?? records.length) || 0 };
}

async function load() {
  loading.value = true;
  try {
    const r = await listClaimedCustomers({ tab: tab.value,
      keyword: keyword.value || undefined,
      sourceType: sourceType.value || undefined,
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    });
    const page = pageOf(r);
    rows.value = page.records;
    total.value = Number(r?.total || 0);
    if (!total.value) total.value = page.total;
  } catch {
    rows.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

function changeTab() {
  pageNo.value = 1;
  load();
}

function search() {
  pageNo.value = 1;
  load();
}

function reset() {
  keyword.value = '';
  sourceType.value = '';
  search();
}

function openStart(row, followUp) {
  Object.assign(startDlg, {
    show: true,
    row,
    followUp,
    reason: '',
    saving: false,
  });
}

async function submitStart() {
  if (!startDlg.row) return;
  if (startDlg.followUp && !startDlg.reason.trim()) {
    ElMessage.warning('请填写再次触达原因');
    return;
  }
  startDlg.saving = true;
  try {
    const claimId = startDlg.row.claimId || startDlg.row.id;
    const task = startDlg.followUp
      ? await startFollowUpTouch(claimId, { reason: startDlg.reason.trim() })
      : await startFirstTouch(claimId);
    ElMessage.success('触达任务已发起');
    startDlg.show = false;
    await load();
    const taskId = task?.id || task?.taskId;
    if (taskId) {
      detail.taskId = taskId;
      detail.allowWrite = true;
      detail.show = true;
    }
  } catch (error) {
    ElMessage.error(`触达任务发起失败：${error?.message || '请稍后重试'}`);
  } finally {
    startDlg.saving = false;
  }
}

function viewTask(row) {
  const taskId = row.latestTaskId || row.taskId;
  if (!taskId) return;
  detail.taskId = taskId;
  detail.allowWrite = isRelationOperable(row);
  detail.show = true;
}

function claimStatus(row) {
  if (!row) return '';
  return String(row.claimStatus || row.relationStatus || row.relationState || '').toUpperCase();
}

function isRelationCancelled(row) {
  const statuses = [row?.claimStatus, row?.relationStatus, row?.relationState, row?.status]
    .filter(Boolean)
    .map(value => String(value).toUpperCase());
  return statuses.some(value => ['CANCELLED', 'CANCELED', 'CLOSED', 'CLOSED_OPENED_BY_OTHER'].includes(value));
}

function isRelationOperable(row) {
  if (row?.canOperate === false || isRelationCancelled(row)) return false;
  return true;
}

function taskStatus(row) {
  return String(row?.latestTaskStatus || row?.taskStatus || row?.touchStatus || '').toUpperCase();
}

function hasRunningTask(row) {
  return Boolean(row?.latestTaskId || row?.taskId) && ['PENDING', 'IN_PROGRESS', 'PROCESSING', 'RUNNING'].includes(taskStatus(row));
}

function isTerminalTask(row) {
  return ['SUCCESS', 'COMPLETED', 'CANCELLED', 'CANCELED', 'FAILED', 'REJECTED'].includes(taskStatus(row));
}

function touchStatusLabel(row) {
  if (isRelationCancelled(row)) return '关系已取消';
  return {
    NOT_STARTED: '未触达',
    PENDING: '待触达',
    IN_PROGRESS: '触达中',
    PROCESSING: '触达中',
    RUNNING: '触达中',
    SUCCESS: '已完成',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    CANCELED: '已取消',
    FAILED: '已终止',
    REJECTED: '已终止',
  }[taskStatus(row)] || (taskStatus(row) ? taskStatus(row) : '未触达');
}

function touchTagType(row) {
  if (isRelationCancelled(row)) return 'info';
  if (['SUCCESS', 'COMPLETED'].includes(taskStatus(row))) return 'success';
  if (['IN_PROGRESS', 'PROCESSING', 'RUNNING'].includes(taskStatus(row))) return 'primary';
  if (['CANCELLED', 'CANCELED', 'FAILED', 'REJECTED'].includes(taskStatus(row))) return 'danger';
  return 'warning';
}

function relationResultLabel(row) {
  const status = claimStatus(row);
  if (status === 'CLOSED_OPENED_BY_OTHER') return '他人已开户，触达已关闭';
  if (status === 'WON_OPENED') return '本人已完成开户';
  if (isRelationCancelled(row)) return '认领关系已取消';
  if (hasRunningTask(row)) return '触达任务进行中';
  if (isTerminalTask(row)) return '当前任务已结束，可再次触达';
  return '有效认领关系，可发起触达';
}

function relationReason(row) {
  return row?.disabledReason || row?.cancelReason || row?.canceledReason || row?.closeReason || row?.closedReason || '';
}

function sourceLabel(value) {
  return {
    PUBLIC: '公开认领',
    PUBLIC_CLAIM: '公开认领',
    CLAIM: '客户认领',
    POOL: '公开认领',
    SCOPE: '指定范围认领',
    SCOPE_CLAIM: '指定范围认领',
    ASSIGNED: '指定范围认领',
    OWNER: '主办专属',
    CROSS_ORG_MARKETING: '跨机构营销',
    RESTART: '再次触达',
  }[String(value || '').toUpperCase()] || value || '-';
}

function tagNames(row) {
  const source = row?.tagNames || row?.tags || row?.currentTags || [];
  if (!Array.isArray(source)) return source ? [String(source)] : [];
  return [...new Set(source.map(item => typeof item === 'string' ? item : item?.tagName || item?.name).filter(Boolean))];
}

function fmt(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-';
}

load();
</script>

<style scoped lang="scss">
.page-head { display: flex; align-items: baseline; gap: 12px; margin-bottom: 12px; }
.page-head h1 { margin: 0; font-size: 18px; }
.page-head span { font-size: 12px; color: #909399; }
.filter-card { margin-bottom: 12px; }
.table { margin-top: 12px; }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
.start-sla-hint { margin-bottom: 16px; }
.start-customer-info :deep(.el-form-item) { margin-bottom: 10px; }
.start-customer-info :deep(.el-form-item__content) { color: #303133; font-weight: 500; }
.customer-summary { display: grid; gap: 3px; line-height: 1.35; }
.disabled-reason { display: grid; gap: 2px; line-height: 1.35; }
.disabled-reason strong { color: #f56c6c; font-size: 12px; }
.disabled-reason span { color: #909399; font-size: 11px; }
.operation-disabled { display: inline-block; color: #909399; font-size: 12px; line-height: 1.35; }
</style>
