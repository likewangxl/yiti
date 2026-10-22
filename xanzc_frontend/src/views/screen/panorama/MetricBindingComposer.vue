<template>
  <section class="metric-binding" data-testid="metric-catalog-binder">
    <header class="metric-binding__head">
      <div>
        <h3>从指标库绑定</h3>
        <p>选择需要展示的指标；单位无法识别时，请手动选择。</p>
      </div>
      <el-button data-testid="metric-catalog-reload" :disabled="disabled || loadingCatalog" @click="loadCatalog">刷新目录</el-button>
    </header>

    <p v-if="loadingCatalog" class="hint" role="status">正在读取指标目录…</p>
    <div v-if="catalogError" data-testid="metric-catalog-error">
      <el-alert type="error" :closable="false" show-icon :title="catalogError" />
    </div>
    <el-alert v-if="shape.blocked" data-testid="metric-catalog-blocked" type="warning" :closable="false" show-icon :title="shape.reason" />

    <el-form v-else label-position="top" size="default" class="metric-binding__form" @submit.prevent>
      <div class="metric-binding__filters">
        <el-form-item label="搜索">
          <el-input data-testid="metric-catalog-search" v-model="keyword" clearable placeholder="名称、编码或分类" :disabled="disabled" />
        </el-form-item>
        <el-form-item label="维度">
          <el-select data-testid="metric-catalog-dim" v-model="baseDim" :disabled="disabled || namedGroup" placeholder="全部维度">
            <el-option label="全部维度" value="" />
            <el-option label="机构" value="ORG" />
            <el-option label="员工" value="EMP" />
            <el-option label="客户" value="CUST" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select data-testid="metric-catalog-category" v-model="category" :disabled="disabled" placeholder="全部分类">
            <el-option label="全部分类" value="" />
            <el-option v-for="item in categories" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
      </div>
      <p class="hint" data-testid="metric-catalog-count">可绑定 {{ filtered.length }} 项，目录 {{ catalogItems.length }} 项。{{ namedGroup ? '当前屏使用命名机构组，只能选择机构指标。' : '未使用命名机构组时，聚合结果按全辖口径校验权限。' }}</p>
      <p v-for="note in suggestionNotes" :key="note" class="hint" data-testid="metric-catalog-note">{{ note }}</p>

      <div v-for="field in primaryMetricFields" :key="field.semantic" class="metric-binding__assign">
        <el-form-item :required="Boolean(field.required)">
          <template #label>
            <span>{{ field.label }}</span>
            <em v-if="field.required && !assignments[field.semantic]?.metricCode" class="metric-binding__required">需要配置</em>
          </template>
          <el-select :id="`metric-catalog-${field.semantic}`" :data-testid="`metric-catalog-assign-${field.semantic}`" filterable :disabled="disabled" :model-value="assignments[field.semantic]?.metricCode || ''" placeholder="请选择指标" @change="value => setMetric(field, value)">
            <el-option label="请选择指标" value="" />
            <el-option v-for="metric in optionsFor(field)" :key="metric.metricCode" :label="metricOptionLabel(metric)" :value="metric.metricCode" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="field.unitKinds?.length" label="单位">
          <el-select :data-testid="`metric-catalog-unit-${field.semantic}`" :disabled="disabled" :model-value="assignments[field.semantic]?.unit || ''" placeholder="选择单位" @change="value => setUnit(field.semantic, value)">
            <el-option label="选择单位" value="" />
            <el-option v-for="unit in field.unitKinds" :key="unit" :label="unitLabel(unit)" :value="unit" />
          </el-select>
        </el-form-item>
      </div>
      <p v-if="filtered.length > optionLimit" class="hint">只展示前 {{ optionLimit }} 条，请继续缩小搜索。</p>
      <el-button v-if="!comparisonsOnly" data-testid="metric-catalog-suggest" :disabled="disabled || !hasSuggestion" @click="applySuggestions">填入名称建议</el-button>

      <section v-if="showPeriodComparisons" class="metric-binding__periods" data-testid="metric-period-comparisons">
        <h4>期间比较</h4>
        <p class="hint">较昨日、较上月、较上年可以绑定结果表里已经算好的增量，或用该指标在基准日的余额回推。未配置、没有对应日期或单位不是金额时，预览保持待接入。绑定增量时，来源行的数据日必须是大屏数据日。按基准日回推会把聚合改为按日期。</p>
        <p v-if="comparisonsOnly" class="hint">结算性存款没有独立查询。比较指标会并入本屏已绑定的同一结果表来源；没有可用来源时预览保持待接入。</p>
        <div v-for="slot in periodSlots" :key="slot.key" class="metric-binding__period">
          <el-form-item :label="slot.label">
            <el-select :data-testid="`metric-period-mode-${slot.key}`" :disabled="disabled" v-model="periodModes[slot.key]">
              <el-option label="不配置" value="UNSET" />
              <el-option label="绑定增量指标" value="METRIC" />
              <el-option label="按基准日回推" value="OFFSET" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="periodModes[slot.key] !== 'UNSET'" label="指标">
            <el-select :data-testid="`metric-period-metric-${slot.key}`" filterable :disabled="disabled" :model-value="periodAssignments[slot.key]?.metricCode || ''" placeholder="请选择指标" @change="value => setPeriodMetric(slot.key, value)">
              <el-option label="请选择指标" value="" />
              <el-option v-for="metric in periodOptions(slot.key)" :key="metric.metricCode" :label="metricOptionLabel(metric)" :value="metric.metricCode" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="periodModes[slot.key] !== 'UNSET'" label="单位">
            <el-select :data-testid="`metric-period-unit-${slot.key}`" :disabled="disabled" :model-value="periodAssignments[slot.key]?.unit || ''" placeholder="选择单位" @change="value => setPeriodUnit(slot.key, value)">
              <el-option label="选择单位" value="" />
              <el-option v-for="unit in amountUnits" :key="unit" :label="unitLabel(unit)" :value="unit" />
            </el-select>
          </el-form-item>
        </div>
      </section>

      <details v-if="!comparisonsOnly" class="metric-binding__advanced" data-testid="metric-catalog-advanced">
        <summary>高级取数设置</summary>
        <p class="hint">默认按组件和指标口径取数，仅在需要特殊汇总或筛选时调整。</p>
        <p class="hint">机构、员工、客户指标分别读取 ORG_INDEX_RESULT、EMP_INDEX_RESULT、CUST_INDEX_RESULT。输出列使用指标名称。</p>
        <div v-for="field in legacyMetricFields" :key="field.semantic" class="metric-binding__assign">
          <el-form-item :required="Boolean(field.required)">
            <template #label>
              <span>{{ field.label }}</span>
              <em v-if="field.required && !assignments[field.semantic]?.metricCode" class="metric-binding__required">需要配置</em>
            </template>
            <el-select :id="`metric-catalog-${field.semantic}`" :data-testid="`metric-catalog-assign-${field.semantic}`" filterable :disabled="disabled" :model-value="assignments[field.semantic]?.metricCode || ''" placeholder="请选择指标" @change="value => setMetric(field, value)">
              <el-option label="请选择指标" value="" />
              <el-option v-for="metric in optionsFor(field)" :key="metric.metricCode" :label="metricOptionLabel(metric)" :value="metric.metricCode" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="field.unitKinds?.length" label="单位">
            <el-select :data-testid="`metric-catalog-unit-${field.semantic}`" :disabled="disabled" :model-value="assignments[field.semantic]?.unit || ''" placeholder="选择单位" @change="value => setUnit(field.semantic, value)">
              <el-option label="选择单位" value="" />
              <el-option v-for="unit in field.unitKinds" :key="unit" :label="unitLabel(unit)" :value="unit" />
            </el-select>
          </el-form-item>
        </div>
        <div class="metric-binding__agg">
          <el-form-item label="聚合维度">
            <el-select data-testid="metric-catalog-groupby" v-model="groupBy" :disabled="disabled">
              <el-option label="汇总为一行" value="NONE" />
              <el-option label="按主体（排名）" value="SUBJECT" />
              <el-option label="按日期（趋势）" value="DATE" />
            </el-select>
          </el-form-item>
          <el-form-item label="聚合函数">
            <el-select data-testid="metric-catalog-agg" v-model="agg" :disabled="disabled" placeholder="按指标口径自动选择">
              <el-option label="按指标口径自动选择" value="" />
              <el-option label="求和" value="SUM" />
              <el-option label="平均" value="AVG" />
              <el-option label="最大" value="MAX" />
              <el-option label="最小" value="MIN" />
              <el-option label="计数" value="COUNT" />
            </el-select>
          </el-form-item>
        </div>

        <div class="metric-binding__filters-editor">
          <div class="metric-binding__filters-head">
            <span>过滤</span>
            <el-button data-testid="metric-catalog-add-filter" :disabled="disabled" @click="addFilter">添加条件</el-button>
          </div>
          <div v-for="(filter, index) in filters" :key="index" class="metric-binding__filter-row">
            <el-select :data-testid="`metric-catalog-filter-target-${index}`" v-model="filter.target" :disabled="disabled">
              <el-option label="指标值" value="metric" />
              <el-option label="主体编码" value="subject" />
              <el-option label="数据日期" value="data_date" />
            </el-select>
            <el-select v-if="filter.target === 'metric'" v-model="filter.metricCode" :disabled="disabled" placeholder="选择已选指标">
              <el-option label="选择已选指标" value="" />
              <el-option v-for="metric in selectedMetrics" :key="metric.metricCode" :label="metric.metricName" :value="metric.metricCode" />
            </el-select>
            <el-select v-model="filter.op" :disabled="disabled">
              <el-option label="等于" value="EQ" />
              <el-option label="不等于" value="NE" />
              <el-option label="大于" value="GT" />
              <el-option label="大于等于" value="GE" />
              <el-option label="小于" value="LT" />
              <el-option label="小于等于" value="LE" />
            </el-select>
            <el-input v-model="filter.value" placeholder="过滤值" :disabled="disabled" />
            <el-button link type="danger" :disabled="disabled" @click="filters.splice(index, 1)">移除</el-button>
          </div>
          <p class="hint">指标值过滤会写成对应的 val_槽位，不会把指标名称拼进 SQL。</p>
        </div>

        <el-form-item label="原因">
          <el-input data-testid="metric-catalog-reason" v-model="reason" :disabled="disabled" />
        </el-form-item>
      </details>
      <el-alert v-for="item in plan.warnings" :key="item" class="metric-binding__status" data-testid="metric-catalog-warning" type="warning" :closable="false" show-icon :title="item" />
      <el-alert v-for="item in plan.errors" :key="item" class="metric-binding__status" type="error" :closable="false" show-icon :title="item" />

      <div class="metric-binding__actions">
        <el-button v-if="!comparisonsOnly" data-testid="metric-catalog-preview" :disabled="disabled || !plan.ok || previewing" :loading="previewing" @click="preview">预览 10 行</el-button>
        <el-button data-testid="metric-catalog-apply" type="primary" :disabled="disabled || !plan.ok" @click="$emit('apply', plan)">
          {{ comparisonsOnly ? '保存期间比较' : (plan.existingDatasourceId ? '绑定到已有数据源' : '创建数据源并绑定') }}
        </el-button>
      </div>
      <el-alert v-if="previewError" class="metric-binding__status" type="error" :closable="false" show-icon :title="previewError" />
      <div v-if="previewRows.length" class="metric-binding__preview" data-testid="metric-catalog-preview-table">
        <el-table :data="previewTableRows" size="default" stripe border max-height="220">
          <el-table-column v-for="(column, index) in previewColumns" :key="`${column}-${index}`" :prop="`c${index}`" :label="column || `列${index + 1}`" min-width="120" show-overflow-tooltip />
        </el-table>
      </div>
      <p v-else-if="previewEmpty" class="hint">试跑成功，但没有返回行。请核对数据日期、机构组和槽位是否有数。</p>
    </el-form>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { ElAlert, ElButton, ElForm, ElFormItem, ElInput, ElOption, ElSelect, ElTable, ElTableColumn } from 'element-plus';
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
  catalog: { type: Array, default: null },
  showPeriodComparisons: { type: Boolean, default: false },
  comparisonsOnly: { type: Boolean, default: false },
  periodComparisons: { type: Object, default: null }
});
defineEmits(['apply']);

