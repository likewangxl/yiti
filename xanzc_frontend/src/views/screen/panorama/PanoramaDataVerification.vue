<template>
  <section class="panorama-data-verification" data-testid="panorama-data-verification" aria-labelledby="panorama-data-verification-title">
    <div class="panorama-data-verification__heading">
      <div>
        <span class="panorama-data-verification__eyebrow">已保存草稿</span>
        <h2 id="panorama-data-verification-title">验证已保存草稿数据</h2>
      </div>
      <button
        type="button"
        data-testid="verify-saved-draft-data"
        :disabled="disabled || loading"
        @click="verify"
      >
        {{ loading ? '验证中…' : '验证已保存草稿数据' }}
      </button>
    </div>

    <div class="panorama-data-verification__notice" role="note">
      <strong>结构核验边界</strong>
      <span>{{ disclaimer }}</span>
      <span>验证只读取服务端已保存草稿，不自动保存、发布或修改业务数据。</span>
    </div>

    <div
      class="panorama-data-verification__status"
      data-testid="panorama-verification-status"
      :data-status="result.overallStatus"
      :class="`is-${String(result.overallStatus || '').toLowerCase()}`"
      role="status"
    >
      <span class="panorama-data-verification__status-dot" aria-hidden="true"></span>
      <strong>{{ result.overallStatusLabel || '尚未验证' }}</strong>
      <span v-if="result.message" class="panorama-data-verification__status-message">{{ result.message }}</span>
    </div>

    <div v-if="result.changedSlots?.length" class="panorama-data-verification__alert" role="alert">
      当前编辑绑定尚未保存，请先保存后再验证。不会自动写入。
      <span>未保存展示项：{{ changedSlotLabels }}</span>
    </div>
    <div v-if="result.applicable === false" class="panorama-data-verification__alert" role="alert">
      当前草稿不是代码化大屏，本项核验不适用。
    </div>

    <div v-if="rows.length" class="panorama-data-verification__table-wrap">
      <table class="panorama-data-verification__table" data-testid="panorama-verification-table">
        <caption class="panorama-data-verification__visually-hidden">代码化大屏已保存草稿数据结构核验结果</caption>
        <thead>
          <tr>
            <th scope="col">展示项</th>
            <th scope="col">绑定</th>
            <th scope="col">返回行数</th>
            <th scope="col">覆盖 / 缺失</th>
            <th scope="col">核验状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.slot" :data-slot="row.slot">
            <th scope="row">
              <span>{{ row.label || row.slot }}</span>
              <small v-if="row.sampleOnly" class="panorama-data-verification__sample">仅核验一个授权样本</small>
            </th>
            <td>
              <div class="panorama-data-verification__binding">{{ formatBinding(row) }}</div>
            </td>
            <td>
              <span>{{ rowCountLabel(row) }}</span>
              <small v-if="row.maxRows && row.coverage?.truncatedPossible" class="panorama-data-verification__warning">可能截断</small>
            </td>
            <td>
              <span>{{ coverageLabel(row) }}</span>
              <small v-if="row.dates?.length" class="panorama-data-verification__muted">最新日期 {{ row.dates[row.dates.length - 1] }}</small>
              <small v-else class="panorama-data-verification__warning">日期无法核验</small>
            </td>
            <td>
              <span class="panorama-data-verification__badge" :class="statusClass(row.status)">{{ row.statusLabel || '尚未验证' }}</span>
              <small v-if="row.zeroCount" class="panorama-data-verification__muted">零值 {{ row.zeroCount }} 个（不等同缺失）</small>
              <details v-if="row.issues?.length" class="panorama-data-verification__issues">
                <summary>查看 {{ row.issues.length }} 项提示</summary>
                <ul>
                  <li v-for="(item, index) in row.issues" :key="`${item.code}-${index}`">{{ item.message }}</li>
                </ul>
              </details>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-else class="panorama-data-verification__empty" data-testid="panorama-verification-empty">
      点击“验证已保存草稿数据”后，这里显示每个已配置展示项的返回结构、日期和授权机构覆盖情况。
    </p>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { ALL_SLOT_ORDER, BINDING_SLOTS } from './bindings';
import {
  createRuntimeDataVerificationService,
  OVERALL_STATUS,
  VERIFICATION_DISCLAIMER,
  statusLabel
} from './runtimeDataVerification';

const props = defineProps({
  screen: { type: Object, default: null },
  canvas: { type: Object, default: null },
  datasources: { type: Array, default: () => [] },
  slotOrder: { type: Array, default: () => [...ALL_SLOT_ORDER] },
  bindingState: { type: Object, default: () => ({}) },
  disabled: { type: Boolean, default: false },
  // 注入服务只用于测试隔离或宿主复用实例；默认实例仍只读现有 screen API。
  service: { type: Object, default: null }
});

