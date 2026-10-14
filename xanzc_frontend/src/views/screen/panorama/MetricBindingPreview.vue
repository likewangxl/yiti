<template>
  <main class="metric-binding-preview">
    <p class="metric-binding-preview__banner" data-testid="binding-preview-banner">
      仅开发态 mock，非联调。下面的指标是 2026-06-05 历史快照中的样例，不是当前数据库目录，也不会请求后端。
    </p>
    <MetricBindingComposer
      slot-key="depositAverage"
      slot-label="存款月均"
      template-name="branch-overview-v1"
      :field-specs="fieldSpecs"
      composition-mode="rows"
      :screen="screen"
      :datasources="[]"
      :catalog="catalog"
      @apply="applied = $event" />
    <pre v-if="applied" data-testid="binding-preview-plan">{{ planText }}</pre>
  </main>
</template>

<script setup>
import { computed, ref } from 'vue';
import MetricBindingComposer from './MetricBindingComposer.vue';

const fieldSpecs = [
  { semantic: 'value', label: '数值', kind: 'metric', required: true, unitKinds: ['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION'] }
];
const screen = { bizLine: 'COMMON', orgScopeMode: 'NAMED_GROUP', orgGroupCode: 'GRP_SAMPLE' };
const catalog = [
  { metricCode: 'M_0265', metricName: '一般性存款月均余额-机构', baseDim: 'ORG', valSlot: 12, unit: 'YUAN', unitSource: 'CONFIRMED_CODE', metricCategory: '规模类', summaryRule: 'SUM', tableName: 'ORG_INDEX_RESULT', subjectColumn: 'org_code', outputColumn: '一般性存款月均余额-机构', bindable: true },
  { metricCode: 'M_0266', metricName: '一般性存款月均余额较上月-机构', baseDim: 'ORG', valSlot: 51, unit: null, unitSource: 'NONE', metricCategory: '规模类', summaryRule: 'SUM', tableName: 'ORG_INDEX_RESULT', subjectColumn: 'org_code', outputColumn: '一般性存款月均余额较上月-机构', bindable: true },
  { metricCode: 'M_0277', metricName: '对公一般性存款余额', baseDim: 'ORG', valSlot: 20, unit: 'YUAN', unitSource: 'COLUMN', metricCategory: '规模类', summaryRule: 'SUM', tableName: 'ORG_INDEX_RESULT', subjectColumn: 'org_code', outputColumn: '对公一般性存款余额', bindable: true },
  { metricCode: 'M_0309', metricName: '零售一般性存款余额', baseDim: 'ORG', valSlot: 21, unit: 'YUAN', unitSource: 'COLUMN', metricCategory: '规模类', summaryRule: 'SUM', tableName: 'ORG_INDEX_RESULT', subjectColumn: 'org_code', outputColumn: '零售一般性存款余额', bindable: true },
  { metricCode: 'M_0347', metricName: '对公一般性贷款余额', baseDim: 'ORG', valSlot: 30, unit: null, unitSource: 'NONE', metricCategory: '规模类', summaryRule: 'SUM', tableName: 'ORG_INDEX_RESULT', subjectColumn: 'org_code', outputColumn: '对公一般性贷款余额', bindable: true }
];
const applied = ref(null);
const planText = computed(() => JSON.stringify(applied.value?.binding || {}, null, 2));
</script>

<style scoped>
.metric-binding-preview { max-width: 960px; margin: 24px auto; padding: 0 16px 32px; }
.metric-binding-preview__banner { margin: 0 0 12px; padding: 10px 12px; background: #fff7e8; color: #8a5a00; font-size: 13px; }
pre { margin-top: 12px; padding: 12px; overflow: auto; background: #0f172a; color: #e2e8f0; font-size: 12px; }
</style>
