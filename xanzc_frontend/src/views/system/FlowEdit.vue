<template>
  <main
    class="bp-crud flow-edit-page"
    aria-labelledby="flow-edit-page-title"
    :aria-busy="loading || saving || publishing ? 'true' : 'false'"
  >
    <header class="page-h">
      <PageTitle id="flow-edit-page-title" title="审批流程设计">
        <span class="sub">以节点、连线与网关条件表达审批路径；保存为草稿后可由后端校验并发布影子流程。</span>
      </PageTitle>
      <div class="actions action-group" role="group" aria-label="流程设计操作">
        <el-button :disabled="saving || publishing" @click="goBack">返回列表</el-button>
        <el-button :disabled="readonly || loading || !loadConfirmed || publishing" :loading="saving" @click="doSave">保存草稿</el-button>
        <el-button type="primary" :disabled="readonly || loading || !loadConfirmed || saving" :loading="publishing" @click="doPublish">发布</el-button>
      </div>
    </header>

    <el-alert
      v-if="readonly"
      class="ro-alert"
      type="warning"
      show-icon
      :closable="false"
      title="当前为只读导入流程"
      description="该模型仅供查看。请返回列表后使用“克隆”创建可编辑草稿。"
    />
    <p v-if="loadError" class="error-state" role="alert">
      {{ loadError }} <el-button link type="primary" @click="loadGraph">重试</el-button>
    </p>

    <section class="card-section designer-meta" aria-label="流程基本信息">
      <div class="flow-meta-fields">
        <label class="meta-field" for="flow-name-input">
          <span class="meta-label">流程名称</span>
          <el-input id="flow-name-input" v-model="graph.name" :disabled="readonly || loading" placeholder="请输入流程名称" maxlength="100" />
        </label>
        <div class="meta-field">
          <span class="meta-label">业务类型</span>
          <span class="meta-value">{{ bizTypeLabel(graph.bizType) }}</span>
        </div>
        <p class="designer-state" role="status" aria-live="polite">{{ designerState }}</p>
      </div>
    </section>

    <section
      class="card-section data-panel designer-panel"
      aria-label="审批流程画布设计器"
      aria-labelledby="flow-designer-heading"
      :aria-busy="loading ? 'true' : 'false'"
    >
      <div class="toolbar designer-toolbar">
        <div>
          <h2 id="flow-designer-heading" class="section-title">流程画布</h2>
          <p class="hint">拖入审批或网关节点；从节点底部锚点拖到目标节点创建连线。选中节点或连线后在右侧配置属性。</p>
        </div>
        <p class="table-state">{{ graph.nodes.length }} 个节点 · {{ graph.edges.length }} 条连线</p>
      </div>

      <div class="designer" :class="{ 'is-readonly': readonly || loading }">
        <aside v-if="!readonly && !loading" class="palette-panel" aria-label="流程节点工具箱">
          <FlowPalette :has-start="hasStart" @add-endpoint="addEndpoint" @add-node="addNodeFromPalette" />
        </aside>

        <section class="canvas-wrap" aria-label="流程设计画布区域">
          <FlowCanvas
            ref="canvasRef"
            :nodes="graph.nodes"
            :edges="graph.edges"
            :selection="selection"
            :variables="variables"
            :readonly="readonly || loading"
            @select="onSelect"
            @create-edge="onCreateEdge"
            @drop-node="onDropNode"
            @delete-node="onDeleteNode"
            @delete-edge="onDeleteEdge"
          />
        </section>

        <aside class="prop-panel" aria-label="流程属性配置">
          <FlowNodePanel
            v-if="selection.type === 'node' && selectedNode"
            :node="selectedNode"
            :readonly="readonly || loading"
            :approver-variables="approverVariables"
            @delete="onDeleteNode(selection.key)"
          />
          <FlowEdgePanel
            v-else-if="selection.type === 'edge' && selectedEdge"
            :edge="selectedEdge"
            :nodes="graph.nodes"
            :variables="variables"
            :readonly="readonly || loading"
            @delete="onDeleteEdge(selection.key)"
          />
          <div v-else class="prop-empty" role="status">
            <strong>尚未选择元素</strong>
            <span>选择节点可维护审批人和会签方式；选择连线可维护默认分支与条件。</span>
          </div>
        </aside>
      </div>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getFlow, saveFlow, publishFlow, listFlowVariables, listApproverVariables } from '@/api/flowDesign';
