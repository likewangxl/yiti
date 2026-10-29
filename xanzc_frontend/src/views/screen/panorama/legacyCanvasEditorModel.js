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
