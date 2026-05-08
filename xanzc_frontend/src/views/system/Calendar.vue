<template>
  <div>
    <div class="page-h">
      <h1>工作日历 · 2026年4月</h1>
      <div class="actions">
        <el-button>‹ 上月</el-button>
        <el-button>下月 ›</el-button>
        <el-button>📥 导入</el-button>
        <el-button type="primary">年初初始化</el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" style="margin-bottom:12px"
      title="点击格子切换：工作日 → 休息日 → 调休工作 → 工作日。仅允许修改未来日期（今日 4/23 之后）。">
      <template #default>
        <span style="margin-right:12px"><i class="dot work"></i> 工作日</span>
        <span style="margin-right:12px"><i class="dot rest"></i> 休息日</span>
        <span><i class="dot adj"></i> 调休工作</span>
      </template>
    </el-alert>

    <div class="card-section cal">
      <div class="hd">
        <div v-for="d in ['一','二','三','四','五','六','日']" :key="d" class="hcell">{{ d }}</div>
      </div>
      <div class="grid">
        <div v-for="(d, i) in days" :key="i" :class="['cell', d.kind, { today: d.today, muted: d.muted }]">
          <div class="num">{{ d.n }}</div>
          <div v-if="d.label" class="lbl">{{ d.label }}</div>
          <div v-if="d.tag" class="tag">{{ d.tag }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
function build() {
  // 只是演示——4 月 1 是周三，整月按截图布局
  const arr = [];
  // 上月尾
  for (let n of [29,30]) arr.push({ n, muted: true });
  for (let n = 1; n <= 30; n++) {
    const dow = (n + 1) % 7; // 1=周三起算
    let kind = 'work';
    let tag = '';
    let label = '';
    if (n === 4) { kind = 'rest'; tag = '假'; label = '清明'; }
    if (n === 5) { kind = 'rest'; tag = '假'; }
    if (n === 6) { kind = 'rest'; tag = '假'; }
    if (n === 7) { kind = 'adj'; label = '调休'; }
    if (n === 11 || n === 12 || n === 18 || n === 19 || n === 25 || n === 26) { kind = 'rest'; tag = '假'; }
    arr.push({ n, kind, tag, label, today: n === 23 });
  }
  // 下月头
  for (let n of [1,2,3]) arr.push({ n, muted: true });
  return arr;
}
const days = build();
</script>

<style lang="scss" scoped>
.dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; vertical-align: middle; margin-right: 4px;
  &.work { background: $success; }
  &.rest { background: $danger; }
  &.adj  { background: $warning; }
}
.cal { padding: 16px 20px; }
.hd { display: grid; grid-template-columns: repeat(7, 1fr); gap: 4px; padding-bottom: 4px;
  .hcell { text-align: center; font-size: 13px; color: $text-3; padding: 6px 0; }
}
.grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 4px; }
.cell { aspect-ratio: 1.4; background: #fff; border: 1px solid $border-1; border-radius: 4px; padding: 6px 8px; font-size: 12px;
  cursor: pointer; position: relative; transition: .15s;
  &:hover { border-color: $primary-400; }
  &.muted { color: $text-4; background: $bg-soft; }
  &.rest  { background: #fef2f2; border-color: #fca5a5; }
  &.adj   { background: #ecfdf5; border-color: #a7f3d0; }
  &.today { border-color: $primary; border-width: 2px; }
  .num { font-weight: 500; }
  .lbl { color: $danger; font-size: 11px; margin-top: 2px; }
  .tag { position: absolute; bottom: 4px; right: 6px; font-size: 11px; color: $danger; font-weight: 600; }
  &.today::after { content: '今日'; position: absolute; bottom: 4px; right: 6px; font-size: 11px; color: $primary; font-weight: 600; }
  &.today.rest::after, &.today.adj::after { display: none; }
}
</style>