const emit = defineEmits(['verified', 'status-change']);
const localService = createRuntimeDataVerificationService();
const loading = ref(false);
const componentGeneration = ref(0);
const result = ref({
  overallStatus: OVERALL_STATUS.UNVERIFIED,
  overallStatusLabel: statusLabel(OVERALL_STATUS.UNVERIFIED),
  disclaimer: VERIFICATION_DISCLAIMER,
  businessMeaningVerified: false,
  results: []
});

const activeService = computed(() => props.service || localService);
const rows = computed(() => Array.isArray(result.value.results) ? result.value.results : []);
const disclaimer = computed(() => result.value.disclaimer || VERIFICATION_DISCLAIMER);
const changedSlotLabels = computed(() => (result.value.changedSlots || [])
  .map(slot => BINDING_SLOTS[slot]?.label || slot).join('、'));

function snapshotInput() {
  return {
    screen: props.screen,
    canvas: props.canvas,
    datasources: props.datasources,
    slotOrder: props.slotOrder,
    bindingState: props.bindingState
  };
}

function reset() {
  componentGeneration.value += 1;
  activeService.value.invalidate?.();
  result.value = {
    overallStatus: OVERALL_STATUS.UNVERIFIED,
    overallStatusLabel: statusLabel(OVERALL_STATUS.UNVERIFIED),
    disclaimer: VERIFICATION_DISCLAIMER,
    businessMeaningVerified: false,
    results: []
  };
  emit('status-change', result.value);
}

async function verify() {
  if (props.disabled || loading.value) return false;
  const token = ++componentGeneration.value;
  loading.value = true;
  result.value = {
    ...result.value,
    overallStatus: OVERALL_STATUS.IN_PROGRESS,
    overallStatusLabel: statusLabel(OVERALL_STATUS.IN_PROGRESS),
    message: '',
    businessMeaningVerified: false
  };
  try {
    const next = await activeService.value.verify(snapshotInput());
    if (token !== componentGeneration.value) return false;
    result.value = next || {
      overallStatus: OVERALL_STATUS.CONFIG_ERROR,
      overallStatusLabel: statusLabel(OVERALL_STATUS.CONFIG_ERROR),
      disclaimer: VERIFICATION_DISCLAIMER,
      businessMeaningVerified: false,
      results: [],
      message: '验证服务未返回结果'
    };
    emit('verified', result.value);
    emit('status-change', result.value);
    return result.value;
  } catch (error) {
    if (token !== componentGeneration.value) return false;
    result.value = {
      overallStatus: OVERALL_STATUS.CONFIG_ERROR,
      overallStatusLabel: statusLabel(OVERALL_STATUS.CONFIG_ERROR),
      disclaimer: VERIFICATION_DISCLAIMER,
      businessMeaningVerified: false,
      results: [],
      message: error?.message || '验证服务失败'
    };
    emit('status-change', result.value);
    return result.value;
  } finally {
    loading.value = false;
  }
}

function rowCountLabel(row) {
  return row.rowCount === null || row.rowCount === undefined ? '—' : `${row.rowCount} 行`;
}

function coverageLabel(row) {
  const coverage = row.coverage || {};
  if (coverage.label) {
    if (Array.isArray(coverage.missing) && coverage.missing.length) {
      return `${coverage.label}，缺失 ${coverage.missing.join('、')}`;
    }
    return coverage.label;
  }
  return row.rowCount === null || row.rowCount === undefined ? '未核验' : `返回 ${row.rowCount} 行`;
}

function formatBinding(row) {
  const binding = row.binding;
  if (!binding) return '未配置';
  const source = binding.datasourceName || (binding.datasourceId ? `数据源 ${binding.datasourceId}` : '数据源未明');
  const fields = Object.entries(binding.fields || {}).map(([semantic, column]) => `${semantic}=${column}`).join('，');
  const units = Object.entries(binding.units || {}).map(([semantic, unit]) => `${semantic}:${unit}`).join('，');
  return [source, fields || '字段未配', units || '单位未配'].filter(Boolean).join(' · ');
}

function statusClass(status) {
  return `is-${String(status || 'UNVERIFIED').toLowerCase()}`;
}

watch(
  () => [props.screen, props.canvas, props.bindingState],
  () => {
    // 切屏、保存新版本或编辑绑定后，旧核验结果必须回到中性未验证态。
    if (!loading.value) reset();
    else {
      componentGeneration.value += 1;
      activeService.value.invalidate?.();
      result.value = {
        overallStatus: OVERALL_STATUS.UNVERIFIED,
        overallStatusLabel: statusLabel(OVERALL_STATUS.UNVERIFIED),
        disclaimer: VERIFICATION_DISCLAIMER,
        businessMeaningVerified: false,
        results: []
      };
      emit('status-change', result.value);
    }
  },
  { deep: true }
);

