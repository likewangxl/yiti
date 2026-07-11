<template>
  <!-- 奖励分配明细录入（分配人把该部门共同的「分配合计」分给部门下每一个人） -->
  <div class="reward-task">
    <div class="page-h">
      <el-button :icon="ArrowLeft" plain @click="emit('back')">返回</el-button>
      <h1 class="process-title">{{ group.taskName || group.dept || '无部门' }}</h1>
      <span class="desc">{{ group.taskTypeLabel || group.taskType }} · {{ group.dept || '' }}</span>
      <div class="proc-actions">
        <div class="default-setter">
          <span class="ds-label">给每个人分配</span>
          <el-input-number v-model="fillEach" :min="0" :step="1" :precision="2" size="small"
            controls-position="right" style="width: 140px" />
          <el-button size="small" @click="applyFillEach">应用到全部</el-button>
        </div>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="!canSubmit"
          @click="handleSubmit"
        >提交分配{{ pendingCount > 0 ? `（${pendingCount}）` : '' }}</el-button>
      </div>
      <div class="deadline-hint">
        <el-icon><Clock /></el-icon>
        截止：{{ formatDateTime(group.deadline) }}
      </div>
    </div>

    <!-- 分配合计 + 剩余提示 -->
    <div class="alloc-summary">
      <div class="summary-item">
        <span class="lbl">分配合计</span>
        <span class="val total">{{ formatNum(assignTotal) }}</span>
      </div>
      <div class="summary-item">
        <span class="lbl">已分配</span>
        <span class="val">{{ formatNum(allocated) }}</span>
      </div>
      <div class="summary-item">
        <span class="lbl">距离还剩</span>
        <span class="val" :class="{ negative: remaining < 0, done: isZero(remaining) }">{{ formatNum(remaining) }}</span>
      </div>
    </div>

    <el-table v-loading="loading" :data="items" border stripe style="width: 100%">
      <el-table-column label="被分配人工号" width="140" align="center">
        <template #default="{ row }">{{ row.beAssignedUserId }}</template>
      </el-table-column>
      <el-table-column prop="beAssignedUserName" label="被分配人姓名" min-width="120">
        <template #default="{ row }">{{ row.beAssignedUserName || '—' }}</template>
      </el-table-column>
      <el-table-column prop="deptName" label="部门" min-width="140">
        <template #default="{ row }">{{ row.deptName || '—' }}</template>
      </el-table-column>
      <el-table-column label="原始值" width="120" align="right">
        <template #default="{ row }">{{ formatNum(row.originalValue) }}</template>
      </el-table-column>
      <el-table-column label="兑现值" width="120" align="right">
        <template #default="{ row }">{{ formatNum(row.cashValue) }}</template>
      </el-table-column>
      <el-table-column label="分配值" min-width="180">
        <template #default="{ row }">
          <span v-if="row.submitted === 1" class="score-submitted">
            {{ formatNum(row.assignValue) }}
            <el-icon style="font-size:12px;color:#1e7e34"><Check /></el-icon>
          </span>
          <el-input-number
            v-else
            v-model="editValues[row.itemId]"
            :min="0" :step="1" :precision="2" size="small" controls-position="right"
            style="width: 150px"
          />
        </template>
      </el-table-column>
      <template #empty>该部门暂无待分配人员</template>
    </el-table>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Clock, Check } from '@element-plus/icons-vue'
import { listRewardPendingItems, submitRewardBatch } from '@/api/eval'

const props = defineProps({
  group: { type: Object, required: true }
})
const emit = defineEmits(['back'])

const loading = ref(false)
const items = ref([])
// 每行分配值草稿：itemId -> 分配值
const editValues = reactive({})
// 快捷填充「给每个人分配」的值
const fillEach = ref(0)
const submitting = ref(false)

// 该组分配合计（组内一致，取首条；汇总行也带 assignTotal 作兜底）
const assignTotal = computed(() => {
  const first = items.value.find(it => it.assignTotal != null)
  return Number(first?.assignTotal ?? props.group.assignTotal ?? 0)
})
// 未提交人数
const pendingCount = computed(() => items.value.filter(it => it.submitted !== 1).length)
// 已分配之和（仅未提交行按草稿计；已提交行按其 assignValue 计入，保证剩余逻辑对整组成立）
const allocated = computed(() =>
  items.value.reduce((s, it) => {
    if (it.submitted === 1) return s + Number(it.assignValue || 0)
    return s + Number(editValues[it.itemId] || 0)
  }, 0)
)
// 距离还剩 = 分配合计 - 已分配（负数=超额）
const remaining = computed(() => round2(assignTotal.value - allocated.value))
// 可提交：剩余为 0（浮点容差）且全部未提交行 > 0
const canSubmit = computed(() =>
  pendingCount.value > 0
  && isZero(remaining.value)
  && items.value.filter(it => it.submitted !== 1).every(it => Number(editValues[it.itemId]) > 0)
)

