<template>
  <!-- 条件构造器：编辑一条连线的分支条件，v-model 绑定 {logic, conditions:[{field,op,value}]} 或 null -->
  <div class="condition-builder">

    <!-- 逻辑关系（仅多条件时显示） -->
    <div v-if="localConditions.length > 1" class="logic-row">
      <span class="logic-label">逻辑关系：</span>
      <el-radio-group v-model="localLogic" @change="emitUpdate">
        <el-radio-button value="AND">且</el-radio-button>
        <el-radio-button value="OR">或</el-radio-button>
      </el-radio-group>
    </div>

    <!-- variables 为空时给用户提示 -->
    <div v-if="variables.length === 0" class="empty-hint">
      该业务无可用变量，请联系管理员配置流程变量白名单。
    </div>

    <!-- 条件行列表 -->
    <div
      v-for="(cond, idx) in localConditions"
      :key="idx"
      class="condition-row"
    >
      <!-- 字段选择 -->
      <el-select
        v-model="cond.field"
        style="width: 140px; flex-shrink: 0"
        placeholder="选择或输入变量"
        filterable
        allow-create
        default-first-option
        @change="emitUpdate"
      >
        <el-option
          v-for="v in variables"
          :key="v.field"
          :label="v.label"
          :value="v.field"
        />
      </el-select>

      <!-- 运算符选择 -->
      <el-select
        v-model="cond.op"
        style="width: 130px; flex-shrink: 0"
        placeholder="运算符"
        @change="emitUpdate"
      >
        <el-option label="等于"      value="EQ"       />
        <el-option label="不等于"    value="NE"       />
        <el-option label="大于"      value="GT"       />
        <el-option label="大于等于"  value="GE"       />
        <el-option label="小于"      value="LT"       />
        <el-option label="小于等于"  value="LE"       />
        <el-option label="属于(多值)"    value="IN"   />
        <el-option label="不属于(多值)"  value="NOT_IN" />
        <el-option label="包含"      value="CONTAINS" />
      </el-select>

      <!-- 值输入：窄面板下换行独占一行，加大宽度；IN/NOT_IN 给多值提示 -->
      <el-input
        v-model="cond.value"
        style="flex: 1 1 200px; min-width: 200px"
        :placeholder="isMultiValue(cond.op)
          ? '多个值逗号分隔，如 CORP,PER'
          : '请输入值'"
        @input="emitUpdate"
      />

      <!-- 删除按钮 -->
      <el-button
        type="danger"
        text
        style="flex-shrink: 0; margin-left: 4px"
        @click="removeCondition(idx)"
      >删除</el-button>
    </div>

    <!-- 添加条件按钮 -->
    <el-button
      type="primary"
      text
      style="margin-top: 6px"
      @click="addCondition"
    >+ 添加条件</el-button>

  </div>
</template>

<script setup>
import { ref, watch } from 'vue';

// ---- props / emits ----
const props = defineProps({
  /**
   * v-model 绑定的条件对象，格式：
   *   { logic: 'AND'|'OR', conditions: [{field, op, value}] }
   * 无条件时为 null。
   */
  modelValue: {
    type: Object,
    default: null
  },
  /**
   * 流程变量白名单，由父组件从后端接口传入。
   * 元素格式：{ field: string, label: string, type: string }
   */
  variables: {
    type: Array,
    default: () => []
  }
});

const emit = defineEmits(['update:modelValue']);

// ---- 本地副本（深拷贝，避免直接改 props） ----
const localLogic = ref('AND');
const localConditions = ref([]);

/** 从 props.modelValue 初始化本地状态 */
function syncFromProps(val) {
  if (val && Array.isArray(val.conditions)) {
    localLogic.value = val.logic || 'AND';
    // 每行深拷贝，防止外部引用污染
    localConditions.value = val.conditions.map(c => ({ ...c }));
  } else {
    localLogic.value = 'AND';
    localConditions.value = [];
  }
}

// 初始化 & 监听外部变更（父组件重置时同步）
watch(
  () => props.modelValue,
  (val) => syncFromProps(val),
  { immediate: true, deep: true }
);

// ---- 工具函数 ----

/** IN / NOT_IN 运算符需要多值 placeholder */
function isMultiValue(op) {
  return op === 'IN' || op === 'NOT_IN';
}

// ---- 条件行操作 ----

/** 添加一行空白条件 */
function addCondition() {
  localConditions.value = [
    ...localConditions.value,
    { field: '', op: 'EQ', value: '' }
  ];
  emitUpdate();
}

/** 删除指定行 */
function removeCondition(idx) {
  localConditions.value = localConditions.value.filter((_, i) => i !== idx);
  emitUpdate();
}

// ---- emit 组装 ----

/**
 * 任何变更后统一调用：
 *   - 若 conditions 为空 → emit null
 *   - 否则 emit { logic, conditions }（单条件时 logic 固定 'AND'）
 */
function emitUpdate() {
  if (localConditions.value.length === 0) {
    emit('update:modelValue', null);
    return;
  }
  // 单条件时逻辑无意义，固定返回 AND
  const logic = localConditions.value.length === 1 ? 'AND' : localLogic.value;
  emit('update:modelValue', {
    logic,
    conditions: localConditions.value.map(c => ({ ...c }))
  });
}
</script>

<style scoped>
.condition-builder {
  width: 100%;
}

/* 逻辑关系行 */
.logic-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.logic-label {
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
}

/* 单条件行：窄面板下允许换行，字段+运算符一行，值输入框换行加宽独占一行 */
.condition-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

/* variables 为空提示 */
.empty-hint {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}
</style>
