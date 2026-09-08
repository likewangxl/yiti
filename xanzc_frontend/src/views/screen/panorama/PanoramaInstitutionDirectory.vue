<template>
  <div
    class="panorama-institution-directory-modal"
    data-testid="institution-directory-modal"
    @click.self="close"
  >
    <section
      ref="dialogRef"
      class="panorama-institution-directory"
      data-testid="institution-directory-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="institution-directory-title"
      tabindex="-1"
      @keydown="onDialogKeydown"
    >
      <header class="institution-directory__header">
        <div>
          <span class="institution-directory__kicker">{{ isDemoModel ? '演示机构' : '授权机构' }}</span>
          <h2 id="institution-directory-title">机构目录</h2>
          <p>{{ isDemoModel ? '本地演示，非真实机构和业务数据' : '仅展示当前大屏服务端已授权的机构' }}</p>
        </div>
        <div class="institution-directory__header-meta">
          <span>共 {{ institutions.length }} 家</span>
          <button
            type="button"
            class="institution-directory__close"
            data-action="directory-close"
            aria-label="关闭机构目录"
            @click="close"
          >×</button>
        </div>
      </header>

      <div class="institution-directory__toolbar">
        <label class="institution-directory__search" for="institution-directory-search">
          <span>搜索机构</span>
          <input
            id="institution-directory-search"
            v-model="search"
            data-testid="institution-directory-search"
            type="search"
            autocomplete="off"
            placeholder="输入机构编码或名称"
          >
        </label>
        <div class="institution-directory__filters" role="tablist" aria-label="机构目录筛选">
          <button
            v-for="filter in filters"
            :key="filter.key"
            type="button"
            role="tab"
            :aria-selected="String(activeFilter === filter.key)"
            :class="{ active: activeFilter === filter.key }"
            :data-directory-filter="filter.key"
            :data-testid="`institution-directory-filter-${filter.key}`"
            @click="activeFilter = filter.key"
          >{{ filter.label }} <small>{{ filterCount(filter.key) }}</small></button>
        </div>
      </div>

      <div class="institution-directory__content">
        <div class="institution-directory__table-wrap">
          <table class="institution-directory__table">
            <caption class="institution-directory__visually-hidden">授权机构目录</caption>
            <thead>
              <tr>
                <th scope="col">机构</th>
                <th scope="col">城市</th>
                <th scope="col">定位状态</th>
                <th scope="col">已有指标</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(institution, index) in filteredInstitutions"
                :key="`${institution.orgCode || 'institution'}-${index}`"
                data-testid="institution-directory-row"
                :data-org-code="institution.orgCode"
                :aria-selected="String(isSelected(institution))"
                tabindex="0"
                @click="selectInstitution(institution)"
                @keydown.enter="selectInstitution(institution)"
                @keydown.space.prevent="selectInstitution(institution)"
              >
                <td class="institution-directory__identity">
                  <strong>{{ institution.orgName || '未命名机构' }}</strong>
                  <small>{{ institution.orgCode || '—' }}</small>
                </td>
                <td>
                  <span>{{ cityLabel(institution) }}</span>
                  <small v-if="!hasKnownCity(institution)" class="institution-directory__warning">城市待维护</small>
                </td>
                <td>
                  <span :class="['institution-directory__status', isLocated(institution) ? 'is-located' : 'is-unlocated']">
                    {{ isLocated(institution) ? '已定位' : '待定位' }}
                  </span>
                </td>
                <td class="institution-directory__metrics">
                  <span v-if="metricSummary(institution)">{{ metricSummary(institution) }}</span>
                  <span v-else class="institution-directory__muted">暂无指标</span>
                </td>
              </tr>
            </tbody>
          </table>
          <div v-if="!filteredInstitutions.length" class="institution-directory__empty">暂无匹配机构</div>
        </div>

        <aside
          v-if="selectedInstitution"
          class="institution-directory__detail"
          data-testid="institution-directory-detail"
          aria-live="polite"
        >
          <div class="institution-directory__detail-heading">
            <div>
              <span class="institution-directory__kicker">机构详情</span>
              <h3>{{ selectedInstitution.orgName || selectedInstitution.orgCode || '机构' }}</h3>
              <small>{{ selectedInstitution.orgCode || '—' }}</small>
            </div>
            <span class="institution-directory__detail-state">
              {{ isLocated(selectedInstitution) ? '已定位' : '待定位' }}
            </span>
          </div>

          <dl class="institution-directory__facts">
            <div>
              <dt>城市</dt>
              <dd>
                {{ cityLabel(selectedInstitution) }}
                <small v-if="!hasKnownCity(selectedInstitution)" class="institution-directory__warning">城市待维护</small>
              </dd>
            </div>
            <div>
              <dt>坐标</dt>
              <dd>{{ coordinateLabel(selectedInstitution) }}</dd>
            </div>
            <div>
              <dt>位置来源</dt>
              <dd data-testid="institution-directory-location-source">{{ locationSourceLabel(selectedInstitution) }}</dd>
            </div>
          </dl>

          <section class="institution-directory__metric-section" aria-label="机构已有指标">
            <h4>已有指标</h4>
            <div v-if="detailMetrics.length" class="institution-directory__metric-grid">
              <div v-for="metric in detailMetrics" :key="metric.key" class="institution-directory__metric">
                <span>{{ metric.label }}</span>
                <strong>{{ formatMetric(metric.value) }}</strong>
                <small>{{ metric.unit }}</small>
              </div>
            </div>
            <p v-else class="institution-directory__empty-copy">暂无已维护指标</p>
          </section>

          <section class="institution-directory__trend-section" aria-label="机构趋势">
            <h4>经营趋势</h4>
            <table v-if="detailTrend.length" class="institution-directory__trend-table">
              <thead><tr><th scope="col">日期</th><th scope="col">存款余额</th><th scope="col">贷款余额</th></tr></thead>
              <tbody>
                <tr v-for="row in detailTrend" :key="rowKey(row)">
                  <td>{{ row.date || '—' }}</td>
                  <td>{{ formatMetric(row.deposit) }}</td>
                  <td>{{ formatMetric(row.loan) }}</td>
                </tr>
              </tbody>
            </table>
            <p v-else class="institution-directory__empty-copy">暂无趋势数据</p>
          </section>

          <section v-if="detailAttention.length" class="institution-directory__attention-section" aria-label="机构经营关注">
            <h4>经营关注</h4>
            <ul>
              <li v-for="item in detailAttention" :key="item.label">
                <span>{{ item.label || '—' }}</span>
                <strong>{{ formatMetric(item.count) }}</strong>
              </li>
            </ul>
          </section>
        </aside>
        <div v-else class="institution-directory__detail institution-directory__detail--empty">
          <span class="institution-directory__kicker">机构详情</span>
          <p>点击机构查看已有指标与趋势</p>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { cityGeoByCode, cityOptions } from './geography.js';

