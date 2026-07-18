<template>
  <div class="w-border-decor" :class="cls">
    <template v-if="isCorner">
      <span class="corner tl" /><span class="corner tr" /><span class="corner bl" /><span class="corner br" />
    </template>
  </div>
</template>
<script setup>
// 边框装饰——复用 _screen-theme.scss 的科技感描边/发光视觉语言(scr-cyan),纯装饰不接数据。
// 本期从 3 种扩到 7 种(全 CSS 自绘零图片零依赖);变体清单/类名映射唯一来源在 variants.js,
// 非法/缺省 variant 回退 tech-a(存量画布 JSON 兼容),四角类变体(tech-b/f)共享 corner 模板。
import { computed } from 'vue';
import { CORNER_VARIANTS, borderDecorClass } from './variants';
const props = defineProps({ element: { type: Object, required: true } });
const cls = computed(() => borderDecorClass((props.element.propValue || {}).variant));
// 用回退后的类名判定,保证非法 variant 不渲染四角 span
const isCorner = computed(() => CORNER_VARIANTS.includes(cls.value.slice('decor-'.length)));
</script>
<style scoped>
.w-border-decor { width: 100%; height: 100%; box-sizing: border-box; position: relative; pointer-events: none; }
/* 四角 span 通用定位(tech-b/tech-f 共享;各变体只定义自己的角视觉) */
.w-border-decor .corner { position: absolute; width: 24px; height: 24px; }
.w-border-decor .tl { top: 0; left: 0; }
.w-border-decor .tr { top: 0; right: 0; }
.w-border-decor .bl { bottom: 0; left: 0; }
.w-border-decor .br { bottom: 0; right: 0; }

/* tech-a 描边发光：整框细描边 + 顶部发光条(复用 .scr-block::before 视觉)
   色值改用 CSS 变量 + 字面量 fallback:今天画布根未挂 scr-theme-vars 时按 fallback 渲染(视觉零变化)，
   Task 9/10 在画布根挂 _screen-theme.scss 的 scr-theme-vars 后自动跟随主题覆盖。
   rgba(0,229,255,.25)/#00e5ff 与 _screen-theme.scss 既有 --scr-border/--scr-cyan 定义值完全一致，直接复用；
   .45/.12 两档透明度既有变量表未收录，按同一 --scr-cyan-NN 命名惯例新增，无同名变量时同样退回字面量 fallback。 */
.decor-tech-a { border: 1px solid var(--scr-border, rgba(0, 229, 255, .25)); border-radius: 6px; }
.decor-tech-a::before {
  content: '';
  position: absolute; top: 0; left: 0; right: 0; height: 2px;
  background: linear-gradient(90deg, transparent, var(--scr-cyan, #00e5ff) 20%, var(--scr-cyan, #00e5ff) 80%, transparent);
  opacity: .65;
}

/* tech-b 四角光标：四角 L 形装饰角标 */
.decor-tech-b .corner { border: 2px solid var(--scr-cyan, #00e5ff); opacity: .85; }
.decor-tech-b .tl { border-right: none; border-bottom: none; }
.decor-tech-b .tr { border-left: none; border-bottom: none; }
.decor-tech-b .bl { border-right: none; border-top: none; }
.decor-tech-b .br { border-left: none; border-top: none; }

/* tech-c 双线描边：内外双线描边框 */
.decor-tech-c {
  border: 1px solid var(--scr-cyan-45, rgba(0, 229, 255, .45));
  border-radius: 4px;
  box-shadow: inset 0 0 0 4px var(--scr-cyan-12, rgba(0, 229, 255, .12));
}

/* tech-d 渐变霓虹：青→蓝渐变描边环(::before padding+mask 镂空中心) + 内外霓虹光晕;
   光晕放宿主元素上——mask 会连 box-shadow 一起裁掉,不能与渐变环同层 */
.decor-tech-d {
  border-radius: 6px;
  box-shadow: 0 0 14px rgba(0, 229, 255, .35), inset 0 0 14px rgba(0, 229, 255, .16);
}
.decor-tech-d::before {
  content: '';
  position: absolute; inset: 0; border-radius: 6px; padding: 2px;
  background: linear-gradient(135deg, var(--scr-cyan, #00e5ff), var(--scr-blue, #3d7eff) 50%, var(--scr-cyan, #00e5ff));
  -webkit-mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
  -webkit-mask-composite: xor;
  mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
  mask-composite: exclude;
}

/* tech-e 斜切角：八边形斜切角渐变框(clip-path 切角 + mask 镂空中心,角部呈斜切加宽视觉) */
.decor-tech-e::before {
  content: '';
  position: absolute; inset: 0; padding: 2px;
  background: linear-gradient(135deg, var(--scr-cyan, #00e5ff), var(--scr-cyan-45, rgba(0, 229, 255, .45)) 50%, var(--scr-cyan, #00e5ff));
  clip-path: polygon(18px 0, calc(100% - 18px) 0, 100% 18px, 100% calc(100% - 18px),
    calc(100% - 18px) 100%, 18px 100%, 0 calc(100% - 18px), 0 18px);
  -webkit-mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
  -webkit-mask-composite: xor;
  mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
  mask-composite: exclude;
}

/* tech-f 点阵角：四角点阵网格(radial-gradient 圆点) + 极淡整框定界 */
.decor-tech-f { border: 1px solid var(--scr-cyan-12, rgba(0, 229, 255, .12)); }
.decor-tech-f .corner {
  width: 28px; height: 28px; opacity: .9;
  background-image: radial-gradient(var(--scr-cyan, #00e5ff) 1.5px, transparent 1.6px);
  background-size: 7px 7px;
}

/* tech-g 内发光：细描边 + 内侧青色弥散光(双层 inset 阴影,近边亮向内渐隐) */
.decor-tech-g {
  border: 1px solid var(--scr-border, rgba(0, 229, 255, .25));
  border-radius: 6px;
  box-shadow: inset 0 0 18px rgba(0, 229, 255, .22), inset 0 0 46px rgba(0, 229, 255, .08);
}
</style>
