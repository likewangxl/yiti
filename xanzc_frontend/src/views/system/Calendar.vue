<template>
  <main class="bp-crud calendar-page" aria-labelledby="calendar-page-title" :aria-busy="loading ? 'true' : 'false'">
    <header class="page-h">
      <PageTitle id="calendar-page-title"><span class="sub">{{ year }} 年 {{ month }} 月 · 仅可修改今日之后的日期。</span></PageTitle>
      <div class="actions action-group" role="group" aria-label="工作日历操作">
        <el-button @click="shiftMonth(-1)">上月</el-button>
        <el-button @click="goToday">返回本月</el-button>
        <el-button @click="shiftMonth(1)">下月</el-button>
        <el-button @click="downloadTemplate">下载模板</el-button>
        <el-upload :show-file-list="false" accept=".xlsx,.xls" :before-upload="onImport" class="upload-trigger">
          <el-button :loading="importing" :disabled="importing">导入日历</el-button>
        </el-upload>
        <el-button type="primary" :loading="initing" :disabled="initing" @click="onInit">年初初始化</el-button>
      </div>
    </header>

    <section class="card-section filter-bar calendar-legend" aria-label="工作日历说明">
      <p>当前周期：<strong>{{ year }} 年 {{ month }} 月</strong>。点击可编辑日期前会再次确认，导入和全年初始化会写入全年或批量数据。</p>
      <div class="legend-items" aria-label="日历状态图例">
        <span><i class="dot work" aria-hidden="true"></i>工作日</span>
        <span><i class="dot rest" aria-hidden="true"></i>休息日</span>
        <span><i class="dot adj" aria-hidden="true"></i>调休工作日</span>
      </div>
    </section>

    <section
      class="card-section data-panel calendar-panel"
      aria-label="月度工作日历"
      aria-labelledby="calendar-grid-heading"
      aria-describedby="calendar-table-state"
      :aria-busy="loading ? 'true' : 'false'"
    >
      <div class="toolbar">
        <div>
          <h2 id="calendar-grid-heading" class="section-title">{{ year }} 年 {{ month }} 月工作日安排</h2>
          <p class="hint">当日及历史日期保留只读状态，避免回溯影响已经运行的调度任务。</p>
        </div>
        <p id="calendar-table-state" class="table-state" role="status" aria-live="polite">
          {{ loading ? '工作日历加载中' : `${days.filter(day => !day.muted).length} 个日期已就绪` }}
        </p>
      </div>
      <p v-if="loadError" class="error-state" role="alert">{{ loadError }} <el-button link type="primary" @click="reload">重试</el-button></p>

      <div class="calendar-grid" role="group" aria-labelledby="calendar-grid-heading">
        <div class="week-head" aria-label="星期标题">
          <span v-for="day in ['一', '二', '三', '四', '五', '六', '日']" :key="day" class="week-cell">星期{{ day }}</span>
        </div>
        <div class="date-grid">
          <button
            v-for="(day, index) in days"
            :key="index"
            type="button"
            :class="['calendar-cell', day.kind, { today: day.today, muted: day.muted, past: day.past }]"
            :disabled="day.muted || day.past || isChanging(day.dateStr)"
            :aria-current="day.today ? 'date' : undefined"
            :aria-label="dayAriaLabel(day)"
            @click="onCellClick(day)"
          >
            <span class="day-number">{{ day.n }}</span>
            <span v-if="day.label" class="day-label">{{ day.label }}</span>
            <span v-if="day.tag" class="day-tag">{{ day.tag }}</span>
            <span v-else-if="day.today" class="day-tag">今日</span>
          </button>
        </div>
      </div>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getCalendar, setCalendarDay, initCalendarYear, importCalendar } from '@/api/system';

const now = new Date();
const year = ref(now.getFullYear());
const month = ref(now.getMonth() + 1);
const todayStr = computed(() => `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`);
const loading = ref(false);
const loadError = ref('');
const initing = ref(false);
const importing = ref(false);
const changingDates = ref(new Set());
const days = ref([]);

function shiftMonth(delta) {
  let nextMonth = month.value + delta;
  let nextYear = year.value;
  if (nextMonth < 1) { nextMonth = 12; nextYear -= 1; }
  if (nextMonth > 12) { nextMonth = 1; nextYear += 1; }
  year.value = nextYear;
  month.value = nextMonth;
  reload();
}
function goToday() {
  year.value = now.getFullYear();
  month.value = now.getMonth() + 1;
  reload();
}