onBeforeUnmount(() => {
  componentGeneration.value += 1;
  activeService.value.dispose?.();
});

defineExpose({ verify, reset, result, loading });
</script>

<style scoped>
.panorama-data-verification {
  color: #dbe8ff;
  background: rgba(8, 20, 52, .94);
  border: 1px solid rgba(112, 153, 223, .28);
  border-radius: 10px;
  padding: 18px;
}

.panorama-data-verification__heading,
.panorama-data-verification__status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.panorama-data-verification__eyebrow {
  color: #71d8ff;
  font-size: 11px;
  letter-spacing: .12em;
}

.panorama-data-verification h2 {
  margin: 4px 0 0;
  color: #f1f6ff;
  font-size: 18px;
}

.panorama-data-verification button {
  border: 1px solid rgba(109, 217, 255, .72);
  border-radius: 6px;
  padding: 8px 13px;
  color: #eaffff;
  background: rgba(24, 125, 164, .34);
  cursor: pointer;
}

.panorama-data-verification button:disabled {
  cursor: not-allowed;
  opacity: .55;
}

.panorama-data-verification__notice,
.panorama-data-verification__alert {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  margin-top: 14px;
  padding: 10px 12px;
  color: #9db4d9;
  background: rgba(31, 58, 102, .28);
  border-left: 3px solid #71d8ff;
  font-size: 12px;
  line-height: 1.55;
}

.panorama-data-verification__alert {
  color: #ffe1a8;
  background: rgba(116, 77, 27, .2);
  border-left-color: #f4c46f;
}

.panorama-data-verification__status {
  justify-content: flex-start;
  margin-top: 14px;
  min-height: 28px;
  color: #a9bddc;
  font-size: 13px;
}

.panorama-data-verification__status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #91a8cb;
}

.is-structure_checked .panorama-data-verification__status-dot { background: #4fd3ab; }
.is-has_gaps .panorama-data-verification__status-dot,
.is-config_error .panorama-data-verification__status-dot { background: #f4c46f; }
.is-permission_denied .panorama-data-verification__status-dot,
.is-stale .panorama-data-verification__status-dot { background: #fb8b89; }

.panorama-data-verification__status-message {
  color: #839bc1;
  font-size: 12px;
}

.panorama-data-verification__table-wrap {
  margin-top: 14px;
  overflow-x: auto;
}

.panorama-data-verification__table {
  width: 100%;
  min-width: 760px;
  border-collapse: collapse;
  font-size: 12px;
}

.panorama-data-verification__table th,
.panorama-data-verification__table td {
  padding: 10px 9px;
  border-bottom: 1px solid rgba(112, 153, 223, .18);
  text-align: left;
  vertical-align: top;
}

.panorama-data-verification__table thead th {
  color: #8ca6d1;
  font-weight: 500;
  white-space: nowrap;
}

.panorama-data-verification__table tbody th {
  color: #edf5ff;
  font-weight: 600;
  white-space: nowrap;
}

.panorama-data-verification__binding {
  max-width: 310px;
  color: #c8d8f1;
  line-height: 1.55;
}

.panorama-data-verification__muted,
.panorama-data-verification__sample,
.panorama-data-verification__warning {
  display: block;
  margin-top: 3px;
  color: #8199bd;
  font-size: 11px;
  font-weight: 400;
}

.panorama-data-verification__sample { color: #f4c46f; }
.panorama-data-verification__warning { color: #f4c46f; }

.panorama-data-verification__badge {
  display: inline-block;
  padding: 3px 7px;
  border-radius: 4px;
  color: #b9c7dc;
  background: rgba(126, 150, 185, .18);
  white-space: nowrap;
}

.panorama-data-verification__badge.is-structure_verified { color: #9bf0d0; background: rgba(52, 174, 135, .2); }
.panorama-data-verification__badge.is-verified_with_warnings,
.panorama-data-verification__badge.is-not_verifiable,
.panorama-data-verification__badge.is-no_rows { color: #ffe1a8; background: rgba(172, 120, 39, .2); }
.panorama-data-verification__badge.is-structure_error,
.panorama-data-verification__badge.is-config_error,
.panorama-data-verification__badge.is-request_error { color: #ffb9b7; background: rgba(171, 73, 73, .22); }

.panorama-data-verification__issues {
  margin-top: 7px;
  color: #9db4d9;
  font-size: 11px;
}

.panorama-data-verification__issues summary { cursor: pointer; }
.panorama-data-verification__issues ul { margin: 5px 0 0; padding-left: 16px; }

.panorama-data-verification__empty {
  margin: 16px 0 0;
  color: #8299be;
  font-size: 12px;
}

.panorama-data-verification__visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}
</style>
