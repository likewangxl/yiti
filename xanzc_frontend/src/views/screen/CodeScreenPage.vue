<template>
  <main class="code-screen-page" :data-mode="activeDataMode || undefined" :aria-label="dataModeAriaLabel(activeDataMode)" :aria-busy="state === 'loading' ? 'true' : 'false'">
    <header class="code-screen-page__banner">
      <div class="code-screen-page__banner-meta">
        <strong>{{ dataModeLabel(activeDataMode) }}</strong>
        <span v-if="dataModeDescription(activeDataMode)">{{ dataModeDescription(activeDataMode) }}</span>
        <span v-if="activeDataMode === 'LIVE'">统计口径与环境见来源说明</span>
        <span data-testid="runtime-updated-at">本次查询/刷新时间 {{ lastUpdated }}</span>
        <button type="button" data-action="refresh-runtime" @click="refreshScreen">刷新</button>
        <button type="button" data-action="back-to-screen-center" @click="backToCenter">返回大屏中心</button>
      </div>
    </header>

    <section v-if="state === 'loading'" class="code-screen-page__state" role="status" aria-live="polite">
      正在确认当前账号的大屏权限…
    </section>

    <section v-else-if="state === 'forbidden'" class="code-screen-page__state code-screen-page__state--error" data-testid="code-screen-forbidden" role="alert">
      <h2>当前账号无权访问此大屏</h2>
      <p>目录授权已变化，请返回大屏中心重新选择。</p>
      <button type="button" data-action="back-to-screen-center" @click="backToCenter">返回大屏中心</button>
    </section>

    <section v-else-if="state === 'unsupported'" class="code-screen-page__state code-screen-page__state--error" data-testid="code-screen-unsupported" role="alert">
      <h2>大屏模板不可用</h2>
      <p>目录或发布包未返回受支持的模板或数据模式。</p>
      <button type="button" data-action="back-to-screen-center" @click="backToCenter">返回大屏中心</button>
    </section>

    <section v-else-if="state === 'error'" class="code-screen-page__state code-screen-page__state--error" data-testid="code-screen-error" role="alert">
      <h2>大屏目录确认失败</h2>
      <p>{{ errorMessage }}</p>
      <button type="button" data-action="back-to-screen-center" @click="backToCenter">返回大屏中心</button>
    </section>

    <section v-else class="code-screen-page__content">
      <PanoramaRuntime
        v-if="SUPPORTED_TEMPLATES.has(activeTemplate)"
        ref="runtimeRef"
        :view="runtimeView"
        :context="runtimeContext"
        :batch-required="activeTemplate === 'branch-overview-v1'"
        back-path="/screens"
        @refresh="onRuntimeRefresh"
      />
    </section>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getScreenView, listAvailableScreens } from '@/api/screen';
import { useUserStore } from '@/stores/user';
import PanoramaRuntime from './panorama/PanoramaRuntime.vue';
import { BUSINESS_LINE_TARGETS, parseNavigationQuery, resolveInstitution } from './presentation/navigation/navigationModel';

const CATALOG_REGISTRATIONS = Object.freeze({
  'branch-overview-v1': Object.freeze({ ...BUSINESS_LINE_TARGETS.COMMON, screenName: '分行经营总览', dataModes: Object.freeze(['TEST']) }),
  'corporate-overview-v1': Object.freeze({ ...BUSINESS_LINE_TARGETS.CORP, screenName: '对公经营总览', dataModes: Object.freeze(['TEST', 'LIVE']) }),
  'retail-overview-v1': Object.freeze({ ...BUSINESS_LINE_TARGETS.RETAIL, screenName: '零售经营总览', dataModes: Object.freeze(['TEST', 'LIVE']) })
});
const SUPPORTED_TEMPLATES = new Set(Object.keys(CATALOG_REGISTRATIONS));

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const state = ref('loading');
const errorMessage = ref('');
const activeEntry = ref(null);
const runtimeView = ref(null);
const runtimeRef = ref(null);
const lastUpdated = ref(formatRuntimeTime(new Date()));
let loadGeneration = 0;

const activeTemplate = computed(() => String(route.params.template || ''));
const activeDataMode = computed(() => activeEntry.value?.dataMode || '');
const navigationContext = computed(() => parseNavigationQuery(route.query));