function staticHolidays(targetYear, targetMonth) {
  const holidays = {
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
  const result = {};
  for (const [key, value] of Object.entries(holidays)) {
    const [holidayMonth, holidayDay] = key.split('-').map(Number);
    if (holidayMonth === targetMonth) result[holidayDay] = value;
  }
  return result;
}

function build(rawDays = {}) {
  const targetYear = year.value;
  const targetMonth = month.value;
  const first = new Date(targetYear, targetMonth - 1, 1);
  const lastDayOfPrev = new Date(targetYear, targetMonth - 1, 0).getDate();
  const lastDayOfCurrent = new Date(targetYear, targetMonth, 0).getDate();
  const firstDay = first.getDay() === 0 ? 7 : first.getDay();
  const holidays = staticHolidays(targetYear, targetMonth);
  const values = [];

  for (let index = firstDay - 1; index > 0; index -= 1) values.push({ n: lastDayOfPrev - index + 1, muted: true });
  for (let day = 1; day <= lastDayOfCurrent; day += 1) {
    const date = new Date(targetYear, targetMonth - 1, day);
    const dow = date.getDay();
    let kind = dow === 0 || dow === 6 ? 'rest' : 'work';
    let tag = '';
    let label = '';
    const holiday = holidays[day];
    if (holiday) ({ kind, tag, label } = { kind, tag, label, ...holiday });
    const backend = rawDays[day];
    if (backend) {
      if (backend.workday === 0 || backend.isWorkday === 0) kind = 'rest';
      else if ((backend.workday === 1 || backend.isWorkday === 1) && (dow === 0 || dow === 6)) kind = 'adj';
      else if (backend.workday === 1 || backend.isWorkday === 1) kind = 'work';
      if (backend.label || backend.remark) label = backend.label || backend.remark;
    }
    const isToday = targetYear === now.getFullYear() && targetMonth === now.getMonth() + 1 && day === now.getDate();
    const past = targetYear < now.getFullYear()
      || (targetYear === now.getFullYear() && targetMonth < now.getMonth() + 1)
      || (targetYear === now.getFullYear() && targetMonth === now.getMonth() + 1 && day <= now.getDate());
    const dateStr = `${targetYear}-${String(targetMonth).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    values.push({ n: day, kind, tag, label, today: isToday, past, dateStr });
  }
  while (values.length < 42) values.push({ n: values.length - lastDayOfCurrent - firstDay + 2, muted: true });
  days.value = values;
}

async function reload() {
  loading.value = true;
  loadError.value = '';
  build();
  try {
    const result = await getCalendar(year.value, month.value);
    const mapped = {};
    for (const day of Array.isArray(result) ? result : []) {
      const number = (day.day || day.date || '').slice(8, 10);
      if (number) mapped[parseInt(number, 10)] = day;
    }
    build(mapped);
  } catch (error) {
    loadError.value = `工作日历加载失败：${error?.message || '请稍后重试'}`;
  } finally {
    loading.value = false;
  }
}

const isChanging = (date) => date && changingDates.value.has(date);
function setChanging(date, value) {
  const next = new Set(changingDates.value);
  if (value) next.add(date);
  else next.delete(date);
  changingDates.value = next;
}
function kindLabel(day) {
  if (day.kind === 'rest') return '休息日';
  if (day.kind === 'adj') return '调休工作日';
  return '工作日';
}
function dayAriaLabel(day) {
  if (day.muted) return `非本月日期 ${day.n} 日`;
  const unavailable = day.past ? '，只读不可修改' : '，可点击修改';
  return `${year.value} 年 ${month.value} 月 ${day.n} 日，${kindLabel(day)}${day.label ? `，${day.label}` : ''}${unavailable}`;
}
async function onCellClick(day) {
  if (day.muted || day.past || isChanging(day.dateStr)) return;
  const nextLabel = day.kind === 'rest' ? '工作日' : '休息日';
  try {
    await ElMessageBox.confirm(
      `确认将 ${day.dateStr} 从「${kindLabel(day)}」切换为「${nextLabel}」？`,
      '切换工作日状态',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' }
    );
  } catch {
    return;
  }
  setChanging(day.dateStr, true);
  try {
    await setCalendarDay(day.dateStr, { isWorkday: day.kind === 'rest' ? 1 : 0, remark: '' });
    ElMessage.success('工作日状态已更新');
    await reload();
  } catch (error) {
    ElMessage.error(`切换失败：${error?.message || '请重试'}`);
  } finally {
    setChanging(day.dateStr, false);
  }
}

async function onInit() {
  if (initing.value) return;
  try {
    await ElMessageBox.confirm(
      `确认初始化 ${year.value} 年日历？将按法定节假日重置全年数据。`,
      '年初初始化',
      { confirmButtonText: '确认初始化', cancelButtonText: '取消', type: 'warning' }
    );
  } catch {
    return;
  }
  initing.value = true;
  try {
    await initCalendarYear(year.value);
    ElMessage.success(`${year.value} 年日历初始化成功`);
    await reload();
  } catch (error) {
    ElMessage.error(`初始化失败：${error?.message || '请重试'}`);
  } finally {
    initing.value = false;
  }
}

function downloadTemplate() {
  import('xlsx').then(XLSX => {
    const templateRows = [
      ['日期', '是否工作日', '备注'],
      [`${year.value}-01-01`, 0, '元旦'],
      [`${year.value}-01-02`, 1, ''],
      [`${year.value}-02-01`, 1, '调休上班'],
      [`${year.value}-05-01`, 0, '劳动节'],
      [`${year.value}-10-01`, 0, '国庆节']
    ];
    const sheet = XLSX.utils.aoa_to_sheet(templateRows);
    sheet['!cols'] = [{ wch: 16 }, { wch: 14 }, { wch: 20 }];
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, sheet, '工作日历');
    XLSX.writeFile(workbook, `工作日历导入模板_${year.value}年.xlsx`);
  });
}

async function onImport(file) {
  if (importing.value || !file) return false;
  try {
    await ElMessageBox.confirm(
      `确认导入工作日历文件「${file.name || '所选文件'}」？导入将批量覆盖文件中包含的日期配置。`,
      '确认导入日历',
      { confirmButtonText: '确认导入', cancelButtonText: '取消', type: 'warning' }
    );
  } catch {
    return false;
  }
  importing.value = true;
  try {
    await importCalendar(file);
    ElMessage.success('工作日历导入成功');
    await reload();
  } catch (error) {
    ElMessage.error(`导入失败：${error?.message || '请检查文件格式'}`);
  } finally {
    importing.value = false;
  }
  return false;
}

onMounted(reload);
</script>

<style lang="scss" scoped>
.calendar-legend { align-items: center; display: flex; gap: var(--space-4); justify-content: space-between; }
.calendar-legend p { color: var(--color-text); font-size: 14px; line-height: 22px; }
.legend-items { display: flex; flex-wrap: wrap; gap: var(--space-4); }
.legend-items span { color: var(--color-text); font-size: 12px; white-space: nowrap; }
.dot { border-radius: 50%; display: inline-block; height: 8px; margin-right: var(--space-1); width: 8px; }
.dot.work { background: var(--color-success-fg); }
.dot.rest { background: var(--color-danger-fg); }
.dot.adj { background: var(--color-warning-fg); }
.calendar-grid { min-width: 0; }
.week-head,
.date-grid { display: grid; gap: var(--space-1); grid-template-columns: repeat(7, minmax(0, 1fr)); }
.week-head { margin-bottom: var(--space-1); }
.week-cell { color: var(--color-text); font-size: 12px; font-weight: 600; padding: var(--space-2); text-align: center; }
.calendar-cell {
  align-items: flex-start;
  aspect-ratio: 1.45;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  color: var(--color-text-strong);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  font: inherit;
  gap: var(--space-1);
  justify-content: flex-start;
  padding: var(--space-2) var(--space-3);
  position: relative;
  text-align: left;
}
.calendar-cell:not(:disabled):hover { border-color: var(--color-brand-500); background: var(--color-brand-100); }
.calendar-cell:disabled { cursor: not-allowed; }
.calendar-cell.muted { background: var(--color-surface-soft); color: var(--color-text-muted); }
.calendar-cell.past { opacity: .72; }
.calendar-cell.rest { background: var(--color-danger-bg); border-color: var(--color-danger-fg); }
.calendar-cell.adj { background: var(--color-success-bg); border-color: var(--color-success-fg); }
.calendar-cell.today { border: 2px solid var(--color-brand-700); }
.day-number { font-size: 14px; font-variant-numeric: tabular-nums; font-weight: 600; }
.day-label { color: var(--color-text); font-size: 12px; line-height: 18px; }
.day-tag { bottom: var(--space-2); color: var(--color-text-muted); font-size: 12px; font-weight: 600; position: absolute; right: var(--space-2); }
.calendar-cell.rest .day-label,
.calendar-cell.rest .day-tag { color: var(--color-danger-fg); }
.calendar-cell.adj .day-label,
.calendar-cell.adj .day-tag { color: var(--color-success-fg); }
.error-state {
  background: var(--color-danger-bg);
  border-left: 3px solid var(--color-danger-fg);
  color: var(--color-danger-fg);
  font-size: 12px;
  line-height: 18px;
  margin-bottom: var(--space-3);
  padding: var(--space-2) var(--space-3);
}
</style>
