<template>
  <section class="panorama-bindings" aria-labelledby="panorama-bindings-title">
    <header class="panorama-bindings__header">
      <div>
        <span class="panorama-bindings__eyebrow">大屏管理</span>
        <h1 id="panorama-bindings-title">经营全景大屏管理</h1>
        <p>选择展示指标和已授权数据源，保存草稿后再预览或发布。</p>
      </div>
      <div class="panorama-bindings__actions">
        <button type="button" data-testid="binding-back-workspace" :disabled="writing" @click="navigateWorkspace">返回工作区</button>
        <button type="button" data-testid="binding-datasources" :disabled="writing" @click="navigateDatasources">管理数据源</button>
        <button type="button" data-testid="binding-new-screen" :disabled="writing" @click="openSettings(null)">新建大屏</button>
        <button type="button" data-testid="binding-settings" :disabled="writing || !screenReady" @click="openSettings(activeScreen)">大屏设置</button>
        <button type="button" data-testid="binding-preview" :disabled="!screenReady || writing" @click="preview">预览</button>
        <button type="button" data-testid="binding-save" :disabled="writing || !screenReady" @click="saveDraft">保存草稿</button>
        <button type="button" data-testid="binding-publish" :disabled="writing || !screenReady" @click="publishDraft">发布</button>
        <button type="button" data-testid="binding-discard" :disabled="writing || !screenReady" @click="discardDraft">放弃草稿</button>
      </div>
    </header>

    <p v-if="error" class="panorama-bindings__error" role="alert">{{ error }}</p>
    <p v-if="conflict" class="panorama-bindings__conflict" data-testid="binding-conflict" role="alert">{{ conflict }}</p>
    <p v-if="loading" class="panorama-bindings__loading" role="status">正在读取服务端屏配置…</p>

    <div class="panorama-bindings__screen-bar">
      <label for="panorama-screen-select">经营大屏</label>
      <select id="panorama-screen-select" data-testid="screen-select" v-model="activeScreenId" :disabled="writing || loading" @change="loadCanvas(activeScreenId)">
        <option value="">请选择已存在的大屏</option>
        <option v-for="screen in screens" :key="screen.id" :value="String(screen.id)">
          {{ screen.screenName || screen.screen_name || screen.screenCode || `屏幕 #${screen.id}` }}
        </option>
      </select>
      <span v-if="activeScreen" class="panorama-bindings__screen-meta">
        {{ activeScreen.screenCode || activeScreen.screen_code }} · {{ activeScreen.bizLine || activeScreen.biz_line || 'COMMON' }}
      </span>
    </div>

    <div v-if="legacyComponents.length" class="panorama-bindings__legacy" data-testid="legacy-conversion-warning">
      <strong>检测到旧拖拽组件</strong>
      <span>代码模板会替换当前草稿组件树；已发布快照不会自动修改。请先确认保留当前已发布版本后再转换。</span>
      <label>
        <input v-model="conversionAccepted" type="checkbox" data-testid="conversion-confirm" />
        我确认已保留/核对当前已发布版本，并允许替换草稿组件树
      </label>
    </div>

    <div class="panorama-bindings__body">
      <nav class="panorama-bindings__slots" aria-label="经营指标区域">
        <h2>槽位</h2>
        <button v-for="slot in slotOrder" :key="slot" type="button"
                :class="{ 'is-active': selectedSlot === slot, 'is-bound': Boolean(bindingState[slot]?.dsId) }"
                :data-testid="`slot-${slot}`" @click="selectSlot(slot)">
          <span>{{ BINDING_SLOTS[slot].label }}</span>
          <small>{{ bindingState[slot]?.dsId ? '已配置' : '未绑定' }}</small>
        </button>
      </nav>

      <main class="panorama-bindings__editor" aria-live="polite">
        <template v-if="selectedSpec">
          <header class="panorama-bindings__editor-head">
            <div>
              <h2>{{ selectedSpec.label }}</h2>
              <p v-if="selectedSlot === 'composition'">{{ compositionMode === 'columns' ? '对公金额/零售金额均需绑定' : '名称/构成值均需绑定' }}</p>
              <p v-else-if="selectedSpec.oneOfRequired?.length">{{ selectedSpec.oneOfRequired.join('、') }}至少选择一个；{{ selectedSpec.atLeastOneOf?.length ? `${selectedSpec.atLeastOneOf.join('、')}至少选择一个` : '' }}</p>
              <p v-else>{{ selectedSpec.required?.join('、') || '无必填字段' }}为必填</p>
            </div>
            <span v-if="selectedBinding?.dsId" class="panorama-bindings__bound">已选择数据源</span>
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
                ? '双列模式要求数据源返回恰好一行，固定对公/零售标签；不会按名称自动绑定。'
                : '行模式允许多行，每行必须包含构成名称和构成值。' }}
            </p>
          </div>

          <label class="panorama-bindings__field-label" for="panorama-datasource">数据源</label>
          <select id="panorama-datasource" data-testid="slot-datasource" v-model="selectedDatasourceId" @change="onDatasourceChange">
            <option value="">请选择当前屏可用数据源</option>
            <option v-for="source in availableDatasources" :key="source.id" :value="String(source.id)" :disabled="source.__compositionColumnsUnsupported">
              {{ source.dsName || source.ds_name || source.dsCode || `数据源 #${source.id}` }}{{ source.__compositionColumnsUnsupported ? '（当前双列模式不支持）' : '' }}
            </option>
          </select>
          <p v-if="!availableDatasources.length" class="panorama-bindings__hint">当前屏范围没有可用的数据源。</p>

          <div class="panorama-bindings__fields">
            <div v-for="fieldSpec in selectedFieldSpecs" :key="fieldSpec.semantic" class="panorama-bindings__field-row">
              <label :for="`panorama-field-${fieldSpec.semantic}`">
                {{ fieldSpec.label }}<em v-if="fieldSpec.required">必填</em>
              </label>
              <select :id="`panorama-field-${fieldSpec.semantic}`"
                      :data-testid="`field-option-${selectedSlot}-${fieldSpec.semantic}`"
                      :value="selectedBinding.fields[fieldSpec.semantic] || ''"
                      @change="setField(fieldSpec.semantic, $event.target.value)">
                <option value="">未绑定</option>
                <option v-for="option in fieldOptionsFor(fieldSpec.semantic)" :key="option.col" :value="option.col">
                  {{ option.label }}（{{ option.col }}）
                </option>
              </select>
              <select v-if="fieldSpec.kind !== 'dimension' && fieldSpec.unitKinds?.length"
                      :data-testid="`unit-${selectedSlot}-${fieldSpec.semantic}`"
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
          <p class="panorama-bindings__hint">字段候选来自数据源已保存的字段说明；单位缺失时不会猜测。</p>
          <p v-if="selectedSlot === 'citySummary'" class="panorama-bindings__hint">城市汇总必须选择已按城市汇总的数据源；系统不会把支行机构数据相加成城市指标。</p>
        </template>
        <p v-else>请选择一个槽位。</p>
      </main>
    </div>

    <PanoramaIntegrationReadiness
      v-if="screenReady"
      :screens="screens"
      :screen="activeScreen"
      :canvas="canvas"
      :datasources="datasources"
      :binding-state="bindingState"
    />

    <footer class="panorama-bindings__footer">
      <label for="panorama-publish-reason">发布原因</label>
      <input id="panorama-publish-reason" data-testid="publish-reason" v-model="publishReason" maxlength="500" placeholder="发布时必填" />
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
import { filterDatasourcesByMeta, parseDatasourceConfig } from '../designer/widgets/chart-widget/dsFilter';
import {
  BINDING_SLOTS,
  PERIOD_LABELS,
  PERIOD_VALUES,
  SLOT_ORDER,
  buildCodeComponents,
  getCompositionMode,
  getDatasourceFieldOptions,
  normalizeBinding,
  validateBinding
} from './bindings';
import PanoramaSettings from './PanoramaSettings.vue';
import PanoramaIntegrationReadiness from './PanoramaIntegrationReadiness.vue';

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
let loadGeneration = 0;
let disposed = false;
let settingsRefreshGeneration = 0;