const props = defineProps({
  model: { type: Object, default: () => ({}) }
});
const emit = defineEmits(['close', 'branch-select']);

const dialogRef = ref(null);
const search = ref('');
const activeFilter = ref('all');
const selectedOrgCode = ref('');
const focusBeforeOpen = ref(null);
const overflowBeforeOpen = ref('');

const filters = Object.freeze([
  { key: 'all', label: '全部' },
  { key: 'unlocated', label: '待定位' },
  { key: 'unknown-city', label: '城市待维护' }
]);

const metricDefinitions = Object.freeze([
  { key: 'deposit', label: '存款余额', unit: '亿元' },
  { key: 'loan', label: '贷款余额', unit: '亿元' },
  { key: 'customers', label: '客户总量', unit: '万户' },
  { key: 'revenue', label: '营收', unit: '亿元' },
  { key: 'target', label: '目标值', unit: '%' },
  { key: 'rate', label: '目标完成率', unit: '%' }
]);

const locationSourceLabels = Object.freeze({
  PROFILE: '机构画像',
  MANUAL: '人工核定',
  GEOCODE_VERIFIED: '地址解析核定'
});

const cityCodeNames = new Map(cityOptions
  .filter(item => item?.code)
  .map(item => [String(item.code), String(item.name || item.code)]));
