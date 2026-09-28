<template>
  <section class="branch-performance" :class="{ 'branch-performance--compact': compact, 'branch-performance--ranking-only': rankingOnly }" :data-compact-layout="compact ? 'flow' : undefined" data-testid="branch-performance-panel" aria-label="KPI统计与个人排名">
    <header class="branch-performance__heading">
      <div><span class="branch-performance__eyebrow">绩效洞察</span><h2>{{ rankingOnly ? '当前支行员工排名' : 'KPI统计与个人排名' }}</h2><p>当前支行 · 同一考核方案与数据日期 · 总得分降序，同分并列</p></div>
      <div class="branch-performance__controls">
        <label>考核方案<select v-model="schemeCode" data-testid="branch-kpi-scheme" :disabled="initializing || !enabled"><option value="">请选择方案</option><option v-for="scheme in schemes" :key="scheme.schemeCode" :value="scheme.schemeCode">{{ scheme.schemeName || scheme.schemeCode }}</option></select></label>
        <label>数据日期<input v-model="selectedDate" data-testid="branch-kpi-date" type="date" :disabled="!enabled" /></label>
        <button type="button" data-testid="branch-kpi-refresh" :disabled="!enabled || initializing" @click="refresh">刷新KPI</button>
      </div>
    </header>
    <p v-if="!rankingOnly" class="branch-performance__source">来源：KPI计分系统 · 各项单位以考核方案为准 · 考核实际为扣除基础值后的净值 · 完成率 =（实际值－基础值）÷目标值 × 100%</p>
    <p v-if="error" class="branch-performance__error" role="alert">{{ error }}</p>
    <p v-else-if="initializing || loading" class="branch-performance__empty" role="status">正在读取完整KPI数据…</p>
    <p v-else-if="!enabled" class="branch-performance__empty">先确认支行经营数据及机构范围</p>
    <p v-else-if="!schemes.length" class="branch-performance__empty">暂无可见的已发布KPI方案</p>
    <p v-else-if="!schemeCode || !selectedDate" class="branch-performance__empty">请选择考核方案和数据日期</p>
    <template v-else-if="loaded">
      <div v-if="!rankingOnly" class="branch-performance__summary" data-testid="branch-kpi-summary">
        <article><span>机构KPI指标</span><strong>{{ performance.orgItems.length }}</strong><small>已绑定方案指标</small></article>
        <article><span>有计分记录的员工</span><strong>{{ performance.participantCount }}</strong><small>当前权限可见范围</small></article>
        <article><span>可完整排名员工</span><strong>{{ performance.rankedCount }}</strong><small>全部应计分项有效</small></article>
        <article><span>平均得分</span><strong>{{ number(performance.averageScore) }}</strong><small>仅完整计分员工</small></article>
        <article><span>最高得分</span><strong>{{ number(performance.highestScore) }}</strong><small>同方案同日期</small></article>
      </div>
      <BranchAchievementPanel v-if="!rankingOnly" :targets="orgTargets" title="机构KPI完成情况" actual-label="考核实际（扣基期）" />
      <div class="branch-performance__ranking-heading"><div><h3>个人KPI排行榜</h3><p>按总得分排序；同分采用竞赛排名（1、1、3）。记录覆盖 {{ performance.participantCount }} 人，非全支行在岗人数。</p></div><label>搜索员工<input v-model="keyword" data-testid="branch-kpi-search" type="search" placeholder="姓名或工号" /></label></div>
      <div class="branch-performance__table-wrap" :data-compact-scroll="compact ? 'true' : undefined" tabindex="0" aria-label="个人KPI完整排名表">
        <table><thead><tr><th>名次</th><th>员工</th><th>总得分</th><th v-if="rankingOnly">未完成</th><template v-else><th>已完成指标</th><th>未完成指标</th><th>数据不足</th><th>指标数</th></template></tr></thead><tbody>
          <tr v-for="row in pageRows" :key="row.subjectId" data-testid="branch-personal-kpi-row"><td><b class="branch-performance__rank" :class="{ 'is-top': row.rank <= 3 }">{{ row.rank }}</b></td><th scope="row">{{ row.subjectName || '姓名未提供' }}<small>{{ row.subjectId }}</small></th><td class="branch-performance__score">{{ number(row.totalScore) }}</td><td v-if="rankingOnly">{{ row.incompleteCount }}</td><template v-else><td>{{ row.completedCount }}</td><td>{{ row.incompleteCount }}</td><td>{{ row.missingCount }}</td><td>{{ row.metricCount }}</td></template></tr>
          <tr v-if="!filteredRows.length"><td :colspan="rankingOnly ? 4 : 7" class="branch-performance__empty">{{ performance.rankedCount ? '没有匹配员工' : '当前方案日期暂无完整计分员工' }}</td></tr>
        </tbody></table>
      </div>
      <nav class="branch-performance__pagination" aria-label="个人排名分页"><span>共 {{ filteredRows.length }} 人 · 第 {{ pageNo }} / {{ totalPages }} 页</span><button data-testid="branch-kpi-prev" :disabled="pageNo <= 1" @click="pageNo--">上一页</button><button data-testid="branch-kpi-next" :disabled="pageNo >= totalPages" @click="pageNo++">下一页</button></nav>
      <details v-if="performance.unranked.length" class="branch-performance__unranked"><summary>计分不完整 {{ performance.unranked.length }} 人 · 查看全部</summary><ul><li v-for="row in performance.unranked" :key="row.subjectId">{{ row.subjectName || row.subjectId }}（{{ row.subjectId }}） · 计分缺失，不参与排名</li></ul></details>
    </template>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { listBranchKpiSchemes, listBranchKpiResults } from '@/api/branchPerformance';
