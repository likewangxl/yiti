<template>
  <div>
    <div class="page-h">
      <h1>工作日历 · {{ year }}年{{ month }}月</h1>
      <div class="actions">
        <el-button @click="shiftMonth(-1)">‹ 上月</el-button>
        <el-button @click="goToday">今天</el-button>
        <el-button @click="shiftMonth(1)">下月 ›</el-button>
        <el-button>📥 导入</el-button>
        <el-button type="primary">年初初始化</el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" style="margin-bottom:12px"
      :title="`点击格子切换：工作日 → 休息日 → 调休工作 → 工作日。仅允许修改未来日期（今日 ${todayStr} 之后）。`">
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
import { ref, computed, onMounted } from 'vue';
import { getCalendar } from '@/api/system';

// === 当前显示的年月（默认今天所在月） ===
const now = new Date();
const year = ref(now.getFullYear());
const month = ref(now.getMonth() + 1);

const todayStr = computed(() => {
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${m}-${d}`;
});

function shiftMonth(delta) {
  let m = month.value + delta;
  let y = year.value;
  if (m < 1) { m = 12; y -= 1; }
  if (m > 12) { m = 1; y += 1; }
  year.value = y; month.value = m;
  reload();
}
function goToday() {
  year.value = now.getFullYear();
  month.value = now.getMonth() + 1;
  reload();
}

// === 静态法定假日表（可被后端 getCalendar 覆盖）===
function staticHolidays(y, m) {
  // 月-日 → { kind, tag, label }
  const HOLIDAYS = {
    '01-01': { kind: 'rest', tag: '假', label: '元旦' },
    '04-04': { kind: 'rest', tag: '假', label: '清明' },
    '04-05': { kind: 'rest', tag: '假' },
    '04-06': { kind: 'rest', tag: '假' },
    '05-01': { kind: 'rest', tag: '假', label: '劳动' },
    '05-02': { kind: 'rest', tag: '假' },
    '05-03': { kind: 'rest', tag: '假' },
    '06-22': { kind: 'rest', tag: '假', label: '端午' },
    '10-01': { kind: 'rest', tag: '假', label: '国庆' },
    '10-02': { kind: 'rest', tag: '假' },
    '10-03': { kind: 'rest', tag: '假' }
  };
  const out = {};
  for (const [k, v] of Object.entries(HOLIDAYS)) {
    const [hm, hd] = k.split('-').map(Number);
    if (hm === m) out[hd] = v;
  }
  return out;
}

// === 构建月历格子数组 ===
const days = ref([]);
function build(rawDays = {}) {
  const y = year.value, m = month.value;
  const first = new Date(y, m - 1, 1);
  const lastDayOfPrev = new Date(y, m - 1, 0).getDate();
  const lastDayOfCur = new Date(y, m, 0).getDate();
  // 周一 = 1, 周日 = 0；将周日映射成 7 让周一为首列
  const firstDow = first.getDay() === 0 ? 7 : first.getDay();

  const todayY = now.getFullYear(), todayM = now.getMonth() + 1, todayD = now.getDate();
  const isCurMonth = (y === todayY && m === todayM);

  const holidays = staticHolidays(y, m);
  const arr = [];

  // 上月尾
  for (let i = firstDow - 1; i > 0; i--) {
    arr.push({ n: lastDayOfPrev - i + 1, muted: true });
  }
  // 当月
  for (let n = 1; n <= lastDayOfCur; n++) {
    const dt = new Date(y, m - 1, n);
    const dow = dt.getDay(); // 0 周日 / 6 周六
    let kind = 'work', tag = '', label = '';
    // 周末默认 rest
    if (dow === 0 || dow === 6) { kind = 'rest'; }
    // 法定节假日覆盖
    const hol = holidays[n];
    if (hol) Object.assign({ kind, tag, label }, hol), ({ kind, tag, label } = { ...{ kind, tag, label }, ...hol });
    // 后端覆盖（如有数据）
    const back = rawDays[n];
    if (back) {
      if (back.workday === 0 || back.isWorkday === 0) kind = 'rest';
      else if (back.workday === 1 && (dow === 0 || dow === 6)) kind = 'adj';
      if (back.label) label = back.label;
    }
    arr.push({ n, kind, tag, label, today: isCurMonth && n === todayD });
  }
  // 下月头：补齐到 6 行 × 7 列 = 42 格（确保布局稳定）
  while (arr.length < 42) {
    arr.push({ n: arr.length - lastDayOfCur - firstDow + 2, muted: true });
  }
  days.value = arr;
}

async function reload() {
  // 先按静态规则渲染（即时）
  build();
  // 异步拉后端，覆盖
  try {
    const r = await getCalendar(year.value, month.value);
    if (Array.isArray(r) && r.length) {
      const map = {};
      for (const d of r) {
        // 后端字段约定：{ day: 'YYYY-MM-DD', isWorkday: 0|1, label?: string }
        const day = (d.day || d.date || '').slice(8, 10);
        if (day) map[parseInt(day, 10)] = d;
      }
      build(map);
    }
  } catch {}
}

onMounted(reload);
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
