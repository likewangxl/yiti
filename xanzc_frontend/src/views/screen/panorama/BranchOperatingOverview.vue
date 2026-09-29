<template>
  <PanoramaDashboard
    :model="overviewModel"
    :source-presentation="branchPresentation"
    :loading="loading"
    :error="error"
    :demo="demo"
    :draft-overview="true"
    :show-configure="false"
    @refresh="emit('refresh')"
    @back="emit('back')"
    @branch-select="emit('branch-select', $event)"
    @business-line-select="emit('business-line-select', $event)"
    @map-context="emit('map-context', $event)"
  >
    <template #header-context>
      <label class="branch-operating-overview-picker">
        <span class="panorama-visually-hidden">当前支行</span>
        <select data-testid="branch-operating-branch-select" :value="safeModel.orgCode || ''" aria-label="选择支行" @change="selectBranch($event.target.value)">
          <option v-if="!safeModel.orgCode" value="">请选择支行</option>
          <option v-for="institution in institutions" :key="institution.orgCode" :value="institution.orgCode">{{ institution.orgName || institution.orgCode }}</option>
        </select>
      </label>
    </template>
    <template #branch-map>
      <BranchIncompleteAchievementPanel :targets="targets" />
    </template>
    <template #branch-ranking>
      <BranchPerformancePanel
        :org-code="String(safeModel.orgCode || '')"
        :data-date="kpiDataDate"
        :enabled="performanceEnabled && !loading && !error"
        :refresh-key="performanceRefreshKey"
        compact
        ranking-only
      />
    </template>
  </PanoramaDashboard>
</template>

<script setup>
import { computed } from 'vue';
import PanoramaDashboard from './PanoramaDashboard.vue';
import BranchIncompleteAchievementPanel from './BranchIncompleteAchievementPanel.vue';
import BranchPerformancePanel from './BranchPerformancePanel.vue';
import { buildBranchOverviewPresentation } from './branchOverviewPresentation.js';

const props = defineProps({
  model: { type: Object, default: () => ({}) },
  sourcePresentation: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  demo: { type: Boolean, default: false },
  performanceEnabled: { type: Boolean, default: false },
  performanceRefreshKey: { type: Number, default: 0 }
});
const emit = defineEmits(['refresh', 'back', 'branch-select', 'business-line-select', 'map-context']);
const safeModel = computed(() => (props.model && typeof props.model === 'object' ? props.model : {}));
const institutions = computed(() => Array.isArray(safeModel.value.institutions) ? safeModel.value.institutions : []);
const targets = computed(() => Array.isArray(safeModel.value.targets) ? safeModel.value.targets : []);
const branchPresentation = computed(() => buildBranchOverviewPresentation(props.sourcePresentation));
const overviewModel = computed(() => ({
  ...safeModel.value,
  title: safeModel.value.orgName || safeModel.value.title || '支行经营总览',
  scopeLabel: safeModel.value.orgName || safeModel.value.scopeLabel || '当前支行'
}));
const kpiDataDate = computed(() => /^\d{4}-\d{2}-\d{2}$/.test(safeModel.value.targetDate || '')
  ? safeModel.value.targetDate : (safeModel.value.dataDate || ''));

function selectBranch(value) {
  const code = String(value || '').trim();
  if (code) emit('branch-select', code);
}
</script>

<style scoped>
.branch-operating-overview-picker{display:flex;align-items:center}.branch-operating-overview-picker select{min-width:150px}
</style>