const optionLimit = 200;
const amountUnits = ['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION'];
const periodSlots = [
  { key: 'day', label: '较昨日' },
  { key: 'month', label: '较上月' },
  { key: 'year', label: '较上年' }
];
const keyword = ref('');
const baseDim = ref('ORG');
const category = ref('');
const assignments = ref({});
const groupBy = ref('NONE');
const agg = ref('');
const filters = ref([]);
const reason = ref('从指标库绑定到当前组件');
const periodModes = ref({ day: 'UNSET', month: 'UNSET', year: 'UNSET' });
const periodAssignments = ref({ day: {}, month: {}, year: {} });
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
// 已有变化绑定仍由父级保留；期间比较组件只提供含明确基期的比较入口。
const metricFields = computed(() => props.comparisonsOnly ? [] : (props.fieldSpecs || []).filter(field =>
  field?.kind !== 'dimension' && !(props.showPeriodComparisons && field.semantic === 'change')));
const primaryMetricFields = computed(() => metricFields.value.filter(field => field.semantic !== 'change'));
const legacyMetricFields = computed(() => metricFields.value.filter(field => field.semantic === 'change'));
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
  datasources: props.datasources,
  comparisonsOnly: props.comparisonsOnly,
  periodComparisons: props.showPeriodComparisons ? periodPayload() : undefined
}));
const previewColumns = computed(() => previewResult.value?.columns || []);
const previewRows = computed(() => previewResult.value?.rows || []);
const previewTableRows = computed(() => previewRows.value.map(row => {
  const record = {};
  previewColumns.value.forEach((_, index) => { record[`c${index}`] = row?.[index] ?? ''; });
  return record;
}));

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

