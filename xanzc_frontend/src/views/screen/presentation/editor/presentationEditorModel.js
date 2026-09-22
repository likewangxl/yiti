import {
  DISPLAY_COMPONENT_TYPES,
  DISPLAY_SCHEMA_VERSION,
  INTERACTION_ACTIONS,
  resolveComponentTitle,
  validateDisplayConfig
} from '../contract/displayContract';

/** 编辑器允许创建的受控组件类型。顺序与展示协议保持一致。 */
export const COMPONENT_TYPES = Object.freeze([...DISPLAY_COMPONENT_TYPES]);

export const EDITOR_STATUS = Object.freeze({
  BOUND: 'BOUND',
  DRAFT_UNBOUND: 'DRAFT_UNBOUND'
});

const LAYOUT_REGIONS = new Set(['HEADER', 'LEFT', 'CENTER', 'RIGHT', 'BOTTOM', 'OVERLAY']);
const TITLE_MODES = new Set(['AUTO', 'CUSTOM']);
const BUSINESS_LINE_TARGETS = new Set(['CORP', 'RETAIL', 'COMMON']);
const EDITOR_ONLY_KEYS = new Set(['editorStatus', 'bindingStatus']);
const COMPONENT_KEYS = new Set([
  'componentId', 'componentType', 'layoutRegion', 'order', 'visible',
  'text', 'format', 'content', 'interaction', 'dataRefs', 'editorStatus', 'bindingStatus'
]);
const TEXT_KEYS = new Set(['titleMode', 'title', 'subtitle', 'description']);
const FORMAT_KEYS = new Set(['displayUnit', 'decimals', 'thousandsSeparator', 'negativeStyle', 'emptyText']);
const INTERACTION_KEYS = new Set(['action', 'target']);
const CONTENT_KEYS = new Set(['mainField', 'subFields', 'series', 'columns', 'tabs', 'rankingMetrics']);

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function hasOwn(value, key) {
  return isObject(value) && Object.prototype.hasOwnProperty.call(value, key);
}

/**
 * 配置对象只包含 JSON 数据，但这里保留一个无 structuredClone 的浏览器回退，
 * 使状态模型在旧测试运行时也不会意外共享嵌套引用。
 */
export function deepClone(value) {
  if (typeof structuredClone === 'function') {
    try {
      return structuredClone(value);
    } catch {
      // 继续使用下面的 JSON 数据递归复制。
    }
  }
  if (Array.isArray(value)) return value.map(item => deepClone(item));
  if (isObject(value)) return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, deepClone(item)]));
  return value;
}

function deepEqual(left, right) {
  if (Object.is(left, right)) return true;
  if (Number.isNaN(left) && Number.isNaN(right)) return true;
  if (Array.isArray(left) || Array.isArray(right)) {
    if (!Array.isArray(left) || !Array.isArray(right) || left.length !== right.length) return false;
    return left.every((item, index) => deepEqual(item, right[index]));
  }
  if (!isObject(left) || !isObject(right)) return false;
  const leftKeys = Object.keys(left);
  const rightKeys = Object.keys(right);
  if (leftKeys.length !== rightKeys.length) return false;
  return leftKeys.every(key => hasOwn(right, key) && deepEqual(left[key], right[key]));
}

function modelError(code, message, issues = []) {
  const error = new Error(`${code}: ${message}`);
  error.code = code;
  error.issues = [...issues];
  return error;
}

function isValidBlockId(value) {
  return Number.isSafeInteger(value) && value > 0;
}

function isBoundComponent(component) {
  return Array.isArray(component?.dataRefs)
    && component.dataRefs.length > 0
    && component.dataRefs.every(ref => isValidBlockId(ref?.blockId));
}

function cloneComponents(presentation) {
  return Array.isArray(presentation?.display?.components)
    ? presentation.display.components.map(component => deepClone(component))
    : [];
}

function markEditorStatus(component) {
  const copy = deepClone(component);
  if (isBoundComponent(copy)) delete copy.editorStatus;
  else copy.editorStatus = EDITOR_STATUS.DRAFT_UNBOUND;
  return copy;
}

