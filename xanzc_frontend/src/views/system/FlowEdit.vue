<template>
  <div>
    <div class="page-h">
      <h1>
        审批流程编辑
        <span class="sub">表单式编辑节点 / 审批人 / 连线 / 条件分支；保存草稿后发布生成影子流程，不影响现有线上审批</span>
      </h1>
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
      title="只读导入流程，请返回列表用『克隆』另存后再编辑"
    />

    <!-- 基本信息 -->
    <div class="card-section">
      <div class="sec-title">基本信息</div>
      <el-form label-width="90px" size="default">
        <el-form-item label="流程名称">
          <el-input v-model="graph.name" :disabled="readonly" placeholder="请输入流程名称" maxlength="100" style="max-width: 360px" />
        </el-form-item>
        <el-form-item label="业务类型">
          <span class="ro-text">{{ bizTypeLabel(graph.bizType) }}</span>
        </el-form-item>
      </el-form>
    </div>

    <!-- 节点 -->
    <div class="card-section">
      <div class="sec-title">节点</div>
      <el-table :data="graph.nodes" size="default" empty-text="暂无节点">
        <el-table-column label="节点标识" min-width="150">
          <template #default="{ row }">
            <el-input v-model="row.nodeKey" :disabled="readonly" placeholder="唯一标识，如 start / approve1" />
          </template>
        </el-table-column>
        <el-table-column label="名称" min-width="150">
          <template #default="{ row }">
            <el-input v-model="row.name" :disabled="readonly" placeholder="节点名称" />
          </template>
        </el-table-column>
        <el-table-column label="类型" width="130">
          <template #default="{ row }">
            <el-select v-model="row.nodeType" :disabled="readonly" @change="onNodeTypeChange(row)">
              <el-option v-for="t in NODE_TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="审批模式" width="130">
          <template #default="{ row }">
            <el-select v-model="row.approveMode" :disabled="readonly || row.nodeType !== 'APPROVAL'" placeholder="—">
              <el-option label="或签" value="ANY" />
              <el-option label="会签" value="ALL" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row, $index }">
            <el-button
              v-if="row.nodeType === 'APPROVAL'"
              link
              type="primary"
              size="small"
              @click="openApproverDlg(row)"
            >配置审批人({{ (row.approvers || []).length }})</el-button>
            <el-button link type="danger" size="small" :disabled="readonly" @click="removeNode($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button v-if="!readonly" text type="primary" style="margin-top: 8px" @click="addNode">+ 添加节点</el-button>
    </div>

    <!-- 连线 -->
    <div class="card-section">
      <div class="sec-title">连线</div>
      <el-table :data="graph.edges" size="default" empty-text="暂无连线">
        <el-table-column label="起点" width="180">
          <template #default="{ row }">
            <el-select v-model="row.fromNodeKey" :disabled="readonly" filterable placeholder="选择起点节点" style="width: 100%">
              <el-option v-for="n in nodeKeyOptions" :key="n.value" :label="n.label" :value="n.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="终点" width="180">
          <template #default="{ row }">
            <el-select v-model="row.toNodeKey" :disabled="readonly" filterable placeholder="选择终点节点" style="width: 100%">
              <el-option v-for="n in nodeKeyOptions" :key="n.value" :label="n.label" :value="n.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="默认分支" width="100" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.isDefault" :disabled="readonly" />
          </template>
        </el-table-column>
        <el-table-column label="条件" min-width="200">
          <template #default="{ row }">
            <span class="cond-text">{{ conditionSummary(row.condition) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row, $index }">
            <el-button link type="primary" size="small" @click="openConditionDlg(row)">编辑条件</el-button>
            <el-button link type="danger" size="small" :disabled="readonly" @click="removeEdge($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button v-if="!readonly" text type="primary" style="margin-top: 8px" @click="addEdge">+ 添加连线</el-button>
    </div>

    <!-- 结构预览 -->
    <div class="card-section">
      <div class="sec-title">结构预览</div>
      <pre class="preview">{{ structurePreview }}</pre>
    </div>

    <!-- 审批人配置弹窗 -->
    <el-dialog v-model="approverDlg.show" :title="`配置审批人 · ${approverDlg.node?.name || approverDlg.node?.nodeKey || ''}`" width="560px">
      <ApproverPicker v-if="approverDlg.node" v-model="approverDlg.node.approvers" />
      <template #footer>
        <el-button type="primary" @click="approverDlg.show = false">完成</el-button>
      </template>
    </el-dialog>

    <!-- 条件配置弹窗 -->
    <el-dialog v-model="conditionDlg.show" title="编辑分支条件" width="640px">
      <ConditionBuilder v-if="conditionDlg.edge" v-model="conditionDlg.edge.condition" :variables="variables" />
      <template #footer>
        <el-button type="primary" @click="conditionDlg.show = false">完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getFlow, saveFlow, publishFlow, listFlowVariables } from '@/api/flowDesign';
