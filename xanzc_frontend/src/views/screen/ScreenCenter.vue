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

    <section v-if="draftPreviewEntries.length" class="screen-center__preview-tools" data-testid="screen-center-preview-tools" aria-label="新版草稿预览入口">
      <div class="screen-center__preview-copy">
        <p class="screen-center__preview-eyebrow">授权草稿入口</p>
        <h2>新版草稿预览</h2>
        <p>打开当前保存的新版布局草稿，预览结果由后端按当前用户权限确认。</p>
      </div>
      <div class="screen-center__preview-actions" role="group" aria-label="新版草稿预览入口">
        <button
          v-for="entry in draftPreviewEntries"
          :key="entry.screenCode"
          type="button"
          :data-action="entry.action"
          :data-preview-screen-code="entry.screenCode"
          @click="openDraftPreview(entry)"
        >
          <strong>{{ entry.label }}</strong>
          <span>未发布草稿预览</span>
        </button>
      </div>
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
      <h2>当前没有已接入大屏</h2>
      <p>尚未接入的授权大屏需要完成可信发布和数据源绑定后，才会显示在这里。</p>
    </section>

    <section v-else-if="filteredScreens.length === 0" class="screen-center__state" aria-live="polite">
      <span class="screen-center__state-icon" aria-hidden="true">⌕</span>
      <h2>没有匹配的大屏</h2>
      <p>请调整条线筛选或搜索关键词后重试。</p>
    </section>

    <section v-else class="screen-center__grid" aria-label="可访问大屏列表">
      <article
        v-for="screen in filteredScreens"
        :key="screen.screenCode"
        class="screen-card"
        data-screen-card
        :data-screen-kind="screen.kind || 'catalog'"
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
            <span v-if="!isPersonalScreen(screen)" class="screen-card__mode-badge">{{ screenDataModeLabel(screen) }}</span>
          </div>
          <p v-if="isPersonalScreen(screen)" class="screen-card__description">个人核心指标、今日优先事项、我的客户、我发起的业务进度</p>
          <p v-else-if="isBranchOperatingScreen(screen)" class="screen-card__description">{{ screen.description }}</p>
          <p v-else class="screen-card__code">编码：{{ screen.screenCode }}</p>
          <div class="screen-card__meta">
            <template v-if="isPersonalScreen(screen)">
              <span>个人</span>
              <span>本人业务数据</span>
            </template>
            <template v-else>
              <span data-biz-label>{{ bizLineLabel(screen.bizLine) }}</span>
              <span>{{ viewLevelLabel(screen.viewLevel) }}</span>
              <span v-if="screen.dataMode === 'LIVE'" data-testid="screen-live-source-note">统计口径与环境见来源说明</span>
            </template>
          </div>
          <button
            type="button"
            class="screen-card__open"
            :data-action="isBranchOperatingScreen(screen) ? 'open-branch-operating' : undefined"
            @click="openScreen(screen)"
          >{{ isPersonalScreen(screen) ? '进入驾驶舱' : '进入大屏' }}</button>
        </div>
      </article>
    </section>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { listAvailableScreens } from '@/api/screen';
import { useMenuStore } from '@/stores/menu';
import { usePermissionStore } from '@/stores/permission';
import { useUserStore } from '@/stores/user';

