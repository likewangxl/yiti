<template>
  <section v-if="components.length" class="presentation-series-table" data-testid="presentation-series-table">
    <article v-for="item in components" :key="item.componentId" class="presentation-series-table__panel" :data-component-id="item.componentId">
      <header><div><h2>{{ item.title }}</h2><p v-if="item.subtitle">{{ item.subtitle }}</p></div><small v-if="item.state !== 'READY'">{{ item.issues.join('；') }}</small></header>
      <PanoramaTrend v-if="item.componentType === 'TREND' && item.state === 'READY'" :rows="item.rows" :series="item.series" :title="item.title" />
      <div v-else-if="item.componentType === 'TREND'" class="presentation-series-table__empty">暂无可用趋势</div>
      <div v-else class="presentation-series-table__table-wrap">
        <table><thead><tr><th v-for="column in item.columns" :key="column.columnKey">{{ column.label }}</th></tr></thead>
          <tbody><tr v-for="row in item.rows" :key="row.key"><td v-for="cell in row.cells" :key="cell.key">{{ cell.text }}</td></tr></tbody></table>
        <p v-if="!item.rows.length">暂无明细数据</p>
      </div>
    </article>
  </section>
</template>

<script setup>
import PanoramaTrend from '../../panorama/PanoramaTrend.vue';
defineProps({ components: { type: Array, default: () => [] } });
</script>

<style scoped>
.presentation-series-table{display:grid;gap:12px;margin:12px 0}.presentation-series-table__panel{overflow:hidden;border:1px solid rgba(106,157,220,.35);border-radius:8px;background:rgba(7,24,62,.88);color:#eaf2ff}.presentation-series-table__panel>header{display:flex;justify-content:space-between;gap:10px;padding:11px 14px;border-bottom:1px solid rgba(106,157,220,.2)}.presentation-series-table h2,.presentation-series-table p{margin:0}.presentation-series-table header p,.presentation-series-table header small{color:#9fc2df;font-size:11px}.presentation-series-table__table-wrap{overflow:auto;padding:10px 14px}.presentation-series-table table{width:100%;border-collapse:collapse}.presentation-series-table th,.presentation-series-table td{padding:8px;border-bottom:1px solid rgba(106,157,220,.18);text-align:left;white-space:nowrap}.presentation-series-table th{color:#9fc2df;font-size:11px}.presentation-series-table__empty{padding:24px;text-align:center;color:#9fc2df}
</style>
