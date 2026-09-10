<template>
  <main class="screen-center" aria-labelledby="screen-center-title" :aria-busy="loading ? 'true' : 'false'">
    <header class="screen-center__header">
      <div>
        <p class="screen-center__eyebrow">经营分析</p>
        <h1 id="screen-center-title">大屏中心</h1>
        <p class="screen-center__description">选择你有权限访问的经营大屏。</p>
      </div>
      <div v-if="!loading && !loadError" class="screen-center__count" aria-label="可访问屏数量">
        <strong>{{ screens.length }}</strong>
        <span>个可访问大屏</span>
      </div>
    </header>

    <section class="screen-center__toolbar" aria-label="大屏筛选">
      <div class="screen-center__filters" role="group" aria-label="业务条线">
        <button
          v-for="line in BIZ_LINE_FILTERS"
          :key="line.value"
          type="button"
          class="screen-center__filter"
          :class="{ 'screen-center__filter--active': activeBizLine === line.value }"
          :data-biz-line="line.value"
          :aria-pressed="activeBizLine === line.value ? 'true' : 'false'"
          @click="activeBizLine = line.value"
        >
          {{ line.label }}
        </button>
      </div>
      <label class="screen-center__search">
        <span>搜索</span>
        <input v-model="searchKeyword" type="search" aria-label="搜索大屏" placeholder="按名称或编码搜索" />
      </label>
    </section>

    <section v-if="loading" class="screen-center__state screen-center__state--loading" aria-live="polite">
      <span class="screen-center__state-icon" aria-hidden="true">⌁</span>
      <h2>正在加载大屏目录</h2>
      <p>正在确认当前用户的大屏权限。</p>
    </section>

    <section v-else-if="loadError" class="screen-center__state screen-center__state--error" role="alert">
      <span class="screen-center__state-icon" aria-hidden="true">!</span>
      <h2>大屏目录加载失败</h2>
      <p>{{ loadError }}</p>
      <button type="button" data-action="retry-screen-catalog" @click="loadCatalog">重试</button>
    </section>

    <section v-else-if="screens.length === 0" class="screen-center__state" aria-live="polite">
      <span class="screen-center__state-icon" aria-hidden="true">▣</span>
      <h2>当前没有可访问大屏</h2>
      <p>获得大屏访问权限后，对应页面会显示在这里。</p>
    </section>

    <section v-else-if="filteredScreens.length === 0" class="screen-center__state" aria-live="polite">
      <span class="screen-center__state-icon" aria-hidden="true">⌕</span>
      <h2>没有匹配的大屏</h2>
      <p>请调整条线筛选或搜索关键词后重试。</p>
    </section>

    <section v-else class="screen-center__grid" aria-label="可访问大屏列表">
      <article
        v-for="screen in filteredScreens"
        :key="screen.template"
        class="screen-card"
        data-screen-card
        :data-screen-code="screen.screenCode"
        :data-template="screen.template"
      >
        <div class="screen-card__icon" aria-hidden="true">
          <svg viewBox="0 0 48 48" focusable="false">
            <rect x="5" y="7" width="38" height="27" rx="3" />
            <path d="M12 15h24M12 22h12M12 28h18M20 34v7M28 34v7M15 41h18" />
          </svg>
        </div>
        <div class="screen-card__body">
          <div class="screen-card__title-row">
            <h2>{{ displayName(screen) }}</h2>
            <span class="screen-card__demo-badge">演示数据</span>
          </div>
          <p class="screen-card__code">编码：{{ screen.screenCode }}</p>
          <div class="screen-card__meta">
            <span data-biz-label>{{ bizLineLabel(screen.bizLine) }}</span>
            <span>{{ viewLevelLabel(screen.viewLevel) }}</span>
          </div>
          <button type="button" class="screen-card__open" @click="openScreen(screen)">进入大屏</button>
        </div>
      </article>
    </section>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { listAvailableScreens } from '@/api/screen';
import { useUserStore } from '@/stores/user';