function normalizePresentationSource(source, options = {}) {
  let presentation = source;
  if (source?.renderPackage?.canvasStyle?.presentation) presentation = source.renderPackage.canvasStyle.presentation;
  else if (source?.canvasStyle?.presentation) presentation = source.canvasStyle.presentation;
  else if (source?.presentation?.type || source?.presentation?.displaySchemaVersion !== undefined) {
    presentation = source.presentation;
  }
  presentation = isObject(presentation) ? deepClone(presentation) : {};

  if (hasOwn(presentation, 'displaySchemaVersion')
    && presentation.displaySchemaVersion !== DISPLAY_SCHEMA_VERSION) {
    throw modelError('DISPLAY_SCHEMA_VERSION_UNSUPPORTED', '仅支持 displaySchemaVersion=1');
  }

  const result = {
    ...presentation,
    ...(hasOwn(presentation, 'type') ? {} : (options.type ? { type: options.type } : {})),
    ...(hasOwn(presentation, 'template') ? {} : (options.template ? { template: options.template } : {})),
    displaySchemaVersion: DISPLAY_SCHEMA_VERSION,
    display: {
      ...(isObject(presentation.display) ? presentation.display : {}),
      components: cloneComponents({ display: {
        components: Array.isArray(presentation.display?.components) ? presentation.display.components : []
      } })
    }
  };
  result.display.components = result.display.components.map(markEditorStatus);
  return result;
}

function componentIds(presentation) {
  return new Set((presentation?.display?.components || []).map(component => component.componentId));
}

function componentById(session, componentId) {
  const components = session?.presentation?.display?.components;
  const index = Array.isArray(components) ? components.findIndex(item => item.componentId === componentId) : -1;
  if (index < 0) throw modelError('COMPONENT_NOT_FOUND', `组件不存在: ${componentId}`);
  return { components, index, component: components[index] };
}

function ensureSession(session) {
  if (!isObject(session) || !isObject(session.presentation)) {
    throw modelError('EDITOR_SESSION_REQUIRED', '需要展示编辑会话');
  }
  if (!isObject(session.presentation.display) || !Array.isArray(session.presentation.display.components)) {
    throw modelError('EDITOR_SESSION_INVALID', '编辑会话缺少 display.components');
  }
  return session;
}

function attachReadAliases(session) {
  // 只提供便于面板读取的只读视图；它们不进入快照或序列化结果。
  Object.defineProperty(session, 'components', {
    enumerable: false,
    configurable: true,
    get: () => session.presentation.display.components
  });
  Object.defineProperty(session, 'display', {
    enumerable: false,
    configurable: true,
    get: () => session.presentation.display
  });
  return session;
}

function reviewIdsFor(session, extra = []) {
  const validIds = new Set(session.presentation.display.components.map(component => component.componentId));
  return [...new Set([...(session.reviewRequiredComponentIds || []), ...extra])]
    .filter(componentId => validIds.has(componentId));
}

function makeSession(presentation, loadedSnapshot, options = {}) {
  const current = deepClone(presentation);
  const snapshot = deepClone(loadedSnapshot || current);
  const selected = options.selectedComponentId && current.display.components
    .some(component => component.componentId === options.selectedComponentId)
    ? options.selectedComponentId : null;
  const reviewRequiredComponentIds = [...new Set(options.reviewRequiredComponentIds || [])]
    .filter(componentId => current.display.components.some(component => component.componentId === componentId));
  const session = {
    presentation: current,
    loadedSnapshot: snapshot,
    selectedComponentId: selected,
    dirty: !deepEqual(current, snapshot),
    reviewRequiredComponentIds,
    reviewRequired: reviewRequiredComponentIds.length > 0
  };
  return attachReadAliases(session);
}

function nextSession(session, presentation, options = {}) {
  ensureSession(session);
  const reviewRequiredComponentIds = options.reviewRequiredComponentIds
    || reviewIdsFor(session, options.addReviewRequired || []);
  return makeSession(presentation, session.loadedSnapshot || session.presentation, {
    selectedComponentId: hasOwn(options, 'selectedComponentId')
      ? options.selectedComponentId : session.selectedComponentId,
    reviewRequiredComponentIds
  });
}

function nextId(base, usedIds) {
  const cleanBase = String(base || 'component').replace(/[^A-Za-z0-9_-]/g, '-');
  const validBase = /^[A-Za-z]/.test(cleanBase) ? cleanBase : `component-${cleanBase}`;
  const truncate = value => value.slice(0, 64);
  let candidate = truncate(validBase.length >= 2 ? validBase : `${validBase}-1`);
  if (!usedIds.has(candidate)) return candidate;
  for (let suffix = 1; suffix < 10000; suffix += 1) {
    const tail = `-copy-${suffix}`;
    candidate = `${validBase.slice(0, Math.max(2, 64 - tail.length))}${tail}`;
    if (!usedIds.has(candidate)) return candidate;
  }
  throw modelError('COMPONENT_ID_EXHAUSTED', '无法生成不冲突的组件ID');
}

