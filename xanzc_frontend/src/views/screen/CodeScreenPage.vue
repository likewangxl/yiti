<template>
  <main class="code-screen-page" data-demo="true" aria-label="演示大屏" :aria-busy="state === 'loading' ? 'true' : 'false'">
    <header class="code-screen-page__banner">
      <div class="code-screen-page__banner-meta">
        <strong>演示数据</strong>
        <span>非业务数据</span>
        <span data-testid="demo-updated-at">更新时间 {{ lastUpdated }}</span>
        <button type="button" data-action="refresh-demo" @click="refreshDemo">刷新</button>
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
      <p>目录未返回受支持的演示模板或数据模式。</p>
      <button type="button" data-action="back-to-screen-center" @click="backToCenter">返回大屏中心</button>
    </section>

    <section v-else-if="state === 'error'" class="code-screen-page__state code-screen-page__state--error" role="alert">
      <h2>大屏目录确认失败</h2>
      <p>{{ errorMessage }}</p>
      <button type="button" data-action="back-to-screen-center" @click="backToCenter">返回大屏中心</button>
    </section>

    <section v-else class="code-screen-page__content">
      <PanoramaDashboard
        v-if="activeTemplate === 'branch-overview-v1'"
        :model="demoModel"
        :loading="false"
        error=""
        :demo="true"
        @refresh="refreshDemo"
        @back="backToCenter"
      />
      <RetailDashboard
        v-else-if="activeTemplate === 'retail-overview-v1'"
        :model="retailDemoModel"
        :loading="false"
        error=""
        :demo="true"
        @refresh="refreshDemo"
        @back="backToCenter"
      />
    </section>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { listAvailableScreens } from '@/api/screen';
import { useUserStore } from '@/stores/user';
import PanoramaDashboard from './panorama/PanoramaDashboard.vue';
import RetailDashboard from './panorama/RetailDashboard.vue';
import { demoModel } from './panorama/demoModel.js';
import { retailDemoModel } from './panorama/retailDemoModel.js';

const CATALOG_REGISTRATIONS = Object.freeze({
  'branch-overview-v1': Object.freeze({ screenCode: 'SCR_PROVINCE', screenName: '分行经营总览' }),
  'retail-overview-v1': Object.freeze({ screenCode: 'SCR_RETAIL_OVERVIEW', screenName: '零售经营总览' })
});

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const state = ref('loading');
const errorMessage = ref('');
const activeEntry = ref(null);
const lastUpdated = ref(formatDemoTime(new Date()));
let loadGeneration = 0;

const activeTemplate = computed(() => String(route.params.template || ''));

function formatDemoTime(value) {
  const date = value instanceof Date ? value : new Date(value);
  const pad = number => String(number).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function isForbidden(error) {
  return Number(error?.response?.status || error?.status) === 403;
}

function resolveCatalogEntry(catalog, template) {
  const expected = CATALOG_REGISTRATIONS[template];
  if (!expected || !Array.isArray(catalog)) return { kind: 'unsupported', entry: null };
  const matches = catalog.filter(entry => entry
    && entry.screenCode === expected.screenCode
    && entry.template === template);
  if (!matches.length) return { kind: 'forbidden', entry: null };
  if (matches.length !== 1 || matches[0].dataMode !== 'DEMO') {
    return { kind: 'unsupported', entry: null };
  }
  return { kind: 'ready', entry: { ...matches[0], screenName: expected.screenName } };
}

async function loadCatalog() {
  const generation = ++loadGeneration;
  activeEntry.value = null;
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
    lastUpdated.value = formatDemoTime(new Date());
    state.value = 'ready';
  } catch (error) {
    if (generation !== loadGeneration) return;
    state.value = isForbidden(error) ? 'forbidden' : 'error';
    errorMessage.value = error?.message || '请稍后重试';
  }
}

function refreshDemo() {
  lastUpdated.value = formatDemoTime(new Date());
}

function backToCenter() {
  router.push('/screens');
}

watch(() => [route.params.template, userStore.user], loadCatalog);
onMounted(loadCatalog);
onBeforeUnmount(() => { loadGeneration += 1; });
</script>

<style scoped>
.code-screen-page { min-height: 100vh; box-sizing: border-box; color: #eaf2ff; background: #07102c; }
.code-screen-page__banner { position: sticky; top: 0; z-index: 10; display: flex; align-items: center; justify-content: flex-end; gap: 20px; padding: 8px 20px; color: #eaf2ff; background: rgba(7, 16, 44, .96); border-bottom: 1px solid rgba(119, 178, 255, .28); }
.code-screen-page__banner-meta { display: flex; align-items: center; gap: 12px; color: #ffd78a; font-size: 12px; }
.code-screen-page__banner-meta strong { padding: 5px 8px; color: #251a04; background: #f5c565; border-radius: 5px; }
.code-screen-page__banner-meta button, .code-screen-page__state button { padding: 5px 10px; color: #dcecff; background: rgba(21, 54, 101, .76); border: 1px solid rgba(127, 199, 255, .45); border-radius: 5px; font: inherit; cursor: pointer; }
.code-screen-page__banner-meta button:hover, .code-screen-page__banner-meta button:focus-visible, .code-screen-page__state button:focus-visible { border-color: #bde2ff; outline: 2px solid rgba(127, 199, 255, .45); outline-offset: 2px; }
.code-screen-page__state { display: grid; justify-items: center; gap: 10px; min-height: 55vh; place-content: center; padding: 32px; color: #b8c9dc; text-align: center; }
.code-screen-page__state h2, .code-screen-page__state p { margin: 0; }
.code-screen-page__state h2 { color: #eaf2ff; }
.code-screen-page__state--error h2 { color: #ffd6d1; }
.code-screen-page__content { min-height: calc(100vh - 42px); }
.code-screen-page :deep(.panorama-demo-badge), .code-screen-page :deep(.retail-demo-badge) { display: none; }
@media (max-width: 720px) { .code-screen-page__banner { justify-content: flex-start; padding: 7px 12px; } .code-screen-page__banner-meta { flex-wrap: wrap; gap: 6px 10px; } }
</style>