const slotOrder = SLOT_ORDER;
const writing = computed(() => saving.value || publishing.value || discarding.value);
const selectedSpec = computed(() => BINDING_SLOTS[selectedSlot.value] || null);
const selectedBinding = computed(() => {
  if (!bindingState[selectedSlot.value]) bindingState[selectedSlot.value] = emptyBinding(selectedSlot.value);
  return bindingState[selectedSlot.value];
});
const selectedFieldSpecs = computed(() => {
  if (!selectedSpec.value) return [];
  if (selectedSlot.value !== 'composition') return selectedSpec.value.fields || [];
  const semantics = compositionMode.value === 'columns' ? ['corporate', 'retail'] : ['name', 'value'];
  return (selectedSpec.value.fields || []).filter(fieldSpec => semantics.includes(fieldSpec.semantic));
});
const selectedDatasource = computed(() => datasources.value.find(item => String(item.id) === String(selectedBinding.value.dsId)) || null);
const selectedDatasourceId = computed({
  get: () => selectedBinding.value.dsId ? String(selectedBinding.value.dsId) : '',
  set: value => { selectedBinding.value.dsId = value ? Number(value) : null; }
});
function fieldOptionsForBinding(slot, semantic, dsId) {
  const fieldSpec = BINDING_SLOTS[slot]?.fields?.find(item => item.semantic === semantic);
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
  if (slot === 'citySummary' && semantic === 'orgCode'
      && String(screenScope.value.orgScopeMode).toUpperCase() === 'NAMED_GROUP') {
    return options.filter(option => option.col === 'org_code' && option.builtin === true);
  }
  return options;
}