const BIZ_LINE_FILTERS = Object.freeze([
  { value: 'ALL', label: '全部' },
  { value: 'COMMON', label: '综合' },
  { value: 'CORP', label: '对公' },
  { value: 'RETAIL', label: '零售' }
]);
const BIZ_LINE_LABELS = Object.freeze({ COMMON: '综合', CORP: '对公', RETAIL: '零售' });
const VIEW_LEVEL_LABELS = Object.freeze({ PROVINCE: '全辖', BRANCH: '机构', PERSON: '个人' });
const SUPPORTED_TEMPLATES = new Set(['branch-overview-v1', 'retail-overview-v1']);

const router = useRouter();
const userStore = useUserStore();
const screens = ref([]);
const activeBizLine = ref('ALL');
const searchKeyword = ref('');
const loading = ref(false);
const loadError = ref('');
let loadGeneration = 0;

const filteredScreens = computed(() => {
  const keyword = searchKeyword.value.trim().toLocaleLowerCase();
  return screens.value.filter((screen) => {
    const bizLine = String(screen.bizLine || '').toUpperCase();
    if (activeBizLine.value !== 'ALL' && bizLine !== activeBizLine.value) return false;
    if (!keyword) return true;
    return [screen.screenName, screen.screenCode]
      .filter(Boolean)
      .some((value) => String(value).toLocaleLowerCase().includes(keyword));
  });
});

function displayName(screen) {
  return String(screen?.screenName || screen?.screenCode || '未命名大屏');
}

function bizLineLabel(value) {
  const normalized = String(value || '').toUpperCase();
  return BIZ_LINE_LABELS[normalized] || '其他条线';
}

function viewLevelLabel(value) {
  const normalized = String(value || '').toUpperCase();
  return VIEW_LEVEL_LABELS[normalized] || '未指定视角';
}

function normalizeCatalog(catalog) {
  if (!Array.isArray(catalog)) throw new Error('大屏目录响应格式无效');
  return catalog.filter((screen) => screen
    && screen.screenCode
    && SUPPORTED_TEMPLATES.has(screen.template)
    && screen.dataMode === 'DEMO');
}

async function loadCatalog() {
  const generation = ++loadGeneration;
  loading.value = true;
  loadError.value = '';
  screens.value = [];
  try {
    const catalog = await listAvailableScreens();
    if (generation !== loadGeneration) return;
    screens.value = normalizeCatalog(catalog);
  } catch (error) {
    if (generation !== loadGeneration) return;
    screens.value = [];
    loadError.value = error?.message || '请稍后重试';
  } finally {
    if (generation === loadGeneration) loading.value = false;
  }
}

function openScreen(screen) {
  const template = screen?.template;
  if (!SUPPORTED_TEMPLATES.has(template) || screen?.dataMode !== 'DEMO') return;
  router.push({ name: 'CodeScreenPage', params: { template } });
}

watch(() => userStore.user, (user) => {
  if (user) {
    loadCatalog();
    return;
  }
  loadGeneration += 1;
  screens.value = [];
  loadError.value = '';
  loading.value = false;
});

onMounted(loadCatalog);
onBeforeUnmount(() => {
  loadGeneration += 1;
  screens.value = [];
});
</script>