import { loadBranchPerformance, readAllPerformancePages } from './branchPerformanceLoader';
import { buildBranchPerformanceModel } from './branchPerformanceModel';
import BranchAchievementPanel from './BranchAchievementPanel.vue';

const props = defineProps({ orgCode: { type: String, default: '' }, dataDate: { type: String, default: '' }, enabled: { type: Boolean, default: false }, refreshKey: { type: Number, default: 0 }, compact: { type: Boolean, default: false }, rankingOnly: { type: Boolean, default: false } });
const schemes = ref([]), schemeCode = ref(''), selectedDate = ref(''), error = ref(''), loading = ref(false), initializing = ref(false), loaded = ref(false), payload = ref({}), keyword = ref(''), pageNo = ref(1);
let generation = 0, disposed = false;
const performance = computed(() => buildBranchPerformanceModel({
  metrics: [...(payload.value.orgMetrics || []).map(m => ({ ...m, baseDim: 'ORG' })), ...(payload.value.empMetrics || []).map(m => ({ ...m, baseDim: 'EMP' }))],
  orgRecords: payload.value.orgRecords || [], empRecords: payload.value.empRecords || []
}));
const orgTargets = computed(() => performance.value.orgItems.map(row => ({ ...row, date: selectedDate.value, category: '机构KPI' })));
const filteredRows = computed(() => performance.value.ranking.filter(row => `${row.subjectName || ''} ${row.subjectId}`.toLowerCase().includes(keyword.value.trim().toLowerCase())));
const totalPages = computed(() => Math.max(1, Math.ceil(filteredRows.value.length / 10)));
const pageRows = computed(() => filteredRows.value.slice((pageNo.value - 1) * 10, pageNo.value * 10));
watch(keyword, () => { pageNo.value = 1; });
function number(value) { return value === null || value === undefined ? '—' : new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(value); }
function resetResults() { payload.value = {}; loaded.value = false; error.value = ''; pageNo.value = 1; keyword.value = ''; }
async function refresh() {
  if (initializing.value) return;
  const token = ++generation;
  resetResults(); loading.value = false;
  if (!props.enabled || !props.orgCode || !schemeCode.value || !selectedDate.value) return;
  loading.value = true;
  try {
    const result = await loadBranchPerformance(listBranchKpiResults, { orgCode: props.orgCode, schemeCode: schemeCode.value, dataDate: selectedDate.value }, () => !disposed && token === generation);
    if (token !== generation || disposed) return;
    // 服务端明确返回指定机构范围后，机构维度仍做精确身份复核。
    if (result.orgRecords.some(row => String(row.subjectId) !== props.orgCode)) throw new Error('机构KPI响应范围不一致，已拒绝展示');
    payload.value = result; loaded.value = true;
  } catch (e) { if (token === generation && !disposed) error.value = e.message || 'KPI读取失败，请重试'; }
  finally { if (token === generation) loading.value = false; }
}
async function initialize() {
  const token = ++generation;
  resetResults(); loading.value = false; initializing.value = false;
  if (!props.enabled || !props.orgCode) return;
  selectedDate.value = /^\d{4}-\d{2}-\d{2}$/.test(props.dataDate) ? props.dataDate : '';
  initializing.value = true;
  try {
    const response = await readAllPerformancePages(listBranchKpiSchemes, {}, () => !disposed && token === generation);
    if (token !== generation || disposed) return;
    schemes.value = response.records;
    if (!schemes.value.some(row => row.schemeCode === schemeCode.value)) schemeCode.value = schemes.value[0]?.schemeCode || '';
    await nextTick();
    if (token !== generation || disposed) return;
    initializing.value = false;
    await refresh();
  } catch (e) { if (token === generation && !disposed) { error.value = e.message || 'KPI方案读取失败'; initializing.value = false; } }
}
watch(() => [props.orgCode, props.enabled, props.refreshKey, props.dataDate], initialize, { immediate: true });
watch([schemeCode, selectedDate], () => { if (!initializing.value) void refresh(); });
onBeforeUnmount(() => { disposed = true; generation++; });
</script>

