<template>
  <!-- 奖励分配明细录入（分配人把该部门共同的「分配合计」分给部门下每一个人） -->
  <section class="bp-crud reward-task" aria-labelledby="reward-task-page-title" :aria-busy="loading ? 'true' : 'false'">
    <header class="page-h">
      <el-button :icon="ArrowLeft" plain aria-label="返回待处理任务" @click="emit('back')">返回</el-button>
      <h1 id="reward-task-page-title" class="process-title">{{ group.taskName || group.dept || '无部门' }}</h1>
      <span class="sub">{{ group.taskTypeLabel || group.taskType }} · {{ group.dept || '' }}</span>
      <div class="proc-actions action-group" role="group" aria-label="奖励分配操作">
        <div class="default-setter">
          <span class="ds-label">给每个人分配</span>
          <el-input-number v-model="fillEach" :min="0" :step="1" :precision="2" size="small"
            controls-position="right" class="fill-input" aria-label="每人默认分配金额" />
          <el-button size="small" @click="applyFillEach">应用到全部</el-button>
        </div>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="!canSubmit || submitting"
          @click="handleSubmit"
        >提交分配{{ pendingCount > 0 ? `（${pendingCount}）` : '' }}</el-button>
      </div>
      <div class="deadline-hint">
        <el-icon><Clock /></el-icon>
        <span>截止：{{ formatDateTime(group.deadline) }}</span>
      </div>
    </header>

    <!-- 分配合计 + 剩余提示 -->
    <section class="card-section allocation-summary" aria-label="奖励分配汇总">
      <h2 class="section-title">奖励分配汇总</h2>
      <div class="summary-grid">
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
      <p class="hint" role="status" aria-live="polite">{{ isZero(remaining) ? '分配金额已平衡，可以提交。' : '分配值之和必须等于分配合计。' }}</p>
    </section>

    <section class="card-section data-panel" aria-label="奖励分配明细" aria-describedby="reward-task-table-state">
      <div class="toolbar">
        <div>
          <h2 id="reward-task-table-heading" class="section-title">奖励分配明细</h2>
          <p class="hint">每名人员的分配值允许为 0，提交前必须完成整组金额平衡。</p>
        </div>
        <p id="reward-task-table-state" class="table-state" role="status" aria-live="polite">
          {{ loading ? '奖励分配明细加载中' : loadError || (items.length ? `待提交 ${pendingCount} 人` : '该部门暂无待分配人员') }}
        </p>
      </div>
    <el-table v-loading="loading" :data="items" border stripe empty-text="该部门暂无待分配人员"
      aria-labelledby="reward-task-table-heading" aria-describedby="reward-task-table-state">
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
        <!-- 已提交行显示落库值；未提交行实时预览 = 原始值 + 当前草稿分配值 -->
        <template #default="{ row }">
          <span v-if="row.submitted === 1">{{ formatNum(row.cashValue) }}</span>
          <span v-else class="cash-preview">{{ formatNum(previewCash(row)) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="分配值" min-width="180">
        <template #default="{ row }">
          <span v-if="row.submitted === 1" class="score-submitted">
            {{ formatNum(row.assignValue) }}
            <el-icon class="submitted-icon"><Check /></el-icon>
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
    <div class="pager summary-foot" aria-label="奖励分配明细说明"><span class="hint">提交成功后返回待处理任务列表，人数会同步刷新。</span></div>
    </section>
  </section>
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
const loadError = ref('')
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
// 可提交：剩余为 0（浮点容差）且全部未提交行已填且 >= 0（分配值允许为 0）
const canSubmit = computed(() =>
  pendingCount.value > 0
  && isZero(remaining.value)
  && items.value.filter(it => it.submitted !== 1).every(it => {
    const v = editValues[it.itemId]
    return v != null && !isNaN(Number(v)) && Number(v) >= 0
  })
)
// 未提交行兑现值实时预览 = 原始值 + 当前草稿分配值
function previewCash(row) {
  return round2(Number(row.originalValue || 0) + Number(editValues[row.itemId] || 0))
}

async function load() {
  loading.value = true
  loadError.value = ''
  items.value = []
  Object.keys(editValues).forEach(k => delete editValues[k])
  try {
    const rows = await listRewardPendingItems(props.group.batchId, props.group.dept || '')
    items.value = Array.isArray(rows) ? rows : []
    for (const it of items.value) {
      if (it.submitted !== 1) editValues[it.itemId] = 0
    }
  } catch (e) {
    loadError.value = '奖励分配明细加载失败，请重试'
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
  if (submitting.value) return
  const pending = items.value.filter(it => it.submitted !== 1)
  if (pending.length === 0) {
    ElMessage.warning('没有待分配的人员')
    return
  }
  for (const it of pending) {
    const v = editValues[it.itemId]
    if (v == null || isNaN(Number(v)) || Number(v) < 0) {
      ElMessage.warning(`「${it.beAssignedUserName || it.beAssignedUserId}」分配值不能为空且不能为负数`)
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
      // 与后端落库规则保持一致：兑现值 = 原始值 + 分配值
      it.cashValue = round2(Number(it.originalValue || 0) + it.assignValue)
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
.reward-task {
  .proc-actions {
    margin-left: auto;
    display: flex;
    align-items: center;
    gap: var(--space-3);

    .default-setter {
      display: flex;
      align-items: center;
      gap: var(--space-2);
      padding: var(--space-1) var(--space-3);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-control);
      background: var(--color-surface-soft);

      .ds-label { font-size: 14px; color: var(--color-text); white-space: nowrap; }
    }
  }

  .fill-input { width: 140px; }

  .deadline-hint {
    display: flex;
    align-items: center;
    gap: var(--space-1);
    font-size: 12px;
    padding: var(--space-1) var(--space-3);
    border-radius: var(--radius-control);
    margin-left: var(--space-3);
    color: var(--color-warning-fg);
    background: var(--color-warning-bg);
    border: 1px solid var(--color-warning-fg);
  }

  .allocation-summary {
    .summary-grid {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: var(--space-4);
    }

    .summary-item {
      display: flex;
      flex-direction: column;
      gap: var(--space-1);

      .lbl { font-size: 12px; color: var(--color-text-muted); }
      .val { font-size: 22px; line-height: 32px; font-weight: 600; color: var(--color-text-strong); font-variant-numeric: tabular-nums; }
      .val.total { color: var(--color-brand-700); }
      .val.negative { color: var(--color-danger-fg); }
      .val.done { color: var(--color-success-fg); }
    }
  }

  .score-submitted {
    display: inline-flex;
    align-items: center;
    gap: var(--space-1);
    font-weight: 600;
    color: var(--color-success-fg);
  }
  .submitted-icon { font-size: 12px; color: var(--color-success-fg); }
  .cash-preview { color: var(--color-text); font-variant-numeric: tabular-nums; }
}
</style>