function nextCopyId(componentId, usedIds) {
  const cleanBase = String(componentId || 'component').replace(/[^A-Za-z0-9_-]/g, '-');
  const validBase = /^[A-Za-z]/.test(cleanBase) ? cleanBase : `component-${cleanBase}`;
  for (let suffix = 1; suffix < 10000; suffix += 1) {
    const tail = `-copy-${suffix}`;
    const candidate = `${validBase.slice(0, Math.max(2, 64 - tail.length))}${tail}`;
    if (!usedIds.has(candidate)) return candidate;
  }
  throw modelError('COMPONENT_ID_EXHAUSTED', '无法生成不冲突的复制组件ID');
}

function defaultContent(componentType) {
  const empty = { mainField: '', subFields: [], series: [], columns: [], tabs: [], rankingMetrics: [] };
  if (['METRIC_CARD', 'COMPLETION', 'MAP'].includes(componentType)) return { ...empty, mainField: 'value' };
  if (componentType === 'TREND') return {
    ...empty,
    series: [{ seriesKey: 'series-1', field: 'value', label: '指标', unit: 'YUAN' }]
  };
  if (componentType === 'COMPOSITION_TABS') return {
    ...empty,
    tabs: [{ tabKey: 'tab-1', label: '结构', corporateField: 'corporate', retailField: 'retail', totalField: '', unit: 'YUAN' }]
  };
  if (componentType === 'RANKING') return {
    ...empty,
    rankingMetrics: [{ metricKey: 'metric-1', field: 'value', label: '指标', unit: 'YUAN', direction: 'DESC' }]
  };
  if (componentType === 'DETAIL_TABLE') return {
    ...empty,
    columns: [{ columnKey: 'column-1', field: 'value', label: '指标', unit: 'YUAN', visible: true }]
  };
  return empty;
}

function mergeObjects(base, patch) {
  const output = deepClone(base);
  if (!isObject(patch)) return patch === undefined ? output : deepClone(patch);
  for (const [key, value] of Object.entries(patch)) {
    if (isObject(value) && isObject(output[key])) output[key] = mergeObjects(output[key], value);
    else output[key] = deepClone(value);
  }
  return output;
}

function defaultDataRef(blockId, currentRef = {}, binding = {}) {
  const ref = {
    blockId,
    role: hasOwn(binding, 'role') ? binding.role : (currentRef.role || 'PRIMARY'),
    metricCode: hasOwn(binding, 'metricCode') ? binding.metricCode : (currentRef.metricCode ?? ''),
    metricName: hasOwn(binding, 'metricName') ? binding.metricName : (currentRef.metricName ?? ''),
    unit: hasOwn(binding, 'unit') ? binding.unit : (currentRef.unit || 'YUAN'),
    dimension: hasOwn(binding, 'dimension') ? binding.dimension : (currentRef.dimension || 'COMMON')
  };
  if (hasOwn(binding, 'formula')) ref.formula = binding.formula;
  else if (hasOwn(currentRef, 'formula')) ref.formula = currentRef.formula;
  return ref;
}

function bindingToRef(binding, currentRef = {}) {
  const source = isObject(binding) && isObject(binding.dataRef) ? { ...binding, ...binding.dataRef } : binding;
  const blockId = isObject(source) ? source.blockId : source;
  if (!isValidBlockId(blockId)) throw modelError('INVALID_BLOCK_ID', 'blockId必须是正整数');
  return defaultDataRef(blockId, currentRef, isObject(source) ? source : {});
}

function sameDataRef(left, right) {
  return deepEqual(left, right);
}

