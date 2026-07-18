<template>
  <div class="branch-review-container">
    <h2 class="page-title">支部审核工作台</h2>
    <p class="page-desc">审核报送员提交的材料，通过后正式上报至组织部</p>

    <!-- 统计 -->
    <div class="stat-bar">
      <div class="stat-item pending-stat">
        <span class="stat-num">{{ pendingItems.length }}</span>
        <span class="stat-label">待审核</span>
      </div>
      <div class="stat-item approved-stat">
        <span class="stat-num">{{ approvedItems.length }}</span>
        <span class="stat-label">已通过</span>
      </div>
      <div class="stat-item rejected-stat">
        <span class="stat-num">{{ rejectedItems.length }}</span>
        <span class="stat-label">已退回</span>
      </div>
    </div>

    <!-- 筛选 -->
    <div class="filter-bar">
      <el-radio-group v-model="filterStatus" size="small">
        <el-radio-button label="all">全部</el-radio-button>
        <el-radio-button label="pending">待审核</el-radio-button>
        <el-radio-button label="approved">已通过</el-radio-button>
        <el-radio-button label="rejected">已退回</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 审核列表 -->
    <div class="review-list" v-loading="loading">
      <div v-if="filteredItems.length === 0" class="empty-state">
        <span>暂无{{ filterStatus === 'all' ? '' : filterStatus === 'pending' ? '待审核' : filterStatus === 'approved' ? '已通过' : '已退回' }}记录</span>
      </div>

      <div v-for="item in filteredItems" :key="item.id" class="review-card">
        <div class="card-header">
          <div class="card-left">
            <span class="dim-badge" :style="{ background: getDimColor(item.dim) }">{{ item.dim }}</span>
            <span class="item-name">{{ item.itemName }}</span>
          </div>
          <div class="card-right">
            <el-tag
              :type="item.status === 'approved' ? 'success' : item.status === 'rejected' ? 'danger' : 'warning'"
              size="small"
            >
              {{ item.status === 'approved' ? '已通过上报' : item.status === 'rejected' ? '已退回' : '待审核' }}
            </el-tag>
          </div>
        </div>

        <div class="card-body">
          <div class="meta-row">
            <span class="meta-item">📋 提交人：{{ item.submitter }}</span>
            <span class="meta-item">📅 {{ item.submitDate }}</span>
          </div>
          <div class="summary">{{ item.summary }}</div>

          <!-- 附件 -->
          <div v-if="item.files && item.files.length > 0" class="files-row">
            <span v-for="(file, idx) in item.files" :key="idx" class="file-tag">
              {{ getFileIcon(file) }} {{ file }}
            </span>
          </div>

          <!-- 表单数据 -->
          <div v-if="item.formData" class="form-data">
            <div v-for="(val, key) in item.formData" :key="key" class="data-row">
              <span class="data-key">{{ key }}：</span>
              <span class="data-val">{{ val }}</span>
            </div>
          </div>
        </div>

        <!-- 操作区 -->
        <div v-if="item.status === 'pending'" class="card-actions">
          <div class="action-note">
            <el-input
              v-model="item.reviewNote"
              size="small"
              placeholder="审核意见（选填）"
              style="width: 300px;"
            />
          </div>
          <div class="action-btns">
            <el-button type="success" size="small" :loading="item.acting" @click="handleApprove(item)">
              ✅ 审核通过并上报
            </el-button>
            <el-button type="danger" size="small" plain :loading="item.acting" @click="handleReject(item)">
              ↩ 退回修改
            </el-button>
          </div>
        </div>

        <!-- 审核结果 -->
        <div v-if="item.status === 'approved'" class="result-bar approved-bar">
          ✅ 已通过 — 已正式上报至组织部审核
          <span v-if="item.reviewNote" class="result-note">备注：{{ item.reviewNote }}</span>
        </div>
        <div v-if="item.status === 'rejected'" class="result-bar rejected-bar">
          ↩ 已退回给报送员修改
          <span v-if="item.reviewNote" class="result-note">原因：{{ item.reviewNote }}</span>
        </div>
      </div>
    </div>

    <div class="pager">
      <el-pagination
        v-model:current-page="pageNo"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="reload"
        @size-change="reload"
      />
    </div>
  </div>
</template>

<script setup>
// 支部审核工作台。
// F1：script 内 API 全部改走 @/api/redengine。后端 6 个 Controller 里只有一条"两级审核"队列
// （ReReviewController：GET /re/reviews/queue + POST approve/reject），没有区分"支部级"/"组织级"
// 两条独立队列——本页与 review/ReviewView.vue（沉浸式审核工作台）共用同一队列/同一 approve/reject
// 接口，仅 UI 呈现方式不同（卡片列表 vs 三栏沉浸式），与 red-engine-center ReReviewController 的
// 类注释一致（"移植自 redengine ReviewController"，Task 9 已把两级审核收敛为一条 status=1 队列）。
// approve/reject 成功后接口返回的记录会从 status=1 队列里消失（无法再次 GET 到），故本页把
// "已通过/已退回"结果保留在本地 items 数组里展示（不从数组删除），直到用户翻页/刷新重新拉取
// 才会清空——与源系统纯本地状态切换的交互体验保持一致。
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getReviewQueue, approveSubmit, rejectSubmit } from '@/api/redengine'

const filterStatus = ref('all')
const loading = ref(false)
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)

const items = ref([])

const DIM_LABEL = { dim1: '外联共建', dim2: '业务提升', dim3: '头雁先锋', dim4: '督导响应' }
const DIM_COLOR = { 外联共建: '#dc2626', 业务提升: '#2563eb', 头雁先锋: '#ca8a04', 督导响应: '#16a34a' }

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

