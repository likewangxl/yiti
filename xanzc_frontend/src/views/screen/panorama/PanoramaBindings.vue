<template>
  <main v-bp-overflow-tooltip class="panorama-bindings bp-crud" aria-labelledby="panorama-bindings-title">
    <header class="page-h panorama-bindings__header">
      <div>
        <span class="panorama-bindings__eyebrow">大屏管理</span>
        <h1 id="panorama-bindings-title">大屏组件配置</h1>
        <p>选择大屏和组件，配置标题、数据来源及展示字段后保存草稿。</p>
      </div>
      <div class="panorama-bindings__actions">
        <el-button v-if="hasLocalChanges" data-testid="binding-cancel" :disabled="writing || loading" @click="cancelLocalChanges">取消修改</el-button>
        <el-button data-testid="binding-preview" title="请先保存草稿再预览" :disabled="!screenReady || writing || hasLocalChanges" @click="preview">预览</el-button>
        <el-button data-testid="binding-save" type="primary" :loading="saving" :disabled="writing || !screenReady" @click="saveDraft">保存草稿</el-button>
      </div>
    </header>

    <p v-if="error" class="panorama-bindings__error" role="alert">{{ error }}</p>
    <p v-if="conflict" class="panorama-bindings__conflict" data-testid="binding-conflict" role="alert">{{ conflict }}</p>
    <p v-if="loading" class="panorama-bindings__loading" role="status">正在读取服务端屏配置…</p>

    <section class="card-section panorama-bindings__screen-bar">
      <label for="panorama-screen-select">经营大屏</label>
      <select id="panorama-screen-select" data-testid="screen-select" v-model="activeScreenId" :disabled="writing || loading" @change="changeActiveScreen">
        <option value="">请选择已存在的大屏</option>
        <option v-for="screen in screens" :key="screen.id" :value="String(screen.id)">
          {{ screen.screenName || screen.screen_name || screen.screenCode || `屏幕 #${screen.id}` }}
        </option>
      </select>
      <span v-if="activeScreen" class="panorama-bindings__screen-meta">
        {{ activeScreen.screenCode || activeScreen.screen_code }} · {{ activeScreen.bizLine || activeScreen.biz_line || 'COMMON' }}
      </span>
    </section>

    <div v-if="legacyComponents.length" class="panorama-bindings__legacy" data-testid="legacy-conversion-warning">
      <strong>检测到旧布局或其他模板的组件</strong>
      <span>保存会替换当前草稿组件树；已发布快照不会自动修改。请先确认已核对当前已发布版本。</span>
      <label>
        <input v-model="conversionAccepted" type="checkbox" data-testid="conversion-confirm" @change="markLocalDirty" />
        我确认已保留/核对当前已发布版本，并允许替换草稿组件树
      </label>
    </div>

    <section v-if="screenReady" class="card-section panorama-bindings__workbench" aria-label="大屏组件配置">
      <div class="panorama-bindings__section-head">
        <div>
          <h2>组件配置</h2>
          <p>左侧选择已存在的展示内容，右侧修改标题和数据绑定。</p>
        </div>
        <span v-if="hasLocalChanges" class="panorama-bindings__dirty" role="status">有未保存修改</span>
      </div>

      <div class="panorama-bindings__body">
        <nav class="panorama-bindings__slots" aria-label="经营指标区域">
          <h3>{{ hasNewDisplayPresentation ? '可见组件' : '展示内容' }}</h3>
          <template v-if="hasNewDisplayPresentation">
            <button v-for="entry in displayComponents" :key="entry.component.componentId" type="button"
                    :class="{ 'is-active': selectedComponentId === entry.component.componentId }"
                    :data-component-id="entry.component.componentId" @click="selectDisplayComponent(entry.component.componentId)">
              <span>{{ displayComponentLabel(entry) }}</span>
              <small>{{ displayComponentTypeLabel(entry.component) }}</small>
            </button>
          </template>
          <template v-else>
            <button v-for="slot in slotOrder" :key="slot" type="button"
                    :class="{ 'is-active': selectedSlot === slot, 'is-bound': Boolean(bindingState[slot]?.dsId) }"
                    :data-testid="`slot-${slot}`" @click="selectSlot(slot)">
              <span>{{ presentationLabel(slot) }}</span>
              <small>{{ slotStatusLabel(slot) }}</small>
            </button>
            <button v-if="isBranchTemplate && !slotOrder.includes('loanRate')" type="button"
                    :class="{ 'is-active': selectedSlot === 'loanRate', 'is-bound': Boolean(bindingState.loanRate?.dsId) }"
                    data-testid="slot-loanRate" @click="selectSlot('loanRate')">
              <span>{{ presentationLabel('loanRate') }}</span>
              <small>可选·待配置</small>
            </button>
          </template>
        </nav>

        <main class="panorama-bindings__editor" aria-live="polite">
          <template v-if="selectedSpec">
            <header class="panorama-bindings__editor-head">
              <div>
                <h2>{{ hasNewDisplayPresentation ? displayComponentLabel(selectedDisplayComponentEntry) : selectedSpec.label }}</h2>
                <p data-testid="binding-guidance">{{ selectedGuidance }}</p>
              </div>
              <span v-if="selectedBinding?.dsId" class="panorama-bindings__bound">已选择数据来源<span v-if="autoReviewSlots.has(selectedSlot)">·待核验</span></span>
            </header>

            <label class="panorama-bindings__field-label" for="panorama-component-title">标题</label>
            <input id="panorama-component-title" data-testid="component-title" :value="componentTitle"
                   placeholder="留空恢复默认标题" :disabled="writing || loading" @input="updateComponentTitle($event.target.value)" />
            <p class="panorama-bindings__hint">{{ componentDefaultTitle }}；清空后恢复默认标题。</p>

            <template v-if="hasNewDisplayPresentation && selectedDisplayFieldMappings.length">
              <div v-for="mapping in selectedDisplayFieldMappings" :key="mapping.key" class="panorama-bindings__component-field">
                <label class="panorama-bindings__field-label" :for="mapping.testId">{{ mapping.label }}</label>
                <select :id="mapping.testId" :data-testid="mapping.testId"
                        :disabled="writing || loading || !selectedDatasource"
                        :value="mapping.value" @change="setComponentField(mapping, $event.target.value)">
                  <option value="">跟随组件默认字段</option>
                  <option v-for="option in componentFieldOptions(mapping)" :key="option.col" :value="option.col">
                    {{ option.label }}（{{ option.col }}）
                  </option>
                </select>
              </div>
              <p v-if="selectedDisplayFieldMissing" class="panorama-bindings__hint">当前展示字段未出现在数据来源已声明的指标候选中，保存时保留原配置。</p>
              <p v-if="isSchemaMetricCard" data-testid="component-display-unit" class="panorama-bindings__hint">展示单位：{{ displayUnitLabel(selectedDisplayUnit) }}（沿用当前组件格式）</p>
            </template>

            <div v-if="selectedSlot === 'composition'" class="panorama-bindings__composition-mode">
              <label class="panorama-bindings__field-label" for="panorama-composition-mode">构成模式</label>
              <select id="panorama-composition-mode" data-testid="composition-mode"
                      :value="compositionMode" :disabled="writing || loading" @change="setCompositionMode($event.target.value)">
                <option value="rows">行模式（名称 + 构成值）</option>
                <option value="columns">双列模式（对公 + 零售）</option>
              </select>
              <p data-testid="composition-mode-hint" class="panorama-bindings__hint">
                {{ compositionMode === 'columns'
                  ? '双列模式要求数据源返回恰好一行，固定对公/零售标签；总量/分母可选但必须与两列同类且明确为指标。'
                  : '行模式允许多行，每行必须包含构成名称和构成值。' }}
              </p>
            </div>

            <label class="panorama-bindings__field-label" for="panorama-datasource">数据来源</label>
            <PanoramaDatasourcePicker
              id="panorama-datasource"
              data-testid="slot-datasource"
              :sources="availableDatasources"
              :disabled="writing || loading"
              v-model="selectedDatasourceId"
              @change="onDatasourceChange"
            />
            <p data-testid="datasource-option-count" class="panorama-bindings__hint">当前屏可选数据源：{{ selectableDatasourceCount }} 个数据来源</p>
            <p v-if="sharedDatasourceComponentCount > 1" class="panorama-bindings__hint">修改数据来源会影响共用来源的其他组件，标题和展示字段仍分别保存。</p>
            <p v-if="!selectableDatasourceCount" class="panorama-bindings__hint">当前屏范围没有可用的数据来源。</p>
            <p v-if="!selectedDatasource" data-testid="datasource-required-hint" class="panorama-bindings__hint">请先选择数据源（数据来源），再配置展示字段和单位。</p>
            <p v-else-if="selectedFieldSpecs.length && !hasApplicableFieldOptions" data-testid="datasource-fields-unavailable-hint" class="panorama-bindings__hint">已选择数据来源，但没有适用字段候选（展示字段），请核对字段角色或元数据。</p>

            <div v-if="!hasNewDisplayPresentation" class="panorama-bindings__fields">
              <div v-for="fieldSpec in selectedFieldSpecs" :key="fieldSpec.semantic" class="panorama-bindings__field-row">
                <label :for="`panorama-field-${fieldSpec.semantic}`">
                  {{ fieldSpec.label }}<em v-if="fieldSpec.required">需要配置</em>
                </label>
                <select :id="`panorama-field-${fieldSpec.semantic}`"
                        :data-testid="`field-option-${selectedSlot}-${fieldSpec.semantic}`"
                        :disabled="!selectedDatasource || writing || loading"
                        :value="selectedBinding.fields[fieldSpec.semantic] || ''"
                        @change="setField(fieldSpec.semantic, $event.target.value)">
                  <option value="">待配置</option>
                  <option v-for="option in fieldOptionsFor(fieldSpec.semantic)" :key="option.col" :value="option.col">
                    {{ option.label }}（{{ option.col }}）
                  </option>
                </select>
                <select v-if="fieldSpec.kind !== 'dimension' && fieldSpec.unitKinds?.length"
                        :data-testid="`unit-${selectedSlot}-${fieldSpec.semantic}`"
                        :disabled="!selectedDatasource || writing || loading"
                        :value="unitFor(fieldSpec.semantic)"
                        @change="setUnit(fieldSpec.semantic, $event.target.value)">
                  <option value="">未知单位</option>
                  <option v-for="unit in fieldSpec.unitKinds" :key="unit" :value="unit">{{ unitLabel(unit) }}</option>
                </select>
                <small v-if="unitHint(fieldSpec.semantic)" class="panorama-bindings__unit-hint">{{ unitHint(fieldSpec.semantic) }}</small>
              </div>
            </div>

            <label class="panorama-bindings__field-label" for="panorama-period">周期</label>
            <select id="panorama-period" data-testid="binding-period" :disabled="writing || loading"
                    :value="selectedBinding.period" @change="setPeriod($event.target.value)">
              <option v-for="period in PERIOD_VALUES" :key="period" :value="period">{{ PERIOD_LABELS[period] }}</option>
            </select>
            <p class="panorama-bindings__hint">字段候选来自数据来源已保存的字段说明；单位缺失时不会猜测，可随时修改配置。</p>
            <p v-if="selectedSlot === 'citySummary'" class="panorama-bindings__hint">城市汇总必须选择已按城市汇总的数据源；系统不会把支行机构数据相加成城市指标。</p>
          </template>
          <p v-else>请选择一个展示内容。</p>
        </main>
      </div>
    </section>

    <footer class="panorama-bindings__footer">
      <span>当前版本 {{ canvasVersion ?? '-' }}</span>
    </footer>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  getScreenCanvas,
  listScreenDatasources,
  listScreens,
  saveScreenCanvas
} from '@/api/screen';
import { parseDatasourceConfig } from '../designer/widgets/chart-widget/dsFilter';
import {
  BINDING_SLOTS,
  BRANCH_OPTIONAL_SLOT_ORDER,
  PERIOD_LABELS,
  PERIOD_VALUES,
  SLOT_ORDER,
  buildCodeComponents,
  getCompositionMode,
  getDatasourceFieldOptions,
  normalizeBinding,
  validateBinding
} from './bindings';
import { RETAIL_SLOT_ORDER, RETAIL_BINDING_SLOTS } from './retailBindings';
import {
  CORPORATE_BINDING_SLOTS,
  CORPORATE_SLOT_ORDER,
  CORPORATE_TEMPLATE
} from './corporateBindings';
import {
  applyDefaultBindings,
  bindingPreview,
  prefillBindingForDatasource,
  resolveDefaultBinding,
  AUTO_BIND_STATUS
} from './defaultBindings';
import PanoramaDatasourcePicker from './PanoramaDatasourcePicker.vue';
import { buildBusinessSourceCandidates } from '../presentation/sources/businessSourceCandidates';
import {
  commitSnapshot,
  createPresentationEditorSession,
  serializeEditorSession,
  setComponentTitle as setModelComponentTitle,
  updateComponent as updateModelComponent,
  updateComponentContent as updateModelComponentContent
} from '../presentation/editor/presentationEditorModel';

