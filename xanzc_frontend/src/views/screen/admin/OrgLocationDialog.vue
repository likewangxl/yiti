<template>
  <el-dialog
    :model-value="modelValue"
    class="bp-crud-dialog org-location-dialog"
    title="地址与定位"
    width="720px"
    @update:model-value="onVisibleChange"
  >
    <div class="readonly-tip">
      机构名称、编码和城市来自当前有效机构画像；地址或城市变更将使旧坐标失效，需重新确认。地址解析只在点击预览后发生，候选确认不会自动改写原始地址。
    </div>

    <div v-if="loading" class="loading-state" role="status">正在读取地址与定位能力…</div>

    <div v-if="capabilityError" class="error-state" data-testid="location-capability-error" role="alert">
      {{ capabilityError }}
    </div>
    <div v-if="capabilities && !storageReady" class="error-state" data-testid="location-storage-unavailable" role="alert">
      位置存储不可用：{{ capabilities.storageReason || '位置存储当前不可用，请联系管理员。' }}
    </div>
    <div v-if="capabilities && !capabilities.geocodingAvailable" class="error-state" data-testid="location-geocoding-unavailable" role="alert">
      地址解析不可用：{{ geocodingReason }}
    </div>

    <template v-if="storageReady">
      <div v-if="recordError" class="error-state" data-testid="location-record-error" role="alert">
        {{ recordError }}
      </div>

      <template v-else-if="recordReady">
        <div v-if="saveError || conflictError" class="error-state" data-testid="location-save-error" role="alert">
          {{ saveError || conflictError }}
          <span v-if="conflictVersion !== null" class="hint">服务端最新版本：{{ conflictVersion }}；当前表单版本未自动覆盖。</span>
        </div>
        <el-form label-width="112px" size="small" class="location-form">
          <el-form-item label="机构">
            <span data-testid="location-org">{{ orgName || '-' }}（{{ orgCode || '-' }}）</span>
          </el-form-item>

          <el-form-item label="详细地址" required>
            <el-input
              v-model="form.address"
              maxlength="255"
              show-word-limit
              placeholder="请输入机构详细地址"
              data-testid="location-address"
            />
            <span class="hint">当前响应未提供地址时可补录；地址最长 255 字。</span>
          </el-form-item>

          <el-form-item label="画像城市">
            <el-input :model-value="form.cityCode || '未维护'" readonly data-testid="location-city" />
            <span v-if="cityCodeValid" class="hint">只读来源：当前机构有效画像的 6 位 cityCode。</span>
            <span v-else class="warning-hint" data-testid="location-city-warning">请先维护机构画像中的有效6位城市编码，才能确认地址或坐标。</span>
          </el-form-item>

          <el-form-item label="地址来源">
            <span data-testid="location-address-source">{{ displayValue(record?.addressSource) }}</span>
          </el-form-item>
          <el-form-item label="定位状态">
            <span data-testid="location-status">{{ displayValue(record?.status) }}</span>
          </el-form-item>
          <el-form-item label="定位来源">
            <span data-testid="location-source">{{ displayValue(record?.locationSource) }}</span>
          </el-form-item>
          <el-form-item label="来源/精度">
            <span data-testid="location-provider">{{ displayValue(record?.provider) }}</span>
            <span class="metadata-separator">/</span>
            <span data-testid="location-match-level">{{ displayValue(record?.matchLevel) }}</span>
          </el-form-item>

          <el-form-item label="经度">
            <input
              v-model="form.lng"
              class="plain-control"
              type="number"
              inputmode="decimal"
              min="-180"
              max="180"
              step="0.000001"
              placeholder="-180 至 180"
              data-testid="manual-lng"
              @input="onCoordinateInput"
            />
          </el-form-item>
          <el-form-item label="纬度">
            <input
              v-model="form.lat"
              class="plain-control"
              type="number"
              inputmode="decimal"
              min="-90"
              max="90"
              step="0.000001"
              placeholder="-90 至 90"
              data-testid="manual-lat"
              @input="onCoordinateInput"
            />
          </el-form-item>
          <el-form-item label="坐标系">
            <span data-testid="location-coord-sys">GCJ02</span>
            <span class="hint">本期固定 GCJ02；人工坐标必须成对且在合法范围内。</span>
          </el-form-item>
          <el-form-item label="人工确认">
            <label class="check-control">
              <input
                v-model="form.manualConfirmed"
                type="checkbox"
                :disabled="form.selectionMode === 'CANDIDATE'"
                data-testid="manual-confirmed"
              />
              明确确认以上坐标为人工核实的 GCJ02 坐标
            </label>
            <span v-if="form.selectionMode === 'CANDIDATE'" class="hint">当前已选择地址解析候选，不能同时使用人工确认模式。</span>
          </el-form-item>

          <el-form-item v-if="existingHasCoordinates" label="清除旧坐标">
            <label class="check-control">
              <input
                v-model="form.clearLocation"
                type="checkbox"
                :disabled="form.selectionMode !== 'NONE'"
                data-testid="clear-location"
              />
              明确清除现有坐标（不修改地址时也可单独清除）
            </label>
            <span v-if="addressChanged && form.selectionMode === 'NONE'" class="warning-hint">
              地址或城市变更将使旧坐标失效，需重新确认；点击保存会清除旧定位，除非填写一组新的人工/候选坐标。
            </span>
          </el-form-item>

          <el-form-item label="变更原因" required>
            <el-input v-model="form.reason" type="textarea" maxlength="500" show-word-limit placeholder="请填写本次地址或定位变更原因" data-testid="location-reason" />
          </el-form-item>
          <el-form-item label="当前版本">
            <span data-testid="location-version">{{ form.version }}</span>
          </el-form-item>
        </el-form>

        <section class="geocode-panel" aria-label="地址解析预览">
          <div class="panel-title">地址解析候选</div>
          <p v-if="geocodingReason" class="warning-hint" data-testid="geocoding-unavailable">{{ geocodingReason }}</p>
          <p class="hint">服务端不会后台自动外联；只有点击预览后才会请求候选。</p>
          <button
            type="button"
            class="action-button primary"
            data-testid="geocode-preview"
            :disabled="!canPreview"
            @click="previewCandidates"
          >
            {{ previewing ? '解析中…' : '地址解析预览' }}
          </button>
          <div v-if="previewError" class="error-state compact" data-testid="geocode-error" role="alert">{{ previewError }}</div>
          <ul v-if="candidates.length" class="candidate-list" data-testid="candidate-list">
            <li v-for="candidate in candidates" :key="candidate.candidateToken || `${candidate.lng},${candidate.lat},${candidate.matchLevel}`" class="candidate-item">
              <div class="candidate-main">
                <strong>{{ candidate.formattedAddress || candidate.address || '未提供格式化地址' }}</strong>
                <span class="hint">{{ candidate.matchLevel || '未知精度' }} · {{ candidate.coordSys || '未知坐标系' }} · {{ candidate.lng }}, {{ candidate.lat }}</span>
              </div>
              <button
                v-if="candidate.verificationAllowed"
                type="button"
                class="action-button"
                data-testid="candidate-confirm"
                :disabled="!candidate.candidateToken || (form.selectionMode === 'CANDIDATE' && form.candidateToken === candidate.candidateToken)"
                @click="chooseCandidate(candidate)"
              >
                {{ form.selectionMode === 'CANDIDATE' && form.candidateToken === candidate.candidateToken ? '已选择确认' : '选择确认' }}
              </button>
              <button v-else type="button" class="action-button" data-testid="candidate-confirm-disabled" disabled>
                {{ candidate.verificationReason || '匹配精度不足，不能确认' }}
              </button>
            </li>
          </ul>
          <div v-if="selectedCandidate" class="selected-candidate" data-testid="selected-candidate">
            已选择候选坐标；待绑定地址仍为：{{ form.address || '未填写' }}。候选展示：{{ selectedCandidate.formattedAddress || '未提供格式化地址' }}
          </div>
        </section>
      </template>
    </template>

    <template #footer>
      <button type="button" class="action-button" data-testid="location-cancel" @click="close">取消</button>
      <button
        v-if="storageReady && recordReady"
        type="button"
        class="action-button primary"
        data-testid="location-save"
        :disabled="saving || !cityCodeValid"
        @click="save"
      >
        {{ saving ? '保存中…' : '保存' }}
      </button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import {
  getOrgLocationCapabilities,
  getOrgLocation,
  updateOrgLocation,
  previewOrgLocationGeocode
} from '@/api/orgLocation';

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  org: { type: Object, default: () => ({}) }
});
const emit = defineEmits(['update:modelValue', 'saved']);