function metricOptionLabel(metric) {
  const table = metric.tableName || '结果表待定';
  const unit = metric.unit ? unitLabel(metric.unit) : '单位待选';
  return `${metric.metricName}（${metric.metricCode} · ${table} · 槽位 ${metric.valSlot} · ${unit}）`;
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

function periodPayload() {
  return Object.fromEntries(periodSlots.map(slot => [slot.key, {
    mode: periodModes.value[slot.key] || 'UNSET',
    metricCode: periodAssignments.value[slot.key]?.metricCode || '',
    unit: periodAssignments.value[slot.key]?.unit || ''
  }]));
}

function restorePeriodComparisons(value) {
  const nextModes = { day: 'UNSET', month: 'UNSET', year: 'UNSET' };
  const nextAssignments = { day: {}, month: {}, year: {} };
  for (const slot of periodSlots) {
    const saved = value?.[slot.key];
    if (saved?.mode === 'METRIC' || saved?.mode === 'OFFSET') {
      nextModes[slot.key] = saved.mode;
      nextAssignments[slot.key] = { metricCode: saved.metricCode || '', unit: saved.unit || '' };
    }
  }
  periodModes.value = nextModes;
  periodAssignments.value = nextAssignments;
}

function periodOptions(key) {
  const selected = periodAssignments.value[key]?.metricCode;
  const rows = filtered.value.slice(0, optionLimit);
  if (selected && !rows.some(item => item.metricCode === selected)) {
    const current = catalogItems.value.find(item => item.metricCode === selected);
    if (current) rows.unshift(current);
  }
  return rows;
}

function setPeriodMetric(key, metricCode) {
  const metric = catalogItems.value.find(item => item.metricCode === metricCode);
  if (!metricCode) {
    periodAssignments.value = { ...periodAssignments.value, [key]: {} };
    return;
  }
  const next = { ...(periodAssignments.value[key] || {}), metricCode };
  if (amountUnits.includes(metric?.unit)) next.unit = metric.unit;
  periodAssignments.value = { ...periodAssignments.value, [key]: next };
}

function setPeriodUnit(key, unit) {
  periodAssignments.value = {
    ...periodAssignments.value,
    [key]: { ...(periodAssignments.value[key] || {}), unit }
  };
}

watch(() => [props.slotKey, props.compositionMode, props.periodComparisons], () => {
  const next = bindingShape(props.slotKey, props.compositionMode);
  agg.value = '';
  assignments.value = {};
  filters.value = [];
  suggestionNotes.value = [];
  previewResult.value = null;
  previewEmpty.value = false;
  previewError.value = '';
  restorePeriodComparisons(props.periodComparisons);
  groupBy.value = Object.values(periodModes.value).includes('OFFSET') ? 'DATE' : (next.groupBy || 'NONE');
}, { immediate: true });

watch(periodModes, modes => {
  if (Object.values(modes).includes('OFFSET') && groupBy.value === 'NONE') groupBy.value = 'DATE';
}, { deep: true });

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
.metric-binding { margin-top: 16px; padding-top: 12px; border-top: 1px solid var(--color-border, #ebeef5); }
.metric-binding__head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; margin-bottom: 8px; }
.metric-binding__head h3 { margin: 0; color: var(--color-text-strong, #303133); font-size: 14px; font-weight: 600; }
.metric-binding__head p, .metric-binding .hint { margin: 4px 0 0; color: var(--el-text-color-secondary, #909399); font-size: 12px; line-height: 18px; }
.metric-binding__filters, .metric-binding__agg { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); column-gap: 12px; }
.metric-binding__advanced { margin: 12px 0; padding: 10px 12px; border: 1px solid var(--color-border, #ebeef5); border-radius: 4px; }
.metric-binding__advanced > summary { color: var(--color-text-regular, #606266); font-size: 13px; cursor: pointer; }
.metric-binding__advanced[open] > summary { margin-bottom: 8px; }
.metric-binding__agg { grid-template-columns: repeat(2, minmax(0, 1fr)); margin-top: 12px; }
.metric-binding__assign { display: grid; grid-template-columns: minmax(0, 1fr) 168px; column-gap: 12px; align-items: start; }
.metric-binding__periods { margin-top: 4px; padding-top: 8px; border-top: 1px solid var(--color-border, #ebeef5); }
.metric-binding__periods h4 { margin: 0; color: var(--color-text-strong, #303133); font-size: 13px; font-weight: 600; }
.metric-binding__period { display: grid; grid-template-columns: 180px minmax(0, 1fr) 168px; column-gap: 12px; align-items: start; }
.metric-binding :deep(.el-form-item) { margin-bottom: 12px; }
.metric-binding :deep(.el-form-item__label) { margin-bottom: 6px; color: var(--color-text-strong, #303133); font-size: 13px; font-weight: 600; line-height: 20px; }
.metric-binding :deep(.el-select), .metric-binding :deep(.el-input) { width: 100%; }
.metric-binding__required { margin-left: 6px; color: var(--el-color-danger, #f56c6c); font-size: 12px; font-style: normal; font-weight: 400; }
.metric-binding__filters-head { display: flex; align-items: center; justify-content: space-between; margin: 0 0 8px; color: var(--color-text-strong, #303133); font-size: 13px; font-weight: 600; }
.metric-binding__filter-row { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-bottom: 8px; }
.metric-binding__filter-row :deep(.el-select) { width: 160px; }
.metric-binding__filter-row :deep(.el-input) { width: 180px; }
.metric-binding__actions { display: flex; gap: 8px; margin-top: 4px; }
.metric-binding__status { margin-top: 8px; }
.metric-binding__preview { margin-top: 8px; }
.metric-binding :deep(.el-alert) { margin-top: 8px; }
.metric-binding :deep(.el-alert__title) { font-size: 13px; line-height: 20px; }
@media (max-width: 760px) {
  .metric-binding__filters, .metric-binding__agg, .metric-binding__assign, .metric-binding__period { grid-template-columns: 1fr; }
  .metric-binding__filter-row :deep(.el-select), .metric-binding__filter-row :deep(.el-input) { width: 100%; }
}
</style>
