<template>
  <div class="cockpit-container" v-loading="loading">
    <h1 class="page-title">全局数据驾驶舱</h1>

    <!-- Stat Cards -->
    <div class="stat-grid">
      <div class="stat-card" style="background: #fee2e2; border-top: 3px solid #dc2626;">
        <div class="stat-label">全行均分</div>
        <div class="stat-value" style="color: #dc2626;">{{ avgScore }}</div>
        <div class="stat-sub">满分100</div>
      </div>
      <div class="stat-card" style="background: #fef2f2; border-top: 3px solid #991b1b;">
        <div class="stat-label">红牌支部</div>
        <div class="stat-value" style="color: #991b1b;">{{ redCardCount }}</div>
        <div class="stat-sub">低于60分</div>
      </div>
      <div class="stat-card" style="background: #dbeafe; border-top: 3px solid #2563eb;">
        <div class="stat-label">待审工单</div>
        <div class="stat-value" style="color: #2563eb;">{{ pendingCount }}</div>
        <div class="stat-sub">需尽快处理</div>
      </div>
      <div class="stat-card" style="background: #dcfce7; border-top: 3px solid #16a34a;">
        <div class="stat-label">最高得分</div>
        <div class="stat-value" style="color: #16a34a;">{{ maxScore }}</div>
        <div class="stat-sub">{{ topBranch }}</div>
      </div>
    </div>

    <!-- Bar Chart -->
    <div class="section-card">
      <div class="section-title" style="border-left: 3px solid #dc2626; color: #1e293b;">各支部综合得分</div>
      <div class="bar-chart">
        <!-- Chart body: y-axis + plot area -->
        <div class="chart-body">
          <div class="y-axis">
            <span style="top: 0;">100</span>
            <span style="top: 20%;">80</span>
            <span style="top: 40%;">60</span>
            <span style="top: 60%;">40</span>
            <span style="top: 80%;">20</span>
            <span style="top: 100%;">0</span>
          </div>
          <div class="plot-area">
            <!-- Grid lines -->
            <div class="grid-line" style="top: 0;"></div>
            <div class="grid-line" style="top: 20%;"></div>
            <div class="grid-line" style="top: 40%;"></div>
            <div class="grid-line" style="top: 60%;"></div>
            <div class="grid-line" style="top: 80%;"></div>
            <div class="grid-line" style="top: 100%;"></div>
            <!-- Red dashed line at 60 = top 40% -->
            <div class="red-line" style="top: 40%;">
              <span class="red-line-label">一票否决线 60</span>
            </div>
            <!-- Bar columns -->
            <div class="bars-row">
              <div v-for="branch in sortedBranches" :key="branch.id" class="bar-col">
                <div class="bar-value">{{ branch.score }}</div>
                <div class="bar" :style="{ height: (branch.score / 100 * 250) + 'px', background: getBarColor(branch.score) }"></div>
              </div>
            </div>
          </div>
        </div>
        <!-- X-axis labels -->
        <div class="x-labels">
          <div class="x-spacer"></div>
          <div class="x-row">
            <div v-for="branch in sortedBranches" :key="branch.id" class="x-label">
              {{ branch.name.replace('党支部', '') }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Red Card Warning -->
    <div v-if="redCardBranches.length > 0" class="section-card warning-card">
      <div class="warning-header">🔴 红线预警 · 一票否决</div>
      <div class="warning-list">
        <div v-for="branch in redCardBranches" :key="branch.id" class="warning-item">
          <span class="warning-name">{{ branch.name }}</span>
          <span class="warning-score">{{ branch.score }}分</span>
          <span class="warning-tag">低于60分红线</span>
        </div>
      </div>
    </div>

    <!-- 逾期扣分巡检 -->
    <div class="section-card" style="margin-top: 20px;">
      <div class="section-title" style="border-left: 3px solid #f59e0b; color: #1e293b;">
        逾期扣分巡检
        <el-button type="primary" size="small" style="margin-left: 16px;" @click="handleRecheck">前往预警池处理</el-button>
      </div>
      <el-table :data="overdueBranches" stripe style="width: 100%; margin-top: 12px;">
        <el-table-column prop="name" label="支部名称" />
        <el-table-column prop="overdue" label="逾期次数" width="100">
          <template #default="{ row }">
            <el-tag type="danger" size="small">{{ row.overdue }}次</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="预估扣分" width="100">
          <template #default="{ row }">
            <span style="color: #dc2626; font-weight: 600;">-{{ row.overdue * 5 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default>
            <el-tag type="warning" size="small">待复核</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Full Ranking Table -->
    <div class="section-card" style="margin-top: 20px;">
      <div class="section-title" style="border-left: 3px solid #dc2626; color: #1e293b;">全部支部排名</div>
      <el-table :data="rankedBranches" stripe style="width: 100%; margin-top: 12px;">
        <el-table-column label="排名" width="70" align="center">
          <template #default="{ $index }">
            <span v-if="$index === 0" class="medal">🥇</span>
            <span v-else-if="$index === 1" class="medal">🥈</span>
            <span v-else-if="$index === 2" class="medal">🥉</span>
            <span v-else class="rank-num">{{ $index + 1 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="支部名称" />
        <el-table-column label="党建联建(35)" width="120" align="center">
          <template #default="{ row }">{{ row.d1 ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="业务提升(50)" width="120" align="center">
          <template #default="{ row }">{{ row.d2 ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="头雁先锋(10)" width="120" align="center">
          <template #default="{ row }">{{ row.d3 ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="督导总结(5)" width="110" align="center">
          <template #default="{ row }">{{ row.d4 ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="总分" width="80" align="center">
          <template #default="{ row }">
            <span style="font-weight: 700; color: #1e293b;">{{ row.score }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.score >= 80" type="success" size="small">优秀</el-tag>
            <el-tag v-else-if="row.score >= 70" type="warning" size="small">良好</el-tag>
            <el-tag v-else-if="row.score >= 60" type="info" size="small">合格</el-tag>
            <el-tag v-else type="danger" size="small">红牌</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
// 全局数据驾驶舱。
// F1：script 内 API 全部改走 @/api/redengine（getCockpitOverview/getRanking/getOverdueList/
// getOrgTree），F3：路由跳转补 /redengine 前缀。
// 已知能力缺口（先读 red-engine-center DTO 确认，非猜测）：
//   - ReRankingItemDTO 只有 orgId/finalScore/period，没有各支部"党建联建/业务提升/头雁先锋/
//     督导总结"四维度分项得分——后端目前没有按组织聚合的分项评分查询接口（分项评分落在 RE_SCORE
//     按 itemCode 记录，需要额外聚合接口才能还原，本任务范围内不新增）。排名表 d1~d4 四列固定
//     显示"-"占位，非渲染 Bug。
//   - ReRankingItemDTO/ReOverdueItemDTO 均只有 orgId，没有组织名称，本页额外拉一次 getOrgTree()
//     在前端拍平建 orgId→orgName 映射表用于展示（对照 docs/superpowers/sql 种子确认
//     P_RE_ORG_TREE 已授权给全部 4 个党建角色 + SYS_ADMIN，非管理员也能调用不会 403）。
//   - "逾期扣分巡检"表的"预估扣分"列按后端 ReOverdueExecuteReqDTO 文档的默认扣分基准（未显式指定
//     deductionPoints 时兜底5分/次）估算展示，实际执行值以红黄牌预警池页面执行时手填为准。
//   - "一键复核"（源系统 handleRecheck 也只是纯前端 ElMessage 装饰，无任何后端调用）：后端没有
//     "批量复核"端点，改为跳转到红黄牌预警池页（真实的执行扣分入口在那里，含必填原因），
//     不新造一个不存在的批量接口（YAGNI）。
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getCockpitOverview, getRanking, getOverdueList, getOrgTree } from '@/api/redengine'

const router = useRouter()
const loading = ref(false)

const branches = ref([])
const pendingCount = ref(0)
const overdueBranches = ref([])

// 党组织树拍平成 orgId → orgName 映射，供排名/逾期列表展示组织名称
function flattenOrgTree(nodes, map) {
  for (const n of nodes || []) {
    map.set(n.id, n.orgName)
    if (n.children && n.children.length) flattenOrgTree(n.children, map)
  }
}

async function reload() {
  loading.value = true
  try {
    const [overview, ranking, overdueList, orgTree] = await Promise.all([
      getCockpitOverview(),
      getRanking(),
      getOverdueList(),
      getOrgTree(),
    ])

    const orgNameMap = new Map()
    flattenOrgTree(orgTree, orgNameMap)

    pendingCount.value = overview?.pendingCount ?? 0

    branches.value = (ranking || []).map((row) => ({
      id: row.orgId,
      name: orgNameMap.get(row.orgId) || `组织#${row.orgId}`,
      score: Number(row.finalScore ?? 0),
    }))

    const overdueCountByOrg = new Map()
    for (const row of overdueList || []) {
      overdueCountByOrg.set(row.orgId, (overdueCountByOrg.get(row.orgId) || 0) + 1)
    }
    overdueBranches.value = Array.from(overdueCountByOrg.entries()).map(([orgId, count]) => ({
      name: orgNameMap.get(orgId) || `组织#${orgId}`,
      overdue: count,
    }))
  } catch {
    branches.value = []
    overdueBranches.value = []
  } finally {
    loading.value = false
  }
}

const avgScore = computed(() => {
  if (branches.value.length === 0) return '0.0'
  const sum = branches.value.reduce((acc, b) => acc + b.score, 0)
  return (sum / branches.value.length).toFixed(1)
})

const redCardBranches = computed(() =>
  branches.value.filter((b) => b.score < 60).sort((a, b) => a.score - b.score)
)

const redCardCount = computed(() => redCardBranches.value.length)

const maxScore = computed(() => (branches.value.length ? Math.max(...branches.value.map((b) => b.score)) : 0))

const topBranch = computed(() => {
  if (branches.value.length === 0) return '-'
  const top = branches.value.reduce((a, b) => (a.score > b.score ? a : b))
  return top.name.replace('党支部', '')
})

const sortedBranches = computed(() =>
  [...branches.value].sort((a, b) => b.score - a.score)
)

const rankedBranches = computed(() =>
  [...branches.value].sort((a, b) => b.score - a.score)
)

const getBarColor = (score) => {
  if (score >= 80) return '#16a34a'
  if (score >= 70) return '#ca8a04'
  if (score >= 60) return '#ea580c'
  return '#dc2626'
}

const handleRecheck = () => {
  router.push('/redengine/warning')
}

onMounted(reload)
</script>

<style scoped lang="scss">
.cockpit-container {
  padding: 0;

  .page-title {
    font-size: 28px;
    font-weight: 700;
    margin-bottom: 24px;
    color: #1e293b;
  }

  .stat-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 16px;
    margin-bottom: 24px;

    .stat-card {
      border-radius: 8px;
      padding: 20px;
      transition: box-shadow 0.2s;

      &:hover {
        box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
      }

      .stat-label {
        font-size: 12px;
        color: #64748b;
        margin-bottom: 8px;
      }

      .stat-value {
        font-size: 32px;
        font-weight: 700;
        line-height: 1.2;
        margin-bottom: 4px;
      }

      .stat-sub {
        font-size: 12px;
        color: #94a3b8;
      }
    }
  }

  .section-card {
    background: #fff;
    border-radius: 8px;
    padding: 20px;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
    margin-bottom: 0;

    .section-title {
      font-size: 16px;
      font-weight: 600;
      padding-left: 12px;
      margin-bottom: 16px;
      display: flex;
      align-items: center;
    }
  }

  .warning-card {
    margin-top: 20px;
    border: 2px solid #fecaca;
    background: #fff5f5;

    .warning-header {
      font-size: 16px;
      font-weight: 700;
      color: #991b1b;
      margin-bottom: 12px;
    }

    .warning-list {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .warning-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 12px;
      background: #fef2f2;
      border-radius: 6px;
      border-left: 3px solid #dc2626;

      .warning-name {
        font-size: 14px;
        font-weight: 600;
        color: #1e293b;
        min-width: 140px;
      }

      .warning-score {
        font-size: 20px;
        font-weight: 700;
        color: #dc2626;
      }

      .warning-tag {
        font-size: 12px;
        color: #991b1b;
        background: #fecaca;
        padding: 2px 8px;
        border-radius: 4px;
      }
    }
  }

  /* Bar Chart */
  .bar-chart {
    .chart-body {
      display: flex;
      height: 250px;
      position: relative;
    }

    .y-axis {
      width: 36px;
      position: relative;
      padding-right: 8px;
      flex-shrink: 0;

      span {
        position: absolute;
        right: 8px;
        font-size: 11px;
        color: #94a3b8;
        transform: translateY(-50%);
      }
    }

    .plot-area {
      flex: 1;
      position: relative;
      border-left: 1px solid #e2e8f0;
      border-bottom: 1px solid #e2e8f0;
    }

    .grid-line {
      position: absolute;
      left: 0;
      right: 0;
      height: 0;
      border-top: 1px solid #f1f5f9;
      pointer-events: none;
    }

    .red-line {
      position: absolute;
      left: 0;
      right: 0;
      height: 0;
      border-top: 2px dashed #dc2626;
      pointer-events: none;
      z-index: 2;

      .red-line-label {
        position: absolute;
        left: 4px;
        top: 2px;
        font-size: 10px;
        color: #dc2626;
        font-weight: 600;
        background: rgba(255,255,255,0.85);
        padding: 0 4px;
        border-radius: 2px;
      }
    }

    .bars-row {
      position: absolute;
      bottom: 0;
      left: 0;
      right: 0;
      top: 0;
      display: flex;
      align-items: flex-end;
      justify-content: space-around;
      padding: 0 8px;
    }

    .bar-col {
      display: flex;
      flex-direction: column;
      align-items: center;
      flex: 1;
      z-index: 1;

      .bar-value {
        font-size: 11px;
        font-weight: 700;
        color: #334155;
        margin-bottom: 3px;
      }

      .bar {
        width: 32px;
        border-radius: 4px 4px 0 0;
        transition: height 0.8s ease;
        min-height: 2px;
      }
    }

    .x-labels {
      display: flex;
      margin-top: 6px;

      .x-spacer {
        width: 36px;
        flex-shrink: 0;
      }

      .x-row {
        flex: 1;
        display: flex;
        justify-content: space-around;
        padding: 0 8px;
      }

      .x-label {
        flex: 1;
        text-align: center;
        font-size: 11px;
        color: #64748b;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
    }
  }

  /* Ranking table */
  .medal {
    font-size: 20px;
  }

  .rank-num {
    font-size: 14px;
    color: #64748b;
    font-weight: 600;
  }
}

@media (max-width: 768px) {
  .cockpit-container {
    .stat-grid {
      grid-template-columns: repeat(2, 1fr);
    }
  }
}
</style>
