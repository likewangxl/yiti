<template>
  <div class="w-periodfilter">
    <span v-for="p in opts.periods" :key="p" class="pf-btn" :class="{ on: p === selected }"
          @click="pick(p)">{{ PERIOD_LABELS[p] || p }}</span>
  </div>
</template>
<script setup>
// 全屏周期过滤器——横向周期切换按钮组(深色科技风,CSS 自绘零依赖)。
// 联动:运行时 ScreenView provide 响应式 globalPeriod(GLOBAL_PERIOD_INJECT_KEY),切换时写入,
// BlockContainer watch 后按 utils/globalPeriod 纯函数判定是否覆盖自身周期重新取数;
// 设计器画布未 provide 该键 → inject 兜底 null,点击仅切本地高亮,不触发任何联动(静态展示)。
import { computed, inject, ref, watch } from 'vue';
import { GLOBAL_PERIOD_INJECT_KEY, PERIOD_LABELS, normalizePeriodOptions } from '@/utils/globalPeriod';

const props = defineProps({ element: { type: Object, required: true }, mode: { type: String, default: 'design' } });
// propValue 规整(非法剔除/空集回退/默认项校正)集中在纯函数,组件端零判定分支
const opts = computed(() => normalizePeriodOptions(props.element.propValue));
const selected = ref(opts.value.defaultPeriod);
// Attr 面板改可选集合后,已选项可能被剔除 → 回落到规整后的默认项(仅本地高亮,不写全局)
watch(opts, o => { if (!o.periods.includes(selected.value)) selected.value = o.defaultPeriod; });

const globalPeriod = inject(GLOBAL_PERIOD_INJECT_KEY, null);
function pick(p) {
  selected.value = p;
  // 仅运行时(ScreenView 已 provide)写入 screen 级周期;默认 null=不干预,首次点击才开始联动
  if (globalPeriod) globalPeriod.value = p;
}
</script>
<style scoped>
.w-periodfilter {
  width: 100%; height: 100%; box-sizing: border-box;
  display: flex; align-items: center; justify-content: flex-end; gap: 8px;
  overflow: hidden; white-space: nowrap;
}
.pf-btn {
  padding: 4px 14px; font-size: 13px; letter-spacing: 1px; cursor: pointer; user-select: none;
  color: var(--scr-text-dim, #7d9bc9);
  background: rgba(0, 229, 255, .04);
  border: 1px solid var(--scr-border, rgba(96, 148, 214, .38));
  border-radius: 3px;
  /* 科技感斜切角:左上/右下小切角 */
  clip-path: polygon(8px 0, 100% 0, 100% calc(100% - 8px), calc(100% - 8px) 100%, 0 100%, 0 8px);
  transition: color .15s, background .15s, box-shadow .15s;
}
.pf-btn:hover { color: var(--scr-cyan, #00e5ff); }
.pf-btn.on {
  color: var(--scr-cyan, #00e5ff);
  background: linear-gradient(180deg, rgba(0, 229, 255, .22), rgba(0, 229, 255, .06));
  border-color: var(--scr-cyan, #00e5ff);
  box-shadow: 0 0 8px var(--scr-cyan-45, rgba(0, 229, 255, .45)) inset, 0 0 6px rgba(0, 229, 255, .25);
  text-shadow: 0 0 6px var(--scr-cyan-45, rgba(0, 229, 255, .45));
}
</style>