const capabilities = ref(null);
const capabilityError = ref('');
const record = ref(null);
const recordError = ref('');
const recordReady = ref(false);
const loading = ref(false);
const saving = ref(false);
const saveError = ref('');
const conflictError = ref('');
const conflictVersion = ref(null);
const previewing = ref(false);
const previewError = ref('');
const candidates = ref([]);
const selectedCandidate = ref(null);
const form = reactive(createForm());

let alive = true;
let generation = 0;
let previewSequence = 0;
let suppressWatch = false;
let observedAddress = '';
let observedCityCode = '';
let observedLng = null;
let observedLat = null;
let baseline = { address: '', cityCode: '', lng: null, lat: null };
let existingCoordinates = false;

const orgCode = computed(() => String(props.org?.orgCode || '').trim());
const orgName = computed(() => String(props.org?.orgName || '').trim());
const storageReady = computed(() => Boolean(capabilities.value?.storageEnabled && capabilities.value?.storageAvailable));
const cityCodeValid = computed(() => /^\d{6}$/.test(String(form.cityCode || '').trim()));
const addressText = computed(() => String(form.address ?? '').trim());
const reasonText = computed(() => String(form.reason ?? '').trim());
const geocodingReason = computed(() => {
  if (!capabilities.value || capabilities.value.geocodingAvailable) return '';
  return capabilities.value.geocodingReason || (capabilities.value.geocodingEnabled ? '地址解析服务不可用' : '地址解析未启用');
});
const existingHasCoordinates = computed(() => existingCoordinates);
const addressChanged = computed(() => addressText.value !== baseline.address || String(form.cityCode || '').trim() !== baseline.cityCode);
const canPreview = computed(() => storageReady.value
  && Boolean(capabilities.value?.geocodingAvailable)
  && cityCodeValid.value
  && addressText.value.length > 0
  && addressText.value.length <= 255
  && reasonText.value.length > 0
  && reasonText.value.length <= 500
  && !previewing.value);

