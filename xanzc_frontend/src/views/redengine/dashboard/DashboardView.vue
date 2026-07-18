<template>
  <div class="dashboard-container">
    <h1 class="page-title">工作台</h1>

    <!-- Stat Cards -->
    <div class="stat-grid">
      <div class="stat-card" style="background: #fee2e2; border-top: 3px solid #dc2626;">
        <div class="stat-label">总得分</div>
        <div class="stat-value" style="color: #dc2626;">{{ stats.totalScore }}</div>
        <div class="stat-sub">满分100</div>
      </div>
      <div class="stat-card" style="background: #fef9c3; border-top: 3px solid #ca8a04;">
        <div class="stat-label">排名</div>
        <div class="stat-value" style="color: #ca8a04;">{{ stats.rank }}</div>
        <div class="stat-sub">共9个支部</div>
      </div>
      <div class="stat-card" style="background: #fee2e2; border-top: 3px solid #dc2626;">
        <div class="stat-label">待办事项</div>
        <div class="stat-value" style="color: #dc2626;">{{ stats.todoCount }}</div>
        <div class="stat-sub">需尽快处理</div>
      </div>
      <div class="stat-card" style="background: #fef9c3; border-top: 3px solid #ca8a04;">
        <div class="stat-label">全员达标率</div>
        <div class="stat-value" style="color: #ca8a04;">{{ stats.passRate }}%</div>
        <div class="stat-sub">目标100%</div>
      </div>
    </div>

    <!-- Two Column: 待办事项 + 已办追踪 -->
    <div class="two-col-grid">
      <div class="section-card">
        <div class="section-title" style="border-left: 3px solid #dc2626; color: #dc2626;">待办事项</div>
        <ul class="todo-list">
          <li v-for="item in todoItems" :key="item.id" class="todo-item">
            <span class="todo-dot" :style="{ background: item.color }"></span>
            <span class="todo-text">{{ item.text }}</span>
            <span class="todo-time">{{ item.time }}</span>
          </li>
        </ul>
      </div>
      <div class="section-card">
        <div class="section-title" style="border-left: 3px solid #16a34a; color: #16a34a;">已办追踪</div>
        <ul class="todo-list">
          <li v-for="item in doneItems" :key="item.id" class="todo-item">
            <span class="done-icon">✅</span>
            <span class="todo-text">{{ item.text }}</span>
            <span class="todo-time">{{ item.time }}</span>
          </li>
        </ul>
      </div>
    </div>

    <!-- 全员营销积分 -->
    <div class="section-card" style="margin-top: 20px;">
      <div class="section-title" style="border-left: 3px solid #dc2626; color: #dc2626;">全员营销积分</div>
      <div class="member-scores">
        <div v-for="member in memberScores" :key="member.name" class="member-row">
          <span class="member-name">{{ member.name }}</span>
          <div class="member-bar-wrap">
            <div
              class="member-bar"
              :style="{ width: (member.score / 20 * 100) + '%', background: member.score >= 15 ? '#16a34a' : member.score >= 10 ? '#ca8a04' : '#dc2626' }"
            ></div>
          </div>
          <span class="member-score">{{ member.score }}/20</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
// 工作台：源系统本就是纯静态演示数据（无任何 API 调用），本次迁移原样保真移植，
// 仅做 F3 路由/F1 目录位置调整，不新增数据接入（YAGNI——后端目前也没有对应的
// "个人待办/已办追踪/全员营销积分"读接口）。
import { ref } from 'vue'

const stats = ref({
  totalScore: 78.5,
  rank: 3,
  todoCount: 5,
  passRate: 72,
})

const todoItems = ref([
  { id: 1, text: '党建联建维度材料上报（截止3月31日）', time: '剩余7天', color: '#dc2626' },
  { id: 2, text: '业务提升维度季度总结待提交', time: '剩余12天', color: '#dc2626' },
  { id: 3, text: '全员营销积分录入（张伟、李娜未达标）', time: '剩余5天', color: '#ca8a04' },
  { id: 4, text: '头雁工程活动照片补充上传', time: '剩余15天', color: '#ca8a04' },
  { id: 5, text: '督导检查整改报告', time: '已逾期', color: '#6b7280' },
])

const doneItems = ref([
  { id: 1, text: '2月份党建联建材料已审核通过', time: '03-15' },
  { id: 2, text: '业务提升2月数据已确认', time: '03-12' },
  { id: 3, text: '全员营销1月积分已公示', time: '03-10' },
  { id: 4, text: '头雁工程Q1活动方案已批复', time: '03-08' },
])

const memberScores = ref([
  { name: '王建国', score: 18 },
  { name: '李明华', score: 16 },
  { name: '张丽萍', score: 14 },
  { name: '陈志强', score: 12 },
  { name: '刘晓燕', score: 8 },
  { name: '赵德柱', score: 6 },
])
</script>

<style scoped lang="scss">
.dashboard-container {
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

  .two-col-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 20px;
  }

  .section-card {
    background: #fff;
    border-radius: 8px;
    padding: 20px;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);

    .section-title {
      font-size: 16px;
      font-weight: 600;
      padding-left: 12px;
      margin-bottom: 16px;
    }
  }

  .todo-list {
    list-style: none;
    padding: 0;
    margin: 0;

    .todo-item {
      display: flex;
      align-items: center;
      padding: 10px 0;
      border-bottom: 1px solid #f1f5f9;
      gap: 10px;

      &:last-child {
        border-bottom: none;
      }

      .todo-dot {
        width: 8px;
        height: 8px;
        border-radius: 50%;
        flex-shrink: 0;
      }

      .done-icon {
        font-size: 16px;
        flex-shrink: 0;
      }

      .todo-text {
        flex: 1;
        font-size: 14px;
        color: #334155;
      }

      .todo-time {
        font-size: 12px;
        color: #94a3b8;
        flex-shrink: 0;
      }
    }
  }

  .member-scores {
    .member-row {
      display: flex;
      align-items: center;
      padding: 8px 0;
      gap: 12px;

      .member-name {
        width: 70px;
        font-size: 14px;
        color: #334155;
        flex-shrink: 0;
      }

      .member-bar-wrap {
        flex: 1;
        height: 16px;
        background: #f1f5f9;
        border-radius: 8px;
        overflow: hidden;

        .member-bar {
          height: 100%;
          border-radius: 8px;
          transition: width 0.6s ease;
        }
      }

      .member-score {
        width: 50px;
        font-size: 13px;
        color: #64748b;
        text-align: right;
        flex-shrink: 0;
      }
    }
  }
}

@media (max-width: 768px) {
  .dashboard-container {
    .stat-grid {
      grid-template-columns: repeat(2, 1fr);
    }

    .two-col-grid {
      grid-template-columns: 1fr;
    }
  }
}
</style>
