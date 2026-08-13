<template>
  <main v-bp-overflow-tooltip class="bp-crud workflow-monitor-page" aria-labelledby="workflow-monitor-page-title" :aria-busy="loading ? 'true' : 'false'">
    <header class="page-h">
      <PageTitle id="workflow-monitor-page-title"><span class="sub">秘书岗按本机构、行长按全行查看进行中和已完成的审批流实例。</span></PageTitle>
      <div class="actions action-group" role="group" aria-label="审批流监控操作">
        <el-button @click="reload">刷新</el-button>
      </div>
    </header>

    <section class="card-section filter-bar monitor-filter" aria-label="审批流监控筛选">
    <el-tabs v-model="query.status" class="monitor-tabs" @tab-change="onSearch">
      <el-tab-pane label="进行中" name="RUNNING" />
      <el-tab-pane label="已完成" name="COMPLETED" />
    </el-tabs>

    <!-- 过滤区：业务类型 / 关键字 / 发起人 -->
      <el-form class="filter-form" :inline="true" size="default" aria-label="审批流监控筛选条件" @submit.prevent="onSearch">
        <el-form-item label="业务类型">
          <el-select v-model="query.bizType" clearable placeholder="全部" style="width:160px">
            <el-option v-for="o in BIZ_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" clearable placeholder="标题关键字" style="width:200px"
            @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item label="发起人">
          <el-input v-model="query.startedBy" clearable placeholder="工号" style="width:160px"
            @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="card-section data-panel monitor-table-panel" aria-label="审批流实例列表" aria-labelledby="workflow-monitor-heading" aria-describedby="workflow-monitor-state" :aria-busy="loading ? 'true' : 'false'">
      <div class="toolbar">
        <div>
          <h2 id="workflow-monitor-heading" class="section-title">流程实例</h2>
          <p class="hint">转交仅适用于存在活跃任务的运行中流程；候选组任务未签收时显示“指派”。</p>
        </div>
        <p id="workflow-monitor-state" class="table-state" role="status" aria-live="polite">{{ loading ? '审批流实例加载中' : rows.length ? `共 ${total} 条流程实例` : '暂无流程记录' }}</p>
      </div>
      <p v-if="loadError" class="error-state" role="alert">{{ loadError }} <el-button link type="primary" @click="reload">重试</el-button></p>
      <el-table :data="rows" size="default" v-loading="loading" empty-text="暂无流程记录" aria-labelledby="workflow-monitor-heading" aria-describedby="workflow-monitor-state">
        <el-table-column label="流程 · 标题" min-width="240" show-overflow-tooltip>
          <template #default="{row}">
            <code class="mono">{{ row.businessKey || '-' }}</code>
            <div class="sub-name">{{ row.title || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="业务类型" width="110">
          <template #default="{row}">{{ bizTypeLabel(row.bizType) }}</template>
        </el-table-column>
        <el-table-column label="当前处理人" width="170">
          <template #default="{row}">
            <template v-if="row.currentAssignee">
              <div>{{ row.currentAssignee }}</div>
              <div v-if="row.currentAssigneeOrgCode" class="sub-id">{{ row.currentAssigneeOrgCode }}</div>
            </template>
            <template v-else>-</template>
          </template>
        </el-table-column>
        <el-table-column label="发起人" width="170">
          <template #default="{row}">
            <template v-if="row.startUser">
              <div>{{ row.startUser }}</div>
              <div v-if="row.startUserOrgCode" class="sub-id">{{ row.startUserOrgCode }}</div>
            </template>
            <template v-else>-</template>
          </template>
        </el-table-column>
        <el-table-column label="发起时间" width="170">
          <template #default="{row}">{{ fmtDateTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{row}">
            <el-tag :class="statusCls(row.processStatus)" effect="plain" size="small">{{ statusLabel(row.processStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" class-name="operation-cell" width="150" fixed="right">
          <template #default="{row}">
            <div class="row-actions" role="group" :aria-label="`${row.title || row.businessKey || '流程实例'} 操作`">
              <el-button link type="primary" size="small" @click="openDetail(row)">查看</el-button>
              <el-dropdown trigger="click" popper-class="bp-crud-menu">
                <el-button link size="small" aria-label="更多流程实例操作">更多</el-button>
                <template #dropdown><el-dropdown-menu><el-dropdown-item :disabled="!canTransfer(row)" :title="canTransfer(row) ? '' : '流程已结束或暂无活跃任务，不可转交/指派'" @click="openTransfer(row)">{{ transferLabel(row) }}</el-dropdown-item></el-dropdown-menu></template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <nav class="pager" aria-label="审批流监控分页">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="reload"
          @size-change="() => { pageNo = 1; reload(); }"
        />
      </nav>
    </section>

    <!--
      详情抽屉：基础信息 + 流程节点 + 审批历史 + 转交历史。
      「流程进度图」已于 2026-07-21 移除：后端 /processes/{id}/diagram 依赖 Flowable 部署时生成的
      PNG 资源，而本库 573 条流程定义 HAS_GRAPHICAL_NOTATION_ 全为 0（BPMN 均无 BPMNDI 图形信息，
      设计器生成的与静态部署的都一样），getProcessDiagram 恒返回 null → 接口恒 500，该图从未成功
      渲染过。节点进度信息由下方「流程节点」表格承载，不再保留一个必然失败的入口。
    -->
    <el-drawer v-model="detail.show" :title="`流程详情 · ${detail.row?.businessKey || ''}`" size="56%" :destroy-on-close="true" :aria-busy="detail.loading ? 'true' : 'false'">
      <div v-loading="detail.loading">
        <p v-if="detail.error" class="error-state" role="alert">{{ detail.error }} <el-button link type="primary" @click="openDetail(detail.row)">重试</el-button></p>
        <el-descriptions v-if="detail.info" :column="2" border size="default">
          <el-descriptions-item label="流程实例ID" :span="2">{{ detail.info.processInstanceId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="业务类型">{{ bizTypeLabel(detail.info.bizType) }}</el-descriptions-item>
          <el-descriptions-item label="流程状态">
            <el-tag :class="statusCls(detail.info.processStatus)" effect="plain" size="small">{{ statusLabel(detail.info.processStatus) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="标题" :span="2">{{ detail.info.title || '-' }}</el-descriptions-item>
          <el-descriptions-item label="发起人">
            {{ detail.info.startUserName || '' }}<span v-if="detail.info.startUserId">（{{ detail.info.startUserId }}）</span>
          </el-descriptions-item>
          <el-descriptions-item label="发起机构">{{ detail.info.startOrgName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="当前节点">{{ detail.info.currentNodeName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="当前处理人">
            {{ detail.info.currentAssigneeName || '' }}<span v-if="detail.info.currentAssignee">（{{ detail.info.currentAssignee }}）</span>
            <span v-if="!detail.info.currentAssignee">-</span>
          </el-descriptions-item>
          <el-descriptions-item label="发起时间">{{ fmtDateTime(detail.info.startTime) }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ fmtDateTime(detail.info.endTime) }}</el-descriptions-item>
        </el-descriptions>

        <div class="d-block">
          <div class="d-title">流程节点</div>
          <el-table v-if="detail.nodes.length" :data="detail.nodes" size="small" border empty-text="暂无节点数据">
            <el-table-column label="节点" min-width="140">
              <template #default="{row}">{{ row.nodeName || row.nodeKey || '-' }}</template>
            </el-table-column>
            <el-table-column label="类型" width="110">
              <template #default="{row}">{{ NODE_TYPE_LABEL[row.nodeType] || row.nodeType || '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{row}">
                <el-tag :class="nodeStatusCls(row.status)" effect="plain" size="small">{{ nodeStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="处理人" width="130">
              <template #default="{row}">{{ row.assigneeName || row.assignee || '-' }}</template>
            </el-table-column>
            <el-table-column label="开始时间" width="160">
              <template #default="{row}">{{ fmtDateTime(row.startTime) }}</template>
            </el-table-column>
            <el-table-column label="结束时间" width="160">
              <template #default="{row}">{{ fmtDateTime(row.endTime) }}</template>
            </el-table-column>
          </el-table>
          <div v-else class="d-empty">无</div>
        </div>

        <div class="d-block">
          <div class="d-title">审批历史</div>
          <el-empty v-if="!detail.history.length" description="暂无审批记录" :image-size="60" />
          <el-timeline v-else>
            <el-timeline-item
              v-for="(log, idx) in detail.history"
              :key="idx"
              :timestamp="fmtDateTime(log.operateTime)"
              placement="top"
              :type="actionTimelineType(log.action)"
              :hollow="idx !== 0">
              <div class="approval-line">
                <el-tag :class="actionCls(log.action)" effect="plain" size="small">{{ actionLabel(log.action) }}</el-tag>
                <span class="node">{{ log.nodeName || log.nodeKey || '-' }}</span>
              </div>
              <div class="approval-meta">
                <span class="meta-key">操作人：</span>
                <span>{{ log.operatorName || log.operatorEmpNo || log.operator || '-' }}</span>
                <span v-if="log.operatorEmpNo" class="sub-id-inline">（{{ log.operatorEmpNo }}）</span>
                <span class="meta-sep">·</span>
                <span class="meta-key">机构：</span>
                <span>{{ log.operatorOrgName || '-' }}</span>
              </div>
              <div v-if="log.opinion" class="approval-opinion">意见：{{ log.opinion }}</div>
            </el-timeline-item>
          </el-timeline>
        </div>

        <!--
          转交历史：该流程实例被转交/指派的全过程。含未决（待认领）与全部终态，
          回答「谁转给谁、谁认领了、谁拒绝了、为什么拒绝、什么时候」。
          fromEmpId 为空 = 指派语义（任务尚无人签收，从候选池直接指定），非缺数据。
        -->
        <div class="d-block">
          <div class="d-title">转交历史</div>
          <el-empty v-if="!detail.transfers.length" description="暂无转交记录" :image-size="60" />
          <el-table v-else :data="detail.transfers" size="small" border>
            <el-table-column label="节点" min-width="130">
              <template #default="{row}">{{ row.nodeName || row.nodeKey || '-' }}</template>
            </el-table-column>
            <el-table-column label="类型" width="76">
              <template #default="{row}">
                <el-tag :class="row.fromEmpId ? 'tag-info' : 'tag-warning'" effect="plain" size="small">
                  {{ row.fromEmpId ? '转交' : '指派' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="转出" width="130">
              <template #default="{row}">
                <span v-if="row.fromEmpId">{{ row.fromName || '-' }}<span class="sub-id-inline">（{{ row.fromEmpId }}）</span></span>
                <span v-else class="sub-id">候选池</span>
              </template>
            </el-table-column>
            <el-table-column label="接收人" width="130">
              <template #default="{row}">{{ row.toName || '-' }}<span class="sub-id-inline">（{{ row.toEmpId }}）</span></template>
            </el-table-column>
            <el-table-column label="发起人" width="130">
              <template #default="{row}">{{ row.initiatorName || '-' }}<span class="sub-id-inline">（{{ row.initiatorEmpId }}）</span></template>
            </el-table-column>
            <el-table-column label="结果" width="92">
              <template #default="{row}">
                <el-tag :class="transferStatusCls(row.status)" effect="plain" size="small">{{ transferStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="原因" min-width="180">
              <template #default="{row}">
                <div class="tr-reason">转交：{{ row.transferReason || '-' }}</div>
                <div v-if="row.rejectReason" class="tr-reason tr-reject">拒绝：{{ row.rejectReason }}</div>
              </template>
            </el-table-column>
            <el-table-column label="发起时间" width="160">
              <template #default="{row}">{{ fmtDateTime(row.initiatedTime) }}</template>
            </el-table-column>
            <el-table-column label="处理时间" width="160">
              <template #default="{row}">{{ row.decidedTime ? fmtDateTime(row.decidedTime) : '-' }}</template>
            </el-table-column>
          </el-table>
        </div>
      </div>
      <template #footer>
        <el-button @click="detail.show = false">关闭</el-button>
      </template>
    </el-drawer>

    <!-- 转交弹窗：秘书岗/行长把该行当前活跃任务转交给本机构其他人（待认领后生效） -->
    <TransferDialog v-model="transferDlg.show" :task="transferDlg.task" @success="reload" />
  </main>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { monitorProcesses, getProcessInfo, getProcessHistory, getProcessNodes, processTransferHistory } from '@/api/workflow';
import { fmtDateTime } from '@/utils/datetime';
import TransferDialog from '@/components/TransferDialog.vue';

const route = useRoute();

// 业务类型字典：与 FlowList.vue 保持一致（当前平台仅这两种流程业务类型）
const BIZ_TYPE_OPTIONS = [
  { value: 'ALLOC_ADJUST', label: '业绩调整' },
  { value: 'TARGET_ADJUST', label: '目标方案' }
];
const BIZ_TYPE_MAP = Object.fromEntries(BIZ_TYPE_OPTIONS.map(o => [o.value, o.label]));
const bizTypeLabel = (type) => BIZ_TYPE_MAP[type] || (type ?? '-');

// 流程状态：口径与后端 ProcessStatus 枚举一致
const STATUS_MAP = {
  RUNNING: { label: '运行中', cls: 'tag-warning' },
  COMPLETED: { label: '已完成', cls: 'tag-success' },
  CANCELLED: { label: '已取消', cls: 'tag-info' }
};
const statusCls = (s) => STATUS_MAP[s]?.cls || 'tag-info';
const statusLabel = (s) => STATUS_MAP[s]?.label || s || '-';

// 节点类型 / 节点状态字典（ProcessDiagramNodeDTO）
const NODE_TYPE_LABEL = { startEvent: '开始', userTask: '审批', exclusiveGateway: '网关', endEvent: '结束' };
const NODE_STATUS_MAP = {
  COMPLETED: { label: '已完成', cls: 'tag-success' },
  ACTIVE: { label: '进行中', cls: 'tag-warning' },
  PENDING: { label: '待处理', cls: 'tag-info' }
};
const nodeStatusCls = (s) => NODE_STATUS_MAP[s]?.cls || 'tag-info';
const nodeStatusLabel = (s) => NODE_STATUS_MAP[s]?.label || s || '-';

// 转交状态字典：前 4 项与 workspace/Index.vue 的 TRANSFER_STATUS_MAP 保持一致（同一批状态码，
// 两处措辞必须同步改）；INVALIDATED 是监控视角才会看到的终态——原任务已被删除（撤单/驳回
// 级联）导致转交作废，收发件箱按状态过滤后不展示它，这里是全量历史故需覆盖。
const TRANSFER_STATUS_MAP = {
  PENDING_ACCEPT: { label: '待认领', cls: 'tag-warning' },
  ACCEPTED: { label: '已认领', cls: 'tag-success' },
  REJECTED: { label: '已拒绝', cls: 'tag-danger' },
  CANCELLED: { label: '已撤回', cls: 'tag-info' },
  INVALIDATED: { label: '已失效', cls: 'tag-info' }
};
const transferStatusCls = (s) => TRANSFER_STATUS_MAP[s]?.cls || 'tag-info';
const transferStatusLabel = (s) => TRANSFER_STATUS_MAP[s]?.label || s || '-';

// 审批日志动作字典（ApprovalLogDTO.action），与 perf/Adjust.vue 审批历史保持一致的视觉语言
const ACTION_LABEL = { SUBMIT: '提交', APPROVE: '通过', REJECT: '驳回', CLAIM: '签收', TRANSFER: '转办' };
const actionLabel = (a) => ACTION_LABEL[a] || a || '-';
const actionCls = (a) => ({
  APPROVE: 'tag-success', REJECT: 'tag-danger', SUBMIT: 'tag-info', CLAIM: 'tag-warning', TRANSFER: 'tag-warning'
}[a] || 'tag-info');
const actionTimelineType = (a) => ({
  APPROVE: 'success', REJECT: 'danger', SUBMIT: 'primary', CLAIM: 'warning', TRANSFER: 'warning'
}[a] || 'info');

// 过滤条件：status 与 el-tabs 共用（进行中/已完成）
const query = reactive({ status: 'RUNNING', bizType: '', keyword: '', startedBy: '' });

// 列表 + 分页
const rows = ref([]);
const total = ref(0);
const loading = ref(false);
const loadError = ref('');
const pageNo = ref(1);
const pageSize = ref(20);
let listRequestVersion = 0;

async function reload() {
  const requestVersion = ++listRequestVersion;
  loading.value = true;
  loadError.value = '';
  try {
    const r = await monitorProcesses({
      status: query.status,
      bizType: query.bizType || undefined,
      keyword: query.keyword?.trim() || undefined,
      startedBy: query.startedBy?.trim() || undefined,
      pageNo: pageNo.value,
      pageSize: pageSize.value
    });
    if (requestVersion !== listRequestVersion) return;
    const arr = r?.records || (Array.isArray(r) ? r : []);
    rows.value = arr;
    total.value = r?.total ?? arr.length;
  } catch (error) {
    if (requestVersion !== listRequestVersion) return;
    rows.value = [];
    total.value = 0;
    loadError.value = `审批流实例加载失败：${error?.message || '请稍后重试'}`;
  } finally {
    if (requestVersion === listRequestVersion) loading.value = false;
  }
}

function onSearch() { pageNo.value = 1; reload(); }
function onReset() {
  query.bizType = '';
  query.keyword = '';
  query.startedBy = '';
  pageNo.value = 1;
  reload();
}

onMounted(async () => {
  await reload();
  // 支持从「工作台 · 我转出的」查看入口跳转过来时直接展开对应流程详情（复用本页详情抽屉，
  // 不重复实现一份只读详情），行数据在当前页查不到时退化为按 processInstanceId 单独拉取。
  const pid = route.query.processInstanceId;
  if (pid) {
    const row = rows.value.find(r => r.processInstanceId === pid) || { processInstanceId: pid, businessKey: route.query.businessKey || '' };
    openDetail(row);
  }
});

// 转交/指派：仅 RUNNING 且有活跃任务（currentTaskId）的行可操作。
// 2026-07-20 去掉了原先的 currentAssignee 判定：未签收的候选组任务（如机构负责人会签）现已支持
// 直接「指派」（后端 from_emp_id 可空），与审批链路口径一致；再卡 assignee 会让秘书岗无法分派。
function canTransfer(row) {
  return row.processStatus === 'RUNNING' && !!row.currentTaskId;
}

// 未签收（无当前处理人）时是把任务从候选池指派给某人，用「指派」更贴合语义；已签收则是「转交」。
function transferLabel(row) {
  return row.currentAssignee ? '转交' : '指派';
}

const transferDlg = reactive({ show: false, task: null });
function openTransfer(row) {
  if (!canTransfer(row)) return;
  transferDlg.task = {
    taskId: row.currentTaskId,
    nodeName: row.title || row.businessKey,
    businessKey: row.businessKey,
    // 传给弹窗用于区分转交/指派文案；未签收时为空
    currentAssignee: row.currentAssignee
  };
  transferDlg.show = true;
}

// 详情抽屉：并行拉取实例详情 / 审批历史 / 节点 / 转交历史
const detail = reactive({
  show: false, loading: false, row: null,
  processInstanceId: '', info: null, nodes: [], history: [], transfers: [], error: ''
});
let detailRequestVersion = 0;

async function openDetail(row) {
  if (!row?.processInstanceId) return;
  const requestVersion = ++detailRequestVersion;
  detail.row = row;
  detail.processInstanceId = row.processInstanceId;
  detail.info = null;
  detail.nodes = [];
  detail.history = [];
  detail.transfers = [];
  detail.error = '';
  detail.show = true;
  detail.loading = true;
  try {
    const [info, history, nodesResp, transfers] = await Promise.all([
      getProcessInfo(row.processInstanceId),
      getProcessHistory(row.processInstanceId),
      getProcessNodes(row.processInstanceId),
      processTransferHistory(row.processInstanceId)
    ]);
    if (requestVersion !== detailRequestVersion) return;
    detail.info = info || null;
    detail.history = Array.isArray(history) ? history : [];
    detail.nodes = Array.isArray(nodesResp?.nodes) ? nodesResp.nodes : [];
    detail.transfers = Array.isArray(transfers) ? transfers : [];
  } catch (error) {
    if (requestVersion !== detailRequestVersion) return;
    detail.error = `流程详情加载失败：${error?.message || '请稍后重试'}`;
  } finally {
    if (requestVersion === detailRequestVersion) detail.loading = false;
  }
}
</script>

<style lang="scss" scoped>
.monitor-tabs { margin-bottom: var(--space-3); }
.monitor-tabs :deep(.el-tabs__header) { margin-bottom: 0; }
.monitor-tabs :deep(.el-tabs__nav-wrap)::after { background: var(--color-border); }
.row-actions { display: flex; justify-content: flex-end; gap: var(--space-1); }
.mono { background: var(--color-surface-soft); border: 1px solid var(--color-border); border-radius: var(--radius-control); color: var(--color-text); font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; font-size: 12px; padding: 2px 6px; }
.sub-name { color: var(--color-text); font-size: 13px; margin-top: var(--space-1); }
.sub-id { color: var(--color-text-muted); font-size: 12px; }

.d-block { margin-top: var(--space-6); }
.d-title { color: var(--color-text-strong); font-size: 14px; font-weight: 600; margin-bottom: var(--space-2); }
.d-empty { color: var(--color-text-muted); font-size: 13px; }
.tr-reason { line-height: 1.5; word-break: break-all; }
.tr-reject { color: var(--color-danger-fg); }

.approval-line { align-items: center; display: flex; gap: var(--space-2); }
.approval-line .node { color: var(--color-text-strong); font-size: 13px; font-weight: 500; }
.approval-meta { color: var(--color-text); font-size: 12px; margin-top: var(--space-1); }
.approval-meta .meta-key, .approval-meta .meta-sep, .sub-id-inline { color: var(--color-text-muted); }
.approval-meta .meta-sep { margin: 0 var(--space-2); }
.approval-opinion { color: var(--color-text-strong); font-size: 13px; margin-top: var(--space-1); }
</style>
