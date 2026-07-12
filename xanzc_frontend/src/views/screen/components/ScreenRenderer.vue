<template>
  <template v-for="col in columns" :key="col.region">
    <!-- PROVINCE 的 MAIN 固定渲染地图 -->
    <div class="scr-col" :style="{ flex: col.flex }">
      <MapCenter v-if="col.isMap" :map-points="mapPoints" />
      <template v-else>
        <div v-for="row in col.rows" :key="row.rowNo" class="scr-row"
             :style="{ flex: row.heightPct + ' 1 0' }">
          <BlockContainer v-for="b in row.blocks" :key="b.id"
                          :block="b" :context="context"
                          :style="{ flex: b.widthPct + ' 1 0' }" />
        </div>
      </template>
    </div>
  </template>
</template>

<script setup>
import { computed } from 'vue';
import BlockContainer from './BlockContainer.vue';
import MapCenter from './MapCenter.vue';

const props = defineProps({
  screen: { type: Object, required: true },
  blocks: { type: Array, default: () => [] },
  mapPoints: { type: Array, default: () => [] },
  context: { type: Object, default: () => ({}) }
});

// region → 行分组（rowNo 升序，行内 colNo 升序，行高取首块 heightPct）
function groupRows(regionBlocks) {
  const byRow = new Map();
  for (const b of regionBlocks) {
    if (!byRow.has(b.rowNo)) byRow.set(b.rowNo, []);
    byRow.get(b.rowNo).push(b);
  }
  return [...byRow.entries()]
    .sort((a, b) => a[0] - b[0])
    .map(([rowNo, list]) => ({
      rowNo,
      heightPct: list[0]?.heightPct || 50,
      blocks: list.sort((a, b) => a.colNo - b.colNo)
    }));
}

const columns = computed(() => {
  const isProvince = props.screen?.viewLevel === 'PROVINCE';
  const regions = ['LEFT', 'MAIN', 'RIGHT'];
  const out = [];
  for (const region of regions) {
    const regionBlocks = props.blocks.filter(b => b.region === region);
    const isMap = isProvince && region === 'MAIN';
    if (!isMap && regionBlocks.length === 0) continue; // 空区域不占位（MAIN 地图除外）
    out.push({
      region,
      isMap,
      flex: region === 'MAIN' ? 2 : 1, // MAIN 双倍宽（省级 25/50/25）
      rows: groupRows(regionBlocks)
    });
  }
  return out;
});
</script>
