<template>
  <section v-if="headerModel.enabled" class="city-operating-header" data-testid="city-operating-header">
    <PresentationLayout
      :presentation="headerModel.presentation"
      :model="headerModel.model"
      mode="city"
      :data-date="headerModel.model.dataDate"
      :demo="demo"
      :draft-overview="true"
      :amount-unit="amountUnit"
      @business-line-select="emit('business-line-select', $event)"
    />
  </section>
</template>

<script setup>
import { computed } from 'vue';
import PresentationLayout from '../presentation/layout/PresentationLayout.vue';
import { buildCityOperatingHeaderModel } from './cityOperatingHeaderModel.js';

const props = defineProps({
  sourcePresentation: { type: Object, default: () => ({}) },
  citySummary: { type: Object, default: null },
  amountUnit: { type: String, default: 'TEN_THOUSAND' },
  demo: { type: Boolean, default: false }
});
const emit = defineEmits(['business-line-select']);
const headerModel = computed(() => buildCityOperatingHeaderModel(props.sourcePresentation, props.citySummary));
</script>

<style scoped>
.city-operating-header { min-width: 0; margin: 12px 22px; }
.city-operating-header :deep(.presentation-layout) { margin: 0; }
.city-operating-header :deep(.presentation-layout__main),
.city-operating-header :deep(.presentation-layout__footer) { display: none; }
</style>
