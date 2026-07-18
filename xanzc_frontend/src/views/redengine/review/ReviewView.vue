<template>
  <div class="review-container">
    <h2 class="page-title">沉浸式审核工作台</h2>

    <div class="review-layout">
      <!-- 左侧：审核队列 -->
      <div class="queue-panel" v-loading="loading">
        <div class="queue-header">
          <span class="badge pending-badge">待审 {{ pendingItems.length }}</span>
          <span class="badge pass-badge">通过 {{ passedItems.length }}</span>
          <span class="badge reject-badge">驳回 {{ rejectedItems.length }}</span>
        </div>
        <div class="queue-list">
          <div
            v-for="item in reviewItems"
            :key="item.id"
            :class="['queue-item', { active: selectedItem?.id === item.id }]"
            @click="selectItem(item)"
          >
            <div class="item-branch">{{ item.branch }}</div>
            <div class="item-name">{{ item.item }}</div>
            <div class="item-meta">{{ item.submitter }} · {{ item.date }}</div>
            <span :class="['status-tag', item.status]">
              {{ item.status === 'pending' ? '待审' : item.status === 'passed' ? '已通过' : '已驳回' }}
            </span>
          </div>
        </div>
      </div>

      <!-- 中间：内容预览 -->
      <div v-if="selectedItem" class="preview-panel" v-loading="previewLoading">
        <div class="preview-header">
          <div class="preview-title">{{ selectedItem.item }}</div>
          <div class="preview-sub">{{ selectedItem.branch }} · {{ selectedItem.submitter }}</div>
        </div>
        <div class="preview-body">
          <!-- 结构化数据 -->
          <div v-if="selectedItem.formData" class="form-data-block">
            <div class="block-title">📋 结构化填报数据（核验区）</div>
            <div v-for="(value, key) in selectedItem.formData" :key="key" class="form-row">
              <span class="form-key">{{ key }}:</span>
              <span class="form-value">{{ value }}</span>
            </div>
          </div>

          <!-- 附件清单 -->
          <div class="files-block">
            <div class="block-title">📄 附件清单</div>
            <div v-if="selectedItem.files.length === 0" class="file-item file-empty">暂无可查询附件</div>
            <div v-for="(file, idx) in selectedItem.files" :key="idx" class="file-item">
              <span class="file-icon">{{ getFileIcon(file) }}</span>
              <span>{{ file }}</span>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="preview-panel empty-panel">
        <div class="empty-text">← 请从左侧选择一项进行审核</div>
      </div>

      <!-- 右侧：评分与裁决 -->
      <div v-if="selectedItem" class="scoring-panel">
        <h3 class="scoring-title">评分与裁决</h3>

        <div class="info-block">
          <div class="info-label">考核项</div>
          <div class="info-value">{{ selectedItem.item }}</div>
        </div>

        <div class="rule-block">
          <div class="rule-label">📋 评分规则</div>
          <div class="rule-text">{{ selectedItem.rule }}</div>
        </div>

        <div class="suggest-block">
          <div class="suggest-label">📐 满分上限</div>
          <div class="suggest-value">{{ selectedItem.maxScore }} 分</div>
        </div>

        <div class="score-input">
          <label>最终得分</label>
          <el-input-number
            v-model="finalScore"
            :min="0"
            :max="selectedItem.maxScore"
            style="width: 100%;"
          />
        </div>

        <div class="comment-input">
          <label>审核意见</label>
          <el-input
            v-model="reviewComment"
            type="textarea"
            :rows="3"
            placeholder="输入审核意见..."
          />
        </div>

        <!-- 操作按钮 -->
        <div v-if="selectedItem.status === 'pending'" class="action-buttons">
          <el-button type="success" class="action-btn" :loading="acting" @click="handleApprove">
            ✅ 通过并计 {{ finalScore ?? 0 }} 分
          </el-button>
          <el-button class="action-btn reject-btn" :loading="acting" @click="handleRejectClick">
            ❌ 驳回重交
          </el-button>
        </div>

        <div v-if="selectedItem.status === 'passed'" class="result-block passed">
          ✅ 已通过，计 {{ selectedItem.finalScore }} 分
        </div>
        <div v-if="selectedItem.status === 'rejected'" class="result-block rejected">
          <div>❌ 已驳回</div>
          <div class="reject-reason">{{ selectedItem.rejectReason }}</div>
        </div>
      </div>
    </div>

    <!-- 驳回弹窗 -->
    <el-dialog v-model="showRejectModal" title="驳回原因" width="500px">
      <div class="reject-info">
        <div class="reject-item-name">{{ selectedItem?.item }}</div>
        <div class="reject-branch">支部：{{ selectedItem?.branch }}</div>
      </div>
      <el-input
        v-model="rejectReason"
        type="textarea"
        :rows="4"
        placeholder="请输入驳回原因，例如：未见明确痛点研讨议程，不计分"
      />
      <template #footer>
        <el-button @click="showRejectModal = false">取消</el-button>
        <el-button type="danger" :loading="acting" @click="handleConfirmReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// 沉浸式审核工作台。