const props = defineProps({ screenId: { type: [Number, String], default: '' } });
const emit = defineEmits(['saved', 'published', 'discarded', 'preview', 'error']);
const route = useRoute();
const router = useRouter();

const screens = ref([]);
const activeScreenId = ref('');
const activeScreen = ref(null);
const canvas = ref(null);
const canvasStyle = ref({});
const draftComponents = ref([]);
const datasources = ref([]);
const selectedSlot = ref(SLOT_ORDER[0]);
const selectedComponentId = ref('');
const bindingState = reactive({});
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const conflict = ref('');
const conversionAccepted = ref(false);
const screenReady = ref(false);
const compositionMode = ref('rows');
const autoNotice = ref('');
const autoGaps = ref([]);
const autoReviewSlots = reactive(new Set());
const manualSlots = reactive(new Set());
const localDirty = ref(false);
let loadGeneration = 0;
let disposed = false;
const displaySession = ref(createPresentationEditorSession({}, { type: 'CODE', template: 'branch-overview-v1' }));

const selectedTemplate = ref('branch-overview-v1');
const isRetailTemplate = computed(() => selectedTemplate.value === 'retail-overview-v1');
const isCorporateTemplate = computed(() => selectedTemplate.value === CORPORATE_TEMPLATE);
const isBranchTemplate = computed(() => !isRetailTemplate.value && !isCorporateTemplate.value);
const isRetailScreen = computed(() => String(activeScreen.value?.bizLine || activeScreen.value?.biz_line || '').toUpperCase() === 'RETAIL');
const isCorporateScreen = computed(() => String(activeScreen.value?.bizLine || activeScreen.value?.biz_line || '').toUpperCase() === 'CORP');
const slotOrder = computed(() => {
  const base = isCorporateTemplate.value
    ? CORPORATE_SLOT_ORDER
    : isRetailTemplate.value ? RETAIL_SLOT_ORDER : SLOT_ORDER;
  if (!isBranchTemplate.value) return base;
  const hasOptionalBinding = BRANCH_OPTIONAL_SLOT_ORDER.some(slot => {
    const binding = bindingState[slot];
    return selectedSlot.value === slot || Boolean(binding?.dsId)
      || Object.keys(binding?.fields || {}).length > 0;
  });
  return hasOptionalBinding ? [...base, ...BRANCH_OPTIONAL_SLOT_ORDER] : base;
});
function changeActiveScreen() {
  if (hasLocalChanges.value) {
    conflict.value = '存在未保存的组件配置，请先保存草稿或取消修改后再切换大屏。';
    activeScreenId.value = String(activeScreen.value?.id || '');
    return false;
  }
  return loadCanvas(activeScreenId.value);
}
const writing = computed(() => saving.value);
const hasLocalChanges = computed(() => localDirty.value || Boolean(displaySession.value?.dirty));
const templateSpec = slot => (isCorporateTemplate.value
  ? CORPORATE_BINDING_SLOTS[slot]
  : isRetailTemplate.value ? RETAIL_BINDING_SLOTS[slot] : BINDING_SLOTS[slot]);
