<template>
  <div class="archive-container" v-loading="loading">
    <h2 class="page-title">🏆 年度考核归档</h2>
    <p class="page-desc">按40%权重计入党建考核 · 本期（{{ period }}）结算状态：{{ settled ? '已结算' : '未结算' }}</p>

    <div class="action-bar">
      <el-button v-if="canGenerate" type="danger" :loading="generating" @click="handleGenerate">生成年度报告</el-button>
      <el-button type="primary" :loading="exporting" @click="handleExport">导出Excel</el-button>
    </div>

    <div class="section-card">
      <el-table :data="branches" stripe style="width: 100%;">
        <el-table-column prop="name" label="支部" />
        <el-table-column label="原始得分" width="100" align="center">
          <template #default="{ row }">
            <span style="font-weight:600;">{{ row.score }}</span>
          </template>
        </el-table-column>
        <el-table-column label="×40%权重" width="100" align="center">
          <template #default="{ row }">{{ (row.score * 0.4).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="计入党建考核" width="120" align="center">
          <template #default="{ row }">
            <span style="font-weight:600;">{{ (row.score * 0.4).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="评优资格" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.score >= 60" type="success" size="small">✅ 符合资格</el-tag>
            <el-tag v-else type="danger" size="small">🔒 一票否决</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="stat-grid">
      <div class="stat-card" style="background:#fee2e2; border-top:3px solid #dc2626;">
        <div class="stat-label">全行均分</div>
        <div class="stat-value" style="color:#dc2626;">{{ avgScore }}</div>
      </div>
      <div class="stat-card" style="background:#dcfce7; border-top:3px solid #16a34a;">
        <div class="stat-label">最高分支部</div>
        <div class="stat-value" style="color:#16a34a; font-size:16px;">{{ topBranch }}</div>
      </div>
      <div class="stat-card" style="background:#fee2e2; border-top:3px solid #b91c1c;">
        <div class="stat-label">红牌数</div>
        <div class="stat-value" style="color:#b91c1c;">{{ redCount }}</div>
      </div>
      <div class="stat-card" style="background:#dbeafe; border-top:3px solid #2563eb;">
        <div class="stat-label">审核通过率</div>
        <div class="stat-value" style="color:#2563eb;">{{ passRate }}%</div>
      </div>
    </div>

    <!-- 生成年度报告弹窗：生成原因必填（高危操作审计留痕，对齐后端 ReAnnualGenerateReqDTO.reason
         @NotBlank + @AuditLog(reasonRequired=true)）。2026-07-19 修复：原实现仅 ElMessageBox 二次
         确认、无任何 reason 承载通道 -->
    <el-dialog v-model="generateDialog.show" title="生成年度报告" width="480px">
      <div class="generate-desc">确认生成 {{ currentYear }} 年度考核归档结果？生成后将作为正式考核依据，请谨慎操作。</div>
      <el-form ref="generateFormRef" :model="generateDialog.form" :rules="generateRules" label-width="90px">
        <el-form-item label="生成原因" prop="reason">
          <el-input
            v-model="generateDialog.form.reason"
            type="textarea"
            :rows="3"
            placeholder="高危操作，审计强制留痕，必填"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="generateDialog.show = false">取消</el-button>
        <el-button type="danger" :loading="generating" @click="confirmGenerate">确认生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// 年度考核归档。
// F1：script 内 API 全部改走 @/api/redengine。
// 数据源：与 cockpit/CockpitView.vue 一致，用 getRanking()（+ getOrgTree() 拍平取组织名）
// 承接源系统本地 mock 的"各支部得分"表格。
// 操作按钮改动说明（源系统 handleAction 对三个按钮都只是同一个纯前端 ElMessage 装饰，
// 没有任何后端调用）：
//   - "生成年度报告" → 接后端真实高危端点 POST /re/cockpit/archive/generate/{year}
//     （2026-07-19 修复：@AuditLog 已补齐 reasonRequired=true + ReAnnualGenerateReqDTO.reason
//     @NotBlank，原纯 ElMessageBox 二次确认弹窗改为「生成原因」必填弹窗）；仅当前用户持有
//     P_RE_CKPT_ANNUAL 资源时展示按钮（种子 SQL 里只授权给 R_RE_ORGREV + SYS_ADMIN），复用
//     inject('canSee')。
//   - "导出Excel" → 接 GET /re/export/score（复用 export/ExportView.vue 同款 blob 下载逻辑）。
//   - "推送测评系统" → 后端 6 个 Controller 均无对应端点，属源系统就不存在的能力（同样是纯装饰
//     ElMessage），本次移植按 YAGNI 直接去掉这个按钮，不新造假接口。
import { ref, reactive, computed, inject, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getRanking, getOrgTree, generateAnnual, archiveSettlement, exportData } from '@/api/redengine'

const canSee = inject('canSee', () => false)
const canGenerate = computed(() => canSee({ res: '/api/re/cockpit/archive/generate/*' }))

const loading = ref(false)
const generating = ref(false)
const exporting = ref(false)
const branches = ref([])
const settled = ref(false)

const now = new Date()
const period = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
const currentYear = now.getFullYear()

function flattenOrgTree(nodes, map) {
  for (const n of nodes || []) {
    map.set(n.id, n.orgName)
    if (n.children && n.children.length) flattenOrgTree(n.children, map)
  }
}

async function reload() {
  loading.value = true
  try {
    const [ranking, orgTree, settlement] = await Promise.all([
      getRanking(),
      getOrgTree(),
      archiveSettlement(period).catch(() => false),
    ])
    const orgNameMap = new Map()
    flattenOrgTree(orgTree, orgNameMap)
    branches.value = (ranking || [])
      .map((row) => ({
        id: row.orgId,
        name: orgNameMap.get(row.orgId) || `组织#${row.orgId}`,
        score: Number(row.finalScore ?? 0),
      }))
      .sort((a, b) => b.score - a.score)
    settled.value = !!settlement
  } catch {
    branches.value = []
  } finally {
    loading.value = false
  }
}

const avgScore = computed(() => (branches.value.length ? (branches.value.reduce((s, b) => s + b.score, 0) / branches.value.length).toFixed(1) : '0.0'))
const topBranch = computed(() => branches.value[0]?.name || '-')
const redCount = computed(() => branches.value.filter((b) => b.score < 60).length)
const passRate = computed(() => (branches.value.length ? ((branches.value.filter((b) => b.score >= 60).length / branches.value.length) * 100).toFixed(1) : '0.0'))

// 生成年度报告弹窗状态（高危操作，生成原因必填，见上方脚本头部注释）
const generateFormRef = ref(null)
const generateDialog = reactive({ show: false, form: { reason: '' } })
const generateRules = {
  reason: [{ required: true, message: '请填写生成原因（审计留痕必填）', trigger: 'blur' }]
}

// 点击「生成年度报告」：打开「生成原因」弹窗，而非直接二次确认后即生成
function handleGenerate() {
  generateDialog.form = { reason: '' }
  generateDialog.show = true
}

async function confirmGenerate() {
  try {
    await generateFormRef.value?.validate()
  } catch {
    return
  }
  generating.value = true
  try {
    await generateAnnual(currentYear, generateDialog.form.reason)
    ElMessage.success(`✅ ${currentYear} 年度报告已生成`)
    generateDialog.show = false
    await reload()
  } catch (e) {
    ElMessage.error(e?.message || '生成失败')
  } finally {
    generating.value = false
  }
}

async function handleExport() {
  exporting.value = true
  try {
    const blob = await exportData('score')
    if (!blob) return
    const data = blob instanceof Blob ? blob : new Blob([blob])
    const url = URL.createObjectURL(data)
    const a = document.createElement('a')
    a.href = url
    a.download = 'export_score.xlsx'
    document.body.appendChild(a)
    a.click()
    setTimeout(() => { URL.revokeObjectURL(url); a.remove() }, 0)
    ElMessage.success('✅ 导出Excel已触发')
  } catch (e) {
    ElMessage.error(e?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

onMounted(reload)
</script>

<style scoped lang="scss">
.archive-container { padding: 0; }
.page-title { font-size: 22px; font-weight: 700; color: #1e293b; margin: 0 0 4px 0; }
.page-desc { font-size: 13px; color: #64748b; margin: 0 0 20px 0; }
.generate-desc { margin-bottom: 12px; color: #64748b; font-size: 13px; }

.action-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.section-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  border: 1px solid #e2e8f0;
  margin-bottom: 20px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.stat-card {
  border-radius: 8px;
  padding: 18px;

  .stat-label { font-size: 12px; color: #64748b; margin-bottom: 6px; }
  .stat-value { font-size: 28px; font-weight: 700; }
}

@media (max-width: 768px) {
  .stat-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
