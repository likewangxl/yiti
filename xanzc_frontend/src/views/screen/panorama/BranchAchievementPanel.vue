<template>
  <section
    class="branch-achievement-panel"
    data-testid="branch-achievement-panel"
    :aria-label="title"
    :aria-busy="loading"
  >
    <header class="branch-achievement__header">
      <div class="branch-achievement__heading">
        <span class="branch-achievement__eyebrow">Achievement overview</span>
        <h2>{{ title }}</h2>
        <p>按已绑定考核指标展示实际、目标与完成状态</p>
      </div>
      <label class="branch-achievement__status-filter">
        <span>状态</span>
        <select data-testid="branch-achievement-status" v-model="statusFilter" aria-label="指标状态筛选">
          <option value="all">全部</option>
          <option value="incomplete">未完成</option>
          <option value="completed">已完成</option>
          <option value="missing">数据不足</option>
        </select>
      </label>
    </header>

    <div class="branch-achievement__summary" data-testid="branch-achievement-summary" aria-label="指标完成情况统计">
      <article v-for="item in summaryCards" :key="item.key" class="branch-achievement__summary-card" :class="`is-${item.key}`">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </article>
      <p class="branch-achievement__summary-note">达标率按有有效目标实际口径指标数计算</p>
    </div>

    <div class="branch-achievement__filters" aria-label="指标筛选与排序">
      <label class="branch-achievement__search">
        <span>搜索</span>
        <input
          data-testid="branch-achievement-search"
          v-model="searchText"
          type="search"
          placeholder="指标名称或负责人"
          aria-label="搜索指标名称或负责人"
        >
      </label>
      <label>
        <span>分类</span>
        <select data-testid="branch-achievement-category" v-model="categoryFilter" aria-label="指标分类筛选">
          <option value="">全部分类</option>
          <option v-for="category in categories" :key="category" :value="category">{{ category }}</option>
        </select>
      </label>
      <label>
        <span>排序</span>
        <select data-testid="branch-achievement-sort" v-model="sortMode" aria-label="指标排序">
          <option value="rate-asc">完成率升序</option>
          <option value="rate-desc">完成率降序</option>
          <option value="name">名称</option>
        </select>
      </label>
      <span class="branch-achievement__filter-count">过滤后 {{ filteredItems.length }} 项</span>
    </div>

    <div v-if="pagedItems.length" class="branch-achievement__table-wrap" tabindex="0" role="region" aria-label="指标完成情况表">
      <table class="branch-achievement__table">
        <thead>
          <tr>
            <th scope="col">指标名称</th>
            <th scope="col">负责人</th>
            <th scope="col">{{ actualLabel }}</th>
            <th scope="col">目标</th>
            <th scope="col">完成率</th>
            <th scope="col">缺口</th>
            <th scope="col">日期</th>
            <th scope="col">状态</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in pagedItems"
            :key="item.id"
            data-testid="branch-achievement-row"
            :data-item-id="item.id"
            :data-state="item.state"
          >
            <th scope="row">
              <span class="branch-achievement__item-label">{{ textOrDash(item.label) }}</span>
              <small v-if="item.category" class="branch-achievement__item-category">{{ item.category }}</small>
            </th>
            <td>{{ textOrDash(item.owner) }}</td>
            <td>
              {{ formatMetric(item.actual) }} <small>{{ unitText(item) }}</small>
              <small v-if="hasSourceBreakdown(item)" class="branch-achievement__source-note">原值 {{ formatMetric(item.sourceActual) }} · 基础值 {{ formatMetric(item.base) }}</small>
            </td>
            <td>{{ formatMetric(item.target) }} <small>{{ unitText(item) }}</small></td>
            <td>
              <div class="branch-achievement__rate" :class="{ 'is-missing': item.rate == null }">
                <span class="branch-achievement__rate-track" aria-hidden="true"><i :style="{ width: `${progressWidth(item.rate)}%` }"></i></span>
                <strong>{{ formatRate(item.rate) }}</strong>
              </div>
            </td>
            <td :class="{ 'is-negative': isNegative(item.gap) }">{{ formatMetric(item.gap) }} <small>{{ unitText(item) }}</small></td>
            <td>{{ textOrDash(item.date) }}</td>
            <td>
              <span class="branch-achievement__state" :class="`is-${item.state}`">{{ stateText(item.state) }}</span>
              <small v-if="item.state === 'missing'" class="branch-achievement__missing-reason">{{ item.missingReason || '来源数据不足' }}</small>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-else class="branch-achievement__empty" role="status">
      <strong>{{ emptyTitle }}</strong>
      <span v-if="emptyDetail">{{ emptyDetail }}</span>
    </div>

    <footer class="branch-achievement__footer">
      <span data-testid="branch-achievement-page">{{ filteredItems.length }} 项 · {{ currentPage }} / {{ pageCount }}</span>
      <div class="branch-achievement__pagination" aria-label="指标分页">
        <button
          type="button"
          data-testid="branch-achievement-prev"
          :disabled="currentPage <= 1"
          aria-label="上一页"
          @click="goToPage(currentPage - 1)"
        >上一页</button>
        <button
          type="button"
          data-testid="branch-achievement-next"
          :disabled="currentPage >= pageCount"
          aria-label="下一页"
          @click="goToPage(currentPage + 1)"
        >下一页</button>
      </div>
    </footer>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { buildBranchAchievementModel } from './branchAchievementModel.js';