import FlowCanvas from '@/views/system/flow/FlowCanvas.vue';
import FlowPalette from '@/views/system/flow/FlowPalette.vue';
import FlowNodePanel from '@/views/system/flow/FlowNodePanel.vue';
import FlowEdgePanel from '@/views/system/flow/FlowEdgePanel.vue';

const route = useRoute();
const router = useRouter();
const id = route.params.id;
const BIZ_TYPE_MAP = { ALLOC_ADJUST: '业绩调整', TARGET_ADJUST: '目标方案' };
const NODE_DEFAULT_NAME = { START: '开始', APPROVAL: '审批', GATEWAY: '网关', END: '结束' };
const bizTypeLabel = (type) => BIZ_TYPE_MAP[type] || (type ?? '-');

const graph = reactive({ name: '', bizType: '', nodes: [], edges: [] });
const variables = ref([]);
const approverVariables = ref([]);
const readonly = ref(false);
const loading = ref(false);
const loadConfirmed = ref(false);
const loadError = ref('');
const saving = ref(false);
const publishing = ref(false);
const selection = reactive({ type: null, key: null });
const canvasRef = ref(null);

const hasStart = computed(() => graph.nodes.some((node) => node.nodeType === 'START'));
const selectedNode = computed(() => selection.type === 'node' ? graph.nodes.find((node) => node.nodeKey === selection.key) : null);
const selectedEdge = computed(() => selection.type === 'edge' ? graph.edges[selection.key] : null);
const designerState = computed(() => {
  if (loading.value) return '流程模型加载中';
  if (loadError.value) return '流程模型加载失败，可重试';
  if (readonly.value) return '只读查看模式，不可修改或发布';
  if (saving.value) return '草稿保存中';
  if (publishing.value) return '流程发布中';
  return '编辑模式：所有变更尚未保存前仅保留在当前页面';
});

async function loadGraph() {
  if (loading.value) return;
  loading.value = true;
  loadConfirmed.value = false;
  loadError.value = '';
  try {
    const model = (await getFlow(id)) || {};
    graph.name = model.name || '';
    graph.bizType = model.bizType || '';
    graph.nodes = (Array.isArray(model.nodes) ? model.nodes : []).map((node) => ({
      nodeKey: node.nodeKey || '',
      nodeType: node.nodeType || 'APPROVAL',
      name: node.name || '',
      approveMode: node.approveMode || (node.nodeType === 'APPROVAL' ? 'ANY' : null),
      sortNo: node.sortNo,
      posX: node.posX != null ? node.posX : null,
      posY: node.posY != null ? node.posY : null,
      approvers: Array.isArray(node.approvers) ? node.approvers.map((approver) => ({ ...approver })) : []
    }));
    graph.edges = (Array.isArray(model.edges) ? model.edges : []).map((edge) => ({
      fromNodeKey: edge.fromNodeKey || '',
      toNodeKey: edge.toNodeKey || '',
      isDefault: !!edge.isDefault,
      outputName: edge.outputName || '',
      condition: edge.condition && typeof edge.condition === 'object' ? edge.condition : null
    }));
    readonly.value = model.readonly === true || model.isReadonlyImport === true || model.isReadonlyImport === 1;
    const [flowVariables, approverVariableList] = await Promise.allSettled([
      listFlowVariables(graph.bizType),
      listApproverVariables(graph.bizType)
    ]);
    variables.value = flowVariables.status === 'fulfilled' && Array.isArray(flowVariables.value) ? flowVariables.value : [];
    approverVariables.value = approverVariableList.status === 'fulfilled' && Array.isArray(approverVariableList.value) ? approverVariableList.value : [];
    clearSelection();
    loadConfirmed.value = true;
  } catch (error) {
    loadError.value = `流程模型加载失败：${error?.message || '请稍后重试'}`;
    ElMessage.error(loadError.value);
  } finally {
    loading.value = false;
  }
}