Object.keys(cityGeoByCode || {}).forEach(code => {
  if (!cityCodeNames.has(String(code))) cityCodeNames.set(String(code), String(code));
});
const realCityCodes = new Set(cityCodeNames.keys());

const institutions = computed(() => {
  const source = props.model && typeof props.model === 'object' ? props.model.institutions : [];
  return Array.isArray(source) ? source.filter(item => item && typeof item === 'object') : [];
});
const isDemoModel = computed(() => props.model?.demo === true || props.model?.demoOnly === true);
const normalizedSearch = computed(() => String(search.value || '').trim().toLocaleLowerCase());
const selectedInstitution = computed(() => institutions.value.find(item => String(item.orgCode || '') === selectedOrgCode.value) || null);
const filteredInstitutions = computed(() => institutions.value.filter(institution => {
  const query = normalizedSearch.value;
  if (query && ![institution.orgCode, institution.orgName]
    .some(value => String(value || '').toLocaleLowerCase().includes(query))) return false;
  if (activeFilter.value === 'unlocated') return !isLocated(institution);
  if (activeFilter.value === 'unknown-city') return !hasKnownCity(institution);
  return true;
}));
const detailMetrics = computed(() => metricDefinitions.filter(metric => {
  const metrics = selectedInstitution.value?.metrics;
  return metrics && typeof metrics === 'object' && Object.prototype.hasOwnProperty.call(metrics, metric.key);
}).map(metric => ({ ...metric, value: selectedInstitution.value.metrics[metric.key] })));
const detailTrend = computed(() => Array.isArray(selectedInstitution.value?.trend) ? selectedInstitution.value.trend : []);
const detailAttention = computed(() => Array.isArray(selectedInstitution.value?.attention) ? selectedInstitution.value.attention : []);

function normalizedCityCode(institution) {
  return String(institution?.cityCode || '').trim();
}

function hasKnownCity(institution) {
  const code = normalizedCityCode(institution);
  return Boolean(code && realCityCodes.has(code));
}

function cityLabel(institution) {
  const code = normalizedCityCode(institution);
  if (!code || !realCityCodes.has(code)) return '未知城市';
  // cityName is preferred when supplied by the authorized model; the code map
  // is the only fallback, so the directory never infers a city from a name.
  return String(institution?.cityName || cityCodeNames.get(code) || code);
}

