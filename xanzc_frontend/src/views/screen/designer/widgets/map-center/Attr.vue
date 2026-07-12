<template>
  <CommonAttr :element="element" />
</template>
<script setup>
// MapCenter 地图组件属性面板——一期无私有可配置字段(点位数据由运行时 mapPoints 注入,非 propValue
// 驱动),仅保留 CommonAttr 提供的位置/尺寸/透明度公共项,故不传 slot。
import CommonAttr from '@/views/screen/designer/panels/CommonAttr.vue';
defineProps({ element: { type: Object, required: true } });
</script>
