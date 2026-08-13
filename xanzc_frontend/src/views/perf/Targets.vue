<template>
<main v-bp-overflow-tooltip class="bp-crud targets-page" aria-labelledby="targets-page-title">
    <header class="page-h">
      <PageTitle id="targets-page-title"><span class="sub">统一维护目标方案、待办审批和审批留痕</span></PageTitle>
      <div class="actions action-group" role="group" aria-label="目标管理操作">
        <el-button :loading="loadingPlans || todoLoading || doneLoading" @click="reload">刷新</el-button>
        <el-button v-if="activeTab === 'plans' && canCreatePlan" @click="downloadPlanTpl">下载模板</el-button>
        <el-button v-if="activeTab === 'plans' && canCreatePlan" :loading="importing" :disabled="importing" @click="triggerImportPlan">导入目标方案</el-button>
        <el-button v-if="activeTab === 'plans' && canCreatePlan" type="primary" @click="openCreatePlan">新增方案</el-button>
      </div>
    </header>
    <input ref="importPlanInput" class="file-input" type="file" accept=".xlsx,.xls" aria-label="导入目标方案文件" @change="onImportPlanFile" />

    <el-tabs v-model="activeTab" class="targets-tabs" aria-label="目标管理工作区" @tab-change="onTabChange">
      <el-tab-pane label="目标方案" name="plans">
        <section class="card-section filter-bar" aria-label="目标方案筛选">
          <el-form class="filter-form" inline aria-label="目标方案筛选">
            <el-form-item label="方案搜索"><el-input v-model="f.keyword" clearable placeholder="方案编码 / 名称" aria-label="按方案编码或名称筛选" @keyup.enter="reload" /></el-form-item>
            <el-form-item label="状态"><el-select v-model="f.status" clearable placeholder="全部" aria-label="按方案状态筛选"><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="DISABLED" /></el-select></el-form-item>
            <el-form-item><el-button type="primary" @click="reload">查询</el-button><el-button @click="resetFilters">重置</el-button></el-form-item>
          </el-form>
        </section>

        <section class="card-section data-panel" aria-label="目标方案列表" aria-describedby="targets-plans-state" :aria-busy="loadingPlans ? 'true' : 'false'">
          <div class="toolbar">
            <div><h2 id="targets-plans-heading" class="section-title">目标方案列表</h2><p class="hint">目标方案进入目标值子页后，继续沿用当前方案与权限范围。</p></div>
            <p id="targets-plans-state" class="table-state" role="status" aria-live="polite">{{ plansState }}</p>
          </div>
          <div v-if="plansError" class="table-error" role="alert"><span>{{ plansError }}</span><el-button link type="primary" @click="loadPlans">重新加载</el-button></div>
          <el-table :data="pagedPlans" size="default" :empty-text="plansError ? '加载失败，请重新加载' : '暂无目标方案数据'" v-loading="loadingPlans" aria-labelledby="targets-plans-heading" aria-describedby="targets-plans-state">
            <el-table-column label="目标方案" min-width="260" show-overflow-tooltip><template #default="{ row }"><div>{{ row.planName || row.planCode || '-' }}</div><div v-if="row.planCode" class="cell-meta">{{ row.planCode }}</div></template></el-table-column>
            <el-table-column label="创建时间" min-width="170"><template #default="{ row }">{{ fmtDateTime(row.createdTime) || '-' }}</template></el-table-column>
            <el-table-column label="创建人" min-width="180" show-overflow-tooltip><template #default="{ row }"><div>{{ row.createdByName || row.createdByUsername || row.createdBy || '-' }}</div><div v-if="row.createdByUsername" class="cell-meta">{{ row.createdByUsername }}</div></template></el-table-column>
            <el-table-column label="状态" width="96"><template #default="{ row }"><el-tag :class="statusCls(row.status)" effect="plain" size="small">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
            <el-table-column label="操作" class-name="operation-cell" width="160" fixed="right"><template #default="{ row }"><el-button link type="primary" size="small" @click="openValues(row)">目标值</el-button><el-dropdown v-if="row.createdBy === userStore.user?.empId" trigger="click" popper-class="bp-crud-menu"><el-button link size="small" aria-label="更多目标方案操作">更多</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item @click="openEditPlan(row)">编辑</el-dropdown-item><el-dropdown-item divided class="danger-item" @click="onDeletePlan(row)">删除</el-dropdown-item></el-dropdown-menu></template></el-dropdown></template></el-table-column>
          </el-table>
          <nav class="pager" aria-label="目标方案列表分页"><el-pagination v-model:current-page="pager.pageNo" v-model:page-size="pager.pageSize" :page-sizes="[10, 20, 50, 100]" :total="filteredPlans.length" background layout="total, sizes, prev, pager, next, jumper" /></nav>
        </section>
      </el-tab-pane>

      <el-tab-pane v-if="canApprove" label="待我审批" name="todo">
        <section class="card-section data-panel" aria-label="待我审批列表" aria-describedby="targets-todo-state" :aria-busy="todoLoading ? 'true' : 'false'">
          <div class="toolbar"><div><h2 id="targets-todo-heading" class="section-title">待我审批</h2><p class="hint">审批意见必填；候选任务会先按原有流程认领，再提交审批结果。</p></div><div class="approval-summary"><p id="targets-todo-state" class="table-state" role="status" aria-live="polite">{{ todoState }}</p><el-button type="primary" :disabled="!todoSelection.length || batchDlg.saving" @click="openBatchReview">批量审批（已选 {{ todoSelection.length }} 条）</el-button></div></div>
          <div v-if="todoError" class="table-error" role="alert"><span>{{ todoError }}</span><el-button link type="primary" @click="loadTodos">重新加载</el-button></div>
          <el-table :data="pagedTodos" size="default" :empty-text="todoError ? '加载失败，请重新加载' : '暂无待审批任务'" v-loading="todoLoading" aria-labelledby="targets-todo-heading" aria-describedby="targets-todo-state" @selection-change="onTodoSelectionChange">
            <el-table-column type="selection" width="45" />
            <el-table-column label="方案编号" min-width="150"><template #default="{ row }"><code class="mono">{{ planOfTodo(row)?.planCode || '-' }}</code></template></el-table-column>
            <el-table-column label="方案名称" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ planOfTodo(row)?.planName || '-' }}</template></el-table-column>
            <el-table-column label="关联KPI方案" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ planOfTodo(row) ? kpiLabelOf(planOfTodo(row).kpiSchemeId) : '-' }}</template></el-table-column>
            <el-table-column label="当前节点" width="160"><template #default="{ row }">{{ row.taskName || row.nodeKey || '-' }}</template></el-table-column>
            <el-table-column label="发起人" width="140"><template #default="{ row }">{{ row.startUserName || row.startUser || '-' }}</template></el-table-column>
            <el-table-column label="提交时间" width="170"><template #default="{ row }">{{ fmtDateTime(row.startTime) }}</template></el-table-column>
            <el-table-column label="操作" class-name="operation-cell" width="110" fixed="right"><template #default="{ row }"><el-button link type="primary" size="small" @click="openReview(row)">审批</el-button></template></el-table-column>
          </el-table>
          <nav class="pager" aria-label="待我审批分页"><el-pagination v-model:current-page="todoPager.pageNo" v-model:page-size="todoPager.pageSize" :page-sizes="[10, 20, 50]" :total="todos.length" background layout="total, sizes, prev, pager, next, jumper" /></nav>
        </section>
      </el-tab-pane>

      <el-tab-pane v-if="canApprove" label="已审批" name="done">
        <section class="card-section data-panel" aria-label="已审批列表" aria-describedby="targets-done-state" :aria-busy="doneLoading ? 'true' : 'false'">
          <div class="toolbar"><div><h2 id="targets-done-heading" class="section-title">已审批</h2><p class="hint">只显示已通过或已驳回的目标修正申请，可查看审批记录与原因。</p></div><p id="targets-done-state" class="table-state" role="status" aria-live="polite">{{ doneState }}</p></div>
          <div v-if="doneError" class="table-error" role="alert"><span>{{ doneError }}</span><el-button link type="primary" @click="loadDones">重新加载</el-button></div>
          <el-table :data="pagedDones" size="default" :empty-text="doneError ? '加载失败，请重新加载' : '暂无已审批记录'" v-loading="doneLoading" aria-labelledby="targets-done-heading" aria-describedby="targets-done-state">
            <el-table-column label="方案编号" min-width="150"><template #default="{ row }"><code class="mono">{{ planOfDone(row)?.planCode || '-' }}</code></template></el-table-column>
            <el-table-column label="方案名称" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ planOfDone(row)?.planName || '-' }}</template></el-table-column>
            <el-table-column label="关联KPI方案" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ planOfDone(row) ? kpiLabelOf(planOfDone(row).kpiSchemeId) : '-' }}</template></el-table-column>
            <el-table-column label="发起人" width="160"><template #default="{ row }">{{ row.createdByName || row.createdBy || '-' }}</template></el-table-column>
            <el-table-column label="申请时间" width="170"><template #default="{ row }">{{ fmtDateTime(row.createdTime) }}</template></el-table-column>
            <el-table-column label="审批时间" width="170"><template #default="{ row }">{{ fmtDateTime(row.updatedTime) }}</template></el-table-column>
            <el-table-column label="结果" width="90"><template #default="{ row }"><el-tag v-if="row.status === 'APPROVED'" class="tag-success" effect="plain" size="small">通过</el-tag><el-tag v-else-if="row.status === 'REJECTED'" class="tag-danger" effect="plain" size="small">驳回</el-tag><span v-else>-</span></template></el-table-column>
            <el-table-column label="操作" class-name="operation-cell" width="110" fixed="right"><template #default="{ row }"><el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button></template></el-table-column>
          </el-table>
          <nav class="pager" aria-label="已审批分页"><el-pagination v-model:current-page="donePager.pageNo" v-model:page-size="donePager.pageSize" :page-sizes="[10, 20, 50]" :total="dones.length" background layout="total, sizes, prev, pager, next, jumper" /></nav>
        </section>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="reviewDlg.show" class="bp-crud-dialog" :title="reviewTitle" width="560px" :close-on-click-modal="false" :close-on-press-escape="!reviewDlg.saving" aria-label="目标修正审批确认">
      <div class="review-meta"><div><span class="lab">申请编号：</span><code>{{ reviewDlg.row?.businessKey || reviewDlg.row?.bizId || '-' }}</code></div><div><span class="lab">发起人：</span>{{ reviewDlg.row?.startUserName || reviewDlg.row?.startUser || '-' }}</div><div><span class="lab">提交时间：</span>{{ fmtDateTime(reviewDlg.row?.startTime) }}</div></div>
      <div v-if="reviewDlg.detail" class="review-detail" v-loading="reviewDlg.detailLoading"><div v-for="(adj, i) in reviewDlg.detail.adjustments" :key="i" class="adj-item"><div><span class="lab">指标：</span>{{ metricLabel(adj.metricCode) }}</div><div><span class="lab">原目标值：</span><strong>{{ fmtNum(adj.oldValue) }}</strong><span class="change-text">调整为</span><span class="lab">新目标值：</span><strong class="new-val">{{ fmtNum(adj.newValue) }}</strong></div><div v-if="adj.oldBaseValue != null || adj.newBaseValue != null"><span class="lab">原基础值：</span><strong>{{ adj.oldBaseValue != null ? fmtNum(adj.oldBaseValue) : '-' }}</strong><span class="change-text">调整为</span><span class="lab">新基础值：</span><strong class="new-val">{{ adj.newBaseValue != null ? fmtNum(adj.newBaseValue) : '-' }}</strong></div></div><div><span class="lab">调整原因：</span>{{ reviewDlg.detail.reason || '-' }}</div></div>
      <div v-else-if="reviewDlg.detailLoading" class="detail-loading" v-loading="true"></div>
      <el-form :model="reviewDlg" class="review-form" label-position="top" size="default"><el-form-item label="审批意见" required><el-input v-model="reviewDlg.opinion" type="textarea" :rows="3" placeholder="请填写审批意见（必填，将记入审批日志）" /></el-form-item></el-form>
      <el-alert class="dialog-alert" type="info" :closable="false" show-icon title="通过后更新目标值；驳回后保留原目标值。审批意见会写入审批日志。" />
      <template #footer><el-button :disabled="reviewDlg.saving" @click="reviewDlg.show = false">取消</el-button><el-button type="danger" :loading="reviewDlg.saving" :disabled="reviewDlg.saving" @click="submitReview('REJECT')">确认驳回</el-button><el-button type="primary" :loading="reviewDlg.saving" :disabled="reviewDlg.saving" @click="submitReview('APPROVE')">确认通过</el-button></template>
    </el-dialog>

    <el-dialog v-model="batchDlg.show" class="bp-crud-dialog" title="批量审批确认" width="520px" :close-on-click-modal="false" :close-on-press-escape="!batchDlg.saving" aria-label="批量审批确认">
      <el-alert class="dialog-alert" type="info" :closable="false" show-icon :title="`已选 ${todoSelection.length} 条记录，将对全部所选记录统一处理。`" />
      <el-form :model="batchDlg" class="review-form" label-position="top" size="default"><el-form-item label="审批意见" required><el-input v-model="batchDlg.opinion" type="textarea" :rows="3" placeholder="请填写审批意见（必填，将记入每条审批日志）" /></el-form-item></el-form>
      <template #footer><el-button :disabled="batchDlg.saving" @click="batchDlg.show = false">取消</el-button><el-button type="danger" :loading="batchDlg.saving" :disabled="batchDlg.saving" @click="submitBatchReview('REJECT')">确认驳回</el-button><el-button type="primary" :loading="batchDlg.saving" :disabled="batchDlg.saving" @click="submitBatchReview('APPROVE')">确认通过</el-button></template>
    </el-dialog>

    <el-dialog v-model="detailDlg.show" class="bp-crud-dialog" :title="detailTitle" width="580px" aria-label="目标修正审批详情">
      <div class="review-meta"><div><span class="lab">申请编号：</span><code>{{ detailDlg.row?.businessKey || detailDlg.row?.id || '-' }}</code></div><div><span class="lab">发起人：</span>{{ detailDlg.row?.createdByName || detailDlg.row?.createdBy || '-' }}</div><div><span class="lab">申请时间：</span>{{ fmtDateTime(detailDlg.row?.createdTime) }}</div><div><span class="lab">审批结果：</span><el-tag v-if="detailDlg.row?.status === 'APPROVED'" class="tag-success" effect="plain" size="small">通过</el-tag><el-tag v-else-if="detailDlg.row?.status === 'REJECTED'" class="tag-danger" effect="plain" size="small">驳回</el-tag><span v-else>-</span></div></div>
      <div v-if="detailDlg.detail" class="review-detail" v-loading="detailDlg.loading"><div v-for="(adj, i) in detailDlg.detail.adjustments" :key="i" class="adj-item"><div><span class="lab">指标：</span>{{ metricLabel(adj.metricCode) }}</div><div><span class="lab">原目标值：</span><strong>{{ fmtNum(adj.oldValue) }}</strong><span class="change-text">调整为</span><span class="lab">新目标值：</span><strong class="new-val">{{ fmtNum(adj.newValue) }}</strong></div><div v-if="adj.oldBaseValue != null || adj.newBaseValue != null"><span class="lab">原基础值：</span><strong>{{ adj.oldBaseValue != null ? fmtNum(adj.oldBaseValue) : '-' }}</strong><span class="change-text">调整为</span><span class="lab">新基础值：</span><strong class="new-val">{{ adj.newBaseValue != null ? fmtNum(adj.newBaseValue) : '-' }}</strong></div></div><div><span class="lab">调整原因：</span>{{ detailDlg.detail.reason || '-' }}</div></div>
      <div v-if="detailDlg.history.length" class="review-history"><h3 class="history-title">审批记录</h3><div v-for="(log, i) in sortedHistory" :key="i" class="history-item"><span class="lab">{{ log.action === 'SUBMIT' ? '申请人' : '审批人' }}：</span>{{ log.operatorName || log.operatorEmpNo || log.operator || '-' }}<span v-if="log.operatorEmpNo">（{{ log.operatorEmpNo }}）</span><span class="history-separator">|</span><span class="lab">节点：</span>{{ log.nodeName || log.nodeKey || '-' }}<span class="history-separator">|</span><span class="lab">时间：</span>{{ fmtDateTime(log.operateTime) }}<div v-if="log.opinion"><span class="lab">意见：</span>{{ log.opinion }}</div></div></div>
      <template #footer><el-button type="primary" @click="detailDlg.show = false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="planDlg.show" class="bp-crud-dialog" :title="planDlg.editing ? '编辑目标方案' : '新增目标方案'" width="560px" :close-on-click-modal="false" :close-on-press-escape="!planDlg.saving" aria-label="目标方案编辑">
      <el-form ref="planFormRef" :model="planDlg.form" :rules="planRules" label-width="120px" size="default"><el-form-item label="方案编码" prop="planCode"><el-input v-model="planDlg.form.planCode" :disabled="!!planDlg.editing" placeholder="大写字母开头，如 TP_2026_Q2" /></el-form-item><el-form-item label="方案名称" prop="planName"><el-input v-model="planDlg.form.planName" placeholder="如 2026 年度目标方案" /></el-form-item></el-form>
      <template #footer><el-button :disabled="planDlg.saving" @click="planDlg.show = false">取消</el-button><template v-if="planDlg.editing"><el-button v-if="planDlg.form._status === 'ACTIVE'" type="warning" :disabled="planDlg.saving" @click="togglePlanStatus(planDlg.editing, 'DISABLED')">禁用</el-button><el-button v-else type="success" :disabled="planDlg.saving" @click="togglePlanStatus(planDlg.editing, 'ACTIVE')">启用</el-button></template><el-button type="primary" :loading="planDlg.saving" :disabled="planDlg.saving" @click="onSavePlan">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="kpiTrgDlg.show" class="bp-crud-dialog" title="确认触发 KPI 计算" width="520px" :close-on-click-modal="false" :close-on-press-escape="!kpiTrgDlg.saving" aria-label="触发KPI计算确认">
      <el-alert class="dialog-alert" type="warning" :closable="false" show-icon title="该操作将基于所选数据日期的指标结果与目标值，重算关联 KPI 方案得分。" />
      <el-form ref="kpiTrgFormRef" :model="kpiTrgDlg.form" class="review-form" :rules="kpiTrgRules" label-position="top" size="default"><el-form-item label="数据日期" prop="dataDate"><el-date-picker v-model="kpiTrgDlg.form.dataDate" class="field-control" type="date" value-format="YYYY-MM-DD" placeholder="选择数据日期" /></el-form-item><el-form-item label="KPI方案"><el-input :model-value="kpiTrgDlg.form.schemeLabel" disabled /></el-form-item><el-form-item label="触发原因" prop="reason"><el-input v-model="kpiTrgDlg.form.reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="请说明触发 KPI 计算的原因（将记入审计日志）" /></el-form-item></el-form>
      <template #footer><el-button :disabled="kpiTrgDlg.saving" @click="kpiTrgDlg.show = false">取消</el-button><el-button type="primary" :loading="kpiTrgDlg.saving" :disabled="kpiTrgDlg.saving" @click="onConfirmPlanTrigger">确认执行</el-button></template>
    </el-dialog>
  </main>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listTargets, createTargetPlan, updateTargetPlan, deleteTargetPlan, listKpiRules, getTargetAdjust, listTargetAdjusts, getTargetAdjustApprovalHistory, listTargetValues, listMetrics, calcKpiScore, uploadImportFile } from '@/api/perf';
