// 已发布渲染包的素材组件注册表。
// 这里故意只加载运行时 Component，不加载已退役的属性面板、拖拽元数据或编辑器 store。
import TextLabel from './widgets/text-label/Component.vue';
import ImageBox from './widgets/image-box/Component.vue';
import RectShape from './widgets/rect-shape/Component.vue';
import BorderDecor from './widgets/border-decor/Component.vue';
import ClockWidget from './widgets/clock-widget/Component.vue';
import TitleBar from './widgets/title-bar/Component.vue';
import DecorLine from './widgets/decor-line/Component.vue';
import Marquee from './widgets/marquee/Component.vue';
import PeriodFilter from './widgets/period-filter/Component.vue';

const runtimeWidgets = Object.freeze({
  TextLabel,
  ImageBox,
  RectShape,
  BorderDecor,
  ClockWidget,
  TitleBar,
  DecorLine,
  Marquee,
  PeriodFilter
});

export function findRuntimeWidget(component) {
  return runtimeWidgets[component] || null;
}

export default runtimeWidgets;
