<template>
  <section class="metric-binding" data-testid="metric-catalog-binder">
    <header class="metric-binding__head">
      <div>
        <h3>从指标库绑定</h3>
        <p>指标来自绩效指标定义。查询结果的列名是指标名称，不是宽表槽位号。单位识别不了时需要手工选择，系统不会猜成元。</p>
      </div>
      <button type="button" data-testid="metric-catalog-reload" :disabled="disabled || loadingCatalog" @click="loadCatalog">刷新目录</button>
    </header>

    <p v-if="loadingCatalog" class="metric-binding__hint" role="status">正在读取指标目录…</p>
    <p v-if="catalogError" class="metric-binding__alert" data-testid="metric-catalog-error" role="alert">{{ catalogError }}</p>
    <p v-if="shape.blocked" class="metric-binding__alert" data-testid="metric-catalog-blocked" role="note">{{ shape.reason }}</p>

    <template v-else>
      <div class="metric-binding__filters">
        <label>
          搜索
          <input data-testid="metric-catalog-search" :value="keyword" type="search" placeholder="名称、编码或分类" :disabled="disabled" @input="keyword = $event.target.value" />
        </label>
        <label>
          维度
          <select data-testid="metric-catalog-dim" :value="baseDim" :disabled="disabled || namedGroup" @change="baseDim = $event.target.value">
            <option value="">全部维度</option>
            <option value="ORG">机构</option>
            <option value="EMP">员工</option>
            <option value="CUST">客户</option>
          </select>
        </label>
        <label>
          分类
          <select data-testid="metric-catalog-category" :value="category" :disabled="disabled" @change="category = $event.target.value">
            <option value="">全部分类</option>
            <option v-for="item in categories" :key="item" :value="item">{{ item }}</option>
          </select>
        </label>
      </div>
      <p class="metric-binding__hint" data-testid="metric-catalog-count">可绑定 {{ filtered.length }} 项，目录 {{ catalogItems.length }} 项。{{ namedGroup ? '当前屏使用命名机构组，只能选择机构指标。' : '未使用命名机构组时，聚合结果按全辖口径校验权限。' }}</p>
      <p v-for="note in suggestionNotes" :key="note" class="metric-binding__hint" data-testid="metric-catalog-note">{{ note }}</p>

      <div v-for="field in metricFields" :key="field.semantic" class="metric-binding__assign">
        <label :for="`metric-catalog-${field.semantic}`">{{ field.label }}<em v-if="field.required">需要配置</em></label>
        <select :id="`metric-catalog-${field.semantic}`" :data-testid="`metric-catalog-assign-${field.semantic}`" :disabled="disabled" :value="assignments[field.semantic]?.metricCode || ''" @change="setMetric(field, $event.target.value)">
          <option value="">请选择指标</option>
          <option v-for="metric in optionsFor(field)" :key="metric.metricCode" :value="metric.metricCode">
            {{ metric.metricName }}（{{ metric.metricCode }} · 槽位 {{ metric.valSlot }}{{ metric.unit ? ` · ${unitLabel(metric.unit)}` : ' · 单位待选' }}）
          </option>
        </select>
        <select v-if="field.unitKinds?.length" :data-testid="`metric-catalog-unit-${field.semantic}`" :disabled="disabled" :value="assignments[field.semantic]?.unit || ''" @change="setUnit(field.semantic, $event.target.value)">
          <option value="">选择单位</option>
          <option v-for="unit in field.unitKinds" :key="unit" :value="unit">{{ unitLabel(unit) }}</option>
        </select>
      </div>
      <p v-if="filtered.length > optionLimit" class="metric-binding__hint">只展示前 {{ optionLimit }} 条，请继续缩小搜索。</p>
      <button type="button" data-testid="metric-catalog-suggest" :disabled="disabled || !hasSuggestion" @click="applySuggestions">填入名称建议</button>

      <div class="metric-binding__agg">
        <label>
          聚合维度
          <select data-testid="metric-catalog-groupby" :value="groupBy" :disabled="disabled" @change="groupBy = $event.target.value">
            <option value="NONE">汇总为一行</option>
            <option value="SUBJECT">按主体（排名）</option>
            <option value="DATE">按日期（趋势）</option>
          </select>
        </label>
        <label>
          聚合函数
          <select data-testid="metric-catalog-agg" :value="agg" :disabled="disabled" @change="agg = $event.target.value">
            <option value="SUM">求和</option>
            <option value="AVG">平均</option>
            <option value="MAX">最大</option>
            <option value="MIN">最小</option>
            <option value="COUNT">计数</option>
          </select>
        </label>
      </div>

      <div class="metric-binding__filters-editor">
        <div class="metric-binding__filters-head">
          <strong>过滤</strong>
          <button type="button" data-testid="metric-catalog-add-filter" :disabled="disabled" @click="addFilter">添加条件</button>
        </div>
        <div v-for="(filter, index) in filters" :key="index" class="metric-binding__filter-row">
          <select :data-testid="`metric-catalog-filter-target-${index}`" :value="filter.target" :disabled="disabled" @change="filter.target = $event.target.value">
            <option value="metric">指标值</option>
            <option value="subject">主体编码</option>
            <option value="data_date">数据日期</option>
          </select>
          <select v-if="filter.target === 'metric'" :value="filter.metricCode" :disabled="disabled" @change="filter.metricCode = $event.target.value">
            <option value="">选择已选指标</option>
            <option v-for="metric in selectedMetrics" :key="metric.metricCode" :value="metric.metricCode">{{ metric.metricName }}</option>
          </select>
          <select :value="filter.op" :disabled="disabled" @change="filter.op = $event.target.value">
            <option value="EQ">等于</option>
            <option value="NE">不等于</option>
            <option value="GT">大于</option>
            <option value="GE">大于等于</option>
            <option value="LT">小于</option>
            <option value="LE">小于等于</option>
          </select>
          <input :value="filter.value" placeholder="过滤值" :disabled="disabled" @input="filter.value = $event.target.value" />
          <button type="button" :disabled="disabled" @click="filters.splice(index, 1)">移除</button>
        </div>
        <p class="metric-binding__hint">指标值过滤会写成对应的 val_槽位，不会把指标名称拼进 SQL。</p>
      </div>

      <label class="metric-binding__reason">
        原因
        <input data-testid="metric-catalog-reason" :value="reason" :disabled="disabled" @input="reason = $event.target.value" />
      </label>
      <p v-for="item in plan.warnings" :key="item" class="metric-binding__hint" data-testid="metric-catalog-warning">{{ item }}</p>
      <p v-for="item in plan.errors" :key="item" class="metric-binding__alert">{{ item }}</p>

      <div class="metric-binding__actions">
        <button type="button" data-testid="metric-catalog-preview" :disabled="disabled || !plan.ok || previewing" @click="preview">预览 10 行</button>
        <button type="button" data-testid="metric-catalog-apply" :disabled="disabled || !plan.ok" @click="$emit('apply', plan)">
          {{ plan.existingDatasourceId ? '绑定到已有数据源' : '创建数据源并绑定' }}
        </button>
      </div>
      <p v-if="previewError" class="metric-binding__alert" role="alert">{{ previewError }}</p>
      <div v-if="previewRows.length" class="metric-binding__preview" data-testid="metric-catalog-preview-table">
        <table>
          <thead>
            <tr><th v-for="column in previewColumns" :key="column">{{ column }}</th></tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in previewRows" :key="index">
              <td v-for="(cell, cellIndex) in row" :key="cellIndex">{{ cell ?? '' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else-if="previewEmpty" class="metric-binding__hint">试跑成功，但没有返回行。请核对数据日期、机构组和槽位是否有数。</p>
    </template>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { listScreenMetricCatalog, tryRunScreenDatasource } from '@/api/screen';
import { buildDatasourceTryRunRequest } from '@/utils/dsConfig';
import { bindingShape, buildMetricBindingPlan, filterCatalog, suggestAssignments } from './screenMetricBinding';

const props = defineProps({
  slotKey: { type: String, default: '' },
  slotLabel: { type: String, default: '' },
  templateName: { type: String, default: '' },
  fieldSpecs: { type: Array, default: () => [] },
  compositionMode: { type: String, default: 'rows' },
  screen: { type: Object, default: () => ({}) },
  datasources: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false },
  catalog: { type: Array, default: null }
});
defineEmits(['apply']);

const optionLimit = 200;
const keyword = ref('');
const baseDim = ref('ORG');
const category = ref('');
const assignments = ref({});
const groupBy = ref('NONE');
const agg = ref('SUM');
const filters = ref([]);
const reason = ref('从指标库绑定到当前组件');
const suggestionNotes = ref([]);
const remoteCatalog = ref([]);
const loadingCatalog = ref(false);
const catalogError = ref('');
const previewing = ref(false);
const previewError = ref('');
const previewResult = ref(null);
const previewEmpty = ref(false);

const namedGroup = computed(() => String(props.screen?.orgScopeMode || '').toUpperCase() === 'NAMED_GROUP');
const shape = computed(() => bindingShape(props.slotKey, props.compositionMode));
const catalogItems = computed(() => Array.isArray(props.catalog) ? props.catalog : remoteCatalog.value);
const categories = computed(() => [...new Set(catalogItems.value.map(item => item?.metricCategory).filter(Boolean))].sort());
const filtered = computed(() => filterCatalog(catalogItems.value, {
  keyword: keyword.value,
  baseDim: namedGroup.value ? 'ORG' : baseDim.value,
  category: category.value,
  bindableOnly: true
}));
const metricFields = computed(() => (props.fieldSpecs || []).filter(field => field?.kind !== 'dimension'));
const suggestion = computed(() => suggestAssignments({
  template: props.templateName,
  slot: props.slotKey,
  fieldSpecs: props.fieldSpecs,
  metrics: filtered.value
}));
const hasSuggestion = computed(() => Object.values(suggestion.value.assignments).some(Boolean));
const selectedMetrics = computed(() => metricFields.value
  .map(field => catalogItems.value.find(item => item.metricCode === assignments.value[field.semantic]?.metricCode))
  .filter(Boolean));
const plan = computed(() => buildMetricBindingPlan({
  slot: props.slotKey,
  slotLabel: props.slotLabel,
  template: props.templateName,
  fieldSpecs: props.fieldSpecs,
  compositionMode: props.compositionMode,
  screen: props.screen,
  metrics: catalogItems.value,
  assignments: assignments.value,
  aggregation: { groupBy: groupBy.value, agg: agg.value },
  filters: filters.value,
  reason: reason.value,
  datasources: props.datasources
}));
const previewColumns = computed(() => previewResult.value?.columns || []);
const previewRows = computed(() => previewResult.value?.rows || []);

watch(() => [props.slotKey, props.compositionMode], () => {
  const next = bindingShape(props.slotKey, props.compositionMode);
  groupBy.value = next.groupBy || 'NONE';
  agg.value = 'SUM';
  assignments.value = {};
  filters.value = [];
  suggestionNotes.value = [];
  previewResult.value = null;
  previewEmpty.value = false;
  previewError.value = '';
}, { immediate: true });

watch(namedGroup, value => { if (value) baseDim.value = 'ORG'; }, { immediate: true });
watch(() => props.catalog, value => { if (!Array.isArray(value)) loadCatalog(); }, { immediate: true });

function optionsFor(field) {
  const selected = assignments.value[field.semantic]?.metricCode;
  const rows = filtered.value.slice(0, optionLimit);
  if (selected && !rows.some(item => item.metricCode === selected)) {
    const current = catalogItems.value.find(item => item.metricCode === selected);
    if (current) rows.unshift(current);
  }
  return rows;
}

function setMetric(field, metricCode) {
  const metric = catalogItems.value.find(item => item.metricCode === metricCode);
  const next = { ...(assignments.value[field.semantic] || {}), metricCode };
  if (!metricCode) {
    const copy = { ...assignments.value };
    delete copy[field.semantic];
    assignments.value = copy;
    return;
  }
  if (metric?.unit && (!field.unitKinds?.length || field.unitKinds.includes(metric.unit))) next.unit = metric.unit;
  assignments.value = { ...assignments.value, [field.semantic]: next };
}

function setUnit(semantic, unit) {
  assignments.value = {
    ...assignments.value,
    [semantic]: { ...(assignments.value[semantic] || {}), unit }
  };
}

function applySuggestions() {
  suggestionNotes.value = suggestion.value.notes;
  const next = { ...assignments.value };
  for (const field of metricFields.value) {
    const metric = suggestion.value.assignments[field.semantic];
    if (!metric) continue;
    const unit = metric.unit && (!field.unitKinds?.length || field.unitKinds.includes(metric.unit)) ? metric.unit : '';
    next[field.semantic] = { metricCode: metric.metricCode, unit };
  }
  assignments.value = next;
}

function addFilter() {
  const metricCode = selectedMetrics.value[0]?.metricCode || '';
  filters.value = [...filters.value, { target: 'metric', metricCode, op: 'GT', value: '' }];
}

async function loadCatalog() {
  if (Array.isArray(props.catalog)) return;
  if (typeof listScreenMetricCatalog !== 'function') {
    catalogError.value = '指标目录接口未接入。';
    return;
  }
  loadingCatalog.value = true;
  catalogError.value = '';
  try {
    const rows = await listScreenMetricCatalog({ baseDim: namedGroup.value ? 'ORG' : undefined });
    remoteCatalog.value = Array.isArray(rows) ? rows : (rows?.records || []);
    if (!remoteCatalog.value.length) catalogError.value = '指标目录为空。请在真实库核对 PERF_METRIC_DEF 是否有 ACTIVE 且已分配槽位的指标。';
  } catch (error) {
    remoteCatalog.value = [];
    catalogError.value = error?.message || '指标目录读取失败。请确认已登记资源 R_RPT_SCR_METRIC_CAT，并在真实库核对。';
  } finally {
    loadingCatalog.value = false;
  }
}

async function preview() {
  if (!plan.value.ok) return;
  previewing.value = true;
  previewError.value = '';
  previewResult.value = null;
  previewEmpty.value = false;
  try {
    const body = buildDatasourceTryRunRequest(plan.value.previewRow, {
      reason: reason.value,
      period: plan.value.binding.period,
      testOrgGroupCode: props.screen?.orgGroupCode || ''
    });
    const result = await tryRunScreenDatasource(body);
    previewResult.value = result;
    previewEmpty.value = !result?.rows?.length;
  } catch (error) {
    previewError.value = error?.message || '试跑失败';
  } finally {
    previewing.value = false;
  }
}

function unitLabel(unit) {
  return ({
    YUAN: '元', TEN_THOUSAND: '万元', HUNDRED_MILLION: '亿元', COUNT: '个数',
    TEN_THOUSAND_COUNT: '万户', PERCENT: '%', RATIO: '比例'
  })[unit] || unit;
}
</script>

<style scoped>
.metric-binding { margin-top: 16px; padding: 12px; border: 1px solid var(--color-border, #d9e2f2); border-radius: 6px; background: var(--color-surface-subtle, #f7f9fc); }
.metric-binding__head, .metric-binding__filters, .metric-binding__agg, .metric-binding__actions, .metric-binding__filter-row, .metric-binding__assign { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.metric-binding__head { justify-content: space-between; align-items: flex-start; }
.metric-binding h3 { margin: 0 0 4px; font-size: 14px; }
.metric-binding p, .metric-binding__hint { margin: 6px 0; color: var(--color-text-muted, #667085); font-size: 12px; line-height: 18px; }
.metric-binding__alert { margin: 6px 0; color: var(--color-danger-700, #b42318); font-size: 12px; }
.metric-binding label { display: flex; flex-direction: column; gap: 4px; min-width: 0; color: var(--color-text-strong, #303133); font-size: 12px; font-weight: 600; }
.metric-binding input, .metric-binding select, .metric-binding button { min-height: 32px; box-sizing: border-box; }
.metric-binding input, .metric-binding select { min-width: 0; padding: 0 8px; border: 1px solid var(--color-border-strong, #c0c4cc); border-radius: 4px; background: #fff; color: inherit; font-weight: 400; }
.metric-binding__assign select { flex: 1; }
.metric-binding__reason input, .metric-binding__filters input[type="search"] { width: 100%; }
.metric-binding__reason { display: block; margin-top: 8px; }
.metric-binding button { padding: 0 10px; border: 1px solid var(--color-border-strong, #c0c4cc); border-radius: 4px; background: #fff; cursor: pointer; }
.metric-binding button:disabled { cursor: not-allowed; opacity: .6; }
.metric-binding__actions { margin-top: 8px; }
.metric-binding__preview { overflow: auto; max-height: 220px; margin-top: 8px; background: #fff; }
.metric-binding table { width: 100%; border-collapse: collapse; font-size: 12px; }
.metric-binding th, .metric-binding td { padding: 4px 6px; border-bottom: 1px solid var(--color-border, #ebeef5); text-align: left; white-space: nowrap; }
.metric-binding em { margin-left: 4px; color: var(--color-danger-700, #b42318); font-style: normal; font-weight: 400; }
</style>
