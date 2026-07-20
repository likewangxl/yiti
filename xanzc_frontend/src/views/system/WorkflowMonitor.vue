<template>
  <div>
    <div class="page-h">
      <PageTitle><span class="sub">秘书岗按本机构、行长按全行查看进行中/已完成的审批流实例</span></PageTitle>
      <div class="actions">
        <el-button @click="reload">刷新</el-button>
      </div>
    </div>

    <el-tabs v-model="query.status" class="monitor-tabs" @tab-change="onSearch">
      <el-tab-pane label="进行中" name="RUNNING" />
      <el-tab-pane label="已完成" name="COMPLETED" />
    </el-tabs>

    <!-- 过滤区：业务类型 / 关键字 / 发起人 -->
    <div class="card-section">
      <el-form :inline="true" size="default">
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
    </div>

    <div class="card-section table">
      <el-table :data="rows" size="default" v-loading="loading" empty-text="暂无流程记录">
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
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openDetail(row)">查看</el-button>
            <el-button
              link type="primary" size="small"
              :disabled="!canTransfer(row)"
              :title="canTransfer(row) ? '' : '流程已结束或暂无活跃任务，不可转交/指派'"
              @click="openTransfer(row)">{{ transferLabel(row) }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
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
      </div>
    </div>

    <!-- 详情抽屉：基础信息 + 流程图 + 节点 + 审批历史（复用流程查询接口） -->
    <el-drawer v-model="detail.show" :title="`流程详情 · ${detail.row?.businessKey || ''}`" size="56%" :destroy-on-close="true">
      <div v-loading="detail.loading">
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
          <div class="d-title">流程进度图</div>
          <div class="diagram-wrap">
            <img v-if="detail.processInstanceId && !detail.diagramFailed" :src="diagramSrc" class="diagram-img"
              @error="detail.diagramFailed = true" alt="流程进度图" />
            <div v-if="detail.diagramFailed" class="d-empty">流程图加载失败</div>
          </div>
        </div>

        <div class="d-block">
          <div class="d-title">节点状态</div>
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
      </div>
      <template #footer>
        <el-button @click="detail.show = false">关闭</el-button>
      </template>
    </el-drawer>

    <!-- 转交弹窗：秘书岗/行长把该行当前活跃任务转交给本机构其他人（待认领后生效） -->
    <TransferDialog v-model="transferDlg.show" :task="transferDlg.task" @success="reload" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { monitorProcesses, getProcessInfo, getProcessHistory, getProcessNodes, processDiagramUrl } from '@/api/workflow';
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
const pageNo = ref(1);
const pageSize = ref(20);

async function reload() {
  loading.value = true;
  try {
    const r = await monitorProcesses({
      status: query.status,
      bizType: query.bizType || undefined,
      keyword: query.keyword?.trim() || undefined,
      startedBy: query.startedBy?.trim() || undefined,
      pageNo: pageNo.value,
      pageSize: pageSize.value
    });
    const arr = r?.records || (Array.isArray(r) ? r : []);
    rows.value = arr;
    total.value = r?.total ?? arr.length;
  } catch { /* call 内部已提示 */ } finally { loading.value = false; }
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
  transferDlg.task = {
    taskId: row.currentTaskId,
    nodeName: row.title || row.businessKey,
    businessKey: row.businessKey,
    // 传给弹窗用于区分转交/指派文案；未签收时为空
    currentAssignee: row.currentAssignee
  };
  transferDlg.show = true;
}

// 详情抽屉：并行拉取实例详情 / 历史 / 节点，流程图走 <img> 直连（同源 session cookie）
const detail = reactive({
  show: false, loading: false, row: null,
  processInstanceId: '', info: null, nodes: [], history: [], diagramFailed: false
});
const diagramSrc = computed(() => detail.processInstanceId ? processDiagramUrl(detail.processInstanceId) : '');

async function openDetail(row) {
  detail.row = row;
  detail.processInstanceId = row.processInstanceId;
  detail.diagramFailed = false;
  detail.info = null;
  detail.nodes = [];
  detail.history = [];
  detail.show = true;
  detail.loading = true;
  try {
    const [info, history, nodesResp] = await Promise.all([
      getProcessInfo(row.processInstanceId),
      getProcessHistory(row.processInstanceId),
      getProcessNodes(row.processInstanceId)
    ]);
    detail.info = info || null;
    detail.history = Array.isArray(history) ? history : [];
    detail.nodes = Array.isArray(nodesResp?.nodes) ? nodesResp.nodes : [];
  } catch { /* call 内部已提示 */ } finally {
    detail.loading = false;
  }
}
</script>

<style lang="scss" scoped>
.page-h h1 .sub { font-size: 13px; color: $text-3; margin-left: 12px; font-weight: 400; }
.monitor-tabs { margin-bottom: 12px; }
.monitor-tabs :deep(.el-tabs__header) { margin-bottom: 0; }
.table { padding: 0; padding-bottom: 12px; }
.pager { display: flex; justify-content: flex-end; padding: 12px 14px; }
.pager :deep(.el-pagination) { flex-wrap: wrap; row-gap: 8px; justify-content: flex-end; }
.mono { font-family: ui-monospace, monospace; font-size: 12px; background: $bg-soft; padding: 2px 6px; border-radius: 3px; }
.sub-name { color: $text-2; font-size: 13px; margin-top: 2px; }
.sub-id { color: $text-3; font-size: 12px; }

.d-block { margin-top: 20px; }
.d-title { font-size: 14px; font-weight: 600; color: $text-1; margin-bottom: 8px; }
.d-empty { color: $text-3; font-size: 13px; }
.diagram-wrap { border: 1px solid $border-1; border-radius: 4px; padding: 10px; background: $bg-soft; text-align: center; }
.diagram-img { max-width: 100%; }

.approval-line { display: flex; align-items: center; gap: 8px; }
.approval-line .node { font-size: 13px; color: $text-1; font-weight: 500; }
.approval-meta { margin-top: 4px; font-size: 12px; color: $text-2; }
.approval-meta .meta-key { color: $text-3; }
.approval-meta .meta-sep { margin: 0 6px; color: $text-3; }
.sub-id-inline { color: $text-3; }
.approval-opinion { margin-top: 4px; font-size: 13px; color: $text-1; }
</style>
