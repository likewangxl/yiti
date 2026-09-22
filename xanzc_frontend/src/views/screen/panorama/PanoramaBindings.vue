<template>
  <section class="panorama-bindings" aria-labelledby="panorama-bindings-title">
    <header class="panorama-bindings__header">
      <div>
        <span class="panorama-bindings__eyebrow">大屏管理</span>
        <h1 id="panorama-bindings-title">经营全景大屏管理</h1>
        <p>按当前大屏范围选择展示内容和已授权数据来源；自动配置只应用明确且唯一的候选，保存草稿后再预览或发布。</p>
      </div>
      <div class="panorama-bindings__actions">
        <button type="button" data-testid="binding-back-workspace" :disabled="writing" @click="navigateWorkspace">返回工作区</button>
        <button type="button" data-testid="binding-datasources" :disabled="writing" @click="navigateDatasources">管理数据源</button>
        <button type="button" data-testid="binding-new-screen" :disabled="writing" @click="openSettings(null)">新建大屏</button>
        <button type="button" data-testid="binding-settings" :disabled="writing || !screenReady" @click="openSettings(activeScreen)">大屏设置</button>
        <button type="button" data-testid="binding-auto-empty" :disabled="writing || loading || !screenReady" @click="applyDefaultsToEmpty">一键配置空白内容</button>
        <button type="button" data-testid="binding-preview" :disabled="!screenReady || writing" @click="preview">预览</button>
        <button type="button" data-testid="binding-save" :disabled="writing || !screenReady" @click="saveDraft">保存草稿</button>
        <button type="button" data-testid="binding-publish" :disabled="writing || !screenReady" @click="publishDraft">发布</button>
        <button type="button" data-testid="binding-discard" :disabled="writing || !screenReady" @click="discardDraft">放弃草稿</button>
      </div>
    </header>

    <p v-if="error" class="panorama-bindings__error" role="alert">{{ error }}</p>
    <p v-if="conflict" class="panorama-bindings__conflict" data-testid="binding-conflict" role="alert">{{ conflict }}</p>
    <p v-if="autoNotice" class="panorama-bindings__auto-notice" data-testid="binding-auto-notice" role="status">{{ autoNotice }}</p>
    <p v-if="loading" class="panorama-bindings__loading" role="status">正在读取服务端屏配置…</p>

    <div class="panorama-bindings__screen-bar">
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
      <label for="panorama-template-select">展示模板</label>
      <select id="panorama-template-select" v-model="selectedTemplate" data-testid="template-select" :disabled="writing || loading || !screenReady" @change="changeTemplate">
        <option value="branch-overview-v1">分行经营总览</option>
        <option value="retail-overview-v1" :disabled="!isRetailScreen">零售经营总览（零售条线）</option>
        <option value="corporate-overview-v1" :disabled="!isCorporateScreen">对公经营总览（对公条线）</option>
      </select>
    </div>

    <div v-if="legacyComponents.length" class="panorama-bindings__legacy" data-testid="legacy-conversion-warning">
      <strong>检测到旧布局或其他模板的组件</strong>
      <span>代码模板会替换当前草稿组件树；已发布快照不会自动修改。请先确认保留当前已发布版本后再转换。</span>
      <label>
        <input v-model="conversionAccepted" type="checkbox" data-testid="conversion-confirm" />
        我确认已保留/核对当前已发布版本，并允许替换草稿组件树
      </label>
    </div>

    <section v-if="screenReady" class="panorama-bindings__display-mode">
      <div><strong>组件化展示配置</strong><p>标题、内容、来源、格式和交互在同一工作台编辑；下方保留高级字段映射。</p></div>
      <button v-if="!displayEditorEnabled" type="button" data-testid="enable-display-editor" @click="enableDisplayEditor">启用三栏编辑器</button>
      <span v-else>{{ displaySession.dirty ? '存在未保存组件修改' : '组件配置已同步' }}</span>
    </section>
    <PresentationEditor v-if="screenReady && displayEditorEnabled" v-model:session="displaySession"
                        :block-options="displayBlockOptions" :migration-source="legacyMigrationSource"
                        @cancel="cancelDisplayChanges" @migration-cancel="dismissLegacyMigration"
                        @migration-applied="markMigrationApplied" />

    <div class="panorama-bindings__body">
      <nav class="panorama-bindings__slots" aria-label="经营指标区域">
        <h2>展示内容</h2>
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
      </nav>

      <main class="panorama-bindings__editor" aria-live="polite">
        <template v-if="selectedSpec">
          <header class="panorama-bindings__editor-head">
            <div>
              <h2>{{ selectedSpec.label }}</h2>
              <p data-testid="binding-guidance">{{ selectedGuidance }}</p>
            </div>
            <span v-if="selectedBinding?.dsId" class="panorama-bindings__bound">已选择数据来源<span v-if="autoReviewSlots.has(selectedSlot)">·待核验</span></span>
          </header>

          <div v-if="selectedSlot === 'composition'" class="panorama-bindings__composition-mode">
            <label class="panorama-bindings__field-label" for="panorama-composition-mode">构成模式</label>
            <select id="panorama-composition-mode" data-testid="composition-mode"
                    :value="compositionMode" @change="setCompositionMode($event.target.value)">
              <option value="rows">行模式（名称 + 构成值）</option>
              <option value="columns">双列模式（对公 + 零售）</option>
            </select>
            <p data-testid="composition-mode-hint" class="panorama-bindings__hint">
              {{ compositionMode === 'columns'
                ? '双列模式要求数据源返回恰好一行，固定对公/零售标签；总量/分母可选但必须与两列同类且明确为指标。'
                : '行模式允许多行，每行必须包含构成名称和构成值。' }}
            </p>
          </div>

          <label class="panorama-bindings__field-label" for="panorama-datasource">数据配置</label>
          <PanoramaDatasourcePicker
            id="panorama-datasource"
            data-testid="slot-datasource"
            :sources="availableDatasources"
            v-model="selectedDatasourceId"
            @change="onDatasourceChange"
          />
          <p data-testid="datasource-option-count" class="panorama-bindings__hint">当前屏可选数据源：{{ selectableDatasourceCount }} 个数据来源</p>
          <p v-if="!selectableDatasourceCount" class="panorama-bindings__hint">当前屏范围没有可用的数据来源。</p>
          <p v-if="!selectedDatasource" data-testid="datasource-required-hint" class="panorama-bindings__hint">请先选择数据源（数据来源），再配置展示字段和单位。</p>
          <p v-else-if="selectedFieldSpecs.length && !hasApplicableFieldOptions" data-testid="datasource-fields-unavailable-hint" class="panorama-bindings__hint">已选择数据来源，但没有适用字段候选（展示字段），请核对字段角色或元数据。</p>

          <div class="panorama-bindings__fields">
            <div v-for="fieldSpec in selectedFieldSpecs" :key="fieldSpec.semantic" class="panorama-bindings__field-row">
              <label :for="`panorama-field-${fieldSpec.semantic}`">
                {{ fieldSpec.label }}<em v-if="fieldSpec.required">需要配置</em>
              </label>
              <select :id="`panorama-field-${fieldSpec.semantic}`"
                      :data-testid="`field-option-${selectedSlot}-${fieldSpec.semantic}`"
                      :disabled="!selectedDatasource"
                      :value="selectedBinding.fields[fieldSpec.semantic] || ''"
                      @change="setField(fieldSpec.semantic, $event.target.value)">
                <option value="">待配置</option>
                <option v-for="option in fieldOptionsFor(fieldSpec.semantic)" :key="option.col" :value="option.col">
                  {{ option.label }}（{{ option.col }}）
                </option>
              </select>
              <select v-if="fieldSpec.kind !== 'dimension' && fieldSpec.unitKinds?.length"
                      :data-testid="`unit-${selectedSlot}-${fieldSpec.semantic}`"
                      :disabled="!selectedDatasource"
                      :value="unitFor(fieldSpec.semantic)"
                      @change="setUnit(fieldSpec.semantic, $event.target.value)">
                <option value="">未知单位</option>
                <option v-for="unit in fieldSpec.unitKinds" :key="unit" :value="unit">{{ unitLabel(unit) }}</option>
              </select>
              <small v-if="unitHint(fieldSpec.semantic)" class="panorama-bindings__unit-hint">{{ unitHint(fieldSpec.semantic) }}</small>
            </div>
          </div>

          <label class="panorama-bindings__field-label" for="panorama-period">周期</label>
          <select id="panorama-period" data-testid="binding-period" v-model="selectedBinding.period">
            <option v-for="period in PERIOD_VALUES" :key="period" :value="period">{{ PERIOD_LABELS[period] }}</option>
          </select>
          <p class="panorama-bindings__hint">字段候选来自数据来源已保存的字段说明；单位缺失时不会猜测，可随时修改配置。</p>
          <p v-if="selectedSlot === 'citySummary'" class="panorama-bindings__hint">城市汇总必须选择已按城市汇总的数据源；系统不会把支行机构数据相加成城市指标。</p>
        </template>
        <p v-else>请选择一个展示内容。</p>
      </main>
    </div>

    <section v-if="screenReady" class="panorama-bindings__auto-preview" data-testid="binding-auto-preview" aria-labelledby="binding-auto-preview-title">
      <div class="panorama-bindings__auto-preview-head">
        <div>
          <h2 id="binding-auto-preview-title">自动配置预览</h2>
          <p>已应用的内容仍可修改；缺口、歧义和待核验来源不会被标记为真实联调完成。</p>
        </div>
        <span>{{ autoPreview.applied.length }} 项已应用 · {{ autoPreview.gaps.length }} 项待处理</span>
      </div>
      <div v-if="autoPreview.applied.length" class="panorama-bindings__auto-preview-list" data-testid="binding-auto-applied">
        <p v-for="item in autoPreview.applied" :key="`applied-${item.slot}`">{{ item.summary }}</p>
      </div>
      <div v-if="autoPreview.gaps.length" class="panorama-bindings__auto-preview-list is-gap" data-testid="binding-auto-gaps">
        <p v-for="item in autoPreview.gaps" :key="`gap-${item.slot}`">{{ presentationLabel(item.slot) }}：{{ item.message }}</p>
      </div>
    </section>

    <PanoramaDataVerification
      v-if="screenReady"
      :screen="activeScreen"
      :canvas="canvas"
      :datasources="datasources"
      :slot-order="slotOrder"
      :binding-state="bindingState"
      :disabled="writing || loading"
    />

    <PanoramaIntegrationReadiness
      :slot-order="slotOrder"
      v-if="screenReady"
      :screens="screens"
      :screen="activeScreen"
      :canvas="canvas"
      :datasources="datasources"
      :binding-state="bindingState"
    />

    <footer class="panorama-bindings__footer">
      <label for="panorama-publish-reason">发布原因</label>
      <input id="panorama-publish-reason" data-testid="publish-reason" v-model="publishReason" maxlength="500" placeholder="发布时需要填写原因" />
      <span>当前版本 {{ canvasVersion ?? '-' }}</span>
    </footer>

    <div v-if="settingsVisible" class="panorama-bindings__settings-panel" data-testid="binding-settings-panel">
      <PanoramaSettings
        :screen="settingsTarget"
        :visible="settingsVisible"
        @update:visible="settingsVisible = $event"
        @saved="onSettingsSaved"
      />
    </div>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  discardScreenCanvas,
  getScreenCanvas,
  listScreenDatasources,
  listScreens,
  publishScreenCanvas,
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
import PanoramaSettings from './PanoramaSettings.vue';
import PanoramaIntegrationReadiness from './PanoramaIntegrationReadiness.vue';
import PanoramaDataVerification from './PanoramaDataVerification.vue';
import PanoramaDatasourcePicker from './PanoramaDatasourcePicker.vue';
import { buildBusinessSourceCandidates } from '../presentation/sources/businessSourceCandidates';
import PresentationEditor from '../presentation/editor/PresentationEditor.vue';
import {
  cancelEditorSession, commitSnapshot, createPresentationEditorSession, serializeEditorSession
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
const bindingState = reactive({});
const loading = ref(false);
const saving = ref(false);
const publishing = ref(false);
const discarding = ref(false);
const error = ref('');
const conflict = ref('');
const publishReason = ref('');
const conversionAccepted = ref(false);
const screenReady = ref(false);
const settingsVisible = ref(false);
const settingsTarget = ref(null);
const compositionMode = ref('rows');
const autoNotice = ref('');
const autoGaps = ref([]);
const autoReviewSlots = reactive(new Set());
const manualSlots = reactive(new Set());
let loadGeneration = 0;
let disposed = false;
let settingsRefreshGeneration = 0;
const displayEditorEnabled = ref(false);
const displaySession = ref(createPresentationEditorSession({}, { type: 'CODE', template: 'branch-overview-v1' }));
const migrationDismissed = ref(false);
const migrationApplied = ref(false);

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
function changeTemplate() {
  selectedSlot.value = slotOrder.value.find(slot => bindingState[slot]) || slotOrder.value[0];
  conversionAccepted.value = draftComponents.value.length === 0;
  autoNotice.value = '';
  autoGaps.value = [];
  applyDefaultToSlot(selectedSlot.value);
}
function dismissLegacyMigration() {
  migrationDismissed.value = true;
}
function markMigrationApplied() {
  migrationApplied.value = true;
}
function enableDisplayEditor() {
  displayEditorEnabled.value = true;
  migrationApplied.value = false;
  displaySession.value = createPresentationEditorSession({
    ...(canvasStyle.value?.presentation || {}), type: 'CODE', template: selectedTemplate.value
  }, { type: 'CODE', template: selectedTemplate.value });
}
function cancelDisplayChanges() {
  displaySession.value = cancelEditorSession(displaySession.value);
}
function changeActiveScreen() {
  if (displayEditorEnabled.value && displaySession.value.dirty) {
    conflict.value = '存在未保存的组件配置，请先保存草稿或取消本地修改后再切换大屏。';
    activeScreenId.value = String(activeScreen.value?.id || '');
    return false;
  }
  return loadCanvas(activeScreenId.value);
}
const writing = computed(() => saving.value || publishing.value || discarding.value);
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
    ? ['corporate', 'retail', 'total'] : ['name', 'value'];
  return (selectedSpec.value.fields || []).filter(fieldSpec => semantics.includes(fieldSpec.semantic));
});
const selectedDatasource = computed(() => datasources.value.find(item => String(item.id) === String(selectedBinding.value.dsId)) || null);
const displayBlockOptions = computed(() => draftComponents.value
  .filter(item => item?.component === 'ChartWidget' && Number.isSafeInteger(Number(item.blockId)) && Number(item.blockId) > 0)
  .map(item => {
    const bindingKey = String(item?.propValue?.bindingKey || '');
    const bind = parse(item.bindJson, {});
    return {
      blockId: Number(item.blockId),
      label: `${presentationLabel(bindingKey)} · block ${item.blockId}`,
      role: 'PRIMARY',
      metricCode: bindingKey.toUpperCase(),
      metricName: presentationLabel(bindingKey),
      unit: Object.values(bind.units || {})[0] || 'YUAN',
      dimension: 'ORG'
    };
  }));
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