function createForm() {
  return {
    address: '', cityCode: '', lng: null, lat: null,
    coordSys: 'GCJ02', manualConfirmed: false,
    candidateToken: '', clearLocation: false,
    version: 0, reason: '', selectionMode: 'NONE'
  };
}

function displayValue(value) {
  return value === undefined || value === null || String(value).trim() === '' ? '未维护' : String(value);
}

function hasOwn(value, key) {
  return Object.prototype.hasOwnProperty.call(value, key);
}

function isCapabilitiesResponse(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  return ['storageEnabled', 'storageAvailable', 'geocodingEnabled', 'geocodingAvailable']
    .every(key => typeof value[key] === 'boolean');
}

function isLocationRecord(value, expectedOrgCode) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  if (!hasOwn(value, 'orgCode') || typeof value.orgCode !== 'string'
    || value.orgCode !== expectedOrgCode) return false;
  return hasOwn(value, 'version') && typeof value.version === 'number'
    && Number.isInteger(value.version) && value.version >= 0;
}

function isCurrent(requestGeneration, code = orgCode.value) {
  return alive && props.modelValue && generation === requestGeneration && code === orgCode.value;
}

function resetView() {
  capabilities.value = null;
  capabilityError.value = '';
  record.value = null;
  recordError.value = '';
  recordReady.value = false;
  loading.value = false;
  saving.value = false;
  saveError.value = '';
  conflictError.value = '';
  conflictVersion.value = null;
  previewSequence += 1;
  previewing.value = false;
  previewError.value = '';
  candidates.value = [];
  selectedCandidate.value = null;
  existingCoordinates = false;
  baseline = { address: '', cityCode: '', lng: null, lat: null };
  observedAddress = '';
  observedCityCode = '';
  observedLng = null;
  observedLat = null;
  suppressWatch = true;
  Object.assign(form, createForm());
  suppressWatch = false;
}