import ApproverPicker from '@/views/system/flow/ApproverPicker.vue';
import ConditionBuilder from '@/views/system/flow/ConditionBuilder.vue';

const route = useRoute();
const router = useRouter();
/** 当前编辑的流程 ID，来自路由参数 :id */
const id = route.params.id;

// 业务类型显示映射（与 FlowList 对齐）
const BIZ_TYPE_MAP = {
  ALLOC_ADJUST: '业绩调整',
  TARGET_ADJUST: '目标方案'
};
function bizTypeLabel(type) {
  return BIZ_TYPE_MAP[type] || (type ?? '-');
}

// 节点类型选项（value=后端枚举，label=中文）
const NODE_TYPE_OPTIONS = [
  { value: 'START', label: '开始' },
  { value: 'APPROVAL', label: '审批' },
  { value: 'GATEWAY', label: '网关' },
  { value: 'END', label: '结束' }
];
const NODE_TYPE_MAP = NODE_TYPE_OPTIONS.reduce((m, t) => ((m[t.value] = t.label), m), {});

// 条件运算符中文映射（与 ConditionBuilder 内选项对齐）
const OP_LABEL_MAP = {
  EQ: '等于', NE: '不等于', GT: '大于', GE: '大于等于',
  LT: '小于', LE: '小于等于', IN: '属于', NOT_IN: '不属于', CONTAINS: '包含'
};
const LOGIC_LABEL_MAP = { AND: '且', OR: '或' };

// === 主数据 ===
// graph 用 reactive 持有，确保 nodes/edges/approvers 都是数组
const graph = reactive({ name: '', bizType: '', nodes: [], edges: [] });
// 条件分支可用变量白名单 [{field,label,type}]
const variables = ref([]);
// 只读导入流程标记：禁用保存/发布
const readonly = ref(false);
const saving = ref(false);
const publishing = ref(false);

onMounted(async () => {
  let model = null;
  try {
    model = await getFlow(id);
  } catch (e) {
    ElMessage.error('加载流程失败：' + (e?.message || e));
  }
  model = model || {};
  // 规整：确保数组字段不为 null
  graph.name = model.name || '';
  graph.bizType = model.bizType || '';
  graph.nodes = (Array.isArray(model.nodes) ? model.nodes : []).map(n => ({
    nodeKey: n.nodeKey || '',
    nodeType: n.nodeType || 'APPROVAL',
    name: n.name || '',
    approveMode: n.approveMode || (n.nodeType === 'APPROVAL' ? 'ANY' : null),
    sortNo: n.sortNo,
    approvers: Array.isArray(n.approvers) ? n.approvers.map(a => ({ ...a })) : []
  }));
  graph.edges = (Array.isArray(model.edges) ? model.edges : []).map(e => ({
    fromNodeKey: e.fromNodeKey || '',
    toNodeKey: e.toNodeKey || '',
    isDefault: !!e.isDefault,
    // condition 可能是对象或 null
    condition: e.condition && typeof e.condition === 'object' ? e.condition : null
  }));
  // 只读导入标记（兼容多种字段命名）
  readonly.value = model.readonly === true || model.isReadonlyImport === true || model.isReadonlyImport === 1;

  // 加载条件分支可用变量
  try {
    variables.value = await listFlowVariables(graph.bizType);
  } catch {
    variables.value = [];
  }
});

// 节点 nodeKey 下拉选项（连线起终点用）
const nodeKeyOptions = computed(() =>
  graph.nodes
    .filter(n => n.nodeKey)
    .map(n => ({ value: n.nodeKey, label: `${n.nodeKey}${n.name ? '（' + n.name + '）' : ''}` }))
);

// === 节点操作 ===
function addNode() {
  graph.nodes.push({ nodeKey: '', nodeType: 'APPROVAL', name: '', approveMode: 'ANY', sortNo: undefined, approvers: [] });
}
function removeNode(idx) {
  graph.nodes.splice(idx, 1);
}
/** 类型切换：非 APPROVAL 清空审批模式，切回 APPROVAL 补默认或签 */
function onNodeTypeChange(row) {
  if (row.nodeType === 'APPROVAL') {
    if (!row.approveMode) row.approveMode = 'ANY';
  } else {
    row.approveMode = null;
  }
}

// === 连线操作 ===
function addEdge() {
  graph.edges.push({ fromNodeKey: '', toNodeKey: '', isDefault: false, condition: null });
}
function removeEdge(idx) {
  graph.edges.splice(idx, 1);
}

// === 审批人弹窗 ===
const approverDlg = reactive({ show: false, node: null });
function openApproverDlg(node) {
  // 直接引用节点对象，ApproverPicker v-model 写回即生效
  approverDlg.node = node;
  approverDlg.show = true;
}

// === 条件弹窗 ===
const conditionDlg = reactive({ show: false, edge: null });
function openConditionDlg(edge) {
  // 直接引用连线对象，ConditionBuilder v-model 写回 edge.condition 即生效
  conditionDlg.edge = edge;
  conditionDlg.show = true;
}