import { listTodoTasks, listDoneTasks, approveTask, rejectTask, claimTask } from '@/api/workflow';
import { getMyPermissions } from '@/api/auth';
import { useUserStore } from '@/stores/user';

const route  = useRoute();
const router = useRouter();
const userStore = useUserStore();

// 新增方案按钮可见性：仅资材部用户（资财部经办 BACK_FINANCE / 资财部负责人 FINANCE_LEADER）。
// 按业务要求"只有资材部用户可以使用"，不再放行系统管理员(R_ADMIN/SYS_ADMIN)等其它角色。
const canCreatePlan = computed(() => {
  const roles = userStore.user?.roles || [];
  const codes = roles.map(r => (typeof r === 'string' ? r : (r.roleId || r.roleCode)));
  return codes.some(c => c === '238' || c === '129'   // role_id：资财部经办人=238 / 资财部负责人=129
                          || c === 'BACK_FINANCE' || c === 'FINANCE_LEADER');
});

// 资财部负责人(FINANCE_LEADER)：可查看全部目标方案，不受"仅本人创建"限制
const isFinanceLeader = computed(() => {
  const roles = userStore.user?.roles || [];
  const codes = roles.map(r => (typeof r === 'string' ? r : (r.roleId || r.roleCode)));
  return codes.some(c => c === 'FINANCE_LEADER' || c === '129');  // role_id：资财部负责人=129
});