function normalizeCoordinate(value, minimum, maximum) {
  if (value === null || value === undefined || String(value).trim() === '') return null;
  const number = Number(value);
  return Number.isFinite(number) && number >= minimum && number <= maximum ? number : Number(value);
}

function sameCoordinate(left, right) {
  const a = left === null || left === undefined || String(left).trim() === '' ? null : Number(left);
  const b = right === null || right === undefined || String(right).trim() === '' ? null : Number(right);
  return (Number.isNaN(a) && Number.isNaN(b)) || a === b;
}

function applyRecord(nextRecord) {
  const source = nextRecord;
  const city = String(props.org?.cityCode || '').trim();
  const storedCity = String(source.cityCode || '').trim();
  const lng = normalizeCoordinate(source.lng, -180, 180);
  const lat = normalizeCoordinate(source.lat, -90, 90);
  existingCoordinates = source.lng !== null && source.lng !== undefined
    || source.lat !== null && source.lat !== undefined;
  baseline = {
    address: String(source.address || '').trim(),
    cityCode: storedCity,
    lng,
    lat
  };
  suppressWatch = true;
  Object.assign(form, {
    address: source.address || '',
    cityCode: city,
    lng,
    lat,
    coordSys: 'GCJ02',
    manualConfirmed: false,
    candidateToken: '',
    clearLocation: false,
    version: source.version,
    reason: '',
    selectionMode: 'NONE'
  });
  observedAddress = addressText.value;
  observedCityCode = city;
  observedLng = lng;
  observedLat = lat;
  suppressWatch = false;
}

function clearCandidate() {
  clearCandidateWithOptions();
}

function clearCandidateWithOptions({ resetCoordinates = true } = {}) {
  previewSequence += 1;
  previewing.value = false;
  form.candidateToken = '';
  selectedCandidate.value = null;
  candidates.value = [];
  if (form.selectionMode === 'CANDIDATE') {
    form.selectionMode = 'NONE';
    if (resetCoordinates) {
      const wasSuppressed = suppressWatch;
      suppressWatch = true;
      form.lng = null;
      form.lat = null;
      observedLng = null;
      observedLat = null;
      suppressWatch = wasSuppressed;
    }
    form.coordSys = 'GCJ02';
    form.manualConfirmed = false;
  }
}

function onCoordinateInput() {
  // Native input is intentionally paired with the watcher so direct test/programmatic edits
  // and real user edits share the same candidate invalidation semantics.
  if (suppressWatch) return;
  clearCandidateWithOptions({ resetCoordinates: false });
  form.selectionMode = 'MANUAL';
  form.manualConfirmed = false;
}

watch(() => [form.address, form.cityCode], ([address, city]) => {
  if (suppressWatch) {
    observedAddress = String(address || '').trim();
    observedCityCode = String(city || '').trim();
    return;
  }
  const nextAddress = String(address || '').trim();
  const nextCity = String(city || '').trim();
  if (nextAddress !== observedAddress || nextCity !== observedCityCode) clearCandidate();
  observedAddress = nextAddress;
  observedCityCode = nextCity;
});