const BIZ_LINE_FILTERS = Object.freeze([
  { value: 'ALL', label: '全部' },
  { value: 'COMMON', label: '综合' },
  { value: 'CORP', label: '对公' },
  { value: 'RETAIL', label: '零售' }
]);
const BIZ_LINE_LABELS = Object.freeze({ COMMON: '综合', CORP: '对公', RETAIL: '零售' });
const VIEW_LEVEL_LABELS = Object.freeze({ PROVINCE: '全辖', BRANCH: '机构', PERSON: '个人' });
const SUPPORTED_TEMPLATES = new Set(['branch-overview-v1', 'retail-overview-v1', 'corporate-overview-v1']);
const REGISTERED_MODES = Object.freeze({
  'branch-overview-v1': Object.freeze(['TEST']),
  'retail-overview-v1': Object.freeze(['TEST', 'LIVE']),
  'corporate-overview-v1': Object.freeze(['TEST', 'LIVE'])
});
const FIXED_SCREEN_CODES = new Set(['SCR_PROVINCE', 'SCR_CORP_OVERVIEW', 'SCR_RETAIL_OVERVIEW']);
const DRAFT_PREVIEW_RESOURCE = '/api/screen/admin/canvas/*';
const DRAFT_PREVIEW_REGISTRY = Object.freeze([
  Object.freeze({ screenCode: 'SCR_CORP_OVERVIEW', template: 'corporate-overview-v1', label: '对公经营总览', action: 'open-corporate-preview' }),
  Object.freeze({ screenCode: 'SCR_RETAIL_OVERVIEW', template: 'retail-overview-v1', label: '零售经营总览', action: 'open-retail-preview' }),
  Object.freeze({ screenCode: 'SCR_PROVINCE', template: 'branch-overview-v1', label: '分行经营总览', action: 'open-branch-preview' })
]);
const PERSONAL_SCREEN = Object.freeze({
  key: 'personal-dashboard',
  kind: 'personal',
  screenCode: 'personal-dashboard',
  screenName: '我的经营驾驶舱',
  viewLevel: 'PERSON',
  bizLine: 'COMMON'
});
const BRANCH_OPERATING_SCREEN = Object.freeze({
  key: 'branch-operating',
  kind: 'branch-operating',
  screenCode: 'branch-operating',
  screenName: '支行经营总览',
  viewLevel: 'BRANCH',
  bizLine: 'COMMON',
  dataMode: 'TEST',
  description: '数据库测试场景，可切换系统存量数据后查看单支行核心指标、经营趋势与目标完成情况'
});

const router = useRouter();
const menuStore = useMenuStore();
const permissionStore = usePermissionStore();
const userStore = useUserStore();
const screens = ref([]);
const draftPreviewEntries = ref([]);
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
    const searchValues = isPersonalScreen(screen)
      ? [screen.screenName, screen.screenCode, '个人', '个人大屏', 'personal', 'personal-dashboard']
      : [screen.screenName, screen.screenCode];
    return searchValues
      .filter(Boolean)
      .some((value) => String(value).toLocaleLowerCase().includes(keyword));
  });
});

function isPersonalScreen(screen) {
  return screen?.kind === 'personal';
}

function isBranchOperatingScreen(screen) {
  return screen?.kind === 'branch-operating';
}

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

function dataModeLabel(value) {
  if (value === 'TEST') return '测试库数据';
  if (value === 'LIVE') return '已接入数据';
  return '数据模式未知';
}

function screenDataModeLabel(screen) {
  return isBranchOperatingScreen(screen) && screen?.dataMode === 'TEST'
    ? '测试数据'
    : dataModeLabel(screen?.dataMode);
}

function hasRegisteredMode(screen) {
  return REGISTERED_MODES[screen?.template]?.includes(screen?.dataMode) || false;
}

function isFixedScreen(screen) {
  return FIXED_SCREEN_CODES.has(screen?.screenCode);
}

function normalizeCatalog(catalog) {
  if (!Array.isArray(catalog)) throw new Error('大屏目录响应格式无效');
  return catalog.filter((screen) => screen
    && screen.screenCode
    && SUPPORTED_TEMPLATES.has(screen.template)
    && hasRegisteredMode(screen));
}

function hasBranchOperatingSource(catalog) {
  const hasCorporateLive = catalog.some((screen) => screen
    && screen.screenCode === 'SCR_CORP_OVERVIEW'
    && screen.template === 'corporate-overview-v1'
    && screen.dataMode === 'LIVE');
  const hasProvinceTest = catalog.some((screen) => screen
    && screen.screenCode === 'SCR_PROVINCE'
    && screen.template === 'branch-overview-v1'
    && screen.dataMode === 'TEST');
  return hasCorporateLive && hasProvinceTest;
}

function buildScreenDirectory(catalog) {
  if (!hasBranchOperatingSource(catalog)) return catalog;
  return [...catalog, BRANCH_OPERATING_SCREEN];
}

