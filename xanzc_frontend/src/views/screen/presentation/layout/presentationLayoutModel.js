/**
 * displaySchemaVersion=1 的整页布局解析。
 *
 * 这里仅负责解析和排序保存的展示协议；指标查询、数值转换和机构排名
 * 继续交给 presentation/model 下已有的只读适配器，避免在布局层复制业务逻辑。
 */

export const DISPLAY_LAYOUT_REGIONS = Object.freeze({
  HEADER: 0,
  LEFT: 1,
  CENTER: 2,
  RIGHT: 3,
  BOTTOM: 4,
  OVERLAY: 5
});

export const DISPLAY_LAYOUT_COMPONENT_TYPES = Object.freeze([
  'METRIC_CARD',
  'COMPLETION',
  'TREND',
  'COMPOSITION_TABS',
  'RANKING',
  'MAP',
  'DETAIL_TABLE'
]);

const COMPONENT_TYPE_SET = new Set(DISPLAY_LAYOUT_COMPONENT_TYPES);

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function text(value) {
  return value === null || value === undefined ? '' : String(value).trim();
}

/** 从视图响应或 displayPresentation 包装层中取得真正的 presentation。 */
export function presentationOf(source) {
  if (!isObject(source)) return {};
  if (isObject(source.displayPresentation)) return presentationOf(source.displayPresentation);
  if (source.displaySchemaVersion !== undefined || source.display) return source;
  if (isObject(source.presentation)) return presentationOf(source.presentation);
  if (isObject(source.canvasStyle?.presentation)) return presentationOf(source.canvasStyle.presentation);
  if (isObject(source.renderPackage?.canvasStyle?.presentation)) return presentationOf(source.renderPackage.canvasStyle.presentation);
  return {};
}

export function isConfiguredPresentation(source) {
  return presentationOf(source).displaySchemaVersion === 1;
}

function regionOrder(value) {
  return DISPLAY_LAYOUT_REGIONS[text(value).toUpperCase()] ?? 99;
}

/** 返回可渲染的版本1组件，顺序固定为 region -> order -> 原始位置。 */
export function getDisplayComponents(source) {
  const presentation = presentationOf(source);
  if (presentation.displaySchemaVersion !== 1) return [];
  const components = Array.isArray(presentation.display?.components) ? presentation.display.components : [];
  return components
    .map((component, index) => ({ component, index }))
    .filter(({ component }) => COMPONENT_TYPE_SET.has(component?.componentType) && component?.visible !== false)
    .sort((left, right) => regionOrder(left.component?.layoutRegion) - regionOrder(right.component?.layoutRegion)
      || ((Number.isInteger(left.component?.order) ? left.component.order : left.index)
        - (Number.isInteger(right.component?.order) ? right.component.order : right.index))
      || left.index - right.index)
    .map(({ component }) => component);
}

/** 让单个 MAP 组件复用现有地图模型，同时避免选中另一张 MAP。 */
export function presentationForComponent(source, component) {
  const presentation = presentationOf(source);
  return {
    ...presentation,
    display: {
      ...(isObject(presentation.display) ? presentation.display : {}),
      components: component ? [component] : []
    }
  };
}

export function componentTitle(component, fallback = '') {
  const custom = component?.text?.titleMode === 'CUSTOM' ? text(component.text.title) : '';
  return custom || text(component?.text?.title) || text(fallback) || '机构排名';
}

export { isObject };