const selectedSpec = computed(() => templateSpec(selectedSlot.value) || null);
const selectedBinding = computed(() => {
  if (!bindingState[selectedSlot.value]) bindingState[selectedSlot.value] = emptyBinding(selectedSlot.value);
  return bindingState[selectedSlot.value];
});
const selectedFieldSpecs = computed(() => {
  if (!selectedSpec.value) return [];
  if (selectedSlot.value !== 'composition') return selectedSpec.value.fields || [];
  const semantics = compositionMode.value === 'columns'
    ? ['corporate', 'retail', 'total', 'corporateLoan', 'retailLoan', 'totalLoan', 'intermediaryIncome', 'operatingRevenue'] : ['name', 'value'];
  return (selectedSpec.value.fields || []).filter(fieldSpec => semantics.includes(fieldSpec.semantic));
});
const selectedDatasource = computed(() => datasources.value.find(item => String(item.id) === String(selectedBinding.value.dsId)) || null);
const displayComponents = computed(() => {
  if (!hasNewDisplayPresentation.value) return [];
  const sourceComponents = Array.isArray(displaySession.value?.presentation?.display?.components)
    ? displaySession.value.presentation.display.components : [];
  const draftByBlock = new Map(draftComponents.value
    .filter(item => Number.isSafeInteger(Number(item?.blockId)) && Number(item.blockId) > 0)
    .map(item => [Number(item.blockId), item]));
  return sourceComponents.filter(component => component?.visible !== false).map(component => {
    const ref = (Array.isArray(component.dataRefs) ? component.dataRefs : [])
      .find(item => Number.isSafeInteger(Number(item?.blockId)) && Number(item.blockId) > 0);
    const blockId = Number(ref?.blockId);
    const draft = draftByBlock.get(blockId);
    const bindingKey = String(draft?.propValue?.bindingKey || '').trim();
    if (!bindingKey) return null;
    return { component, blockId, bindingKey, draft };
  }).filter(Boolean);
});
const selectedDisplayComponentEntry = computed(() => displayComponents.value
  .find(entry => entry.component.componentId === selectedComponentId.value) || displayComponents.value[0] || null);
const selectedDisplayComponent = computed(() => selectedDisplayComponentEntry.value?.component || null);
const isSchemaMetricCard = computed(() => hasNewDisplayPresentation.value
  && ['METRIC_CARD', 'COMPLETION'].includes(selectedDisplayComponent.value?.componentType));
const selectedDisplayFieldMappings = computed(() => {
  if (!hasNewDisplayPresentation.value || !selectedDisplayComponent.value) return [];
  const component = selectedDisplayComponent.value;
  const content = component.content || {};
  if (['METRIC_CARD', 'COMPLETION', 'MAP'].includes(component.componentType)
      && Object.prototype.hasOwnProperty.call(content, 'mainField')) {
    return [{ key: 'mainField', kind: 'mainField', label: '展示字段', value: String(content.mainField || ''), testId: 'component-main-field' }];
  }
  if (component.componentType === 'TREND') {
    return (Array.isArray(content.series) ? content.series : []).map((item, index) => ({
      key: `series-${item.seriesKey || index}`, kind: 'series', index,
      label: item.label || item.seriesKey || `趋势序列 ${index + 1}`,
      value: String(item.field || ''), testId: `component-series-field-${item.seriesKey || index}`
    }));
  }
  if (component.componentType === 'COMPOSITION_TABS') {
    const fields = ['corporateField', 'retailField', 'totalField'];
    return (Array.isArray(content.tabs) ? content.tabs : []).flatMap((item, index) => fields
      .filter(field => Object.prototype.hasOwnProperty.call(item || {}, field))
      .map(field => ({ key: `tab-${item.tabKey || index}-${field}`, kind: 'tabs', index, field,
        label: `${item.label || item.tabKey || '构成'}·${field.replace('Field', '')}`,
        value: String(item[field] || ''), testId: `component-tab-field-${item.tabKey || index}-${field}` })));
  }
  if (component.componentType === 'RANKING') {
    return (Array.isArray(content.rankingMetrics) ? content.rankingMetrics : []).map((item, index) => ({
      key: `ranking-${item.metricKey || index}`, kind: 'rankingMetrics', index,
      label: item.label || item.metricKey || `排名指标 ${index + 1}`,
      value: String(item.field || ''), testId: `component-ranking-field-${item.metricKey || index}`
    }));
  }
  if (component.componentType === 'DETAIL_TABLE') {
    return (Array.isArray(content.columns) ? content.columns : []).map((item, index) => ({
      key: `column-${item.columnKey || index}`, kind: 'columns', index,
      label: item.label || item.columnKey || `表格列 ${index + 1}`,
      value: String(item.field || ''), testId: `component-column-field-${item.columnKey || index}`
    }));
  }
  return [];
});
const selectedMainField = computed(() => selectedDisplayFieldMappings.value[0]?.value || '');
function componentMappingItem(mapping) {
  const component = selectedDisplayComponent.value;
  if (!component || !mapping) return null;
  if (mapping.kind === 'mainField') return component.content || {};
  return Array.isArray(component.content?.[mapping.kind]) ? component.content[mapping.kind][mapping.index] || null : null;
}

function componentFieldOptions(mapping) {
  if (!selectedDatasource.value || !mapping) return [];
  const columnKey = String(componentMappingItem(mapping)?.columnKey || '');
  const dimensionColumns = new Set(['orgCode', 'orgName', 'cityCode', 'cityName', 'ownerOperatingOrgCode', 'parentOrgCode', 'coordSys', 'located', 'label']);
  const expectedRole = mapping.kind === 'columns' && dimensionColumns.has(columnKey) ? 'DIM' : 'METRIC';
  const currentUnit = String(componentMappingItem(mapping)?.unit || selectedDisplayComponent.value?.dataRefs?.[0]?.unit || '');
  const currentKind = unitKindOf(currentUnit);
  return getDatasourceFieldOptions(selectedDatasource.value)
    .filter(option => String(option.role || '').toUpperCase() === expectedRole)
    .filter(option => !currentKind || !option.unit
      || (mapping.kind === 'tabs'
        ? String(option.unit).toUpperCase() === currentUnit.toUpperCase()
        : unitKindOf(option.unit) === currentKind));
}

const selectedDisplayFieldOptions = computed(() => componentFieldOptions(selectedDisplayFieldMappings.value[0]));
const selectedDisplayFieldMissing = computed(() => Boolean(
  selectedMainField.value && !selectedDisplayFieldOptions.value.some(option => option.col === selectedMainField.value)
));
const sharedDatasourceComponentCount = computed(() => hasNewDisplayPresentation.value
  ? displayComponents.value.filter(entry => entry.bindingKey === selectedSlot.value).length : 0);
const selectedDisplayUnit = computed(() => String(selectedDisplayComponent.value?.format?.displayUnit || 'AUTO'));
const selectedDatasourceId = computed({
  get: () => selectedBinding.value.dsId ? String(selectedBinding.value.dsId) : '',
  set: value => { selectedBinding.value.dsId = value ? Number(value) : null; }
});
function fieldOptionsForBinding(slot, semantic, dsId) {
  const fieldSpec = templateSpec(slot)?.fields?.find(item => item.semantic === semantic);
  if (!fieldSpec) return [];
  const source = datasources.value.find(item => String(item.id) === String(dsId));
  const expectedRole = fieldSpec.kind === 'dimension' ? 'DIM' : 'METRIC';
  // A date/subject dimension must not become a numeric metric, and a metric
  // must not become a trend axis. Unknown roles fail closed.
  const options = getDatasourceFieldOptions(source || {})
    .filter(option => String(option.role || '').toUpperCase() === expectedRole);
  // NAMED_GROUP city summaries resolve identity through the authorized
  // institution directory.  Only the engine's built-in ORG_INDEX_RESULT
  // org_code dimension is safe here; a configured org_name (or a free-form
  // dimension) cannot establish an institution identity.
  if (['citySummary', 'retailRanking', 'corpRanking', ...(isRetailTemplate.value || isCorporateTemplate.value ? ['branches'] : [])].includes(slot) && semantic === 'orgCode'
      && String(screenScope.value.orgScopeMode).toUpperCase() === 'NAMED_GROUP') {
    return options.filter(option => option.col === 'org_code' && option.builtin === true);
  }
  return options;
}

