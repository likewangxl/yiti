<template>
  <div class="warning-container" v-loading="loading">
    <h2 class="page-title">红黄牌预警池</h2>

    <!-- 红牌支部 -->
    <div class="section-block">
      <h3 class="section-title red-title">🔴 红牌支部（一票否决）</h3>
      <div class="card-grid">
        <div
          v-for="item in redCards"
          :key="item.id"
          class="warning-card red-card"
        >
          <div class="card-top">
            <span class="branch-name">{{ item.branch }}</span>
            <span class="locked-tag">🔒 已锁定</span>
          </div>
          <div class="score-row">
            <span class="big-score red-score">{{ item.score }}</span>
            <span class="score-unit">分</span>
          </div>
          <div class="dim-breakdown">
            <span class="dim-item">考核期间：{{ item.period || '-' }}</span>
          </div>
          <div class="card-warning red-warning">⚠️ 不符合年度考核资格</div>
        </div>
        <div v-if="redCards.length === 0" class="empty-tip">暂无红牌支部</div>
      </div>
    </div>

    <!-- 黄牌支部 -->
    <div class="section-block">
      <h3 class="section-title yellow-title">🟡 黄牌支部（预警）</h3>
      <div class="card-grid">
        <div
          v-for="item in yellowCards"
          :key="item.id"
          class="warning-card yellow-card"
        >
          <div class="card-top">
            <span class="branch-name">{{ item.branch }}</span>
            <span class="warn-tag">⚠ 预警</span>
          </div>
          <div class="score-row">
            <span class="big-score yellow-score">{{ item.score }}</span>
            <span class="score-unit">分</span>
          </div>
          <div class="dim-breakdown">
            <span class="dim-item">考核期间：{{ item.period || '-' }}</span>
          </div>
          <div class="card-warning yellow-warning">📌 建议加强整改</div>
        </div>
        <div v-if="yellowCards.length === 0" class="empty-tip">暂无黄牌支部</div>
      </div>
    </div>

    <!-- 逾期上报-待执行扣分：源系统本页无此功能（源 WarningView.vue 纯展示、无任何操作按钮），
         本次按 task-14-brief 要求新增：高危操作 POST /re/cockpit/overdue/execute 强制 reason
         （@AuditLog(reasonRequired=true) + ReOverdueExecuteReqDTO.reason 为 @NotBlank），
         用弹窗收集必填原因 -->
    <div class="section-block" v-if="canExecute">
      <h3 class="section-title exec-title">⏰ 逾期上报 · 待执行扣分</h3>
      <div v-if="overdueItems.length === 0" class="empty-tip">暂无逾期上报</div>
      <el-table v-else :data="overdueItems" stripe style="width: 100%;">
        <el-table-column prop="branch" label="党组织" width="180" />
        <el-table-column prop="projectName" label="项目名称" min-width="180" />
        <el-table-column prop="submitDate" label="上报日期" width="120" />
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button type="danger" size="small" @click="openExecDialog(row)">执行扣分</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 执行扣分弹窗：reason 必填（高危操作审计留痕），源系统无此输入项，本次新增 -->
    <el-dialog v-model="execDialog.show" title="执行逾期扣分" width="480px">
      <div class="exec-branch">党组织：{{ execDialog.branch }}</div>
      <el-form ref="execFormRef" :model="execDialog.form" :rules="execRules" label-width="90px">
        <el-form-item label="扣分分值">
          <el-input-number v-model="execDialog.form.deductionPoints" :min="0" :max="100" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="执行原因" prop="reason">
          <el-input
            v-model="execDialog.form.reason"
            type="textarea"
            :rows="3"
            placeholder="高危操作，审计强制留痕，必填"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="execDialog.show = false">取消</el-button>
        <el-button type="danger" :loading="execDialog.submitting" @click="confirmExecute">确认执行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// 红黄牌预警池。
// F1：script 内 API 全部改走 @/api/redengine。
// 能力缺口说明：ReWarningItemDTO 只有 orgId/finalScore/period/level 四个字段，没有源系统
// dims（党建联建/业务提升/头雁先锋/督导总结四维度分项）——与 cockpit/CockpitView.vue 同样的
// 已知缺口（分项评分无按组织聚合的查询接口），本页把"dim-breakdown"改为展示 period（考核期间），
// 不再展示编造的分项数值。orgId→组织名同样靠额外拉一次 getOrgTree() 在前端拍平映射。
// 新增能力（task-14-brief 明确要求）：源 WarningView.vue 完全没有"执行逾期扣分"操作
// （纯展示卡片，无任何按钮/表单），本次新增"逾期上报-待执行扣分"表格 + 执行弹窗，对接
// POST /re/cockpit/overdue/execute（ReOverdueExecuteReqDTO：submitId/deductionPoints/reason，
// reason 为 @NotBlank 且 @AuditLog(reasonRequired=true)——高危操作审计强制留痕，故弹窗把
// "执行原因"设为必填项）。执行按钮仅在当前用户持有 P_RE_CKPT_EXEC 资源
// （/api/re/cockpit/overdue/execute，按种子 SQL 仅 R_RE_ORGREV + SYS_ADMIN 授权）时展示，
// 复用 Task 13 RedEngineLayout 提供的 inject('canSee')。
import { ref, reactive, computed, inject, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getRedWarning, getYellowWarning, getOverdueList, executeOverdue, getOrgTree } from '@/api/redengine'