function finiteValue(value) {
  if (value === null || value === undefined || value === '') return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function isLocated(institution) {
  return Boolean(institution?.located)
    && finiteValue(institution?.lng) !== null
    && finiteValue(institution?.lat) !== null;
}

function isSelected(institution) {
  return String(institution?.orgCode || '') === selectedOrgCode.value;
}

function coordinateLabel(institution) {
  if (!isLocated(institution)) return '暂无坐标';
  return `${institution.lng}, ${institution.lat}${institution.coordSys ? ` · ${institution.coordSys}` : ''}`;
}

function locationSourceLabel(institution) {
  if (!isLocated(institution)) return '待定位';
  const source = String(institution?.locationSource || '').trim().toUpperCase();
  return locationSourceLabels[source] || '来源待核对';
}

function formatMetric(value) {
  const number = finiteValue(value);
  if (number === null) return '—';
  return new Intl.NumberFormat('en-US', {
    maximumFractionDigits: 2,
    minimumFractionDigits: Number.isInteger(number) ? 0 : 2
  }).format(number);
}

function metricEntries(institution) {
  const metrics = institution?.metrics;
  if (!metrics || typeof metrics !== 'object') return [];
  return metricDefinitions.filter(metric => Object.prototype.hasOwnProperty.call(metrics, metric.key))
    .map(metric => ({ ...metric, value: metrics[metric.key] }));
}

function metricSummary(institution) {
  const entries = metricEntries(institution).filter(metric => finiteValue(metric.value) !== null);
  if (!entries.length) return '';
  return entries.slice(0, 3).map(metric => `${metric.label} ${formatMetric(metric.value)}${metric.unit}`).join(' · ');
}

function filterCount(key) {
  if (key === 'all') return institutions.value.length;
  if (key === 'unlocated') return institutions.value.filter(institution => !isLocated(institution)).length;
  if (key === 'unknown-city') return institutions.value.filter(institution => !hasKnownCity(institution)).length;
  return 0;
}

function rowKey(row) {
  return `${row?.date || 'date'}-${row?.deposit ?? 'deposit'}-${row?.loan ?? 'loan'}`;
}

function selectInstitution(institution) {
  const code = String(institution?.orgCode || '').trim();
  if (!code || !institutions.value.some(item => String(item.orgCode || '') === code)) return;
  selectedOrgCode.value = code;
  emit('branch-select', code);
}

function close() {
  emit('close');
}

function focusables() {
  const container = dialogRef.value;
  return container
    ? [...container.querySelectorAll('button:not([disabled]), input:not([disabled]), [tabindex]:not([tabindex="-1"])')]
    : [];
}

function onDialogKeydown(event) {
  if (['Escape', 'Esc', 'esc'].includes(event.key)) {
    event.preventDefault();
    close();
    return;
  }
  if (event.key !== 'Tab') return;
  const targets = focusables();
  if (!targets.length) {
    event.preventDefault();
    dialogRef.value?.focus();
    return;
  }
  const first = targets[0];
  const last = targets[targets.length - 1];
  if (event.shiftKey && (document.activeElement === first || document.activeElement === dialogRef.value)) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}

function onDocumentKeydown(event) {
  if (event.defaultPrevented || !['Escape', 'Esc', 'esc'].includes(event.key)) return;
  event.preventDefault();
  close();
}

watch(institutions, list => {
  if (selectedOrgCode.value && !list.some(item => String(item.orgCode || '') === selectedOrgCode.value)) {
    selectedOrgCode.value = '';
  }
});

onMounted(() => {
  focusBeforeOpen.value = document.activeElement;
  overflowBeforeOpen.value = document.body.style.overflow;
  document.body.style.overflow = 'hidden';
  document.addEventListener('keydown', onDocumentKeydown);
  dialogRef.value?.focus();
});

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onDocumentKeydown);
  document.body.style.overflow = overflowBeforeOpen.value;
  const target = focusBeforeOpen.value;
  focusBeforeOpen.value = null;
  if (target && typeof target.focus === 'function') target.focus();
});
</script>

<style scoped>
.panorama-institution-directory-modal {
  position: fixed;
  inset: 0;
  z-index: 1100;
  display: grid;
  place-items: center;
  padding: 3vh 3vw;
  color: var(--panorama-text, #eaf2ff);
  background: rgba(1, 6, 23, .8);
  backdrop-filter: blur(5px);
  font-family: "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
}

.panorama-institution-directory {
  display: flex;
  width: min(1180px, 94vw);
  height: min(780px, 92vh);
  min-height: 440px;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--panorama-border-strong, rgba(96, 214, 255, .74));
  border-radius: 10px;
  outline: none;
  background: linear-gradient(145deg, rgba(13, 38, 91, .98), rgba(4, 14, 39, .98));
  box-shadow: 0 24px 88px rgba(0, 0, 0, .52), inset 0 1px 0 rgba(192, 220, 255, .08);
}

.institution-directory__header,
.institution-directory__toolbar,
.institution-directory__content { min-width: 0; }

.institution-directory__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding: 18px 22px 15px;
  border-bottom: 1px solid rgba(121, 161, 248, .2);
}

.institution-directory__kicker {
  color: #72b9ed;
  font-size: 10px;
  letter-spacing: .16em;
  text-transform: uppercase;
}

.institution-directory__header h2,
.institution-directory__detail h3,
.institution-directory__metric-section h4,
.institution-directory__trend-section h4,
.institution-directory__attention-section h4 { margin: 0; }