function fieldOptionsFor(semantic) {
  return fieldOptionsForBinding(selectedSlot.value, semantic, selectedBinding.value.dsId);
}
const hasApplicableFieldOptions = computed(() => {
  if (!selectedDatasource.value) return false;
  const requiredSemantics = new Set([
    ...(selectedSpec.value?.required || []),
    ...(selectedSpec.value?.oneOfRequired || []),
    ...(selectedSpec.value?.atLeastOneOf || [])
  ]);
  const candidates = requiredSemantics.size
    ? selectedFieldSpecs.value.filter(fieldSpec => requiredSemantics.has(fieldSpec.semantic))
    : selectedFieldSpecs.value;
  return candidates.some(fieldSpec => fieldOptionsFor(fieldSpec.semantic).length > 0);
});
const canvasVersion = computed(() => canvas.value?.canvasVersion ?? null);

const isCodePresentation = computed(() => canvasStyle.value?.presentation?.type === 'CODE'
  && canvasStyle.value?.presentation?.template === selectedTemplate.value);
const hasNewDisplayPresentation = computed(() => canvasStyle.value?.presentation?.displaySchemaVersion === 1);

// 迁移候选识别不复用 isCodePresentation：旧 CODE 包本身也可能没有新展示子协议。
const malformedLegacyDraft = computed(() => {
  const raw = canvas.value?.canvasDraftJson;
  if (typeof raw !== 'string' || !raw.trim()) return false;
  try { JSON.parse(raw); return false; } catch { return true; }
});
const migrationCandidate = computed(() => {
  if (hasNewDisplayPresentation.value) return [];
  if (draftComponents.value.length) return draftComponents.value;
  return malformedLegacyDraft.value ? [{}] : [];
});
// 保留旧 CODE 绑定保存链的原有语义：只有非 CODE 旧布局才进入旧组件替换确认门禁。
const legacyComponents = computed(() => isCodePresentation.value ? [] : migrationCandidate.value);

const screenScope = computed(() => ({
  viewLevel: activeScreen.value?.viewLevel || activeScreen.value?.view_level || 'BRANCH',
  bizLine: activeScreen.value?.bizLine || activeScreen.value?.biz_line || 'COMMON',
  orgScopeMode: activeScreen.value?.orgScopeMode || activeScreen.value?.org_scope_mode || 'LEGACY_CONTEXT',
  orgGroupCode: activeScreen.value?.orgGroupCode || activeScreen.value?.org_group_code || ''
}));

const autoPreview = computed(() => bindingPreview({
  template: selectedTemplate.value,
  screenScope: screenScope.value,
  datasources: datasources.value,
  bindings: bindingState,
  slots: slotOrder.value
}));

function presentationLabel(slot) {
  return templateSpec(slot)?.label || slot;
}

function displayComponentLabel(entry) {
  const component = entry?.component || entry;
  const title = component?.text?.titleMode === 'CUSTOM' ? String(component.text.title || '').trim() : '';
  const bindingKey = entry?.bindingKey || selectedSlot.value;
  const context = String(component?.componentId || '').startsWith('business-retail-')
    ? '零售' : String(component?.componentId || '').startsWith('business-corp-') ? '对公' : '';
  const label = title || component?.dataRefs?.[0]?.metricName || presentationLabel(bindingKey) || component?.componentId || '未命名组件';
  return context ? `${label}（${context}）` : label;
}

function displayComponentTypeLabel(component = {}) {
  return ({ METRIC_CARD: '指标卡', TREND: '趋势', RANKING: '排名', MAP: '地图', COMPOSITION_TABS: '业务构成', DETAIL_TABLE: '明细表' })[component.componentType]
    || component.componentType || '组件';
}

const componentTitle = computed(() => {
  if (hasNewDisplayPresentation.value && selectedDisplayComponent.value) {
    return selectedDisplayComponent.value.text?.titleMode === 'CUSTOM'
      ? String(selectedDisplayComponent.value.text.title || '') : '';
  }
  return String(canvasStyle.value?.metricLabels?.[selectedSlot.value] || '');
});

const componentDefaultTitle = computed(() => {
  if (!hasNewDisplayPresentation.value) return presentationLabel(selectedSlot.value);
  const entry = selectedDisplayComponentEntry.value;
  return entry?.component?.dataRefs?.[0]?.metricName
    || presentationLabel(entry?.bindingKey || selectedSlot.value)
    || displayComponentTypeLabel(entry?.component);
});

function markLocalDirty() {
  localDirty.value = true;
}

function updateComponentTitle(value) {
  const nextValue = String(value ?? '');
  if (hasNewDisplayPresentation.value && selectedDisplayComponent.value) {
    try {
      displaySession.value = setModelComponentTitle(
        displaySession.value,
        selectedDisplayComponent.value.componentId,
        nextValue
      );
    } catch (titleError) {
      error.value = titleError?.message || '标题配置无效';
      emit('error', titleError);
      return;
    }
  } else {
    const metricLabels = { ...(canvasStyle.value?.metricLabels || {}) };
    const trimmed = nextValue.trim();
    if (trimmed) metricLabels[selectedSlot.value] = trimmed;
    else delete metricLabels[selectedSlot.value];
    canvasStyle.value = { ...canvasStyle.value, metricLabels };
  }
  conflict.value = '';
  markLocalDirty();
}

function setComponentField(mapping, value) {
  if (!hasNewDisplayPresentation.value || !selectedDisplayComponent.value || !mapping) return;
  try {
    const nextValue = String(value || '');
    const sourceOption = componentFieldOptions(mapping).find(option => option.col === nextValue);
    const sourceUnit = ['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION', 'COUNT', 'TEN_THOUSAND_COUNT', 'PERCENT', 'RATIO']
      .includes(String(sourceOption?.unit || '').toUpperCase()) ? String(sourceOption.unit).toUpperCase() : '';
    const patch = {};
    if (mapping.kind === 'mainField') {
      patch.mainField = nextValue;
    } else {
      const current = Array.isArray(selectedDisplayComponent.value.content?.[mapping.kind])
        ? selectedDisplayComponent.value.content[mapping.kind].map(item => ({ ...item })) : [];
      const item = { ...(current[mapping.index] || {}) };
      if (mapping.kind === 'tabs') item[mapping.field] = nextValue;
      else item.field = nextValue;
      if (sourceUnit && mapping.kind !== 'tabs') item.unit = sourceUnit;
      current[mapping.index] = item;
      patch[mapping.kind] = current;
    }
    let nextSession = updateModelComponentContent(
      displaySession.value,
      selectedDisplayComponent.value.componentId,
      patch
    );
    if (mapping.kind === 'mainField' && sourceUnit) {
      const refs = (Array.isArray(selectedDisplayComponent.value.dataRefs)
        ? selectedDisplayComponent.value.dataRefs : []).map(ref => ({ ...ref }));
      if (refs.length) {
        refs[0].unit = sourceUnit;
        nextSession = updateModelComponent(nextSession, selectedDisplayComponent.value.componentId, { dataRefs: refs });
      }
    }
    displaySession.value = nextSession;
    conflict.value = '';
    markLocalDirty();
  } catch (fieldError) {
    error.value = fieldError?.message || '展示字段配置无效';
    emit('error', fieldError);
  }
}

function slotStatusLabel(slot) {
  if (autoReviewSlots.has(slot)) return '已配置·待核验';
  return bindingState[slot]?.dsId ? '已配置' : '待配置';
}

function fieldLabel(slot, semantic) {
  return templateSpec(slot)?.fields?.find(item => item.semantic === semantic)?.label || semantic;
}

const selectedGuidance = computed(() => {
  if (!selectedSpec.value) return '请选择展示内容';
  if (selectedSlot.value === 'composition') {
    return compositionMode.value === 'columns'
      ? '对公金额/零售金额均需绑定；总量/分母可选，若配置必须是同类且有明确单位的指标，来源必须返回一行双列数据。'
      : '构成名称/构成值均需绑定（两项都需要配置），来源必须明确声明对应字段。';
  }
  const labels = (selectedSpec.value.required || []).map(semantic => fieldLabel(selectedSlot.value, semantic));
  const oneOf = (selectedSpec.value.oneOfRequired || []).map(semantic => fieldLabel(selectedSlot.value, semantic));
  const anyOf = (selectedSpec.value.atLeastOneOf || []).map(semantic => fieldLabel(selectedSlot.value, semantic));
  const parts = [];
  if (labels.length) parts.push(`${labels.join('、')}需要配置`);
  if (oneOf.length) parts.push(`身份字段请选择${oneOf.join('或')}`);
  if (anyOf.length) parts.push(`${anyOf.join('、')}至少配置一项`);
  return parts.join('；') || '可按需要配置展示字段，单位必须有明确依据。';
});

