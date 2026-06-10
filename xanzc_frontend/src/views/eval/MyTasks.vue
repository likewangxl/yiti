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
        <el-table-column label="任务类型" width="130" align="center">
          <template #default="{ row }">
            <span class="tag-type">{{ row.taskTypeLabel || row.taskType }}</span>
          </template>
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
        <h1 class="process-title">{{ processView.group.dept || '无部门' }}</h1>
        <span class="desc">{{ processView.group.taskTypeLabel || processView.group.taskType }}</span>
        <div class="deadline-hint">
          <el-icon><Clock /></el-icon>
          截止：{{ formatDateTime(processView.group.deadline) }}
        </div>
      </div>

      <el-table v-loading="processView.loading" :data="processView.items" border stripe style="width: 100%">
        <el-table-column prop="beEvalUserId" label="被打分人编号" width="130" align="center" />
        <el-table-column prop="beEvalUserName" label="被打分人姓名" min-width="120">
          <template #default="{ row }">{{ row.beEvalUserName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="beEvalDept" label="部门" min-width="140">
          <template #default="{ row }">{{ row.beEvalDept || '—' }}</template>
        </el-table-column>
        <el-table-column prop="beEvalTag" label="标签" width="120">
          <template #default="{ row }">{{ row.beEvalTag || '—' }}</template>
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
            />
            <!-- 等级打分 -->
            <el-select
              v-else
              v-model="editScores[row.itemId]"
              placeholder="选择等级" size="small" style="width: 200px"
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Clock, Check } from '@element-plus/icons-vue'
import { listPendingTasks, listPendingItems, submitPendingScore } from '@/api/eval'

// 等级打分选项：等级名 -> 对应分值（与系统口径一致）
const LEVEL_OPTIONS = [
  { label: '非常满意', score: 100 },
  { label: '比较满意', score: 95 },
  { label: '满意', score: 85 },
  { label: '一般', score: 75 },
  { label: '不满意', score: 59 },
]

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
const submittingId = ref(null)

async function enterProcess(group) {
  processView.group = group
  processView.active = true
  processView.items = []
  processView.loading = true
  Object.keys(editScores).forEach(k => delete editScores[k])
  try {
    const items = await listPendingItems(group.batchId, group.dept || '')
    processView.items = Array.isArray(items) ? items : []
    // 初始化草稿：数值默认 80，等级不预选
    for (const it of processView.items) {
      if (it.submitted !== 1) {
        editScores[it.itemId] = it.scoreType === 'NUM' ? 80 : null
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

  .deadline-hint {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 13px;
    padding: 4px 10px;
    border-radius: 6px;
    margin-left: auto;
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