function withDataRef(session, componentId, dataRefs, options = {}) {
  const { components, index, component } = componentById(session, componentId);
  const oldRefs = Array.isArray(component.dataRefs) ? component.dataRefs : [];
  const refs = dataRefs.map(ref => deepClone(ref));
  const blockIds = new Set();
  for (const ref of refs) {
    if (!isValidBlockId(ref?.blockId)) throw modelError('INVALID_BLOCK_ID', `组件 ${componentId} 的 blockId必须是正整数`);
    if (blockIds.has(ref.blockId)) throw modelError('DUPLICATE_BLOCK_ID', `组件 ${componentId} 不能重复引用 blockId=${ref.blockId}`);
    blockIds.add(ref.blockId);
  }
  const nextComponent = markEditorStatus({ ...deepClone(component), dataRefs: refs });
  if (nextComponent.text?.titleMode === 'AUTO') {
    const metricName = refs[0]?.metricName;
    nextComponent.text = { ...nextComponent.text, title: typeof metricName === 'string' ? metricName : '' };
  }
  const nextPresentation = deepClone(session.presentation);
  nextPresentation.display.components[index] = nextComponent;
  const changed = !sameDataRef(oldRefs, refs);
  const review = changed && nextComponent.text?.titleMode === 'CUSTOM' && !options.clearReview;
  return nextSession(session, nextPresentation, {
    addReviewRequired: review ? [componentId] : [],
    reviewRequiredComponentIds: review
      ? reviewIdsFor(session, [componentId])
      : (options.clearReview ? (session.reviewRequiredComponentIds || []).filter(id => id !== componentId) : session.reviewRequiredComponentIds)
  });
}

/** 从旧 presentation 或 displaySchemaVersion=1 配置创建纯状态编辑会话。 */
export function createPresentationEditorSession(source, options = {}) {
  const presentation = normalizePresentationSource(source, options);
  return makeSession(presentation, presentation, { selectedComponentId: null });
}

/** 语义化别名，供页面按“加载”动作调用。 */
export const loadPresentationEditorSession = createPresentationEditorSession;

/** 选择组件只改变编辑器选择状态，不把会话标为 dirty。 */
export function selectComponent(session, componentId) {
  ensureSession(session);
  if (componentId !== null && componentId !== undefined) componentById(session, componentId);
  return makeSession(session.presentation, session.loadedSnapshot || session.presentation, {
    selectedComponentId: componentId ?? null,
    reviewRequiredComponentIds: session.reviewRequiredComponentIds
  });
}

/** 创建一个满足七类组件最低 content 要求的编辑器初始实例。 */
export function addComponent(session, componentType, options = {}) {
  ensureSession(session);
  let type = componentType;
  let config = options;
  if (isObject(componentType)) {
    config = { ...componentType, ...options };
    type = config.componentType;
  }
  if (!COMPONENT_TYPES.includes(type)) {
    throw modelError('UNSUPPORTED_COMPONENT_TYPE', `组件类型不受支持: ${type}`);
  }
  const usedIds = componentIds(session.presentation);
  const requestedId = config.componentId || `${type.toLowerCase().replaceAll('_', '-')}-1`;
  const componentId = nextId(requestedId, usedIds);
  const layoutRegion = config.layoutRegion || 'LEFT';
  if (!LAYOUT_REGIONS.has(layoutRegion)) throw modelError('INVALID_LAYOUT_REGION', `布局区域不受支持: ${layoutRegion}`);
  if (hasOwn(config, 'visible') && typeof config.visible !== 'boolean') {
    throw modelError('INVALID_VISIBLE', 'visible必须是布尔值');
  }
  const regionOrders = session.presentation.display.components
    .filter(component => component.layoutRegion === layoutRegion)
    .map(component => component.order).filter(Number.isInteger);
  const order = Number.isInteger(config.order) && config.order >= 0
    ? config.order : (regionOrders.length ? Math.max(...regionOrders) + 1 : 0);
  const base = {
    componentId,
    componentType: type,
    layoutRegion,
    order,
    visible: hasOwn(config, 'visible') ? config.visible : true,
    text: {
      titleMode: 'AUTO', title: '', subtitle: '', description: '',
      ...(isObject(config.text) ? deepClone(config.text) : {})
    },
    format: {
      displayUnit: 'AUTO', decimals: 2, thousandsSeparator: true,
      negativeStyle: 'SIGNED', emptyText: '—',
      ...(isObject(config.format) ? deepClone(config.format) : {})
    },
    content: mergeObjects(defaultContent(type), config.content || {}),
    interaction: { action: 'NONE', ...(isObject(config.interaction) ? deepClone(config.interaction) : {}) },
    dataRefs: []
  };
  if (!TITLE_MODES.has(base.text.titleMode)) throw modelError('INVALID_TITLE_MODE', '标题模式不受支持');
  if (base.text.titleMode === 'CUSTOM' && typeof base.text.title !== 'string' || base.text.titleMode === 'CUSTOM' && !base.text.title.trim()) {
    throw modelError('CUSTOM_TITLE_REQUIRED', '自定义标题不能为空');
  }
  if (hasOwn(config, 'dataRefs')) {
    if (!Array.isArray(config.dataRefs)) throw modelError('INVALID_DATA_REFS', 'dataRefs必须是数组');
    base.dataRefs = config.dataRefs.map(ref => bindingToRef(ref, {}));
  } else if (hasOwn(config, 'blockId')) {
    base.dataRefs = [bindingToRef(config, {})];
  }
  const component = markEditorStatus(base);
  const nextPresentation = deepClone(session.presentation);
  nextPresentation.display.components.push(component);
  return nextSession(session, nextPresentation, { selectedComponentId: componentId });
}