watch(() => [form.lng, form.lat], ([lng, lat]) => {
  if (suppressWatch) {
    observedLng = lng;
    observedLat = lat;
    return;
  }
  if (!sameCoordinate(lng, observedLng) || !sameCoordinate(lat, observedLat)) {
    clearCandidateWithOptions({ resetCoordinates: false });
    form.selectionMode = 'MANUAL';
    form.manualConfirmed = false;
  }
  observedLng = lng;
  observedLat = lat;
});

async function loadForOrg() {
  const code = orgCode.value;
  const requestGeneration = ++generation;
  resetView();
  if (!code || !props.modelValue) return;
  loading.value = true;
  try {
    const nextCapabilities = await getOrgLocationCapabilities();
    if (!isCurrent(requestGeneration, code)) return;
    if (!isCapabilitiesResponse(nextCapabilities)) throw new Error('能力返回数据格式无效。');
    capabilities.value = nextCapabilities;
    if (!storageReady.value) return;

    try {
      const nextRecord = await getOrgLocation(code);
      if (!isCurrent(requestGeneration, code)) return;
      if (!isLocationRecord(nextRecord, code)) throw new Error('记录返回数据格式无效。');
      record.value = nextRecord;
      applyRecord(record.value);
      recordReady.value = true;
    } catch (error) {
      if (!isCurrent(requestGeneration, code)) return;
      recordError.value = formatError('地址与定位记录读取失败', error);
    }
  } catch (error) {
    if (!isCurrent(requestGeneration, code)) return;
    capabilityError.value = formatError('地址与定位能力读取失败', error);
  } finally {
    if (isCurrent(requestGeneration, code)) loading.value = false;
  }
}

function formatError(prefix, error) {
  const status = error?.response?.status ?? error?.status;
  if (status === 403) return `${prefix}：没有权限（403），请联系管理员。`;
  const message = String(error?.message || '').trim();
  return `${prefix}：${message || '服务暂不可用，请稍后重试。'}`;
}

function isConflict(error) {
  const status = error?.response?.status ?? error?.status;
  const code = String(error?.code || error?.response?.data?.code || '').toUpperCase();
  return status === 409 || code.includes('CONFLICT') || code.includes('VERSION');
}

function close() {
  generation += 1;
  resetView();
  emit('update:modelValue', false);
}

function onVisibleChange(value) {
  if (!value) {
    generation += 1;
    resetView();
  }
  emit('update:modelValue', value);
}

function validateSave() {
  if (!storageReady.value || !recordReady.value) return '地址与定位存储当前不可用，不能保存。';
  if (!cityCodeValid.value) return '请先维护机构画像中的有效6位城市编码。';
  if (addressText.value.length > 255) return '详细地址长度不能超过255字。';
  if (reasonText.value.length === 0) return '变更原因必填。';
  if (reasonText.value.length > 500) return '变更原因长度不能超过500字。';

  const candidateMode = form.selectionMode === 'CANDIDATE' && Boolean(form.candidateToken);
  const coordsChanged = !sameCoordinate(form.lng, baseline.lng) || !sameCoordinate(form.lat, baseline.lat);
  const manualMode = !candidateMode && (form.selectionMode === 'MANUAL' || coordsChanged);
  if (candidateMode && form.manualConfirmed) return '地址解析候选与人工坐标确认不能同时使用。';
  if (manualMode) {
    if (String(form.coordSys || '').toUpperCase() !== 'GCJ02') return '人工坐标坐标系必须固定为 GCJ02。';
    const lng = normalizeCoordinate(form.lng, -180, 180);
    const lat = normalizeCoordinate(form.lat, -90, 90);
    if (lng === null || lat === null || !Number.isFinite(lng) || !Number.isFinite(lat)
      || lng < -180 || lng > 180 || lat < -90 || lat > 90) {
      return '人工经纬度必须成对填写且在合法范围内。';
    }
    if (candidateMode) return '地址解析候选与人工坐标确认不能同时使用。';
    if (!Boolean(form.manualConfirmed)) return '人工坐标必须明确确认。';
  }
  return '';
}