function fieldOptionsFor(semantic) {
  return fieldOptionsForBinding(selectedSlot.value, semantic, selectedBinding.value.dsId);
}
const canvasVersion = computed(() => canvas.value?.canvasVersion ?? null);

const isCodePresentation = computed(() => canvasStyle.value?.presentation?.type === 'CODE'
  && canvasStyle.value?.presentation?.template === 'branch-overview-v1');

// 任何仍有内容的旧坐标画布都需要用户确认转换；不能只检查 ChartWidget，
// 否则 Group/Text 等旧节点会在保存时被静默删除。
const legacyComponents = computed(() => draftComponents.value.length && !isCodePresentation.value
  ? draftComponents.value : []);

const screenScope = computed(() => ({
  bizLine: activeScreen.value?.bizLine || activeScreen.value?.biz_line || 'COMMON',
  orgScopeMode: activeScreen.value?.orgScopeMode || activeScreen.value?.org_scope_mode || 'LEGACY_CONTEXT'
}));

function isCompositionColumnsDatasource(source = {}) {
  const config = parseDatasourceConfig(source.configJson);
  const sourceKind = String(source.sourceKind || source.source_kind || '').trim().toUpperCase();
  return sourceKind === 'WIDE_TABLE' && config?.table === 'ORG_INDEX_RESULT';
}

