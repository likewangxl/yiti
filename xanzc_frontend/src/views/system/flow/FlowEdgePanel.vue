<template>
  <!-- 右栏-选中连线：编辑动态条件分支参数（默认分支 + 条件增删改） -->
  <div class="edge-panel" v-if="edge">
    <div class="panel-head">
      <span class="panel-title">流转连线</span>
      <el-button
        v-if="!readonly"
        link
        type="danger"
        size="small"
        @click="$emit('delete')"
      >删除连线</el-button>
    </div>

    <!-- 起止节点（只读展示）-->
    <div class="edge-route">
      <span class="route-node">{{ fromLabel }}</span>
      <span class="route-arrow">→</span>
      <span class="route-node">{{ toLabel }}</span>
    </div>

    <el-form label-position="top" size="default">
      <el-form-item label="输出名称">
        <el-input
          v-model="edge.outputName"
          :disabled="readonly"
          maxlength="64"
          placeholder="走向标签，如 提交部门负责人"
        />
        <div class="field-hint">
          网关出边作为"下一步走向"选项的可读标签；经办审批时按它动态选择走向并反显。
        </div>
      </el-form-item>

      <el-form-item label="默认分支">
        <el-switch v-model="edge.isDefault" :disabled="readonly" @change="onDefaultChange" />
        <div class="field-hint">
          网关无任何条件匹配时走默认分支；同一网关建议仅设置一条默认分支。
        </div>
      </el-form-item>

      <el-form-item label="条件分支">
        <div v-if="edge.isDefault" class="field-hint">默认分支无需配置条件。</div>
        <div v-else-if="readonly" class="cond-readonly">
          <span v-if="conditionText" class="cond-text">{{ conditionText }}</span>
          <span v-else class="field-hint">无条件（直接流转）</span>
        </div>
        <ConditionBuilder
          v-else
          v-model="edge.condition"
          :variables="variables"
        />
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import ConditionBuilder from '@/views/system/flow/ConditionBuilder.vue';

const props = defineProps({
  /** 选中的连线对象（父级 graph.edges 引用），直接 v-model 写回 condition/isDefault */
  edge: { type: Object, default: null },
  /** 节点列表，用于把 fromNodeKey/toNodeKey 显示成节点名 */
  nodes: { type: Array, default: () => [] },
  /** 条件分支变量白名单 */
  variables: { type: Array, default: () => [] },
  readonly: { type: Boolean, default: false }
});
defineEmits(['delete']);

const OP_LABEL = {
  EQ: '等于', NE: '不等于', GT: '大于', GE: '大于等于',
  LT: '小于', LE: '小于等于', IN: '属于', NOT_IN: '不属于', CONTAINS: '包含'
};
const LOGIC_LABEL = { AND: '且', OR: '或' };

function nodeLabel(key) {
  const n = props.nodes.find(x => x.nodeKey === key);
  return n ? (n.name || n.nodeKey) : (key || '?');
}
const fromLabel = computed(() => nodeLabel(props.edge?.fromNodeKey));
const toLabel = computed(() => nodeLabel(props.edge?.toNodeKey));

/** 只读态条件文字化 */
const conditionText = computed(() => {
  const cond = props.edge?.condition;
  if (!cond || !Array.isArray(cond.conditions) || !cond.conditions.length) return '';
  const parts = cond.conditions.map(c => {
    const label = (props.variables.find(v => v.field === c.field)?.label) || c.field || '?';
    return `${label} ${OP_LABEL[c.op] || c.op || ''} ${c.value ?? ''}`.trim();
  });
  return parts.join(` ${LOGIC_LABEL[cond.logic] || '且'} `);
});

/** 勾选默认分支时清空条件，避免默认+条件并存语义冲突 */
function onDefaultChange(val) {
  if (val) props.edge.condition = null;
}
</script>

<style lang="scss" scoped>
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.panel-title { font-size: 15px; font-weight: 600; color: $text-1; }
.edge-route {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  margin-bottom: 14px;
  background: $bg-soft;
  border: 1px solid $border-2;
  border-radius: 6px;
  font-size: 13px;
}
.route-node { font-weight: 600; color: $text-1; }
.route-arrow { color: #2563eb; }
.field-hint { font-size: 11px; color: $text-3; line-height: 1.5; margin-top: 2px; }
.cond-text { font-size: 13px; color: $text-2; }
</style>