function buildDraftPreviewEntries(catalog) {
  return DRAFT_PREVIEW_REGISTRY.flatMap((registry) => {
    const matches = catalog.filter(candidate => candidate
      && candidate.screenCode === registry.screenCode
      && candidate.template === registry.template
      && hasRegisteredMode(candidate));
    return matches.length === 1 ? [{ ...registry, dataMode: matches[0].dataMode }] : [];
  });
}

function hasDraftPreviewAccess() {
  try {
    return Boolean(userStore.user)
      && permissionStore.loaded === true
      && permissionStore.canAccess(DRAFT_PREVIEW_RESOURCE);
  } catch (_) {
    return false;
  }
}

async function loadCatalog() {
  const generation = ++loadGeneration;
  loading.value = true;
  loadError.value = '';
  screens.value = [];
  draftPreviewEntries.value = [];
  if (!userStore.user) {
    loading.value = false;
    return;
  }

  const menuRequest = Promise.resolve()
    .then(() => menuStore.load())
    .then(() => generation === loadGeneration
      && Boolean(userStore.user)
      && menuStore.loaded === true
      && menuStore.hasUrl('/workspace'))
    .catch(() => false);
  const permissionRequest = Promise.resolve()
    .then(() => permissionStore.load())
    .then(() => generation === loadGeneration && hasDraftPreviewAccess())
    .catch(() => false);
  const catalogRequest = Promise.resolve().then(() => listAvailableScreens());

  try {
    const catalog = await catalogRequest;
    if (generation !== loadGeneration) return;
    const normalizedCatalog = normalizeCatalog(catalog);
    const screenDirectory = buildScreenDirectory(normalizedCatalog);
    // 机构目录是页面主体，菜单授权迟到时先展示已确认的机构结果，避免授权接口延迟阻塞目录。
    screens.value = screenDirectory;
    loading.value = screenDirectory.length === 0;

    const canPreviewDraft = await permissionRequest;
    if (generation !== loadGeneration) return;
    if (canPreviewDraft) draftPreviewEntries.value = buildDraftPreviewEntries(normalizedCatalog);

    const hasPersonalAccess = await menuRequest;
    if (generation !== loadGeneration || loadError.value) return;
    if (hasPersonalAccess) screens.value = [PERSONAL_SCREEN, ...screens.value];
  } catch (error) {
    if (generation !== loadGeneration) return;
    screens.value = [];
    loadError.value = error?.message || '请稍后重试';
  } finally {
    if (generation === loadGeneration) loading.value = false;
  }
}

function openScreen(screen) {
  if (isPersonalScreen(screen)) {
    if (!userStore.user || menuStore.loaded !== true || !menuStore.hasUrl('/workspace')) return;
    router.push({ name: 'PersonalDashboard', query: { from: 'screen-center' } });
    return;
  }
  if (isBranchOperatingScreen(screen)) {
    // 经营主路径统一从省级代码化大屏进入；旧 /branch-operating URL 仍由其
    // 自身页面受保护兼容，不再把独立机构选择页作为中心入口。
    router.push({ name: 'CodeScreenPage', params: { template: 'branch-overview-v1' }, query: { businessLine: 'COMMON' } });
    return;
  }
  const template = screen?.template;
  if (!SUPPORTED_TEMPLATES.has(template) || !hasRegisteredMode(screen)) return;
  if (isFixedScreen(screen)) {
    router.push({ name: 'CodeScreenPage', params: { template } });
    return;
  }
  router.push({ name: 'ScreenView', params: { screenCode: screen.screenCode } });
}

function openDraftPreview(entry) {
  if (!hasDraftPreviewAccess() || !draftPreviewEntries.value.some(candidate => candidate.screenCode === entry?.screenCode)) {
    draftPreviewEntries.value = [];
    return;
  }
  router.push({
    name: 'ScreenView',
    params: { screenCode: entry.screenCode },
    query: { preview: 'draft', from: 'screen-center' }
  });
}

