<template>
  <div class="flow-edit-page">
    <div class="page-h">
      <PageTitle>
        <span class="sub">流程图式可视化编辑：拖入节点、连线表达流转、网关出边配置条件分支；保存草稿后发布生成影子流程，不影响现有线上审批</span>
      </PageTitle>
      <div class="actions">
        <el-button @click="goBack">← 返回列表</el-button>
        <el-button :disabled="readonly" :loading="saving" @click="doSave">保存草稿</el-button>
        <el-button type="primary" :disabled="readonly" :loading="publishing" @click="doPublish">发布</el-button>
      </div>
    </div>

    <!-- 只读导入流程提示 -->
    <el-alert
      v-if="readonly"
      class="ro-alert"
      type="warning"
      show-icon
      :closable="false"
      title="只读导入流程，仅供查看，请返回列表用『克隆』另存后再编辑"
    />

    <!-- 基本信息 -->
    <div class="basic-bar">
      <span class="bi-label">流程名称</span>
      <el-input v-model="graph.name" :disabled="readonly" placeholder="请输入流程名称" maxlength="100" style="max-width: 320px" size="default" />
      <span class="bi-label" style="margin-left: 20px">业务类型</span>
      <span class="bi-val">{{ bizTypeLabel(graph.bizType) }}</span>
    </div>

    <!-- 三栏：工具盘 | 画布 | 属性栏 -->
    <div class="designer">
      <!-- 左：节点工具盘（只读隐藏）-->
      <FlowPalette
        v-if="!readonly"
        :has-start="hasStart"
        @add-endpoint="addEndpoint"
      />

      <!-- 中：画布 -->
      <div class="canvas-wrap">
        <FlowCanvas
          ref="canvasRef"
          :nodes="graph.nodes"
          :edges="graph.edges"
          :selection="selection"
          :variables="variables"
          :readonly="readonly"
          @select="onSelect"
          @create-edge="onCreateEdge"
          @drop-node="onDropNode"
          @delete-node="onDeleteNode"
          @delete-edge="onDeleteEdge"
        />
      </div>

      <!-- 右：属性栏 -->
      <div class="prop-panel">
        <FlowNodePanel
          v-if="selection.type === 'node' && selectedNode"
          :node="selectedNode"
          :readonly="readonly"
          :approver-variables="approverVariables"
          @delete="onDeleteNode(selection.key)"
        />
        <FlowEdgePanel
          v-else-if="selection.type === 'edge' && selectedEdge"
          :edge="selectedEdge"
          :nodes="graph.nodes"
          :variables="variables"
          :readonly="readonly"
          @delete="onDeleteEdge(selection.key)"
        />
        <div v-else class="prop-empty">
          <div class="pe-icon">◇</div>
          <div>选中节点编辑审批环节参数<br>选中连线编辑条件分支</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getFlow, saveFlow, publishFlow, listFlowVariables, listApproverVariables } from '@/api/flowDesign';
import FlowCanvas from '@/views/system/flow/FlowCanvas.vue';
import FlowPalette from '@/views/system/flow/FlowPalette.vue';
import FlowNodePanel from '@/views/system/flow/FlowNodePanel.vue';
import FlowEdgePanel from '@/views/system/flow/FlowEdgePanel.vue';

const route = useRoute();
const router = useRouter();
/** 当前编辑的流程 ID，来自路由参数 :id */
const id = route.params.id;

// 业务类型显示映射（与 FlowList 对齐）
const BIZ_TYPE_MAP = { ALLOC_ADJUST: '业绩调整', TARGET_ADJUST: '目标方案' };
function bizTypeLabel(type) { return BIZ_TYPE_MAP[type] || (type ?? '-'); }

const NODE_DEFAULT_NAME = { START: '开始', APPROVAL: '审批', GATEWAY: '网关', END: '结束' };

// === 主数据 ===
const graph = reactive({ name: '', bizType: '', nodes: [], edges: [] });
// 条件分支可用变量白名单 [{field,label,type}]
const variables = ref([]);
// VAR 审批人可选的名单类流程变量 [{field,label,type}]
const approverVariables = ref([]);
// 只读导入流程标记
const readonly = ref(false);
const saving = ref(false);
const publishing = ref(false);

// 当前选中：{ type:'node'|'edge'|null, key } —— key 为 nodeKey 或 edge 下标
const selection = reactive({ type: null, key: null });
const canvasRef = ref(null);