const canSee = inject('canSee', () => false)
const canExecute = computed(() => canSee({ res: '/api/re/cockpit/overdue/execute' }))

const loading = ref(false)
const redCards = ref([])
const yellowCards = ref([])
const overdueItems = ref([])

function flattenOrgTree(nodes, map) {
  for (const n of nodes || []) {
    map.set(n.id, n.orgName)
    if (n.children && n.children.length) flattenOrgTree(n.children, map)
  }
}

async function reload() {
  loading.value = true
  try {
    const [red, yellow, overdue, orgTree] = await Promise.all([
      getRedWarning(),
      getYellowWarning(),
      getOverdueList(),
      getOrgTree(),
    ])
    const orgNameMap = new Map()
    flattenOrgTree(orgTree, orgNameMap)

    const toCard = (row) => ({
      id: `${row.orgId}-${row.period}`,
      branch: orgNameMap.get(row.orgId) || `组织#${row.orgId}`,
      score: Number(row.finalScore ?? 0),
      period: row.period,
    })
    redCards.value = (red || []).map(toCard)
    yellowCards.value = (yellow || []).map(toCard)

    overdueItems.value = (overdue || []).map((row) => ({
      id: row.id,
      orgId: row.orgId,
      branch: orgNameMap.get(row.orgId) || `组织#${row.orgId}`,
      projectName: row.projectName,
      submitDate: row.submitDate,
    }))
  } catch {
    redCards.value = []
    yellowCards.value = []
    overdueItems.value = []
  } finally {
    loading.value = false
  }
}

// 执行扣分弹窗状态
const execFormRef = ref(null)
const execDialog = reactive({
  show: false,
  submitting: false,
  submitId: null,
  branch: '',
  form: { deductionPoints: 5, reason: '' },
})
const execRules = {
  reason: [{ required: true, message: '请填写执行原因（审计留痕必填）', trigger: 'blur' }],
}

function openExecDialog(item) {
  execDialog.submitId = item.id
  execDialog.branch = item.branch
  execDialog.form = { deductionPoints: 5, reason: '' }
  execDialog.show = true
}

async function confirmExecute() {
  try {
    await execFormRef.value?.validate()
  } catch {
    return
  }
  execDialog.submitting = true
  try {
    await executeOverdue({
      submitId: execDialog.submitId,
      deductionPoints: execDialog.form.deductionPoints,
      reason: execDialog.form.reason,
    })
    ElMessage.success('已执行扣分')
    execDialog.show = false
    await reload()
  } catch (e) {
    ElMessage.error(e?.message || '执行失败')
  } finally {
    execDialog.submitting = false
  }
}

onMounted(reload)
</script>

<style scoped>
.warning-container {
  padding: 24px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #1e293b;
  margin: 0 0 28px 0;
}

.section-block {
  margin-bottom: 32px;
}

.section-title {
  font-size: 17px;
  font-weight: 600;
  margin: 0 0 16px 0;
}

.red-title {
  color: #dc2626;
}

.yellow-title {
  color: #ca8a04;
}

.exec-title {
  color: #b45309;
}

.exec-branch {
  margin-bottom: 12px;
  color: #64748b;
  font-size: 13px;
}

.empty-tip {
  color: #94a3b8;
  font-size: 13px;
  padding: 12px 0;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.warning-card {
  border-radius: 8px;
  padding: 20px;
  transition: box-shadow 0.2s;
}

.warning-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.red-card {
  background: #fee2e2;
  border: 2px solid #dc2626;
  border-top-width: 4px;
}

.yellow-card {
  background: #fef9c3;
  border: 2px solid #ca8a04;
  border-top-width: 4px;
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.branch-name {
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
}

.locked-tag {
  font-size: 12px;
  font-weight: 600;
  color: #dc2626;
  background: #fecaca;
  padding: 2px 8px;
  border-radius: 10px;
  border: 1px solid #dc2626;
}

.warn-tag {
  font-size: 12px;
  font-weight: 600;
  color: #ca8a04;
  background: #fef08a;
  padding: 2px 8px;
  border-radius: 10px;
  border: 1px solid #ca8a04;
}

.score-row {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-bottom: 12px;
}

.big-score {
  font-size: 28px;
  font-weight: 700;
}

.red-score {
  color: #dc2626;
}

.yellow-score {
  color: #ca8a04;
}

.score-unit {
  font-size: 14px;
  color: #64748b;
}

.dim-breakdown {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.dim-item {
  font-size: 12px;
  color: #475569;
  background: rgba(255, 255, 255, 0.6);
  padding: 2px 8px;
  border-radius: 4px;
}

.card-warning {
  font-size: 13px;
  font-weight: 600;
  padding: 8px 12px;
  border-radius: 6px;
}

.red-warning {
  color: #991b1b;
  background: #fecaca;
}

.yellow-warning {
  color: #854d0e;
  background: #fde68a;
}
</style>
