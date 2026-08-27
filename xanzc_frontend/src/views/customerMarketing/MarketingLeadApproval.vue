<template>
  <main v-bp-overflow-tooltip class="page bp-crud marketing-lead-approval" aria-labelledby="marketing-lead-approval-title">
    <header class="page-head">
      <div>
        <PageTitle id="marketing-lead-approval-title" />
        <span>集中处理待审批线索，查看完整业务字段、分配范围、附件和当前客户主档。</span>
      </div>
    </header>

    <div class="approval-stat-grid" aria-label="审批工作台分类">
      <button v-for="item in summaryCards" :key="item.tab" type="button" class="approval-stat-card" :class="[`tone-${item.tone}`, { 'is-selected': tab === item.tab }]" :aria-pressed="tab === item.tab" @click="switchTab(item.tab)">
        <span class="stat-card-top"><span>{{ item.label }}</span><i /></span>
        <strong>{{ item.value }}</strong>
        <small>{{ item.hint }}</small>
      </button>
    </div>

    <section class="approval-card-section">
      <el-tabs v-model="tab" @tab-change="changeTab">
        <el-tab-pane :label="`待审批（${pendingTotal}）`" name="pending" />
        <el-tab-pane :label="`审批记录（${historyTotal}）`" name="history" />
      </el-tabs>
      <div class="approval-toolbar">
        <el-input v-model="query.keyword" clearable placeholder="客户名称 / 统一社会信用代码 / 线索编号 / 提交人" style="width: 420px" @keyup.enter="search" />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilter">重置</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe class="lead-approval-table">
        <el-table-column prop="leadNo" label="线索编号" min-width="155" fixed="left" />
        <el-table-column prop="custName" label="客户名称" min-width="210" show-overflow-tooltip />
        <el-table-column prop="unifiedCreditCode" label="统一社会信用代码" min-width="185" />
        <el-table-column label="线索类型" width="135"><template #default="{row}">{{ leadTypeLabel(row.leadType) }}</template></el-table-column>
        <el-table-column prop="industry" label="所属行业" width="120"><template #default="{row}">{{ row.industry || '-' }}</template></el-table-column>
        <el-table-column label="分配方式" min-width="145"><template #default="{row}">{{ distributionLabel(row.distributionMode) }}</template></el-table-column>
        <el-table-column label="提交人" min-width="145"><template #default="{row}">{{ submitterLabel(row) }}</template></el-table-column>
        <el-table-column label="状态" width="105"><template #default="{row}"><el-tag :type="statusType(row)" effect="plain">{{ statusLabel(row) }}</el-tag></template></el-table-column>
        <el-table-column label="提交时间" min-width="165"><template #default="{row}">{{ formatTime(row.submittedTime || row.task?.startTime) }}</template></el-table-column>
        <el-table-column label="操作" width="210" fixed="right" class-name="operation-cell">
          <template #default="{row}">
            <el-button v-if="tab === 'pending'" link type="success" @click="decide(row, true)">通过</el-button>
            <el-button v-if="tab === 'pending'" link type="danger" @click="decide(row, false)">退回</el-button>
            <el-button link type="primary" @click="openDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager"><el-pagination background layout="total, sizes, prev, pager, next" :total="total" v-model:current-page="query.pageNo" v-model:page-size="query.pageSize" :page-sizes="[10, 20, 50, 100]" @change="load" /></div>
    </section>

    <el-drawer v-model="detailVisible" :title="detail?.lead ? `线索审批详情 · ${detail.lead.leadNo}` : '线索审批详情'" size="min(820px, 96vw)" class="approval-detail-drawer">
      <template v-if="detail?.lead">
        <div class="detail-banner">
          <div><span>客户名称</span><strong>{{ detail.lead.custName }}</strong><small>{{ detail.lead.unifiedCreditCode }}</small></div>
          <el-tag :type="statusType(selectedRow)" effect="plain">{{ statusLabel(selectedRow) }}</el-tag>
        </div>
        <el-alert v-if="detail.currentCustomer?.isAccountOpened === 1" title="该客户已开户，请结合当前主办权和存量客户信息判断本次营销线索。" type="warning" :closable="false" show-icon />
        <el-alert v-if="detail.profileChanged" title="线索提交快照与当前客户主档存在差异，请审批前重点核对。" type="warning" :closable="false" show-icon class="detail-alert" />

        <h3>基础与经营属性</h3>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="客户号">{{ detail.lead.custNoSnapshot || '未开户暂无客户号' }}</el-descriptions-item>
          <el-descriptions-item label="线索类型">{{ leadTypeLabel(detail.lead.leadType) }}</el-descriptions-item>
          <el-descriptions-item label="所属行业">{{ detail.lead.industry || '-' }}</el-descriptions-item>
          <el-descriptions-item label="所属集团类型">{{ detail.lead.groupType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="所属集团名称">{{ detail.lead.groupName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户类型">{{ detail.lead.customerType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="是否基石客户">{{ yesNo(detail.lead.isKeystone) }}</el-descriptions-item>
          <el-descriptions-item label="企业类型">{{ detail.lead.enterpriseType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="是否开户">{{ yesNo(detail.lead.isAccountOpenedSnapshot) }}</el-descriptions-item>
          <el-descriptions-item label="主档资料是否变化">{{ detail.profileChanged ? '是' : '否' }}</el-descriptions-item>
        </el-descriptions>

        <h3>分配与补充资料</h3>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="分配方式">{{ distributionLabel(detail.lead.distributionMode) }}</el-descriptions-item>
          <el-descriptions-item label="客户经理范围">{{ managerScopeLabel }}</el-descriptions-item>
          <el-descriptions-item label="当前主办">{{ currentOwnerLabel }}</el-descriptions-item>
          <el-descriptions-item label="客户标签">{{ detail.tagIds?.length ? detail.tagIds.join('、') : '-' }}</el-descriptions-item>
          <el-descriptions-item label="授信金额">{{ money(detail.lead.creditAmount) }}</el-descriptions-item>
          <el-descriptions-item label="授信敞口金额">{{ money(detail.lead.creditExposureAmount) }}</el-descriptions-item>
          <el-descriptions-item label="客户说明" :span="2">{{ detail.lead.customerDesc || '-' }}</el-descriptions-item>
          <el-descriptions-item label="附件" :span="2">
            <div v-if="detail.attachments?.length" class="attachments"><el-tag v-for="file in detail.attachments" :key="file.id || file.fileId" effect="plain">{{ file.fileName || file.name || file.id || file.fileId }}</el-tag></div>
            <span v-else>-</span>
          </el-descriptions-item>
        </el-descriptions>

        <h3>审批信息</h3>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="提交人">{{ submitterLabel(selectedRow) }}</el-descriptions-item>
          <el-descriptions-item label="当前节点">{{ selectedRow?.task?.taskName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ formatTime(detail.lead.submittedTime || selectedRow?.task?.startTime) }}</el-descriptions-item>
          <el-descriptions-item label="办理时间">{{ formatTime(selectedRow?.task?.completeTime) }}</el-descriptions-item>
          <el-descriptions-item label="审批意见" :span="2">{{ selectedRow?.task?.opinion || detail.lead.rejectReason || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <div class="drawer-footer">
          <span>{{ tab === 'pending' ? '请核对客户主档、分配方式及附件后办理' : '该记录已完成审批' }}</span>
          <div><el-button @click="detailVisible=false">关闭</el-button><el-button v-if="tab === 'pending'" type="danger" plain @click="decide(selectedRow, false)">退回</el-button><el-button v-if="tab === 'pending'" type="success" @click="decide(selectedRow, true)">通过</el-button></div>
        </div>
      </template>
    </el-drawer>
  </main>
</template>

<script setup>
import { computed, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import PageTitle from '@/components/PageTitle.vue';
import { approveLead, getLeadApprovalDetail, listLeadApprovalHistory, listLeadApprovalPending, rejectLead } from '@/api/marketingManagement';

const tab = ref('pending');
const query = reactive({ keyword: '', pageNo: 1, pageSize: 20 });
const rows = ref([]); const total = ref(0); const pendingTotal = ref(0); const historyTotal = ref(0); const loading = ref(false);
const detailVisible = ref(false); const detail = ref(null); const selectedRow = ref(null);
const summaryCards = computed(() => [
  { tab: 'pending', label: '待审批', value: pendingTotal.value, hint: '等待当前审批人处理', tone: 'warning' },
  { tab: 'history', label: '审批记录', value: historyTotal.value, hint: '当前登录人的办理记录', tone: 'success' }
]);
const managerScopeLabel = computed(() => {
  const lead = detail.value?.lead;
  if (!lead) return '-';
  if (lead.distributionMode === 'PUBLIC') return '全行客户经理';
  if (lead.distributionMode === 'OWNER') return lead.mainManagerIdSnapshot || detail.value?.currentCustomer?.mainManagerId || '主办客户经理';
  return detail.value?.managerEmpIds?.length ? detail.value.managerEmpIds.join('、') : '未指定';
});
const currentOwnerLabel = computed(() => {
  const customer = detail.value?.currentCustomer;
  if (!customer?.mainManagerId) return '无主办';
  return `${customer.mainManagerId} · ${customer.mainManagerName || customer.mainManagerId}`;
});

function pageRows(result) { return result?.records || result?.list || result?.items || []; }
function pageTotal(result) { return Number(result?.total || 0); }
async function load() {
  loading.value = true;
  try {
    const result = await (tab.value === 'pending' ? listLeadApprovalPending(query) : listLeadApprovalHistory(query));
    rows.value = pageRows(result); total.value = pageTotal(result);
    if (tab.value === 'pending') pendingTotal.value = total.value; else historyTotal.value = total.value;
  } catch (error) {
    rows.value = []; total.value = 0; ElMessage.error(`审批列表加载失败：${error?.message || '请稍后重试'}`);
  } finally { loading.value = false; }
}
async function loadSummary() {
  const [pending, history] = await Promise.allSettled([listLeadApprovalPending({ pageNo: 1, pageSize: 1 }), listLeadApprovalHistory({ pageNo: 1, pageSize: 1 })]);
  if (pending.status === 'fulfilled') pendingTotal.value = pageTotal(pending.value);
  if (history.status === 'fulfilled') historyTotal.value = pageTotal(history.value);
}
function changeTab() { query.pageNo = 1; load(); }
function switchTab(nextTab) { if (tab.value === nextTab) return; tab.value = nextTab; changeTab(); }
function search() { query.pageNo = 1; load(); }
function resetFilter() { query.keyword = ''; query.pageNo = 1; load(); }
async function openDetail(row) {
  try { selectedRow.value = row; detail.value = await getLeadApprovalDetail(row.leadId); detailVisible.value = true; }
  catch (error) { ElMessage.error(`审批详情加载失败：${error?.message || '请稍后重试'}`); }
}
async function decide(row, approved) {
  if (!row?.leadId) return;
  try {
    const { value } = await ElMessageBox.prompt(approved ? '请输入审批意见（可选）' : '请输入明确的退回原因', approved ? '通过线索' : '退回线索', { inputPlaceholder: approved ? '可不填写' : '请说明需要补充或修正的内容', inputValidator: value => approved || Boolean(value?.trim()) || '退回原因不能为空' });
    const payload = { taskId: row.task?.taskId || row.task?.id, opinion: value?.trim() || '' };
    if (approved) await approveLead(row.leadId, payload); else await rejectLead(row.leadId, payload);
    ElMessage.success(approved ? '审批通过，线索已按分配方式进入对应客户池' : '线索已退回录入人');
    detailVisible.value = false; detail.value = null; selectedRow.value = null;
    await Promise.all([load(), loadSummary()]);
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(`审批失败：${error?.message || '请刷新后重试'}`);
  }
}
function leadTypeLabel(value) { return ({ NEW_ACCOUNT: '新客户开户线索', EXISTING_MARKETING: '存量客户营销线索' }[value] || value || '-'); }
function distributionLabel(value) { return ({ PUBLIC: '全行公开认领', SCOPE: '指定客户经理范围', OWNER: '主办专属' }[value] || value || '-'); }
function statusLabel(row) {
  const value = row?.leadStatus || row?.task?.processStatus;
  return ({ IN_APPROVAL: '待审批', RUNNING: '待审批', APPROVED: '已通过', COMPLETED: '已通过', REJECTED: '已退回', CANCELLED: '已退回' }[value] || value || '-');
}
function statusType(row) { return ({ 待审批: 'warning', 已通过: 'success', 已退回: 'danger' }[statusLabel(row)] || 'info'); }
function submitterLabel(row) {
  const id = row?.task?.startUserEmpNo || row?.submittedBy || row?.task?.startUser;
  const name = row?.task?.startUserName;
  return id && name ? `${id} · ${name}` : (name || id || '-');
}
function yesNo(value) { return value === 1 || value === true ? '是' : value === 0 || value === false ? '否' : '-'; }
function money(value) { return value == null || value === '' ? '-' : `${Number(value).toLocaleString()} 万元`; }
function formatTime(value) { return value ? String(value).replace('T', ' ') : '-'; }

loadSummary();
load();
</script>

<style scoped lang="scss">
.page-head { align-items: flex-start; display: flex; justify-content: space-between; margin-bottom: 14px; }
.page-head h1 { font-size: 18px; margin: 0; }
.page-head span { color: #909399; display: block; font-size: 12px; margin-top: 4px; }
.approval-stat-grid { display: grid; grid-template-columns: repeat(2, minmax(220px, 1fr)); gap: 12px; margin-bottom: 12px; }
.approval-stat-card { position: relative; overflow: hidden; display: grid; gap: 8px; min-height: 112px; padding: 16px 18px; border: 1px solid #dcdfe6; border-radius: 6px; background: #fff; color: inherit; font: inherit; text-align: left; cursor: pointer; transition: border-color .2s ease, box-shadow .2s ease; }
.approval-stat-card::before { position: absolute; inset: 0 auto 0 0; width: 3px; background: var(--el-color-primary); content: ''; }
.approval-stat-card:hover { border-color: var(--el-color-primary-light-5); }
.approval-stat-card:focus-visible { outline: 2px solid var(--el-color-primary-light-3); outline-offset: 2px; }
.approval-stat-card.is-selected { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); box-shadow: 0 0 0 1px var(--el-color-primary-light-7) inset; }
.stat-card-top { display: flex; align-items: center; justify-content: space-between; color: #606266; font-size: 13px; }
.stat-card-top i { width: 8px; height: 8px; border-radius: 50%; background: var(--el-color-primary); box-shadow: 0 0 0 4px var(--el-color-primary-light-9); }
.approval-stat-card strong { color: #303133; font-size: 26px; line-height: 1; }
.approval-stat-card small { color: #909399; font-size: 12px; }
.approval-stat-card.tone-warning::before, .approval-stat-card.tone-warning .stat-card-top i { background: var(--el-color-warning); }
.approval-stat-card.tone-success::before, .approval-stat-card.tone-success .stat-card-top i { background: var(--el-color-success); }
.approval-card-section { padding: 0 18px 18px; border: 1px solid #dcdfe6; border-radius: 6px; background: #fff; }
.approval-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin: 4px 0 12px; }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
.lead-approval-table { width: 100%; }
.detail-banner { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 18px; padding: 16px; border: 1px solid #dcdfe6; border-left: 3px solid var(--el-color-primary); border-radius: 5px; background: var(--el-color-primary-light-9); }
.detail-banner > div { display: grid; gap: 4px; }
.detail-banner span, .detail-banner small { color: #909399; font-size: 11px; }
.detail-banner strong { color: #303133; font-size: 16px; }
.detail-alert { margin-top: 10px; }
.approval-detail-drawer h3 { margin: 22px 0 10px; color: #303133; font-size: 14px; }
.attachments { display: flex; flex-wrap: wrap; gap: 6px; }
.drawer-footer { display: flex; align-items: center; justify-content: space-between; width: 100%; }
.drawer-footer > span { color: #909399; font-size: 12px; }
.drawer-footer > div { display: flex; gap: 8px; }
@media (max-width: 680px) {
  .approval-stat-grid { grid-template-columns: 1fr; }
  .approval-toolbar, .drawer-footer { align-items: stretch; flex-direction: column; }
  .approval-toolbar :deep(.el-input) { width: 100% !important; }
}
</style>
