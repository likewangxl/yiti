<template>
  <!-- 待处理任务（用户端） -->
<main v-bp-overflow-tooltip class="bp-crud eval-my-tasks-page" aria-labelledby="eval-my-tasks-page-title" :aria-busy="loading || processView.loading ? 'true' : 'false'">

    <!-- ===== 汇总列表视图 ===== -->
    <template v-if="!processView.active && !rewardView.active">
      <header class="page-h">
        <PageTitle id="eval-my-tasks-page-title"><span class="sub">按部门汇总待办，并逐人完成评价或奖励分配</span></PageTitle>
        <div class="actions action-group" role="group" aria-label="我的待办操作">
          <el-button :loading="loading" @click="loadGroups">刷新</el-button>
        </div>
      </header>

      <section class="card-section data-panel" aria-label="我的待处理任务汇总" aria-describedby="eval-my-tasks-state">
        <div class="toolbar">
          <div>
            <h2 id="eval-my-tasks-heading" class="section-title">我的待处理任务</h2>
            <p class="hint">评价任务和奖励分配任务按部门汇总，进入处理后可逐条或批量提交。</p>
          </div>
          <p id="eval-my-tasks-state" class="table-state" role="status" aria-live="polite">
            {{ loading ? '待处理任务加载中' : loadError || (groups.length ? `共 ${groups.length} 个待办分组` : '暂无待处理任务') }}
          </p>
        </div>
      <el-table v-loading="loading" :data="groups" border stripe empty-text="暂无待处理任务"
        aria-labelledby="eval-my-tasks-heading" aria-describedby="eval-my-tasks-state">
        <el-table-column label="任务类型" width="100" align="center">
          <template #default="{ row }">
            <span class="tag-type">{{ row.taskTypeLabel || row.taskType }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="taskName" label="任务名称" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.taskName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="dept" label="分组部门" min-width="180">
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
        <el-table-column label="操作" class-name="operation-cell" width="110" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="enterProcess(row)">处理</el-button>
          </template>
        </el-table-column>
        <template #empty>暂无待处理任务</template>
      </el-table>
        <div class="pager summary-foot" aria-label="待处理任务汇总说明"><span class="hint">列表按任务和部门汇总，提交后返回此处刷新人数。</span></div>
      </section>
    </template>

    <!-- ===== 奖励分配（明细分配）视图 ===== -->
    <RewardTask v-else-if="rewardView.active" :group="rewardView.group" @back="exitReward" />

    <!-- ===== 处理（明细打分）视图 ===== -->
    <template v-else>
      <header class="page-h">
        <el-button :icon="ArrowLeft" plain @click="exitProcess">返回</el-button>
        <h1 id="eval-my-tasks-page-title" class="process-title">{{ processView.group.taskName || processView.group.dept || '无部门' }}</h1>
        <span class="desc">{{ processView.group.taskTypeLabel || processView.group.taskType }} · {{ processView.group.dept || '' }}</span>
        <div class="proc-actions action-group" role="group" aria-label="评分处理操作">
          <!-- 默认级别 / 默认分数：仅覆盖"用户尚未手动调整过"的行，不动已手动改过的 -->
          <div class="default-setter">
            <span class="ds-label">默认级别</span>
            <el-select v-model="defaultGrade" aria-label="默认级别" size="small" class="default-grade" @change="applyDefaultGrade">
              <el-option v-for="opt in LEVEL_OPTIONS" :key="opt.score" :label="`${opt.label}（${opt.score}分）`" :value="opt.score" />
            </el-select>
            <span class="ds-label">默认分数</span>
            <el-input-number v-model="defaultNum" aria-label="默认分数" :min="10" :max="100" :step="1" size="small"
              controls-position="right" class="default-score" @change="applyDefaultNum" />
          </div>
          <el-button
            type="primary"
            :loading="submittingAll"
            :disabled="pendingCount === 0 || submittingAll"
            @click="handleSubmitAll"
          >全部提交{{ pendingCount > 0 ? `（${pendingCount}）` : '' }}</el-button>
        </div>
        <div class="deadline-hint">
          <el-icon><Clock /></el-icon>
          截止：{{ formatDateTime(processView.group.deadline) }}
        </div>
      </header>

      <section class="card-section data-panel" aria-label="待评价人员列表" aria-describedby="eval-score-table-state" :aria-busy="processView.loading ? 'true' : 'false'">
        <div class="toolbar">
          <div>
            <h2 id="eval-score-table-heading" class="section-title">待评价人员</h2>
            <p class="hint">数值评分范围 10–100；等级评分按系统等级口径提交。</p>
          </div>
          <p id="eval-score-table-state" class="table-state" role="status" aria-live="polite">
            {{ processView.loading ? '待评价人员加载中' : processView.items.length ? `待提交 ${pendingCount} 人` : '该部门暂无待评价人员' }}
          </p>
        </div>
      <el-table v-loading="processView.loading" :data="processView.items" border stripe empty-text="该部门暂无待评价人员"
        aria-labelledby="eval-score-table-heading" aria-describedby="eval-score-table-state">
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
              <el-icon class="submitted-icon"><Check /></el-icon>
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
        <el-table-column label="操作" class-name="operation-cell" width="110" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.submitted !== 1"
              type="primary" link
              :loading="submittingId === row.itemId"
              :disabled="submittingId === row.itemId || submittingAll"
              @click="handleSubmit(row)"
            >提交</el-button>
            <span v-else class="muted">已提交</span>
          </template>
        </el-table-column>
        <template #empty>该部门暂无待评价人员</template>
      </el-table>
      </section>
    </template>
  </main>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Clock, Check } from '@element-plus/icons-vue'