/** 既有 helper 负责 bizLine + NAMED_GROUP 收窄；本页再做 ACTIVE 状态过滤。 */
const availableDatasources = computed(() => filterDatasourcesByMeta(
  datasources.value.filter(source => source?.status === 'ACTIVE' || source?.status === 1 || source?.status === '1' || source?.status === true),
  null,
  screenScope.value
).reduce((list, source) => {
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

function emptyBinding(slot = '') {
  return {
    dsId: null,
    period: slot === 'trend' || slot === 'branchTrend' ? 'LAST_6M_EOM' : 'LATEST',
    fields: {},
    units: {}
  };
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
  screenReady.value = false;
}

function resetBindings(components) {
  for (const key of Object.keys(bindingState)) delete bindingState[key];
  for (const component of Array.isArray(components) ? components : []) {
    const slot = component?.propValue?.bindingKey;
    if (!Object.prototype.hasOwnProperty.call(BINDING_SLOTS, slot)) continue;
    bindingState[slot] = normalizeBinding(parse(component.bindJson, {}), slot);
  }
  const first = SLOT_ORDER.find(slot => bindingState[slot]) || SLOT_ORDER[0];
  selectedSlot.value = first;
  if (!bindingState[first]) bindingState[first] = emptyBinding(first);
  compositionMode.value = getCompositionMode(bindingState.composition || {});
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
    const draft = parse(resp.canvasDraftJson, { components: [] });
    draftComponents.value = Array.isArray(draft.components) ? draft.components : [];
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
}

function setCompositionMode(value) {
  if (selectedSlot.value !== 'composition') return;
  const nextMode = value === 'columns' ? 'columns' : 'rows';
  const binding = selectedBinding.value;
  const obsolete = nextMode === 'columns' ? ['name', 'value'] : ['corporate', 'retail'];
  for (const semantic of obsolete) {
    delete binding.fields[semantic];
    delete binding.units[semantic];
  }
  compositionMode.value = nextMode;
  conflict.value = '';
}

function onDatasourceChange() {
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
  conflict.value = '';
}

function fillProvenUnits(binding) {
  if (!binding?.fields || !binding?.units) return;
  const source = datasources.value.find(item => String(item.id) === String(binding.dsId));
  const options = getDatasourceFieldOptions(source || {});
  for (const [semantic, column] of Object.entries(binding.fields)) {
    if (binding.units[semantic]) continue;
    if (options.some(option => option.col === column && option.amountScale)) binding.units[semantic] = 'YUAN';
  }
}

function setField(semantic, value) {
  const binding = selectedBinding.value;
  if (value) binding.fields[semantic] = value;
  else {
    delete binding.fields[semantic];
    delete binding.units[semantic];
  }
  // amountScale 只证明 rows 的原始金额按元返回；管理端显示“原始元值”并保存 YUAN。
  if (value && !binding.units[semantic]) {
    const option = fieldOptionsFor(semantic).find(item => item.col === value);
    if (option?.amountScale) binding.units[semantic] = 'YUAN';
  }
}

function setUnit(semantic, value) {
  if (value) selectedBinding.value.units[semantic] = value;
  else delete selectedBinding.value.units[semantic];
}

function unitFor(semantic) {
  const binding = selectedBinding.value;
  return binding.units?.[semantic] || '';
}

function unitHint(semantic) {
  const column = selectedBinding.value.fields?.[semantic];
  const option = fieldOptionsFor(semantic).find(item => item.col === column);
  return option?.amountScale ? '数据源已声明金额展示尺度，按原始元值保存，运行时只换算一次。' : '';
}

function unitLabel(unit) {
  return ({ YUAN: '原始元值（YUAN）', TEN_THOUSAND: '原始万元', HUNDRED_MILLION: '原始亿元',
    COUNT: '原始个数', TEN_THOUSAND_COUNT: '原始万户', PERCENT: '百分数', RATIO: '比例（运行时转百分数）' })[unit] || unit;
}

function collectValidBindings() {
  const next = {};
  const problems = [];
  for (const slot of SLOT_ORDER) {
    const candidate = bindingState[slot];
    if (!candidate || !candidate.dsId) continue;
    const mode = slot === 'composition'
      ? (compositionMode.value === 'columns' || getCompositionMode(candidate) === 'columns' ? 'columns' : 'rows')
      : null;
    let slotProblems = validateBinding(slot, candidate);
    if (slot === 'composition' && mode === 'columns') {
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
    if (slotProblems.length) problems.push(...slotProblems.map(message => `${BINDING_SLOTS[slot].label}：${message}`));
    let fieldProblemCount = 0;
    for (const [semantic, column] of Object.entries(candidate.fields || {})) {
      if (!fieldOptionsForBinding(slot, semantic, candidate.dsId).some(option => option.col === column)) {
        problems.push(`${BINDING_SLOTS[slot].label}：字段类型或元数据不匹配：${semantic}`);
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
  if (!Object.keys(bindings).length) throw new Error('至少绑定一个经营槽位后才能保存');
  const presentation = { type: 'CODE', template: 'branch-overview-v1' };
  const style = { ...canvasStyle.value, presentation };
  const components = buildCodeComponents(bindings, draftComponents.value);
  if (!components.length) throw new Error('没有可保存的代码组件');
  return { screenId: id, expectedVersion: canvasVersion.value, canvasStyle: style, components };
}

function adoptSaveResponse(resp, fallbackComponents) {
  if (resp && Number.isSafeInteger(resp.canvasVersion)) canvas.value = { ...canvas.value, canvasVersion: resp.canvasVersion };
  const draft = parse(resp?.canvasDraftJson, null);
  draftComponents.value = Array.isArray(draft?.components) ? draft.components : fallbackComponents;
  canvasStyle.value = { ...canvasStyle.value, presentation: { type: 'CODE', template: 'branch-overview-v1' } };
  resetBindings(draftComponents.value);
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
    adoptSaveResponse(response, payload.components);
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
    activeScreenId.value = String(id);
    activeScreen.value = screens.value.find(screen => Number(screen.id) === id) || null;
    await loadCanvas();
  }
});

onMounted(async () => {
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
  disposed = true;
  ++loadGeneration;
  loading.value = false;
});

defineExpose({ loadScreens, loadCanvas, saveDraft, publishDraft, discardDraft, collectValidBindings, onSettingsSaved });
</script>

<style scoped>
.panorama-bindings { color: #1f2d3d; background: #f4f7fb; min-height: 100%; padding: 24px; box-sizing: border-box; }
.panorama-bindings__header, .panorama-bindings__screen-bar, .panorama-bindings__body, .panorama-bindings__footer { max-width: 1240px; margin: 0 auto; }
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
@media (max-width: 760px) { .panorama-bindings__header, .panorama-bindings__body { display:block; } .panorama-bindings__actions { margin-top:12px; } .panorama-bindings__slots { margin-bottom:14px; } .panorama-bindings__field-row { grid-template-columns:1fr; } .panorama-bindings__unit-hint { grid-column:auto; } .panorama-bindings__footer { flex-wrap:wrap; justify-content:flex-start; } }
</style>