// === 维度 / 周期 / 状态 字典 ===
const BASE_DIMS = [
  { v: 'EMP', l: '员工' },
  { v: 'ORG', l: '机构' }
];
const dimLabel    = (t) => ({ EMP: '员工', ORG: '机构' }[t] || (t || '-'));
const statusLabel = (s) => ({ ACTIVE: '启用', DISABLED: '停用' }[s] || (s || '-'));
const statusCls   = (s) => ({ ACTIVE: 'tag-success', DISABLED: 'tag-info' }[s] || 'tag-info');

// === KPI 方案下拉 + id→label 映射（用于表格"关联 KPI 方案"列翻译） ===
const kpiSchemeOptions = ref([]);
const kpiMap = ref(new Map());
const kpiSchemeById = ref(new Map()); // kpiSchemeId → { code, name }（供"触发"弹框解析关联方案编码）
async function loadKpiSchemeOptions() {
  try {
    const r = await listKpiRules({ pageSize: 100 });
    const arr = Array.isArray(r) ? r : (r?.records || []);
    // 后端 TargetPlanService.create 要求关联 KPI 方案必须 status='ACTIVE'，
    // 选 DISABLED/DRAFT/TRIAL_RUN/INACTIVE 都会被 PERF-42200 拒绝；前端过滤掉非 ACTIVE。
    kpiSchemeOptions.value = arr.filter(s => s.status === 'ACTIVE');
    const m = new Map();
    const byId = new Map();
    // kpiMap 保留全部（含非 ACTIVE）用于列表展示历史方案的名称翻译，不影响下拉过滤
    for (const s of arr) {
      const code = s.schemeCode || s.code || '';
      const name = s.schemeName || s.name || '';
      m.set(s.id, `${code} · ${name}`.replace(/^ · /, '').replace(/ · $/, ''));
      byId.set(s.id, { code, name });
    }
    kpiMap.value = m;
    kpiSchemeById.value = byId;
  } catch { /* 列表仍可显示 ID 兜底 */ }
}