function buildUpdatePayload() {
  const payload = {
    address: addressText.value,
    cityCode: String(form.cityCode || '').trim(),
    version: form.version,
    reason: reasonText.value
  };
  const coordsChanged = !sameCoordinate(form.lng, baseline.lng) || !sameCoordinate(form.lat, baseline.lat);
  const candidateMode = form.selectionMode === 'CANDIDATE' && Boolean(form.candidateToken);
  const manualMode = !candidateMode && (form.selectionMode === 'MANUAL' || coordsChanged);
  if (candidateMode) {
    payload.candidateToken = form.candidateToken;
  } else if (manualMode) {
    payload.lng = Number(form.lng);
    payload.lat = Number(form.lat);
    payload.coordSys = 'GCJ02';
    payload.manualConfirmed = Boolean(form.manualConfirmed);
  } else if (form.clearLocation
    || (existingHasCoordinates.value && addressChanged.value)) {
    payload.clearLocation = true;
  }
  return payload;
}

async function save() {
  if (saving.value) return;
  const validationError = validateSave();
  if (validationError) {
    saveError.value = validationError;
    return;
  }
  saveError.value = '';
  conflictError.value = '';
  const requestGeneration = generation;
  const code = orgCode.value;
  const payload = buildUpdatePayload();
  saving.value = true;
  try {
    const result = await updateOrgLocation(code, payload);
    if (!isCurrent(requestGeneration, code)) return;
    if (!isLocationRecord(result, code)) {
      saveError.value = '地址与定位保存返回数据格式无效，未关闭对话框。';
      return;
    }
    record.value = result;
    emit('saved', result);
    emit('update:modelValue', false);
  } catch (error) {
    if (!isCurrent(requestGeneration, code)) return;
    if (isConflict(error)) {
      conflictError.value = '版本冲突：已重新读取服务端版本，请重新打开后再保存，当前表单未被覆盖。';
      try {
        const latest = await getOrgLocation(code);
        if (!isCurrent(requestGeneration, code)) return;
        if (!isLocationRecord(latest, code)) throw new Error('最新记录返回数据格式无效。');
        record.value = latest;
        conflictVersion.value = latest.version;
      } catch (reloadError) {
        if (isCurrent(requestGeneration, code)) {
          conflictError.value += `（最新版本读取失败：${String(reloadError?.message || '服务不可用')}）`;
        }
      }
    } else {
      saveError.value = formatError('地址与定位保存失败', error);
    }
  } finally {
    if (isCurrent(requestGeneration, code)) saving.value = false;
  }
}

async function previewCandidates() {
  if (!canPreview.value) return;
  const requestGeneration = generation;
  const requestSequence = ++previewSequence;
  const code = orgCode.value;
  previewError.value = '';
  candidates.value = [];
  selectedCandidate.value = null;
  previewing.value = true;
  try {
    const result = await previewOrgLocationGeocode(code, {
      address: addressText.value,
      cityCode: String(form.cityCode || '').trim(),
      reason: reasonText.value
    });
    if (!isCurrent(requestGeneration, code) || requestSequence !== previewSequence) return;
    if (!Array.isArray(result)) {
      candidates.value = [];
      previewError.value = '地址解析预览返回数据格式无效。';
      return;
    }
    candidates.value = result;
  } catch (error) {
    if (!isCurrent(requestGeneration, code) || requestSequence !== previewSequence) return;
    previewError.value = formatError('地址解析预览失败', error);
  } finally {
    if (isCurrent(requestGeneration, code) && requestSequence === previewSequence) previewing.value = false;
  }
}

function chooseCandidate(candidate) {
  if (!candidate?.verificationAllowed || !candidate.candidateToken) return;
  const lng = normalizeCoordinate(candidate.lng, -180, 180);
  const lat = normalizeCoordinate(candidate.lat, -90, 90);
  if (lng === null || lat === null || !Number.isFinite(lng) || !Number.isFinite(lat)) return;
  suppressWatch = true;
  form.candidateToken = candidate.candidateToken;
  form.selectionMode = 'CANDIDATE';
  form.lng = lng;
  form.lat = lat;
  form.coordSys = 'GCJ02';
  form.manualConfirmed = false;
  observedLng = lng;
  observedLat = lat;
  suppressWatch = false;
  selectedCandidate.value = candidate;
}