<style lang="scss" scoped>
.screen-center { box-sizing: border-box; width: min(100%, 1440px); min-width: 0; margin: 0 auto; padding: 4px 0 32px; color: var(--color-text); }
.screen-center__header { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; padding: 8px 0 24px; }
.screen-center__eyebrow { margin: 0 0 8px; color: var(--color-brand-700); font-size: 12px; font-weight: 600; letter-spacing: .12em; }
.screen-center h1, .screen-center h2, .screen-center p { margin-top: 0; }
.screen-center h1 { margin-bottom: 8px; font-size: clamp(24px, 3vw, 32px); line-height: 1.2; }
.screen-center__description { max-width: 720px; margin-bottom: 0; color: var(--color-text-muted); line-height: 1.6; }
.screen-center__count { display: flex; flex: 0 0 auto; align-items: baseline; gap: 6px; color: var(--color-text-muted); white-space: nowrap; }
.screen-center__count strong { color: var(--color-brand-700); font-size: 32px; line-height: 1; }
.screen-center__toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 14px 16px; background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius-control); box-shadow: var(--shadow-surface); }
.screen-center__filters { display: flex; flex-wrap: wrap; gap: 6px; min-width: 0; }
.screen-center__filter { min-height: 34px; padding: 0 13px; color: var(--color-text-muted); background: transparent; border: 1px solid transparent; border-radius: 17px; font: inherit; cursor: pointer; }
.screen-center__filter:hover { color: var(--color-brand-700); background: var(--color-surface-soft); }
.screen-center__filter--active { color: var(--color-brand-700); background: var(--color-brand-100); border-color: var(--color-brand-500); font-weight: 600; }
.screen-center__filter:focus-visible, .screen-center__search input:focus-visible, .screen-card__open:focus-visible, .screen-center__state button:focus-visible { outline: 2px solid var(--color-focus); outline-offset: 2px; }
.screen-center__search { display: flex; align-items: center; flex: 0 1 280px; gap: 8px; color: var(--color-text-muted); font-size: 13px; white-space: nowrap; }
.screen-center__search > span { flex: 0 0 auto; }
.screen-center__search input { box-sizing: border-box; width: 100%; min-width: 0; height: 36px; padding: 0 11px; color: var(--color-text); background: var(--color-surface-soft); border: 1px solid var(--color-border); border-radius: var(--radius-control); font: inherit; }
.screen-center__grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 16px; padding-top: 20px; }
.screen-card { display: flex; min-width: 0; min-height: 190px; padding: 20px; gap: 16px; background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius-control); box-shadow: var(--shadow-surface); }
.screen-card__icon { display: grid; flex: 0 0 50px; place-items: center; width: 50px; height: 50px; color: var(--color-brand-700); background: var(--color-brand-100); border-radius: 14px; }
.screen-card__icon svg { width: 34px; height: 34px; fill: none; stroke: currentColor; stroke-width: 1.5; stroke-linecap: round; stroke-linejoin: round; }
.screen-card__body { display: flex; flex: 1 1 auto; min-width: 0; flex-direction: column; }
.screen-card__title-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; }
.screen-card h2 { margin-bottom: 7px; overflow: hidden; color: var(--color-text); font-size: 17px; line-height: 1.4; text-overflow: ellipsis; white-space: nowrap; }
.screen-card__demo-badge { flex: 0 0 auto; padding: 4px 7px; color: var(--color-warning-700, #8a5a00); background: var(--color-warning-100, #fff5d6); border-radius: 10px; font-size: 11px; white-space: nowrap; }
.screen-card__code { overflow: hidden; margin-bottom: 12px; color: var(--color-text-muted); font-family: var(--font-mono, ui-monospace, monospace); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.screen-card__meta { display: flex; flex-wrap: wrap; gap: 7px; color: var(--color-text-muted); font-size: 12px; }
.screen-card__meta span { padding: 4px 8px; background: var(--color-surface-soft); border-radius: 12px; }
.screen-card__open { align-self: flex-start; min-height: 34px; margin-top: auto; padding: 0 14px; color: var(--color-brand-700); background: transparent; border: 1px solid var(--color-brand-300); border-radius: var(--radius-control); font: inherit; cursor: pointer; }
.screen-card__open:hover { color: var(--color-surface); background: var(--color-brand-700); border-color: var(--color-brand-700); }
.screen-center__state { display: grid; justify-items: center; gap: 8px; padding: 90px 20px; color: var(--color-text-muted); text-align: center; }
.screen-center__state h2 { margin-bottom: 0; color: var(--color-text); font-size: 20px; }
.screen-center__state p { max-width: 520px; margin-bottom: 4px; line-height: 1.6; }
.screen-center__state-icon { color: var(--color-brand-700); font-size: 36px; line-height: 1; }
.screen-center__state--error { color: var(--color-danger-700, #b42318); }
.screen-center__state--error .screen-center__state-icon { color: inherit; }
.screen-center__state button { min-height: 34px; padding: 0 14px; color: var(--color-brand-700); background: transparent; border: 1px solid var(--color-brand-300); border-radius: var(--radius-control); font: inherit; cursor: pointer; }
@media (max-width: 720px) { .screen-center__header, .screen-center__toolbar { align-items: stretch; flex-direction: column; } .screen-center__count { align-self: flex-start; } .screen-center__search { flex-basis: auto; } .screen-center__grid { grid-template-columns: 1fr; } }
</style>
