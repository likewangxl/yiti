<template>
  <!-- 右栏-选中节点：编辑审批环节参数（名称/类型/审批模式/审批人） -->
  <div class="node-panel" v-if="node">
    <div class="panel-head">
      <span class="panel-title">{{ nodeTypeLabel(node.nodeType) }}节点</span>
      <el-button
        v-if="!readonly"
        link
        type="danger"
        size="small"
        @click="$emit('delete')"
      >删除节点</el-button>
    </div>

    <el-form label-position="top" size="default">
      <el-form-item label="节点名称">
        <el-input
          v-model="node.name"
          :disabled="readonly"
          maxlength="100"
          placeholder="请输入节点名称"
        />
      </el-form-item>

      <el-form-item label="节点标识">
        <el-input :model-value="node.nodeKey" disabled />
        <div class="field-hint">系统内部唯一标识，自动生成，不可修改</div>
      </el-form-item>

      <el-form-item label="节点类型">
        <el-select v-model="node.nodeType" :disabled="readonly || isEndpoint" style="width: 100%" @change="onTypeChange">
          <el-option label="审批" value="APPROVAL" />
          <el-option label="网关" value="GATEWAY" />
          <el-option v-if="node.nodeType === 'START'" label="开始" value="START" />
          <el-option v-if="node.nodeType === 'END'" label="结束" value="END" />
        </el-select>
      </el-form-item>

      <!-- 审批模式（仅 APPROVAL）-->
      <el-form-item v-if="node.nodeType === 'APPROVAL'" label="审批模式">
        <el-radio-group v-model="node.approveMode" :disabled="readonly">
          <el-radio-button value="ANY">或签</el-radio-button>
          <el-radio-button value="ALL">会签</el-radio-button>
        </el-radio-group>
        <div class="field-hint">或签：任一审批人通过即可；会签：所有审批人均需通过</div>
      </el-form-item>

      <!-- 审批人（仅 APPROVAL）。机构归属已下放到「层级角色」审批人，节点不再单独配置 -->
      <el-form-item v-if="node.nodeType === 'APPROVAL'" label="审批人">
        <div class="apv-wrap" :class="{ readonly }">
          <ApproverPicker v-if="!readonly" v-model="node.approvers" :approver-variables="approverVariables" />
          <div v-else class="apv-readonly">
            <div v-for="(a, i) in (node.approvers || [])" :key="i" class="apv-ro-row">
              {{ approverTypeLabel(a.approverType) }}：{{ a.approverValue }}
            </div>
            <span v-if="!(node.approvers || []).length" class="field-hint">无</span>
          </div>
        </div>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import ApproverPicker from '@/views/system/flow/ApproverPicker.vue';

const props = defineProps({
  /** 选中的节点对象（父级 graph.nodes 引用），直接 v-model 写回 */
  node: { type: Object, default: null },
  readonly: { type: Boolean, default: false },
  /** VAR 审批人可选的名单类流程变量 [{field,label}] */
  approverVariables: { type: Array, default: () => [] }
});
defineEmits(['delete']);

const NODE_TYPE_LABEL = { START: '开始', APPROVAL: '审批', GATEWAY: '网关', END: '结束' };
function nodeTypeLabel(t) { return NODE_TYPE_LABEL[t] || t; }

const APPROVER_TYPE_LABEL = { ROLE: '角色', ORG: '机构', USER: '指定人' };
function approverTypeLabel(t) { return APPROVER_TYPE_LABEL[t] || t; }

// START/END 端点不允许改类型
const isEndpoint = computed(() => props.node?.nodeType === 'START' || props.node?.nodeType === 'END');

/** 切到 APPROVAL 时补默认或签；切走时清空审批模式 */
function onTypeChange() {
  if (props.node.nodeType === 'APPROVAL') {
    if (!props.node.approveMode) props.node.approveMode = 'ANY';
    if (!Array.isArray(props.node.approvers)) props.node.approvers = [];
  } else {
    props.node.approveMode = null;
  }
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
.field-hint { font-size: 11px; color: $text-3; line-height: 1.5; margin-top: 2px; }
.apv-wrap { width: 100%; }
.apv-ro-row { font-size: 13px; color: $text-2; padding: 2px 0; }
</style>