function formatRuntimeTime(value) {
  const date = value instanceof Date ? value : new Date(value);
  const pad = number => String(number).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function dataModeLabel(mode) {
  if (mode === 'TEST') return '非生产联调数据';
  if (mode === 'LIVE') return '已接入数据';
  return '数据状态确认中';
}

function dataModeDescription(mode) {
  if (mode === 'TEST') return '非生产业务数据';
  if (mode === 'LIVE') return '接口数据，不代表生产真实性认证';
  return '';
}

function dataModeAriaLabel(mode) {
  if (mode === 'TEST') return '非生产联调大屏';
  if (mode === 'LIVE') return '已接入数据大屏';
  return '经营大屏';
}

function isForbidden(error) {
  return Number(error?.response?.status || error?.status) === 403
    || ['ORG_NOT_AUTHORIZED', 'ORG_LAYER_UNCONFIRMED', 'ORG_NOT_DISPLAYABLE', 'CITY_NOT_AUTHORIZED', 'NAVIGATION_CONTEXT_MISMATCH'].includes(error?.code);
}

function resolveCatalogEntry(catalog, template) {
  const expected = CATALOG_REGISTRATIONS[template];
  if (!expected || !Array.isArray(catalog)) return { kind: 'unsupported', entry: null };
  if (navigationContext.value.businessLine && navigationContext.value.businessLine !== expected.businessLine) {
    return { kind: 'unsupported', entry: null };
  }
  const matches = catalog.filter(entry => entry
    && entry.screenCode === expected.screenCode
    && entry.template === template);
  if (!matches.length) return { kind: 'forbidden', entry: null };
  if (matches.length !== 1 || !expected.dataModes.includes(matches[0]?.dataMode)) {
    return { kind: 'unsupported', entry: null };
  }
  return { kind: 'ready', entry: { ...matches[0], screenName: expected.screenName } };
}

const runtimeContext = computed(() => ({
  screenCode: activeEntry.value?.screenCode,
  schemaVersion: runtimeView.value?.runtimeSchemaVersion,
  runtimeSchemaVersion: runtimeView.value?.runtimeSchemaVersion,
  orgCode: navigationContext.value.orgCode,
  cityCode: navigationContext.value.cityCode,
  businessLine: navigationContext.value.businessLine || activeEntry.value?.bizLine || '',
  period: navigationContext.value.period,
  metricKey: navigationContext.value.metricKey,
  sourceScreenCode: navigationContext.value.sourceScreenCode,
  sourcePreview: navigationContext.value.sourcePreview,
  navigationView: navigationContext.value.view,
  navigationState: navigationContext.value.state
}));

function parseRuntimeView(response, entry) {
  if (!response || response.state !== 'published' || response.screenCode !== entry.screenCode
      || response.runtimeSchemaVersion !== 2 || !response.renderPackageJson) throw new Error('发布包不可用');
  let renderPackage;
  try { renderPackage = JSON.parse(response.renderPackageJson); } catch { throw new Error('渲染包解析失败'); }
  if (!renderPackage || renderPackage.schemaVersion !== 2
      || !Array.isArray(renderPackage.components)
      || !renderPackage.bindSnapshots || typeof renderPackage.bindSnapshots !== 'object'
      || Array.isArray(renderPackage.bindSnapshots)) throw new Error('发布包身份不可用');
  const presentation = renderPackage?.canvasStyle?.presentation;
  if (presentation?.type !== 'CODE' || presentation?.template !== entry.template) throw new Error('发布包模板不匹配');
  const orgCode = navigationContext.value.orgCode;
  if (orgCode) {
    const resolved = resolveInstitution({ ...response, renderPackage }, orgCode);
    if (!resolved.authorized) {
      const error = new Error('当前机构不在目标屏授权目录中');
      error.code = 'ORG_NOT_AUTHORIZED';
      throw error;
    }
    if (!resolved.layer.known) {
      const error = new Error('当前机构经营层级待确认');
      error.code = 'ORG_LAYER_UNCONFIRMED';
      throw error;
    }
    if (!resolved.layer.displayable) {
      const error = new Error('当前机构不属于允许展示的经营层级');
      error.code = 'ORG_NOT_DISPLAYABLE';
      throw error;
    }
    if (navigationContext.value.cityCode && String(resolved.institution.cityCode || '') !== navigationContext.value.cityCode) {
      const error = new Error('当前机构与城市上下文不一致');
      error.code = 'NAVIGATION_CONTEXT_MISMATCH';
      throw error;
    }
  } else if (navigationContext.value.cityCode) {
    const directory = Array.isArray(response.panoramaInstitutions || response.panorama_institutions)
      ? (response.panoramaInstitutions || response.panorama_institutions) : [];
    if (!directory.some(item => String(item?.cityCode || item?.city_code || '') === navigationContext.value.cityCode)) {
      const error = new Error('当前城市不在目标屏授权目录中');
      error.code = 'CITY_NOT_AUTHORIZED';
      throw error;
    }
  }
  return { ...response, renderPackage };
}

async function loadCatalog() {
  const generation = ++loadGeneration;
  activeEntry.value = null;
  runtimeView.value = null;
  errorMessage.value = '';
  if (!userStore.user) {
    state.value = 'forbidden';
    return;
  }
  state.value = 'loading';
  try {
    const catalog = await listAvailableScreens();
    if (generation !== loadGeneration) return;
    const resolved = resolveCatalogEntry(catalog, activeTemplate.value);
    if (resolved.kind !== 'ready') {
      state.value = resolved.kind;
      return;
    }
    activeEntry.value = resolved.entry;
    const parsedRuntimeView = parseRuntimeView(await getScreenView(resolved.entry.screenCode), resolved.entry);
    if (generation !== loadGeneration) return;
    runtimeView.value = parsedRuntimeView;
    if (generation !== loadGeneration) return;
    lastUpdated.value = formatRuntimeTime(new Date());
    state.value = 'ready';
  } catch (error) {
    if (generation !== loadGeneration) return;
    activeEntry.value = null;
    runtimeView.value = null;
    state.value = isForbidden(error) ? 'forbidden' : 'error';
    errorMessage.value = error?.message || '请稍后重试';
  }
}

function refreshScreen() {
  runtimeRef.value?.refresh?.();
  lastUpdated.value = formatRuntimeTime(new Date());
}

function onRuntimeRefresh() {
  lastUpdated.value = formatRuntimeTime(new Date());
}

function backToCenter() {
  router.push('/screens');
}

watch(() => [route.params.template, route.query, userStore.user], loadCatalog, { deep: true });
onMounted(loadCatalog);
onBeforeUnmount(() => { loadGeneration += 1; });
</script>

<style scoped>
.code-screen-page { min-height: 100dvh; box-sizing: border-box; color: #eaf2ff; background: #07102c; }
.code-screen-page__banner { position: sticky; top: 0; z-index: 10; display: flex; align-items: center; justify-content: flex-end; gap: 20px; height: 42px; min-height: 42px; box-sizing: border-box; padding: 8px 20px; color: #eaf2ff; background: rgba(7, 16, 44, .96); border-bottom: 1px solid rgba(119, 178, 255, .28); }
.code-screen-page__banner-meta { display: flex; align-items: center; gap: 12px; color: #ffd78a; font-size: 12px; }
.code-screen-page__banner-meta strong { padding: 5px 8px; color: #251a04; background: #f5c565; border-radius: 5px; }
.code-screen-page__banner-meta button, .code-screen-page__state button { padding: 5px 10px; color: #dcecff; background: rgba(21, 54, 101, .76); border: 1px solid rgba(127, 199, 255, .45); border-radius: 5px; font: inherit; cursor: pointer; }
.code-screen-page__banner-meta button:hover, .code-screen-page__banner-meta button:focus-visible, .code-screen-page__state button:focus-visible { border-color: #bde2ff; outline: 2px solid rgba(127, 199, 255, .45); outline-offset: 2px; }
.code-screen-page__state { display: grid; justify-items: center; gap: 10px; min-height: 55vh; place-content: center; padding: 32px; color: #b8c9dc; text-align: center; }
.code-screen-page__state h2, .code-screen-page__state p { margin: 0; }
.code-screen-page__state h2 { color: #eaf2ff; }
.code-screen-page__state--error h2 { color: #ffd6d1; }
.code-screen-page__content { min-height: 0; }
.code-screen-page :deep(.panorama-runtime) { --cockpit-chrome-height: 42px; min-height: calc(100dvh - 42px); }
@media (max-width: 720px) { .code-screen-page__banner { height: auto; justify-content: flex-start; padding: 7px 12px; } .code-screen-page__banner-meta { flex-wrap: wrap; gap: 6px 10px; } .code-screen-page :deep(.panorama-runtime) { min-height: calc(100dvh - 42px); } }
</style>
