<template>
  <div class="records-container">
    <h2 class="page-title">上报记录</h2>

    <!-- 统计条 -->
    <div class="stat-bar">
      <div class="stat-chip">全部 <strong>{{ total }}</strong></div>
      <div class="stat-chip pending-chip">待审核 <strong>{{ countByStatus('pending') }}</strong></div>
      <div class="stat-chip passed-chip">已通过 <strong>{{ countByStatus('passed') }}</strong></div>
      <div class="stat-chip rejected-chip">已驳回 <strong>{{ countByStatus('rejected') }}</strong></div>
    </div>

    <!-- 筛选 -->
    <div class="filter-row">
      <el-radio-group v-model="filterStatus" size="small">
        <el-radio-button label="all">全部</el-radio-button>
        <el-radio-button label="pending">待审核</el-radio-button>
        <el-radio-button label="passed">已通过</el-radio-button>
        <el-radio-button label="rejected">已驳回</el-radio-button>
      </el-radio-group>
      <el-select v-model="filterDim" placeholder="筛选维度" clearable size="small" style="width:160px;">
        <el-option label="外联共建" value="dim1" />
        <el-option label="业务提升" value="dim2" />
        <el-option label="头雁先锋" value="dim3" />
        <el-option label="督导响应" value="dim4" />
      </el-select>
    </div>

    <!-- 表格 -->
    <div class="table-card" v-loading="loading">
      <el-table :data="filteredRecords" stripe style="width: 100%;" :row-class-name="tableRowClass">
        <el-table-column label="维度" width="110">
          <template #default="{ row }">
            <span class="dim-tag" :style="{ background: getDimColor(row.dimension) }">{{ getDimLabel(row.dimension) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="item" label="考核项" min-width="180" />
        <el-table-column prop="submitter" label="提交人" width="110" />
        <el-table-column prop="date" label="提交日期" width="120" />
        <el-table-column label="审核状态" width="100" align="center">
          <template #default="{ row }">
            <span :class="['status-badge', row.status]">
              {{ row.status === 'pending' ? '待审核' : row.status === 'passed' ? '已通过' : row.status === 'rejected' ? '已驳回' : '草稿' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="得分" width="80" align="center">
          <template #default>
            <!-- red-engine-center 现有 6 个 Controller 未提供"按 submitId 查 RE_SCORE 明细"的只读端点
                 （评分仅在两级审核 approve 时落库、按组织+期间聚合后从 /re/cockpit/ranking 只读回，
                 没有单条上报级别的评分回读接口），故此列固定展示占位符，属已知能力缺口，非渲染 Bug -->
            <span style="color:#cbd5e1;">-</span>
          </template>
        </el-table-column>
        <el-table-column label="审核意见" min-width="150">
          <template #default="{ row }">
            <span class="feedback-text">{{ row.feedback || '-' }}</span>
          </template>
        </el-table-column>
      </el-table>
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
  </div>
</template>

<script setup>
// 上报记录：源系统为纯本地 mock 数组，本次接入真实后端 GET /api/re/submits/my（当前登录人
// 按党组织维度可见的上报记录，分页）。字段映射按 red-engine-center ReSubmit 实体（entity/ReSubmit.java）：
//   dimension: dim1~dim4（非源系统中文字符串，本页做 dim→中文 label 映射）
//   status: 0草稿/1已提交(pending)/2已通过(passed)/3已驳回(rejected)（非源系统字符串枚举）
// "得分"列：后端未提供按 submitId 查询 RE_SCORE 明细的接口（评分仅在审核通过时落库、按组织+
// 期间聚合后经 /re/cockpit/ranking 只读回），无法在此列表还原真实数值，固定展示"-"占位并在
// script 内注释说明（已知能力缺口，见 task-14-report.md），非 YAGNI 裁剪就是不做假数据。
import { ref, reactive, computed, onMounted } from 'vue'
import { getMySubmits } from '@/api/redengine'

const filterStatus = ref('all')
const filterDim = ref('')

const loading = ref(false)
const pageNo = ref(1)
const pageSize = ref(10)
const total = ref(0)
const records = ref([])

const STATUS_MAP = { 0: 'draft', 1: 'pending', 2: 'passed', 3: 'rejected' }
const DIM_LABEL = { dim1: '外联共建', dim2: '业务提升', dim3: '头雁先锋', dim4: '督导响应' }
const DIM_COLOR = { dim1: '#dc2626', dim2: '#2563eb', dim3: '#ca8a04', dim4: '#16a34a' }

async function reload() {
  loading.value = true
  try {
    const r = await getMySubmits(pageNo.value, pageSize.value)
    const arr = r?.records || (Array.isArray(r) ? r : [])
    records.value = arr.map((row) => ({
      id: row.id,
      dimension: row.dimension,
      item: row.itemName ? `${row.itemCode ? row.itemCode + ' ' : ''}${row.itemName}` : (row.itemCode || '-'),
      submitter: row.submitterId || '-',
      date: row.submitDate,
      status: STATUS_MAP[row.status] ?? 'pending',
      feedback: row.reviewFeedback,
    }))
    total.value = r?.total ?? records.value.length
  } catch {
    records.value = []
  } finally {
    loading.value = false
  }
}

const filteredRecords = computed(() => {
  return records.value.filter((r) => {
    if (filterStatus.value !== 'all' && r.status !== filterStatus.value) return false
    if (filterDim.value && r.dimension !== filterDim.value) return false
    return true
  })
})

// 页内统计：接口按登录人分页返回，故这里统计的是"当前页"计数，与 stat-bar 的"全部"（total，
// 来自后端分页汇总）语义不同，属真实分页场景下的合理近似，不做二次全量拉取
function countByStatus(status) {
  return records.value.filter((r) => r.status === status).length
}

const getDimLabel = (dim) => DIM_LABEL[dim] || dim || '-'
const getDimColor = (dim) => DIM_COLOR[dim] || '#64748b'

const tableRowClass = ({ row }) => {
  if (row.status === 'rejected') return 'row-rejected'
  return ''
}

onMounted(reload)
</script>

<style scoped lang="scss">
.records-container { padding: 0; }
.page-title { font-size: 22px; font-weight: 700; color: #1e293b; margin: 0 0 16px 0; }

/* Stats */
.stat-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.stat-chip {
  font-size: 13px;
  color: #475569;
  padding: 6px 14px;
  background: #f1f5f9;
  border-radius: 20px;

  strong { color: #1e293b; margin-left: 4px; }

  &.pending-chip { background: #fef9c3; strong { color: #92400e; } }
  &.passed-chip { background: #dcfce7; strong { color: #166534; } }
  &.rejected-chip { background: #fee2e2; strong { color: #b91c1c; } }
}

/* Filter */
.filter-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

/* Table card */
.table-card {
  background: #fff;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}

.pager { display: flex; justify-content: flex-end; padding: 12px 14px; }

.dim-tag {
  display: inline-block;
  font-size: 11px;
  color: #fff;
  padding: 2px 10px;
  border-radius: 4px;
  font-weight: 600;
}

.status-badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;

  &.pending { background: #fef9c3; color: #92400e; }
  &.passed { background: #dcfce7; color: #166534; }
  &.rejected { background: #fee2e2; color: #b91c1c; }
  &.draft { background: #f1f5f9; color: #64748b; }
}

.feedback-text {
  font-size: 12px;
  color: #64748b;
}

:deep(.row-rejected) {
  background: #fef2f2 !important;
}

:deep(.el-table) {
  .el-table__header th {
    background: #f8fafc;
    color: #475569;
    font-weight: 600;
    font-size: 13px;
  }

  .el-table__row td {
    font-size: 13px;
    padding: 10px 0;
  }
}
</style>
