// 组件两层注册——参照 DataEase:①素材/装饰用手写字典 componentsMap(命名 Xxx + XxxAttr);
// ②图表元数据用 import.meta.glob 自动扫描(新增图表=新增一个 charts/*.js,零改核心)。
import TextLabel from './text-label/Component.vue';
import TextLabelAttr from './text-label/Attr.vue';
import textLabelMeta from './text-label/meta';
import ImageBox from './image-box/Component.vue';
import ImageBoxAttr from './image-box/Attr.vue';
import imageBoxMeta from './image-box/meta';
import RectShape from './rect-shape/Component.vue';
import RectShapeAttr from './rect-shape/Attr.vue';
import rectShapeMeta from './rect-shape/meta';
import BorderDecor from './border-decor/Component.vue';
import BorderDecorAttr from './border-decor/Attr.vue';
import borderDecorMeta from './border-decor/meta';
import ClockWidget from './clock-widget/Component.vue';
import ClockWidgetAttr from './clock-widget/Attr.vue';
import clockWidgetMeta from './clock-widget/meta';
import ChartWidget from './chart-widget/Component.vue';
import ChartWidgetAttr from './chart-widget/Attr.vue';
import chartWidgetMeta from './chart-widget/meta';

const componentsMap = {
  TextLabel, TextLabelAttr,
  ImageBox, ImageBoxAttr,
  RectShape, RectShapeAttr,
  BorderDecor, BorderDecorAttr,
  ClockWidget, ClockWidgetAttr,
  ChartWidget, ChartWidgetAttr
};

export const materialMetas = [textLabelMeta, imageBoxMeta, rectShapeMeta, borderDecorMeta, clockWidgetMeta];

// 图表类型自动扫描注册(eager 同步纳入):9 个 charts/*.js
const chartModules = import.meta.glob('./chart-widget/charts/*.js', { eager: true });
export const chartMetas = Object.values(chartModules).map(m => m.default);

export function findWidget(component) { return componentsMap[component] || null; }
export function findAttr(component) { return componentsMap[component + 'Attr'] || null; }

/** 从元数据造一个新组件节点(拖入画布 / 粘贴用) */
export function newComponentFromMeta(component, innerType) {
  const meta = component === 'ChartWidget'
    ? chartWidgetMeta
    : materialMetas.find(m => m.component === component);
  const base = meta || { defaultStyle: { top: 0, left: 0, width: 200, height: 120 }, defaultProps: {} };
  const node = {
    id: 'w-' + Math.random().toString(36).slice(2, 8),
    component,
    style: { ...base.defaultStyle },
    propValue: JSON.parse(JSON.stringify(base.defaultProps || {})),
    isLock: false, isShow: true
  };
  if (component === 'ChartWidget') {
    node.innerType = innerType || 'METRIC_CARD';
    node.blockId = null;      // 待保存时后端 upsert 得 id
    node.bindJson = '{}';
    node.styleJson = JSON.stringify({ title: '未命名图表', refreshSec: 60 });
    node.drillJson = '{}';
  }
  return node;
}