watch(() => userStore.user, (user) => {
  if (user) {
    loadCatalog();
    return;
  }
  loadGeneration += 1;
  screens.value = [];
  draftPreviewEntries.value = [];
  loadError.value = '';
  loading.value = false;
});

watch(
  [() => permissionStore.loaded, () => permissionStore.resourceUrls, () => userStore.user],
  () => {
    if (!hasDraftPreviewAccess()) draftPreviewEntries.value = [];
  }
);

onMounted(loadCatalog);
onBeforeUnmount(() => {
  loadGeneration += 1;
  screens.value = [];
  draftPreviewEntries.value = [];
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
.screen-center__preview-tools { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-top: 16px; padding: 16px 18px; background: var(--color-brand-100); border: 1px solid var(--color-brand-300); border-radius: var(--radius-control); }
.screen-center__preview-copy { min-width: 0; }
.screen-center__preview-copy h2 { margin-bottom: 5px; color: var(--color-text); font-size: 16px; }
.screen-center__preview-copy p { margin-bottom: 0; color: var(--color-text-muted); font-size: 12px; line-height: 1.5; }
.screen-center__preview-copy .screen-center__preview-eyebrow { margin-bottom: 4px; color: var(--color-brand-700); font-size: 11px; font-weight: 700; letter-spacing: .08em; }
.screen-center__preview-actions { display: flex; flex: 0 0 auto; flex-wrap: wrap; gap: 8px; }
.screen-center__preview-actions button { display: flex; min-width: 168px; flex-direction: column; align-items: flex-start; gap: 3px; padding: 9px 12px; color: var(--color-brand-700); background: var(--color-surface); border: 1px solid var(--color-brand-300); border-radius: var(--radius-control); font: inherit; text-align: left; cursor: pointer; }
.screen-center__preview-actions button:hover { background: var(--color-brand-700); border-color: var(--color-brand-700); color: var(--color-surface); }
.screen-center__preview-actions button:focus-visible { outline: 2px solid var(--color-focus); outline-offset: 2px; }
.screen-center__preview-actions strong { font-size: 13px; }
.screen-center__preview-actions span { color: var(--color-text-muted); font-size: 11px; }
.screen-center__preview-actions button:hover span { color: inherit; }
.screen-center__grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 16px; padding-top: 20px; }
.screen-card { display: flex; min-width: 0; min-height: 190px; padding: 20px; gap: 16px; background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius-control); box-shadow: var(--shadow-surface); }
.screen-card__icon { display: grid; flex: 0 0 50px; place-items: center; width: 50px; height: 50px; color: var(--color-brand-700); background: var(--color-brand-100); border-radius: 14px; }
.screen-card__icon svg { width: 34px; height: 34px; fill: none; stroke: currentColor; stroke-width: 1.5; stroke-linecap: round; stroke-linejoin: round; }
.screen-card__body { display: flex; flex: 1 1 auto; min-width: 0; flex-direction: column; }
.screen-card__title-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; }
.screen-card h2 { margin-bottom: 7px; overflow: hidden; color: var(--color-text); font-size: 17px; line-height: 1.4; text-overflow: ellipsis; white-space: nowrap; }
.screen-card__mode-badge { flex: 0 0 auto; padding: 4px 7px; color: var(--color-warning-700, #8a5a00); background: var(--color-warning-100, #fff5d6); border-radius: 10px; font-size: 11px; white-space: nowrap; }
.screen-card__code { overflow: hidden; margin-bottom: 12px; color: var(--color-text-muted); font-family: var(--font-mono, ui-monospace, monospace); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.screen-card__description { margin-bottom: 12px; color: var(--color-text-muted); font-size: 13px; line-height: 1.6; }
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
@media (max-width: 720px) { .screen-center__header, .screen-center__toolbar, .screen-center__preview-tools { align-items: stretch; flex-direction: column; } .screen-center__count { align-self: flex-start; } .screen-center__search { flex-basis: auto; } .screen-center__preview-actions { width: 100%; } .screen-center__preview-actions button { flex: 1 1 0; min-width: 0; } .screen-center__grid { grid-template-columns: 1fr; } }
</style>