onMounted(async () => {
  let model = null;
  try {
    model = await getFlow(id);
  } catch (e) {
    ElMessage.error('加载流程失败：' + (e?.message || e));
  }
  model = model || {};
  graph.name = model.name || '';
  graph.bizType = model.bizType || '';
  graph.nodes = (Array.isArray(model.nodes) ? model.nodes : []).map(n => ({
    nodeKey: n.nodeKey || '',
    nodeType: n.nodeType || 'APPROVAL',
    name: n.name || '',
    approveMode: n.approveMode || (n.nodeType === 'APPROVAL' ? 'ANY' : null),
    // 审批机构归属：SELF=本机构 / PARENT=上级机构 / null=不判断
    sortNo: n.sortNo,
    // 画布坐标：后端可能为 null（旧数据），画布入场会兜底布局
    posX: n.posX != null ? n.posX : null,
    posY: n.posY != null ? n.posY : null,
    approvers: Array.isArray(n.approvers) ? n.approvers.map(a => ({ ...a })) : []
  }));
  graph.edges = (Array.isArray(model.edges) ? model.edges : []).map(e => ({
    fromNodeKey: e.fromNodeKey || '',
    toNodeKey: e.toNodeKey || '',
    isDefault: !!e.isDefault,
    // 分支「输出名称」（走向标签）
    outputName: e.outputName || '',
    condition: e.condition && typeof e.condition === 'object' ? e.condition : null
  }));
  readonly.value = model.readonly === true || model.isReadonlyImport === true || model.isReadonlyImport === 1;

  try {
    variables.value = await listFlowVariables(graph.bizType);
  } catch {
    variables.value = [];
  }
  try {
    approverVariables.value = await listApproverVariables(graph.bizType);
  } catch {
    approverVariables.value = [];
  }
});

// === 选中态派生 ===
const hasStart = computed(() => graph.nodes.some(n => n.nodeType === 'START'));
const selectedNode = computed(() =>
  selection.type === 'node' ? graph.nodes.find(n => n.nodeKey === selection.key) : null
);
const selectedEdge = computed(() =>
  selection.type === 'edge' ? graph.edges[selection.key] : null
);

function onSelect(sel) {
  selection.type = sel.type;
  selection.key = sel.key;
}

// === nodeKey 生成（图内唯一）===
function genNodeKey(type) {
  if (type === 'START') return graph.nodes.some(n => n.nodeKey === 'start') ? uniq('start') : 'start';
  const prefix = { END: 'end', APPROVAL: 'approval', GATEWAY: 'gateway' }[type] || 'node';
  return uniq(prefix);
}
function uniq(prefix) {
  let i = 1, key;
  do { key = `${prefix}_${i++}`; } while (graph.nodes.some(n => n.nodeKey === key));
  return key;
}

function makeNode(type, posX, posY) {
  return {
    nodeKey: genNodeKey(type),
    nodeType: type,
    name: NODE_DEFAULT_NAME[type] || '',
    approveMode: type === 'APPROVAL' ? 'ANY' : null,
    sortNo: graph.nodes.length + 1,
    posX, posY,
    approvers: []
  };
}

// === 画布事件处理 ===

/** 从工具盘拖入：在落点新增节点并选中 */
function onDropNode({ nodeType, posX, posY }) {
  const node = makeNode(nodeType, posX, posY);
  graph.nodes.push(node);
  onSelect({ type: 'node', key: node.nodeKey });
}

/** 端点按钮新增 START/END */
function addEndpoint(type) {
  if (type === 'START' && hasStart.value) {
    ElMessage.warning('已存在开始节点');
    return;
  }
  // START 放顶部居中，END 放底部
  const posX = 360;
  const posY = type === 'START' ? 30 : Math.max(120, graph.nodes.length * 30 + 120);
  const node = makeNode(type, posX, posY);
  graph.nodes.push(node);
  onSelect({ type: 'node', key: node.nodeKey });
}

/** 建边并选中新边 */
function onCreateEdge({ fromNodeKey, toNodeKey }) {
  graph.edges.push({ fromNodeKey, toNodeKey, isDefault: false, outputName: '', condition: null });
  onSelect({ type: 'edge', key: graph.edges.length - 1 });
}

/** 删节点 + 级联删相关边 + 清选中 */
function onDeleteNode(nodeKey) {
  const idx = graph.nodes.findIndex(n => n.nodeKey === nodeKey);
  if (idx < 0) return;
  graph.nodes.splice(idx, 1);
  // 级联删除与该节点相连的边
  for (let i = graph.edges.length - 1; i >= 0; i--) {
    if (graph.edges[i].fromNodeKey === nodeKey || graph.edges[i].toNodeKey === nodeKey) {
      graph.edges.splice(i, 1);
    }
  }
  clearSelection();
}

/** 删边 + 清选中 */
function onDeleteEdge(edgeIdx) {
  if (edgeIdx == null || edgeIdx < 0 || edgeIdx >= graph.edges.length) return;
  graph.edges.splice(edgeIdx, 1);
  clearSelection();
}

function clearSelection() {
  selection.type = null;
  selection.key = null;
}