// === 触发 KPI 计算（目标方案关联的 KPI 方案锁定）===
const kpiTrgFormRef = ref(null);
const kpiTrgDlg = reactive({
  show: false, saving: false,
  form: { dataDate: new Date().toISOString().slice(0, 10), schemeCode: '', schemeLabel: '', reason: '' }
});
const kpiTrgRules = {
  dataDate: [{ required: true, message: '请选择数据日期' }],
  reason:   [{ required: true, message: '请填写触发原因', trigger: 'blur' }]
};
function openPlanTrigger(row) {
  const sch = row.kpiSchemeId ? kpiSchemeById.value.get(row.kpiSchemeId) : null;
  if (!sch || !sch.code) {
    return ElMessage.warning('该目标方案未关联有效的 KPI 方案，无法触发计算');
  }
  kpiTrgDlg.form.dataDate = new Date().toISOString().slice(0, 10);
  kpiTrgDlg.form.schemeCode = sch.code;
  kpiTrgDlg.form.schemeLabel = `${sch.code}${sch.name ? ' - ' + sch.name : ''}`;
  kpiTrgDlg.form.reason = '';
  kpiTrgDlg.show = true;
}
async function onConfirmPlanTrigger() {
  try { await kpiTrgFormRef.value.validate(); } catch { return; }
  kpiTrgDlg.saving = true;
  try {
    // 后端 /api/perf/kpi-score/calc：先记审计日志，再调用 KPI 计算服务
    await calcKpiScore({
      dataDate:   kpiTrgDlg.form.dataDate,
      schemeCode: kpiTrgDlg.form.schemeCode,
      reason:     (kpiTrgDlg.form.reason || '').trim()
    });
    ElMessage.success('已触发 KPI 计算');
    kpiTrgDlg.show = false;
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '触发失败');
  } finally { kpiTrgDlg.saving = false; }
}
const kpiLabelOf = (id) => kpiMap.value.get(id) || id || '-';