// F1：script 内 API 全部改走 @/api/redengine。与 branch-review/BranchReviewView.vue 共用同一条
// 后端审核队列（GET /re/reviews/queue + POST approve/reject——red-engine-center 只有一套"两级审核"
// 收敛后的 status=1 队列，无独立的组织级队列，见该视图脚本注释），本页额外用
// GET /re/reviews/{id}/preview 在选中队列项时拉取详情供中间预览栏渲染。
// 字段/能力缺口说明（先读 red-engine-center DTO/entity 确认，非凭空猜测）：
//   - "系统建议分值"（源系统 selectedItem.suggest）：源系统本身是硬编码演示值，非真实算法输出，
//     后端也没有对应的评分建议接口，本次移植去掉该栏，改为展示 maxScore（满分上限），
//     避免向审核人展示一个"看似算法给出、实为编造"的建议分值。
//   - "预览区"（源系统 selectedItem.previewContent 原始文本卡片）：同样是源系统纯装饰性 mock 文本，
//     无对应后端字段，本次移植去掉，保留结构化 formData 卡片与附件清单即可覆盖审核所需信息。
//   - 附件清单：ReSubmit.fileUrls 是源系统遗留字段，新提交走 fileObjectIds + governance
//     BizFileRel 关联登记，但当前 6 个 Controller 未提供"按 submitId 查关联文件"的只读端点，
//     故大多数新数据此处为空，属已知能力缺口，非渲染 Bug。
//   - 评分规则文案（rule）/满分（maxScore）：优先取队列返回的 ReSubmit.maxScore；规则说明文字
//     后端未提供文案字段，沿用与 report/JointView.vue 一致的静态评分标准文案（按 itemCode 映射），
//     不是编造数据，是产品既定评分标准的前端静态展示。
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getReviewQueue, getReviewPreview, approveSubmit, rejectSubmit } from '@/api/redengine'

// 评分标准文案：与 report/JointView.vue 的 rule-box 文案保持一致（产品既定标准，非编造数据）
const RULE_TEXT = {
  '1.1': '每次1.5分，最高6分。纯座谈不计分。',
  '1.2': '每份协议1分，最高4分。',
  '1.3': '取最高项不叠加：破冰(2分)→推进(4分)→落地(8分)。满分25分。',
  '2.1': '实际达成量÷目标×50分。≥100%计50分；60%-99%按公式折算；<60%计0分。',
  '2.2': '实际达成量÷目标×50分。≥100%计50分；60%-99%按公式折算；<60%计0分。',
  '4.1': '支部书记亲自带队拜访核心客户/协调关键部门，每次1分，满分5分。',
  '4.2': '有明确责任分工(2分)；难题攻克取得明显进展(3分)。满分5分。',
  sup: '按时提交节点跟踪表、典型案例、工作总结。逾期1天扣1分，逾期3天不得分。满分5分。',
}
const DIM_LABEL = { dim1: '外联共建', dim2: '业务提升', dim3: '头雁先锋', dim4: '督导响应' }

const loading = ref(false)
const previewLoading = ref(false)
const acting = ref(false)
const reviewItems = ref([])

const selectedItem = ref(null)
const finalScore = ref(0)
const reviewComment = ref('')
const showRejectModal = ref(false)
const rejectReason = ref('')

const pendingItems = computed(() => reviewItems.value.filter((i) => i.status === 'pending'))
const passedItems = computed(() => reviewItems.value.filter((i) => i.status === 'passed'))
const rejectedItems = computed(() => reviewItems.value.filter((i) => i.status === 'rejected'))