function onSelect(nextSelection) {
  selection.type = nextSelection.type;
  selection.key = nextSelection.key;
}
function genNodeKey(type) {
  if (type === 'START') return graph.nodes.some((node) => node.nodeKey === 'start') ? uniq('start') : 'start';
  return uniq({ END: 'end', APPROVAL: 'approval', GATEWAY: 'gateway' }[type] || 'node');
}
function uniq(prefix) {
  let index = 1;
  let key = '';
  do { key = `${prefix}_${index++}`; } while (graph.nodes.some((node) => node.nodeKey === key));
  return key;
}
function makeNode(type, posX, posY) {
  return {
    nodeKey: genNodeKey(type), nodeType: type, name: NODE_DEFAULT_NAME[type] || '',
    approveMode: type === 'APPROVAL' ? 'ANY' : null, sortNo: graph.nodes.length + 1,
    posX, posY, approvers: []
  };
}
function onDropNode({ nodeType, posX, posY }) {
  if (readonly.value || loading.value) return;
  const node = makeNode(nodeType, posX, posY);
  graph.nodes.push(node);
  onSelect({ type: 'node', key: node.nodeKey });
}
function addNodeFromPalette(nodeType) {
  if (readonly.value || loading.value) return;
  const position = canvasRef.value?.nextDropPosition?.() || { posX: 180, posY: 120 + graph.nodes.length * 32 };
  onDropNode({ nodeType, ...position });
}
function addEndpoint(type) {
  if (readonly.value || loading.value) return;
  if (type === 'START' && hasStart.value) {
    ElMessage.warning('已存在开始节点');
    return;
  }
  const node = makeNode(type, 360, type === 'START' ? 30 : Math.max(120, graph.nodes.length * 30 + 120));
  graph.nodes.push(node);
  onSelect({ type: 'node', key: node.nodeKey });
}
function onCreateEdge({ fromNodeKey, toNodeKey }) {
  if (readonly.value || loading.value) return;
  graph.edges.push({ fromNodeKey, toNodeKey, isDefault: false, outputName: '', condition: null });
  onSelect({ type: 'edge', key: graph.edges.length - 1 });
}
function onDeleteNode(nodeKey) {
  if (readonly.value || loading.value) return;
  const index = graph.nodes.findIndex((node) => node.nodeKey === nodeKey);
  if (index < 0) return;
  graph.nodes.splice(index, 1);
  for (let edgeIndex = graph.edges.length - 1; edgeIndex >= 0; edgeIndex -= 1) {
    if (graph.edges[edgeIndex].fromNodeKey === nodeKey || graph.edges[edgeIndex].toNodeKey === nodeKey) graph.edges.splice(edgeIndex, 1);
  }
  clearSelection();
}
function onDeleteEdge(edgeIndex) {
  if (readonly.value || loading.value || edgeIndex == null || edgeIndex < 0 || edgeIndex >= graph.edges.length) return;
  graph.edges.splice(edgeIndex, 1);
  clearSelection();
}
function clearSelection() { selection.type = null; selection.key = null; }

function buildPayload() {
  return {
    name: graph.name,
    bizType: graph.bizType,
    nodes: graph.nodes.map((node, index) => ({
      nodeKey: node.nodeKey,
      nodeType: node.nodeType,
      name: node.name,
      approveMode: node.nodeType === 'APPROVAL' ? (node.approveMode || 'ANY') : null,
      sortNo: node.sortNo != null ? node.sortNo : index,
      posX: node.posX != null ? Math.round(node.posX) : null,
      posY: node.posY != null ? Math.round(node.posY) : null,
      approvers: (node.approvers || []).map((approver) => ({
        approverType: approver.approverType,
        approverValue: approver.approverValue,
        orgScope: approver.orgScope || null,
        roleCode: approver.roleCode || null
      }))
    })),
    edges: graph.edges.map((edge) => ({
      fromNodeKey: edge.fromNodeKey,
      toNodeKey: edge.toNodeKey,
      isDefault: !!edge.isDefault,
      outputName: edge.outputName || null,
      condition: edge.condition || null
    }))
  };
}
function validateGraph() {
  const errors = [];
  const startCount = graph.nodes.filter((node) => node.nodeType === 'START').length;
  const endCount = graph.nodes.filter((node) => node.nodeType === 'END').length;
  if (startCount !== 1) errors.push(`必须恰好 1 个开始节点（当前 ${startCount} 个）`);
  if (endCount < 1) errors.push('至少需要 1 个结束节点');
  const keys = graph.nodes.map((node) => node.nodeKey);
  if (keys.some((key) => !key || !key.trim())) errors.push('存在空的节点标识');
  const duplicated = keys.filter((key, index) => key && keys.indexOf(key) !== index);
  if (duplicated.length) errors.push(`节点标识重复：${[...new Set(duplicated)].join('、')}`);
  for (const node of graph.nodes) {
    if (node.nodeType === 'APPROVAL' && (node.approvers || []).length === 0) errors.push(`审批节点「${node.name || node.nodeKey || '?'}」缺少审批人`);
  }
  return errors;
}