async function reload() {
  loading.value = true
  try {
    const r = await getReviewQueue(pageNo.value, pageSize.value)
    const arr = r?.records || (Array.isArray(r) ? r : [])
    items.value = arr.map((row) => ({
      id: row.id,
      dim: DIM_LABEL[row.dimension] || row.dimension || '-',
      itemName: row.itemName ? `${row.itemCode ? row.itemCode + ' ' : ''}${row.itemName}` : (row.itemCode || '-'),
      submitter: row.submitterId || '-',
      submitDate: row.submitDate,
      summary: row.projectName || row.itemName || '-',
      // fileUrls 为源系统遗留字段（新提交走 fileObjectIds + governance BizFileRel 关联登记），
      // 当前 6 个 Controller 没有暴露"按 submitId 查关联文件"的只读端点，故大多数新数据此处为空，
      // 属已知能力缺口，非渲染 Bug
      files: parseFiles(row.fileUrls),
      formData: parseFormData(row.formData),
      status: 'pending',
      reviewNote: '',
      acting: false,
    }))
    total.value = r?.total ?? items.value.length
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

const pendingItems = computed(() => items.value.filter((i) => i.status === 'pending'))
const approvedItems = computed(() => items.value.filter((i) => i.status === 'approved'))
const rejectedItems = computed(() => items.value.filter((i) => i.status === 'rejected'))

const filteredItems = computed(() => {
  if (filterStatus.value === 'all') return items.value
  return items.value.filter((i) => i.status === filterStatus.value)
})

const getDimColor = (dim) => DIM_COLOR[dim] || '#64748b'

const getFileIcon = (file) => {
  if (file.endsWith('.pdf')) return '📕'
  if (file.endsWith('.xlsx') || file.endsWith('.xls')) return '📗'
  if (file.endsWith('.zip')) return '📦'
  if (file.endsWith('.png') || file.endsWith('.jpg')) return '🖼️'
  if (file.endsWith('.docx') || file.endsWith('.doc')) return '📄'
  return '📄'
}

const handleApprove = async (item) => {
  item.acting = true
  try {
    await approveSubmit(item.id, { feedback: item.reviewNote || undefined })
    item.status = 'approved'
    ElMessage.success(`✅ 已通过「${item.itemName}」并正式上报至组织部`)
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    item.acting = false
  }
}

const handleReject = async (item) => {
  if (!item.reviewNote.trim()) {
    ElMessage.warning('请填写退回原因')
    return
  }
  item.acting = true
  try {
    await rejectSubmit(item.id, { feedback: item.reviewNote })
    item.status = 'rejected'
    ElMessage.success(`↩ 已退回「${item.itemName}」给报送员修改`)
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    item.acting = false
  }
}

onMounted(reload)
</script>

<style scoped lang="scss">
.branch-review-container { padding: 0; }

.page-title { font-size: 22px; font-weight: 700; color: #1e293b; margin: 0 0 4px 0; }
.page-desc { font-size: 13px; color: #64748b; margin: 0 0 20px 0; }

/* Stats bar */
.stat-bar {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 13px;

  .stat-num {
    font-size: 24px;
    font-weight: 700;
  }

  .stat-label {
    color: #475569;
  }
}

.pending-stat { background: #fef9c3; .stat-num { color: #ca8a04; } }
.approved-stat { background: #dcfce7; .stat-num { color: #16a34a; } }
.rejected-stat { background: #fee2e2; .stat-num { color: #dc2626; } }

/* Filter */
.filter-bar {
  margin-bottom: 16px;
}

/* Review list */
.review-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: #94a3b8;
  font-size: 14px;
  background: #f8fafc;
  border-radius: 8px;
}

.review-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  overflow: hidden;
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  border-bottom: 1px solid #f1f5f9;
  background: #fafbfc;

  .card-left {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .dim-badge {
    font-size: 11px;
    color: #fff;
    padding: 2px 10px;
    border-radius: 4px;
    font-weight: 600;
  }

  .item-name {
    font-size: 14px;
    font-weight: 600;
    color: #1e293b;
  }
}

.card-body {
  padding: 16px 20px;

  .meta-row {
    display: flex;
    gap: 20px;
    margin-bottom: 10px;

    .meta-item {
      font-size: 12px;
      color: #64748b;
    }
  }

  .summary {
    font-size: 14px;
    color: #334155;
    line-height: 1.6;
    margin-bottom: 12px;
  }

  .files-row {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 12px;

    .file-tag {
      font-size: 12px;
      background: #f1f5f9;
      padding: 4px 10px;
      border-radius: 6px;
      color: #475569;
    }
  }

  .form-data {
    background: #eff6ff;
    border: 1px solid #bfdbfe;
    border-radius: 8px;
    padding: 12px;

    .data-row {
      font-size: 13px;
      margin-bottom: 4px;
      display: flex;
      gap: 4px;

      .data-key {
        color: #1e40af;
        font-weight: 600;
        white-space: nowrap;
      }

      .data-val {
        color: #1e293b;
      }
    }
  }
}

/* Actions */
.card-actions {
  padding: 12px 20px;
  border-top: 1px solid #f1f5f9;
  background: #fafbfc;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;

  .action-note {
    flex: 1;
    min-width: 200px;
  }

  .action-btns {
    display: flex;
    gap: 8px;
  }
}

/* Result bars */
.result-bar {
  padding: 10px 20px;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 16px;

  .result-note {
    font-weight: 400;
    font-size: 12px;
    color: #475569;
  }
}

.approved-bar {
  background: #dcfce7;
  color: #166534;
}

.rejected-bar {
  background: #fee2e2;
  color: #991b1b;
}

.pager { display: flex; justify-content: flex-end; padding: 12px 4px; }
</style>