function isCompositionColumnsDatasource(source = {}) {
  const config = parseDatasourceConfig(source.configJson);
  const sourceKind = String(source.sourceKind || source.source_kind || '').trim().toUpperCase();
  return sourceKind === 'WIDE_TABLE' && config?.table === 'ORG_INDEX_RESULT';
}

/** 业务候选模型统一解释后端已支持的范围组合，并保留不兼容来源的禁用原因。 */
const scopedDatasources = computed(() => datasources.value
    .filter(source => !isRetailTemplate.value || String(source.bizLine || source.biz_line || '').toUpperCase() === 'RETAIL')
    .filter(source => !isCorporateTemplate.value || String(source.bizLine || source.biz_line || '').toUpperCase() === 'CORP')
  .reduce((list, source) => {
  if (selectedSlot.value !== 'composition' || compositionMode.value !== 'columns') return list.concat(source);
  if (isCompositionColumnsDatasource(source)) return list.concat(source);
  // Keep an already persisted incompatible source visible and marked so the
  // user can see the existing binding and replace it; no new invalid source
  // enters the selectable list.
  if (String(source.id) === String(selectedBinding.value?.dsId)) {
    return list.concat({ ...source, __compositionColumnsUnsupported: true });
  }
  return list;
}, []));
const availableDatasources = computed(() => buildBusinessSourceCandidates(scopedDatasources.value, {
  screenBizLine: screenScope.value.bizLine,
  scopeMode: screenScope.value.orgScopeMode,
  expectedShape: selectedSpec.value?.innerType === 'LINE_TREND' ? 'TIMESERIES' : 'SINGLE'
}));
const selectableDatasourceCount = computed(() => availableDatasources.value
  .filter(source => !source.__compositionColumnsUnsupported && !source.disabled).length);

function emptyBinding(slot = '') {
  return {
    dsId: null,
    period: slot === 'trend' || slot === 'branchTrend' || slot === 'retailTrend' || slot === 'corpTrend'
      ? 'LAST_6M_EOM' : 'LATEST',
    fields: {},
    units: {}
  };
}

function writeBinding(slot, binding) {
  if (!binding) return;
  bindingState[slot] = {
    ...emptyBinding(slot),
    ...binding,
    fields: { ...(binding.fields || {}) },
    units: { ...(binding.units || {}) }
  };
}

function autoInput(slot = selectedSlot.value, existing = bindingState[slot]) {
  return {
    slot,
    template: selectedTemplate.value,
    screenScope: screenScope.value,
    datasources: datasources.value,
    existingBinding: existing,
    // 用户明确切换过构成模式后尊重其选择；初次进入时让默认解析器根据
    // 固定对公/零售列契约尝试双列模式，不把列式来源误当成行式来源。
    mode: slot === 'composition' && manualSlots.has(slot) ? compositionMode.value : undefined
  };
}

function applyDefaultToSlot(slot = selectedSlot.value) {
  if (!screenReady.value || !slot || manualSlots.has(slot)) return null;
  const current = bindingState[slot] || emptyBinding(slot);
  if (current.dsId || Object.keys(current.fields || {}).length) return null;
  const result = resolveDefaultBinding(autoInput(slot, current));
  if (result.status === AUTO_BIND_STATUS.APPLIED && result.binding) {
    writeBinding(slot, result.binding);
    if (slot === 'composition' && result.binding.fields?.corporate && result.binding.fields?.retail) {
      compositionMode.value = 'columns';
    }
    autoReviewSlots.add(slot);
    autoGaps.value = autoGaps.value.filter(item => item.slot !== slot);
    autoNotice.value = `${presentationLabel(slot)}已配置·待核验：请核对来源范围和返回结果。`;
  } else if (result.gap) {
    const existing = autoGaps.value.filter(item => item.slot !== slot);
    autoGaps.value = [...existing, { slot, status: result.status, message: result.gap }];
    autoNotice.value = `${presentationLabel(slot)}暂未自动配置：${result.gap}`;
  }
  return result;
}

function applyDefaultsToEmpty() {
  if (!screenReady.value) return null;
  const result = applyDefaultBindings({
    template: selectedTemplate.value,
    screenScope: screenScope.value,
    datasources: datasources.value,
    bindings: bindingState,
    slots: slotOrder.value
  });
  for (const slot of result.applied) {
    writeBinding(slot, result.bindings[slot]);
    if (slot === 'composition' && !manualSlots.has(slot)
        && result.bindings[slot]?.fields?.corporate && result.bindings[slot]?.fields?.retail) {
      compositionMode.value = 'columns';
    }
    autoReviewSlots.add(slot);
  }
  for (const slot of result.preserved) autoReviewSlots.delete(slot);
  autoGaps.value = result.gaps;
  autoNotice.value = result.applied.length
    ? `已为 ${result.applied.length} 项空白展示内容应用明确候选；${result.gaps.length} 项仍需处理，已配置内容未被覆盖。`
    : (result.gaps.length ? `没有新增自动配置；${result.gaps.length} 项展示内容存在缺口或歧义。` : '没有空白展示内容。');
  return result;
}

function parse(value, fallback = {}) {
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value;
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : fallback;
  } catch { return fallback; }
}

function screenIdValue(value) {
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}

function requestedScreenId() {
  return screenIdValue(props.screenId) || screenIdValue(route?.query?.screenId);
}

function clearCanvasState() {
  canvas.value = null;
  canvasStyle.value = {};
  draftComponents.value = [];
  datasources.value = [];
  for (const key of Object.keys(bindingState)) delete bindingState[key];
  selectedSlot.value = SLOT_ORDER[0];
  selectedComponentId.value = '';
  bindingState[selectedSlot.value] = emptyBinding(selectedSlot.value);
  compositionMode.value = 'rows';
  conversionAccepted.value = false;
  autoNotice.value = '';
  autoGaps.value = [];
  autoReviewSlots.clear();
  manualSlots.clear();
  localDirty.value = false;
  screenReady.value = false;
}

function resetBindings(components) {
  for (const key of Object.keys(bindingState)) delete bindingState[key];
  for (const component of Array.isArray(components) ? components : []) {
    const slot = component?.propValue?.bindingKey;
    if (!Object.prototype.hasOwnProperty.call(BINDING_SLOTS, slot)) continue;
    bindingState[slot] = normalizeBinding(parse(component.bindJson, {}), slot);
  }
  const first = slotOrder.value.find(slot => bindingState[slot]) || slotOrder.value[0];
  selectedSlot.value = first;
  if (!bindingState[first]) bindingState[first] = emptyBinding(first);
  compositionMode.value = getCompositionMode(bindingState.composition || {});
  autoReviewSlots.clear();
  manualSlots.clear();
  autoGaps.value = [];
  autoNotice.value = '';
}

async function loadScreens() {
  const list = await listScreens();
  if (!Array.isArray(list) || !list.length) throw new Error('未读取到可配置的大屏，已停止加载，不会新建未知屏幕');
  screens.value = list;
  const requested = requestedScreenId();
  const selected = requested && list.some(item => Number(item.id) === requested)
    ? requested : screenIdValue(list[0]?.id);
  if (!selected) throw new Error('屏列表缺少有效 ID，已停止加载');
  activeScreenId.value = String(selected);
  activeScreen.value = list.find(item => Number(item.id) === selected) || null;
}

