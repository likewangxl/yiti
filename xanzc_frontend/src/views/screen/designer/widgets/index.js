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
// 2026-07-17 素材装饰扩充:科技感标题条/装饰线/跑马灯(全 CSS 自绘零依赖,后端 COMPONENT_TYPES 白名单同步)
import TitleBar from './title-bar/Component.vue';
import TitleBarAttr from './title-bar/Attr.vue';
import titleBarMeta from './title-bar/meta';
import DecorLine from './decor-line/Component.vue';
import DecorLineAttr from './decor-line/Attr.vue';
import decorLineMeta from './decor-line/meta';
import Marquee from './marquee/Component.vue';
import MarqueeAttr from './marquee/Attr.vue';
import marqueeMeta from './marquee/meta';
// 2026-07-17 §5.3 全屏周期过滤器:运行时写 screen 级 globalPeriod 联动 TIMESERIES 区块;
// 每屏最多 1 个(后端 ScreenCanvasServiceImpl 保存/发布校验 RPT-43006,白名单同步新增)
import PeriodFilter from './period-filter/Component.vue';
import PeriodFilterAttr from './period-filter/Attr.vue';
import periodFilterMeta from './period-filter/meta';
import ChartWidget from './chart-widget/Component.vue';
import ChartWidgetAttr from './chart-widget/Attr.vue';
import chartWidgetMeta from './chart-widget/meta';
// MapCenter:省级屏地图,复用运行时组件(props 是 mapPoints 数组,与素材类 element/propValue 签名不同)。
// 只登记进 componentsMap 供 findWidget/findAttr 查得到；不并入 materialMetas——见下方 mapCenterMeta 注释。
import MapCenter from '@/views/screen/components/MapCenter.vue';
import MapCenterAttr from './map-center/Attr.vue';
import mapCenterMeta from './map-center/meta';
// Group:多选成组容器,由画布多选「成组」生成而非拖拽创建——与 MapCenter 同类,
// 只登记 componentsMap,不入 materialMetas 拖拽面板。
import Group from './group/Component.vue';
import GroupAttr from './group/Attr.vue';

const componentsMap = {
  TextLabel, TextLabelAttr,
  ImageBox, ImageBoxAttr,
  RectShape, RectShapeAttr,
  BorderDecor, BorderDecorAttr,
  ClockWidget, ClockWidgetAttr,
  TitleBar, TitleBarAttr,
  DecorLine, DecorLineAttr,
  Marquee, MarqueeAttr,
  PeriodFilter, PeriodFilterAttr,
  ChartWidget, ChartWidgetAttr,
  MapCenter, MapCenterAttr,
  Group, GroupAttr
};

export const materialMetas = [textLabelMeta, imageBoxMeta, rectShapeMeta, borderDecorMeta, clockWidgetMeta,
  titleBarMeta, decorLineMeta, marqueeMeta, periodFilterMeta];
// mapCenterMeta 故意不并入上面的 materialMetas:它驱动 ComponentPanel 的拖拽入口，而 MapCenter
// 一期不开放拖拽创建(运行时由 ScreenRenderer 按 component==='MapCenter' 走独立分支注入 mapPoints，
// 不经拖拽面板/newComponentFromMeta 生成节点；地图组件由屏配置直投渲染包 components 节点)。
export { mapCenterMeta };

// 图表类型自动扫描注册(eager 同步纳入):13 个 charts/*.js(9 基础 + 4 个 KPI 专属)
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
    const type = innerType || 'METRIC_CARD';
    // 注册表层兜底门禁:面板层(Task 9 ComponentPanel)按 enabled 过滤拖拽入口,
    // 但不能假定所有调用方都经过面板——这里对 enabled:false 的占位图表类型直接拒绝,
    // 防止"面板层以为注册表兜底、注册表以为面板层已过滤"的两头漏防。
    const chartMeta = chartMetas.find(c => c.innerType === type);
    if (chartMeta && chartMeta.enabled === false) {
      throw new Error('该图表类型尚未启用: ' + type);
    }
    node.innerType = type;
    node.blockId = null;      // 待保存时后端 upsert 得 id
    node.bindJson = '{}';
    node.styleJson = JSON.stringify({ title: '未命名图表', refreshSec: 60 });
    node.drillJson = '{}';
  }
  return node;
}