const props = defineProps({
  targets: { type: Array, default: () => [] },
  title: { type: String, default: '指标完成情况' },
  actualLabel: { type: String, default: '实际' },
  loading: { type: Boolean, default: false }
});

const PAGE_SIZE = 10;
const statusFilter = ref('incomplete');
const categoryFilter = ref('');
const searchText = ref('');
const sortMode = ref('rate-asc');
const currentPage = ref(1);

const achievement = computed(() => buildBranchAchievementModel(Array.isArray(props.targets) ? props.targets : []));
const allItems = computed(() => Array.isArray(achievement.value?.items) ? achievement.value.items : []);
const summary = computed(() => ({
  total: finiteOr(achievement.value?.total, allItems.value.length),
  completed: finiteOr(achievement.value?.completed, allItems.value.filter(item => item.state === 'completed').length),
  incomplete: finiteOr(achievement.value?.incomplete, allItems.value.filter(item => item.state === 'incomplete').length),
  missing: finiteOr(achievement.value?.missing, allItems.value.filter(item => item.state === 'missing').length),
  attainmentRate: validNumber(achievement.value?.attainmentRate)
}));

const categories = computed(() => [...new Set(allItems.value.map(item => String(item.category || '').trim()).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'zh-CN')));
const filteredItems = computed(() => {
  const query = searchText.value.trim().toLocaleLowerCase();
  const result = allItems.value.filter(item => {
    if (statusFilter.value !== 'all' && item.state !== statusFilter.value) return false;
    if (categoryFilter.value && item.category !== categoryFilter.value) return false;
    if (!query) return true;
    return [item.label, item.owner].some(value => String(value || '').toLocaleLowerCase().includes(query));
  });
  return result.sort(compareItems);
});
const pageCount = computed(() => Math.max(1, Math.ceil(filteredItems.value.length / PAGE_SIZE)));
const pagedItems = computed(() => {
  const start = (currentPage.value - 1) * PAGE_SIZE;
  return filteredItems.value.slice(start, start + PAGE_SIZE);
});

const summaryCards = computed(() => [
  { key: 'total', label: '指标总数', value: summary.value.total },
  { key: 'completed', label: '已完成', value: summary.value.completed },
  { key: 'incomplete', label: '未完成', value: summary.value.incomplete },
  { key: 'missing', label: '数据不足', value: summary.value.missing },
  { key: 'attainment-rate', label: '达标率', value: formatRate(summary.value.attainmentRate) }
]);