/** 复制实例只生成确定性的副本 ID，保留配置内容与数据引用。 */
export function duplicateComponent(session, componentId) {
  const { components, index, component } = componentById(ensureSession(session), componentId);
  const nextComponent = deepClone(component);
  nextComponent.componentId = nextCopyId(componentId, componentIds(session.presentation));
  const nextPresentation = deepClone(session.presentation);
  const regionIndexes = components
    .map((item, itemIndex) => item.layoutRegion === component.layoutRegion ? itemIndex : -1)
    .filter(itemIndex => itemIndex >= 0);
  const sourceRegionIndex = regionIndexes.indexOf(index);
  nextPresentation.display.components.splice(index + 1, 0, nextComponent);
  const regionAfterInsert = nextPresentation.display.components
    .filter(item => item.layoutRegion === component.layoutRegion);
  regionAfterInsert.forEach((item, regionIndex) => { item.order = regionIndex; });
  // 不依赖数组下标作为组件身份，但保持复制件位于原实例之后。
  if (sourceRegionIndex < 0) nextComponent.order = component.order;
  return nextSession(session, nextPresentation, { selectedComponentId: nextComponent.componentId });
}

/** 删除展示实例；组件内 dataRefs 只是引用，不会删除任何数据源对象。 */
export function deleteComponent(session, componentId) {
  const { index } = componentById(ensureSession(session), componentId);
  const nextPresentation = deepClone(session.presentation);
  nextPresentation.display.components.splice(index, 1);
  const selectedComponentId = session.selectedComponentId === componentId ? null : session.selectedComponentId;
  const reviewRequiredComponentIds = (session.reviewRequiredComponentIds || []).filter(id => id !== componentId);
  return nextSession(session, nextPresentation, { selectedComponentId, reviewRequiredComponentIds });
}

/** 设置显示状态，显式保留 false。 */
export function setComponentVisibility(session, componentId, visible) {
  if (typeof visible !== 'boolean') throw modelError('INVALID_VISIBLE', 'visible必须是布尔值');
  const { index, component } = componentById(ensureSession(session), componentId);
  const nextPresentation = deepClone(session.presentation);
  nextPresentation.display.components[index] = { ...deepClone(component), visible };
  return nextSession(session, nextPresentation);
}

function regionComponentIndexes(components, region) {
  return components.map((component, index) => component.layoutRegion === region ? index : -1)
    .filter(index => index >= 0);
}

function applyRegionOrder(presentation, region, orderedIds) {
  const components = presentation.display.components;
  const indexes = regionComponentIndexes(components, region);
  const current = indexes.map(index => components[index]);
  const currentIds = current.map(component => component.componentId);
  if (new Set(orderedIds).size !== orderedIds.length
    || orderedIds.length !== currentIds.length
    || currentIds.some(componentId => !orderedIds.includes(componentId))) {
    throw modelError('INVALID_REORDER', `区域 ${region} 的组件顺序必须完整且不重复`);
  }
  const byId = new Map(current.map(component => [component.componentId, component]));
  orderedIds.forEach((componentId, order) => {
    const target = deepClone(byId.get(componentId));
    target.order = order;
    components[indexes[order]] = target;
  });
}

