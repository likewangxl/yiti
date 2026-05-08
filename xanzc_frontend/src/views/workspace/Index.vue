<template>
  <div class="workspace">
    <!-- 顶部欢迎条 -->
    <div class="hero">
      <div class="greet">{{ data.greet }}</div>
      <div class="desc">{{ data.desc }}</div>
    </div>

    <!-- 4 stat 卡 -->
    <div class="stats">
      <div v-for="s in data.stats" :key="s.label" class="stat">
        <div class="label">{{ s.label }}</div>
        <div class="value">{{ s.value }}</div>
        <div :class="['trend', s.trendType]">{{ s.trend }}</div>
      </div>
    </div>

    <div class="cols">
      <!-- 待办任务表 -->
      <div class="card-section">
        <div class="card-h">
          <div class="title">待办任务</div>
          <div class="hint">红黄绿状态显式展示</div>
          <a class="more">更多</a>
        </div>
        <el-table :data="data.todos" stripe size="small">
          <el-table-column prop="name" label="流程名称" min-width="200" />
          <el-table-column prop="node" label="当前节点" width="140" />
          <el-table-column label="SLA" width="90">
            <template #default="{ row }">
              <span class="sla-dot" :class="row.sla"></span>
              {{ slaText(row.sla) }}
            </template>
          </el-table-column>
          <el-table-column label="剩余时长" width="110">
            <template #default="{ row }">
              <span :class="['remain', row.sla]">{{ row.remain }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template #default>
              <el-button type="primary" link size="small">办理</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 通知 -->
      <div class="card-section notify">
        <div class="card-h">
          <div class="title">通知</div>
          <a class="more">全部</a>
        </div>
        <div v-for="(n, i) in data.notifications" :key="i" class="ntf">
          <span class="dot" :class="{ unread: !n.read }"></span>
          <div class="body">
            <div class="t">{{ n.title }}</div>
            <div class="m">{{ n.tag }} · {{ n.time }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 快捷入口 -->
    <div class="card-section">
      <div class="card-h"><div class="title">快捷入口</div></div>
      <div class="shortcuts">
        <div v-for="s in data.shortcuts" :key="s.label" class="sc">
          <div class="ico">{{ s.icon }}</div>
          <div class="lbl">{{ s.label }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { workspace as initial } from '@/mock';
import { getWorkspace } from '@/api/workspace';
const data = ref(initial);
const slaText = (s) => ({ normal: '正常', warn: '预警', overdue: '超时' }[s] || s);
onMounted(async () => {
  try { const r = await getWorkspace(); if (r) data.value = r; } catch (e) { /* noop */ }
});
</script>

<style lang="scss" scoped>
.workspace {
  display: flex; flex-direction: column; gap: 12px;
}

.hero {
  background: linear-gradient(135deg, $primary 0%, $primary-400 100%);
  color: #fff;
  border-radius: 4px;
  padding: 20px 24px;
  .greet { font-size: 20px; font-weight: 600; }
  .desc { font-size: 13px; opacity: .85; margin-top: 6px; }
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.cols {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: 12px;
}

.card-section {
  margin-bottom: 0;
  padding: 0;
}

.card-h {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 20px;
  border-bottom: 1px solid $border-1;
  .title { font-size: 14px; font-weight: 600; }
  .hint  { font-size: 12px; color: $text-3; }
  .more  { margin-left: auto; color: $primary; cursor: pointer; font-size: 12px; }
}

.sla-dot {
  display: inline-block; width: 8px; height: 8px; border-radius: 50%;
  margin-right: 6px; vertical-align: middle;
  &.normal  { background: $success; }
  &.warn    { background: $warning; }
  &.overdue { background: $danger; }
}
.remain.warn { color: $warning; font-weight: 500; }
.remain.overdue { color: $danger; font-weight: 600; }

.notify {
  .ntf {
    display: flex; gap: 12px; padding: 12px 20px;
    border-bottom: 1px solid $border-3;
    &:last-child { border-bottom: none; }
    .dot { width: 6px; height: 6px; border-radius: 50%; margin-top: 8px; background: $border-2; flex-shrink: 0; }
    .dot.unread { background: $danger; }
    .body { flex: 1; min-width: 0; }
    .t { font-size: 13px; color: $text-1; }
    .m { font-size: 11px; color: $text-3; margin-top: 4px; }
  }
}

.shortcuts {
  padding: 16px 20px;
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 12px;
  .sc {
    background: $bg-soft;
    border: 1px solid $border-1;
    border-radius: 4px;
    padding: 16px 8px;
    text-align: center;
    cursor: pointer;
    transition: .15s;
    &:hover { border-color: $primary-400; transform: translateY(-2px); box-shadow: 0 4px 12px rgba(30,91,186,.15); }
    .ico { font-size: 26px; margin-bottom: 6px; }
    .lbl { font-size: 12px; color: $text-2; }
  }
}
</style>