// 指标编号 → 指标名称映射（审批详情里把"指标：M_0268"显示成"M_0268 · 名称"）
const metricNameMap = ref(new Map());
async function loadMetricMap() {
  try {
    const r = await listMetrics({ pageSize: 1000 });
    const arr = Array.isArray(r) ? r : (r?.records || []);
    const m = new Map();
    for (const x of arr) {
      if (x.metricCode) m.set(x.metricCode, x.metricName || '');
    }
    metricNameMap.value = m;
  } catch { /* 取不到名称时回退仅显示编号 */ }
}
// 展示成 "M_0268 · 指标名称"；无名称或未命中时仅显示编号
const metricLabel = (code) => {
  if (!code) return '-';
  const name = metricNameMap.value.get(code);
  return name ? `${code} · ${name}` : code;
};

// ===== 审批列表展示方案信息（方案编号/名称/关联KPI方案/维度）=====
// planId → 方案对象（来自已加载的目标方案列表 plans）
const planById = computed(() => {
  const m = new Map();
  for (const p of plans.value) if (p.id != null) m.set(String(p.id), p);
  return m;
});
// 目标修正申请索引：businessKey / id → apply，供"待我审批"(workflow task) 反查 planId
const applyByKey = ref(new Map());
async function loadApplyIndex() {
  try {
    const r = await listTargetAdjusts({ pageSize: 100 });
    const arr = Array.isArray(r) ? r : (r?.records || []);
    const m = new Map();
    for (const a of arr) {
      if (a.businessKey) m.set(a.businessKey, a);
      if (a.id) m.set(String(a.id), a);
    }
    applyByKey.value = m;
  } catch { /* 索引取不到则方案列回退显示 '-' */ }
}
// 待我审批行(workflow task) → 方案；先用 businessKey 反查 apply 再取 planId
const planOfTodo = (row) => {
  const a = applyByKey.value.get(row.businessKey) || applyByKey.value.get(row.bizId);
  return a ? planById.value.get(String(a.planId)) : null;
};
// 已审批行(apply) → 方案（apply 自带 planId）
const planOfDone = (row) => planById.value.get(String(row.planId));

// === 方案列表 ===
// f = 筛选条件（双向绑定到控件）。filteredPlans 直接读 f，输入即时过滤。
const f = reactive({ keyword: '', status: '' });

// 重置查询条件：清空筛选项（表格基于 f 即时过滤，清空后自动恢复全量）
function resetFilters() {
  f.keyword = '';
  f.status = '';
}
const plans = ref([]);
const loadingPlans = ref(false);
const plansError = ref('');

// 创建人/发起人姓名一律用后端返回的 createdByName：
// 目标方案 TargetPlanService#425、目标调整 TargetAdjustService#289 均已解析填充。
// 此处原有 loadUserMap() 走管理员接口 /api/admin/users 现拉全量用户建 id→姓名 映射，
// 已于 2026-07-21 删除——它是后端补齐 createdByName 后漏清理的遗留调用，且带来真实故障：
// 该接口资源 A_USER_LIST 仅授予角色 1/3/4/131/169，资财部经办人(238)等角色进页面必得 403，
// http.js 响应拦截器随即弹「没有权限」（页面内 catch 只能防崩，拦不住这个提示）。
// 不要为了显示姓名再把它加回来，也不要改用其它管理员接口。

async function loadPlans() {
  loadingPlans.value = true;
  plansError.value = '';
  try {
    const r = await listTargets({ pageSize: 100 });
    // listTargets 走 unwrapPage：分页响应返回 { records: [...], total } 形态；
    // 非分页直接 Array。两种都要兼容，否则前端永远显示空白。
    plans.value = Array.isArray(r) ? r : (r?.records || []);
  } catch {
    plans.value = [];
    plansError.value = '目标方案加载失败，请检查网络或权限后重新加载。';
  } finally {
    loadingPlans.value = false;
  }
}

// 即时过滤：f 任一字段变化都会触发 computed 重算，无需点"查询"
const filteredPlans = computed(() => {
  let arr = plans.value;
  // 目标方案：仅展示创建人=本人的记录；资财部负责人不受限制，可看全部
  const myEmpId = userStore.user?.empId;
  if (myEmpId && !isFinanceLeader.value) arr = arr.filter(p => p.createdBy === myEmpId);
  if (f.keyword) {
    const kw = String(f.keyword).toLowerCase();
    arr = arr.filter(p => (p.planCode || '').toLowerCase().includes(kw)
                       || (p.planName || '').toLowerCase().includes(kw));
  }
  if (f.status)    arr = arr.filter(p => p.status === f.status);
  return arr;
});

// === 分页（前端 client-side：filteredPlans → slice 给表格） ===
const pager = reactive({ pageNo: 1, pageSize: 10 });
const pagedPlans = computed(() => {
  const start = (pager.pageNo - 1) * pager.pageSize;
  return filteredPlans.value.slice(start, start + pager.pageSize);
});
const plansState = computed(() => {
  if (loadingPlans.value) return '目标方案列表加载中';
  if (plansError.value) return '目标方案列表加载失败';
  return filteredPlans.value.length ? `共 ${filteredPlans.value.length} 个目标方案` : '暂无目标方案数据';
});

// “刷新”按钮：重拉一次方案数据并回到第 1 页（筛选条件由 filteredPlans 即时生效，不需再 apply）。
async function onSearch() {
  pager.pageNo = 1;
  await loadPlans();
}

// === Tabs 状态：目标方案 / 待我审批 / 已审批 ===
const activeTab = ref('plans');

// 「审批资格」权限 gate：与 Adjust.vue 同口径，
// 用户 resourceUrls 含任一 /api/workflow/tasks(*) URL 即视为有审批资格；SYS_ADMIN 一律放行
const canApprove = ref(false);
async function loadCanApprove() {
  try {
    const p = await getMyPermissions();
    if (p?.isSystemAdmin) { canApprove.value = true; return; }
    const urls = p?.resourceUrls || p?.resources || [];
    canApprove.value = Array.isArray(urls) && urls.some(
      u => typeof u === 'string' && u.startsWith('/api/workflow/tasks')
    );
  } catch { canApprove.value = false; }
}

