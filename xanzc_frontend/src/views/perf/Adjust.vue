<template>
  <div>
    <div class="page-h">
      <h1>业绩调整 <span class="sub">比例之和 = 100% · 单行 ≥ 1% · 同一员工不重复</span></h1>
      <div class="actions">
        <el-button @click="reload">刷新</el-button>
        <el-button v-if="activeTab==='mine'" type="primary" @click="openCreate">+ 新建调整申请</el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false"
      title="线下调整后将触发 KPI 历史回算。'我的申请' = 当前账号提交的；'待我审批' = 流程任务派到我的。"
      style="margin-bottom:12px" />

    <el-tabs v-model="activeTab" @tab-change="reload" class="adjust-tabs">
      <!-- ============ 我的申请 ============ -->
      <el-tab-pane label="我的申请" name="mine">
        <div class="card-section table">
          <el-table :data="rows" size="default" empty-text="暂无调整申请" v-loading="loading">
            <el-table-column label="申请编号" width="170">
              <template #default="{row}"><code class="mono">{{ row.applyNo || row.id }}</code></template>
            </el-table-column>
            <el-table-column label="客户" min-width="160">
              <template #default="{row}">{{ row.custName || row.custNo || row.custId || '-' }}</template>
            </el-table-column>
            <el-table-column label="维度" width="100">
              <template #default="{row}"><el-tag class="tag-info" effect="plain">{{ row.allocDim || '-' }}</el-tag></template>
            </el-table-column>
            <el-table-column label="业务类型" width="120">
              <template #default="{row}">{{ row.bizKind || '-' }}</template>
            </el-table-column>
            <el-table-column label="归属机构" width="120">
              <template #default="{row}">{{ row.ownerOrgId || '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{row}">
                <el-tag :class="statusCls(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="申请时间" width="160">
              <template #default="{row}">{{ row.createdTime || row.time || '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{row}">
                <el-button link type="primary" size="small" @click="openView(row)">查看</el-button>
                <el-popconfirm
                  v-if="canWithdraw(row.status)"
                  :title="`确认撤回申请 ${row.applyNo || row.id}？`"
                  @confirm="onWithdraw(row)">
                  <template #reference>
                    <el-button link type="danger" size="small">撤回</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- ============ 待我审批 ============ -->
      <el-tab-pane v-if="canApprove" label="待我审批" name="todo">
        <div class="card-section table">
          <el-table :data="todos" size="default" empty-text="暂无待审批任务" v-loading="todoLoading">
            <el-table-column label="标题" min-width="220">
              <template #default="{row}"><code class="mono">{{ row.title || row.businessKey }}</code></template>
            </el-table-column>
            <el-table-column label="当前节点" width="140" prop="taskName" />
            <el-table-column label="发起人" width="160">
              <template #default="{row}">
                {{ row.startUserName || '-' }}
                <span v-if="row.startUser" class="sub-id">({{ row.startUser }})</span>
              </template>
            </el-table-column>
            <el-table-column label="发起机构" width="220">
              <template #default="{row}">
                <template v-if="row.startOrgName || row.startOrgId">
                  {{ row.startOrgId || '-' }}<span v-if="row.startOrgName"> · {{ row.startOrgName }}</span>
                </template>
                <template v-else>-</template>
              </template>
            </el-table-column>
            <el-table-column label="发起时间" width="160">
              <template #default="{row}">{{ fmt(row.startTime) }}</template>
            </el-table-column>
            <el-table-column label="任务到达" width="160">
              <template #default="{row}">{{ fmt(row.taskCreateTime) }}</template>
            </el-table-column>
            <el-table-column label="SLA" width="90">
              <template #default="{row}">
                <el-tag :class="slaCls(row.slaStatus)" effect="plain">{{ slaLabel(row.slaStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{row}">
                <el-button link type="primary" size="small" @click="openTodoDetail(row)">查看申请</el-button>
                <el-button link type="success" size="small" @click="openApprove(row)">通过</el-button>
                <el-button link type="danger"  size="small" @click="openReject(row)">驳回</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- ============ 已审批 ============ -->
      <el-tab-pane v-if="canApprove" label="已审批" name="done">
        <div class="card-section table">
          <el-table :data="dones" size="default" empty-text="暂无已审批记录" v-loading="doneLoading">
            <el-table-column label="标题" min-width="220">
              <template #default="{row}"><code class="mono">{{ row.title || row.businessKey }}</code></template>
            </el-table-column>
            <el-table-column label="当前节点" width="140" prop="taskName" />
            <el-table-column label="发起人" width="160">
              <template #default="{row}">
                {{ row.startUserName || '-' }}
                <span v-if="row.startUser" class="sub-id">({{ row.startUser }})</span>
              </template>
            </el-table-column>
            <el-table-column label="发起机构" width="220">
              <template #default="{row}">
                <template v-if="row.startOrgName || row.startOrgId">
                  {{ row.startOrgId || '-' }}<span v-if="row.startOrgName"> · {{ row.startOrgName }}</span>
                </template>
                <template v-else>-</template>
              </template>
            </el-table-column>
            <el-table-column label="发起时间" width="160">
              <template #default="{row}">{{ fmt(row.startTime) }}</template>
            </el-table-column>
            <el-table-column label="任务到达" width="160">
              <template #default="{row}">{{ fmt(row.taskCreateTime) }}</template>
            </el-table-column>
            <el-table-column label="SLA" width="90">
              <template #default="{row}">
                <el-tag :class="slaCls(row.slaStatus)" effect="plain">{{ slaLabel(row.slaStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{row}">
                <el-button link type="primary" size="small" @click="openTodoDetail(row)">查看申请</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 新建/查看 弹框 -->
    <el-dialog v-model="dlg.show" :title="dlgTitle" width="900px" :close-on-click-modal="false" @closed="onDlgClosed">
      <el-form ref="dlgFormRef" :model="dlg.form" :rules="dlgRules" label-position="top" size="default">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="客户编号" prop="custNo" required>
              <el-input v-model="dlg.form.custNo" :disabled="dlg.readOnly" placeholder="如 C20260001" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="分配维度" prop="allocDim" required>
              <el-select v-model="dlg.form.allocDim" :disabled="dlg.readOnly" style="width:100%">
                <el-option label="按规则分配（RULE）"  value="RULE" />
                <el-option label="按账户分配（ACCOUNT）" value="ACCOUNT" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="业务类型" prop="bizKind" required>
              <el-select v-model="dlg.form.bizKind" :disabled="dlg.readOnly" style="width:100%">
                <el-option label="对公存款（CORP_DEPOSIT）" value="CORP_DEPOSIT" />
                <el-option label="对公贷款（CORP_LOAN）"    value="CORP_LOAN" />
                <el-option label="对公外汇（CORP_FOREX）"   value="CORP_FOREX" />
                <el-option label="个人存款（PER_DEP）"      value="PER_DEP" />
                <el-option label="个人贷款（PER_LOAN）"     value="PER_LOAN" />
                <el-option label="中间业务（FEE_BIZ）"      value="FEE_BIZ" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="账号">
              <el-input v-model="dlg.form.accountNo" :disabled="dlg.readOnly" placeholder="可空" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="归属机构" prop="ownerOrgId" required>
              <el-input v-model="dlg.form.ownerOrgId" :disabled="dlg.readOnly" placeholder="如 NS001" />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="card-h">
          <div class="title">分配明细</div>
          <span class="weight-sum" :class="{ ok: totalPct === 100 }">
            合计：{{ totalPct }}% {{ totalPct === 100 ? '✓' : '' }}
          </span>
        </div>
        <el-table :data="dlg.form.items" size="default" border>
          <el-table-column label="员工号" width="160">
            <template #default="{row}">
              <el-input v-model="row.empId" :disabled="dlg.readOnly" size="small" placeholder="如 E001" />
            </template>
          </el-table-column>
          <el-table-column label="承担比例 %" width="140">
            <template #default="{row}">
              <el-input-number v-model="row.pct" :disabled="dlg.readOnly" :min="0" :max="100" :precision="0" :controls="false" size="small" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="240">
            <template #default="{row}">
              <el-input v-model="row.remark" :disabled="dlg.readOnly" size="small" />
            </template>
          </el-table-column>
          <el-table-column v-if="!dlg.readOnly" label="操作" width="80" align="center">
            <template #default="{$index}">
              <el-button link type="danger" size="small" @click="dlg.form.items.splice($index, 1)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button v-if="!dlg.readOnly" plain @click="addItemRow" style="margin-top:10px">+ 添加分配人</el-button>

        <el-form-item label="申请原因" prop="reason" required style="margin-top:14px">
          <el-input v-model="dlg.form.reason" :disabled="dlg.readOnly" type="textarea" :rows="2" maxlength="500" show-word-limit
            placeholder="请说明本次调整原因（必填，将记入审批日志）" />
        </el-form-item>
      </el-form>

      <!-- 审批流记录（仅查看模式展示）-->
      <template v-if="dlg.readOnly">
        <div class="card-h">
          <div class="title">审批流记录</div>
          <span class="sub-tip">按时间倒序 · 最新在上</span>
        </div>
        <div v-loading="dlg.approvalLoading" class="approval-wrap">
          <el-empty v-if="!dlg.approvalLoading && (!dlg.approvalLogs || dlg.approvalLogs.length === 0)"
            description="暂无审批记录" :image-size="60" />
          <el-timeline v-else>
            <el-timeline-item
              v-for="(log, idx) in dlg.approvalLogs"
              :key="idx"
              :timestamp="fmt(log.operateTime)"
              placement="top"
              :type="actionTimelineType(log.action)"
              :hollow="idx !== 0">
              <div class="approval-line">
                <el-tag :class="actionCls(log.action)" effect="plain" size="small">
                  {{ actionLabel(log.action) }}
                </el-tag>
                <span class="node">{{ log.nodeName || log.nodeKey || '-' }}</span>
              </div>
              <div class="approval-meta">
                <span class="meta-key">审核人：</span>
                <span>{{ log.operatorName || log.operator || '-' }}</span>
                <span v-if="log.operator && log.operatorName" class="sub-id">({{ log.operator }})</span>
                <span class="meta-sep">·</span>
                <span class="meta-key">机构：</span>
                <span>{{ log.operatorOrgName || '-' }}</span>
              </div>
              <div v-if="log.opinion" class="approval-opinion">意见：{{ log.opinion }}</div>
            </el-timeline-item>
          </el-timeline>
        </div>
      </template>

      <template #footer>
        <el-button @click="dlg.show = false">{{ dlg.readOnly ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!dlg.readOnly" type="primary" :loading="dlg.saving" @click="onSubmit">提交审批</el-button>
      </template>
    </el-dialog>

    <!-- 业务部门经办审批专用对话框（biz_dept_review 节点，含原业绩所属人复选框） -->
    <el-dialog v-model="approveDlg.show" :title="approveDlgTitle" width="520px" :close-on-click-modal="false">
      <el-form label-position="top" size="default">
        <el-form-item label="审批意见">
          <el-input v-model="approveDlg.opinion" type="textarea" :rows="3" maxlength="500" show-word-limit
            placeholder="请填写审批意见（可空）" />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="approveDlg.needsOriginalOwnerApprove">
            是否需要原业绩所属人审批
          </el-checkbox>
          <div class="approve-checkbox-tip">
            勾选后流程进入"原业绩所属人审批"节点；不勾选则直接到部门负责人审批环节
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="approveDlg.saving" @click="onApproveSubmit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  listAdjusts, submitAdjust, withdrawAdjust, getAdjustDetail,
  getAdjustApprovalHistory
} from '@/api/perf';
import { listTodoTasks, listDoneTasks, approveTask, rejectTask, claimTask } from '@/api/workflow';
import { getMyPermissions } from '@/api/auth';
import { useUserStore } from '@/stores/user';

const STATUS_LABEL = {
  DRAFT: '草稿', IN_APPROVAL: '审批中', APPROVED: '已通过',
  REJECTED: '已驳回', WITHDRAWN: '已撤回'
};
const statusLabel = (s) => STATUS_LABEL[s] || s || '-';
const statusCls = (s) => ({
  APPROVED: 'tag-success', IN_APPROVAL: 'tag-warning',
  DRAFT: 'tag-info', REJECTED: 'tag-danger', WITHDRAWN: 'tag-info'
}[s] || 'tag-info');
const canWithdraw = (s) => s === 'IN_APPROVAL' || s === 'DRAFT';

const SLA_LABEL = { GREEN: '正常', YELLOW: '预警', RED: '超时' };
const slaLabel = (s) => SLA_LABEL[s] || s || '-';
const slaCls = (s) => ({ GREEN: 'tag-success', YELLOW: 'tag-warning', RED: 'tag-danger' }[s] || 'tag-info');

const ACTION_LABEL = {
  SUBMIT: '提交', APPROVE: '通过', REJECT: '驳回', CLAIM: '签收', TRANSFER: '转办'
};
const actionLabel = (a) => ACTION_LABEL[a] || a || '-';
const actionCls = (a) => ({
  APPROVE: 'tag-success', REJECT: 'tag-danger',
  SUBMIT: 'tag-info', CLAIM: 'tag-warning', TRANSFER: 'tag-warning'
}[a] || 'tag-info');
const actionTimelineType = (a) => ({
  APPROVE: 'success', REJECT: 'danger',
  SUBMIT: 'primary', CLAIM: 'warning', TRANSFER: 'warning'
}[a] || 'info');

const fmt = (s) => {
  if (!s) return '-';
  // 后端可能给 ISO 字符串或本地字符串，统一截到分钟
  const str = String(s).replace('T', ' ');
  return str.length >= 16 ? str.substring(0, 16) : str;
};

const userStore = useUserStore();

// ============ tab 状态 ============
const activeTab = ref('mine');

// 是否有"工作流任务/审批"相关 API 权限。没有则隐藏"待我审批"+"已审批"两个 tab。
// 判断口径：用户 resourceUrls 含任一 /api/workflow/tasks(*) URL，即视为有审批资格。
// SYS_ADMIN（isSystemAdmin=true）一律放行。
const canApprove = ref(false);

async function loadCanApprove() {
  try {
    const p = await getMyPermissions();
    if (p?.isSystemAdmin) { canApprove.value = true; return; }
    const urls = p?.resourceUrls || p?.resources || [];
    canApprove.value = Array.isArray(urls) && urls.some(
      u => typeof u === 'string' && u.startsWith('/api/workflow/tasks')
    );
  } catch {
    canApprove.value = false;
  }
}

// ============ 我的申请 ============
const rows = ref([]);
const loading = ref(false);
async function reloadMine() {
  loading.value = true;
  try {
    const empId = userStore.user?.empId;
    if (!empId) { rows.value = []; return; }
    const r = await listAdjusts({ pageSize: 50, createdBy: empId });
    if (Array.isArray(r)) rows.value = r;
  } catch {} finally { loading.value = false; }
}

// ============ 待我审批 ============
const todos = ref([]);
const todoLoading = ref(false);
async function reloadTodo() {
  todoLoading.value = true;
  try {
    const r = await listTodoTasks({ pageSize: 50, bizType: 'ALLOC_ADJUST' });
    if (Array.isArray(r)) todos.value = r;
  } catch {} finally { todoLoading.value = false; }
}

// ============ 已审批（已办） ============
const dones = ref([]);
const doneLoading = ref(false);
async function reloadDone() {
  doneLoading.value = true;
  try {
    // 后端 queryDoneList 已按 taskAssignee=当前用户 + finished 过滤
    // bizType 限定到 ALLOC_ADJUST 与"待我审批" tab 对齐
    const r = await listDoneTasks({ pageSize: 50, bizType: 'ALLOC_ADJUST' });
    if (Array.isArray(r)) dones.value = r;
  } catch {} finally { doneLoading.value = false; }
}

// ============ 统一刷新（按 tab 路由） ============
function reload() {
  if (activeTab.value === 'todo') reloadTodo();
  else if (activeTab.value === 'done') reloadDone();
  else reloadMine();
}

async function openTodoDetail(row) {
  // businessKey 形如 ALLOC_ADJUST:{applyId}
  const applyId = (row.businessKey || '').split(':')[1] || row.bizId;
  if (!applyId) return ElMessage.warning('无法识别申请 ID');
  try {
    const d = await getAdjustDetail(applyId);
    // 复用查看弹框
    dlg.readOnly = true;
    dlg.viewingId = applyId;
    dlg.approvalLogs = [];
    Object.assign(dlg.form, {
      custNo: d.custNo || d.custId || '',
      allocDim: d.allocDim, bizKind: d.bizKind, accountNo: d.accountNo,
      ownerOrgId: d.ownerOrgId, reason: d.reason || d.remark,
      items: (d.items || []).map(it => ({ empId: it.empId, pct: it.pct ?? it.ratio, remark: it.remark }))
    });
    dlg.show = true;
    loadApprovalHistory(applyId);
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '查看失败');
  }
}

// 候选任务（assignee=null, claimable=true）走 approve/reject 前必须先 claim 成为受理人
async function ensureClaimed(row) {
  if (row.claimable) {
    await claimTask(row.taskId);
  }
}

// 业务部门经办（biz_dept_review）节点专用审批对话框 state
// 该节点表单含 needsOriginalOwnerApprove CHECKBOX，由经办勾选决定是否走原业绩所属人审批分支
const approveDlg = reactive({
  show: false, saving: false, row: null,
  opinion: '同意', needsOriginalOwnerApprove: false
});
const approveDlgTitle = computed(
  () => `审批通过：${approveDlg.row?.title || approveDlg.row?.businessKey || ''}`
);

async function openApprove(row) {
  // 公司部/零售部/业务部门经办审批节点：弹自定义对话框含 needsOriginalOwnerApprove 复选框
  if (row.nodeKey === 'biz_dept_review') {
    approveDlg.row = row;
    approveDlg.opinion = '同意';
    approveDlg.needsOriginalOwnerApprove = false;
    approveDlg.saving = false;
    approveDlg.show = true;
    return;
  }
  // 其他节点：沿用简单意见输入
  let opinion;
  try {
    const r = await ElMessageBox.prompt('请填写审批意见（可空）', `审批通过：${row.title || row.businessKey}`, {
      type: 'success', inputValue: '同意'
    });
    opinion = r.value;
  } catch { return; }
  try {
    await ensureClaimed(row);
    await approveTask(row.taskId, opinion);
    ElMessage.success('已通过');
    reloadTodo();
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '审批失败');
  }
}

async function onApproveSubmit() {
  if (!approveDlg.row) return;
  approveDlg.saving = true;
  try {
    await ensureClaimed(approveDlg.row);
    await approveTask(approveDlg.row.taskId, approveDlg.opinion, {
      needsOriginalOwnerApprove: !!approveDlg.needsOriginalOwnerApprove
    });
    ElMessage.success('已通过');
    approveDlg.show = false;
    reloadTodo();
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '审批失败');
  } finally {
    approveDlg.saving = false;
  }
}

async function openReject(row) {
  let opinion;
  try {
    const r = await ElMessageBox.prompt('请填写驳回原因（必填）', `驳回：${row.title || row.businessKey}`, {
      type: 'warning', inputPattern: /\S+/, inputErrorMessage: '驳回原因必填'
    });
    opinion = r.value;
  } catch { return; }
  try {
    await ensureClaimed(row);
    await rejectTask(row.taskId, opinion);
    ElMessage.success('已驳回');
    reloadTodo();
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '驳回失败');
  }
}

// ============ 新建/查看 弹框 ============
const dlgFormRef = ref(null);
const dlg = reactive({
  show: false, readOnly: false, saving: false, viewingId: null,
  approvalLogs: [], approvalLoading: false,
  form: {
    custNo: '', allocDim: 'RULE', bizKind: 'CORP_DEPOSIT',
    accountNo: '', ownerOrgId: '', reason: '',
    items: [{ empId: '', pct: 100, remark: '' }]
  }
});
const dlgTitle = computed(() => dlg.readOnly ? '查看调整申请' : '新建调整申请');
const totalPct = computed(() => dlg.form.items.reduce((s, x) => s + (Number(x.pct) || 0), 0));
const dlgRules = {
  custNo:     [{ required: true, message: '请填写客户编号' }],
  allocDim:   [{ required: true, message: '请选择分配维度' }],
  bizKind:    [{ required: true, message: '请选择业务类型' }],
  ownerOrgId: [{ required: true, message: '请填写归属机构编码' }],
  reason:     [
    { required: true, message: '请填写申请原因（必填，将记入审批日志）' },
    { max: 500, message: '申请原因不超过 500 字' }
  ]
};

function defaultItem() { return { empId: '', pct: 0, remark: '' }; }
function addItemRow() { dlg.form.items.push(defaultItem()); }
function onDlgClosed() {
  dlg.viewingId = null;
  dlg.readOnly = false;
  dlg.approvalLogs = [];
  dlg.approvalLoading = false;
}

// 拉审批流记录（后端已按时间倒序，前端直接渲染）
async function loadApprovalHistory(applyId) {
  if (!applyId) {
    dlg.approvalLogs = [];
    return;
  }
  dlg.approvalLoading = true;
  try {
    const list = await getAdjustApprovalHistory(applyId);
    dlg.approvalLogs = Array.isArray(list) ? list : [];
  } catch (err) {
    // 审批流拉取失败不阻塞主流程，仅清空 + 控制台告警
    console.warn('[Adjust] 审批流记录加载失败', err);
    dlg.approvalLogs = [];
  } finally {
    dlg.approvalLoading = false;
  }
}

function openCreate() {
  dlg.readOnly = false;
  dlg.viewingId = null;
  Object.assign(dlg.form, {
    custNo: '', allocDim: 'RULE', bizKind: 'CORP_DEPOSIT',
    accountNo: '', ownerOrgId: '', reason: '',
    items: [{ empId: '', pct: 100, remark: '' }]
  });
  dlg.show = true;
}
async function openView(row) {
  dlg.readOnly = true;
  dlg.viewingId = row.id || row.applyNo;
  dlg.approvalLogs = [];
  Object.assign(dlg.form, {
    custNo: row.custNo || row.custId || '',
    allocDim: row.allocDim || 'RULE',
    bizKind: row.bizKind || 'CORP_DEPOSIT',
    accountNo: row.accountNo || '',
    ownerOrgId: row.ownerOrgId || '',
    reason: row.reason || '',
    items: row.items?.length ? [...row.items] : [{ empId: '', pct: 100, remark: '' }]
  });
  dlg.show = true;
  try {
    const d = await getAdjustDetail(dlg.viewingId);
    if (d?.id) Object.assign(dlg.form, {
      custNo: d.custNo || d.custId, allocDim: d.allocDim, bizKind: d.bizKind,
      accountNo: d.accountNo, ownerOrgId: d.ownerOrgId, reason: d.reason,
      items: (d.items || []).map(it => ({ empId: it.empId, pct: it.pct ?? it.shareRatio, remark: it.remark }))
    });
  } catch {}
  loadApprovalHistory(dlg.viewingId);
}

async function onSubmit() {
  try { await dlgFormRef.value.validate(); } catch { return; }
  if (!dlg.form.items.length) return ElMessage.warning('至少添加 1 条分配明细');
  for (let i = 0; i < dlg.form.items.length; i++) {
    const it = dlg.form.items[i];
    if (!it.empId) return ElMessage.warning(`第 ${i + 1} 行：请填写员工号`);
    if (!(it.pct >= 1)) return ElMessage.warning(`第 ${i + 1} 行：承担比例须 ≥ 1%`);
  }
  const seen = new Set();
  for (const it of dlg.form.items) {
    if (seen.has(it.empId)) return ElMessage.warning(`员工 ${it.empId} 出现多次，请合并`);
    seen.add(it.empId);
  }
  if (totalPct.value !== 100) return ElMessage.warning(`分配比例合计须为 100%，当前 ${totalPct.value}%`);

  dlg.saving = true;
  try {
    await submitAdjust({
      custNo:     dlg.form.custNo,
      allocDim:   dlg.form.allocDim,
      bizKind:    dlg.form.bizKind,
      accountNo:  dlg.form.accountNo || undefined,
      ownerOrgId: dlg.form.ownerOrgId,
      reason:     dlg.form.reason,
      items: dlg.form.items.map(it => ({ empId: it.empId, ratio: Number(it.pct) }))
    });
    ElMessage.success('已提交审批');
    dlg.show = false;
    reloadMine();
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '提交失败');
  } finally { dlg.saving = false; }
}

async function onWithdraw(row) {
  let reason;
  try {
    const r = await ElMessageBox.prompt('请填写撤回原因', '撤回申请', {
      type: 'warning', inputPattern: /\S+/, inputErrorMessage: '撤回原因必填'
    });
    reason = r.value;
  } catch { return; }
  try {
    await withdrawAdjust(row.id || row.applyNo, reason);
    ElMessage.success('已撤回');
    reloadMine();
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '撤回失败');
  }
}

onMounted(async () => {
  await loadCanApprove();
  // 没审批资格强制回到"我的申请"，避免 URL/路由复用残留 activeTab='todo' 的边角
  if (!canApprove.value && activeTab.value !== 'mine') activeTab.value = 'mine';
  reload();
});
</script>

<style lang="scss" scoped>
.page-h h1 .sub { font-size: 13px; color: $text-3; margin-left: 12px; font-weight: 400; }
.adjust-tabs { :deep(.el-tabs__nav-wrap)::after { background: $border-1; } }
.table { padding: 0; padding-bottom: 12px; }
.mono { font-family: ui-monospace, monospace; font-size: 12px; }
.sub-id { font-size: 12px; color: $text-3; margin-left: 4px; }

.card-h {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 0 10px;
  border-bottom: 1px solid $border-1;
  margin: 8px 0 14px;
  .title { font-size: 14px; font-weight: 600; color: $text-1; flex: 1; }
  .weight-sum {
    font-size: 13px; color: $text-3; font-weight: 500;
    &.ok { color: $success; font-weight: 700; }
  }
  .sub-tip { font-size: 12px; color: $text-3; }
}

.approve-checkbox-tip {
  margin-left: 24px;
  font-size: 12px;
  color: $text-3;
  margin-top: 2px;
  line-height: 1.4;
}

.approval-wrap {
  padding: 4px 0 4px 6px;
  min-height: 80px;
  .approval-line {
    display: flex; align-items: center; gap: 8px;
    font-size: 13px;
    .node { font-weight: 600; color: $text-1; }
  }
  .approval-meta {
    margin-top: 4px;
    font-size: 12px; color: $text-2;
    .meta-key { color: $text-3; }
    .meta-sep { margin: 0 8px; color: $text-3; }
  }
  .approval-opinion {
    margin-top: 4px;
    font-size: 12px; color: $text-2;
    background: $bg-soft;
    padding: 6px 8px; border-radius: 4px;
    word-break: break-all;
  }
}
</style>