.institution-directory__header h2 { margin-top: 5px; font-size: 24px; letter-spacing: .08em; }
.institution-directory__header p { margin: 6px 0 0; color: var(--panorama-text-dim, #8fa9db); font-size: 12px; }
.institution-directory__header-meta { display: flex; align-items: center; gap: 15px; color: var(--panorama-text-dim, #8fa9db); font-size: 12px; }
.institution-directory__close { width: 34px; height: 34px; border: 1px solid rgba(125, 161, 232, .24); border-radius: 6px; color: #bed0f5; background: transparent; font-size: 25px; line-height: 1; cursor: pointer; }
.institution-directory__close:hover,
.institution-directory__close:focus-visible { border-color: var(--panorama-border-strong, #60d6ff); color: var(--panorama-cyan, #4de8ef); outline: none; }

.institution-directory__toolbar { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; padding: 14px 22px 12px; }
.institution-directory__search { display: grid; min-width: min(360px, 100%); gap: 6px; color: var(--panorama-text-dim, #8fa9db); font-size: 11px; }
.institution-directory__search input { width: 100%; min-height: 34px; padding: 7px 10px; border: 1px solid rgba(121, 161, 248, .28); border-radius: 5px; color: #ecf4ff; background: rgba(3, 13, 37, .66); font: inherit; }
.institution-directory__search input:focus { border-color: var(--panorama-cyan, #4de8ef); outline: 1px solid rgba(77, 232, 239, .28); }
.institution-directory__filters { display: inline-flex; overflow: hidden; border: 1px solid rgba(121, 161, 248, .25); border-radius: 6px; }
.institution-directory__filters button { min-height: 34px; padding: 6px 12px; border: 0; border-right: 1px solid rgba(121, 161, 248, .16); color: var(--panorama-text-dim, #8fa9db); background: transparent; font: inherit; font-size: 11px; cursor: pointer; }
.institution-directory__filters button:last-child { border-right: 0; }
.institution-directory__filters button.active,
.institution-directory__filters button:hover,
.institution-directory__filters button:focus-visible { color: var(--panorama-text, #eaf2ff); background: rgba(65, 127, 220, .34); outline: none; }
.institution-directory__filters small { margin-left: 3px; color: inherit; font-size: 10px; opacity: .8; }

.institution-directory__content { display: grid; min-height: 0; flex: 1; grid-template-columns: minmax(0, 1.5fr) minmax(300px, .9fr); gap: 12px; padding: 0 22px 20px; }
.institution-directory__table-wrap { min-width: 0; min-height: 0; overflow: auto; border: 1px solid rgba(121, 161, 248, .18); border-radius: 7px; background: rgba(3, 13, 37, .38); }
.institution-directory__table { width: 100%; border-collapse: collapse; table-layout: fixed; color: var(--panorama-text-dim, #8fa9db); font-size: 12px; }
.institution-directory__table th { position: sticky; top: 0; z-index: 1; padding: 10px; border-bottom: 1px solid rgba(121, 161, 248, .22); color: #9db6e6; background: #0b2454; font-weight: 500; text-align: left; }
.institution-directory__table th:nth-child(1) { width: 31%; }
.institution-directory__table th:nth-child(2) { width: 20%; }
.institution-directory__table th:nth-child(3) { width: 17%; }
.institution-directory__table th:nth-child(4) { width: 32%; }
.institution-directory__table td { padding: 10px; border-bottom: 1px solid rgba(121, 161, 248, .12); vertical-align: top; }
.institution-directory__table tbody tr { cursor: pointer; }
.institution-directory__table tbody tr:hover,
.institution-directory__table tbody tr:focus-visible,
.institution-directory__table tbody tr[aria-selected="true"] { outline: none; background: rgba(56, 146, 224, .14); }
.institution-directory__identity strong,
.institution-directory__identity small,
.institution-directory__table td > span,
.institution-directory__warning { display: block; }
.institution-directory__identity strong { overflow: hidden; color: #dce9ff; text-overflow: ellipsis; white-space: nowrap; }
.institution-directory__identity small { margin-top: 4px; color: #7595c8; font-size: 10px; }
.institution-directory__warning { margin-top: 4px; color: var(--panorama-amber, #ffc45e); font-size: 10px; }
.institution-directory__status { font-size: 11px; }
.institution-directory__status.is-located { color: var(--panorama-up, #58e4b5); }
.institution-directory__status.is-unlocated { color: var(--panorama-amber, #ffc45e); }
.institution-directory__metrics { overflow: hidden; color: #c4d8fb; line-height: 1.55; text-overflow: ellipsis; }
.institution-directory__muted,
.institution-directory__empty,
.institution-directory__empty-copy { color: var(--panorama-text-dim, #8fa9db); }
.institution-directory__empty { padding: 32px 16px; text-align: center; font-size: 12px; }

.institution-directory__detail { min-width: 0; min-height: 0; overflow: auto; padding: 16px; border: 1px solid rgba(121, 161, 248, .2); border-radius: 7px; background: rgba(8, 24, 61, .66); }
.institution-directory__detail--empty { display: grid; place-content: center; text-align: center; }
.institution-directory__detail--empty p { margin: 8px 0 0; color: var(--panorama-text-dim, #8fa9db); font-size: 12px; }
.institution-directory__detail-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; padding-bottom: 12px; border-bottom: 1px solid rgba(121, 161, 248, .15); }
.institution-directory__detail h3 { margin-top: 6px; overflow: hidden; color: #eff5ff; font-size: 19px; text-overflow: ellipsis; white-space: nowrap; }
.institution-directory__detail-heading small { display: block; margin-top: 4px; color: #7595c8; font-size: 10px; }
.institution-directory__detail-state { color: var(--panorama-up, #58e4b5); font-size: 11px; white-space: nowrap; }
.institution-directory__facts { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 7px; margin: 12px 0 16px; }
.institution-directory__facts div { min-width: 0; padding: 8px; border: 1px solid rgba(121, 161, 248, .12); border-radius: 5px; background: rgba(3, 13, 37, .3); }
.institution-directory__facts dt { color: #7595c8; font-size: 10px; }
.institution-directory__facts dd { margin: 5px 0 0; overflow: hidden; color: #dce9ff; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.institution-directory__facts dd .institution-directory__warning { display: inline; margin-left: 4px; }
.institution-directory__metric-section h4,
.institution-directory__trend-section h4,
.institution-directory__attention-section h4 { color: #b9ccf0; font-size: 12px; font-weight: 600; }
.institution-directory__metric-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 7px; margin-top: 8px; }
.institution-directory__metric { min-width: 0; padding: 8px; border: 1px solid rgba(121, 161, 248, .12); border-radius: 5px; }
.institution-directory__metric span,
.institution-directory__metric small { display: block; color: #7595c8; font-size: 10px; }
.institution-directory__metric strong { display: inline-block; margin-top: 5px; color: #eff5ff; font-size: 16px; }
.institution-directory__metric small { display: inline-block; margin-left: 3px; }
.institution-directory__empty-copy { margin: 8px 0 0; font-size: 11px; }
.institution-directory__trend-section { margin-top: 17px; }
.institution-directory__trend-table { width: 100%; margin-top: 8px; border-collapse: collapse; color: #c9daf8; font-size: 10px; }
.institution-directory__trend-table th,
.institution-directory__trend-table td { padding: 6px 4px; border-bottom: 1px solid rgba(121, 161, 248, .12); text-align: right; }
.institution-directory__trend-table th:first-child,
.institution-directory__trend-table td:first-child { text-align: left; }
.institution-directory__trend-table th { color: #7595c8; font-weight: 500; }
.institution-directory__attention-section { margin-top: 17px; }
.institution-directory__attention-section ul { margin: 8px 0 0; padding: 0; list-style: none; }
.institution-directory__attention-section li { display: flex; justify-content: space-between; gap: 8px; padding: 6px 0; border-bottom: 1px solid rgba(121, 161, 248, .12); color: #c9daf8; font-size: 11px; }
.institution-directory__attention-section strong { color: var(--panorama-amber, #ffc45e); }
.institution-directory__visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }

@media (max-width: 800px) {
  .panorama-institution-directory-modal { padding: 0; }
  .panorama-institution-directory { width: 100vw; height: 100vh; min-height: 0; border-radius: 0; }
  .institution-directory__header,
  .institution-directory__toolbar,
  .institution-directory__content { padding-right: 14px; padding-left: 14px; }
  .institution-directory__toolbar { align-items: stretch; flex-direction: column; }
  .institution-directory__search { min-width: 0; }
  .institution-directory__filters button { flex: 1; }
  .institution-directory__filters { display: flex; }
  .institution-directory__content { grid-template-columns: 1fr; grid-template-rows: minmax(220px, .9fr) minmax(280px, 1fr); overflow: auto; }
  .institution-directory__detail { overflow: visible; }
}
</style>