/** 旧配置只作为当前编辑器的迁移预览输入，不触发保存、发布或数据源写入。 */
const legacyMigrationSource = computed(() => {
  if (!migrationCandidate.value.length || migrationDismissed.value) return null;
  const published = parse(canvas.value?.canvasPublishedJson ?? canvas.value?.renderPackageJson
    ?? canvas.value?.renderPackage ?? canvas.value?.canvasPublished, null);
  const bindSnapshots = published?.bindSnapshots || parse(canvas.value?.bindSnapshots, {});
  return published
    ? { renderPackage: published, blocks: canvas.value?.blocks, bindSnapshots, canvasStyle: canvasStyle.value }
    : { renderPackageJson: canvas.value?.renderPackageJson, canvasDraftJson: canvas.value?.canvasDraftJson,
      canvasStyleJson: canvas.value?.canvasStyleJson,
      components: draftComponents.value,
      blocks: canvas.value?.blocks, bindSnapshots, canvasStyle: canvasStyle.value };
});

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
  bindingState[selectedSlot.value] = emptyBinding(selectedSlot.value);
  compositionMode.value = 'rows';
  conversionAccepted.value = false;
  migrationDismissed.value = false;
  migrationApplied.value = false;
  autoNotice.value = '';
  autoGaps.value = [];
  autoReviewSlots.clear();
  manualSlots.clear();
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