// 时间格式化（仅 tabs 用，避免新引入 dayjs）
function fmtDateTime(v) {
  if (!v) return '-';
  const d = new Date(v);
  if (isNaN(d.getTime())) return String(v);
  const pad = n => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

// === 待我审批（TARGET_ADJUST bizType） ===
const todos = ref([]);
const todoLoading = ref(false);
const todoError = ref('');
const todoPager = reactive({ pageNo: 1, pageSize: 10 });
const pagedTodos = computed(() => {
  const start = (todoPager.pageNo - 1) * todoPager.pageSize;
  return todos.value.slice(start, start + todoPager.pageSize);
});
async function loadTodos() {
  todoLoading.value = true;
  todoError.value = '';
  try {
    const r = await listTodoTasks({ pageSize: 50, bizType: 'TARGET_ADJUST' });
    // listTodoTasks 经 unwrapPage 返回 {records,total}（非数组），必须取 records
    todos.value = Array.isArray(r) ? r : (r?.records || []);
    todoPager.pageNo = 1;
    // 刷新申请索引，保证待审批行能按 businessKey 反查到方案信息
    loadApplyIndex();
  } catch {
    todos.value = [];
    todoError.value = '待我审批加载失败，请刷新后重试。';
  }
  finally { todoLoading.value = false; }
}
const todoState = computed(() => {
  if (todoLoading.value) return '待我审批加载中';
  if (todoError.value) return '待我审批加载失败';
  return todos.value.length ? `待处理 ${todos.value.length} 条` : '暂无待审批任务';
});

// === 已审批（TARGET_ADJUST bizType） ===
const dones = ref([]);
const doneLoading = ref(false);
const doneError = ref('');
const donePager = reactive({ pageNo: 1, pageSize: 10 });
const pagedDones = computed(() => {
  const start = (donePager.pageNo - 1) * donePager.pageSize;
  return dones.value.slice(start, start + donePager.pageSize);
});
async function loadDones() {
  doneLoading.value = true;
  doneError.value = '';
  try {
    // 从业务表拉已完结的修正申请（APPROVED / REJECTED），不从 workflow done-tasks 拉
    // 避免未审批完的中间节点 task 混入"已审批"列表
    const r = await listTargetAdjusts({ pageSize: 100 });
    // listTargetAdjusts 经 unwrapPage 返回 {records,total}（非数组），必须取 records
    const all = Array.isArray(r) ? r : (r?.records || []);
    dones.value = all.filter(d => d.status === 'APPROVED' || d.status === 'REJECTED');
    donePager.pageNo = 1;
  } catch {
    dones.value = [];
    doneError.value = '已审批记录加载失败，请刷新后重试。';
  }
  finally { doneLoading.value = false; }
}
const doneState = computed(() => {
  if (doneLoading.value) return '已审批列表加载中';
  if (doneError.value) return '已审批列表加载失败';
  return dones.value.length ? `共 ${dones.value.length} 条已审批记录` : '暂无已审批记录';
});
// === 已审批详情弹窗（只读） ===
const detailDlg = reactive({ show: false, loading: false, row: null, detail: null, history: [] });
const detailTitle = computed(() => {
  if (!detailDlg.row) return '审批详情';
  const st = detailDlg.row.status === 'APPROVED' ? '已通过' : detailDlg.row.status === 'REJECTED' ? '已驳回' : '';
  return `审批详情${st ? ' · ' + st : ''}`;
});
const sortedHistory = computed(() =>
  [...detailDlg.history].sort((a, b) => {
    const ta = new Date(a.operateTime || 0).getTime();
    const tb = new Date(b.operateTime || 0).getTime();
    return tb - ta;
  })
);
async function openDetail(row) {
  detailDlg.row = row;
  detailDlg.detail = null;
  detailDlg.history = [];
  detailDlg.loading = true;
  detailDlg.show = true;
  const applyId = row.id;
  try {
    const [adj, hist] = await Promise.all([
      getTargetAdjust(applyId).catch(() => null),
      getTargetAdjustApprovalHistory(applyId).catch(() => [])
    ]);
    if (adj) {
      const remark = typeof adj.remark === 'string' ? JSON.parse(adj.remark) : (adj.remark || {});
      detailDlg.detail = { adjustments: remark.adjustments || [], reason: remark.reason || '' };
    }
    detailDlg.history = Array.isArray(hist) ? hist : [];
  } catch { /* 兜底 */ }
  detailDlg.loading = false;
}


// 全局“刷新”按钮按当前页签路由。
async function reload() {
  if (activeTab.value === 'plans') return onSearch();
  if (activeTab.value === 'todo')  return loadTodos();
  if (activeTab.value === 'done')  return loadDones();
}
function onTabChange(name) {
  if (name === 'todo' && !todos.value.length) loadTodos();
  if (name === 'done') loadDones();
}

// === 审批弹窗 ===
const reviewDlg = reactive({ show: false, saving: false, row: null, opinion: '', detail: null, detailLoading: false });
const reviewTitle = computed(() => {
  if (!reviewDlg.row) return '审批';
  return `审批 · ${reviewDlg.row.title || reviewDlg.row.businessKey || ''}`;
});
const fmtNum = (v) => (v == null || v === '') ? '-' : Number(v).toLocaleString();
async function openReview(row) {
  reviewDlg.row = row;
  reviewDlg.opinion = '';
  reviewDlg.detail = null;
  reviewDlg.detailLoading = true;
  reviewDlg.show = true;
  // 根据 bizId（= applyId）拉修正申请详情，解析 remark JSON 得到 adjustments + reason
  const applyId = (row.businessKey || '').split(':')[1] || row.bizId;
  if (applyId) {
    try {
      const d = await getTargetAdjust(applyId);
      const remark = typeof d?.remark === 'string' ? JSON.parse(d.remark) : (d?.remark || {});
      reviewDlg.detail = {
        adjustments: remark.adjustments || [],
        reason: remark.reason || d?.reason || ''
      };
    } catch { reviewDlg.detail = null; }
  }
  reviewDlg.detailLoading = false;
}
// 候选组任务（assignee=null, claimable=true）必须先 claim 才能 approve/reject。
// 同一 taskId 共用进行中的 claim Promise，避免双击或批量审批与单条审批并发时重复认领。
const claimedTaskIds = new Set();
const claimInFlight = new Map();
const decisionInFlight = new Set();
async function ensureClaimed(row) {
  const taskId = row?.taskId;
  if (!row?.claimable || !taskId || claimedTaskIds.has(taskId)) return;
  if (!claimInFlight.has(taskId)) {
    const request = claimTask(taskId)
      .then((result) => {
        claimedTaskIds.add(taskId);
        row.claimable = false;
        return result;
      })
      .finally(() => claimInFlight.delete(taskId));
    claimInFlight.set(taskId, request);
  }
  return claimInFlight.get(taskId);
}

async function submitDecision(row, action, opinion) {
  const taskId = row?.taskId;
  if (!taskId || decisionInFlight.has(taskId)) return false;
  decisionInFlight.add(taskId);
  try {
    await ensureClaimed(row);
    if (action === 'APPROVE') {
      await approveTask(taskId, opinion);
    } else {
      await rejectTask(taskId, opinion);
    }
    return true;
  } finally {
    decisionInFlight.delete(taskId);
  }
}
async function submitReview(action) {
  if (reviewDlg.saving) return;
  if (!reviewDlg.opinion || !reviewDlg.opinion.trim()) {
    return ElMessage.warning('请填写审批意见');
  }
  if (!reviewDlg.row?.taskId) {
    return ElMessage.error('任务 ID 缺失，无法提交');
  }
  reviewDlg.saving = true;
  try {
    const submitted = await submitDecision(reviewDlg.row, action, reviewDlg.opinion);
    if (!submitted) return;
    ElMessage.success(action === 'APPROVE' ? '已通过' : '已驳回');
    reviewDlg.show = false;
    await loadTodos();
    dones.value = [];
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '审批失败');
  } finally {
    reviewDlg.saving = false;
  }
}