/** 把一条 condition 对象文字化成摘要，无条件返回 — */
function conditionSummary(cond) {
  if (!cond || !Array.isArray(cond.conditions) || cond.conditions.length === 0) return '—';
  const parts = cond.conditions.map(c => {
    const fieldLabel = (variables.value.find(v => v.field === c.field)?.label) || c.field || '?';
    const opLabel = OP_LABEL_MAP[c.op] || c.op || '?';
    return `${fieldLabel} ${opLabel} ${c.value ?? ''}`.trim();
  });
  const sep = ` ${LOGIC_LABEL_MAP[cond.logic] || '且'} `;
  return parts.join(sep);
}

// === 结构预览（只读文本） ===
const structurePreview = computed(() => {
  if (graph.nodes.length === 0) return '（暂无节点）';
  // 节点按 sortNo 升序，无 sortNo 的保持出现序排后
  const ordered = [...graph.nodes].sort((a, b) => {
    const sa = a.sortNo, sb = b.sortNo;
    if (sa == null && sb == null) return 0;
    if (sa == null) return 1;
    if (sb == null) return -1;
    return sa - sb;
  });
  const lines = [];
  for (const n of ordered) {
    const typeLabel = NODE_TYPE_MAP[n.nodeType] || n.nodeType || '?';
    let extra = '';
    if (n.nodeType === 'APPROVAL') {
      const modeLabel = n.approveMode === 'ALL' ? '会签' : '或签';
      const cnt = (n.approvers || []).length;
      extra = ` · ${modeLabel} · 审批人${cnt}`;
    }
    lines.push(`[${typeLabel}] ${n.nodeKey || '(未命名)'}${n.name ? '【' + n.name + '】' : ''}${extra}`);
    // 该节点的出边
    const outEdges = graph.edges.filter(e => e.fromNodeKey === n.nodeKey);
    for (const e of outEdges) {
      const condLabel = conditionSummary(e.condition);
      const defMark = e.isDefault ? '[默认] ' : '';
      const condPart = condLabel === '—' ? '' : `（分支：${condLabel}）`;
      lines.push(`    → ${defMark}${e.toNodeKey || '(未指定)'} ${condPart}`.trimEnd());
    }
  }
  return lines.join('\n');
});

// === 规整 graph 给后端 ===
function buildPayload() {
  return {
    name: graph.name,
    bizType: graph.bizType,
    nodes: graph.nodes.map((n, i) => ({
      nodeKey: n.nodeKey,
      nodeType: n.nodeType,
      name: n.name,
      // 仅 APPROVAL 携带审批模式，其余置 null
      approveMode: n.nodeType === 'APPROVAL' ? (n.approveMode || 'ANY') : null,
      sortNo: n.sortNo != null ? n.sortNo : i,
      approvers: (n.approvers || []).map(a => ({ approverType: a.approverType, approverValue: a.approverValue }))
    })),
    edges: graph.edges.map(e => ({
      fromNodeKey: e.fromNodeKey,
      toNodeKey: e.toNodeKey,
      isDefault: !!e.isDefault,
      condition: e.condition || null
    }))
  };
}

/** 轻校验：返回问题数组（空数组=通过） */
function validateGraph() {
  const errs = [];
  const startCount = graph.nodes.filter(n => n.nodeType === 'START').length;
  const endCount = graph.nodes.filter(n => n.nodeType === 'END').length;
  if (startCount !== 1) errs.push(`必须恰好 1 个开始节点（当前 ${startCount} 个）`);
  if (endCount < 1) errs.push('至少需要 1 个结束节点');

  // nodeKey 非空且唯一
  const keys = graph.nodes.map(n => n.nodeKey);
  if (keys.some(k => !k || !k.trim())) errs.push('存在空的节点标识');
  const dup = keys.filter((k, i) => k && keys.indexOf(k) !== i);
  if (dup.length) errs.push(`节点标识重复：${[...new Set(dup)].join('、')}`);

  // 每个 APPROVAL 至少 1 审批人
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
  // 轻校验只提示不阻断，后端最终校验
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
  // 发布前强制校验，不通过直接弹提示不调接口
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
    // 发布前先存一次草稿，确保后端拿到最新图
    await saveFlow(id, buildPayload());
    await publishFlow(id);
    ElMessage.success('发布成功');
  } catch (e) {
    // 后端校验明细可能较长，用 alert 完整展示不自动消失
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
.page-h h1 .sub { font-size: 13px; color: $text-3; margin-left: 12px; font-weight: 400; }
.ro-alert { margin-bottom: 14px; }
.sec-title { font-size: 15px; font-weight: 600; margin-bottom: 12px; color: $text-1; }
.ro-text { color: $text-2; }
.cond-text { color: $text-2; font-size: 13px; }
.preview {
  margin: 0;
  padding: 12px 14px;
  background: $bg-soft;
  border: 1px solid $border-2;
  border-radius: 4px;
  font-family: ui-monospace, monospace;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