function openSettings(screen) {
  settingsTarget.value = screen || null;
  settingsVisible.value = true;
}

function navigateWorkspace() {
  if (writing.value || !router?.push) return;
  router.push('/workspace');
}

function navigateDatasources() {
  if (writing.value || !router?.push) return;
  router.push('/screen-admin/datasources');
}

async function onSettingsSaved(result = {}) {
  // Metadata, access-role, and rollback writes all change server state that
  // the binding editor displays. Refresh the selected screen after the child
  // completes while retaining the current screen when possible.
  settingsVisible.value = false;
  const refreshToken = ++settingsRefreshGeneration;
  const loadToken = loadGeneration;
  const targetId = screenIdValue(result.screenId) || screenIdValue(activeScreenId.value);
  loading.value = true;
  try {
    await loadScreens();
    // The settings save is allowed to refresh the selected screen only while
    // this refresh is still the latest operation.  The screen selector is
    // disabled during the await, and an imperative screen load also advances
    // loadGeneration, so a late list response cannot switch B back to A.
    if (disposed || refreshToken !== settingsRefreshGeneration || loadToken !== loadGeneration) return;
    const selected = targetId && screens.value.find(screen => Number(screen.id) === targetId);
    if (selected) {
      activeScreenId.value = String(targetId);
      activeScreen.value = selected;
    }
    await loadCanvas();
  } catch (refreshError) {
    if (disposed || refreshToken !== settingsRefreshGeneration) return;
    error.value = refreshError?.message || '设置已保存，但刷新大屏配置失败';
    emit('error', refreshError);
  } finally {
    if (!disposed && refreshToken === settingsRefreshGeneration) loading.value = false;
  }
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
    displayEditorEnabled.value = canvasStyle.value?.presentation?.displaySchemaVersion === 1;
    displaySession.value = createPresentationEditorSession(canvasStyle.value?.presentation || {}, {
      type: 'CODE', template: selectedTemplate.value
    });
    const draft = parse(resp.canvasDraftJson, { components: [] });
    draftComponents.value = Array.isArray(draft.components) ? draft.components : [];
    // 旧组件树直接打开三栏编辑器以展示受控迁移入口；转换仍由用户在编辑器中明确应用。
    migrationApplied.value = false;
    if (migrationCandidate.value.length) displayEditorEnabled.value = true;
    resetBindings(draftComponents.value);
    datasources.value = Array.isArray(sourceList) ? sourceList : [];
    for (const binding of Object.values(bindingState)) fillProvenUnits(binding);
    conversionAccepted.value = legacyComponents.value.length === 0;
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

function selectSlot(slot) {
  if (!Object.prototype.hasOwnProperty.call(BINDING_SLOTS, slot)) return;
  selectedSlot.value = slot;
  if (!bindingState[slot]) bindingState[slot] = emptyBinding(slot);
  if (slot === 'composition' && !manualSlots.has(slot)) {
    const fields = bindingState[slot]?.fields || {};
    if (fields.corporate && fields.retail) compositionMode.value = 'columns';
    else if (fields.name || fields.value) compositionMode.value = 'rows';
  }
  applyDefaultToSlot(slot);
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
}

function setUnit(semantic, value) {
  if (value) selectedBinding.value.units[semantic] = value;
  else delete selectedBinding.value.units[semantic];
  manualSlots.add(selectedSlot.value);
  autoReviewSlots.delete(selectedSlot.value);
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
  // 旧 CODE 包打开迁移面板时，未点击“应用到当前草稿”仍沿用原有保存链；
  // 只有新协议本身或明确应用迁移后，才序列化编辑器的 display 配置。
  const useDisplayDraft = displayEditorEnabled.value
    && (hasNewDisplayPresentation.value || migrationApplied.value);
  const presentation = useDisplayDraft
    ? { ...serializeEditorSession(displaySession.value), type: 'CODE', template: selectedTemplate.value }
    : { type: 'CODE', template: selectedTemplate.value };
  const style = { ...canvasStyle.value, presentation };
  const components = buildCodeComponents(bindings, draftComponents.value);
  if (!components.length) throw new Error('没有可保存的代码组件');
  return { screenId: id, expectedVersion: canvasVersion.value, canvasStyle: style, components };
}

function adoptSaveResponse(resp, fallbackComponents, savedPresentation) {
  const reviewSlots = new Set(autoReviewSlots);
  const usedDisplayDraft = displayEditorEnabled.value
    && (hasNewDisplayPresentation.value || migrationApplied.value);
  if (resp && Number.isSafeInteger(resp.canvasVersion)) canvas.value = { ...canvas.value, canvasVersion: resp.canvasVersion };
  const draft = parse(resp?.canvasDraftJson, null);
  draftComponents.value = Array.isArray(draft?.components) ? draft.components : fallbackComponents;
  canvasStyle.value = { ...canvasStyle.value, presentation: savedPresentation || { type: 'CODE', template: selectedTemplate.value } };
  if (usedDisplayDraft) displaySession.value = commitSnapshot(displaySession.value);
  resetBindings(draftComponents.value);
  for (const slot of reviewSlots) {
    if (bindingState[slot]?.dsId) autoReviewSlots.add(slot);
  }
}

function writeIsCurrent(targetId, token) {
  return !disposed && screenReady.value
    && token === loadGeneration
    && targetId === screenIdValue(activeScreenId.value);
}

async function saveDraft(options = {}) {
  const allowWhilePublishing = Boolean(options.allowWhilePublishing);
  if (saving.value || discarding.value || (publishing.value && !allowWhilePublishing)) return false;
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

async function publishDraft() {
  if (publishing.value || saving.value || discarding.value) return false;
  const reason = String(publishReason.value || '').trim();
  if (!reason) { error.value = '发布大屏必须填写原因'; return false; }
  const targetId = screenIdValue(activeScreenId.value);
  const token = loadGeneration;
  if (!targetId || !writeIsCurrent(targetId, token)) {
    error.value = '当前屏配置尚未读取完成';
    return false;
  }
  publishing.value = true;
  try {
    const saved = await saveDraft({ allowWhilePublishing: true, targetId, generation: token });
    if (!saved) return false;
    if (!writeIsCurrent(targetId, token)) return false;
    const expectedVersion = canvasVersion.value;
    await publishScreenCanvas({ screenId: targetId, expectedVersion, reason });
    if (!writeIsCurrent(targetId, token)) return false;
    // Publishing advances the server canvas version. Reload the same target
    // before exposing success so subsequent edits use the new CAS version.
    const reloaded = await loadCanvas(targetId);
    if (!reloaded) return false;
    emit('published', { screenId: targetId, reason });
    publishReason.value = '';
    return true;
  } catch (publishError) {
    if (publishError?.code === 'RPT-43012') conflict.value = '版本冲突：发布未执行，当前编辑仍保留。';
    else error.value = publishError?.message || '发布失败';
    emit('error', publishError);
    return false;
  } finally { publishing.value = false; }
}

async function discardDraft() {
  if (discarding.value || saving.value || publishing.value) return false;
  const reason = String(publishReason.value || '').trim();
  if (!reason) { error.value = '放弃草稿必须填写原因'; return false; }
  const targetId = screenIdValue(activeScreenId.value);
  const token = loadGeneration;
  if (!targetId || !writeIsCurrent(targetId, token)) {
    error.value = '当前屏配置尚未读取完成';
    return false;
  }
  discarding.value = true;
  try {
    await discardScreenCanvas(targetId, { expectedVersion: canvasVersion.value, reason });
    if (!writeIsCurrent(targetId, token)) return false;
    const reloaded = await loadCanvas(targetId);
    if (!reloaded) return false;
    emit('discarded', { screenId: targetId, reason });
    publishReason.value = '';
    return true;
  } catch (discardError) {
    if (discardError?.code === 'RPT-43012') conflict.value = '版本冲突：放弃草稿未执行，当前编辑仍保留。';
    else error.value = discardError?.message || '放弃草稿失败';
    emit('error', discardError);
    return false;
  } finally { discarding.value = false; }
}

watch(() => props.screenId, async value => {
  const id = screenIdValue(value);
  if (id && screens.value.some(screen => Number(screen.id) === id)) {
    if (displayEditorEnabled.value && displaySession.value.dirty) {
      conflict.value = '外部请求切换大屏，但当前组件配置尚未保存；请先保存或取消本地修改。';
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
    if (displayEditorEnabled.value && displaySession.value.dirty) {
      conflict.value = '路由请求切换大屏，但当前组件配置尚未保存；请先保存或取消本地修改。';
      return;
    }
    activeScreenId.value = String(id);
    activeScreen.value = screens.value.find(screen => Number(screen.id) === id) || null;
    await loadCanvas();
  }
});

function warnUnsavedDisplay(event) {
  if (!displayEditorEnabled.value || !displaySession.value.dirty) return;
  event.preventDefault();
  event.returnValue = '';
}

onMounted(async () => {
  window.addEventListener('beforeunload', warnUnsavedDisplay);
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
  window.removeEventListener('beforeunload', warnUnsavedDisplay);
  disposed = true;
  ++loadGeneration;
  loading.value = false;
});

defineExpose({
  loadScreens, loadCanvas, saveDraft, publishDraft, discardDraft, collectValidBindings, onSettingsSaved,
  applyDefaultToSlot, applyDefaultsToEmpty, autoPreview
});
</script>

<style scoped>
.panorama-bindings { color: #1f2d3d; background: #f4f7fb; min-height: 100%; padding: 24px; box-sizing: border-box; }
.panorama-bindings__header, .panorama-bindings__screen-bar, .panorama-bindings__body, .panorama-bindings__footer { max-width: 1240px; margin: 0 auto; }
.panorama-bindings__display-mode{max-width:1600px;margin:0 auto 12px;padding:12px 16px;display:flex;justify-content:space-between;align-items:center;gap:12px;background:#fff;border:1px solid #e3eaf2;border-radius:8px}.panorama-bindings__display-mode p{margin:3px 0 0;color:#718096;font-size:12px}.panorama-bindings__display-mode span{color:#8b5b00;font-size:12px}
.panorama-bindings__header { display:flex; justify-content:space-between; gap:20px; align-items:flex-start; margin-bottom:18px; }
.panorama-bindings__eyebrow { color:#4767d8; font-size:11px; letter-spacing:1.5px; }
h1, h2, p { margin-top:0; }
.panorama-bindings__header p, .panorama-bindings__hint { color:#718096; font-size:13px; }
.panorama-bindings__actions { display:flex; gap:8px; flex-wrap:wrap; }
button, select, input { border:1px solid #cad5e2; border-radius:6px; background:#fff; min-height:34px; padding:0 10px; color:inherit; }
button { cursor:pointer; }
button:disabled { cursor:not-allowed; opacity:.55; }
.panorama-bindings__screen-bar, .panorama-bindings__footer { display:flex; gap:10px; align-items:center; padding:12px 16px; background:#fff; border:1px solid #e3eaf2; border-radius:8px; margin-bottom:14px; }
.panorama-bindings__screen-bar select { min-width:250px; }
.panorama-bindings__screen-meta { color:#718096; font-size:13px; }
.panorama-bindings__error, .panorama-bindings__conflict { max-width:1240px; margin:0 auto 14px; padding:10px 14px; border-radius:6px; }
.panorama-bindings__error { color:#a11a2b; background:#fff0f1; border:1px solid #ffd2d6; }
.panorama-bindings__conflict { color:#8b5b00; background:#fff8e5; border:1px solid #f6df9d; }
.panorama-bindings__auto-notice { max-width:1240px; margin:0 auto 14px; padding:10px 14px; border-radius:6px; color:#355487; background:#eef5ff; border:1px solid #cbdcf8; }
.panorama-bindings__loading { max-width:1240px; margin:0 auto 14px; color:#4767d8; }
.panorama-bindings__legacy { max-width:1240px; margin:0 auto 14px; display:flex; flex-direction:column; gap:8px; padding:12px 16px; color:#714c00; background:#fff8e5; border:1px solid #f6df9d; border-radius:8px; font-size:13px; }
.panorama-bindings__body { display:grid; grid-template-columns:240px minmax(0,1fr); gap:14px; }
.panorama-bindings__slots, .panorama-bindings__editor { background:#fff; border:1px solid #e3eaf2; border-radius:8px; padding:16px; }
.panorama-bindings__slots { display:flex; flex-direction:column; gap:6px; }
.panorama-bindings__slots h2 { font-size:15px; }
.panorama-bindings__slots button { display:flex; justify-content:space-between; text-align:left; }
.panorama-bindings__slots button.is-active { color:#2f55ce; border-color:#6b83e8; background:#f1f4ff; }
.panorama-bindings__slots small { color:#8291a5; }
.panorama-bindings__editor-head { display:flex; justify-content:space-between; align-items:flex-start; }
.panorama-bindings__editor-head h2 { margin-bottom:4px; }
.panorama-bindings__editor-head p { color:#718096; font-size:12px; }
.panorama-bindings__bound { color:#16805d; font-size:12px; }
.panorama-bindings__field-label { display:block; font-weight:600; margin:12px 0 6px; font-size:13px; }
.panorama-bindings__editor > select, .panorama-bindings__editor > input { width:100%; box-sizing:border-box; }
.panorama-bindings__fields { margin-top:16px; display:flex; flex-direction:column; gap:10px; }
.panorama-bindings__field-row { display:grid; grid-template-columns:150px minmax(0,1fr) 150px; gap:8px; align-items:center; }
.panorama-bindings__field-row label { font-size:13px; }
.panorama-bindings__field-row em { color:#b42318; font-size:11px; margin-left:5px; font-style:normal; }
.panorama-bindings__unit-hint { grid-column:2 / 4; color:#718096; font-size:11px; }
.panorama-bindings__footer { justify-content:flex-end; margin-top:14px; }
.panorama-bindings__footer input { min-width:280px; }
.panorama-bindings__settings-panel { position:fixed; top:0; right:0; bottom:0; z-index:40; width:min(720px, 100vw); overflow:auto; box-shadow:-8px 0 28px rgba(31,45,61,.22); }
.panorama-bindings__auto-preview { max-width:1240px; margin:14px auto 0; padding:16px; border:1px solid #e3eaf2; border-radius:8px; background:#fff; }
.panorama-bindings__auto-preview-head { display:flex; justify-content:space-between; gap:16px; align-items:flex-start; }
.panorama-bindings__auto-preview-head h2 { margin:0 0 4px; font-size:15px; }
.panorama-bindings__auto-preview-head p, .panorama-bindings__auto-preview-head > span { color:#718096; font-size:12px; }
.panorama-bindings__auto-preview-list { display:grid; gap:5px; margin-top:10px; }
.panorama-bindings__auto-preview-list p { margin:0; padding:7px 10px; background:#f5fbf8; color:#166b4b; font-size:12px; border-radius:5px; }
.panorama-bindings__auto-preview-list.is-gap p { background:#fff8e5; color:#8b5b00; }
@media (max-width: 760px) { .panorama-bindings__header, .panorama-bindings__body { display:block; } .panorama-bindings__actions { margin-top:12px; } .panorama-bindings__slots { margin-bottom:14px; } .panorama-bindings__field-row { grid-template-columns:1fr; } .panorama-bindings__unit-hint { grid-column:auto; } .panorama-bindings__footer { flex-wrap:wrap; justify-content:flex-start; } }
</style>