async function loadCanvas(requestedId = activeScreenId.value) {
  const id = screenIdValue(requestedId);
  if (!id) return false;
  const token = ++loadGeneration;
  activeScreen.value = screens.value.find(screen => Number(screen.id) === id) || activeScreen.value;
  clearCanvasState();
  activeScreenId.value = String(id);
  loading.value = true;
  error.value = '';
  conflict.value = '';
  try {
    const [resp, sourceList] = await Promise.all([
      getScreenCanvas(id),
      listScreenDatasources()
    ]);
    if (disposed || token !== loadGeneration) return false;
    if (!resp || typeof resp !== 'object') throw new Error('服务端未返回当前屏草稿，已停止加载');
    canvas.value = resp;
    canvasStyle.value = parse(resp.canvasStyleJson, {});
    selectedTemplate.value = canvasStyle.value?.presentation?.template
      || (isCorporateScreen.value ? CORPORATE_TEMPLATE : isRetailScreen.value ? 'retail-overview-v1' : 'branch-overview-v1');
    displaySession.value = createPresentationEditorSession(canvasStyle.value?.presentation || {}, {
      type: 'CODE', template: selectedTemplate.value
    });
    const draft = parse(resp.canvasDraftJson, { components: [] });
    draftComponents.value = Array.isArray(draft.components) ? draft.components : [];
    resetBindings(draftComponents.value);
    datasources.value = Array.isArray(sourceList) ? sourceList : [];
    for (const binding of Object.values(bindingState)) fillProvenUnits(binding);
    selectedComponentId.value = displayComponents.value[0]?.component.componentId || '';
    if (selectedComponentId.value) {
      selectedSlot.value = displayComponents.value[0].bindingKey;
      if (!bindingState[selectedSlot.value]) bindingState[selectedSlot.value] = emptyBinding(selectedSlot.value);
    }
    conversionAccepted.value = legacyComponents.value.length === 0;
    localDirty.value = false;
    screenReady.value = true;
    return true;
  } catch (loadError) {
    if (disposed || token !== loadGeneration) return false;
    error.value = loadError?.message || '读取屏配置失败';
    emit('error', loadError);
    return false;
  } finally {
    if (!disposed && token === loadGeneration) loading.value = false;
  }
}

async function cancelLocalChanges() {
  if (!hasLocalChanges.value || writing.value || loading.value) return false;
  conflict.value = '';
  return loadCanvas(activeScreenId.value);
}

function selectSlot(slot) {
  if (!Object.prototype.hasOwnProperty.call(BINDING_SLOTS, slot)) return;
  selectedSlot.value = slot;
  if (!bindingState[slot]) bindingState[slot] = emptyBinding(slot);
  if (slot === 'composition' && !manualSlots.has(slot)) {
    const fields = bindingState[slot]?.fields || {};
    if (fields.corporate && fields.retail) compositionMode.value = 'columns';
    else if (fields.name || fields.value) compositionMode.value = 'rows';
  }
  conflict.value = '';
}

function selectDisplayComponent(componentId) {
  const entry = displayComponents.value.find(item => item.component.componentId === componentId);
  if (!entry) return;
  selectedComponentId.value = entry.component.componentId;
  selectedSlot.value = entry.bindingKey;
  if (!bindingState[selectedSlot.value]) bindingState[selectedSlot.value] = emptyBinding(selectedSlot.value);
  if (selectedSlot.value === 'composition' && !manualSlots.has(selectedSlot.value)) {
    compositionMode.value = getCompositionMode(bindingState[selectedSlot.value]);
  }
  conflict.value = '';
}

function setCompositionMode(value) {
  if (selectedSlot.value !== 'composition') return;
  const nextMode = value === 'columns' ? 'columns' : 'rows';
  const binding = selectedBinding.value;
  const obsolete = nextMode === 'columns' ? ['name', 'value'] : ['corporate', 'retail', 'total'];
  for (const semantic of obsolete) {
    delete binding.fields[semantic];
    delete binding.units[semantic];
  }
  compositionMode.value = nextMode;
  manualSlots.add(selectedSlot.value);
  autoReviewSlots.delete(selectedSlot.value);
  conflict.value = '';
  markLocalDirty();
}

function onDatasourceChange(value) {
  const nextId = value && typeof value === 'object' ? value.target?.value : value;
  if (nextId !== undefined) selectedDatasourceId.value = nextId;
  // 切换数据源后只保留新数据源仍声明的列；失效单位也必须删除，
  // 否则服务端会拒绝“单位指向未绑定字段”的 bindJson。
  const binding = selectedBinding.value;
  for (const semantic of Object.keys(binding.fields || {})) {
    if (!fieldOptionsFor(semantic).some(option => option.col === binding.fields[semantic])) {
      delete binding.fields[semantic];
      delete binding.units[semantic];
    }
  }
  for (const semantic of Object.keys(binding.units || {})) {
    if (!binding.fields[semantic]) delete binding.units[semantic];
  }
  fillProvenUnits(binding);
  const source = selectedDatasource.value;
  if (source) {
    const result = prefillBindingForDatasource({
      slot: selectedSlot.value,
      template: selectedTemplate.value,
      screenScope: screenScope.value,
      datasource: source,
      binding,
      mode: selectedSlot.value === 'composition' && manualSlots.has(selectedSlot.value)
        ? compositionMode.value : undefined
    });
    if (result.binding) writeBinding(selectedSlot.value, result.binding);
    if (result.status === AUTO_BIND_STATUS.APPLIED && result.appliedSemantics?.length) {
      autoReviewSlots.add(selectedSlot.value);
      autoNotice.value = `${presentationLabel(selectedSlot.value)}已按当前数据来源预填明确字段，可继续修改配置。`;
    } else if (result.gap) {
      autoGaps.value = [...autoGaps.value.filter(item => item.slot !== selectedSlot.value), {
        slot: selectedSlot.value, status: result.status, message: result.gap
      }];
      autoNotice.value = `${presentationLabel(selectedSlot.value)}：${result.gap}`;
    }
  }
  conflict.value = '';
  markLocalDirty();
}

function fillProvenUnits(binding) {
  // amountScale 是字段展示元数据，不是查询结果的输入单位；旧实现把所有
  // amountScale 都写成 YUAN，会把亿元/万元来源静默换算错。单位只能由已保存
  // 绑定、受控宽表口径或用户明确选择提供。
  if (!binding?.fields || !binding?.units) return;
}

function setField(semantic, value) {
  const binding = selectedBinding.value;
  if (value) binding.fields[semantic] = value;
  else {
    delete binding.fields[semantic];
    delete binding.units[semantic];
  }
  manualSlots.add(selectedSlot.value);
  autoReviewSlots.delete(selectedSlot.value);
  conflict.value = '';
  markLocalDirty();
}

function setUnit(semantic, value) {
  if (value) selectedBinding.value.units[semantic] = value;
  else delete selectedBinding.value.units[semantic];
  manualSlots.add(selectedSlot.value);
  autoReviewSlots.delete(selectedSlot.value);
  conflict.value = '';
  markLocalDirty();
}

function setPeriod(value) {
  if (PERIOD_VALUES.includes(value)) selectedBinding.value.period = value;
  conflict.value = '';
  markLocalDirty();
}

function unitFor(semantic) {
  const binding = selectedBinding.value;
  return binding.units?.[semantic] || '';
}

function unitHint(semantic) {
  const column = selectedBinding.value.fields?.[semantic];
  const option = fieldOptionsFor(semantic).find(item => item.col === column);
  return option?.amountScale ? '数据来源声明了原始元值展示尺度；它不等于输入单位，请确认后选择。' : '';
}

function unitLabel(unit) {
  return ({ YUAN: '原始元值（YUAN）', TEN_THOUSAND: '原始万元', HUNDRED_MILLION: '原始亿元',
    COUNT: '原始个数', TEN_THOUSAND_COUNT: '原始万户', PERCENT: '百分数', RATIO: '比例（运行时转百分数）' })[unit] || unit;
}

function friendlyBindingIssue(slot, message) {
  let output = String(message || '');
  const spec = templateSpec(slot);
  for (const field of spec?.fields || []) {
    output = output.replaceAll(`字段: ${field.semantic}`, `展示内容：${field.label}`)
      .replaceAll(`字段 ${field.semantic}`, `展示内容“${field.label}”`)
      .replaceAll(`字段类型或元数据不匹配：${field.semantic}`, `展示内容“${field.label}”的字段类型或元数据不匹配`)
      .replaceAll(`单位: ${field.semantic}`, `展示内容“${field.label}”的单位`)
      .replaceAll(`单位未绑定字段: ${field.semantic}`, `展示内容“${field.label}”的单位未绑定字段`)
      .replaceAll(`单位无效: ${field.semantic}`, `展示内容“${field.label}”的单位无效`)
      .replaceAll(`单位不适用: ${field.semantic}`, `展示内容“${field.label}”的单位不适用`);
  }
  return output;
}

