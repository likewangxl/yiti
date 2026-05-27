<template>
  <!-- 我的待评价页面（用户端） -->
  <div class="my-tasks-page">

    <!-- ===== 任务列表视图 ===== -->
    <template v-if="!scoringView.active">
      <!-- 页头 -->
      <div class="page-h">
        <h1>我的评价</h1>
        <span class="desc">查看待评价任务 · 提交打分</span>
        <div class="actions"></div>
      </div>

      <!-- 任务列表表格 -->
      <el-table
        v-loading="tableLoading"
        :data="tableData"
        border
        stripe
        style="width: 100%"
      >
        <el-table-column prop="taskName" label="任务名称" min-width="180" />
        <el-table-column prop="endTime" label="截止时间" width="170" align="center">
          <template #default="{ row }">
            {{ formatDateTime(row.endTime) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <span :class="row.status === 0 ? 'tag-success' : 'tag-info'">
              {{ row.status === 0 ? '进行中' : '已结束' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              :disabled="row.status !== 0"
              @click="enterScoringView(row)"
            >
              去打分
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pager.pageNo"
          v-model:page-size="pager.pageSize"
          :total="pager.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </template>

    <!-- ===== 打分视图 ===== -->
    <template v-else>
      <!-- 打分页头 -->
      <div class="page-h">
        <el-button
          :icon="ArrowLeft"
          plain
          @click="exitScoringView"
        >返回</el-button>
        <h1 class="scoring-title">{{ scoringView.task.taskName }}</h1>
        <div class="deadline-hint" :class="deadlineClass">
          <el-icon><Clock /></el-icon>
          截止：{{ formatDateTime(scoringView.task.endTime) }}
          <span v-if="deadlineCountdown" class="countdown">（{{ deadlineCountdown }}）</span>
        </div>
        <div class="actions"></div>
      </div>

      <!-- 被评价人列表 -->
      <el-table
        v-loading="scoringView.loading"
        :data="scoringView.targets"
        border
        stripe
        style="width: 100%"
      >
        <el-table-column prop="targetId" label="记录ID" width="80" align="center" />
        <el-table-column prop="beEvalUserId" label="被评价人ID" width="110" align="center" />
        <el-table-column prop="beEvalUserName" label="被评价人" width="130" />
        <el-table-column label="最终得分" width="110" align="center">
          <template #default="{ row }">
            <span v-if="row.finalScore !== null && row.finalScore !== undefined" class="score-value">
              {{ row.finalScore }}
            </span>
            <span v-else class="score-empty">—</span>
          </template>
        </el-table-column>
        <el-table-column label="我的评分" width="110" align="center">
          <template #default="{ row }">
            <span v-if="getMyScore(row) !== null" class="score-submitted">
              {{ getMyScore(row) }} <el-icon style="font-size:11px;color:#1e7e34"><Check /></el-icon>
            </span>
            <span v-else class="score-empty">未打分</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              :disabled="getMyScore(row) !== null || scoringView.task.status !== 0"
              @click="openScoreDialog(row)"
            >
              {{ getMyScore(row) !== null ? '已打分' : '打分' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <!-- ===== 打分弹窗 ===== -->
    <el-dialog
      v-model="scoreDialog.visible"
      :title="`为 ${scoreDialog.targetName} 打分`"
      width="400px"
      :close-on-click-modal="false"
      @closed="resetScoreDialog"
    >
      <el-form
        ref="scoreFormRef"
        :model="scoreForm"
        :rules="scoreRules"
        label-width="80px"
      >
        <el-form-item label="评分" prop="score">
          <div class="score-slider-wrap">
            <el-slider
              v-model="scoreForm.score"
              :min="10"
              :max="100"
              :step="1"
              show-input
              :show-input-controls="true"
              style="flex: 1"
            />
          </div>
          <div class="score-hint">
            <span :class="scoreHintClass">{{ scoreHintText }}</span>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="scoreDialog.visible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="scoreDialog.submitting"
          @click="handleSubmitScore"
        >提交评分</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Clock, Check } from '@element-plus/icons-vue'
import { listMyTasks, listMyTaskTargets, submitScore } from '@/api/eval'

// ===================== 任务列表 =====================

/** 分页参数 */
const pager = reactive({
  pageNo: 1,
  pageSize: 20,
  total: 0
})

const tableLoading = ref(false)
const tableData = ref([])

/** 加载我的任务列表 */
async function loadList() {
  tableLoading.value = true
  try {
    const res = await listMyTasks({
      page: pager.pageNo,
      pageSize: pager.pageSize
    })
    tableData.value = res.records || []
    pager.total = res.total || 0
  } catch (e) {
    ElMessage.error('加载任务列表失败：' + (e?.message || '未知错误'))
  } finally {
    tableLoading.value = false
  }
}

// ===================== 日期格式化工具 =====================

/**
 * 格式化日期时间显示
 * 兼容 yyyy-MM-dd HH:mm:ss 及 ISO 格式
 */
function formatDateTime(val) {
  if (!val) return '—'
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(val)) return val
  const d = new Date(val)
  if (isNaN(d.getTime())) return val
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

// ===================== 打分视图 =====================

/** 打分视图状态 */
const scoringView = reactive({
  active: false,
  task: null,       // 当前选中的任务对象
  loading: false,
  targets: []       // 被评价人列表 EvalTaskTarget[]
})

/**
 * 每个 target 行打完分后记录本地缓存分数
 * key: targetId, value: score
 * 后端刷新前先用本地缓存展示已提交状态
 */
const localScores = ref({})

/** 获取某行的已提交分数（优先本地缓存，再看服务端 myScore 字段） */
function getMyScore(row) {
  if (localScores.value[row.targetId] !== undefined) {
    return localScores.value[row.targetId]
  }
  // 服务端可能返回 myScore 字段表示当前用户已提交的分数
  if (row.myScore !== null && row.myScore !== undefined) {
    return row.myScore
  }
  return null
}

/** 进入打分视图 */
async function enterScoringView(task) {
  scoringView.task = task
  scoringView.active = true
  scoringView.targets = []
  localScores.value = {}
  scoringView.loading = true
  try {
    const targets = await listMyTaskTargets(task.taskId)
    scoringView.targets = Array.isArray(targets) ? targets : []
  } catch (e) {
    ElMessage.error('加载被评价人列表失败：' + (e?.message || '未知错误'))
  } finally {
    scoringView.loading = false
  }
  // 启动截止时间倒计时
  startCountdown(task.endTime)
}

/** 退出打分视图，返回任务列表 */
function exitScoringView() {
  scoringView.active = false
  scoringView.task = null
  scoringView.targets = []
  localScores.value = {}
  stopCountdown()
}

// ===================== 截止时间倒计时 =====================

const deadlineCountdown = ref('')
let countdownTimer = null

/** 计算剩余时间文本 */
function calcCountdown(endTimeStr) {
  if (!endTimeStr) return ''
  const end = new Date(endTimeStr.replace(' ', 'T'))
  const now = new Date()
  const diff = end - now
  if (diff <= 0) return '已截止'
  const days = Math.floor(diff / 86400000)
  const hours = Math.floor((diff % 86400000) / 3600000)
  const minutes = Math.floor((diff % 3600000) / 60000)
  if (days > 0) return `剩余 ${days} 天 ${hours} 小时`
  if (hours > 0) return `剩余 ${hours} 小时 ${minutes} 分`
  return `剩余 ${minutes} 分钟`
}

function startCountdown(endTimeStr) {
  stopCountdown()
  deadlineCountdown.value = calcCountdown(endTimeStr)
  countdownTimer = setInterval(() => {
    deadlineCountdown.value = calcCountdown(endTimeStr)
  }, 60000)
}

function stopCountdown() {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
  deadlineCountdown.value = ''
}

/** 截止时间样式：临近24小时变黄，已截止变红 */
const deadlineClass = computed(() => {
  if (!scoringView.task?.endTime) return ''
  const end = new Date(scoringView.task.endTime.replace(' ', 'T'))
  const diff = end - new Date()
  if (diff <= 0) return 'deadline-danger'
  if (diff < 86400000) return 'deadline-warning'
  return 'deadline-normal'
})

// ===================== 打分弹窗 =====================

const scoreDialog = reactive({
  visible: false,
  submitting: false,
  targetId: null,
  targetName: '',
  taskId: null
})

const scoreFormRef = ref(null)

const scoreForm = reactive({
  score: 80
})

const scoreRules = {
  score: [
    { required: true, message: '请设置评分', trigger: 'change' },
    {
      validator: (rule, value, callback) => {
        if (value < 10 || value > 100) {
          callback(new Error('评分范围 10～100'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ]
}

/** 评分等级提示文字 */
const scoreHintText = computed(() => {
  const s = scoreForm.score
  if (s >= 90) return '优秀'
  if (s >= 75) return '良好'
  if (s >= 60) return '合格'
  return '不合格'
})

/** 评分等级提示样式 */
const scoreHintClass = computed(() => {
  const s = scoreForm.score
  if (s >= 90) return 'hint-success'
  if (s >= 75) return 'hint-primary'
  if (s >= 60) return 'hint-warning'
  return 'hint-danger'
})

/** 打开打分弹窗 */
function openScoreDialog(row) {
  scoreDialog.targetId = row.targetId
  scoreDialog.targetName = row.beEvalUserName || `用户 ${row.beEvalUserId}`
  scoreDialog.taskId = scoringView.task.taskId
  scoreForm.score = 80
  scoreDialog.visible = true
}

/** 重置打分弹窗 */
function resetScoreDialog() {
  scoreFormRef.value?.clearValidate()
  scoreForm.score = 80
}

/** 提交评分 */
async function handleSubmitScore() {
  const valid = await scoreFormRef.value?.validate().catch(() => false)
  if (!valid) return
  scoreDialog.submitting = true
  try {
    await submitScore({
      taskId: scoreDialog.taskId,
      targetId: scoreDialog.targetId,
      score: scoreForm.score
    })
    // 写入本地缓存，让 UI 立即显示已打分状态
    localScores.value = {
      ...localScores.value,
      [scoreDialog.targetId]: scoreForm.score
    }
    ElMessage.success('评分提交成功')
    scoreDialog.visible = false
    // 刷新被评价人列表以获取最新服务端数据
    const targets = await listMyTaskTargets(scoreDialog.taskId)
    scoringView.targets = Array.isArray(targets) ? targets : []
  } catch (e) {
    ElMessage.error('提交评分失败：' + (e?.message || '未知错误'))
  } finally {
    scoreDialog.submitting = false
  }
}

// ===================== 生命周期 =====================

onMounted(() => {
  loadList()
})

onUnmounted(() => {
  stopCountdown()
})
</script>

<style lang="scss" scoped>
$text-1: #1a1a2e;
$text-2: #4a5568;
$text-3: #a0aec0;
$border-1: #e2e8f0;
$bg-soft: #f7fafc;
$primary: #4361ee;
$danger: #e53e3e;

.my-tasks-page {
  padding: 24px;
  background: #fff;
  min-height: 100%;

  /* 页头 */
  .page-h {
    display: flex;
    align-items: center;
    margin-bottom: 20px;
    gap: 12px;

    h1 {
      font-size: 20px;
      font-weight: 600;
      color: $text-1;
      margin: 0;
    }

    .scoring-title {
      font-size: 18px;
    }

    .desc {
      font-size: 13px;
      color: $text-3;
    }

    .actions {
      margin-left: auto;
    }
  }

  /* 截止时间提示 */
  .deadline-hint {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 13px;
    padding: 4px 10px;
    border-radius: 6px;
    margin-left: auto;

    .countdown {
      font-weight: 500;
    }

    &.deadline-normal {
      color: $text-2;
      background: $bg-soft;
    }

    &.deadline-warning {
      color: #d46b08;
      background: #fff7e6;
    }

    &.deadline-danger {
      color: $danger;
      background: #fff1f0;
    }
  }

  /* 分页 */
  .pagination-wrap {
    display: flex;
    justify-content: flex-end;
    margin-top: 20px;
  }

  /* 状态标签 */
  .tag-success {
    display: inline-block;
    padding: 2px 10px;
    border-radius: 12px;
    font-size: 12px;
    background: #e6f4ea;
    color: #1e7e34;
    border: 1px solid #b7dfbf;
  }

  .tag-info {
    display: inline-block;
    padding: 2px 10px;
    border-radius: 12px;
    font-size: 12px;
    background: #f0f0f0;
    color: #666;
    border: 1px solid #d9d9d9;
  }

  /* 分数展示 */
  .score-value {
    font-weight: 600;
    color: $primary;
  }

  .score-submitted {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    font-weight: 600;
    color: #1e7e34;
  }

  .score-empty {
    color: $text-3;
  }

  /* 打分弹窗内 */
  .score-slider-wrap {
    display: flex;
    align-items: center;
    width: 100%;
    padding-right: 12px;
  }

  .score-hint {
    margin-top: 6px;
    font-size: 13px;
    font-weight: 500;

    .hint-success { color: #1e7e34; }
    .hint-primary { color: $primary; }
    .hint-warning { color: #d46b08; }
    .hint-danger  { color: $danger; }
  }
}
</style>