// === 批量审批：多选 + 统一通过/驳回 ===
const todoSelection = ref([]);
function onTodoSelectionChange(rows) {
  todoSelection.value = rows || [];
}
const batchDlg = reactive({ show: false, saving: false, opinion: '' });
// 点击「批量审批」：必须已选 ≥1 条，否则报错；通过后弹出批量审批弹窗
function openBatchReview() {
  if (!todoSelection.value.length) {
    return ElMessage.error('请至少选择一条待审批记录');
  }
  batchDlg.opinion = '';
  batchDlg.show = true;
}
// 对所选全部记录统一通过/驳回；审批意见必填；逐条提交，统计成功/失败
async function submitBatchReview(action) {
  if (batchDlg.saving) return;
  if (!batchDlg.opinion || !batchDlg.opinion.trim()) {
    return ElMessage.warning('请填写审批意见');
  }
  const rows = todoSelection.value.filter(r => r && r.taskId);
  if (!rows.length) {
    return ElMessage.error('请至少选择一条待审批记录');
  }
  batchDlg.saving = true;
  let ok = 0;
  let fail = 0;
  for (const row of rows) {
    try {
      const submitted = await submitDecision(row, action, batchDlg.opinion);
      if (submitted) ok++;
    } catch (e) {
      fail++;
    }
  }
  batchDlg.saving = false;
  batchDlg.show = false;
  const verb = action === 'APPROVE' ? '通过' : '驳回';
  if (fail === 0) {
    ElMessage.success(`批量${verb}成功：${ok} 条`);
  } else {
    ElMessage.warning(`批量${verb}完成：成功 ${ok} 条，失败 ${fail} 条`);
  }
  todoSelection.value = [];
  await loadTodos();
  dones.value = [];
}

// === 跳子页（带 planId 给 TargetValues.vue 预选方案） ===
function openValues(row) {
  router.push({
    name: 'PerfTargetValues',
    query: { planId: row.id || row.planCode }
  });
}

// === 新增方案对话框 ===
const planFormRef = ref(null);
const planDlg = reactive({
  show: false, saving: false, editing: null, hasValues: false,
  form: { planCode: '', planName: '', kpiSchemeId: '', targetDim: 'EMP',
          effectiveDate: '', startDate: '', endDate: '' }
});
// 生效日期不再在 UI 暴露：onSavePlan 提交前自动用 startDate 兜底，因此校验改放在 startDate
const planRules = {
  planCode:      [{ required: true, message: '请填写方案编码' },
                  { pattern: /^[A-Z][A-Z0-9_]*$/, message: '方案编码必须以大写字母开头，仅含大写字母/数字/下划线' }],
  planName:      [{ required: true, message: '请填写方案名称' }],
};
// ============ 目标方案模板下载 / 导入 ============
const importPlanInput = ref(null);
const importing = ref(false);

// 下载静态模板（前端 public/templates 下，浏览器直接拉取）
function downloadPlanTpl() {
  window.open('/templates/目标方案上传模板.xlsx', '_blank');
}
// 触发隐藏 file input
function triggerImportPlan() {
  importPlanInput.value && importPlanInput.value.click();
}
// 选中文件后上传：importType=TARGET_PLAN，整批 all-or-none（后端校验不通过会抛错回显）
async function onImportPlanFile(ev) {
  if (importing.value) return;
  const file = ev?.target?.files?.[0];
  if (!file) return;
  importing.value = true;
  try {
    const resp = await uploadImportFile('TARGET_PLAN', file);
    const total = resp?.totalRows ?? 0;
    const errorRows = resp?.errorRows ?? 0;
    if (errorRows > 0) {
      // 理论上 all-or-none 不会走到这里（失败即抛错），兜底提示
      ElMessage.warning(resp?.errorSummary || `导入完成，但有 ${errorRows} 行失败`);
    } else {
      ElMessage.success(`导入成功：共 ${total} 条目标值`);
    }
    await reload();
  } catch (err) {
    // 后端校验失败（指标/对象/维度/日期重叠等）→ all-or-none 抛错，原因回显到页面
    ElMessage.error(err?.bizMsg || err?.message || '导入失败：请检查模板数据');
  } finally {
    importing.value = false;
    // 清空，便于同名文件可再次选择触发 change
    if (importPlanInput.value) importPlanInput.value.value = '';
  }
}