/** 同区域移动组件；direction 支持 UP/DOWN，也支持目标区域索引。 */
export function moveComponent(session, componentId, directionOrIndex) {
  const { component } = componentById(ensureSession(session), componentId);
  const region = component.layoutRegion;
  const regionItems = session.presentation.display.components
    .filter(item => item.layoutRegion === region).map(item => item.componentId);
  const currentIndex = regionItems.indexOf(componentId);
  let targetIndex = directionOrIndex;
  if (typeof directionOrIndex === 'string') {
    const direction = directionOrIndex.toUpperCase();
    if (direction === 'UP') targetIndex = currentIndex - 1;
    else if (direction === 'DOWN') targetIndex = currentIndex + 1;
    else throw modelError('INVALID_MOVE_DIRECTION', '组件移动方向必须是 UP 或 DOWN');
  }
  if (!Number.isInteger(targetIndex) || targetIndex < 0 || targetIndex >= regionItems.length) return session;
  const ordered = [...regionItems];
  ordered.splice(currentIndex, 1);
  ordered.splice(targetIndex, 0, componentId);
  const nextPresentation = deepClone(session.presentation);
  applyRegionOrder(nextPresentation, region, ordered);
  return nextSession(session, nextPresentation);
}

/** 按同一 layoutRegion 的完整 ID 列表重排。 */
export function reorderComponents(session, region, orderedIds) {
  ensureSession(session);
  if (!LAYOUT_REGIONS.has(region) && Number.isInteger(orderedIds)) {
    return moveComponent(session, region, orderedIds);
  }
  if (!LAYOUT_REGIONS.has(region)) throw modelError('INVALID_LAYOUT_REGION', `布局区域不受支持: ${region}`);
  if (!Array.isArray(orderedIds)) throw modelError('INVALID_REORDER', 'orderedIds必须是数组');
  const nextPresentation = deepClone(session.presentation);
  applyRegionOrder(nextPresentation, region, orderedIds);
  return nextSession(session, nextPresentation);
}

function updateNestedComponent(session, componentId, key, patch, allowedKeys) {
  if (!isObject(patch)) throw modelError('INVALID_COMPONENT_PATCH', `${key}配置必须是对象`);
  for (const patchKey of Object.keys(patch)) {
    if (!allowedKeys.has(patchKey)) throw modelError('UNKNOWN_COMPONENT_FIELD', `${key}包含未知字段: ${patchKey}`);
  }
  const { index, component } = componentById(ensureSession(session), componentId);
  const nextComponent = deepClone(component);
  nextComponent[key] = mergeObjects(component[key] || {}, patch);
  const nextPresentation = deepClone(session.presentation);
  nextPresentation.display.components[index] = nextComponent;
  return nextSession(session, nextPresentation);
}

/** 通用组件配置更新，保留显式 0、false、空字符串。 */
export function updateComponent(session, componentId, patch = {}) {
  if (!isObject(patch)) throw modelError('INVALID_COMPONENT_PATCH', '组件配置必须是对象');
  for (const key of Object.keys(patch)) {
    if (!COMPONENT_KEYS.has(key)) throw modelError('UNKNOWN_COMPONENT_FIELD', `组件包含未知字段: ${key}`);
  }
  // 复用专用更新器，确保通用入口同样执行标题和交互白名单规则。
  let next = session;
  if (hasOwn(patch, 'text')) next = updateComponentText(next, componentId, patch.text);
  if (hasOwn(patch, 'format')) next = updateComponentFormat(next, componentId, patch.format);
  if (hasOwn(patch, 'interaction')) next = updateComponentInteraction(next, componentId, patch.interaction);
  if (hasOwn(patch, 'content')) next = updateComponentContent(next, componentId, patch.content);
  const scalarPatch = Object.fromEntries(Object.entries(patch)
    .filter(([key]) => !['text', 'format', 'interaction', 'content'].includes(key)));
  if (!Object.keys(scalarPatch).length) return next;
  session = next;
  const { index, component } = componentById(ensureSession(session), componentId);
  const nextComponent = deepClone(component);
  for (const [key, value] of Object.entries(scalarPatch)) nextComponent[key] = deepClone(value);
  const marked = markEditorStatus(nextComponent);
  const nextPresentation = deepClone(session.presentation);
  nextPresentation.display.components[index] = marked;
  return nextSession(session, nextPresentation);
}