watch(() => [props.modelValue, props.org?.orgCode], ([visible, code], previous) => {
  if (visible) {
    if (!previous || !previous[0] || previous[1] !== code) loadForOrg();
    return;
  }
  generation += 1;
  resetView();
});
onMounted(() => {
  if (props.modelValue) loadForOrg();
});
onUnmounted(() => {
  alive = false;
  generation += 1;
});

defineExpose({
  capabilities, record, recordReady, form, candidates, selectedCandidate,
  loading, saving, capabilityError, recordError, saveError, conflictError,
  previewing, previewError, conflictVersion,
  storageReady, cityCodeValid, addressChanged, existingHasCoordinates,
  canPreview, buildUpdatePayload, validateSave, previewCandidates, chooseCandidate, save
});
</script>

<style scoped>
.org-location-dialog :deep(.el-dialog__body) { color: var(--color-text); }
.readonly-tip, .hint { color: var(--color-text-muted); font-size: 12px; line-height: 18px; }
.readonly-tip { margin-bottom: var(--space-3); }
.warning-hint { display: block; color: var(--color-warning-fg, #a15c00); font-size: 12px; line-height: 18px; }
.loading-state, .error-state { margin: var(--space-2) 0; padding: var(--space-2) var(--space-3); border-radius: var(--radius-control); }
.loading-state { color: var(--color-text-muted); background: var(--color-surface-soft); }
.error-state { border: 1px solid var(--color-danger-fg); color: var(--color-danger-fg); background: var(--color-danger-bg); }
.error-state.compact { margin-top: var(--space-2); }
.location-form :deep(.el-form-item) { margin-bottom: var(--space-3); }
.plain-control { box-sizing: border-box; width: 100%; min-height: 32px; padding: 5px 10px; border: 1px solid var(--color-border); border-radius: var(--radius-control); color: var(--color-text-strong); background: var(--color-surface); }
.plain-control:focus { outline: 2px solid var(--color-brand-300); border-color: var(--color-brand-500); }
.check-control { display: inline-flex; align-items: center; gap: var(--space-2); color: var(--color-text); font-size: 13px; line-height: 20px; }
.metadata-separator { padding: 0 var(--space-2); color: var(--color-text-muted); }
.geocode-panel { margin-top: var(--space-3); padding: var(--space-3); border: 1px solid var(--color-border); border-radius: var(--radius-control); background: var(--color-surface-soft); }
.panel-title { margin-bottom: var(--space-2); color: var(--color-text-strong); font-weight: 600; }
.action-button { min-height: 32px; padding: 5px 12px; border: 1px solid var(--color-border); border-radius: var(--radius-control); color: var(--color-text-strong); background: var(--color-surface); cursor: pointer; }
.action-button.primary { border-color: var(--color-brand-500); color: var(--color-on-brand, #fff); background: var(--color-brand-500); }
.action-button:disabled { cursor: not-allowed; opacity: .55; }
.candidate-list { display: grid; gap: var(--space-2); margin: var(--space-3) 0 0; padding: 0; list-style: none; }
.candidate-item { display: flex; align-items: center; justify-content: space-between; gap: var(--space-3); padding: var(--space-2); border: 1px solid var(--color-border); border-radius: var(--radius-control); background: var(--color-surface); }
.candidate-main { display: grid; min-width: 0; gap: 2px; }
.candidate-main strong { overflow-wrap: anywhere; color: var(--color-text-strong); }
.selected-candidate { margin-top: var(--space-2); padding: var(--space-2); color: var(--color-text); background: var(--color-brand-100); font-size: 12px; line-height: 18px; }
</style>