function openCreatePlan() {
  planDlg.editing = null;
  planDlg.hasValues = false;
  Object.assign(planDlg.form, {
    planCode: '', planName: '', kpiSchemeId: '', targetDim: 'EMP',
    effectiveDate: '', startDate: '', endDate: ''
  });
  planDlg.show = true;
}
async function openEditPlan(row) {
  planDlg.editing = row.id || row.planCode;
  planDlg.hasValues = false;
  // 先确保 KPI 方案下拉的 options 已加载，否则 el-select 拿到 kpiSchemeId 也无 option 匹配显示空白
  if (!kpiSchemeOptions.value.length) {
    try { await loadKpiSchemeOptions(); } catch {}
  }
  Object.assign(planDlg.form, {
    planCode: row.planCode || '', planName: row.planName || '',
    kpiSchemeId: row.kpiSchemeId || '', targetDim: row.targetDim || 'EMP',
    effectiveDate: row.effectiveDate || '', startDate: row.startDate || '', endDate: row.endDate || '',
    _status: row.status || 'ACTIVE'
  });
  planDlg.show = true;
  // 该方案是否已存在目标值：有则禁止修改关联 KPI 方案 / 目标维度（避免与已录目标值口径冲突）
  try {
    const tv = await listTargetValues({ planId: row.id, planCode: row.planCode, pageSize: 1 });
    planDlg.hasValues = ((tv?.total ?? tv?.records?.length ?? 0) > 0);
  } catch { planDlg.hasValues = false; }
}
async function onSavePlan() {
  if (planDlg.saving) return;
  try { await planFormRef.value.validate(); } catch { return; }
  if (planDlg.form.startDate && planDlg.form.endDate
      && planDlg.form.startDate > planDlg.form.endDate) {
    return ElMessage.warning('起始日期不能晚于截止日期');
  }
  planDlg.saving = true;
  try {
    let targetCycle = 'QUARTER';
    if (planDlg.form.startDate && planDlg.form.endDate) {
      const days = (new Date(planDlg.form.endDate) - new Date(planDlg.form.startDate)) / 86400000;
      if (days > 92) targetCycle = 'YEAR';
    }
    planDlg.form.effectiveDate = planDlg.form.startDate;
    // _status 是前端内部状态（用于"启用/禁用"按钮显示），后端 DTO 无此字段，提交前必须剔除
    const { _status, ...basePayload } = planDlg.form;
    if (planDlg.editing) {
      const { planCode, ...updatePayload } = basePayload;
      await updateTargetPlan(planDlg.editing, { ...updatePayload, targetCycle });
      ElMessage.success('方案更新成功');
    } else {
      await createTargetPlan({ ...basePayload, targetCycle });
      ElMessage.success('方案创建成功');
    }
    planDlg.show = false;
    loadPlans();
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '保存失败');
  } finally {
    planDlg.saving = false;
  }
}
async function togglePlanStatus(idOrRow, newStatus) {
  const id = typeof idOrRow === 'string' ? idOrRow : (idOrRow.id || idOrRow.planCode);
  try {
    await updateTargetPlan(id, { status: newStatus });
    ElMessage.success(newStatus === 'ACTIVE' ? '已启用' : '已禁用');
    planDlg.show = false;
    loadPlans();
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '操作失败');
  }
}

// 删除目标方案：二次确认 + 警告会清除该目标及其全部目标值 → 后端物理删除 → 提示并刷新
async function onDeletePlan(row) {
  const id = row.id || row.planCode;
  const name = row.planName || row.planCode || '该目标方案';
  try {
    await ElMessageBox.confirm(
      `确认删除「${name}」吗？删除将清除该目标方案及其下所有目标值，且不可恢复！`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
    );
  } catch { return; } // 用户取消
  try {
    await deleteTargetPlan(id);
    ElMessage.success('已删除该目标方案及其全部目标值');
    loadPlans();
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '删除失败');
  }
}

onMounted(async () => {
  await Promise.all([loadPlans(), loadKpiSchemeOptions(), loadCanApprove(), loadMetricMap(), loadApplyIndex()]);

  // 从工作台跳转：?tab=todo&taskId=xxx → 切到待我审批 tab + 自动弹审批窗
  const queryTab = route.query.tab;
  const queryTaskId = route.query.taskId;
  if (queryTab === 'todo' && canApprove.value) {
    activeTab.value = 'todo';
    await loadTodos();
    if (queryTaskId) {
      const row = todos.value.find(t => t.taskId === queryTaskId);
      if (row) openReview(row);
    }
  } else if (queryTab === 'done' && canApprove.value) {
    // 工作台已办「详情」跳转：?tab=done&bizKey=TARGET_ADJUST:{applyId} → 已审批 tab + 弹只读详情
    activeTab.value = 'done';
    await loadDones();
    const applyId = (route.query.bizKey || '').split(':')[1];
    const row = dones.value.find(d => (applyId && d.id === applyId) || (queryTaskId && d.taskId === queryTaskId));
    if (row) openDetail(row);
    else ElMessage.warning('申请不在已审批列表或已变更');
  }
});
</script>

<style lang="scss" scoped>
.targets-page {
  min-width: 0;
}

.file-input {
  block-size: 1px;
  clip: rect(0 0 0 0);
  inline-size: 1px;
  overflow: hidden;
  position: absolute;
  white-space: nowrap;
}

.cell-meta {
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 18px;
}

.mono {
  color: var(--color-text);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

.approval-summary {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  justify-content: flex-end;
}

.table-error {
  align-items: center;
  background: var(--color-danger-bg);
  border: 1px solid var(--color-danger-fg);
  color: var(--color-danger-fg);
  display: flex;
  gap: var(--space-3);
  justify-content: space-between;
  margin-bottom: var(--space-3);
  padding: var(--space-2) var(--space-3);
}

.review-meta,
.review-detail {
  border-left: 3px solid var(--color-info-fg);
  color: var(--color-text);
  font-size: 14px;
  line-height: 22px;
  padding: var(--space-3) var(--space-4);
}

.review-meta {
  background: var(--color-info-bg);
}

.review-detail {
  background: var(--color-warning-bg);
  border-left-color: var(--color-warning-fg);
  margin-top: var(--space-3);
}

.review-meta .lab,
.review-detail .lab,
.review-history .lab {
  color: var(--color-text-muted);
  margin-right: var(--space-1);
}

.change-text,
.history-separator {
  color: var(--color-text-muted);
  margin: 0 var(--space-2);
}

.new-val {
  color: var(--color-warning-fg);
}

.adj-item + .adj-item {
  border-top: 1px solid var(--color-border);
  margin-top: var(--space-2);
  padding-top: var(--space-2);
}

.detail-loading {
  min-height: 64px;
}

.review-form {
  margin-top: var(--space-3);
}

.dialog-alert {
  margin-top: var(--space-3);
}

.review-history {
  margin-top: var(--space-3);
}

.history-title {
  color: var(--color-text-strong);
  font-size: 14px;
  line-height: 22px;
  margin-bottom: var(--space-2);
}

.history-item {
  background: var(--color-surface-soft);
  border: 1px solid var(--color-border);
  font-size: 14px;
  line-height: 22px;
  margin-bottom: var(--space-2);
  padding: var(--space-2) var(--space-3);
}

.field-control {
  width: 100%;
}
</style>