/** 更新标题模式、标题、副标题和说明。标题清空意味着恢复 AUTO 继承。 */
export function updateComponentText(session, componentId, patch = {}) {
  if (!isObject(patch)) throw modelError('INVALID_TEXT_PATCH', 'text配置必须是对象');
  for (const key of Object.keys(patch)) {
    if (!TEXT_KEYS.has(key)) throw modelError('UNKNOWN_TEXT_FIELD', `text包含未知字段: ${key}`);
  }
  const { index, component } = componentById(ensureSession(session), componentId);
  const text = { ...(isObject(component.text) ? deepClone(component.text) : {}) };
  const hasTitle = hasOwn(patch, 'title');
  const hasMode = hasOwn(patch, 'titleMode');
  if (hasMode) {
    if (!TITLE_MODES.has(patch.titleMode)) throw modelError('INVALID_TITLE_MODE', '标题模式不受支持');
    text.titleMode = patch.titleMode;
  }
  if (hasTitle) text.title = patch.title;
  for (const key of ['subtitle', 'description']) if (hasOwn(patch, key)) text[key] = patch[key];
  if (hasTitle && !hasMode) text.titleMode = typeof patch.title === 'string' && patch.title.trim() ? 'CUSTOM' : 'AUTO';
  if (text.titleMode === 'AUTO' && (hasMode && patch.titleMode === 'AUTO' || hasTitle && !text.title)) text.title = hasTitle ? patch.title : '';
  if (text.titleMode === 'CUSTOM' && (typeof text.title !== 'string' || !text.title.trim())) {
    throw modelError('CUSTOM_TITLE_REQUIRED', '自定义标题不能为空');
  }
  const nextComponent = { ...deepClone(component), text };
  const nextPresentation = deepClone(session.presentation);
  nextPresentation.display.components[index] = nextComponent;
  const reviewRequiredComponentIds = text.titleMode === 'CUSTOM'
    ? (session.reviewRequiredComponentIds || []).filter(id => id !== componentId)
    : session.reviewRequiredComponentIds;
  return nextSession(session, nextPresentation, { reviewRequiredComponentIds });
}

/** 便捷标题更新；空值自动清除 CUSTOM 覆盖并恢复 AUTO。 */
export function setComponentTitle(session, componentId, titleOrConfig, mode) {
  if (isObject(titleOrConfig)) {
    const config = { ...titleOrConfig };
    if (hasOwn(config, 'value') && !hasOwn(config, 'title')) config.title = config.value;
    delete config.value;
    if (hasOwn(config, 'mode') && !hasOwn(config, 'titleMode')) config.titleMode = config.mode;
    delete config.mode;
    return updateComponentText(session, componentId, config);
  }
  return updateComponentText(session, componentId, {
    title: titleOrConfig,
    ...(mode ? { titleMode: mode } : {})
  });
}

export function updateComponentFormat(session, componentId, patch = {}) {
  return updateNestedComponent(session, componentId, 'format', patch, FORMAT_KEYS);
}

function validateInteraction(interaction) {
  const action = interaction?.action || 'NONE';
  const target = interaction?.target;
  if (!INTERACTION_ACTIONS.includes(action)) throw modelError('INVALID_INTERACTION', `交互动作不受白名单支持: ${action}`);
  if (target !== undefined && target !== '' && (typeof target !== 'string' || !/^[A-Z][A-Z0-9_]{0,63}$/.test(target))) {
    throw modelError('INVALID_INTERACTION', '交互目标不在白名单内');
  }
  if (action === 'NONE' && target) throw modelError('INVALID_INTERACTION', 'NONE 交互不能配置目标');
  if (action === 'OPEN_BUSINESS_LINE' && !BUSINESS_LINE_TARGETS.has(target)) {
    throw modelError('INVALID_INTERACTION', '业务条线目标不在白名单内');
  }
}

export function updateComponentInteraction(session, componentId, patch = {}) {
  const next = updateNestedComponent(session, componentId, 'interaction', patch, INTERACTION_KEYS);
  const { component } = componentById(next, componentId);
  validateInteraction(component.interaction);
  return next;
}

export function updateComponentContent(session, componentId, patch = {}) {
  return updateNestedComponent(session, componentId, 'content', patch, CONTENT_KEYS);
}

/** 绑定一个已有 blockId；换源不触碰任何共享数据源对象。 */
export function bindComponent(session, componentId, binding, metadata = {}) {
  const { component } = componentById(ensureSession(session), componentId);
  const currentRef = Array.isArray(component.dataRefs) ? component.dataRefs[0] || {} : {};
  const input = isObject(binding)
    ? { ...binding, ...(isObject(metadata) ? metadata : {}) }
    : { ...(isObject(metadata) ? metadata : {}), blockId: binding };
  const ref = bindingToRef(input, currentRef);
  return withDataRef(session, componentId, [ref]);
}