function collectValidBindings() {
  const next = {};
  const problems = [];
  for (const slot of slotOrder.value) {
    let candidate = bindingState[slot];
    if ((isRetailTemplate.value || isCorporateTemplate.value) && slot === 'branches' && candidate) {
      // 零售/对公模板的共享机构槽只保存身份，避免遗留分行指标成为隐性配置。
      const identitySpec = isCorporateTemplate.value
        ? CORPORATE_BINDING_SLOTS.branches
        : RETAIL_BINDING_SLOTS.branches;
      const identities = new Set(identitySpec.fields.map(item => item.semantic));
      candidate = { ...candidate, fields: Object.fromEntries(Object.entries(candidate.fields || {}).filter(([key]) => identities.has(key))), units: {} };
    }
    if (!candidate || !candidate.dsId) continue;
    if (isRetailTemplate.value) {
      const source = datasources.value.find(item => String(item.id) === String(candidate.dsId));
      if (String(source?.bizLine || source?.biz_line || '').toUpperCase() !== 'RETAIL') {
        problems.push(`${BINDING_SLOTS[slot].label}：零售模板仅允许零售条线数据源`);
        continue;
      }
    }
    if (isCorporateTemplate.value) {
      const source = datasources.value.find(item => String(item.id) === String(candidate.dsId));
      if (!isCorporateScreen.value || String(source?.bizLine || source?.biz_line || '').toUpperCase() !== 'CORP') {
        problems.push(`${templateSpec(slot)?.label || slot}：对公模板仅允许对公条线大屏与数据源`);
        continue;
      }
    }
    const mode = slot === 'composition'
      ? (compositionMode.value === 'columns' || getCompositionMode(candidate) === 'columns' ? 'columns' : 'rows')
      : null;
    let slotProblems = validateBinding(slot, candidate);
    if (!isCorporateTemplate.value && slot === 'composition' && mode === 'columns') {
      // The shared validator derives columns mode from its two semantic
      // fields. When the user has just selected columns and has not filled a
      // field yet, replace the legacy row errors with mode-specific guidance.
      slotProblems = slotProblems.filter(message => !/^缺少字段: (name|value)$/.test(message));
      for (const semantic of ['corporate', 'retail']) {
        if (!candidate.fields?.[semantic]) slotProblems.push(`缺少字段: ${semantic}`);
      }
      const source = datasources.value.find(item => String(item.id) === String(candidate.dsId));
      if (!isCompositionColumnsDatasource(source)) {
        problems.push(`${BINDING_SLOTS[slot].label}：columns 模式仅允许 WIDE_TABLE 且表为 ORG_INDEX_RESULT`);
      }
    }
    if (slotProblems.length) problems.push(...slotProblems.map(message => `${presentationLabel(slot)}：${friendlyBindingIssue(slot, message)}`));
    let fieldProblemCount = 0;
    for (const [semantic, column] of Object.entries(candidate.fields || {})) {
      if (!fieldOptionsForBinding(slot, semantic, candidate.dsId).some(option => option.col === column)) {
        problems.push(`${presentationLabel(slot)}：展示内容“${fieldLabel(slot, semantic)}”的字段类型或元数据不匹配`);
        fieldProblemCount += 1;
      }
    }
    if (!slotProblems.length && fieldProblemCount === 0
        && !(slot === 'composition' && mode === 'columns'
          && !isCompositionColumnsDatasource(datasources.value.find(item => String(item.id) === String(candidate.dsId))))) {
      next[slot] = normalizeBinding(candidate, slot);
    }
  }
  return { bindings: next, problems };
}

function displayUnitLabel(unit) {
  return ({ AUTO: '自动', YUAN: '元', TEN_THOUSAND: '万元', HUNDRED_MILLION: '亿元',
    COUNT: '个', TEN_THOUSAND_COUNT: '万户', PERCENT: '百分数', RATIO: '比例' })[unit] || unit || '自动';
}

function unitKindOf(unit) {
  const value = String(unit || '').toUpperCase();
  if (['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION'].includes(value)) return 'AMOUNT';
  if (['COUNT', 'TEN_THOUSAND_COUNT'].includes(value)) return 'COUNT';
  if (['PERCENT', 'RATIO'].includes(value)) return 'RATIO';
  return '';
}

function savePayload(targetId = activeScreenId.value) {
  const id = screenIdValue(targetId);
  if (!id || !screenReady.value || !canvas.value) throw new Error('当前屏配置尚未读取完成');
  if (legacyComponents.value.length && !conversionAccepted.value) {
    throw new Error('请先确认已发布版本并允许替换旧草稿组件树');
  }
  const { bindings, problems } = collectValidBindings();
  if (problems.length) throw new Error(problems.join('；'));
  if (!Object.keys(bindings).length) throw new Error('至少配置一个展示内容后才能保存');
  if (isRetailTemplate.value && !isRetailScreen.value) throw new Error('零售经营模板仅适用于零售条线大屏，请先核对大屏设置');
  if (isCorporateTemplate.value && !isCorporateScreen.value) throw new Error('对公经营模板仅适用于对公条线大屏，请先核对大屏设置');
  const existingPresentation = canvasStyle.value?.presentation && typeof canvasStyle.value.presentation === 'object'
    ? canvasStyle.value.presentation : {};
  // schema1 通过展示模型序列化保留受支持的 dataRefs/blockId 与协议字段；
  // 旧 CODE 没有 schema1 的 closed contract，只更新根 canvasStyle.metricLabels。
  const presentation = hasNewDisplayPresentation.value
    ? { ...serializeEditorSession(displaySession.value), type: 'CODE', template: selectedTemplate.value }
    : { ...existingPresentation, type: 'CODE', template: selectedTemplate.value };
  const style = { ...canvasStyle.value, presentation };
  const components = buildCodeComponents(bindings, draftComponents.value);
  if (!components.length) throw new Error('没有可保存的代码组件');
  return { screenId: id, expectedVersion: canvasVersion.value, canvasStyle: style, components };
}

function adoptSaveResponse(resp, fallbackComponents, savedPresentation) {
  const reviewSlots = new Set(autoReviewSlots);
  const usedDisplayDraft = hasNewDisplayPresentation.value;
  if (resp && Number.isSafeInteger(resp.canvasVersion)) canvas.value = { ...canvas.value, canvasVersion: resp.canvasVersion };
  const draft = parse(resp?.canvasDraftJson, null);
  draftComponents.value = Array.isArray(draft?.components) ? draft.components : fallbackComponents;
  canvasStyle.value = { ...canvasStyle.value, presentation: savedPresentation || { type: 'CODE', template: selectedTemplate.value } };
  if (usedDisplayDraft) displaySession.value = commitSnapshot(displaySession.value);
  resetBindings(draftComponents.value);
  for (const slot of reviewSlots) {
    if (bindingState[slot]?.dsId) autoReviewSlots.add(slot);
  }
  localDirty.value = false;
}

function writeIsCurrent(targetId, token) {
  return !disposed && screenReady.value
    && token === loadGeneration
    && targetId === screenIdValue(activeScreenId.value);
}

async function saveDraft(options = {}) {
  if (saving.value) return false;
  const targetId = screenIdValue(options.targetId ?? activeScreenId.value);
  const token = options.generation ?? loadGeneration;
  if (!targetId || !writeIsCurrent(targetId, token)) return false;
  saving.value = true;
  error.value = '';
  conflict.value = '';
  try {
    const payload = savePayload(targetId);
    if (!Number.isSafeInteger(payload.expectedVersion) || payload.expectedVersion < 0) {
      throw new Error('当前屏版本无效，已拒绝保存');
    }
    const response = await saveScreenCanvas(payload);
    if (!writeIsCurrent(targetId, token)) return false;
    adoptSaveResponse(response, payload.components, payload.canvasStyle.presentation);
    emit('saved', response);
    return true;
  } catch (saveError) {
    if (saveError?.code === 'RPT-43012') {
      conflict.value = '版本冲突：当前编辑仍保留在本地，请由用户决定重载或重新编辑后再保存。';
    } else {
      error.value = saveError?.message || '保存草稿失败';
    }
    emit('error', saveError);
    return false;
  } finally {
    saving.value = false;
  }
}

function preview() {
  if (hasLocalChanges.value) {
    error.value = '请先保存草稿再预览';
    return false;
  }
  try {
    const payload = savePayload();
    emit('preview', payload);
    const screenCode = String(activeScreen.value?.screenCode || activeScreen.value?.screen_code || '').trim();
    if (screenCode && router?.push) {
      router.push({ name: 'ScreenView', params: { screenCode }, query: { preview: 'draft' } });
    }
  } catch (previewError) {
    error.value = previewError?.message || '预览前校验失败';
    emit('error', previewError);
  }
}