const emptyTitle = computed(() => {
  if (!allItems.value.length) return '暂无已绑定考核指标';
  if (statusFilter.value === 'incomplete' && summary.value.incomplete === 0) {
    return summary.value.missing > 0 ? '暂无已提供的未完成指标' : '已提供数据的指标均已达标';
  }
  return '当前筛选暂无匹配指标';
});
const emptyDetail = computed(() => {
  if (statusFilter.value === 'incomplete' && summary.value.incomplete === 0 && summary.value.missing > 0) {
    return `仍有 ${summary.value.missing} 项指标数据不足，已单独列在“数据不足”筛选中。`;
  }
  return '';
});

watch([statusFilter, categoryFilter, searchText, sortMode], () => {
  currentPage.value = 1;
});
watch(() => props.targets, () => {
  currentPage.value = 1;
}, { deep: true });

function finiteOr(value, fallback) {
  return Number.isFinite(Number(value)) ? Number(value) : fallback;
}

function validNumber(value) {
  if (value === null || value === undefined || value === '') return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function compareItems(left, right) {
  if (sortMode.value === 'name') return String(left.label || '').localeCompare(String(right.label || ''), 'zh-CN');
  const leftRate = validNumber(left.rate);
  const rightRate = validNumber(right.rate);
  if (leftRate === null && rightRate !== null) return -1;
  if (leftRate !== null && rightRate === null) return 1;
  if (leftRate !== rightRate) {
    const difference = (leftRate ?? 0) - (rightRate ?? 0);
    return sortMode.value === 'rate-desc' ? -difference : difference;
  }
  return String(left.label || '').localeCompare(String(right.label || ''), 'zh-CN');
}

function goToPage(page) {
  currentPage.value = Math.min(Math.max(1, page), pageCount.value);
}

function validMetric(value) {
  if (value === null || value === undefined || value === '') return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function formatMetric(value) {
  const number = validMetric(value);
  return number === null ? '—' : new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(number);
}

function formatRate(value) {
  const number = validMetric(value);
  return number === null ? '—' : `${new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(number)}%`;
}

function progressWidth(value) {
  const number = validMetric(value);
  if (number === null || number <= 0) return 0;
  return Math.min(number, 100);
}

function textOrDash(value) {
  return value === null || value === undefined || String(value).trim() === '' ? '—' : String(value);
}

function unitText(item) {
  return item?.unit ? String(item.unit) : '';
}

function hasSourceBreakdown(item) {
  return validMetric(item?.sourceActual) !== null && validMetric(item?.base) !== null;
}

function isNegative(value) {
  const number = validMetric(value);
  return number !== null && number < 0;
}

function stateText(state) {
  return { completed: '已完成', incomplete: '未完成', missing: '数据不足' }[state] || '状态未知';
}
</script>

<style scoped>
.branch-achievement-panel {
  --branch-achievement-bg: rgba(4, 17, 47, .9);
  --branch-achievement-panel: rgba(9, 31, 77, .82);
  --branch-achievement-border: rgba(87, 170, 244, .32);
  --branch-achievement-border-soft: rgba(115, 157, 231, .16);
  --branch-achievement-text: #edf6ff;
  --branch-achievement-soft: #b4caed;
  --branch-achievement-muted: #8098c6;
  --branch-achievement-cyan: #4de8ef;
  --branch-achievement-purple: #a979ff;
  --branch-achievement-warning: #ffc45e;
  --branch-achievement-danger: #ff7182;
  display: flex;
  min-width: 0;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--branch-achievement-border);
  border-radius: 10px;
  color: var(--branch-achievement-text);
  background: radial-gradient(circle at 80% 0, rgba(65, 128, 216, .2), transparent 34%), var(--branch-achievement-bg);
  font-family: "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
  font-variant-numeric: tabular-nums;
}

.branch-achievement-panel *, .branch-achievement-panel *::before, .branch-achievement-panel *::after { box-sizing: border-box; }
.branch-achievement-panel button, .branch-achievement-panel input, .branch-achievement-panel select { font: inherit; }
.branch-achievement-panel button:focus-visible, .branch-achievement-panel input:focus-visible, .branch-achievement-panel select:focus-visible, .branch-achievement-panel [tabindex]:focus-visible { outline: 2px solid var(--branch-achievement-cyan); outline-offset: 2px; }
.branch-achievement__header, .branch-achievement__filters, .branch-achievement__footer { display: flex; align-items: center; justify-content: space-between; gap: 14px; }
.branch-achievement__header { padding: 16px 18px 13px; border-bottom: 1px solid var(--branch-achievement-border-soft); }
.branch-achievement__heading { min-width: 0; }
.branch-achievement__eyebrow { display: block; color: #72b9ed; font-size: 10px; font-weight: 700; letter-spacing: .16em; line-height: 1.3; text-transform: uppercase; }
.branch-achievement__heading h2 { margin: 4px 0 0; color: var(--branch-achievement-text); font-size: 18px; font-weight: 750; }
.branch-achievement__heading p { margin: 5px 0 0; color: var(--branch-achievement-muted); font-size: 11px; }
.branch-achievement__status-filter, .branch-achievement__filters label { display: inline-flex; align-items: center; gap: 6px; color: var(--branch-achievement-muted); font-size: 11px; }
.branch-achievement__status-filter select, .branch-achievement__filters select, .branch-achievement__filters input { min-height: 31px; border: 1px solid rgba(89, 171, 236, .35); border-radius: 5px; color: var(--branch-achievement-soft); background: rgba(7, 24, 61, .75); }
.branch-achievement__status-filter select, .branch-achievement__filters select { padding: 4px 8px; }
.branch-achievement__filters input { width: min(230px, 100%); padding: 5px 9px; }
.branch-achievement__filters input::placeholder { color: #6e88b8; }
.branch-achievement__summary { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 8px; margin: 12px 14px 0; }
.branch-achievement__summary-card { min-width: 0; padding: 10px 11px 9px; border: 1px solid rgba(74, 153, 226, .22); border-radius: 7px; background: linear-gradient(130deg, rgba(20, 67, 132, .72), rgba(8, 28, 69, .72)); }
.branch-achievement__summary-card span { display: block; color: var(--branch-achievement-muted); font-size: 10px; }
.branch-achievement__summary-card strong { display: block; margin-top: 5px; color: var(--branch-achievement-text); font-size: 20px; line-height: 1; }
.branch-achievement__summary-card.is-completed strong { color: var(--branch-achievement-cyan); }
.branch-achievement__summary-card.is-incomplete strong { color: var(--branch-achievement-warning); }
.branch-achievement__summary-card.is-missing strong { color: var(--branch-achievement-danger); }
.branch-achievement__summary-card.is-attainment-rate strong { color: var(--branch-achievement-purple); }
.branch-achievement__summary-note { grid-column: 1 / -1; margin: 0 2px; color: var(--branch-achievement-muted); font-size: 10px; }
.branch-achievement__filters { justify-content: flex-start; flex-wrap: wrap; padding: 11px 14px 9px; }
.branch-achievement__search { flex: 1 1 220px; }
.branch-achievement__search span, .branch-achievement__filters label > span { flex: 0 0 auto; }
.branch-achievement__filter-count { margin-left: auto; color: var(--branch-achievement-muted); font-size: 11px; }
.branch-achievement__table-wrap { max-width: 100%; overflow-x: auto; padding: 0 14px; }
.branch-achievement__table { width: 100%; min-width: 920px; border-collapse: collapse; color: var(--branch-achievement-soft); font-size: 11px; white-space: nowrap; }
.branch-achievement__table th, .branch-achievement__table td { padding: 9px 8px; border-bottom: 1px solid var(--branch-achievement-border-soft); text-align: right; vertical-align: middle; }
.branch-achievement__table thead th { color: #89a7dc; font-size: 10px; font-weight: 600; }
.branch-achievement__table th:first-child, .branch-achievement__table td:first-child { text-align: left; }
.branch-achievement__table tbody tr:hover { background: rgba(71, 233, 239, .05); }
.branch-achievement__table small { color: var(--branch-achievement-muted); font-size: 9px; }
.branch-achievement__source-note { display: block; margin-top: 3px; }
.branch-achievement__item-label { display: block; color: var(--branch-achievement-text); font-weight: 650; }
.branch-achievement__item-category { display: block; margin-top: 2px; }
.branch-achievement__rate { display: inline-flex; min-width: 128px; align-items: center; gap: 7px; }
.branch-achievement__rate-track { width: 70px; height: 6px; overflow: hidden; border-radius: 6px; background: rgba(117, 150, 223, .2); }
.branch-achievement__rate-track i { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, var(--branch-achievement-cyan), var(--branch-achievement-purple)); }
.branch-achievement__rate strong { color: var(--branch-achievement-cyan); font-weight: 650; }
.branch-achievement__rate.is-missing strong { color: var(--branch-achievement-muted); }
.branch-achievement__table td.is-negative { color: var(--branch-achievement-danger); }
.branch-achievement__state { display: inline-block; padding: 3px 6px; border: 1px solid currentColor; border-radius: 999px; font-size: 10px; }
.branch-achievement__state.is-completed { color: var(--branch-achievement-cyan); }
.branch-achievement__state.is-incomplete { color: var(--branch-achievement-warning); }
.branch-achievement__state.is-missing { color: var(--branch-achievement-danger); }
.branch-achievement__missing-reason { display: block; margin-top: 3px; }
.branch-achievement__empty { display: grid; min-height: 140px; place-content: center; justify-items: center; gap: 7px; padding: 20px; color: var(--branch-achievement-muted); text-align: center; }
.branch-achievement__empty strong { color: var(--branch-achievement-soft); font-size: 13px; }
.branch-achievement__empty span { font-size: 11px; }
.branch-achievement__footer { min-height: 46px; margin-top: 2px; padding: 8px 14px 10px; color: var(--branch-achievement-muted); font-size: 11px; }
.branch-achievement__pagination { display: flex; gap: 6px; }
.branch-achievement__pagination button { min-height: 28px; padding: 3px 9px; border: 1px solid rgba(89, 171, 236, .35); border-radius: 5px; color: var(--branch-achievement-soft); background: rgba(7, 24, 61, .75); cursor: pointer; }
.branch-achievement__pagination button:hover, .branch-achievement__pagination button:focus-visible { border-color: var(--branch-achievement-cyan); color: var(--branch-achievement-cyan); }
.branch-achievement__pagination button:disabled { cursor: not-allowed; opacity: .42; }

@media (max-width: 900px) {
  .branch-achievement__summary { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .branch-achievement__summary-note { grid-column: 1 / -1; }
}

@media (max-width: 620px) {
  .branch-achievement__header, .branch-achievement__footer { align-items: flex-start; flex-direction: column; }
  .branch-achievement__status-filter { width: 100%; justify-content: space-between; }
  .branch-achievement__status-filter select { flex: 1; }
  .branch-achievement__summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .branch-achievement__filters { align-items: stretch; flex-direction: column; }
  .branch-achievement__search { flex: 0 0 auto; }
  .branch-achievement__filters label { width: 100%; justify-content: space-between; }
  .branch-achievement__filters select, .branch-achievement__filters input { flex: 1; width: auto; }
  .branch-achievement__filter-count { margin-left: 0; }
}
</style>