/** 替换组件指定数据引用，允许一个组件拥有多个不同 blockId。 */
export function replaceDataReference(session, componentId, binding, refIndex = 0) {
  const { component } = componentById(ensureSession(session), componentId);
  if (!Number.isInteger(refIndex) || refIndex < 0) throw modelError('INVALID_DATA_REF_INDEX', '数据引用位置不合法');
  const refs = Array.isArray(component.dataRefs) ? component.dataRefs.map(ref => deepClone(ref)) : [];
  const currentRef = refs[refIndex] || {};
  const ref = bindingToRef(binding, currentRef);
  if (refIndex > refs.length) throw modelError('INVALID_DATA_REF_INDEX', '数据引用位置不连续');
  refs[refIndex] = ref;
  return withDataRef(session, componentId, refs);
}

/** 获取编辑器预览标题，AUTO 会读取当前数据引用中的指标名。 */
export function resolveEditorTitle(session, componentId, context = {}) {
  const { component } = componentById(ensureSession(session), componentId);
  const ref = Array.isArray(component.dataRefs) ? component.dataRefs[0] || {} : {};
  return resolveComponentTitle(component, {
    metricName: ref.metricName,
    ...context
  });
}

/** 取消本地修改并恢复加载时快照。 */
export function cancelEditorSession(session) {
  ensureSession(session);
  const snapshot = deepClone(session.loadedSnapshot || session.presentation);
  return makeSession(snapshot, snapshot, { selectedComponentId: null });
}

/** 保存成功后把当前深拷贝设为新的加载快照并清除 dirty。 */
export function commitSnapshot(session) {
  ensureSession(session);
  const current = deepClone(session.presentation);
  return makeSession(current, current, {
    selectedComponentId: session.selectedComponentId,
    reviewRequiredComponentIds: []
  });
}

function stripEditorMetadata(value) {
  if (Array.isArray(value)) return value.map(item => stripEditorMetadata(item));
  if (!isObject(value)) return value;
  return Object.fromEntries(Object.entries(value)
    .filter(([key]) => !EDITOR_ONLY_KEYS.has(key))
    .map(([key, item]) => [key, stripEditorMetadata(item)]));
}

/**
 * 只允许可提交的展示配置通过契约校验；编辑器内部的 DRAFT_UNBOUND 标记不会写入协议。
 */
export function serializeEditorSession(input) {
  const presentation = isObject(input?.presentation) ? input.presentation : input;
  if (!isObject(presentation)) throw modelError('DISPLAY_CONFIG_INVALID', '展示配置不能为空');
  const components = presentation.display?.components;
  const unbound = Array.isArray(components)
    ? components.filter(component => !isBoundComponent(component)).map(component => component.componentId || '<unknown>')
    : [];
  if (unbound.length) {
    throw modelError('DRAFT_UNBOUND', `存在未绑定数据源的展示组件: ${unbound.join(', ')}`);
  }
  const serialized = stripEditorMetadata(deepClone(presentation));
  const issues = validateDisplayConfig(serialized);
  if (issues.length) {
    throw modelError('DISPLAY_CONFIG_INVALID', `展示配置无法序列化: ${issues.join('；')}`, issues);
  }
  return serialized;
}

export const serializePresentation = serializeEditorSession;
export const createEditorModel = createPresentationEditorSession;
export const loadEditorModel = createPresentationEditorSession;
export const addPresentationComponent = addComponent;
export const duplicatePresentationComponent = duplicateComponent;
export const deletePresentationComponent = deleteComponent;
export const removeComponent = deleteComponent;
export const setVisibility = setComponentVisibility;
export const toggleComponentVisibility = (session, componentId) => {
  const { component } = componentById(ensureSession(session), componentId);
  return setComponentVisibility(session, componentId, component.visible !== true);
};
export const movePresentationComponent = moveComponent;
export const reorderPresentationComponents = reorderComponents;
export const updatePresentationComponent = updateComponent;
export const bindPresentationComponent = bindComponent;
export const replaceComponentDataRef = replaceDataReference;
export const cancelPresentationEditorSession = cancelEditorSession;
export const cancelEditorSessionChanges = cancelEditorSession;
export const commitEditorSnapshot = commitSnapshot;

export function getComponentEditorStatus(session, componentId) {
  const { component } = componentById(ensureSession(session), componentId);
  return component.editorStatus || (isBoundComponent(component) ? EDITOR_STATUS.BOUND : EDITOR_STATUS.DRAFT_UNBOUND);
}

export const isComponentDraftUnbound = (session, componentId) => getComponentEditorStatus(session, componentId) === EDITOR_STATUS.DRAFT_UNBOUND;
