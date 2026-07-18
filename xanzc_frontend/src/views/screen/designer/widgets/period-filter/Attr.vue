<template>
  <CommonAttr :element="element">
    <el-form-item label="可选周期">
      <el-checkbox-group v-model="element.propValue.periods" @change="onPeriodsChange">
        <el-checkbox v-for="p in TIME_PARAM_PRESETS" :key="p" :label="p">{{ PERIOD_LABELS[p] }}</el-checkbox>
      </el-checkbox-group>
      <div class="attr-hint">运行大屏时联动所有时序(TIMESERIES)区块;每屏最多摆放 1 个</div>
    </el-form-item>
    <el-form-item label="默认选中">
      <el-select v-model="element.propValue.defaultPeriod" @change="touch">
        <el-option v-for="p in selectable" :key="p" :label="PERIOD_LABELS[p]" :value="p" />
      </el-select>
    </el-form-item>
  </CommonAttr>
</template>
<script setup>
// 属性面板直接 mutate 同一份 store.curComponent 引用(共享引用模式,同 Marquee/TitleBar),改动防抖记快照。
import { computed } from 'vue';
import CommonAttr from '@/views/screen/designer/panels/CommonAttr.vue';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
import { TIME_PARAM_PRESETS } from '@/utils/dsConfig';
import { PERIOD_LABELS, normalizePeriodOptions } from '@/utils/globalPeriod';

const props = defineProps({ element: { type: Object, required: true } });
const store = useScreenDesignerStore();
// 脏节点防御:propValue/periods 缺失时补默认(checkbox-group v-model 需要数组;只补缺失不覆盖,不记快照)
if (!props.element.propValue) props.element.propValue = {};
if (!Array.isArray(props.element.propValue.periods)) {
  props.element.propValue.periods = [...TIME_PARAM_PRESETS];
}
if (!props.element.propValue.defaultPeriod) {
  props.element.propValue.defaultPeriod = normalizePeriodOptions(props.element.propValue).defaultPeriod;
}
// 默认选中下拉只给"当前勾选的周期"(经规整,空勾选回退全量预设,与运行时渲染口径一致)
const selectable = computed(() => normalizePeriodOptions(props.element.propValue).periods);

function touch() { store.pushSnapshotDebounced(); }
function onPeriodsChange() {
  // 勾掉默认选中项时同步校正 defaultPeriod,避免落盘配置自相矛盾(运行时纯函数虽兜底,存储仍应自洽)
  const { periods, defaultPeriod } = normalizePeriodOptions(props.element.propValue);
  if (!periods.includes(props.element.propValue.defaultPeriod)) {
    props.element.propValue.defaultPeriod = defaultPeriod;
  }
  touch();
}
</script>
<style scoped>
.attr-hint { width: 100%; font-size: 12px; color: #7d9bc9; line-height: 1.5; margin-top: 2px; }
</style>