<style scoped>
.branch-performance{margin:0 clamp(16px,2vw,30px) 18px;border:1px solid rgba(119,163,255,.28);border-radius:14px;padding:22px;background:rgba(7,24,62,.88);color:#edf4ff;min-width:0}.branch-performance--compact{display:flex;box-sizing:border-box;min-height:0;margin:0;height:100%;padding:12px;border-radius:8px;flex-direction:column;overflow:visible}.branch-performance--compact .branch-performance__heading{align-items:flex-start;flex:0 0 auto}.branch-performance--compact h2{font-size:16px}.branch-performance--compact .branch-performance__controls{gap:6px}.branch-performance--compact .branch-performance__controls label{font-size:10px}.branch-performance--compact .branch-performance__controls input,.branch-performance--compact .branch-performance__controls select,.branch-performance--compact .branch-performance__controls button{padding:6px 7px;font-size:10px}.branch-performance--compact .branch-performance__table-wrap{display:block;min-height:0;max-height:360px;flex:1 1 auto;overflow:auto}.branch-performance--compact .branch-performance__table-wrap table{min-width:0;table-layout:fixed}.branch-performance--compact .branch-performance__table-wrap th,.branch-performance--compact .branch-performance__table-wrap td{padding:8px 5px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.branch-performance--ranking-only .branch-performance__table-wrap th:nth-child(1),.branch-performance--ranking-only .branch-performance__table-wrap td:nth-child(1){width:38px}.branch-performance--ranking-only .branch-performance__table-wrap th:nth-child(2),.branch-performance--ranking-only .branch-performance__table-wrap td:nth-child(2){width:42%}.branch-performance--ranking-only .branch-performance__table-wrap th:nth-child(3),.branch-performance--ranking-only .branch-performance__table-wrap td:nth-child(3){width:26%}.branch-performance--compact .branch-performance__ranking-heading{margin:12px 0 8px;flex:0 0 auto}.branch-performance--compact .branch-performance__ranking-heading h3{font-size:14px}.branch-performance--compact .branch-performance__ranking-heading p{font-size:10px}.branch-performance--compact .branch-performance__pagination{flex:0 0 auto;font-size:10px}.branch-performance--compact .branch-performance__unranked{flex:0 0 auto}.branch-performance--ranking-only .branch-performance__ranking-heading{margin-top:10px}
.branch-performance__heading,.branch-performance__ranking-heading{display:flex;align-items:center;justify-content:space-between;gap:16px;flex-wrap:wrap}.branch-performance h2,.branch-performance h3{margin:6px 0;font-size:22px}.branch-performance h3{font-size:18px}.branch-performance p{font-size:12px;color:#a9bfe3;line-height:1.7;margin:5px 0}.branch-performance__eyebrow{color:#a77bff;letter-spacing:.12em;font-size:11px}.branch-performance__controls{display:flex;gap:10px;align-items:end;flex-wrap:wrap}.branch-performance label{display:grid;gap:7px;color:#b8cbed;font-size:12px}.branch-performance input,.branch-performance select,.branch-performance button{font:inherit;padding:9px 12px;border-radius:6px;border:1px solid rgba(119,163,255,.35);color:#edf4ff;background:#0b204b;min-width:0}.branch-performance select{max-width:260px}.branch-performance button{cursor:pointer}.branch-performance button:disabled{opacity:.45;cursor:default}.branch-performance :focus-visible{outline:2px solid #47e9ef;outline-offset:3px}.branch-performance__source{padding:10px 0;border-bottom:1px solid rgba(119,163,255,.15)}.branch-performance__error{color:#ffbd9d!important;background:rgba(255,153,102,.08);padding:14px}.branch-performance__empty{text-align:center;padding:24px!important;color:#91a9d8}.branch-performance__summary{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:12px;margin:18px 0}.branch-performance__summary article{display:grid;gap:9px;padding:16px;border-radius:9px;background:rgba(167,123,255,.06);border:1px solid rgba(167,123,255,.16)}.branch-performance__summary span{color:#b8cbed;font-size:12px}.branch-performance__summary strong{font-size:28px;color:#c8a7ff}.branch-performance__summary small{font-size:11px;color:#8299c6}.branch-performance__ranking-heading{margin:24px 0 14px}.branch-performance__table-wrap{max-width:100%;overflow:auto}.branch-performance table{border-collapse:collapse;width:100%;min-width:680px;font-size:13px}.branch-performance th,.branch-performance td{text-align:left;padding:13px 12px;border-bottom:1px solid rgba(119,163,255,.12)}.branch-performance thead{background:rgba(16,41,98,.42);color:#b8cbed}.branch-performance th small{display:block;color:#8299c6;font-size:11px;font-weight:400;margin-top:4px}.branch-performance__score{font-weight:700;color:#b8f2f1}.branch-performance__rank{display:inline-flex;align-items:center;justify-content:center;min-width:28px;height:28px;border-radius:7px;background:rgba(119,163,255,.1)}.branch-performance__rank.is-top{color:#ffcf80;background:rgba(255,196,94,.1)}.branch-performance__pagination{display:flex;justify-content:flex-end;align-items:center;gap:10px;margin-top:14px;font-size:12px;color:#91a9d8}.branch-performance__unranked{margin-top:16px;font-size:12px;color:#ffcf80}.branch-performance__unranked li{margin:7px 0}.branch-performance__unranked ul{max-height:240px;overflow:auto}
@media(max-width:900px){.branch-performance__summary{grid-template-columns:repeat(3,minmax(0,1fr))}.branch-performance{padding:16px}.branch-performance__controls{width:100%}}@media(max-width:600px){.branch-performance__summary{grid-template-columns:repeat(2,minmax(0,1fr))}.branch-performance__controls label{width:100%}.branch-performance__controls select{max-width:none}.branch-performance__pagination{justify-content:space-between}}
</style>
