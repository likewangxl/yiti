<template>
  <!-- 待处理任务（用户端） -->
  <div class="pending-tasks-page">

    <!-- ===== 汇总列表视图 ===== -->
    <template v-if="!processView.active">
      <div class="page-h">
        <h1>待处理任务</h1>
        <span class="desc">按部门汇总 · 逐人评价打分</span>
        <div class="actions">
          <el-button :loading="loading" @click="loadGroups">刷新</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="groups" border stripe style="width: 100%">
        <el-table-column label="任务类型" width="100" align="center">
          <template #default="{ row }">
            <span class="tag-type">{{ row.taskTypeLabel || row.taskType }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="taskName" label="任务名称" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.taskName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="dept" label="部门" min-width="180">
          <template #default="{ row }">{{ row.dept || '—' }}</template>
        </el-table-column>
        <el-table-column label="待评价人数" width="120" align="center">
          <template #default="{ row }">
            <span class="pending-count">{{ row.pendingCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="评价截止时间" width="180" align="center">
          <template #default="{ row }">{{ formatDateTime(row.deadline) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="enterProcess(row)">处理</el-button>
          </template>
        </el-table-column>
        <template #empty>暂无待处理任务</template>
      </el-table>
    </template>

    <!-- ===== 处理（明细打分）视图 ===== -->
    <template v-else>
      <div class="page-h">
        <el-button :icon="ArrowLeft" plain @click="exitProcess">返回</el-button>
        <h1 class="process-title">{{ processView.group.taskName || processView.group.dept || '无部门' }}</h1>
        <span class="desc">{{ processView.group.taskTypeLabel || processView.group.taskType }} · {{ processView.group.dept || '' }}</span>
        <div class="proc-actions">
          <!-- 默认级别 / 默认分数：仅覆盖"用户尚未手动调整过"的行，不动已手动改过的 -->
          <div class="default-setter">
            <span class="ds-label">默认级别</span>
            <el-select v-model="defaultGrade" size="small" style="width: 150px" @change="applyDefaultGrade">
              <el-option v-for="opt in LEVEL_OPTIONS" :key="opt.score" :label="`${opt.label}（${opt.score}分）`" :value="opt.score" />
            </el-select>
            <span class="ds-label">默认分数</span>
            <el-input-number v-model="defaultNum" :min="10" :max="100" :step="1" size="small"
              controls-position="right" style="width: 120px" @change="applyDefaultNum" />
          </div>
          <el-button
            type="primary"
            :loading="submittingAll"
            :disabled="pendingCount === 0"
            @click="handleSubmitAll"
          >全部提交{{ pendingCount > 0 ? `（${pendingCount}）` : '' }}</el-button>
        </div>
        <div class="deadline-hint">
          <el-icon><Clock /></el-icon>
          截止：{{ formatDateTime(processView.group.deadline) }}
        </div>
      </div>

      <el-table v-loading="processView.loading" :data="processView.items" border stripe style="width: 100%">
        <el-table-column label="被打分人编号" width="130" align="center">
          <template #default="{ row }">{{ row.beEvalUserUsername || row.beEvalUserId }}</template>
        </el-table-column>
        <el-table-column prop="beEvalUserName" label="被打分人姓名" min-width="120">
          <template #default="{ row }">{{ row.beEvalUserName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="beEvalDept" label="部门" min-width="140">
          <template #default="{ row }">{{ row.beEvalDept || '—' }}</template>
        </el-table-column>
        <el-table-column label="评级 / 分数" min-width="260">
          <template #default="{ row }">
            <!-- 已提交：只读展示 -->
            <span v-if="row.submitted === 1" class="score-submitted">
              {{ displayScore(row) }}
              <el-icon style="font-size:12px;color:#1e7e34"><Check /></el-icon>
            </span>
            <!-- 数值打分 -->
            <el-input-number
              v-else-if="row.scoreType === 'NUM'"
              v-model="editScores[row.itemId]"
              :min="10" :max="100" :step="1" size="small" controls-position="right"
              style="width: 140px"
              @change="markTouched(row.itemId)"
            />
            <!-- 等级打分 -->
            <el-select
              v-else
              v-model="editScores[row.itemId]"
              placeholder="选择等级" size="small" style="width: 200px"
              @change="markTouched(row.itemId)"
            >
              <el-option
                v-for="opt in LEVEL_OPTIONS"
                :key="opt.score"
                :label="`${opt.label}（${opt.score}分）`"
                :value="opt.score"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.submitted !== 1"
              type="primary" link
              :loading="submittingId === row.itemId"
              @click="handleSubmit(row)"
            >提交</el-button>
            <span v-else class="muted">已提交</span>
          </template>
        </el-table-column>
        <template #empty>该部门暂无待评价人员</template>
      </el-table>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Clock, Check } from '@element-plus/icons-vue'
import { listPendingTasks, listPendingItems, submitPendingScore, submitPendingScoreBatch } from '@/api/eval'

// 等级打分选项：等级名 -> 对应分值（与系统口径一致）
const LEVEL_OPTIONS = [
  { label: '非常满意', score: 100 },
  { label: '比较满意', score: 95 },
  { label: '满意', score: 85 },
  { label: '一般', score: 75 },
  { label: '不满意', score: 59 },
]
// 等级打分默认值："比较满意"
const GRADE_DEFAULT_SCORE = 95
// 数值打分默认值
const NUM_DEFAULT_SCORE = 90

// ===================== 汇总列表 =====================
const loading = ref(false)
const groups = ref([])

async function loadGroups() {
  loading.value = true
  try {
    const r = await listPendingTasks()
    groups.value = Array.isArray(r) ? r : (r?.records || [])
  } catch (e) {
    ElMessage.error('加载待处理任务失败：' + (e?.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// ===================== 处理视图 =====================
const processView = reactive({ active: false, group: null, loading: false, items: [] })
// 每行打分草稿：itemId -> 分数
const editScores = reactive({})
// 用户手动调整过的行：itemId -> true（默认级别/分数只覆盖未手动调整过的行）
const touched = reactive({})
// 顶部"默认级别 / 默认分数"输入：等级默认"比较满意"、数值默认 90
const defaultGrade = ref(GRADE_DEFAULT_SCORE)
const defaultNum = ref(NUM_DEFAULT_SCORE)
const submittingId = ref(null)
// 批量提交中
const submittingAll = ref(false)
// 当前部门未提交人数（用于"全部提交"按钮禁用与计数）
const pendingCount = computed(() => processView.items.filter(it => it.submitted !== 1).length)

async function enterProcess(group) {
  processView.group = group
  processView.active = true
  processView.items = []
  processView.loading = true
  Object.keys(editScores).forEach(k => delete editScores[k])
  // 进入新部门：清空"已手动调整"标记，默认级别/分数复位到基础默认值
  Object.keys(touched).forEach(k => delete touched[k])
  defaultGrade.value = GRADE_DEFAULT_SCORE
  defaultNum.value = NUM_DEFAULT_SCORE
  try {
    const items = await listPendingItems(group.batchId, group.dept || '')
    processView.items = Array.isArray(items) ? items : []
    // 初始化草稿：数值默认 NUM_DEFAULT_SCORE，等级默认"比较满意"(GRADE_DEFAULT_SCORE)
    for (const it of processView.items) {
      if (it.submitted !== 1) {
        editScores[it.itemId] = it.scoreType === 'NUM' ? NUM_DEFAULT_SCORE : GRADE_DEFAULT_SCORE
      }
    }
  } catch (e) {
    ElMessage.error('加载待评价人员失败：' + (e?.message || '未知错误'))
  } finally {
    processView.loading = false
  }
}

function exitProcess() {
  processView.active = false
  processView.group = null
  processView.items = []
  // 返回时刷新汇总，人数随提交同步
  loadGroups()
}

// 标记某行已被用户手动调整（之后默认级别/分数不再覆盖它）
function markTouched(itemId) {
  touched[itemId] = true
}

// 默认级别变更：覆盖所有"未提交且用户未手动调整过"的等级打分行
function applyDefaultGrade(val) {
  for (const it of processView.items) {
    if (it.submitted !== 1 && it.scoreType === 'GRADE' && !touched[it.itemId]) {
      editScores[it.itemId] = val
    }
  }
}

// 默认分数变更：覆盖所有"未提交且用户未手动调整过"的数值打分行
function applyDefaultNum(val) {
  for (const it of processView.items) {
    if (it.submitted !== 1 && it.scoreType === 'NUM' && !touched[it.itemId]) {
      editScores[it.itemId] = val
    }
  }
}

async function handleSubmit(row) {
  const score = editScores[row.itemId]
  if (score === null || score === undefined || score === '') {
    ElMessage.warning(row.scoreType === 'NUM' ? '请填写分数' : '请选择评价等级')
    return
  }
  submittingId.value = row.itemId
  try {
    await submitPendingScore(row.itemId, score)
    // 本地置为已提交，避免重复
    row.submitted = 1
    row.score = score
    ElMessage.success('评价提交成功')
  } catch (e) {
    ElMessage.error('提交失败：' + (e?.message || '未知错误'))
  } finally {
    submittingId.value = null
  }
}

// 一键提交本部门全部未提交人员：先校验每条都已录分，再整批提交（后端 all-or-none）
async function handleSubmitAll() {
  const pending = processView.items.filter(it => it.submitted !== 1)
  if (pending.length === 0) {
    ElMessage.warning('没有待提交的打分')
    return
  }
  // 逐条校验是否已录分
  const payload = []
  for (const it of pending) {
    const score = editScores[it.itemId]
    if (score === null || score === undefined || score === '') {
      ElMessage.warning(`「${it.beEvalUserName || it.beEvalUserUsername || it.beEvalUserId}」${it.scoreType === 'NUM' ? '请填写分数' : '请选择评价等级'}`)
      return
    }
    payload.push({ itemId: it.itemId, score })
  }
  submittingAll.value = true
  try {
    await submitPendingScoreBatch(payload)
    // 整批成功后本地置为已提交
    for (const it of pending) {
      it.submitted = 1
      it.score = editScores[it.itemId]
    }
    ElMessage.success(`已提交 ${payload.length} 人评价`)
  } catch (e) {
    ElMessage.error('批量提交失败：' + (e?.message || '未知错误'))
  } finally {
    submittingAll.value = false
  }
}

// ===================== 工具 =====================
function displayScore(row) {
  if (row.scoreType === 'NUM') return `${row.score} 分`
  const hit = LEVEL_OPTIONS.find(o => o.score === row.score)
  return hit ? `${hit.label}（${row.score}分）` : `${row.score} 分`
}

function formatDateTime(val) {
  if (!val) return '—'
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(val)) return val
  const d = new Date(val)
  if (isNaN(d.getTime())) return val
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

onMounted(loadGroups)
</script>

<style lang="scss" scoped>
$text-1: #1a1a2e;
$text-3: #a0aec0;
$border-1: #e2e8f0;
$bg-soft: #f7fafc;
$primary: #4361ee;

.pending-tasks-page {
  padding: 24px;
  background: #fff;
  min-height: 100%;

  .page-h {
    display: flex;
    align-items: center;
    margin-bottom: 20px;
    gap: 12px;

    h1 { font-size: 20px; font-weight: 600; color: $text-1; margin: 0; }
    .process-title { font-size: 18px; }
    .desc { font-size: 13px; color: $text-3; }
    .actions { margin-left: auto; }
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

      .ds-label {
        font-size: 13px;
        color: $text-1;
        white-space: nowrap;
      }
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

  .tag-type {
    display: inline-block;
    padding: 2px 10px;
    border-radius: 12px;
    font-size: 12px;
    background: #eef2ff;
    color: $primary;
    border: 1px solid #c7d2fe;
  }

  .pending-count {
    font-weight: 600;
    color: $primary;
  }

  .score-submitted {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-weight: 600;
    color: #1e7e34;
  }

  .muted { color: $text-3; }
}
</style>
