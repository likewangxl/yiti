<!--
  指标拾取 Modal —— 动态指标查询用
  左侧：指标库树（可搜索）；右侧：已选清单
  数据源：getMetricsTree() / listMetrics()（来自 src/api/metrics.js）
-->
<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="$emit('update:visible', $event)"
    title="选择指标"
    width="820px"
    :close-on-click-modal="false"
  >
    <div class="picker">
      <div class="col">
        <h4>指标库</h4>
        <el-input v-model="kw" placeholder="🔍 搜索指标" clearable size="default" />
        <div class="tree-wrap">
          <el-tree
            ref="treeRef"
            :data="filteredTree"
            node-key="code"
            show-checkbox
            :default-checked-keys="picked"
            :props="treeProps"
            :filter-node-method="filterNode"
            check-strictly
            @check="onCheck"
          >
            <template #default="{ data }">
              <span class="node">
                <span>{{ data.label }}</span>
                <span v-if="data.code && !data.children" class="mono muted">{{ data.code }}</span>
              </span>
            </template>
          </el-tree>
        </div>
      </div>

      <div class="col">
        <h4>已选指标 ({{ picked.length }})</h4>
        <div class="picked">
          <div v-if="!picked.length" class="empty">尚未选择任何指标</div>
          <div v-for="(c, i) in picked" :key="c" class="pill" :class="{ alt: i % 2 }">
            <span class="mono muted">{{ c }}</span>
            <span class="lbl">{{ codeLabel(c) }}</span>
            <a class="op danger" @click="remove(c)">移除</a>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" @click="confirm">
        确定（已选 {{ picked.length }} 项）
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue';
import { getMetricsTree } from '@/api/metrics';

const props = defineProps({
  visible: Boolean,
  modelValue: { type: Array, default: () => [] },  // 已选 metric code 数组
  dim: { type: String, default: '' }               // 当前查询维度 EMP/ORG/CUST，按它过滤指标
});
const emit = defineEmits(['update:visible', 'update:modelValue', 'confirm']);

const tree = ref([]);
const kw = ref('');
const picked = ref([...props.modelValue]);
const treeRef = ref(null);

const treeProps = { label: 'label', children: 'children' };

watch(() => props.visible, (v) => {
  if (v) {
    picked.value = [...props.modelValue];
    if (!tree.value.length) loadTree();
  }
});
watch(kw, (v) => treeRef.value?.filter(v));

function filterNode(value, data) {
  if (!value) return true;
  return (data.label || '').includes(value) || (data.code || '').toUpperCase().includes(value.toUpperCase());
}

async function loadTree() {
  try {
    const data = await getMetricsTree();
    tree.value = normalize(data);
  } catch (e) {
    tree.value = [];
  }
}

// 把 mock 的 {id, label, children} 规整成 tree 需要的结构 —— 叶子节点用 code 作 key
function normalize(nodes) {
  return (nodes || []).map(n => {
    if (n.children?.length) {
      return { code: n.id, label: n.label, children: normalize(n.children) };
    }
    // 叶子：以 id 为 metric code
    return { code: n.id, label: n.label };
  });
}

// 按维度过滤：顶层节点是 DIM_EMP / DIM_ORG / DIM_CUST（来自 buildMetricTree），
// dim 指定时只展示该维度分支下的指标（直接展开其 children，省掉冗余的"员工指标"根）。
const filteredTree = computed(() => {
  if (!props.dim) return tree.value;
  const hasDimRoots = tree.value.some(n => typeof n.code === 'string' && n.code.startsWith('DIM_'));
  if (!hasDimRoots) return tree.value;             // 结构非预期则不过滤，避免误伤
  const node = tree.value.find(n => n.code === `DIM_${props.dim}`);
  return node ? (node.children || []) : [];        // 该维度无指标 → 空
});

const flatLeaves = computed(() => {
  const out = [];
  const walk = (arr) => arr.forEach(n => n.children ? walk(n.children) : out.push(n));
  walk(tree.value);
  return out;
});

function codeLabel(code) {
  return flatLeaves.value.find(l => l.code === code)?.label || code;
}

function onCheck(_node, info) {
  // info.checkedKeys 包含全部勾选的 key（包括分组），过滤只留叶子
  picked.value = info.checkedKeys.filter(k => flatLeaves.value.some(l => l.code === k));
}

function remove(code) {
  picked.value = picked.value.filter(c => c !== code);
  treeRef.value?.setChecked(code, false);
}

function confirm() {
  emit('update:modelValue', picked.value);
  emit('confirm', picked.value);
  emit('update:visible', false);
}

onMounted(() => { if (props.visible) loadTree(); });
</script>

<style lang="scss" scoped>
.picker { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; min-height: 380px; }
.col h4 { margin: 0 0 8px; font-size: 13px; color: $text-2; font-weight: 600; }
.tree-wrap { margin-top: 8px; max-height: 360px; overflow: auto; border: 1px solid $border-1; border-radius: 4px; padding: 6px; }
.node { display: flex; align-items: center; gap: 6px; .mono.muted { font-size: 11px; color: $text-4; margin-left: auto; } }
.picked {
  border: 1px solid $border-1; border-radius: 4px; max-height: 396px; overflow: auto; padding: 8px;
  .empty { color: $text-4; text-align: center; font-size: 13px; padding: 40px 0; }
  .pill {
    display: flex; align-items: center; padding: 6px 10px; gap: 10px; border-radius: 4px;
    .mono.muted { color: $text-4; font-size: 11px; min-width: 50px; }
    .lbl { flex: 1; font-size: 13px; color: $text-1; }
    .op.danger { color: $danger; cursor: pointer; font-size: 12px; }
    &.alt { background: $bg-soft; }
  }
}
.mono { font-family: Menlo, Consolas, monospace; }
</style>