watch(() => props.screenId, async value => {
  const id = screenIdValue(value);
  if (id && screens.value.some(screen => Number(screen.id) === id)) {
    if (hasLocalChanges.value && !saving.value) {
      conflict.value = '外部请求切换大屏，但当前组件配置尚未保存；请先保存或取消修改。';
      return;
    }
    activeScreenId.value = String(id);
    activeScreen.value = screens.value.find(screen => Number(screen.id) === id) || null;
    await loadCanvas();
  }
});

watch(() => route?.query?.screenId, async value => {
  if (props.screenId) return;
  const id = screenIdValue(value);
  if (id && screens.value.some(screen => Number(screen.id) === id)
      && String(id) !== String(activeScreenId.value)) {
    if (hasLocalChanges.value && !saving.value) {
      conflict.value = '路由请求切换大屏，但当前组件配置尚未保存；请先保存或取消修改。';
      return;
    }
    activeScreenId.value = String(id);
    activeScreen.value = screens.value.find(screen => Number(screen.id) === id) || null;
    await loadCanvas();
  }
});

function warnUnsavedChanges(event) {
  if (!hasLocalChanges.value) return;
  event.preventDefault();
  event.returnValue = '';
}

onMounted(async () => {
  window.addEventListener('beforeunload', warnUnsavedChanges);
  loading.value = true;
  try {
    await loadScreens();
    await loadCanvas();
  } catch (loadError) {
    error.value = loadError?.message || '读取屏列表失败';
    emit('error', loadError);
  } finally { loading.value = false; }
});

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', warnUnsavedChanges);
  disposed = true;
  ++loadGeneration;
  loading.value = false;
});

defineExpose({
  loadScreens, loadCanvas, saveDraft, collectValidBindings, cancelLocalChanges,
  applyDefaultToSlot, applyDefaultsToEmpty, autoPreview, displaySession
});
</script>

<style scoped>
.panorama-bindings { min-height:100%; box-sizing:border-box; padding:20px; color:var(--color-text, #303133); }
.panorama-bindings__header { margin-bottom:16px; }
.panorama-bindings__header h1 { margin:0; color:var(--color-text-strong, #303133); font-size:20px; }
.panorama-bindings__eyebrow { color:var(--color-text-muted, #909399); font-size:12px; }
.panorama-bindings__header p, .panorama-bindings__hint { margin:5px 0 0; color:var(--color-text-muted, #909399); font-size:12px; line-height:18px; }
.panorama-bindings__actions { display:flex; gap:8px; flex-wrap:wrap; margin-left:auto; }
.panorama-bindings__screen-bar { display:flex; gap:10px; align-items:center; margin-bottom:14px; padding:14px 16px; }
.panorama-bindings__screen-bar select { min-width:250px; min-height:34px; padding:0 10px; border:1px solid var(--color-border-strong, #c0c4cc); border-radius:var(--radius-control, 4px); background:var(--color-surface, #fff); color:inherit; }
.panorama-bindings__screen-meta { color:var(--color-text-muted, #909399); font-size:12px; }
.panorama-bindings__error, .panorama-bindings__conflict { margin:0 0 14px; padding:10px 14px; border-radius:var(--radius-control, 4px); }
.panorama-bindings__error { color:var(--color-danger-700, #a11a2b); background:var(--color-danger-50, #fff0f1); border:1px solid var(--color-danger-200, #ffd2d6); }
.panorama-bindings__conflict { color:var(--color-warning-700, #8b5b00); background:var(--color-warning-50, #fff8e5); border:1px solid var(--color-warning-200, #f6df9d); }
.panorama-bindings__loading { margin:0 0 14px; color:var(--color-brand-700, #4767d8); }
.panorama-bindings__legacy { display:flex; flex-direction:column; gap:8px; margin:0 0 14px; padding:12px 16px; color:var(--color-warning-700, #714c00); background:var(--color-warning-50, #fff8e5); border:1px solid var(--color-warning-200, #f6df9d); border-radius:var(--radius-control, 4px); font-size:13px; }
.panorama-bindings__workbench { overflow:hidden; }
.panorama-bindings__section-head { display:flex; justify-content:space-between; align-items:flex-start; gap:16px; padding:16px 16px 12px; }
.panorama-bindings__section-head h2 { margin:0; font-size:16px; }
.panorama-bindings__section-head p { margin:4px 0 0; color:var(--color-text-muted, #909399); font-size:12px; }
.panorama-bindings__dirty { color:var(--color-warning-700, #8b5b00); font-size:12px; }
.panorama-bindings__body { display:grid; grid-template-columns:240px minmax(0,1fr); gap:14px; padding:0 16px 16px; }
.panorama-bindings__slots, .panorama-bindings__editor { min-width:0; padding:16px; border:1px solid var(--color-border, #ebeef5); border-radius:var(--radius-control, 4px); background:var(--color-surface, #fff); }
.panorama-bindings__slots { display:flex; flex-direction:column; gap:6px; }
.panorama-bindings__slots h3 { margin:0 0 4px; font-size:14px; }
.panorama-bindings__slots button { display:flex; justify-content:space-between; gap:8px; min-height:36px; padding:7px 10px; border:1px solid var(--color-border, #dcdfe6); border-radius:var(--radius-control, 4px); background:var(--color-surface, #fff); color:var(--color-text, #303133); text-align:left; cursor:pointer; }
.panorama-bindings__slots button.is-active { color:var(--color-brand-700, #2f55ce); border-color:var(--color-brand-500, #6b83e8); background:var(--color-brand-100, #f1f4ff); }
.panorama-bindings__slots small { color:var(--color-text-muted, #909399); white-space:nowrap; }
.panorama-bindings__editor-head { display:flex; justify-content:space-between; align-items:flex-start; gap:12px; }
.panorama-bindings__editor-head h2 { margin:0 0 4px; font-size:16px; }
.panorama-bindings__editor-head p { margin:0; color:var(--color-text-muted, #909399); font-size:12px; line-height:18px; }
.panorama-bindings__bound { color:var(--color-success-700, #16805d); font-size:12px; }
.panorama-bindings__field-label { display:block; margin:12px 0 6px; color:var(--color-text-strong, #303133); font-size:13px; font-weight:600; }
.panorama-bindings__editor > select, .panorama-bindings__editor > input { width:100%; box-sizing:border-box; min-height:34px; padding:0 10px; border:1px solid var(--color-border-strong, #c0c4cc); border-radius:var(--radius-control, 4px); background:var(--color-surface, #fff); color:inherit; }
.panorama-bindings__fields { display:flex; flex-direction:column; gap:10px; margin-top:16px; }
.panorama-bindings__field-row { display:grid; grid-template-columns:150px minmax(0,1fr) 150px; gap:8px; align-items:center; }
.panorama-bindings__field-row label { font-size:13px; }
.panorama-bindings__component-field { min-width:0; }
.panorama-bindings__field-row select, .panorama-bindings__component-field select { min-height:34px; min-width:0; padding:0 10px; border:1px solid var(--color-border-strong, #c0c4cc); border-radius:var(--radius-control, 4px); background:var(--color-surface, #fff); color:inherit; }
.panorama-bindings__component-field select { width:100%; max-width:100%; box-sizing:border-box; }
.panorama-bindings__field-row em { margin-left:5px; color:var(--color-danger-700, #b42318); font-size:11px; font-style:normal; }
.panorama-bindings__unit-hint { grid-column:2 / 4; color:var(--color-text-muted, #909399); font-size:11px; }
.panorama-bindings__footer { display:flex; justify-content:flex-end; margin-top:14px; color:var(--color-text-muted, #909399); font-size:12px; }
@media (max-width:760px) { .panorama-bindings { padding:16px; } .panorama-bindings__header, .panorama-bindings__body { display:block; } .panorama-bindings__actions { margin-top:12px; } .panorama-bindings__slots { margin-bottom:14px; } .panorama-bindings__field-row { grid-template-columns:1fr; } .panorama-bindings__unit-hint { grid-column:auto; } .panorama-bindings__screen-bar { align-items:stretch; flex-direction:column; } }
</style>