function parseFormData(raw) {
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

function parseFiles(raw) {
  if (!raw) return []
  try {
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

function toQueueItem(row) {
  return {
    id: row.id,
    branch: row.orgId != null ? `组织#${row.orgId}` : '-',
    dim: DIM_LABEL[row.dimension] || row.dimension || '-',
    item: row.itemName ? `${row.itemCode ? row.itemCode + ' ' : ''}${row.itemName}` : (row.itemCode || '-'),
    itemCode: row.itemCode,
    submitter: row.submitterId || '-',
    date: row.submitDate,
    maxScore: row.maxScore ?? 100,
    rule: RULE_TEXT[row.itemCode] || '（暂无对应静态评分标准文案）',
    formData: parseFormData(row.formData),
    files: parseFiles(row.fileUrls),
    status: 'pending',
    finalScore: null,
    rejectReason: '',
  }
}

// 工作台一次性拉一页（size=50）承接原型"整屏滚动队列"的交互，不做二次翻页 UI——
// 符合 common-dev-guide 分页上限（max 100）约束，见 task-14-report.md 说明
async function reload() {
  loading.value = true
  try {
    const r = await getReviewQueue(1, 50)
    const arr = r?.records || (Array.isArray(r) ? r : [])
    reviewItems.value = arr.map(toQueueItem)
  } catch {
    reviewItems.value = []
  } finally {
    loading.value = false
  }
}

// 只在"切换到不同条目"时把评分清零；同一条目的重赋值（handleApprove/handleConfirmReject
// 成功后 `selectedItem.value = { ...item }`、selectItem() 里 getReviewPreview 异步返回后
// `selectedItem.value = merged`，均只是同 id 对象换引用触发视图刷新）不应清空已录入的分值——
// 否则审核通过后"最终得分"输入框会被误重置为 0，与同屏"已通过，计 X 分"结果横幅自相矛盾
// （Playwright 联调 Task 17d 截图复现），且若用户在预览异步加载期间已手填分数，预览返回时
// 也会静默清零用户刚输入的值。
watch(selectedItem, (val, oldVal) => {
  if (val && (!oldVal || val.id !== oldVal.id)) finalScore.value = 0
})

const selectItem = async (item) => {
  selectedItem.value = item
  reviewComment.value = ''
  previewLoading.value = true
  try {
    const detail = await getReviewPreview(item.id)
    if (detail) {
      const merged = { ...item, formData: parseFormData(detail.formData), files: parseFiles(detail.fileUrls) }
      selectedItem.value = merged
      const idx = reviewItems.value.findIndex((i) => i.id === item.id)
      if (idx >= 0) reviewItems.value[idx] = { ...reviewItems.value[idx], ...merged }
    }
  } catch (e) {
    ElMessage.error(e?.message || '加载详情失败')
  } finally {
    previewLoading.value = false
  }
}

const getFileIcon = (file) => {
  if (file.endsWith('.pdf')) return '📕'
  if (file.endsWith('.xlsx') || file.endsWith('.xls')) return '📗'
  if (file.endsWith('.zip')) return '📦'
  if (file.endsWith('.png') || file.endsWith('.jpg')) return '🖼️'
  if (file.endsWith('.docx') || file.endsWith('.doc')) return '📄'
  return '📄'
}

const handleApprove = async () => {
  if (!selectedItem.value) return
  acting.value = true
  try {
    await approveSubmit(selectedItem.value.id, { score: finalScore.value, feedback: reviewComment.value || undefined })
    const item = reviewItems.value.find((i) => i.id === selectedItem.value.id)
    if (item) {
      item.status = 'passed'
      item.finalScore = finalScore.value
      selectedItem.value = { ...item }
    }
    ElMessage.success(`✅ 通过并计 ${finalScore.value} 分`)
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    acting.value = false
  }
}

const handleRejectClick = () => {
  rejectReason.value = ''
  showRejectModal.value = true
}

const handleConfirmReject = async () => {
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  acting.value = true
  try {
    await rejectSubmit(selectedItem.value.id, { feedback: rejectReason.value })
    const item = reviewItems.value.find((i) => i.id === selectedItem.value.id)
    if (item) {
      item.status = 'rejected'
      item.rejectReason = rejectReason.value
      selectedItem.value = { ...item }
    }
    showRejectModal.value = false
    ElMessage.success('✅ 已驳回')
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    acting.value = false
  }
}

onMounted(reload)
</script>

<style scoped lang="scss">
.review-container {
  padding: 0;

  .page-title {
    font-size: 22px;
    font-weight: 700;
    color: #1e293b;
    margin: 0 0 20px 0;
  }
}

.review-layout {
  display: grid;
  grid-template-columns: 240px 330px 1fr;
  gap: 16px;
  height: calc(100vh - 180px);
}

.queue-panel   { grid-column: 1; }
.scoring-panel { grid-column: 2; }
.preview-panel { grid-column: 3; }

/* Queue Panel */
.queue-panel {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.queue-header {
  padding: 12px;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}

.pending-badge { background: #fef9c3; color: #92400e; }
.pass-badge { background: #dcfce7; color: #166534; }
.reject-badge { background: #fee2e2; color: #b91c1c; }

.queue-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.queue-item {
  padding: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  margin-bottom: 6px;
  cursor: pointer;
  background: #f8fafc;
  transition: all 0.2s;

  &:hover { background: #f1f5f9; }

  &.active {
    background: #fee2e2;
    border-color: #dc2626;
  }

  .item-branch {
    font-size: 12px;
    font-weight: 600;
    color: #1e293b;
    margin-bottom: 2px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .item-name {
    font-size: 11px;
    color: #64748b;
    margin-bottom: 2px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .item-meta {
    font-size: 10px;
    color: #94a3b8;
    margin-bottom: 4px;
  }

  .status-tag {
    font-size: 10px;
    padding: 1px 6px;
    border-radius: 3px;
    font-weight: 600;

    &.pending { background: #fef9c3; color: #92400e; }
    &.passed { background: #dcfce7; color: #166534; }
    &.rejected { background: #fee2e2; color: #b91c1c; }
  }
}

/* Preview Panel */
.preview-panel {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.empty-panel {
  display: flex;
  align-items: center;
  justify-content: center;

  .empty-text {
    color: #94a3b8;
    font-size: 14px;
  }
}

.preview-header {
  padding: 14px 16px;
  border-bottom: 1px solid #e2e8f0;

  .preview-title {
    font-size: 14px;
    font-weight: 600;
    color: #1e293b;
    margin-bottom: 2px;
  }

  .preview-sub {
    font-size: 12px;
    color: #64748b;
  }
}

.preview-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.form-data-block {
  background: #dbeafe;
  border: 1px solid #93c5fd;
  border-radius: 8px;
  padding: 14px;
  margin-bottom: 16px;

  .block-title {
    font-size: 13px;
    font-weight: 600;
    color: #1e40af;
    margin-bottom: 10px;
  }

  .form-row {
    font-size: 13px;
    margin-bottom: 6px;
    display: flex;
    gap: 10px;

    .form-key {
      font-weight: 600;
      color: #1e40af;
      min-width: 90px;
    }

    .form-value { color: #1e293b; }
  }
}

.files-block {
  margin-bottom: 16px;

  .block-title {
    font-size: 13px;
    font-weight: 600;
    color: #1e293b;
    margin-bottom: 8px;
  }

  .file-item {
    padding: 6px 10px;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 6px;
    margin-bottom: 4px;
    font-size: 13px;
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;

    &:hover { background: #f1f5f9; }
  }

  .file-empty {
    color: #94a3b8;
    cursor: default;

    &:hover { background: #f8fafc; }
  }
}

/* Scoring Panel */
.scoring-panel {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  padding: 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.scoring-title {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  margin: 0 0 14px 0;
}

.info-block {
  margin-bottom: 12px;

  .info-label {
    font-size: 12px;
    font-weight: 600;
    color: #1e293b;
    margin-bottom: 2px;
  }

  .info-value {
    font-size: 13px;
    color: #475569;
  }
}

.rule-block {
  background: #fef9c3;
  border: 1px solid #fef3c7;
  border-radius: 6px;
  padding: 10px;
  margin-bottom: 12px;

  .rule-label {
    font-size: 11px;
    font-weight: 600;
    color: #92400e;
    margin-bottom: 4px;
  }

  .rule-text {
    font-size: 12px;
    color: #92400e;
    line-height: 1.5;
  }
}

.suggest-block {
  background: #dcfce7;
  border: 1px solid #bbf7d0;
  border-radius: 6px;
  padding: 10px;
  margin-bottom: 14px;

  .suggest-label {
    font-size: 11px;
    font-weight: 600;
    color: #166534;
    margin-bottom: 4px;
  }

  .suggest-value {
    font-size: 18px;
    font-weight: 700;
    color: #16a34a;
  }
}

.score-input, .comment-input {
  margin-bottom: 12px;

  label {
    display: block;
    font-size: 12px;
    font-weight: 600;
    color: #1e293b;
    margin-bottom: 6px;
  }
}

.action-buttons {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: auto;

  .action-btn {
    width: 100%;
    font-weight: 600;
  }

  .reject-btn {
    background: #fecaca;
    color: #b91c1c;
    border: none;

    &:hover {
      background: #fca5a5;
    }
  }
}

.result-block {
  padding: 10px;
  border-radius: 6px;
  text-align: center;
  font-size: 13px;
  font-weight: 600;
  margin-top: auto;

  &.passed {
    background: #dcfce7;
    color: #166534;
  }

  &.rejected {
    background: #fee2e2;
    color: #b91c1c;
    text-align: left;

    .reject-reason {
      font-size: 11px;
      color: #7f1d1d;
      margin-top: 4px;
      font-weight: 400;
    }
  }
}

/* Reject Modal */
.reject-info {
  background: #fee2e2;
  padding: 10px;
  border-radius: 6px;
  margin-bottom: 14px;

  .reject-item-name {
    font-weight: 600;
    color: #7f1d1d;
    font-size: 14px;
  }

  .reject-branch {
    font-size: 12px;
    color: #991b1b;
    margin-top: 4px;
  }
}

@media (max-width: 1024px) {
  .review-layout {
    grid-template-columns: 1fr;
    height: auto;
  }
}
</style>
