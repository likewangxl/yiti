function clone(value) {
  if (Array.isArray(value)) return value.map(clone);
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, clone(item)]));
  return value;
}

export function parseLegacyJson(value, fallback = {}) {
  if (value && typeof value === 'object') return clone(value);
  if (typeof value !== 'string' || !value.trim()) return clone(fallback);
  try { return JSON.parse(value); } catch { return fallback; }
}

const JSON_FIELDS = new Set(['bindJson', 'styleJson', 'drillJson']);
const NULLABLE_NODE_FIELDS = new Set(['name', 'children', 'innerType', 'blockId', 'style', 'propValue', 'bindJson', 'styleJson', 'drillJson']);

function canonicalize(value) {
  if (Array.isArray(value)) return value.map(canonicalize);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.keys(value).sort().map(key => [key, canonicalize(value[key])]));
  }
  return value;
}

function canonicalizeJsonField(value) {
  if (value && typeof value === 'object') return canonicalize(value);
  if (typeof value !== 'string') return value;
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === 'object' ? canonicalize(parsed) : value;
  } catch { return value; }
}

function normalizeNode(node) {
  if (!node || typeof node !== 'object') return node;
  const out = {};
  for (const [key, value] of Object.entries(node)) {
    if (value === null && NULLABLE_NODE_FIELDS.has(key)) continue;
    if (JSON_FIELDS.has(key)) out[key] = canonicalizeJsonField(value);
    else if (key === 'children' && Array.isArray(value)) out[key] = value.map(normalizeNode);
    else out[key] = canonicalize(value);
  }
  if (!Object.prototype.hasOwnProperty.call(out, 'isLock')) out.isLock = false;
  if (!Object.prototype.hasOwnProperty.call(out, 'isShow')) out.isShow = true;
  return canonicalize(out);
}

function normalizeCanvasStyle(value) {
  const style = value && typeof value === 'object' ? { ...value } : {};
  // Jackson 可补齐 DTO 的可空属性；只忽略已知空默认值，不吞掉未知配置。
  for (const key of ['background', 'backgroundType', 'bgGradient', 'bgImage', 'themeOverride',
    'presentation', 'dataNotice', 'dataClassification', 'metricLabels', 'sourceAvailability']) {
    if (style[key] === null) delete style[key];
  }
  if (style.schemaVersion == null) style.schemaVersion = 1;
  if (style.designWidth == null) style.designWidth = 1920;
  if (style.designHeight == null) style.designHeight = 1080;
  if (style.adaptor == null) style.adaptor = 'keepProportion';
  return canonicalize(style);
}

/** 将画布实际响应转换成只比较版本、样式和组件树的稳定快照。 */
export function normalizeLegacyCanvasSnapshot({ canvasVersion, canvasStyle, canvasStyleJson, components, canvasDraftJson } = {}) {
  const draft = parseLegacyJson(canvasDraftJson, {});
  return {
    canvasVersion,
    canvasStyle: normalizeCanvasStyle(canvasStyle ?? parseLegacyJson(canvasStyleJson, {})),
    components: Array.isArray(components ?? draft.components) ? (components ?? draft.components).map(normalizeNode) : components ?? draft.components
  };
}

export function legacyCanvasSnapshotsEqual(left, right) {
  return JSON.stringify(normalizeLegacyCanvasSnapshot(left)) === JSON.stringify(normalizeLegacyCanvasSnapshot(right));
}

export function collectLegacyNodes(nodes = [], output = []) {
  for (const node of Array.isArray(nodes) ? nodes : []) {
    output.push(node);
    if (Array.isArray(node?.children)) collectLegacyNodes(node.children, output);
  }
  return output;
}

export function updateLegacyNode(nodes, targetId, updater) {
  return (Array.isArray(nodes) ? nodes : []).map(node => {
    const next = node?.id === targetId ? updater(clone(node)) : clone(node);
    return Array.isArray(next?.children) ? { ...next, children: updateLegacyNode(next.children, targetId, updater) } : next;
  });
}

function fillJson(nodeValue, blockValue) {
  const nodeJson = parseLegacyJson(nodeValue, null);
  if (nodeJson && Object.keys(nodeJson).length) return typeof nodeValue === 'string' ? nodeValue : JSON.stringify(nodeJson);
  return typeof blockValue === 'string' ? blockValue : JSON.stringify(blockValue || {});
}

export function buildLegacyCanvasPayload({ screenId, canvasVersion, canvasStyle = {}, components = [], blocks = [] } = {}) {
  const blockById = new Map((Array.isArray(blocks) ? blocks : []).map(block => [String(block.id ?? block.blockId), block]));
  const mergeNode = node => {
    const next = clone(node);
    if (next.component === 'ChartWidget') {
      const block = blockById.get(String(next.blockId));
      if (!block) throw new Error(`ChartWidget ${next.id || next.blockId || ''} 缺少对应区块`);
      next.bindJson = fillJson(next.bindJson, block.bindJson);
      next.styleJson = fillJson(next.styleJson, block.styleJson);
      next.drillJson = fillJson(next.drillJson, block.drillJson);
    }
    if (Array.isArray(next.children)) next.children = next.children.map(mergeNode);
    return next;
  };
  return { screenId, expectedVersion: canvasVersion, canvasStyle: clone(canvasStyle), components: (Array.isArray(components) ? components : []).map(mergeNode) };
}

export function nodeTitle(node, blocks = []) {
  const block = blocks.find(item => String(item.id ?? item.blockId) === String(node?.blockId));
  const nodeStyle = parseLegacyJson(node?.styleJson, null);
  const style = nodeStyle && Object.keys(nodeStyle).length ? nodeStyle : parseLegacyJson(block?.styleJson, {});
  const props = node?.propValue && typeof node.propValue === 'object' ? node.propValue : parseLegacyJson(node?.propValue, {});
  return (node?.component === 'TitleBar' ? props.text : '') || style.title || node?.name || node?.component || '未命名组件';
}

export function nodeBinding(node) {
  return parseLegacyJson(node?.bindJson, null);
}