async function load() {
  loading.value = true
  items.value = []
  Object.keys(editValues).forEach(k => delete editValues[k])
  try {
    const rows = await listRewardPendingItems(props.group.batchId, props.group.dept || '')
    items.value = Array.isArray(rows) ? rows : []
    for (const it of items.value) {
      if (it.submitted !== 1) editValues[it.itemId] = 0
    }
  } catch (e) {
    ElMessage.error('加载待分配人员失败：' + (e?.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// 快捷填充：把所有未提交行的分配值设为「给每个人分配」的值
function applyFillEach() {
  const v = Number(fillEach.value || 0)
  for (const it of items.value) {
    if (it.submitted !== 1) editValues[it.itemId] = v
  }
}

async function handleSubmit() {
  const pending = items.value.filter(it => it.submitted !== 1)
  if (pending.length === 0) {
    ElMessage.warning('没有待分配的人员')
    return
  }
  for (const it of pending) {
    if (!(Number(editValues[it.itemId]) > 0)) {
      ElMessage.warning(`「${it.beAssignedUserName || it.beAssignedUserId}」分配值必须大于 0`)
      return
    }
  }
  if (!isZero(remaining.value)) {
    ElMessage.warning('分配值之和必须等于分配合计')
    return
  }
  const payload = pending.map(it => ({ itemId: it.itemId, assignValue: Number(editValues[it.itemId]) }))
  submitting.value = true
  try {
    await submitRewardBatch(props.group.batchId, props.group.dept || '', payload)
    for (const it of pending) {
      it.submitted = 1
      it.assignValue = Number(editValues[it.itemId])
    }
    ElMessage.success(`已提交 ${payload.length} 人分配`)
    emit('back')
  } catch (e) {
    ElMessage.error('提交失败：' + (e?.message || '未知错误'))
  } finally {
    submitting.value = false
  }
}

// ===================== 工具 =====================
// 保留两位小数，规避 0.1+0.2 之类的浮点误差
function round2(n) {
  return Math.round((Number(n) + Number.EPSILON) * 100) / 100
}
// 浮点近似为 0（分配值到分，容差 1e-6）
function isZero(n) {
  return Math.abs(Number(n)) < 1e-6
}
function formatNum(v) {
  if (v === null || v === undefined || v === '') return '—'
  const n = Number(v)
  if (isNaN(n)) return String(v)
  return Number.isInteger(n) ? String(n) : n.toFixed(2)
}
function formatDateTime(val) {
  if (!val) return '—'
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(val)) return val
  const d = new Date(val)
  if (isNaN(d.getTime())) return val
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

onMounted(load)
</script>

<style lang="scss" scoped>
$text-1: #1a1a2e;
$text-3: #a0aec0;
$bg-soft: #f7fafc;
$primary: #4361ee;

.reward-task {
  padding: 24px;
  background: #fff;
  min-height: 100%;

  .page-h {
    display: flex;
    align-items: center;
    margin-bottom: 16px;
    gap: 12px;

    h1 { font-size: 20px; font-weight: 600; color: $text-1; margin: 0; }
    .process-title { font-size: 18px; }
    .desc { font-size: 13px; color: $text-3; }
  }

  .proc-actions {
    margin-left: auto;
    display: flex;
    align-items: center;
    gap: 12px;

    .default-setter {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 4px 10px;
      border-radius: 6px;
      background: $bg-soft;

      .ds-label { font-size: 13px; color: $text-1; white-space: nowrap; }
    }
  }

  .deadline-hint {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 13px;
    padding: 4px 10px;
    border-radius: 6px;
    margin-left: 12px;
    color: #d46b08;
    background: #fff7e6;
  }

  .alloc-summary {
    display: flex;
    gap: 24px;
    margin-bottom: 16px;
    padding: 12px 16px;
    border-radius: 8px;
    background: $bg-soft;

    .summary-item {
      display: flex;
      flex-direction: column;
      gap: 4px;

      .lbl { font-size: 12px; color: $text-3; }
      .val { font-size: 20px; font-weight: 700; color: $text-1; }
      .val.total { color: $primary; }
      .val.negative { color: #cf1322; }
      .val.done { color: #1e7e34; }
    }
  }

  .score-submitted {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-weight: 600;
    color: #1e7e34;
  }
}
</style>