// === 规整 graph 给后端（透传 posX/posY）===
function buildPayload() {
  return {
    name: graph.name,
    bizType: graph.bizType,
    nodes: graph.nodes.map((n, i) => ({
      nodeKey: n.nodeKey,
      nodeType: n.nodeType,
      name: n.name,
      approveMode: n.nodeType === 'APPROVAL' ? (n.approveMode || 'ANY') : null,
      sortNo: n.sortNo != null ? n.sortNo : i,
      posX: n.posX != null ? Math.round(n.posX) : null,
      posY: n.posY != null ? Math.round(n.posY) : null,
      approvers: (n.approvers || []).map(a => ({
        approverType: a.approverType,
        approverValue: a.approverValue,
        orgScope: a.orgScope || null,
        roleCode: a.roleCode || null
      }))
    })),
    edges: graph.edges.map(e => ({
      fromNodeKey: e.fromNodeKey,
      toNodeKey: e.toNodeKey,
      isDefault: !!e.isDefault,
      outputName: e.outputName || null,
      condition: e.condition || null
    }))
  };
}

/** 轻校验：返回问题数组（空数组=通过）。与原表格式编辑器一致 */
function validateGraph() {
  const errs = [];
  const startCount = graph.nodes.filter(n => n.nodeType === 'START').length;
  const endCount = graph.nodes.filter(n => n.nodeType === 'END').length;
  if (startCount !== 1) errs.push(`必须恰好 1 个开始节点（当前 ${startCount} 个）`);
  if (endCount < 1) errs.push('至少需要 1 个结束节点');

  const keys = graph.nodes.map(n => n.nodeKey);
  if (keys.some(k => !k || !k.trim())) errs.push('存在空的节点标识');
  const dup = keys.filter((k, i) => k && keys.indexOf(k) !== i);
  if (dup.length) errs.push(`节点标识重复：${[...new Set(dup)].join('、')}`);

  for (const n of graph.nodes) {
    if (n.nodeType === 'APPROVAL' && (n.approvers || []).length === 0) {
      errs.push(`审批节点「${n.name || n.nodeKey || '?'}」缺少审批人`);
    }
  }
  return errs;
}

// === 操作：返回 / 保存 / 发布 ===
function goBack() {
  router.push('/system/workflow-flows');
}

async function doSave() {
  if (readonly.value) return;
  const errs = validateGraph();
  if (errs.length) {
    ElMessage.warning('草稿存在待修正项：' + errs.join('；'));
  }
  saving.value = true;
  try {
    await saveFlow(id, buildPayload());
    ElMessage.success('草稿已保存');
  } catch (e) {
    ElMessage.error('保存失败：' + (e?.message || e));
  } finally {
    saving.value = false;
  }
}

async function doPublish() {
  if (readonly.value) return;
  const errs = validateGraph();
  if (errs.length) {
    ElMessageBox.alert(
      errs.map(e => '· ' + e).join('<br/>'),
      '无法发布：请先修正以下问题',
      { confirmButtonText: '知道了', type: 'warning', dangerouslyUseHTMLString: true }
    );
    return;
  }
  publishing.value = true;
  try {
    await saveFlow(id, buildPayload());
    await publishFlow(id);
    ElMessage.success('发布成功');
  } catch (e) {
    ElMessageBox.alert(
      e?.message || '发布失败，请检查流程配置',
      '发布失败',
      { confirmButtonText: '知道了', type: 'error' }
    );
  } finally {
    publishing.value = false;
  }
}
</script>

<style lang="scss" scoped>
/* 整页填满内容区高度，流程图编辑区占满剩余高度 */
.flow-edit-page {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.page-h, .ro-alert, .basic-bar { flex-shrink: 0; }
.page-h h1 .sub { font-size: 13px; color: $text-3; margin-left: 12px; font-weight: 400; }
.ro-alert { margin-bottom: 14px; }

.basic-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  margin-bottom: 12px;
  background: #fff;
  border: 1px solid $border-2;
  border-radius: 6px;
}
.bi-label { font-size: 13px; color: $text-2; }
.bi-val { font-size: 13px; color: $text-1; font-weight: 600; }

.designer {
  display: flex;
  gap: 12px;
  align-items: stretch;
  flex: 1;          /* 占满剩余高度 */
  min-height: 420px;
}
.canvas-wrap { flex: 1; min-width: 0; }

.prop-panel {
  width: 320px;
  flex-shrink: 0;
  padding: 16px;
  background: #fff;
  border: 1px solid $border-2;
  border-radius: 6px;
  overflow-y: auto;
}
.prop-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 360px;
  gap: 12px;
  color: $text-3;
  font-size: 13px;
  line-height: 1.7;
  text-align: center;
}
.pe-icon { font-size: 40px; color: $border-2; }
</style>