import { listPendingTasks, listPendingItems, submitPendingScore, submitPendingScoreBatch, listRewardPendingTasks } from '@/api/eval'
import RewardTask from './RewardTask.vue'

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
const loadError = ref('')

async function loadGroups() {
  loading.value = true
  loadError.value = ''
  try {
    // 汇总复用：并行拉取 评价(EVAL) + 奖励分配(REWARD) 两个待处理接口，客户端合并为一张列表。
    // 任一接口失败不阻断另一个（reward 端点未授权/未上线时仍能看到评价任务）。
    const [evalRes, rewardRes] = await Promise.allSettled([listPendingTasks(), listRewardPendingTasks()])
    const evalGroups = evalRes.status === 'fulfilled'
      ? (Array.isArray(evalRes.value) ? evalRes.value : (evalRes.value?.records || []))
      : []
    const rewardGroups = rewardRes.status === 'fulfilled'
      ? (Array.isArray(rewardRes.value) ? rewardRes.value : (rewardRes.value?.records || []))
      : []
    groups.value = [...evalGroups, ...rewardGroups]
    if (evalRes.status === 'rejected' && rewardRes.status === 'rejected') {
      loadError.value = '待处理任务加载失败，请刷新重试'
      ElMessage.error('加载待处理任务失败，请刷新重试')
    }
  } catch (e) {
    ElMessage.error('加载待处理任务失败：' + (e?.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// 点击「处理」按 taskType 分流：REWARD → 奖励分配明细；其余 → 现有打分明细
function enterProcess(row) {
  if (row.taskType === 'REWARD') {
    rewardView.group = row
    rewardView.active = true
    return
  }
  enterScoreProcess(row)
}

// ===================== 处理视图 =====================
const processView = reactive({ active: false, group: null, loading: false, items: [] })
// 奖励分配处理视图（REWARD 走独立子组件 RewardTask）
const rewardView = reactive({ active: false, group: null })

function exitReward() {
  rewardView.active = false
  rewardView.group = null
  loadGroups() // 返回刷新汇总，人数随提交同步
}
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

async function enterScoreProcess(group) {
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
  if (submittingId.value === row.itemId || submittingAll.value || row.submitted === 1) return
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
  if (submittingAll.value) return
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
.eval-my-tasks-page {
  .process-title { font-size: 18px; }

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

      .ds-label {
        font-size: 14px;
        color: var(--color-text);
        white-space: nowrap;
      }
    }
  }

  .default-grade { width: 150px; }
  .default-score { width: 120px; }

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

  .tag-type {
    display: inline-flex;
    padding: 2px var(--space-2);
    border-radius: var(--radius-control);
    font-size: 12px;
    background: var(--color-brand-100);
    color: var(--color-brand-700);
    border: 1px solid var(--color-brand-500);
  }

  .pending-count {
    font-weight: 600;
    color: var(--color-brand-700);
    font-variant-numeric: tabular-nums;
  }

  .score-submitted {
    display: inline-flex;
    align-items: center;
    gap: var(--space-1);
    font-weight: 600;
    color: var(--color-success-fg);
  }

  .submitted-icon { font-size: 12px; color: var(--color-success-fg); }
  .muted { color: var(--color-text-muted); }
  .summary-foot { justify-content: flex-start; }
}
</style>