function goBack() { router.push('/system/workflow-flows'); }
async function doSave() {
  if (readonly.value || loading.value || !loadConfirmed.value || saving.value || publishing.value) return;
  const errors = validateGraph();
  if (errors.length) {
    ElMessage.warning(`草稿存在待修正项：${errors.join('；')}`);
    return;
  }
  saving.value = true;
  try {
    await saveFlow(id, buildPayload());
    ElMessage.success('草稿已保存');
  } catch (error) {
    ElMessage.error(`保存失败：${error?.message || '请稍后重试'}`);
  } finally {
    saving.value = false;
  }
}
async function doPublish() {
  if (readonly.value || loading.value || !loadConfirmed.value || saving.value || publishing.value) return;
  const errors = validateGraph();
  if (errors.length) {
    await ElMessageBox.alert(errors.map((error) => `· ${error}`).join('<br/>'), '无法发布：请先修正以下问题', {
      confirmButtonText: '知道了', type: 'warning', dangerouslyUseHTMLString: true
    });
    return;
  }
  publishing.value = true;
  try {
    await saveFlow(id, buildPayload());
    await publishFlow(id);
    ElMessage.success('流程已发布');
  } catch (error) {
    await ElMessageBox.alert(error?.message || '发布失败，请检查流程配置', '发布失败', { confirmButtonText: '知道了', type: 'error' });
  } finally {
    publishing.value = false;
  }
}

onMounted(loadGraph);
</script>

<style lang="scss" scoped>
.flow-edit-page {
  grid-template-rows: auto auto auto minmax(560px, 1fr);
  min-height: 760px;
}
.ro-alert { margin: 0; }
.designer-meta { padding: var(--space-3) var(--space-4); }
.flow-meta-fields { align-items: end; display: flex; flex-wrap: wrap; gap: var(--space-4); }
.meta-field { display: grid; gap: var(--space-1); min-width: 220px; }
.meta-field:first-child { flex: 1 1 320px; max-width: 460px; }
.meta-label { color: var(--color-text); font-size: 12px; line-height: 18px; }
.meta-value { color: var(--color-text-strong); font-size: 14px; font-weight: 600; line-height: 32px; }
.designer-state { color: var(--color-text-muted); font-size: 12px; line-height: 18px; margin: 0 0 var(--space-1) auto; max-width: 360px; text-align: right; }
.designer-panel { display: flex; flex-direction: column; min-height: 0; }
.designer-toolbar { flex: 0 0 auto; }
.designer-toolbar .hint { margin: 0; max-width: 760px; }
.designer { align-items: stretch; display: flex; flex: 1 1 auto; gap: var(--space-3); min-height: 500px; min-width: 0; }
.palette-panel { flex: 0 0 auto; }
.canvas-wrap { flex: 1 1 auto; min-width: 0; }
.prop-panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  flex: 0 0 336px;
  overflow-y: auto;
  padding: var(--space-4);
}
.prop-empty { align-items: center; color: var(--color-text-muted); display: flex; flex-direction: column; font-size: 13px; gap: var(--space-2); justify-content: center; line-height: 20px; min-height: 360px; text-align: center; }
.prop-empty strong { color: var(--color-text); font-size: 14px; }
</style>
